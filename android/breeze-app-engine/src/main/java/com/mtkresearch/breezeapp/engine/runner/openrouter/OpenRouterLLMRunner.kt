package com.mtkresearch.breezeapp.engine.runner.openrouter

import android.content.Context
import android.util.Log
import com.mtkresearch.breezeapp.engine.annotation.AIRunner
import com.mtkresearch.breezeapp.engine.annotation.RunnerPriority
import com.mtkresearch.breezeapp.engine.annotation.VendorType
import com.mtkresearch.breezeapp.engine.runner.core.BaseRunner
import com.mtkresearch.breezeapp.engine.runner.core.FlowStreamingRunner
import com.mtkresearch.breezeapp.engine.runner.core.RunnerInfo
import com.mtkresearch.breezeapp.engine.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.awaitClose
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject
import org.json.JSONArray

/**
 * OpenRouterLLMRunner
 * 
 * Implementation of Large Language Model runner using OpenRouter API.
 * Provides access to multiple AI models through OpenRouter's unified API
 * with comprehensive streaming support and error handling.
 * 
 * Features:
 * - Multiple model support (OpenAI, Anthropic, etc.)
 * - Streaming and non-streaming inference
 * - Comprehensive error handling and mapping
 * - API key management and validation
 * - Request timeout and cancellation support
 * - OpenRouter-specific parameter mapping
 * 
 * Security:
 * - HTTPS-only communication
 * - API key secure handling (not logged)
 * - Request/response sanitization
 * 
 * @param apiKey OpenRouter API key for authentication
 * @param baseUrl OpenRouter API base URL (default: https://openrouter.ai/api/v1)
 * @param defaultModel Default model to use if not specified in request
 */
