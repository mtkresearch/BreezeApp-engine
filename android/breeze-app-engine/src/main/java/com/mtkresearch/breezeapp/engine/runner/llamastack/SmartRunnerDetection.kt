package com.mtkresearch.breezeapp.engine.runner.llamastack

import android.util.Log

/**
 * Smart Runner Detection Utility
 * 
 * Provides intelligent runner selection based on endpoint analysis.
 * This helps users automatically get the right runner for their endpoint.
 */
object SmartRunnerDetection {
    
    private const val TAG = "SmartRunnerDetection"
    
    /**
     * Analyze endpoint and suggest optimal runner
     */
    fun suggestOptimalRunner(endpoint: String?): RunnerSuggestion {
        if (endpoint.isNullOrBlank()) {
            return RunnerSuggestion.noSuggestion()
        }
        
        val lowerEndpoint = endpoint.lowercase()
        
        return when {
            lowerEndpoint.contains("openrouter.ai") -> {
                RunnerSuggestion(
                    suggestedRunner = "OpenRouterLLMRunner",
                    confidence = 0.95,
                    reason = "OpenRouter endpoints work best with OpenRouterLLMRunner for optimal features and compatibility"
                )
            }
            
            lowerEndpoint.contains("api.openai.com") -> {
                RunnerSuggestion(
                    suggestedRunner = "OpenAIRunner", // If it exists
                    confidence = 0.90,
                    reason = "OpenAI endpoints require dedicated OpenAI runner"
                )
            }
            
            lowerEndpoint.contains("llamastack.ai") || lowerEndpoint.contains("meta.ai") -> {
                RunnerSuggestion(
                    suggestedRunner = "LlamaStackRunner",
                    confidence = 0.95,
                    reason = "LlamaStack endpoints are natively supported by LlamaStackRunner"
                )
            }
            
            // Additional AI service endpoints
            lowerEndpoint.contains("groq.com") -> {
                RunnerSuggestion(
                    suggestedRunner = "LlamaStackRunner",
                    confidence = 0.75,
                    reason = "Groq endpoints are OpenAI-compatible",
                    warning = "Groq-specific features may not be fully supported"
                )
            }
            
            lowerEndpoint.contains("anthropic.com") -> {
                RunnerSuggestion(
                    suggestedRunner = "ClaudeRunner", // If it exists
                    confidence = 0.90,
                    reason = "Anthropic endpoints require dedicated Claude runner"
                )
            }
            
            lowerEndpoint.contains("together.xyz") || lowerEndpoint.contains("together.ai") -> {
                RunnerSuggestion(
                    suggestedRunner = "LlamaStackRunner",
                    confidence = 0.75,
                    reason = "Together AI endpoints are OpenAI-compatible"
                )
            }
            
            // Local development endpoints
            lowerEndpoint.contains("localhost") || lowerEndpoint.contains("127.0.0.1") -> {
                RunnerSuggestion(
                    suggestedRunner = "LlamaStackRunner",
                    confidence = 0.80,
                    reason = "Local endpoints typically use OpenAI-compatible APIs"
                )
            }
            
            // Generic OpenAI-compatible APIs
            lowerEndpoint.contains("/v1/chat/completions") || 
            lowerEndpoint.contains("/chat/completions") -> {
                RunnerSuggestion(
                    suggestedRunner = "LlamaStackRunner",
                    confidence = 0.70,
                    reason = "OpenAI-compatible endpoint can work with LlamaStackRunner",
                    warning = "For best results, consider using the specific runner for your API provider"
                )
            }
            
            else -> RunnerSuggestion.noSuggestion()
        }
    }
    
