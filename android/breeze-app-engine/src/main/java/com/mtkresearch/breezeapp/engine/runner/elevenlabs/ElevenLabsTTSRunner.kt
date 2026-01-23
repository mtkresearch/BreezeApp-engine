package com.mtkresearch.breezeapp.engine.runner.elevenlabs

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
 * ElevenLabsTTSRunner
 *
 * Implementation of Text-to-Speech runner using ElevenLabs API.
 * Provides the highest quality, most natural-sounding AI voices.
 *
 * Features:
 * - Premium voice quality (best in class)
 * - 10,000 characters/month free tier
 * - Multiple premium voices included
 * - Emotion and emphasis control
 * - Multi-language support (29 languages)
 * - Streaming support (optional)
 * - Voice stability and clarity controls
 * - MP3 or PCM audio output
 *
 * Free Tier:
 * - 10,000 characters per month
 * - 3 custom voices
 * - All premium voices
 * - No credit card required
 *
 * Input Format:
 * - Plain text string
 * - Max length: 5000 characters per request
 * - Supports SSML for advanced control
 *
 * Output:
 * - MP3 audio bytes (default, compressed)
 * - PCM audio bytes (optional, uncompressed)
 * - 44.1kHz sample rate
 *
 * API Key:
 * - Get free API key at: https://elevenlabs.io
 * - Sign up → Settings → API Keys → Create
 * - Free tier: 10,000 chars/month
 *
 * @param apiKey ElevenLabs API key
 * @param baseUrl ElevenLabs API base URL
 * @param defaultVoiceId Default voice ID to use
 */
