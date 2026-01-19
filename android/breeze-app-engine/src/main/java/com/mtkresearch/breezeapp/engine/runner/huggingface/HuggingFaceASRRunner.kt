package com.mtkresearch.breezeapp.engine.runner.huggingface

import android.content.Context
import android.util.Log
import com.mtkresearch.breezeapp.engine.annotation.AIRunner
import com.mtkresearch.breezeapp.engine.annotation.RunnerPriority
import com.mtkresearch.breezeapp.engine.annotation.VendorType
import com.mtkresearch.breezeapp.engine.runner.core.BaseRunner
import com.mtkresearch.breezeapp.engine.runner.core.RunnerInfo
import com.mtkresearch.breezeapp.engine.runner.core.ParameterSchema
import com.mtkresearch.breezeapp.engine.runner.core.ParameterType
import com.mtkresearch.breezeapp.engine.runner.core.SelectionOption
import com.mtkresearch.breezeapp.engine.runner.core.ValidationResult
import com.mtkresearch.breezeapp.engine.model.*
import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject
import org.json.JSONArray

/**
 * HuggingFaceASRRunner
 *
 * Implementation of Automatic Speech Recognition runner using Hugging Face Inference API.
 * Provides free access to Whisper and other ASR models through Hugging Face's hosted infrastructure.
 *
 * Features:
 * - Free tier available (rate-limited)
 * - Multiple Whisper model variants
 * - Support for 100+ languages
 * - Automatic language detection
 * - Simple REST API
 * - No credit card required for free tier
 *
 * Input Format:
 * - Audio formats: flac, wav, mp3, ogg, webm
 * - Raw audio bytes (PCM16)
 * - Recommended: 16kHz sample rate
 *
 * Output:
 * - Transcribed text
 * - Optional timestamp information
 *
 * API Key:
 * - Get free key at: https://huggingface.co/settings/keys
 * - Create a "Read" key (no payment required)
 * - Free tier: ~1000 requests/day per model
 *
 * @param apiKey Hugging Face API key (read access)
 * @param baseUrl Hugging Face Inference API base URL
 * @param defaultModel Default model to use (Whisper variant)
 */
