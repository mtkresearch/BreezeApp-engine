package com.mtkresearch.breezeapp.engine.runner.selfhosted

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
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/**
 * SelfHostedASRRunner
 *
 * Implementation of Automatic Speech Recognition runner using self-hosted Whisper model.
 * Uses ByteArray as general input format for all ASR runners.
 *
 * Input Format:
 * - ByteArray containing raw audio data
 * - Server handles audio format detection automatically
 * - Supports: WAV, MP3, FLAC, OGG, WebM, M4A
 *
 * @param serverUrl Base URL of your self-hosted Whisper server
 * @param modelName Name of the model (e.g., "Taigi", "whisper-large-v3")
 */
@AIRunner(
    vendor = VendorType.CUSTOM,
    priority = RunnerPriority.HIGH,
    capabilities = [CapabilityType.ASR],
    enabled = true,
    defaultModel = "Taigi"
)
class SelfHostedASRRunner(
    private val serverUrl: String = DEFAULT_SERVER_URL,
    private val modelName: String = DEFAULT_MODEL,
    private val context: Context? = null
) : BaseRunner {

    constructor(context: Context) : this(
        serverUrl = DEFAULT_SERVER_URL,
        modelName = DEFAULT_MODEL,
        context = context
    )

    constructor() : this(serverUrl = DEFAULT_SERVER_URL)

    companion object {
        private const val TAG = "SelfHostedASRRunner"
        private const val DEFAULT_SERVER_URL = "https://neely-henlike-shin.ngrok-free.dev"
        private const val DEFAULT_MODEL = "Taigi"
        private const val DEFAULT_TIMEOUT_MS = 120000
        private const val MAX_FILE_SIZE_BYTES = 100 * 1024 * 1024

        private const val ENDPOINT_TRANSCRIBE = "/transcribe"
        private const val ENDPOINT_ASR = "/asr"
        private const val ENDPOINT_HEALTH = "/health"

        private const val HEADER_CONTENT_TYPE = "Content-Type"
        private const val HEADER_AUTHORIZATION = "Authorization"

        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_INTERNAL_ERROR = 500
        private const val HTTP_SERVICE_UNAVAILABLE = 503
    }

    private val isLoaded = AtomicBoolean(false)
    private var actualServerUrl: String = serverUrl
    private var actualModelName: String = modelName
    private var timeoutMs: Int = DEFAULT_TIMEOUT_MS
    private var endpoint: String = ENDPOINT_TRANSCRIBE
    private var language: String? = null
    private var apiKey: String? = null
    private var assumeConnectivity: Boolean = false

    override fun load(modelId: String, settings: EngineSettings, initialParams: Map<String, Any>): Boolean {
        try {
            val runnerName = getRunnerInfo().name
            val runnerParams = settings.getRunnerParameters(runnerName)
            Log.d(TAG, "Loading SelfHostedASRRunner with model '${modelId}' and params: $runnerParams")

            if (DEFAULT_SERVER_URL.isNullOrBlank()) {
                Log.e(TAG, "Server URL not found in settings for $runnerName")
                return false
            }
            this.actualServerUrl = DEFAULT_SERVER_URL

            this.actualModelName = modelId.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL

            this.timeoutMs = (runnerParams["timeout_ms"] as? Number)?.toInt() ?: DEFAULT_TIMEOUT_MS
            this.endpoint = (runnerParams["endpoint"] as? String) ?: ENDPOINT_TRANSCRIBE
            this.language = (runnerParams["language"] as? String)?.takeIf { it.isNotBlank() }
            this.apiKey = (runnerParams["api_key"] as? String)?.takeIf { it.isNotBlank() }
            this.assumeConnectivity = (runnerParams["assume_connectivity"] as? Boolean) ?: false

            if (!isValidServerUrl(actualServerUrl)) {
                Log.e(TAG, "Invalid server URL format: $actualServerUrl")
                return false
            }

            if (!assumeConnectivity) {
                val healthCheck = testServerHealth()
                if (!healthCheck) {
                    Log.w(TAG, "Server health check failed, but continuing anyway")
                }
            }

            Log.d(TAG, "SelfHostedASRRunner loaded successfully")
            Log.d(TAG, "Server: $actualServerUrl, Model: $actualModelName, Endpoint: $endpoint")
            isLoaded.set(true)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load SelfHostedASRRunner", e)
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

            // Get audio ByteArray from input - GENERAL INPUT FORMAT FOR ALL ASR RUNNERS
            val audioData = input.inputs[InferenceRequest.INPUT_AUDIO] as? ByteArray
            if (audioData == null || audioData.isEmpty()) {
                return InferenceResult.error(RunnerError.invalidInput("Missing or empty audio input"))
            }

            Log.d(TAG, "Processing ${audioData.size} bytes of audio data")

            if (audioData.size > MAX_FILE_SIZE_BYTES) {
                return InferenceResult.error(
                    RunnerError.invalidInput("Audio file too large (${audioData.size} bytes). Max: $MAX_FILE_SIZE_BYTES bytes")
                )
            }

            val requestLanguage = input.inputs[InferenceRequest.PARAM_LANGUAGE] as? String
                ?: this.language

            val startTime = System.currentTimeMillis()
            val result = processTranscriptionRequest(input, audioData, requestLanguage)
            val elapsed = System.currentTimeMillis() - startTime

            Log.d(TAG, "ASR completed in ${elapsed}ms")

            result
        } catch (e: Exception) {
            Log.e(TAG, "Error processing ASR request", e)
            InferenceResult.error(mapExceptionToError(e))
        }
    }

    override fun unload() {
        Log.d(TAG, "Unloading SelfHostedASRRunner")
        isLoaded.set(false)
    }

    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.ASR)

    override fun isLoaded(): Boolean = isLoaded.get()

    override fun getLoadedModelId(): String = actualModelName

    override fun getRunnerInfo(): RunnerInfo = RunnerInfo(
        name = "SelfHostedASRRunner",
        version = "1.0.0",
        capabilities = getCapabilities(),
        description = "Self-hosted Whisper model ASR runner (supports local and ngrok servers)"
    )

    override fun isSupported(): Boolean {
        if (assumeConnectivity) {
            Log.d(TAG, "SelfHosted ASR supported: connectivity assumption enabled")
            return true
        }

        if (actualServerUrl.isBlank() || !isValidServerUrl(actualServerUrl)) {
            Log.d(TAG, "SelfHosted ASR not supported: invalid server URL")
            return false
        }

        if (actualServerUrl.contains("localhost") || actualServerUrl.contains("127.0.0.1")) {
            Log.d(TAG, "SelfHosted ASR supported: localhost server")
            return true
        }

        val hasConnectivity = hasInternetConnection()
        Log.d(TAG, "SelfHosted ASR supported: $hasConnectivity (remote server)")
        return hasConnectivity
    }

    override fun getParameterSchema(): List<ParameterSchema> {
        return listOf(
            ParameterSchema(
                name = "server_url",
                displayName = "Server URL",
                description = "URL of your self-hosted Whisper server (e.g., http://localhost:5000 or https://your-id.ngrok.io)",
                type = ParameterType.StringType(
                    minLength = 1,
                    pattern = Regex("^https?://.+")
                ),
                defaultValue = DEFAULT_SERVER_URL,
                isRequired = true,
                category = "Server Configuration"
            ),
            ParameterSchema(
                name = "model_name",
                displayName = "Model Name",
                description = "Name of the model (e.g., 'Taigi', 'whisper-large-v3')",
                type = ParameterType.StringType(
                    minLength = 1
                ),
                defaultValue = DEFAULT_MODEL,
                isRequired = false,
                category = "Model Configuration"
            ),
            ParameterSchema(
                name = "endpoint",
                displayName = "API Endpoint",
                description = "Transcription endpoint path",
                type = ParameterType.SelectionType(
                    options = listOf(
                        SelectionOption("/transcribe", "Transcribe", "Standard endpoint"),
                        SelectionOption("/asr", "ASR", "Alternative endpoint")
                    )
                ),
                defaultValue = ENDPOINT_TRANSCRIBE,
                isRequired = false,
                category = "Server Configuration"
            ),
            ParameterSchema(
                name = "language",
                displayName = "Language Code",
                description = "Force specific language (optional, e.g., 'en', 'zh', 'nan' for Taigi). Leave empty for auto-detection.",
                type = ParameterType.StringType(
                    minLength = 0,
                    maxLength = 10
                ),
                defaultValue = "",
                isRequired = false,
                category = "Model Configuration"
            ),
            ParameterSchema(
                name = "api_key",
                displayName = "API Key (Optional)",
                description = "API key if your server requires authentication",
                type = ParameterType.StringType(
                    minLength = 0
                ),
                defaultValue = "",
                isRequired = false,
                isSensitive = true,
                category = "Authentication"
            ),
            ParameterSchema(
                name = "timeout_ms",
                displayName = "Request Timeout (ms)",
                description = "Maximum time to wait for server response",
                type = ParameterType.IntType(
                    minValue = 30000,
                    maxValue = 600000,
                    step = 10000
                ),
                defaultValue = DEFAULT_TIMEOUT_MS,
                isRequired = false,
                category = "Performance"
            ),
            ParameterSchema(
                name = "assume_connectivity",
                displayName = "Assume Connectivity",
                description = "Skip connectivity checks (useful for local servers)",
                type = ParameterType.BooleanType,
                defaultValue = false,
                isRequired = false,
                category = "Advanced"
            )
        )
    }

    override fun validateParameters(parameters: Map<String, Any>): ValidationResult {
        val serverUrl = parameters["server_url"] as? String
        if (serverUrl.isNullOrBlank()) {
            return ValidationResult.invalid("Server URL is required")
        }

        if (!isValidServerUrl(serverUrl)) {
            return ValidationResult.invalid("Invalid server URL format. Must start with http:// or https://")
        }

        return ValidationResult.valid()
    }

    // Private implementation methods

    private fun testServerHealth(): Boolean {
        return try {
            val healthUrl = "$actualServerUrl$ENDPOINT_HEALTH"
            val url = URL(healthUrl)
            val connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }

            val responseCode = connection.responseCode
            connection.disconnect()

            responseCode == 200
        } catch (e: Exception) {
            Log.w(TAG, "Health check failed: ${e.message}")
            false
        }
    }

    private fun processTranscriptionRequest(
        input: InferenceRequest,
        audioData: ByteArray,
        language: String?
    ): InferenceResult {
        return try {
            val response = makeTranscriptionRequest(audioData, language)
            parseTranscriptionResponse(response, input.sessionId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process transcription", e)
            throw e
        }
    }

    /**
     * Make HTTP request to self-hosted Whisper server
     * Sends raw ByteArray with application/octet-stream
     * Language is sent as URL parameter if provided
     */
    private fun makeTranscriptionRequest(audioData: ByteArray, language: String?): String {
        // Build URL with language parameter if provided
        var requestUrl = "$actualServerUrl$endpoint"
        if (language != null) {
            requestUrl += "?language=$language"
        }

        val url = URL(requestUrl)
        val connection = url.openConnection() as HttpURLConnection

        connection.apply {
            requestMethod = "POST"
            // Send as raw binary data - server will detect format
            setRequestProperty(HEADER_CONTENT_TYPE, "application/octet-stream")

            apiKey?.let {
                setRequestProperty(HEADER_AUTHORIZATION, "Bearer $it")
            }

            doOutput = true
            doInput = true
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
        }

        try {
            // Send raw audio ByteArray directly
            connection.outputStream.use { outputStream ->
                outputStream.write(audioData)
                outputStream.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw IOException("HTTP $responseCode: ${mapHttpErrorCode(responseCode)} - $errorResponse")
            }

            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseTranscriptionResponse(response: String, sessionId: String): InferenceResult {
        return try {
            val jsonResponse = JSONObject(response)
            val text = jsonResponse.getString("text")

            val metadata = mutableMapOf<String, Any>(
                InferenceResult.META_SESSION_ID to sessionId,
                InferenceResult.META_MODEL_NAME to actualModelName
            )

            if (jsonResponse.has("language")) {
                metadata["language"] = jsonResponse.getString("language")
            }

            if (jsonResponse.has("processing_time")) {
                metadata["processing_time"] = jsonResponse.getDouble("processing_time")
            }

            if (jsonResponse.has("sample_rate")) {
                metadata["sample_rate"] = jsonResponse.getInt("sample_rate")
            }

            if (jsonResponse.has("model")) {
                metadata["server_model"] = jsonResponse.getString("model")
            }

            if (jsonResponse.has("chunks")) {
                val chunks = jsonResponse.getJSONArray("chunks")
                val timestampedChunks = mutableListOf<Map<String, Any>>()

                for (i in 0 until chunks.length()) {
                    val chunk = chunks.getJSONObject(i)
                    val chunkMap = mutableMapOf<String, Any>()

                    if (chunk.has("text")) {
                        chunkMap["text"] = chunk.getString("text")
                    }

                    if (chunk.has("timestamp")) {
                        val timestamp = chunk.getJSONArray("timestamp")
                        chunkMap["timestamp"] = listOf(
                            timestamp.getDouble(0),
                            timestamp.getDouble(1)
                        )
                    }

                    timestampedChunks.add(chunkMap)
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

    private fun mapExceptionToError(exception: Exception): RunnerError {
        return when (exception) {
            is IOException -> {
                val message = exception.message ?: ""
                when {
                    message.contains("timeout", ignoreCase = true) ->
                        RunnerError(RunnerError.Code.PROCESSING_ERROR, "Request timeout. Server might be processing or unreachable.")
                    message.contains("Connection refused", ignoreCase = true) ->
                        RunnerError(RunnerError.Code.PROCESSING_ERROR, "Cannot connect to server. Check if server is running.")
                    message.contains("No route to host", ignoreCase = true) ->
                        RunnerError(RunnerError.Code.PROCESSING_ERROR, "Server unreachable. Check network and server URL.")
                    else ->
                        RunnerError.processingError("Network error: ${exception.message}", exception)
                }
            }
            is SecurityException -> RunnerError.processingError("Authentication error: ${exception.message}", exception)
            else -> RunnerError.processingError("Unexpected error: ${exception.message}", exception)
        }
    }

    private fun mapHttpErrorCode(code: Int): String {
        return when (code) {
            HTTP_BAD_REQUEST -> "Bad request - invalid audio format or parameters"
            HTTP_UNAUTHORIZED -> "Unauthorized - invalid API key"
            HTTP_INTERNAL_ERROR -> "Server error - check server logs"
            HTTP_SERVICE_UNAVAILABLE -> "Service unavailable - server may be starting"
            else -> "HTTP error $code"
        }
    }

    private fun isValidServerUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

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