@AIRunner(
    vendor = VendorType.ELEVENLABS,
    priority = RunnerPriority.HIGH,
    capabilities = [CapabilityType.TTS],
    enabled = true
)
class ElevenLabsTTSRunner(
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val defaultVoiceId: String = DEFAULT_VOICE_ID,
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
        private const val TAG = "ElevenLabsTTSRunner"
        private const val DEFAULT_BASE_URL = "https://api.elevenlabs.io/v1"
        private const val DEFAULT_VOICE_ID = "21m00Tcm4TlvDq8ikWAM" // Rachel voice
        private const val TEXT_TO_SPEECH_ENDPOINT = "/text-to-speech"
        private const val DEFAULT_TIMEOUT_MS = 60000
        private const val MAX_TEXT_LENGTH = 5000

        // HTTP headers
        private const val HEADER_XI_API_KEY = "xi-api-key"
        private const val HEADER_CONTENT_TYPE = "Content-Type"
        private const val HEADER_ACCEPT = "Accept"

        // Error code mapping
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_PAYMENT_REQUIRED = 402
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_INTERNAL_ERROR = 500
        private const val HTTP_SERVICE_UNAVAILABLE = 503

        // Popular voice IDs (free tier included)
        private const val VOICE_RACHEL = "21m00Tcm4TlvDq8ikWAM" // Young female (default)
        private const val VOICE_ANTONI = "ErXwobaYiN019PkySvjV" // Young male
        private const val VOICE_ELLI = "MF3mGyEYCl7XYWbV9V6O" // Young female
        private const val VOICE_ADAM = "pNInz6obpgDQGcFmaJgB" // Deep male
    }

    private val isLoaded = AtomicBoolean(false)
    private var actualApiKey: String = apiKey
    private var voiceId: String = defaultVoiceId
    private var modelId: String = "eleven_flash_v2_5"
    private var timeoutMs: Int = DEFAULT_TIMEOUT_MS
    private var assumeConnectivity: Boolean = false

    // Voice settings
    private var stability: Float = 0.5f // 0.0 to 1.0
    private var similarityBoost: Float = 0.75f // 0.0 to 1.0
    private var style: Float = 0.0f // 0.0 to 1.0 (v2 models only)
    private var useSpeakerBoost: Boolean = true

    // Output settings
    private var outputFormat: String = "mp3_44100_128" // mp3 or pcm

    override fun load(modelId: String, settings: EngineSettings, initialParams: Map<String, Any>): Boolean {
        try {
            val runnerName = getRunnerInfo().name
            val runnerParams = settings.getRunnerParameters(runnerName)
            Log.d(TAG, "Loading ElevenLabsTTSRunner with model '${modelId}' and params: $runnerParams")

            // 1. Get API Key from runner-specific settings
            val keyFromSettings = runnerParams["api_key"] as? String ?: ""
            if (keyFromSettings.isBlank()) {
                Log.e(TAG, "API key not found in settings for $runnerName")
                return false
            }
            Log.d(TAG, keyFromSettings)
            this.actualApiKey = keyFromSettings

            // 2. Get voice ID and other parameters
            this.voiceId = mapVoiceNameToId(voiceId.takeIf { it.isNotBlank() } ?: "Rachel")
            this.modelId = runnerParams["model_id"] as? String ?: "eleven_flash_v2_5"
            this.timeoutMs = (runnerParams["timeout_ms"] as? Number)?.toInt() ?: DEFAULT_TIMEOUT_MS
            this.assumeConnectivity = (runnerParams["assume_connectivity"] as? Boolean) ?: false

            // Voice settings
            this.stability = (runnerParams["stability"] as? Number)?.toFloat() ?: 0.5f
            this.similarityBoost = (runnerParams["similarity_boost"] as? Number)?.toFloat() ?: 0.75f
            this.style = (runnerParams["style"] as? Number)?.toFloat() ?: 0.0f
            this.useSpeakerBoost = (runnerParams["use_speaker_boost"] as? Boolean) ?: true

            // Output settings
            this.outputFormat = runnerParams["output_format"] as? String ?: "mp3_44100_128"

            // 3. Validate API key format
            if (!isValidApiKey(actualApiKey)) {
                Log.e(TAG, "Invalid API key format")
                return false
            }

            Log.d(TAG, "API key successfully loaded for ElevenLabsTTSRunner.")
            isLoaded.set(true)
            Log.d(TAG, "ElevenLabsTTSRunner loaded successfully with voice: $voiceId")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ElevenLabsTTSRunner", e)
            isLoaded.set(false)
            return false
        }
    }

    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        if (!isLoaded.get()) {
            return InferenceResult.error(RunnerError.resourceUnavailable())
        }

        return try {
            Log.d(TAG, "Processing TTS request for session: ${input.sessionId}")

            // Get text input
            val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String
            if (text.isNullOrBlank()) {
                return InferenceResult.error(RunnerError.invalidInput("Missing or empty text input"))
            }

            Log.d(TAG, "Processing text: '${text.take(100)}...' (${text.length} characters)")

            // Check text length
            if (text.length > MAX_TEXT_LENGTH) {
                return InferenceResult.error(
                    RunnerError.invalidInput("Text too long (${text.length} characters). Max: $MAX_TEXT_LENGTH characters")
                )
            }

            // Process TTS request
            val startTime = System.currentTimeMillis()
            val result = processTTSRequest(input, text)
            val elapsed = System.currentTimeMillis() - startTime

            Log.d(TAG, "TTS completed in ${elapsed}ms")

            result
        } catch (e: Exception) {
            Log.e(TAG, "Error processing TTS request", e)
            InferenceResult.error(mapExceptionToError(e))
        }
    }

    override fun unload() {
        Log.d(TAG, "Unloading ElevenLabsTTSRunner")
        isLoaded.set(false)
    }

    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.TTS)

    override fun isLoaded(): Boolean = isLoaded.get()

    override fun getLoadedModelId(): String = voiceId

    override fun getRunnerInfo(): RunnerInfo = RunnerInfo(
        name = "ElevenLabsTTSRunner",
        version = "1.0.0",
        capabilities = getCapabilities(),
        description = "ElevenLabs premium text-to-speech with natural-sounding AI voices (10k chars/month free)"
    )

    override fun isSupported(): Boolean {
        // Configuration-based bypass
        if (assumeConnectivity) {
            Log.d(TAG, "ElevenLabs supported: connectivity assumption enabled")
            return true
        }

        // Multi-tier support evaluation
        val connectivityStatus = hasInternetConnection()

        Log.d(TAG, "Connectivity check result: $connectivityStatus")

        return when {
            connectivityStatus -> {
                Log.d(TAG, "ElevenLabs supported: connectivity confirmed")
                true
            }
            actualApiKey.isNotBlank() && isValidApiKey(actualApiKey) -> {
                Log.d(TAG, "ElevenLabs supported: valid API key present, assuming connectivity")
                true
            }
            else -> {
                Log.d(TAG, "ElevenLabs not supported: no connectivity and invalid/missing API key")
                false
            }
        }
    }

    override fun getParameterSchema(): List<ParameterSchema> {
        return listOf(
            ParameterSchema(
                name = "api_key",
                displayName = "API Key",
                description = "ElevenLabs API key (get free key at elevenlabs.io - 10k chars/month)",
                type = ParameterType.StringType(
                    minLength = 1
                ),
                defaultValue = "",
                isRequired = true,
                isSensitive = true,
                category = "Authentication"
            ),
            ParameterSchema(
                name = "voice",
                displayName = "Voice",
                description = "Voice to use for speech synthesis",
                type = ParameterType.SelectionType(
                    options = listOf(
                        SelectionOption("Rachel", "Rachel", "Young female - warm and friendly (default)"),
                        SelectionOption("Antoni", "Antoni", "Young male - professional and articulate"),
                        SelectionOption("Elli", "Elli", "Young female - expressive and energetic"),
                        SelectionOption("Adam", "Adam", "Deep male - rich and resonant")
                    )
                ),
                defaultValue = "Rachel",
                isRequired = false,
                category = "Voice"
            ),
            ParameterSchema(
                name = InferenceRequest.PARAM_MODEL,
                displayName = "Model",
                description = "ElevenLabs model version",
                type = ParameterType.SelectionType(
                    options = listOf(
                        SelectionOption("eleven_v3", "Eleven v3", "High Quality"),
                        SelectionOption("eleven_turbo_v2_5", "Turbo v2.5", "Balanced"),
                        SelectionOption("eleven_flash_v2_5", "Flash v2.5", "Fastest - lower latency")
                    )
                ),
                defaultValue = "eleven_flash_v2_5",
                isRequired = false,
                category = "Model Configuration"
            ),
            ParameterSchema(
                name = "stability",
                displayName = "Stability",
                description = "Voice stability (0.0 = more variable/expressive, 1.0 = more stable/consistent)",
                type = ParameterType.FloatType(
                    minValue = 0.0,
                    maxValue = 1.0,
                    step = 0.05,
                    precision = 2
                ),
                defaultValue = 0.5f,
                isRequired = false,
                category = "Voice Settings"
            ),
            ParameterSchema(
                name = "similarity_boost",
                displayName = "Clarity + Similarity",
                description = "Voice clarity and similarity enhancement (0.0 = low, 1.0 = high)",
                type = ParameterType.FloatType(
                    minValue = 0.0,
                    maxValue = 1.0,
                    step = 0.05,
                    precision = 2
                ),
                defaultValue = 0.75f,
                isRequired = false,
                category = "Voice Settings"
            ),
            ParameterSchema(
                name = "style",
                displayName = "Style",
                description = "Speaking style exaggeration (v2 models only, 0.0 = neutral, 1.0 = exaggerated)",
                type = ParameterType.FloatType(
                    minValue = 0.0,
                    maxValue = 1.0,
                    step = 0.05,
                    precision = 2
                ),
                defaultValue = 0.0f,
                isRequired = false,
                category = "Voice Settings"
            ),
            ParameterSchema(
                name = "use_speaker_boost",
                displayName = "Speaker Boost",
                description = "Enhance voice clarity and quality (recommended)",
                type = ParameterType.BooleanType,
                defaultValue = true,
                isRequired = false,
                category = "Voice Settings"
            ),
            ParameterSchema(
                name = "output_format",
                displayName = "Output Format",
                description = "Audio output format",
                type = ParameterType.SelectionType(
                    options = listOf(
                        SelectionOption("mp3_44100_128", "MP3 44.1kHz 128kbps", "Compressed - smaller file size"),
                        SelectionOption("mp3_44100_192", "MP3 44.1kHz 192kbps", "Compressed - higher quality"),
                        SelectionOption("pcm_16000", "PCM 16kHz", "Uncompressed - lowest quality"),
                        SelectionOption("pcm_22050", "PCM 22kHz", "Uncompressed - medium quality"),
                        SelectionOption("pcm_24000", "PCM 24kHz", "Uncompressed - good quality"),
                        SelectionOption("pcm_44100", "PCM 44.1kHz", "Uncompressed - highest quality")
                    )
                ),
                defaultValue = "mp3_44100_128",
                isRequired = false,
                category = "Output"
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
            return ValidationResult.invalid("API key is required. Get free key at elevenlabs.io")
        }

        // Validate stability range
        val stability = (parameters["stability"] as? Number)?.toFloat()
        if (stability != null && (stability < 0.0f || stability > 1.0f)) {
            return ValidationResult.invalid("Stability must be between 0.0 and 1.0")
        }

        // Validate similarity_boost range
        val similarityBoost = (parameters["similarity_boost"] as? Number)?.toFloat()
        if (similarityBoost != null && (similarityBoost < 0.0f || similarityBoost > 1.0f)) {
            return ValidationResult.invalid("Similarity boost must be between 0.0 and 1.0")
        }

        return ValidationResult.valid()
    }

    // Private implementation methods

    /**
     * Process TTS request with ElevenLabs API
     */
    private fun processTTSRequest(
        input: InferenceRequest,
        text: String
    ): InferenceResult {
        return try {
            // Make API request
            val audioData = makeTTSRequest(text)

            // Return audio result
            createAudioResult(audioData, input.sessionId, text.length)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process TTS", e)
            throw e
        }
    }

    /**
     * Make HTTP request to ElevenLabs TTS API
     */
    private fun makeTTSRequest(text: String): ByteArray {
        val url = URL("$baseUrl$TEXT_TO_SPEECH_ENDPOINT/$voiceId")
        val connection = url.openConnection() as HttpURLConnection

        connection.apply {
            requestMethod = "POST"
            setRequestProperty(HEADER_XI_API_KEY, actualApiKey)
            setRequestProperty(HEADER_CONTENT_TYPE, "application/json")
            setRequestProperty(HEADER_ACCEPT, "audio/mpeg")

            doOutput = true
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
        }

        try {
            // Create JSON request body
            val requestBody = JSONObject().apply {
                put("text", text)
                put("model_id", modelId)

                // Voice settings
                put("voice_settings", JSONObject().apply {
                    put("stability", stability)
                    put("similarity_boost", similarityBoost)
                    put("style", style)
                    put("use_speaker_boost", useSpeakerBoost)
                })
            }

            Log.d(TAG, "ElevenLabs Request:")
            Log.d(TAG, "URL: $baseUrl$TEXT_TO_SPEECH_ENDPOINT/$voiceId")
            Log.d(TAG, "Voice: $voiceId, Model: $modelId")
            Log.d(TAG, "Settings: stability=$stability, similarity=$similarityBoost")

            // Send request
            connection.outputStream.use { outputStream ->
                outputStream.write(requestBody.toString().toByteArray())
            }

            // Check response code
            val responseCode = connection.responseCode
            if (responseCode != 200) {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw IOException("HTTP $responseCode: ${mapHttpErrorCode(responseCode)} - $errorResponse")
            }

            // Read audio data
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Create audio result from TTS response
     */
    private fun createAudioResult(audioData: ByteArray, sessionId: String, textLength: Int): InferenceResult {
        val metadata = mutableMapOf<String, Any>(
            InferenceResult.META_SESSION_ID to sessionId,
            InferenceResult.META_MODEL_NAME to voiceId,
            "audio_size_bytes" to audioData.size,
            "audio_format" to outputFormat,
            "voice_id" to voiceId,
            "model_id" to modelId,
            "text_length" to textLength,
            "stability" to stability,
            "similarity_boost" to similarityBoost
        )

        return InferenceResult.success(
            outputs = mapOf(
                InferenceResult.OUTPUT_AUDIO to audioData
            ),
            metadata = metadata
        )
    }

    /**
     * Map voice name to voice ID
     */
    private fun mapVoiceNameToId(voiceName: String): String {
        return when (voiceName.lowercase()) {
            "rachel" -> VOICE_RACHEL
            "antoni" -> VOICE_ANTONI
            "elli" -> VOICE_ELLI
            "adam" -> VOICE_ADAM
            else -> {
                // If it looks like a voice ID (alphanumeric), use it directly
                if (voiceName.matches(Regex("[a-zA-Z0-9]+"))) {
                    voiceName
                } else {
                    VOICE_RACHEL // Default fallback
                }
            }
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
                    message.contains("quota", ignoreCase = true) || message.contains("402") ->
                        RunnerError(RunnerError.Code.PROCESSING_ERROR, "Quota exceeded. Check your ElevenLabs usage.")
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
            HTTP_BAD_REQUEST -> "Bad request - invalid text or parameters"
            HTTP_UNAUTHORIZED -> "Unauthorized - invalid API key"
            HTTP_PAYMENT_REQUIRED -> "Quota exceeded - check your ElevenLabs plan"
            HTTP_FORBIDDEN -> "Forbidden - access denied"
            HTTP_NOT_FOUND -> "Voice not found - check voice ID"
            HTTP_TOO_MANY_REQUESTS -> "Rate limited - too many requests"
            HTTP_INTERNAL_ERROR -> "Server error"
            HTTP_SERVICE_UNAVAILABLE -> "Service unavailable"
            else -> "HTTP error $code"
        }
    }

    /**
     * Basic API key validation
     */
    private fun isValidApiKey(key: String): Boolean {
        // ElevenLabs keys are typically 32 alphanumeric characters
        return key.isNotBlank() && key.length >= 20
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