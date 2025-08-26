package com.mtkresearch.breezeapp.engine.runner.guardian

import android.content.Context
import com.mtkresearch.breezeapp.engine.model.InferenceResult
import com.mtkresearch.breezeapp.engine.model.RunnerError

/**
 * Result of a guardian check operation.
 * 
 * Encapsulates the outcome of guardian analysis and provides utilities
 * for applying the result to the AI pipeline.
 */
sealed class GuardianCheckResult {
    
    /**
     * Guardian check was skipped (guardian not available or disabled).
     */
    data class Skipped(val reason: String) : GuardianCheckResult()
    
    /**
     * Guardian check passed - content is safe.
     */
    data class Passed(val analysisResult: GuardianAnalysisResult) : GuardianCheckResult()
    
    /**
     * Guardian check failed - content violates safety policies.
     */
    data class Failed(
        val analysisResult: GuardianAnalysisResult,
        val strategy: GuardianFailureStrategy
    ) : GuardianCheckResult()
    
    /**
     * Check if this result should block processing.
     */
    fun shouldBlock(): Boolean = when (this) {
        is Skipped -> false
        is Passed -> false
        is Failed -> when (strategy) {
            GuardianFailureStrategy.BLOCK -> true
            GuardianFailureStrategy.WARN -> false
            GuardianFailureStrategy.FILTER -> false
        }
    }
    
    /**
     * Check if this result should apply filtering.
     */
    fun shouldFilter(): Boolean = when (this) {
        is Failed -> strategy == GuardianFailureStrategy.FILTER
        else -> false
    }
    
    /**
     * Check if this result should log a warning.
     */
    fun shouldWarn(): Boolean = when (this) {
        is Failed -> strategy == GuardianFailureStrategy.WARN
        else -> false
    }
    
    /**
     * Convert to InferenceResult for blocking scenarios.
     */
    fun toInferenceResult(context: Context? = null): InferenceResult = when (this) {
        is Skipped -> InferenceResult.error(
            RunnerError("G001", "Guardian check skipped: $reason")
        )
        is Passed -> InferenceResult.error(
            RunnerError("G002", "Internal error: Guardian passed but converted to error result")
        )
        is Failed -> {
            val messageProvider = context?.let { GuardianMessageProvider(it) }
            val primaryCategory = analysisResult.categories.firstOrNull() ?: GuardianCategory.TOXICITY
            
            val userMessage = messageProvider?.getGuardianMessage(primaryCategory, true) 
                ?: "您的訊息包含不適當的內容，請修改後重新發送"
            
            val suggestion = messageProvider?.getGuardianSuggestion(primaryCategory) 
                ?: "請使用更積極正面的表達方式"
            
            val reason = messageProvider?.getGuardianReason(primaryCategory, analysisResult.riskScore) 
                ?: "檢測到有害內容 (風險分數: ${String.format("%.2f", analysisResult.riskScore)})"
            
            InferenceResult.error(
                RunnerError(
                    "G100", 
                    userMessage,
                    false
                )
            )
        }
    }
    
    /**
     * Apply guardian result to an existing InferenceResult.
     * Used for output filtering scenarios.
     */
    fun applyToResult(originalResult: InferenceResult): InferenceResult {
        return when (this) {
            is Skipped, is Passed -> originalResult
            
            is Failed -> when (strategy) {
                GuardianFailureStrategy.BLOCK -> toInferenceResult(null)
                
                GuardianFailureStrategy.WARN -> {
                    // Return original result but add guardian warning to metadata
                    originalResult.copy(
                        metadata = originalResult.metadata + mapOf(
                            "guardian_warning" to true,
                            "guardian_categories" to analysisResult.categories.map { it.name },
                            "risk_score" to analysisResult.riskScore
                        )
                    )
                }
                
                GuardianFailureStrategy.FILTER -> {
                    // Apply content filtering if available
                    val filteredText = analysisResult.filteredText
                    if (filteredText != null && originalResult.error == null) {
                        // Replace the main output text with filtered version
                        val filteredOutputs = originalResult.outputs.toMutableMap()
                        filteredOutputs["text"] = filteredText
                        
                        originalResult.copy(
                            outputs = filteredOutputs,
                            metadata = originalResult.metadata + mapOf(
                                "guardian_filtered" to true,
                                "guardian_categories" to analysisResult.categories.map { it.name },
                                "risk_score" to analysisResult.riskScore
                            )
                        )
                    } else {
                        // Filtering not possible, block the result
                        toInferenceResult(null)
                    }
                }
            }
        }
    }
    
    companion object {
        
        /**
         * Create a skipped result.
         */
        fun skip(reason: String): GuardianCheckResult = Skipped(reason)
        
        /**
         * Create result from InferenceResult returned by guardian runner.
         */
        fun fromInferenceResult(
            guardianResult: InferenceResult, 
            strategy: GuardianFailureStrategy
        ): GuardianCheckResult {
            
            if (guardianResult.error != null) {
                return Skipped("Guardian runner failed: ${guardianResult.error.message}")
            }
            
            // Parse guardian analysis result from outputs
            val outputs = guardianResult.outputs
            val status = parseGuardianStatus(outputs["safety_status"] as? String)
            val riskScore = (outputs["risk_score"] as? Number)?.toDouble() ?: 0.0
            val categories = parseGuardianCategories(outputs["risk_categories"] as? List<*>)
            val action = parseGuardianAction(outputs["action_required"] as? String)
            val filteredText = outputs["filtered_text"] as? String
            
            val analysisResult = GuardianAnalysisResult(
                status = status,
                riskScore = riskScore,
                categories = categories,
                action = action,
                filteredText = filteredText,
                details = guardianResult.metadata
            )
            
            return when (status) {
                GuardianStatus.SAFE -> Passed(analysisResult)
                GuardianStatus.WARNING -> Failed(analysisResult, strategy)
                GuardianStatus.BLOCKED -> Failed(analysisResult, strategy)
            }
        }
        
        private fun parseGuardianStatus(statusStr: String?): GuardianStatus {
            return when (statusStr?.uppercase()) {
                "SAFE" -> GuardianStatus.SAFE
                "WARNING" -> GuardianStatus.WARNING  
                "BLOCKED" -> GuardianStatus.BLOCKED
                else -> GuardianStatus.BLOCKED // Default to blocked for safety
            }
        }
        
        private fun parseGuardianCategories(categoriesList: List<*>?): List<GuardianCategory> {
            return categoriesList?.mapNotNull { categoryStr ->
                when (categoryStr.toString().uppercase()) {
                    "HATE_SPEECH" -> GuardianCategory.HATE_SPEECH
                    "SEXUAL_CONTENT" -> GuardianCategory.SEXUAL_CONTENT
                    "VIOLENCE" -> GuardianCategory.VIOLENCE
                    "SELF_HARM" -> GuardianCategory.SELF_HARM
                    "SPAM" -> GuardianCategory.SPAM
                    "PII" -> GuardianCategory.PII
                    "TOXICITY" -> GuardianCategory.TOXICITY
                    "UNSAFE_CONTENT" -> GuardianCategory.UNSAFE_CONTENT
                    else -> GuardianCategory.UNKNOWN
                }
            } ?: emptyList()
        }
        
        private fun parseGuardianAction(actionStr: String?): GuardianAction {
            return when (actionStr?.uppercase()) {
                "NONE" -> GuardianAction.NONE
                "REVIEW" -> GuardianAction.REVIEW
                "BLOCK" -> GuardianAction.BLOCK
                else -> GuardianAction.BLOCK // Default to block for safety
            }
        }
    }
}