package com.mtkresearch.breezeapp.engine.runner.guardian

import com.mtkresearch.breezeapp.engine.core.Logger
import kotlinx.coroutines.withTimeoutOrNull
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
        
        // Progressive streaming Guardian parameters
        const val PARAM_GUARDIAN_MICRO_BATCH_SIZE = "guardian_micro_batch_size"
        const val PARAM_GUARDIAN_PROCESS_INTERVAL_MS = "guardian_process_interval_ms"
        const val PARAM_GUARDIAN_WINDOW_OVERLAP = "guardian_window_overlap"
        
        // Default values for progressive Guardian
        const val DEFAULT_MICRO_BATCH_SIZE = 5 // tokens
        const val DEFAULT_PROCESS_INTERVAL_MS = 100L // milliseconds  
        const val DEFAULT_WINDOW_OVERLAP = 2 // tokens
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
     * Progressive Guardian analysis for streaming content
     * Processes content in micro-batches to maintain streaming feel while ensuring safety
     * 
     * @param contentBuffer The accumulated content buffer
     * @param config Guardian configuration with micro-batch settings
     * @return List of GuardianMaskingResult indicating which parts to mask
     */
    /**
     * Progressive Guardian analysis for streaming content.
     * Processes content in micro-batches to maintain streaming feel while ensuring safety.
     *
     * @param contentBuffer The accumulated content buffer.
     * @param config Guardian configuration with micro-batch settings.
     * @return List of [GuardianMaskingResult] indicating which parts to mask.
     */
    suspend fun checkProgressiveOutput(
        contentBuffer: String,
        config: GuardianPipelineConfig
    ): List<GuardianMaskingResult> {

        if (!config.shouldCheckOutput() || contentBuffer.isBlank()) {
            return emptyList()
        }

        val microBatchSize = config.microBatchSize
        val windowOverlap = config.windowOverlap

        logger.d(TAG, "Progressive Guardian analysis - buffer length: ${contentBuffer.length}, batch size: $microBatchSize")

        // Split content into overlapping micro-batches
        val microBatches = createMicroBatches(contentBuffer, microBatchSize, windowOverlap)
        val maskingResults = mutableListOf<GuardianMaskingResult>()

        for ((index, batch) in microBatches.withIndex()) {
            try {
                val checkResult = performGuardianAnalysis(batch.content, config, isInput = false)

                if (checkResult is GuardianCheckResult.Failed) {
                    // Create masking result for this batch
                    val maskingResult = GuardianMaskingResult(
                        startIndex = batch.startIndex,
                        endIndex = batch.endIndex,
                        originalText = batch.content,
                        maskedText = applySmartMasking(batch.content),
                        violationCategories = checkResult.analysisResult.categories,
                        riskScore = checkResult.analysisResult.riskScore
                    )
                    maskingResults.add(maskingResult)
                    logger.w(TAG, "Progressive Guardian violation detected in batch $index: ${checkResult.analysisResult.categories}")
                } else {
                    logger.d(TAG, "Progressive Guardian batch $index passed")
                }

            } catch (e: Exception) {
                logger.e(TAG, "Progressive Guardian analysis failed for batch $index", e)
                // Continue processing other batches
            }
        }

        return maskingResults
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
     * Perform the actual guardian analysis with robust timeout protection.
     */
    private suspend fun performGuardianAnalysis(
        textContent: String,
        config: GuardianPipelineConfig,
        isInput: Boolean
    ): GuardianCheckResult {
        
        return try {
            // Apply overall timeout to prevent hanging
            kotlinx.coroutines.withTimeoutOrNull(30000L) { // 30 second overall timeout
                performGuardianAnalysisInternal(textContent, config, isInput)
            } ?: run {
                logger.e(TAG, "Guardian analysis timed out after 30 seconds")
                GuardianCheckResult.skip("Guardian analysis timed out")
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            logger.e(TAG, "Guardian analysis cancelled due to timeout", e)
            GuardianCheckResult.skip("Guardian analysis timeout")
        } catch (e: Exception) {
            logger.e(TAG, "Guardian analysis failed", e)
            GuardianCheckResult.skip("Guardian analysis error: ${e.message}")
        }
    }
    
    /**
     * Internal guardian analysis implementation with granular timeouts.
     */
    private suspend fun performGuardianAnalysisInternal(
        textContent: String,
        config: GuardianPipelineConfig,
        isInput: Boolean
    ): GuardianCheckResult {
        
        // 1. Select guardian runner with timeout
        val guardianRunner = kotlinx.coroutines.withTimeoutOrNull(5000L) {
            selectGuardianRunner(config.guardianRunnerName)
        } ?: run {
            logger.w(TAG, "Guardian runner selection timed out")
            return GuardianCheckResult.skip("Guardian runner selection timeout")
        }
        
        if (guardianRunner == null) {
            logger.w(TAG, "No guardian runner available")
            return GuardianCheckResult.skip("No guardian runner available")
        }

        // 2. Load runner if needed with timeout
        if (!guardianRunner.isLoaded()) {
            logger.d(TAG, "Loading guardian runner: ${guardianRunner.getRunnerInfo().name}")
            val loaded = kotlinx.coroutines.withTimeoutOrNull(10000L) { // 10 second timeout for loading
                val settings = runnerManager.getCurrentSettings()
                guardianRunner.load("", settings, emptyMap())
            } ?: false
            
            if (!loaded) {
                logger.e(TAG, "Failed to load guardian runner (timeout or failure): ${guardianRunner.getRunnerInfo().name}")
                return GuardianCheckResult.skip("Failed to load guardian runner")
            }
        }
        
        // 3. Create guardian request
        val guardianRequest = InferenceRequest(
            sessionId = "guardian-${System.currentTimeMillis()}",
            inputs = mapOf(InferenceRequest.INPUT_TEXT to textContent),
            params = mapOf("strictness" to config.strictnessLevel)
        )
        
        // 4. Run guardian analysis with timeout
        logger.d(TAG, "Running guardian analysis with ${guardianRunner.getRunnerInfo().name}")
        val guardianResult = kotlinx.coroutines.withTimeoutOrNull(20000L) { // 20 second timeout for analysis
            guardianRunner.run(guardianRequest)
        } ?: run {
            logger.e(TAG, "Guardian analysis execution timed out after 20 seconds")
            return GuardianCheckResult.skip("Guardian analysis execution timeout")
        }
        
        // 5. Convert to check result
        val checkResult = GuardianCheckResult.fromInferenceResult(guardianResult, config.failureStrategy)
        
        // 6. Log the result
        logGuardianResult(checkResult, isInput)
        
        return checkResult
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
    internal fun extractTextFromResult(result: InferenceResult): String? {
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
    
    /**
     * Create overlapping micro-batches for progressive analysis
     */
    /**
     * Create overlapping micro-batches for progressive analysis.
     * Note: This uses a simple word splitting and indexOf logic. For content with complex
     * spacing or repeated words, a more advanced tokenizer would be more robust.
     */
    private fun createMicroBatches(
        content: String,
        batchSize: Int,
        overlap: Int
    ): List<ContentBatch> {
        val batches = mutableListOf<ContentBatch>()
        val words = content.split(Regex("\\s+")).filter { it.isNotBlank() }

        if (words.isEmpty()) {
            return emptyList()
        }

        var searchIndex = 0
        val wordIndices = words.map { word ->
            val startIndex = content.indexOf(word, searchIndex)
            if (startIndex != -1) {
                searchIndex = startIndex + word.length
            }
            startIndex to word
        }

        var startWordIndex = 0
        while (startWordIndex < words.size) {
            val endWordIndex = (startWordIndex + batchSize - 1).coerceAtMost(words.size - 1)

            val startCharIndex = wordIndices.getOrNull(startWordIndex)?.first ?: -1
            val endWord = wordIndices.getOrNull(endWordIndex)
            val endCharIndex = if (endWord != null && endWord.first != -1) endWord.first + endWord.second.length else -1

            if (startCharIndex != -1 && endCharIndex != -1) {
                 val batchContent = content.substring(startCharIndex, endCharIndex)
                 batches.add(ContentBatch(startCharIndex, endCharIndex, batchContent))
            }

            // Move to next batch with overlap
            startWordIndex += (batchSize - overlap).coerceAtLeast(1)
        }

        return batches
    }

    /**
     * Smart word masking that preserves context (ported from client).
     */
    private fun applySmartMasking(word: String): String {
        return when (word.length) {
            1 -> "*"
            2 -> "${word.first()}*"
            else -> "${word.first()}${"*".repeat(word.length - 2)}${word.last()}"
        }
    }
}

/**
 * Represents a content batch for progressive analysis
 */
data class ContentBatch(
    val startIndex: Int,
    val endIndex: Int,
    val content: String
)

/**
 * Result of progressive Guardian masking analysis
 */
data class GuardianMaskingResult(
    val startIndex: Int,
    val endIndex: Int,
    val originalText: String,
    val maskedText: String,
    val violationCategories: List<GuardianCategory>,
    val riskScore: Double
)