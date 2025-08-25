package com.mtkresearch.breezeapp.engine.runner.guardian

import com.mtkresearch.breezeapp.engine.core.Logger
import com.mtkresearch.breezeapp.engine.model.CapabilityType
import com.mtkresearch.breezeapp.engine.model.InferenceRequest
import com.mtkresearch.breezeapp.engine.model.InferenceResult
import com.mtkresearch.breezeapp.engine.runner.core.BaseRunner
import com.mtkresearch.breezeapp.engine.runner.core.RunnerManager

/**
 * GuardianPipeline - Core Guardian Processing Engine
 * 
 * Centralizes all guardian-related processing logic and provides a clean API
 * for integrating guardian checks into the AI pipeline.
 * 
 * This class is designed to be:
 * - Lightweight and efficient
 * - Easy to integrate with existing AIEngineManager
 * - Extensible for future guardian features
 */
class GuardianPipeline(
    private val runnerManager: RunnerManager,
    private val logger: Logger
) {
    
    companion object {
        private const val TAG = "GuardianPipeline"
        
        // Parameter keys for guardian configuration overrides
        const val PARAM_GUARDIAN_ENABLED = "guardian_enabled"
        const val PARAM_GUARDIAN_STRICTNESS = "guardian_strictness"
        const val PARAM_GUARDIAN_CHECKPOINT = "guardian_checkpoint"
        const val PARAM_GUARDIAN_RUNNER = "guardian_runner"
    }
    
    /**
     * Perform input validation check.
     * 
     * @param request The input request to validate
     * @param config Guardian configuration
     * @return GuardianCheckResult indicating the outcome
     */
    suspend fun checkInput(
        request: InferenceRequest,
        config: GuardianPipelineConfig
    ): GuardianCheckResult {
        
        if (!config.shouldCheckInput()) {
            return GuardianCheckResult.skip("Input validation disabled")
        }
        
        logger.d(TAG, "Performing input validation for session: ${request.sessionId}")
        
        // Extract text content for analysis
        val textContent = extractTextFromRequest(request)
        if (textContent.isNullOrBlank()) {
            return GuardianCheckResult.skip("No text content to analyze")
        }
        
        return performGuardianAnalysis(textContent, config, isInput = true)
    }
    
    /**
     * Perform output filtering check.
     * 
     * @param result The AI output result to filter
     * @param config Guardian configuration
     * @return GuardianCheckResult indicating the outcome
     */
    suspend fun checkOutput(
        result: InferenceResult,
        config: GuardianPipelineConfig
    ): GuardianCheckResult {
        
        if (!config.shouldCheckOutput() || result.error != null) {
            return GuardianCheckResult.skip("Output filtering disabled or result failed")
        }
        
        logger.d(TAG, "Performing output filtering")
        
        // Extract text content from result
        val textContent = extractTextFromResult(result)
        if (textContent.isNullOrBlank()) {
            return GuardianCheckResult.skip("No text content to filter")
        }
        
        return performGuardianAnalysis(textContent, config, isInput = false)
    }
    
    /**
     * Create guardian configuration from request parameters and global settings.
     * Allows per-request guardian overrides.
     */
    fun createEffectiveConfig(
        baseConfig: GuardianPipelineConfig,
        request: InferenceRequest
    ): GuardianPipelineConfig {
        
        val params = request.params
        
        // Check if guardian is explicitly disabled for this request
        val enabled = (params[PARAM_GUARDIAN_ENABLED] as? Boolean) ?: baseConfig.enabled
        if (!enabled) {
            return GuardianPipelineConfig.DISABLED
        }
        
        // Apply parameter overrides
        val strictness = (params[PARAM_GUARDIAN_STRICTNESS] as? String) 
            ?: baseConfig.strictnessLevel
        
        val checkpointOverride = params[PARAM_GUARDIAN_CHECKPOINT] as? String
        val checkpoints = when (checkpointOverride) {
            "input_only" -> setOf(GuardianCheckpoint.INPUT_VALIDATION)
            "output_only" -> setOf(GuardianCheckpoint.OUTPUT_FILTERING)
            "both" -> setOf(GuardianCheckpoint.BOTH)
            else -> baseConfig.checkpoints
        }
        
        val guardianRunner = (params[PARAM_GUARDIAN_RUNNER] as? String) 
            ?: baseConfig.guardianRunnerName
        
        return baseConfig.copy(
            enabled = enabled,
            checkpoints = checkpoints,
            strictnessLevel = strictness,
            guardianRunnerName = guardianRunner
        )
    }
    
    // Private helper methods
    
    /**
     * Perform the actual guardian analysis.
     */
    private suspend fun performGuardianAnalysis(
        textContent: String,
        config: GuardianPipelineConfig,
        isInput: Boolean
    ): GuardianCheckResult {
        
        return try {
            // 1. Select guardian runner
            val guardianRunner = selectGuardianRunner(config.guardianRunnerName)
            if (guardianRunner == null) {
                logger.w(TAG, "No guardian runner available")
                return GuardianCheckResult.skip("No guardian runner available")
            }
            
            // 2. Create guardian request
            val guardianRequest = InferenceRequest(
                sessionId = "guardian-${System.currentTimeMillis()}",
                inputs = mapOf(InferenceRequest.INPUT_TEXT to textContent),
                params = mapOf("strictness" to config.strictnessLevel)
            )
            
            // 3. Run guardian analysis
            logger.d(TAG, "Running guardian analysis with ${guardianRunner.getRunnerInfo().name}")
            val guardianResult = guardianRunner.run(guardianRequest)
            
            // 4. Convert to check result
            val checkResult = GuardianCheckResult.fromInferenceResult(guardianResult, config.failureStrategy)
            
            // 5. Log the result
            logGuardianResult(checkResult, isInput)
            
            checkResult
            
        } catch (e: Exception) {
            logger.e(TAG, "Guardian analysis failed", e)
            GuardianCheckResult.skip("Guardian analysis error: ${e.message}")
        }
    }
    
    /**
     * Select appropriate guardian runner.
     */
    private fun selectGuardianRunner(preferredName: String?): BaseRunner? {
        return if (preferredName != null) {
            // Use specific guardian runner
            runnerManager.getAllRunners().find { 
                it.getRunnerInfo().name == preferredName &&
                it.getCapabilities().contains(CapabilityType.GUARDIAN)
            }
        } else {
            // Use default guardian runner
            runnerManager.getRunner(CapabilityType.GUARDIAN)
        }
    }
    
    /**
     * Extract text content from inference request.
     */
    private fun extractTextFromRequest(request: InferenceRequest): String? {
        return request.inputs[InferenceRequest.INPUT_TEXT] as? String
    }
    
    /**
     * Extract text content from inference result.
     */
    private fun extractTextFromResult(result: InferenceResult): String? {
        // Try different output keys that might contain text
        return result.outputs["text"] as? String 
            ?: result.outputs["response"] as? String
            ?: result.outputs["content"] as? String
    }
    
    /**
     * Log guardian check result for debugging.
     */
    private fun logGuardianResult(result: GuardianCheckResult, isInput: Boolean) {
        val stage = if (isInput) "input" else "output"
        
        when (result) {
            is GuardianCheckResult.Skipped -> {
                logger.d(TAG, "Guardian $stage check skipped: ${result.reason}")
            }
            is GuardianCheckResult.Passed -> {
                logger.d(TAG, "Guardian $stage check passed (risk: ${result.analysisResult.riskScore})")
            }
            is GuardianCheckResult.Failed -> {
                logger.w(TAG, "Guardian $stage check failed: ${result.analysisResult.status} " +
                    "(categories: ${result.analysisResult.categories.joinToString()}, " +
                    "risk: ${result.analysisResult.riskScore}, " +
                    "strategy: ${result.strategy})")
            }
        }
    }
}