package com.mtkresearch.breezeapp.engine.runner.llamastack.models

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
 * Service for fetching and caching LlamaStack model information
 *
 * Fetches available models from LlamaStack server's /v1/models endpoint.
 * Supports caching to reduce API calls and provide offline fallback.
 */
class LlamaStackModelFetcher(
    private val prefs: SharedPreferences
) {
    companion object {
        private const val TAG = "LlamaStackModelFetcher"
        private const val CACHE_KEY_MODELS = "llamastack_cached_models_json"
        private const val CACHE_KEY_TIMESTAMP = "llamastack_cached_models_timestamp"
        private const val CACHE_KEY_ENDPOINT = "llamastack_cached_endpoint"
        private const val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutes (shorter than OpenRouter since it's local)
        private const val REQUEST_TIMEOUT_MS = 10000
    }

    /**
     * Fetch models from LlamaStack server
     *
     * @param endpoint LlamaStack server endpoint (e.g., "http://127.0.0.1:8321")
     * @param apiKey Optional API key for authentication
     * @param forceRefresh Skip cache and fetch fresh data
     * @return Result with list of LLM models or error
     */
    suspend fun fetchModels(
        endpoint: String,
        apiKey: String = "",
        forceRefresh: Boolean = false
    ): Result<List<LlamaStackModelInfo>> = withContext(Dispatchers.IO) {
        try {
            // Validate endpoint
            if (endpoint.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Endpoint is required")
                )
            }

            // Check cache first (unless force refresh or endpoint changed)
            if (!forceRefresh) {
                val cachedEndpoint = prefs.getString(CACHE_KEY_ENDPOINT, null)
                if (cachedEndpoint == endpoint) {
                    val cached = getCachedModels()
                    if (cached.isNotEmpty() && !isCacheExpired()) {
                        Log.d(TAG, "Returning ${cached.size} models from cache")
                        return@withContext Result.success(cached)
                    }
                }
            }

            // Fetch from API
            Log.d(TAG, "Fetching models from LlamaStack at $endpoint...")
            val allModels = fetchModelsFromApi(endpoint, apiKey)

            // Filter to LLM models only
            val llmModels = allModels.filter { it.isLlm }
            Log.d(TAG, "Found ${llmModels.size} LLM models out of ${allModels.size} total")

            // Cache the results
            cacheModels(llmModels, endpoint)
            Log.d(TAG, "Successfully fetched and cached ${llmModels.size} models")

            Result.success(llmModels)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch models from $endpoint", e)

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
     * Get cached models from SharedPreferences
     */
    private fun getCachedModels(): List<LlamaStackModelInfo> {
        return try {
            val json = prefs.getString(CACHE_KEY_MODELS, null) ?: return emptyList()
            val jsonArray = JSONArray(json)
            val models = mutableListOf<LlamaStackModelInfo>()

            for (i in 0 until jsonArray.length()) {
                val modelJson = jsonArray.getJSONObject(i)
                LlamaStackModelInfo.fromJson(modelJson)?.let { models.add(it) }
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
    private fun cacheModels(models: List<LlamaStackModelInfo>, endpoint: String) {
        try {
            val jsonArray = JSONArray()
            models.forEach { model ->
                val modelJson = JSONObject().apply {
                    put("identifier", model.identifier)
                    put("provider_resource_id", model.providerResourceId)
                    put("provider_id", model.providerId)
                    put("model_type", model.modelType)
                    put("metadata", JSONObject(model.metadata))
                }
                jsonArray.put(modelJson)
            }

            prefs.edit()
                .putString(CACHE_KEY_MODELS, jsonArray.toString())
                .putString(CACHE_KEY_ENDPOINT, endpoint)
                .putLong(CACHE_KEY_TIMESTAMP, System.currentTimeMillis())
                .apply()

            Log.d(TAG, "Cached ${models.size} models for endpoint $endpoint")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache models", e)
        }
    }

    /**
     * Fetch models from LlamaStack API
     */
    private fun fetchModelsFromApi(endpoint: String, apiKey: String): List<LlamaStackModelInfo> {
        val normalizedEndpoint = endpoint.trimEnd('/')
        val url = URL("$normalizedEndpoint/v1/models")
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Content-Type", "application/json")
                if (apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }
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
    private fun parseModelsResponse(responseBody: String): List<LlamaStackModelInfo> {
        val models = mutableListOf<LlamaStackModelInfo>()

        try {
            val json = JSONObject(responseBody)
            val dataArray = json.getJSONArray("data")

            for (i in 0 until dataArray.length()) {
                val modelJson = dataArray.getJSONObject(i)
                LlamaStackModelInfo.fromJson(modelJson)?.let { models.add(it) }
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
            .remove(CACHE_KEY_ENDPOINT)
            .apply()
        Log.d(TAG, "Cache cleared")
    }
}