@AIRunner(
    vendor = VendorType.HF,
    priority = RunnerPriority.HIGH,
    capabilities = [CapabilityType.ASR],
    enabled = true,
    defaultModel = "openai/whisper-large-v3"
)
class HuggingFaceASRRunner(
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val defaultModel: String = DEFAULT_MODEL,
    private val context: Context? = null
) : BaseRunner {

    /**
     * Secondary constructor for Context injection (used by RunnerFactory)
     */
    constructor(context: Context) : this(
        apiKey = "",
        context = context
    )

    /**
     * Default constructor for manual instantiation
     */
    constructor() : this(apiKey = "")

    companion object {
        private const val TAG = "HuggingFaceASRRunner"
        private const val DEFAULT_BASE_URL = "https://router.huggingface.co/hf-inference/models"
        private const val DEFAULT_MODEL = "openai/whisper-large-v3"
        private const val DEFAULT_TIMEOUT_MS = 60000
        private const val MAX_FILE_SIZE_BYTES = 30 * 1024 * 1024 // 30MB

        // HTTP headers
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val HEADER_CONTENT_TYPE = "Content-Type"

        // Error code mapping
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_INTERNAL_ERROR = 500
        private const val HTTP_SERVICE_UNAVAILABLE = 503

        // Model loading states
        private const val ERROR_MODEL_LOADING = "Model is currently loading"
    }

    private val isLoaded = AtomicBoolean(false)
    private var actualapiKey: String = apiKey
    private var modelName: String = defaultModel
    private var timeoutMs: Int = DEFAULT_TIMEOUT_MS
    private var assumeConnectivity: Boolean = false
    private var waitForModel: Boolean = true // Wait for model to load if cold
    private var useCache: Boolean = true // Use cached results
    private var maxRetries: Int = 3 // Retry if model is loading

    override fun load(modelId: String, settings: EngineSettings, initialParams: Map<String, Any>): Boolean {
        try {
            val runnerName = getRunnerInfo().name
            val runnerParams = settings.getRunnerParameters(runnerName)
            Log.d(TAG, "Loading HuggingFaceASRRunner with model '${modelId}' and params: $runnerParams")

            // 1. Get API Key from runner-specific settings
            val keyFromSettings = runnerParams["api_key"] as? String ?: ""
            if (keyFromSettings.isBlank()) {
                Log.e(TAG, "API key not found in settings for $runnerName")
                return false
            }
            this.actualapiKey = keyFromSettings

            // 2. Get model and other parameters from the specific model config and runner settings
            this.modelName = modelId.takeIf { it.isNotBlank() } ?: defaultModel
            this.timeoutMs = (runnerParams["timeout_ms"] as? Number)?.toInt() ?: DEFAULT_TIMEOUT_MS
            this.assumeConnectivity = (runnerParams["assume_connectivity"] as? Boolean) ?: false
            this.waitForModel = (runnerParams["wait_for_model"] as? Boolean) ?: true
            this.useCache = (runnerParams["use_cache"] as? Boolean) ?: true
            this.maxRetries = (runnerParams["max_retries"] as? Number)?.toInt() ?: 3

            // 3. Validate API key format
            if (!isValidapiKey(actualapiKey)) {
                Log.e(TAG, "Invalid API key format")
                return false
            }

            Log.d(TAG, "API key successfully loaded for HuggingFaceASRRunner.")
            isLoaded.set(true)
            Log.d(TAG, "HuggingFaceASRRunner loaded successfully with model: $modelName")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load HuggingFaceASRRunner", e)
            isLoaded.set(false)
            return false
        }
    }

    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        if (!isLoaded.get()) {
            return InferenceResult.error(RunnerError.resourceUnavailable())
        }

        return try {
            Log.d(TAG, "Processing ASR request for session: ${input.sessionId}")

            // Get audio data from input
            val audioData = input.inputs[InferenceRequest.INPUT_AUDIO] as? ByteArray
            if (audioData == null || audioData.isEmpty()) {
                return InferenceResult.error(RunnerError.invalidInput("Missing or empty audio input"))
            }

            Log.d(TAG, "Processing ${audioData.size} bytes of audio data")

            // Check file size
            if (audioData.size > MAX_FILE_SIZE_BYTES) {
                return InferenceResult.error(
                    RunnerError.invalidInput("Audio file too large (${audioData.size} bytes). Max: $MAX_FILE_SIZE_BYTES bytes")
                )
            }

            // Process transcription request with retries
            val startTime = System.currentTimeMillis()
            val result = processTranscriptionWithRetry(input, audioData)
            val elapsed = System.currentTimeMillis() - startTime

            Log.d(TAG, "ASR completed in ${elapsed}ms")

            result
        } catch (e: Exception) {
            Log.e(TAG, "Error processing ASR request", e)
            InferenceResult.error(mapExceptionToError(e))
        }
    }

    override fun unload() {
        Log.d(TAG, "Unloading HuggingFaceASRRunner")
        isLoaded.set(false)
    }

    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.ASR)

    override fun isLoaded(): Boolean = isLoaded.get()

    override fun getLoadedModelId(): String = modelName

    override fun getRunnerInfo(): RunnerInfo = RunnerInfo(
        name = "HuggingFaceASRRunner",
        version = "1.0.0",
        capabilities = getCapabilities(),
        description = "Hugging Face Inference API-based speech recognition runner (Free tier available)"
    )

    override fun isSupported(): Boolean {
        // Configuration-based bypass
        if (assumeConnectivity) {
            Log.d(TAG, "HuggingFace supported: connectivity assumption enabled")
            return true
        }

        // Multi-tier support evaluation
        val connectivityStatus = hasInternetConnection()

        // Log connectivity status for debugging
        Log.d(TAG, "Connectivity check result: $connectivityStatus")

        // HuggingFace runner is supported if:
        // 1. We detect internet connectivity, OR
        // 2. We have a valid API key and can't determine connectivity (assume supported)
        return when {
            connectivityStatus -> {
                Log.d(TAG, "HuggingFace supported: connectivity confirmed")
                true
            }
            actualapiKey.isNotBlank() && isValidapiKey(actualapiKey) -> {
                Log.d(TAG, "HuggingFace supported: valid API key present, assuming connectivity")
                true
            }
            else -> {
                Log.d(TAG, "HuggingFace not supported: no connectivity and invalid/missing API key")
                false
            }
        }
    }

    override fun getParameterSchema(): List<ParameterSchema> {
        return listOf(
            ParameterSchema(
                name = "api_key",
                displayName = "API key",
                description = "Hugging Face API key (get free key at huggingface.co/settings/keys - create 'Read' key)",
                type = ParameterType.StringType(
                    minLength = 1
                ),
                defaultValue = "",
                isRequired = true,
                isSensitive = true,
                category = "Authentication"
            ),
            ParameterSchema(
                name = InferenceRequest.PARAM_MODEL,
                displayName = "Model",
                description = "Whisper model variant (larger = more accurate but slower)",
                type = ParameterType.SelectionType(
                    options = listOf(
                        SelectionOption("openai/whisper-large-v3", "Whisper Large v3", "Most accurate (recommended)"),
                        SelectionOption("openai/whisper-large-v2", "Whisper Large v2", "Previous generation"),
                        SelectionOption("openai/whisper-medium", "Whisper Medium", "Balanced speed/accuracy"),
                        SelectionOption("openai/whisper-small", "Whisper Small", "Faster, less accurate"),
                        SelectionOption("openai/whisper-base", "Whisper Base", "Fast, basic accuracy"),
                        SelectionOption("openai/whisper-tiny", "Whisper Tiny", "Fastest, lowest accuracy")
                    )
                ),
                defaultValue = DEFAULT_MODEL,
                isRequired = false,
                category = "Model Configuration"
            ),
            ParameterSchema(
                name = "base_url",
                displayName = "Base URL",
                description = "Hugging Face Inference API base URL (advanced users only)",
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
                    minValue = 10000,
                    maxValue = 300000,
                    step = 5000
                ),
                defaultValue = DEFAULT_TIMEOUT_MS,
                isRequired = false,
                category = "Performance"
            ),
            ParameterSchema(
                name = "wait_for_model",
                displayName = "Wait for Model",
                description = "Wait for model to load if it's currently starting (recommended)",
                type = ParameterType.BooleanType,
                defaultValue = true,
                isRequired = false,
                category = "Performance"
            ),
            ParameterSchema(
                name = "use_cache",
                displayName = "Use Cache",
                description = "Use cached results for identical requests",
                type = ParameterType.BooleanType,
                defaultValue = true,
                isRequired = false,
                category = "Performance"
            ),
            ParameterSchema(
                name = "max_retries",
                displayName = "Max Retries",
                description = "Maximum retries if model is loading",
                type = ParameterType.IntType(
                    minValue = 0,
                    maxValue = 10,
                    step = 1
                ),
                defaultValue = 3,
                isRequired = false,
                category = "Performance"
            ),
            ParameterSchema(
                name = "assume_connectivity",
                displayName = "Assume Connectivity",
                description = "Assume internet connectivity is available (skip connectivity checks)",
                type = ParameterType.BooleanType,
                defaultValue = false,
                isRequired = false,
                category = "Advanced"
            )
        )
    }

    override fun validateParameters(parameters: Map<String, Any>): ValidationResult {
        // Validate API key
        val apiKey = parameters["api_key"] as? String
        if (apiKey.isNullOrBlank()) {
            return ValidationResult.invalid("API key is required. Get free key at huggingface.co/settings/keys")
        }

        // Validate model format
        val model = parameters[InferenceRequest.PARAM_MODEL] as? String ?: DEFAULT_MODEL
        if (!model.contains("/")) {
            return ValidationResult.invalid("Model must be in format 'organization/model-name'")
        }

        return ValidationResult.valid()
    }

    // Private implementation methods

    /**
     * Process transcription request with retry logic for model loading
     */
    private fun processTranscriptionWithRetry(
        input: InferenceRequest,
        audioData: ByteArray
    ): InferenceResult {
        var attempt = 0
        var lastError: Exception? = null

        while (attempt <= maxRetries) {
            try {
                return processTranscriptionRequest(input, audioData)
            } catch (e: Exception) {
                lastError = e
                val errorMessage = e.message ?: ""

                // Check if model is loading
                if (errorMessage.contains(ERROR_MODEL_LOADING, ignoreCase = true) &&
                    waitForModel &&
                    attempt < maxRetries) {

                    val waitTime = (attempt + 1) * 2000L // Progressive backoff: 2s, 4s, 6s
                    Log.d(TAG, "Model is loading, waiting ${waitTime}ms before retry (attempt ${attempt + 1}/$maxRetries)")
                    Thread.sleep(waitTime)
                    attempt++
                    continue
                }

                // Not a loading error or out of retries
                throw e
            }
        }

        throw lastError ?: Exception("Failed after $maxRetries retries")
    }

    /**
     * Process transcription request with Hugging Face API
     */
    private fun processTranscriptionRequest(
        input: InferenceRequest,
        audioData: ByteArray
    ): InferenceResult {
        return try {
            // Make API request
            val response = makeTranscriptionRequest(audioData)

            // Parse response
            val result = parseTranscriptionResponse(response, input.sessionId)

            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process transcription", e)
            throw e
        }
    }

    /**
     * Make HTTP request to Hugging Face Inference API
     */
    private fun makeTranscriptionRequest(audioData: ByteArray): String {
        val modelUrl = "$baseUrl/$modelName"
        val url = URL(modelUrl)
        val connection = url.openConnection() as HttpURLConnection

        connection.apply {
            requestMethod = "POST"
            setRequestProperty(HEADER_AUTHORIZATION, "Bearer $actualapiKey")
            setRequestProperty(HEADER_CONTENT_TYPE, "audio/wav")

            // Add optional parameters
            if (waitForModel) {
                setRequestProperty("X-Wait-For-Model", "true")
            }
            if (!useCache) {
                setRequestProperty("X-Use-Cache", "false")
            }

            doOutput = true
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
        }

        try {
            // Send audio data
            connection.outputStream.use { outputStream ->
                outputStream.write(audioData)
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
     * Parse transcription response from Hugging Face API
     */
    private fun parseTranscriptionResponse(response: String, sessionId: String): InferenceResult {
        return try {
            val jsonResponse = JSONObject(response)

            // Hugging Face returns: {"text": "transcribed text"}
            val text = jsonResponse.getString("text")

            val metadata = mutableMapOf<String, Any>(
                InferenceResult.META_SESSION_ID to sessionId,
                InferenceResult.META_MODEL_NAME to modelName
            )

            // Check for chunks (if available)
            if (jsonResponse.has("chunks")) {
                val chunks = jsonResponse.getJSONArray("chunks")
                val timestampedChunks = mutableListOf<Map<String, Any>>()

                for (i in 0 until chunks.length()) {
                    val chunk = chunks.getJSONObject(i)
                    timestampedChunks.add(mapOf(
                        "text" to chunk.getString("text"),
                        "timestamp" to chunk.getJSONArray("timestamp").let { ts ->
                            listOf(ts.getDouble(0), ts.getDouble(1))
                        }
                    ))
                }

                metadata["chunks"] = timestampedChunks
            }

            InferenceResult.textOutput(
                text = text,
                metadata = metadata
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse response: $response", e)
            InferenceResult.error(RunnerError.processingError("Failed to parse response: ${e.message}", e))
        }
    }

    /**
     * Map exceptions to appropriate RunnerError
     */
    private fun mapExceptionToError(exception: Exception): RunnerError {
        return when (exception) {
            is IOException -> {
                val message = exception.message ?: ""
                when {
                    message.contains("timeout", ignoreCase = true) ->
                        RunnerError(RunnerError.Code.PROCESSING_ERROR, "Request timeout: ${exception.message}")
                    message.contains(ERROR_MODEL_LOADING, ignoreCase = true) ->
                        RunnerError(RunnerError.Code.PROCESSING_ERROR, "Model is loading. Please try again in a few seconds.")
                    else ->
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
            HTTP_BAD_REQUEST -> "Bad request - invalid audio format or parameters"
            HTTP_UNAUTHORIZED -> "Unauthorized - invalid API key"
            HTTP_FORBIDDEN -> "Forbidden - access denied or rate limited"
            HTTP_NOT_FOUND -> "Model not found - check model name"
            HTTP_TOO_MANY_REQUESTS -> "Rate limited - too many requests (free tier limit)"
            HTTP_INTERNAL_ERROR -> "Server error"
            HTTP_SERVICE_UNAVAILABLE -> "Service unavailable - model may be loading"
            else -> "HTTP error $code"
        }
    }

    /**
     * Basic API key validation
     */
    private fun isValidapiKey(key: String): Boolean {
        // Hugging Face keys start with "hf_"
        return key.isNotBlank() && key.startsWith("hf_")
    }

    /**
     * Check internet connectivity
     */
    private fun hasInternetConnection(): Boolean {
        return try {
            if (hasNetworkStatePermission()) {
                context?.let { ctx ->
                    val connectivityManager = ctx.getSystemService(Context.CONNECTIVITY_SERVICE)
                            as? android.net.ConnectivityManager
                    val activeNetwork = connectivityManager?.activeNetworkInfo
                    return activeNetwork?.isConnectedOrConnecting == true
                }
            }
            performLightweightConnectivityProbe()
        } catch (e: Exception) {
            Log.w(TAG, "Connectivity check failed, assuming connected", e)
            true
        }
    }

    private fun hasNetworkStatePermission(): Boolean {
        return context?.let { ctx ->
            ctx.checkSelfPermission(android.Manifest.permission.ACCESS_NETWORK_STATE) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } ?: false
    }

    private fun performLightweightConnectivityProbe(): Boolean {
        return try {
            val address = java.net.InetAddress.getByName("8.8.8.8")
            !address.equals("")
        } catch (e: Exception) {
            false
        }
    }
}