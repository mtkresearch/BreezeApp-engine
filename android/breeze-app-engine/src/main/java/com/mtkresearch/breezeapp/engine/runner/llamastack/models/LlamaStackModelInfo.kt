package com.mtkresearch.breezeapp.engine.runner.llamastack.models

import org.json.JSONObject

/**
 * Data class representing a LlamaStack model
 *
 * Based on LlamaStack /v1/models API response format:
 * {
 *   "identifier": "ollama/llama3.2:3b",
 *   "provider_resource_id": "llama3.2:3b",
 *   "provider_id": "ollama",
 *   "type": "model",
 *   "metadata": {},
 *   "model_type": "llm"
 * }
 */
data class LlamaStackModelInfo(
    /** Full identifier (e.g., "ollama/llama3.2:3b") */
    val identifier: String,
    /** Provider's resource ID - use this for inference requests (e.g., "llama3.2:3b") */
    val providerResourceId: String,
    /** Provider name (e.g., "ollama", "bedrock") */
    val providerId: String,
    /** Model type (e.g., "llm", "embedding") */
    val modelType: String,
    /** Optional metadata like embedding_dimension, context_length */
    val metadata: Map<String, Any> = emptyMap()
) {
    /** Display name for UI (provider/resource_id) */
    val displayName: String
        get() = identifier

    /** Description based on provider and type */
    val description: String
        get() = buildString {
            append("Provider: $providerId")
            metadata["context_length"]?.let { append(" | Context: $it") }
            metadata["embedding_dimension"]?.let { append(" | Embedding: $it") }
        }

    /** Whether this is an LLM model (vs embedding) */
    val isLlm: Boolean
        get() = modelType == "llm"

    companion object {
        /**
         * Parse a model from LlamaStack API JSON response
         */
        fun fromJson(json: JSONObject): LlamaStackModelInfo? {
            return try {
                val metadata = mutableMapOf<String, Any>()
                json.optJSONObject("metadata")?.let { metaJson ->
                    metaJson.keys().forEach { key ->
                        metadata[key] = metaJson.get(key)
                    }
                }

                LlamaStackModelInfo(
                    identifier = json.getString("identifier"),
                    providerResourceId = json.getString("provider_resource_id"),
                    providerId = json.getString("provider_id"),
                    modelType = json.optString("model_type", "llm"),
                    metadata = metadata
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
