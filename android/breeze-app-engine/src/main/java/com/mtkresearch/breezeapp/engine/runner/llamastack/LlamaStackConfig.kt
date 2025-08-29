package com.mtkresearch.breezeapp.engine.runner.llamastack

import com.mtkresearch.breezeapp.engine.runner.core.ValidationResult
import kotlinx.serialization.Serializable

@Serializable
data class LlamaStackConfig(
    val endpoint: String = "https://api.llamastack.ai",
    val apiKey: String? = null,
    val modelId: String = "llama-3.2-90b-vision-instruct",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val enableVision: Boolean = true,
    val enableRAG: Boolean = false,
    val enableAgents: Boolean = false,
    val timeout: Long = 30_000L,
    val retryAttempts: Int = 3,
    val retryDelayMs: Long = 1000L
) {
    companion object {
        
        fun fromParams(params: Map<String, Any>, defaultModelId: String): LlamaStackConfig {
            return LlamaStackConfig(
                endpoint = params["endpoint"] as? String ?: "https://api.llamastack.ai",
                apiKey = params["api_key"] as? String,
                modelId = params["model_id"] as? String ?: defaultModelId,
                temperature = (params["temperature"] as? Number)?.toFloat() ?: 0.0f,
                maxTokens = (params["max_tokens"] as? Number)?.toInt() ?: 128,
                enableVision = params["enable_vision"] as? Boolean ?: false,
                enableRAG = params["enable_rag"] as? Boolean ?: false,
                enableAgents = params["enable_agents"] as? Boolean ?: false,
                timeout = (params["timeout"] as? Number)?.toLong() ?: 30_000L,
                retryAttempts = (params["retry_attempts"] as? Number)?.toInt() ?: 3,
                retryDelayMs = (params["retry_delay_ms"] as? Number)?.toLong() ?: 1000L
            )
        }
        
        fun production(apiKey: String) = LlamaStackConfig(
            endpoint = "https://api.llamastack.ai",
            apiKey = apiKey,
            modelId = "llama3.2:3b",
            enableVision = false,
            temperature = 0.0f,
            maxTokens = 128
        )
        
        fun development() = LlamaStackConfig(
            endpoint = "http://10.0.2.2:8321",  // Android emulator host access
            modelId = "llama3.2:3b",
            enableVision = false,
            temperature = 0.0f,
            maxTokens = 128
        )
        
        fun visionEnabled(apiKey: String) = LlamaStackConfig(
            endpoint = "https://api.llamastack.ai",
            apiKey = apiKey,
            modelId = "llama-3.2-90b-vision-instruct",
            enableVision = true,
            temperature = 0.7f
        )
    }
    
    fun isVisionCapable(): Boolean = enableVision && modelId.contains("vision", ignoreCase = true)
    
    fun isRAGCapable(): Boolean = enableRAG
    
    fun isAgentCapable(): Boolean = enableAgents
    
    fun validateConfiguration(): ValidationResult {
        if (endpoint.isBlank()) {
            return ValidationResult.invalid("Endpoint URL cannot be empty")
        }
        
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            return ValidationResult.invalid("Endpoint must be a valid HTTP(S) URL")
        }
        
        if (temperature < 0.0f || temperature > 2.0f) {
            return ValidationResult.invalid("Temperature must be between 0.0 and 2.0")
        }
        
        if (maxTokens < 1 || maxTokens > 32768) {
            return ValidationResult.invalid("Max tokens must be between 1 and 32768")
        }
        
        if (timeout < 1000L || timeout > 300_000L) {
            return ValidationResult.invalid("Timeout must be between 1 second and 5 minutes")
        }
        
        return ValidationResult.valid()
    }
}

