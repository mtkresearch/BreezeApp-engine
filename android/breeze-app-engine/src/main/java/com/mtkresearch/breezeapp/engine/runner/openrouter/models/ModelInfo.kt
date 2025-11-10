package com.mtkresearch.breezeapp.engine.runner.openrouter.models

import org.json.JSONObject

/**
 * Model information from OpenRouter API
 *
 * Represents metadata about an available model including pricing,
 * context length, and capabilities.
 */
data class ModelInfo(
    val id: String,                    // e.g. "openai/gpt-4-turbo"
    val name: String,                  // e.g. "GPT-4 Turbo"
    val description: String,           // Human-readable description
    val contextLength: Int?,           // Maximum context window
    val pricing: ModelPricing,         // Cost information
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Format display name with pricing and context info
     */
    fun getDisplayName(): String {
        val priceStr = when {
            pricing.prompt == 0.0 -> "Free"
            pricing.prompt < 0.000001 -> "~Free"
            else -> "$${"%.6f".format(pricing.prompt)}/1K"
        }
        val contextStr = contextLength?.let { "${it / 1000}K ctx" } ?: "? ctx"
        return "$name ($priceStr, $contextStr)"
    }

    /**
     * Get shorter display name for dropdown
     */
    fun getShortDisplayName(): String {
        val priceStr = if (pricing.prompt == 0.0) "Free" else "$${pricing.prompt}"
        return "$name ($priceStr)"
    }

    companion object {
        /**
         * Parse ModelInfo from OpenRouter API JSON response
         */
        fun fromJson(json: JSONObject): ModelInfo? {
            return try {
                val id = json.getString("id")
                val name = json.getString("name")
                val description = json.optString("description", "")
                val contextLength = json.optInt("context_length", 0).takeIf { it > 0 }

                val pricingJson = json.getJSONObject("pricing")
                val pricing = ModelPricing.fromJson(pricingJson)

                ModelInfo(
                    id = id,
                    name = name,
                    description = description,
                    contextLength = contextLength,
                    pricing = pricing
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Model pricing information (per 1K tokens)
 */
data class ModelPricing(
    val prompt: Double,        // Cost per 1K prompt tokens
    val completion: Double     // Cost per 1K completion tokens
) {
    companion object {
        fun fromJson(json: JSONObject): ModelPricing {
            // OpenRouter returns prices as strings or numbers
            val promptPrice = json.optString("prompt", "0")
                .toDoubleOrNull() ?: json.optDouble("prompt", 0.0)
            val completionPrice = json.optString("completion", "0")
                .toDoubleOrNull() ?: json.optDouble("completion", 0.0)

            return ModelPricing(
                prompt = promptPrice,
                completion = completionPrice
            )
        }
    }
}
