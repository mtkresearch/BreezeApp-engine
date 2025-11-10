package com.mtkresearch.breezeapp.engine.runner.openrouter.models

import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Service for fetching and caching OpenRouter model information
 *
 * Handles API calls to OpenRouter /models endpoint with:
 * - 24-hour caching to reduce API calls
 * - Price-based filtering
 * - Graceful error handling
 * - Offline fallback to cached data
 */
class OpenRouterModelFetcher(
    private val prefs: SharedPreferences,
    private val baseUrl: String = "https://openrouter.ai/api/v1"
) {
    companion object {
        private const val TAG = "ModelFetcher"
        private const val CACHE_KEY_MODELS = "cached_models_json"
        private const val CACHE_KEY_TIMESTAMP = "cached_models_timestamp"
        private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val REQUEST_TIMEOUT_MS = 15000
    }

    /**
     * Fetch models from OpenRouter API with caching
     *
     * @param apiKey OpenRouter API key
     * @param forceRefresh Skip cache and fetch fresh data
     * @return Result with list of models or error
     */
    suspend fun fetchModels(
        apiKey: String,
        forceRefresh: Boolean = false
    ): Result<List<ModelInfo>> = withContext(Dispatchers.IO) {
        try {
            // Check cache first (unless force refresh)
            if (!forceRefresh) {
                val cached = getCachedModels()
                if (cached.isNotEmpty() && !isCacheExpired()) {
                    Log.d(TAG, "Returning ${cached.size} models from cache")
                    return@withContext Result.success(cached)
                }
            }

            // Validate API key
            if (apiKey.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("API key is required")
                )
            }

            // Fetch from API
            Log.d(TAG, "Fetching models from OpenRouter API...")
            val models = fetchModelsFromApi(apiKey)

            // Cache the results
            cacheModels(models)
            Log.d(TAG, "Successfully fetched and cached ${models.size} models")

            Result.success(models)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch models", e)

            // Try to return cached data as fallback
            val cached = getCachedModels()
            if (cached.isNotEmpty()) {
                Log.w(TAG, "Returning stale cached data (${cached.size} models)")
                return@withContext Result.success(cached)
            }

            Result.failure(e)
        }
    }

    /**
     * Filter models by maximum price
     *
     * @param models List of models to filter
     * @param maxPrice Maximum price per 1K tokens (0.0 = free only)
     * @return Filtered and sorted list of models
     */
    fun filterByPrice(models: List<ModelInfo>, maxPrice: Double): List<ModelInfo> {
        return models
            .filter { it.pricing.prompt <= maxPrice }
            .sortedBy { it.pricing.prompt }
    }

    /**
     * Get models filtered by price (convenience method)
     */
    suspend fun getFilteredModels(
        apiKey: String,
        maxPrice: Double,
        forceRefresh: Boolean = false
    ): Result<List<ModelInfo>> {
        return fetchModels(apiKey, forceRefresh).map { models ->
            filterByPrice(models, maxPrice)
        }
    }

    /**
     * Get cached models from SharedPreferences
     */
    private fun getCachedModels(): List<ModelInfo> {
        return try {
            val json = prefs.getString(CACHE_KEY_MODELS, null) ?: return emptyList()
            val jsonArray = JSONArray(json)
            val models = mutableListOf<ModelInfo>()

            for (i in 0 until jsonArray.length()) {
                val modelJson = jsonArray.getJSONObject(i)
                ModelInfo.fromJson(modelJson)?.let { models.add(it) }
            }

            models
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse cached models", e)
            emptyList()
        }
    }

    /**
     * Check if cache has expired
     */
    private fun isCacheExpired(): Boolean {
        val timestamp = prefs.getLong(CACHE_KEY_TIMESTAMP, 0L)
        val age = System.currentTimeMillis() - timestamp
        return age > CACHE_DURATION_MS
    }

    /**
     * Cache models to SharedPreferences
     */
    private fun cacheModels(models: List<ModelInfo>) {
        try {
            val jsonArray = JSONArray()
            models.forEach { model ->
                val modelJson = JSONObject().apply {
                    put("id", model.id)
                    put("name", model.name)
                    put("description", model.description)
                    put("context_length", model.contextLength ?: 0)
                    put("pricing", JSONObject().apply {
                        put("prompt", model.pricing.prompt)
                        put("completion", model.pricing.completion)
                    })
                }
                jsonArray.put(modelJson)
            }

            prefs.edit()
                .putString(CACHE_KEY_MODELS, jsonArray.toString())
                .putLong(CACHE_KEY_TIMESTAMP, System.currentTimeMillis())
                .apply()

            Log.d(TAG, "Cached ${models.size} models")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache models", e)
        }
    }

    /**
     * Fetch models from OpenRouter API
     */
    private fun fetchModelsFromApi(apiKey: String): List<ModelInfo> {
        val url = URL("$baseUrl/models")
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = REQUEST_TIMEOUT_MS
                readTimeout = REQUEST_TIMEOUT_MS
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw IOException("HTTP $responseCode: $errorBody")
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            return parseModelsResponse(responseBody)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Parse models from API response JSON
     */
    private fun parseModelsResponse(responseBody: String): List<ModelInfo> {
        val models = mutableListOf<ModelInfo>()

        try {
            val json = JSONObject(responseBody)
            val dataArray = json.getJSONArray("data")

            for (i in 0 until dataArray.length()) {
                val modelJson = dataArray.getJSONObject(i)
                ModelInfo.fromJson(modelJson)?.let { models.add(it) }
            }

            Log.d(TAG, "Parsed ${models.size} models from API response")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse models response", e)
            throw IOException("Failed to parse models: ${e.message}", e)
        }

        return models
    }

    /**
     * Clear cached models
     */
    fun clearCache() {
        prefs.edit()
            .remove(CACHE_KEY_MODELS)
            .remove(CACHE_KEY_TIMESTAMP)
            .apply()
        Log.d(TAG, "Cache cleared")
    }
}