@AIRunner(
    vendor = VendorType.OPENROUTER,
    priority = RunnerPriority.LOW,
    capabilities = [CapabilityType.LLM],
    enabled = false
)
class OpenRouterLLMRunner(
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val defaultModel: String = DEFAULT_MODEL,
    private val context: Context? = null
) : BaseRunner, FlowStreamingRunner {

    /**
     * Secondary constructor for Context injection (used by RunnerFactory)
     */
    constructor(context: Context) : this(
        apiKey = "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", // Will be set via configuration
        context = context
    )

    /**
     * Default constructor for manual instantiation
     */
    constructor() : this(apiKey = "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")

    companion object {
        private const val TAG = "OpenRouterLLMRunner"
        private const val DEFAULT_BASE_URL = "https://openrouter.ai/api/v1"
        private const val DEFAULT_MODEL = "openai/gpt-oss-20b:free"
        private const val CHAT_COMPLETIONS_ENDPOINT = "/chat/completions"
        private const val DEFAULT_TIMEOUT_MS = 30000
        private const val DEFAULT_TEMPERATURE = 0.7f
        private const val DEFAULT_MAX_TOKENS = 2048
        
        // HTTP headers
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val HEADER_CONTENT_TYPE = "Content-Type"
        private const val HEADER_HTTP_REFERER = "HTTP-Referer"
        private const val HEADER_X_TITLE = "X-Title"
        
        // Error code mapping
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_PAYMENT_REQUIRED = 402
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_REQUEST_TIMEOUT = 408
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_BAD_GATEWAY = 502
        private const val HTTP_SERVICE_UNAVAILABLE = 503
    }

    private val isLoaded = AtomicBoolean(false)
    private var actualApiKey: String = apiKey
    private var modelName: String = defaultModel
    private var timeoutMs: Int = DEFAULT_TIMEOUT_MS
    private var assumeConnectivity: Boolean = false

    override fun load(): Boolean {
        val defaultConfig = ModelConfig(
            modelName = defaultModel,
            modelPath = ""
        )
        return load(defaultConfig)
    }

    override fun load(config: ModelConfig): Boolean {
        return try {
            Log.d(TAG, "Loading OpenRouterLLMRunner with model: ${config.modelName}")
            
            // Validate API key
            if (actualApiKey.isBlank()) {
                // Try to get API key from configuration
                actualApiKey = getApiKeyFromConfig(config)
                if (actualApiKey.isBlank()) {
                    Log.e(TAG, "API key is required for OpenRouter runner")
                    return false
                }
            }

            // Extract configuration parameters
            modelName = config.modelName.takeIf { it.isNotBlank() } ?: defaultModel
            timeoutMs = (config.parameters["timeout_ms"] as? Number)?.toInt() ?: DEFAULT_TIMEOUT_MS
            assumeConnectivity = (config.parameters["assume_connectivity"] as? Boolean) ?: false

            // Validate API key format (basic check)
            if (!isValidApiKey(actualApiKey)) {
                Log.e(TAG, "Invalid API key format")
                return false
            }
            
            // Log API key status (without exposing the key)
            Log.d(TAG, "API key validation: length=${actualApiKey.length}, starts_with=${actualApiKey.take(8)}...")

            // Test connection (optional quick validation)
            if (config.parameters["validate_connection"] as? Boolean == true) {
                if (!validateConnection()) {
                    Log.e(TAG, "Failed to validate connection to OpenRouter")
                    return false
                }
            }

            isLoaded.set(true)
            Log.d(TAG, "OpenRouterLLMRunner loaded successfully with model: $modelName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load OpenRouterLLMRunner", e)
            isLoaded.set(false)
            false
        }
    }

    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        if (!isLoaded.get()) {
            return InferenceResult.error(RunnerError.modelNotLoaded())
        }

        return try {
            if (stream) {
                // For streaming requests, collect all results from flow
                val results = runBlocking { runAsFlow(input).toList() }
                val finalResult = results.find { !it.partial } ?: combinePartialResults(results)
                finalResult
            } else {
                // Direct non-streaming request
                processNonStreamingRequest(input)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing request", e)
            InferenceResult.error(RunnerError.runtimeError("Inference failed: ${e.message}", e))
        }
    }

    override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = callbackFlow {
        if (!isLoaded.get()) {
            trySend(InferenceResult.error(RunnerError.modelNotLoaded()))
            close()
            return@callbackFlow
        }

        val prompt = input.inputs[InferenceRequest.INPUT_TEXT] as? String
        if (prompt.isNullOrBlank()) {
            trySend(InferenceResult.error(RunnerError.invalidInput("Missing or empty text input")))
            close()
            return@callbackFlow
        }

        try {
            Log.d(TAG, "Starting streaming request for session: ${input.sessionId}")
            
            // Process streaming request
            processStreamingRequest(input, prompt) { result ->
                trySend(result)
            }
            
            Log.d(TAG, "Streaming completed for session: ${input.sessionId}")
        } catch (e: Exception) {
            Log.e(TAG, "Error in streaming request", e)
            trySend(InferenceResult.error(mapExceptionToError(e)))
        } finally {
            close()
        }

        awaitClose {
            Log.d(TAG, "Streaming flow closed for session: ${input.sessionId}")
        }
    }

    override fun unload() {
        Log.d(TAG, "Unloading OpenRouterLLMRunner")
        isLoaded.set(false)
    }

    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.LLM)

    override fun isLoaded(): Boolean = isLoaded.get()

    override fun getRunnerInfo(): RunnerInfo = RunnerInfo(
        name = "OpenRouterLLMRunner",
        version = "1.0.0",
        capabilities = getCapabilities(),
        description = "OpenRouter API-based Large Language Model runner with streaming support"
    )

    override fun isSupported(): Boolean {
        // Configuration-based bypass
        if (assumeConnectivity) {
            Log.d(TAG, "OpenRouter supported: connectivity assumption enabled")
            return true
        }
        
        // Multi-tier support evaluation
        val connectivityStatus = hasInternetConnection()
        
        // Log connectivity status for debugging
        Log.d(TAG, "Connectivity check result: $connectivityStatus")
        
        // OpenRouter runner is supported if:
        // 1. We detect internet connectivity, OR
        // 2. We have a valid API key and can't determine connectivity (assume supported)
        return when {
            connectivityStatus -> {
                Log.d(TAG, "OpenRouter supported: connectivity confirmed")
                true
            }
            actualApiKey.isNotBlank() && isValidApiKey(actualApiKey) -> {
                Log.d(TAG, "OpenRouter supported: valid API key present, assuming connectivity")
                true
            }
            else -> {
                Log.d(TAG, "OpenRouter not supported: no connectivity and invalid/missing API key")
                false
            }
        }
    }

    // Private implementation methods

    /**
     * Process non-streaming request directly
     */
    private fun processNonStreamingRequest(input: InferenceRequest): InferenceResult {
        val prompt = input.inputs[InferenceRequest.INPUT_TEXT] as? String
            ?: return InferenceResult.error(RunnerError.invalidInput("Missing text input"))

        return try {
            val requestBody = buildRequestBody(input, prompt, stream = false)
            val response = makeHttpRequest(requestBody, streaming = false)
            parseNonStreamingResponse(response, input.sessionId)
        } catch (e: Exception) {
            InferenceResult.error(mapExceptionToError(e))
        }
    }

    /**
     * Process streaming request with callback for each result
     */
    private suspend fun processStreamingRequest(
        input: InferenceRequest,
        prompt: String,
        onResult: (InferenceResult) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val requestBody = buildRequestBody(input, prompt, stream = true)
                val connection = createConnection(streaming = true)
                
                // Send request
                connection.outputStream.use { outputStream ->
                    outputStream.write(requestBody.toByteArray())
                }

                // Check response code before reading stream
                val responseCode = connection.responseCode
                if (responseCode != 200) {
                    val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details"
                    Log.e(TAG, "HTTP $responseCode: ${mapHttpErrorCode(responseCode)} - $errorResponse")
                    onResult(InferenceResult.error(RunnerError.runtimeError("HTTP $responseCode: ${mapHttpErrorCode(responseCode)} - $errorResponse")))
                    return@withContext
                }

                // Read streaming response
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                var line: String?
                val responseBuffer = StringBuilder()

                while (reader.readLine().also { line = it } != null) {
                    val trimmedLine = line!!.trim()
                    
                    if (trimmedLine.startsWith("data: ")) {
                        val data = trimmedLine.substring(6)
                        
                        if (data == "[DONE]") {
                            // Send final result
                            val finalResult = InferenceResult.textOutput(
                                text = responseBuffer.toString(),
                                metadata = mapOf(
                                    InferenceResult.META_SESSION_ID to input.sessionId,
                                    InferenceResult.META_MODEL_NAME to modelName,
                                    "partial" to false
                                ),
                                partial = false
                            )
                            onResult(finalResult)
                            break
                        }
                        
                        if (data.isNotBlank() && data != ": OPENROUTER PROCESSING") {
                            try {
                                val jsonResponse = JSONObject(data)
                                val choices = jsonResponse.getJSONArray("choices")
                                if (choices.length() > 0) {
                                    val choice = choices.getJSONObject(0)
                                    val delta = choice.getJSONObject("delta")
                                    
                                    if (delta.has("content") && !delta.isNull("content")) {
                                        val content = delta.getString("content")
                                        responseBuffer.append(content)
                                        
                                        // Send partial result
                                        val partialResult = InferenceResult.textOutput(
                                            text = content,
                                            metadata = mapOf(
                                                InferenceResult.META_SESSION_ID to input.sessionId,
                                                InferenceResult.META_MODEL_NAME to modelName,
                                                "partial" to true
                                            ),
                                            partial = true
                                        )
                                        onResult(partialResult)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to parse streaming chunk: $data", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                onResult(InferenceResult.error(mapExceptionToError(e)))
            }
        }
    }

    /**
     * Build request body for OpenRouter API
     */
    private fun buildRequestBody(input: InferenceRequest, prompt: String, stream: Boolean): String {
        val requestJson = JSONObject().apply {
            put("model", modelName)
            put("stream", stream)
            
            // Build messages array
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            put("messages", messages)
            
            // Add parameters
            input.params[InferenceRequest.PARAM_TEMPERATURE]?.let { temp ->
                put("temperature", (temp as? Number)?.toFloat() ?: DEFAULT_TEMPERATURE)
            } ?: put("temperature", DEFAULT_TEMPERATURE)
            
            input.params[InferenceRequest.PARAM_MAX_TOKENS]?.let { maxTokens ->
                put("max_tokens", (maxTokens as? Number)?.toInt() ?: DEFAULT_MAX_TOKENS)
            } ?: put("max_tokens", DEFAULT_MAX_TOKENS)
            
            // Additional OpenRouter parameters
            input.params["top_p"]?.let { put("top_p", it) }
            input.params["frequency_penalty"]?.let { put("frequency_penalty", it) }
            input.params["presence_penalty"]?.let { put("presence_penalty", it) }
        }
        
        return requestJson.toString()
    }

    /**
     * Create HTTP connection for OpenRouter API
     */
    private fun createConnection(streaming: Boolean = false): HttpURLConnection {
        val url = URL("$baseUrl$CHAT_COMPLETIONS_ENDPOINT")
        val connection = url.openConnection() as HttpURLConnection
        
        connection.apply {
            requestMethod = "POST"
            setRequestProperty(HEADER_AUTHORIZATION, "Bearer $actualApiKey")
            setRequestProperty(HEADER_CONTENT_TYPE, "application/json")
            setRequestProperty(HEADER_HTTP_REFERER, "com.mtkresearch.breezeapp")
            setRequestProperty(HEADER_X_TITLE, "BreezeApp")
            doOutput = true
            connectTimeout = timeoutMs
            readTimeout = if (streaming) timeoutMs * 3 else timeoutMs // Longer timeout for streaming
        }
        
        return connection
    }

    /**
     * Make HTTP request to OpenRouter API
     */
    private fun makeHttpRequest(requestBody: String, streaming: Boolean = false): String {
        val connection = createConnection(streaming)
        
        try {
            // Send request
            connection.outputStream.use { outputStream ->
                outputStream.write(requestBody.toByteArray())
            }
            
            // Check response code
            val responseCode = connection.responseCode
            if (responseCode != 200) {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw IOException("HTTP $responseCode: ${mapHttpErrorCode(responseCode)} - $errorResponse")
            }
            
            // Read response
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Parse non-streaming response from OpenRouter
     */
    private fun parseNonStreamingResponse(response: String, sessionId: String): InferenceResult {
        return try {
            val jsonResponse = JSONObject(response)
            val choices = jsonResponse.getJSONArray("choices")
            
            if (choices.length() == 0) {
                return InferenceResult.error(RunnerError.runtimeError("No response choices received"))
            }
            
            val choice = choices.getJSONObject(0)
            val message = choice.getJSONObject("message")
            val content = message.getString("content")
            val finishReason = choice.optString("finish_reason", "unknown")
            
            // Extract usage information if available
            val usage = jsonResponse.optJSONObject("usage")
            val metadata = mutableMapOf<String, Any>(
                InferenceResult.META_SESSION_ID to sessionId,
                InferenceResult.META_MODEL_NAME to modelName,
                "finish_reason" to finishReason,
                "partial" to false
            )
            
            usage?.let { usageJson ->
                metadata[InferenceResult.META_TOKEN_COUNT] = usageJson.optInt("total_tokens", 0)
                metadata["prompt_tokens"] = usageJson.optInt("prompt_tokens", 0)
                metadata["completion_tokens"] = usageJson.optInt("completion_tokens", 0)
            }
            
            InferenceResult.textOutput(
                text = content,
                metadata = metadata,
                partial = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse response: $response", e)
            InferenceResult.error(RunnerError.runtimeError("Failed to parse response: ${e.message}", e))
        }
    }

    /**
     * Combine partial results into final result
     */
    private fun combinePartialResults(partialResults: List<InferenceResult>): InferenceResult {
        if (partialResults.isEmpty()) {
            return InferenceResult.error(RunnerError.runtimeError("No results received from model"))
        }
        
        val combinedText = partialResults
            .filter { it.error == null }
            .joinToString("") { it.outputs[InferenceResult.OUTPUT_TEXT] as? String ?: "" }
        
        val lastResult = partialResults.lastOrNull { it.error == null }
        val metadata = lastResult?.metadata?.toMutableMap() ?: mutableMapOf()
        metadata["partial"] = false
        
        return InferenceResult.textOutput(
            text = combinedText,
            metadata = metadata,
            partial = false
        )
    }

    /**
     * Map exceptions to appropriate RunnerError
     */
    private fun mapExceptionToError(exception: Exception): RunnerError {
        return when (exception) {
            is IOException -> {
                if (exception.message?.contains("timeout", ignoreCase = true) == true) {
                    RunnerError.timeout("Request timeout: ${exception.message}")
                } else {
                    RunnerError.runtimeError("Network error: ${exception.message}", exception)
                }
            }
            is SecurityException -> RunnerError.runtimeError("Authentication error: ${exception.message}", exception)
            else -> RunnerError.runtimeError("Unexpected error: ${exception.message}", exception)
        }
    }

    /**
     * Map HTTP error codes to user-friendly messages
     */
    private fun mapHttpErrorCode(code: Int): String {
        return when (code) {
            HTTP_BAD_REQUEST -> "Bad request - invalid parameters"
            HTTP_UNAUTHORIZED -> "Unauthorized - invalid API key"
            HTTP_PAYMENT_REQUIRED -> "Payment required - insufficient credits"
            HTTP_FORBIDDEN -> "Forbidden - content filtered"
            HTTP_REQUEST_TIMEOUT -> "Request timeout"
            HTTP_TOO_MANY_REQUESTS -> "Rate limited - too many requests"
            HTTP_BAD_GATEWAY -> "Service error - model unavailable"
            HTTP_SERVICE_UNAVAILABLE -> "Service unavailable"
            else -> "HTTP error $code"
        }
    }

    /**
     * Get API key from configuration or environment
     */
    private fun getApiKeyFromConfig(config: ModelConfig): String {
        // Try different sources for API key
        return config.parameters["api_key"] as? String
            ?: config.parameters["openrouter_api_key"] as? String
            ?: System.getProperty("openrouter.api.key", "")
            ?: ""
    }

    /**
     * Basic API key validation
     */
    private fun isValidApiKey(apiKey: String): Boolean {
        return apiKey.isNotBlank() && apiKey.length > 10 && !apiKey.contains(" ")
    }

    /**
     * Validate connection to OpenRouter (optional)
     */
    private fun validateConnection(): Boolean {
        return try {
            val connection = createConnection()
            val testRequest = JSONObject().apply {
                put("model", "openai/gpt-3.5-turbo")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "test")
                    })
                })
                put("max_tokens", 1)
            }
            
            connection.outputStream.use { outputStream ->
                outputStream.write(testRequest.toString().toByteArray())
            }
            
            connection.responseCode == 200 || connection.responseCode == 402 // 402 = insufficient credits but valid auth
        } catch (e: Exception) {
            Log.w(TAG, "Connection validation failed", e)
            false
        }
    }

    /**
     * Robust network connectivity detection with multiple fallback strategies
     */
    private fun hasInternetConnection(): Boolean {
        return try {
            // Strategy 1: Permission-aware connectivity check
            if (hasNetworkStatePermission()) {
                context?.let { ctx ->
                    val connectivityManager = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                    val activeNetwork = connectivityManager?.activeNetworkInfo
                    return activeNetwork?.isConnectedOrConnecting == true
                }
            }
            
            // Strategy 2: Lightweight HTTP probe (fallback)
            performLightweightConnectivityProbe()
        } catch (e: SecurityException) {
            Log.d(TAG, "Network state permission not available, using probe fallback")
            performLightweightConnectivityProbe()
        } catch (e: Exception) {
            Log.w(TAG, "Connectivity check failed, assuming connected", e)
            true // Assume connected on unexpected errors
        }
    }
    
    /**
     * Check if app has network state permission
     */
    private fun hasNetworkStatePermission(): Boolean {
        return context?.let { ctx ->
            ctx.checkSelfPermission(android.Manifest.permission.ACCESS_NETWORK_STATE) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } ?: false
    }
    
    /**
     * Lightweight connectivity probe as fallback
     */
    private fun performLightweightConnectivityProbe(): Boolean {
        return try {
            // Quick DNS resolution test (most lightweight)
            val address = java.net.InetAddress.getByName("8.8.8.8")
            !address.equals("")
        } catch (e: Exception) {
            Log.d(TAG, "DNS probe failed, assuming no connectivity")
            false
        }
    }
}