    /**
     * Check if endpoint is compatible with specific runner
     */
    fun isEndpointCompatible(endpoint: String?, runnerName: String): CompatibilityResult {
        if (endpoint.isNullOrBlank()) {
            return CompatibilityResult.unknown()
        }
        
        val lowerEndpoint = endpoint.lowercase()
        
        return when (runnerName) {
            "LlamaStackRunner" -> {
                when {
                    lowerEndpoint.contains("llamastack.ai") -> 
                        CompatibilityResult.perfect("Native LlamaStack endpoint")
                    
                    lowerEndpoint.contains("openrouter.ai") -> 
                        CompatibilityResult.compatible("OpenRouter is OpenAI-compatible", 
                            "Consider OpenRouterLLMRunner for better feature support")
                    
                    lowerEndpoint.contains("api.openai.com") -> 
                        CompatibilityResult.incompatible("OpenAI endpoints require dedicated runner")
                    
                    lowerEndpoint.contains("groq.com") -> 
                        CompatibilityResult.compatible("Groq is OpenAI-compatible", 
                            "Some Groq-specific features may not be supported")
                    
                    lowerEndpoint.contains("together.xyz") || lowerEndpoint.contains("together.ai") -> 
                        CompatibilityResult.compatible("Together AI is OpenAI-compatible")
                    
                    lowerEndpoint.contains("localhost") || lowerEndpoint.contains("127.0.0.1") -> 
                        CompatibilityResult.compatible("Local development endpoint")
                    
                    lowerEndpoint.contains("/v1/chat/completions") -> 
                        CompatibilityResult.compatible("Generic OpenAI-compatible endpoint")
                    
                    lowerEndpoint.contains("anthropic.com") -> 
                        CompatibilityResult.incompatible("Anthropic API requires dedicated Claude runner")
                    
                    else -> CompatibilityResult.unknown()
                }
            }
            
            "OpenRouterLLMRunner" -> {
                when {
                    lowerEndpoint.contains("openrouter.ai") -> 
                        CompatibilityResult.perfect("Native OpenRouter endpoint")
                    
                    else -> CompatibilityResult.incompatible("OpenRouterLLMRunner is specific to OpenRouter API")
                }
            }
            
            else -> CompatibilityResult.unknown()
        }
    }
    
    /**
     * Log compatibility warnings and suggestions
     */
    fun logCompatibilityAnalysis(endpoint: String?, selectedRunner: String) {
        val suggestion = suggestOptimalRunner(endpoint)
        val compatibility = isEndpointCompatible(endpoint, selectedRunner)
        
        when {
            suggestion.hasValidSuggestion() && suggestion.suggestedRunner != selectedRunner -> {
                Log.w(TAG, "⚠️ Suboptimal runner selection detected:")
                Log.w(TAG, "   Endpoint: $endpoint")
                Log.w(TAG, "   Selected: $selectedRunner")
                Log.w(TAG, "   Suggested: ${suggestion.suggestedRunner} (${suggestion.confidence * 100}% confidence)")
                Log.w(TAG, "   Reason: ${suggestion.reason}")
            }
            
            compatibility.isIncompatible() -> {
                Log.e(TAG, "❌ Incompatible runner-endpoint combination:")
                Log.e(TAG, "   Endpoint: $endpoint")
                Log.e(TAG, "   Runner: $selectedRunner")
                Log.e(TAG, "   Issue: ${compatibility.message}")
            }
            
            compatibility.hasWarning() -> {
                Log.w(TAG, "⚠️ Compatibility warning:")
                Log.w(TAG, "   Endpoint: $endpoint")
                Log.w(TAG, "   Runner: $selectedRunner")
                Log.w(TAG, "   Warning: ${compatibility.warning}")
            }
            
            compatibility.isPerfect() -> {
                Log.d(TAG, "✅ Optimal runner-endpoint combination: $selectedRunner with $endpoint")
            }
        }
    }
}

/**
 * Runner suggestion result
 */
data class RunnerSuggestion(
    val suggestedRunner: String?,
    val confidence: Double = 0.0,
    val reason: String = "",
    val warning: String? = null
) {
    fun hasValidSuggestion(): Boolean = !suggestedRunner.isNullOrBlank() && confidence > 0.5
    
    companion object {
        fun noSuggestion() = RunnerSuggestion(null, 0.0, "No specific recommendation")
    }
}

/**
 * Compatibility analysis result
 */
data class CompatibilityResult(
    val level: CompatibilityLevel,
    val message: String,
    val warning: String? = null
) {
    fun isPerfect(): Boolean = level == CompatibilityLevel.PERFECT
    fun isCompatible(): Boolean = level == CompatibilityLevel.COMPATIBLE || level == CompatibilityLevel.PERFECT
    fun isIncompatible(): Boolean = level == CompatibilityLevel.INCOMPATIBLE
    fun hasWarning(): Boolean = !warning.isNullOrBlank()
    
    companion object {
        fun perfect(message: String) = CompatibilityResult(CompatibilityLevel.PERFECT, message)
        fun compatible(message: String, warning: String? = null) = CompatibilityResult(CompatibilityLevel.COMPATIBLE, message, warning)
        fun incompatible(message: String) = CompatibilityResult(CompatibilityLevel.INCOMPATIBLE, message)
        fun unknown() = CompatibilityResult(CompatibilityLevel.UNKNOWN, "Compatibility unknown")
    }
}

enum class CompatibilityLevel {
    PERFECT,      // Ideal match
    COMPATIBLE,   // Works but not optimal
    INCOMPATIBLE, // Won't work
    UNKNOWN       // Can't determine
}