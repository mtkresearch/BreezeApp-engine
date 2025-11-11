package com.mtkresearch.breezeapp.engine.runner.openrouter.models

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches supported parameters for a specific OpenRouter model
 *
 * Uses OpenRouter's /parameters/{author}/{slug} endpoint to determine
 * which parameters a model actually supports.
 */
class ModelParametersFetcher(
    private val baseUrl: String = "https://openrouter.ai/api/v1"
) {
    companion object {
        private const val TAG = "ModelParamsFetcher"
        private const val REQUEST_TIMEOUT_MS = 10000
    }

    /**
     * Fetch supported parameters for a specific model
     *
     * @param modelId Full model ID (e.g., "google/gemini-flash-1.5:free")
     * @param apiKey OpenRouter API key
     * @return List of supported parameter names, or null if fetch fails
     */
    suspend fun fetchSupportedParameters(
        modelId: String,
        apiKey: String
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            // Parse model ID into author/slug
            val (author, slug) = parseModelId(modelId)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Invalid model ID format: $modelId")
                )

            Log.d(TAG, "Fetching parameters for model: $author/$slug")

            // Fetch from API
            val url = URL("$baseUrl/parameters/$author/$slug")
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
                    Log.w(TAG, "Failed to fetch parameters: HTTP $responseCode - $errorBody")
                    return@withContext Result.failure(
                        Exception("HTTP $responseCode: $errorBody")
                    )
                }

                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                val parameters = parseParametersResponse(responseBody)

                Log.d(TAG, "Model $modelId supports: $parameters")
                Result.success(parameters)
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching model parameters", e)
            Result.failure(e)
        }
    }

    /**
     * Parse model ID into author and slug
     *
     * Examples:
     * - "google/gemini-flash-1.5:free" → ("google", "gemini-flash-1.5")
     * - "openai/gpt-4-turbo" → ("openai", "gpt-4-turbo")
     * - "meta-llama/llama-3.1-8b-instruct:free" → ("meta-llama", "llama-3.1-8b-instruct")
     */
    private fun parseModelId(modelId: String): Pair<String, String>? {
        val parts = modelId.split("/")
        if (parts.size != 2) return null

        val author = parts[0]
        val slugWithVariant = parts[1]

        // Remove ":free" or other variants
        val slug = slugWithVariant.substringBefore(":")

        return author to slug
    }

    /**
     * Parse API response to extract supported parameters
     */
    private fun parseParametersResponse(responseBody: String): List<String> {
        return try {
            val json = JSONObject(responseBody)
            val data = json.getJSONObject("data")
            val paramsArray = data.getJSONArray("supported_parameters")

            List(paramsArray.length()) { i ->
                paramsArray.getString(i)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse parameters response", e)
            emptyList()
        }
    }
}
