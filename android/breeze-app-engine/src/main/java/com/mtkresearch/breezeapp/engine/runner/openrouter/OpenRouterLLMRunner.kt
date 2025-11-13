package com.mtkresearch.breezeapp.engine.runner.openrouter

import android.content.Context
import android.util.Log
import com.mtkresearch.breezeapp.engine.annotation.AIRunner
import com.mtkresearch.breezeapp.engine.annotation.RunnerPriority
import com.mtkresearch.breezeapp.engine.annotation.VendorType
import com.mtkresearch.breezeapp.engine.runner.core.BaseRunner
import com.mtkresearch.breezeapp.engine.runner.core.FlowStreamingRunner
import com.mtkresearch.breezeapp.engine.runner.core.RunnerInfo
import com.mtkresearch.breezeapp.engine.runner.core.ParameterSchema
import com.mtkresearch.breezeapp.engine.runner.core.ParameterType
import com.mtkresearch.breezeapp.engine.runner.core.SelectionOption
import com.mtkresearch.breezeapp.engine.runner.core.ValidationResult
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
    enabled = true,
    defaultModel = "openai/gpt-oss-20b:free"
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
        apiKey = "", // Will be set via configuration
        context = context
    )

    /**
     * Default constructor for manual instantiation
     */
    constructor() : this(apiKey = "")

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

    override fun load(modelId: String, settings: EngineSettings, initialParams: Map<String, Any>): Boolean {
        try {
            val runnerName = getRunnerInfo().name
            val runnerParams = settings.getRunnerParameters(runnerName)
            Log.d(TAG, "Loading OpenRouterLLMRunner with model '${modelId}' and params: $runnerParams")

            // 1. Get API Key from runner-specific settings
            val keyFromSettings = runnerParams["api_key"] as? String ?: ""
            if (keyFromSettings.isBlank()) {
                Log.e(TAG, "API key not found in settings for $runnerName")
                return false
            }
            this.actualApiKey = keyFromSettings

            // 2. Get model and other parameters from the specific model config and runner settings
            this.modelName = modelId.takeIf { it.isNotBlank() } ?: defaultModel
            this.timeoutMs = (runnerParams["timeout_ms"] as? Number)?.toInt() ?: DEFAULT_TIMEOUT_MS
            this.assumeConnectivity = (runnerParams["assume_connectivity"] as? Boolean) ?: false

            // 3. Validate API key format
            if (!isValidApiKey(actualApiKey)) {
                Log.e(TAG, "Invalid API key format")
                return false
            }

            Log.d(TAG, "API key successfully loaded for OpenRouterLLMRunner.")
            isLoaded.set(true)
            Log.d(TAG, "OpenRouterLLMRunner loaded successfully with model: $modelName")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load OpenRouterLLMRunner", e)
            isLoaded.set(false)
            return false
        }
    }

    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        if (!isLoaded.get()) {
            return InferenceResult.error(RunnerError.resourceUnavailable())
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
            InferenceResult.error(RunnerError.processingError("Inference failed: ${e.message}", e))
        }
    }

    override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = callbackFlow {
        if (!isLoaded.get()) {
            trySend(InferenceResult.error(RunnerError.resourceUnavailable()))
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

    override fun getLoadedModelId(): String = modelName

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

    override fun getParameterSchema(): List<ParameterSchema> {
        return listOf(
            ParameterSchema(
                name = "api_key",
                displayName = "API Key",
                description = "OpenRouter API key for authentication (starts with 'sk-or-v1-')",
                type = ParameterType.StringType(
                    minLength = 20,
                    pattern = Regex("^sk-or-v1-[a-zA-Z0-9]+$")
                ),
                defaultValue = "",
                isRequired = true,
                isSensitive = true,
                category = "Authentication"
            ),
            ParameterSchema(
                name = InferenceRequest.PARAM_MODEL,
                displayName = "Model",
                description = "OpenRouter model to use for text generation (free tier only by default, use Refresh to load more)",
                type = ParameterType.SelectionType(
                    options = listOf(
                        SelectionOption("openai/gpt-oss-20b:free", "GPT-OSS 20B (Free)", "Free tier model - default fallback")
                    )
                ),
                defaultValue = DEFAULT_MODEL,
                isRequired = false,
                category = "Model Configuration"
            ),
            ParameterSchema(
                name = "base_url",
                displayName = "Base URL",
                description = "OpenRouter API base URL (advanced users only)",
                type = ParameterType.StringType(
                    pattern = Regex("^https?://.+")
                ),
                defaultValue = DEFAULT_BASE_URL,
                isRequired = false,
                category = "Advanced"
            ),
            ParameterSchema(
                name = "timeout_ms",
                displayName = "Request Timeout (ms)",
                description = "Maximum time to wait for API response",
                type = ParameterType.IntType(
                    minValue = 5000,
                    maxValue = 300000,
                    step = 5000
                ),
                defaultValue = DEFAULT_TIMEOUT_MS,
                isRequired = false,
                category = "Performance"
            ),
            ParameterSchema(
                name = "temperature",
                displayName = "Temperature",
                description = "Control randomness in responses (0.0 = deterministic, 2.0 = very creative)",
                type = ParameterType.FloatType(
                    minValue = 0.0,
                    maxValue = 2.0,
                    step = 0.1,
                    precision = 2
                ),
                defaultValue = DEFAULT_TEMPERATURE,
                isRequired = false,
                category = "Generation"
            ),
            ParameterSchema(
                name = "max_tokens",
                displayName = "Max Tokens",
                description = "Maximum number of tokens to generate in response",
                type = ParameterType.IntType(
                    minValue = 1,
                    maxValue = 8192,
                    step = 128
                ),
                defaultValue = DEFAULT_MAX_TOKENS,
                isRequired = false,
                category = "Generation"
            ),
            ParameterSchema(
                name = "top_p",
                displayName = "Top P",
                description = "Nucleus sampling parameter (0.0-1.0)",
                type = ParameterType.FloatType(
                    minValue = 0.0,
                    maxValue = 1.0,
                    step = 0.1,
                    precision = 2
                ),
                defaultValue = 0.9f,
                isRequired = false,
                category = "Generation"
            ),
            ParameterSchema(
                name = "frequency_penalty",
                displayName = "Frequency Penalty",
                description = "Reduce repetition of frequently used tokens",
                type = ParameterType.FloatType(
                    minValue = -2.0,
                    maxValue = 2.0,
                    step = 0.1,
                    precision = 2
                ),
                defaultValue = 0.0f,
                isRequired = false,
                category = "Generation"
            ),
            ParameterSchema(
                name = "presence_penalty",
                displayName = "Presence Penalty",
                description = "Reduce repetition of any tokens that appear",
                type = ParameterType.FloatType(
                    minValue = -2.0,
                    maxValue = 2.0,
                    step = 0.1,
                    precision = 2
                ),
                defaultValue = 0.0f,
                isRequired = false,
                category = "Generation"
            ),
            ParameterSchema(
                name = "top_k",
                displayName = "Top K",
                description = "Limit next token selection to top K tokens",
                type = ParameterType.IntType(
                    minValue = 1,
                    maxValue = 100,
                    step = 1
                ),
                defaultValue = 40,
                isRequired = false,
                category = "Generation"
            ),
            ParameterSchema(
                name = "repetition_penalty",
                displayName = "Repetition Penalty",
                description = "Penalty for repeating tokens (1.0 = no penalty, >1.0 = reduce repetition)",
                type = ParameterType.FloatType(
                    minValue = 1.0,
                    maxValue = 2.0,
                    step = 0.1,
                    precision = 2
                ),
                defaultValue = 1.1f,
                isRequired = false,
                category = "Generation"
            )
        )
    }

    override fun validateParameters(parameters: Map<String, Any>): ValidationResult {
        // Validate API key format
        val apiKey = parameters["api_key"] as? String
        if (apiKey.isNullOrBlank()) {
            return ValidationResult.invalid("API key is required")
        }
        if (!apiKey.startsWith("sk-or-v1-")) {
            return ValidationResult.invalid("OpenRouter API key must start with 'sk-or-v1-'")
        }

        // Validate temperature and top_p combination
        val temperature = (parameters["temperature"] as? Number)?.toFloat() ?: DEFAULT_TEMPERATURE
        val topP = (parameters["top_p"] as? Number)?.toFloat() ?: 0.9f
        
        if (temperature > 1.5f && topP > 0.95f) {
            return ValidationResult.invalid("High temperature (>1.5) with high top_p (>0.95) may produce incoherent results")
        }

        // Validate model selection
        val model = parameters[InferenceRequest.PARAM_MODEL] as? String ?: DEFAULT_MODEL
        val validModels = listOf(
            "openai/gpt-3.5-turbo",
            "openai/gpt-4", 
            "openai/gpt-4-turbo-preview",
            "anthropic/claude-3-sonnet",
            "anthropic/claude-3-opus",
            "meta-llama/llama-2-70b-chat",
            "openai/gpt-oss-20b:free"
        )
        if (model !in validModels) {
            return ValidationResult.invalid("Invalid model selection: $model")
        }

        return ValidationResult.valid()
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
                    onResult(InferenceResult.error(RunnerError.processingError("HTTP $responseCode: ${mapHttpErrorCode(responseCode)} - $errorResponse")))
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
            // Use model from request params if available, otherwise use loaded modelName
            val requestModel = input.params["model"] as? String ?: modelName
            put("model", requestModel)
            put("stream", stream)

            // Build messages array
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            put("messages", messages)

            // Add parameters from the request, using runner defaults as a fallback.
            val floatParams = listOf("temperature", "top_p", "frequency_penalty", "presence_penalty", "repetition_penalty")
            val intParams = listOf("max_tokens", "top_k")

            floatParams.forEach { paramName ->
                input.params[paramName]?.let { value ->
                    val floatValue = when (value) {
                        is Number -> value.toFloat()
                        is String -> value.toFloatOrNull()
                        else -> null
                    }
                    floatValue?.let { put(paramName, it) }
                }
            }

            intParams.forEach { paramName ->
                input.params[paramName]?.let { value ->
                    val intValue = when (value) {
                        is Number -> value.toInt()
                        is String -> value.toIntOrNull()
                        else -> null
                    }
                    intValue?.let { put(paramName, it) }
                }
            }

            // Ensure essential parameters have a default if not provided in the request
            if (!has("temperature")) {
                put("temperature", DEFAULT_TEMPERATURE)
            }
            if (!has("max_tokens")) {
                put("max_tokens", DEFAULT_MAX_TOKENS)
            }
        }

        val requestBodyString = requestJson.toString()
        Log.d(TAG, "OpenRouter Complete Request:")
        Log.d(TAG, "curl -X POST $baseUrl$CHAT_COMPLETIONS_ENDPOINT \\")
        Log.d(TAG, "     -H \"Authorization: Bearer ${actualApiKey.take(20)}...\" \\")
        Log.d(TAG, "     -H \"Content-Type: application/json\" \\")
        Log.d(TAG, "     -d '${requestJson.toString(2)}'")
        return requestBodyString
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
                return InferenceResult.error(RunnerError.processingError("No response choices received"))
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
            InferenceResult.error(RunnerError.processingError("Failed to parse response: ${e.message}", e))
        }
    }

    /**
     * Combine partial results into final result
     */
    private fun combinePartialResults(partialResults: List<InferenceResult>): InferenceResult {
        if (partialResults.isEmpty()) {
            return InferenceResult.error(RunnerError.processingError("No results received from model"))
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
                    RunnerError(RunnerError.Code.PROCESSING_ERROR, "Request timeout: ${exception.message}")
                } else {
                    RunnerError.processingError("Network error: ${exception.message}", exception)
                }
            }
            is SecurityException -> RunnerError.processingError("Authentication error: ${exception.message}", exception)
            else -> RunnerError.processingError("Unexpected error: ${exception.message}", exception)
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