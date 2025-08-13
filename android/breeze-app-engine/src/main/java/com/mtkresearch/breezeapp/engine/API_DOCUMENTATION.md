# BreezeApp Engine API Documentation

This document provides comprehensive documentation for the BreezeApp Engine API, following Clean Architecture principles.

## Architecture Overview

The BreezeApp Engine follows a layered Clean Architecture approach:

```
┌────────────────────────────────────┐
│           Presentation             │
│  (UI Components, ViewModels)      │
├────────────────────────────────────┤
│             Use Cases              │
│  (Request Processing, Coordination)│
├────────────────────────────────────┤
│             Domain                 │
│  (Models, Interfaces, Business Rules)│
├────────────────────────────────────┤
│          Infrastructure            │
│  (Android Services, External APIs) │
└────────────────────────────────────┘
```

## Core Components

### 1. EngineUtils
Unified utility class providing common functionality:

#### Audio Processing
```kotlin
// Create and configure AudioTrack for TTS playback
fun createAudioTrack(sampleRate: Int): AudioTrack

// Write audio samples to AudioTrack
fun writeAudioSamples(track: AudioTrack, samples: FloatArray, stopped: Boolean): Int

// Prepare AudioTrack for new playback
fun prepareForPlayback(track: AudioTrack)

// Stop and cleanup AudioTrack
fun stopAndCleanup(track: AudioTrack)

// Check if RECORD_AUDIO permission is granted
fun hasRecordAudioPermission(context: Context): Boolean

// Create and configure AudioRecord for ASR microphone input
fun createAudioRecord(context: Context): AudioRecord?

// Start recording with AudioRecord
fun startRecording(audioRecord: AudioRecord): Boolean

// Stop recording and release AudioRecord
fun stopAndReleaseAudioRecord(audioRecord: AudioRecord)

// Read audio samples from AudioRecord
fun readAudioSamples(audioRecord: AudioRecord, buffer: ShortArray): Int

// Create a Flow that continuously reads audio from microphone
fun createMicrophoneAudioFlow(context: Context, isRecording: AtomicBoolean): Flow<ShortArray>

// Convert ShortArray (PCM16) to FloatArray for Sherpa processing
fun convertPcm16ToFloat(audioData: ShortArray): FloatArray

// Convert ByteArray (PCM16) to FloatArray for Sherpa processing
fun convertPcm16BytesToFloat(audioData: ByteArray): FloatArray

// Get the sample rate used for ASR recording
fun getAsrSampleRate(): Int

// Simple WAV header parser for PCM 16-bit little-endian files
fun tryParseWav(bytes: ByteArray): WavInfo?

// Extract PCM16 ShortArray from either raw PCM16 bytes or WAV bytes
fun extractPcm16(bytes: ByteArray): Pair<ShortArray, WavInfo?>

// Save PCM16 data to a WAV file
fun savePcm16AsWav(file: File, pcm: ShortArray, sampleRate: Int, channels: Int = 1, bitsPerSample: Int = 16)

// Save a diagnostics WAV file under app files/diagnostics
fun saveDiagnosticsWav(context: Context, fileName: String, pcm: ShortArray, sampleRate: Int, channels: Int = 1): File?

// Prepare ASR input from raw bytes using Sherpa's WaveReader when possible
fun prepareAsrFloatSamples(
    context: Context,
    sessionId: String,
    audioBytes: ByteArray,
    defaultSampleRate: Int = SAMPLE_RATE_ASR,
    resampleToDefault: Boolean = false,
    saveDiagnostics: Boolean = false
): Pair<FloatArray, Int>

// Simple high-quality linear resampler for FloatArray audio
fun resampleLinear(input: FloatArray, srcRate: Int, dstRate: Int): FloatArray

// Convert FloatArray [-1,1] to PCM16 shorts with clipping
fun floatToPcm16(input: FloatArray): ShortArray
```

#### Asset Management
```kotlin
// Copy assets directory to external files directory
fun copyAssetsToExternalFiles(context: Context, assetPath: String): String

// Copy assets directory to internal files directory
fun copyAssetsToInternalFiles(context: Context, assetPath: String): String
```

#### TTS Model Configuration
```kotlin
// TTS Model types supported
enum class TtsModelType {
    VITS_MR_20250709,       // Your custom model
    VITS_MELO_ZH_EN,        // VITS MeloTTS model
    VITS_PIPER_EN_US_AMY,   // English Piper model
    VITS_ICEFALL_ZH,        // Chinese VITS model
    MATCHA_ICEFALL_ZH,      // Chinese Matcha model
    KOKORO_EN,              // English Kokoro model
    CUSTOM                  // Custom configuration
}

// TTS Model configuration data class
data class TtsModelConfig(
    val modelDir: String,
    val modelName: String = "",
    val acousticModelName: String = "",
    val vocoder: String = "",
    val voices: String = "",
    val lexicon: String = "",
    val dataDir: String = "",
    val dictDir: String = "",
    val ruleFsts: String = "",
    val ruleFars: String = "",
    val description: String = ""
)

// Get predefined TTS model configuration
fun getTtsModelConfig(type: TtsModelType): TtsModelConfig

// Create Sherpa OfflineTtsConfig from model configuration
fun createOfflineTtsConfig(
    context: Context,
    modelConfig: TtsModelConfig,
    useExternalStorage: Boolean = true
): com.k2fsa.sherpa.onnx.OfflineTtsConfig?

// Create custom TTS configuration using official Sherpa function
fun createCustomConfig(
    context: Context,
    modelDir: String,
    modelName: String = "",
    acousticModelName: String = "",
    vocoder: String = "",
    voices: String = "",
    lexicon: String = "",
    dataDir: String = "",
    dictDir: String = "",
    ruleFsts: String = "",
    ruleFars: String = "",
    useExternalStorage: Boolean = true
): com.k2fsa.sherpa.onnx.OfflineTtsConfig?

// Get all available model configurations
fun getAllModelConfigs(): List<Pair<TtsModelType, TtsModelConfig>>

// Validate if model assets exist in the assets folder
fun validateModelAssets(context: Context, modelConfig: TtsModelConfig): Boolean
```

### 2. UnifiedModelManager
Centralized model management system:

```kotlin
// Model categories
enum class ModelCategory {
    LLM,    // Large Language Models
    ASR,    // Automatic Speech Recognition
    TTS,    // Text-to-Speech
    VLM     // Vision Language Models
}

// Model state
data class ModelState(
    val modelInfo: ModelInfo,
    val status: Status,
    val downloadProgress: Int = 0,
    val downloadSpeed: Long = 0,
    val downloadEta: Long = -1,
    val storageSize: Long = 0,
    val category: ModelCategory = ModelCategory.UNKNOWN,
    val isDefault: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    enum class Status {
        AVAILABLE,      // Available for download
        DOWNLOADING,    // Currently downloading
        DOWNLOADED,     // Successfully downloaded
        PAUSED,        // Download paused
        ERROR,         // Download/validation error
        INSTALLING,    // Post-download validation/setup
        READY          // Ready for inference
    }
}

// Get all models organized by category
fun getModelsByCategory(): Map<ModelCategory, List<ModelState>>

// Get all available models for a specific category
fun getAvailableModels(category: ModelCategory): List<ModelState>

// Get downloaded models for a category
fun getDownloadedModels(category: ModelCategory): List<ModelState>

// Get default model for a category
fun getDefaultModel(category: ModelCategory): ModelState?

// Get model state by ID
fun getModelState(modelId: String): ModelState?

// Download a specific model
fun downloadModel(
    modelId: String,
    listener: ModelDownloadListener? = null
): DownloadHandle

// Ensure default model for category is ready
fun ensureDefaultModelReady(
    category: ModelCategory,
    listener: ModelDownloadListener? = null
)

// Delete a model
fun deleteModel(modelId: String): Boolean

// Download all default models for specified categories
fun downloadDefaultModels(
    categories: List<ModelCategory>,
    listener: BulkDownloadListener? = null
)

// Clean up old model versions and temporary files
fun cleanupStorage(): StorageCleanupResult

// Calculate total storage used
fun calculateTotalStorageUsed(): Long

// Get storage usage by category
fun getStorageUsageByCategory(): Map<ModelCategory, Long>
```

### 3. EnhancedConfigurationManager
Flexible configuration system:

```kotlin
// Configuration sources
enum class ConfigSource {
    ASSET,      // Static asset configuration
    REMOTE,     // Remote configuration (HTTP/HTTPS)
    PREFERENCES // User preferences
}

// Get current configuration
fun getCurrentConfig(): EngineConfig

// Update configuration dynamically
fun updateConfig(newConfig: EngineConfig)

// Load configuration from source
fun loadConfigFromSource(source: ConfigSource): EngineConfig?

// Validate configuration
fun validateConfig(config: EngineConfig): Boolean

// Get configuration change flow
fun getConfigChanges(): StateFlow<EngineConfig>

// Reset to default configuration
fun resetToDefault()
```

### 4. ServiceOrchestrator
Coordinates service components:

```kotlin
// Initialize all service components
fun initialize()

// Get the service binder for client connections
fun getServiceBinder(): IBinder?

// Update foreground service type for microphone access
fun updateForegroundServiceType(includeMicrophone: Boolean)

// Get the current service type for microphone access
fun getCurrentServiceType(): Int

// Force foreground service state for microphone access
fun forceForegroundForMicrophone()

// Cleanup all resources
fun cleanup()

// Set service instance for components that need it
fun setServiceInstance(service: Service)
```

### 5. BreezeAppEngineCore
Business logic coordinator:

```kotlin
// Initialize the engine core and start background monitoring
fun initialize()

// Shutdown the engine core and cleanup resources
fun shutdown()

// Process an inference request through the AI engine
suspend fun processInferenceRequest(request: InferenceRequest): InferenceResult

// Process a streaming inference request through the AI engine
suspend fun processStreamingRequest(request: InferenceRequest): Flow<InferenceResult>

// Get current engine status and metrics
fun getEngineStatus(): Map<String, Any>

// Get detailed performance metrics
fun getPerformanceMetrics(): Map<String, Any>

// Generate a unique request ID
fun generateRequestId(): String
```

### 6. AIEngineManager
AI request processing coordinator:

```kotlin
// Set default runners mapping
fun setDefaultRunners(mappings: Map<CapabilityType, String>)

// Process inference request
suspend fun process(
    request: InferenceRequest,
    capability: CapabilityType,
    preferredRunner: String? = null
): InferenceResult

// Process streaming inference request
fun processStream(
    request: InferenceRequest,
    capability: CapabilityType,
    preferredRunner: String? = null
): Flow<InferenceResult>

// Cancel an active request
fun cancelRequest(requestId: String): Boolean

// Cleanup all resources
fun cleanup()

// Unload all models to save memory
fun unloadAllModels()

// Force cleanup all resources for abnormal termination
fun forceCleanupAll()
```

## Usage Examples

### 1. Initialize and Use the Engine
```kotlin
// Initialize the engine
val engineCore = BreezeAppEngineCore(context)
engineCore.initialize()

// Process a simple inference request
val request = InferenceRequest(
    sessionId = "test-session-001",
    inputs = mapOf(InferenceRequest.INPUT_TEXT to "Hello, how are you?"),
    params = mapOf("temperature" to 0.7f, "max_tokens" to 150)
)

val result = engineCore.processInferenceRequest(request)
if (result.error == null) {
    val responseText = result.outputs[InferenceResult.OUTPUT_TEXT] as String
    Log.d("Engine", "Response: $responseText")
} else {
    Log.e("Engine", "Error: ${result.error?.message}")
}

// Shutdown when done
engineCore.shutdown()
```

### 2. Streaming Inference
```kotlin
// Process a streaming inference request
val request = InferenceRequest(
    sessionId = "stream-session-001",
    inputs = mapOf(InferenceRequest.INPUT_TEXT to "Tell me a story about a brave knight."),
    params = mapOf("temperature" to 0.8f, "stream" to true)
)

lifecycleScope.launch {
    engineCore.processStreamingRequest(request).collect { result ->
        if (result.error == null) {
            val responseText = result.outputs[InferenceResult.OUTPUT_TEXT] as String
            if (result.partial) {
                // Update UI with partial result
                updatePartialResponse(responseText)
            } else {
                // Final result
                updateFinalResponse(responseText)
            }
        } else {
            // Handle error
            handleError(result.error?.message ?: "Unknown error")
        }
    }
}
```

### 3. Audio Processing with TTS
```kotlin
// Generate speech from text
val ttsRequest = InferenceRequest(
    sessionId = "tts-session-001",
    inputs = mapOf(
        InferenceRequest.INPUT_TEXT to "Hello, this is a text-to-speech test.",
        "speaker_id" to 0,
        "speed" to 1.0f
    ),
    params = mapOf("model" to "vits-mr-20250709")
)

val ttsResult = engineCore.processInferenceRequest(ttsRequest)
if (ttsResult.error == null) {
    val audioData = ttsResult.outputs[InferenceResult.OUTPUT_AUDIO] as ByteArray
    // Play audio or save to file
    playAudio(audioData)
}
```

### 4. Audio Processing with ASR
```kotlin
// Transcribe audio
val asrRequest = InferenceRequest(
    sessionId = "asr-session-001",
    inputs = mapOf(
        InferenceRequest.INPUT_AUDIO to audioByteArray,
        "language" to "en"
    ),
    params = mapOf("stream" to false)
)

val asrResult = engineCore.processInferenceRequest(asrRequest)
if (asrResult.error == null) {
    val transcript = asrResult.outputs[InferenceResult.OUTPUT_TEXT] as String
    Log.d("ASR", "Transcript: $transcript")
}
```

### 5. Model Management
```kotlin
// Get model manager instance
val modelManager = UnifiedModelManager.getInstance(context)

// Get available LLM models
val llmModels = modelManager.getAvailableModels(UnifiedModelManager.ModelCategory.LLM)

// Download a specific model
modelManager.downloadModel("breeze2-3b-8w16a-250630-npu") { modelId, success ->
    if (success) {
        Log.d("Model", "Model $modelId downloaded successfully")
    } else {
        Log.e("Model", "Failed to download model $modelId")
    }
}

// Ensure default model is ready
modelManager.ensureDefaultModelReady(UnifiedModelManager.ModelCategory.LLM) { modelId, success ->
    if (success) {
        Log.d("Model", "Default LLM model ready")
    } else {
        Log.e("Model", "Failed to prepare default LLM model")
    }
}
```

## Error Handling

The BreezeApp Engine uses a standardized error handling approach:

```kotlin
// All errors are encapsulated in RunnerError
data class RunnerError(
    val code: String,           // Error code (e.g., E101, E201)
    val message: String,        // Human-readable error message
    val recoverable: Boolean = false, // Whether the error is recoverable
    val cause: Throwable? = null     // Original exception if available
)

// Common error codes:
// E101 - Runtime error
// E201 - Model not loaded
// E301 - Invalid input
// E401 - Permission denied
// E404 - Runner not found
// E405 - Capability not supported
// E406 - Streaming not supported
// E501 - Model load failed
// E502 - Insufficient memory
```

## Best Practices

1. **Always initialize before use**: Call `initialize()` on core components before using them
2. **Handle errors gracefully**: Check for `result.error` in all inference results
3. **Use streaming for long operations**: For LLM responses or TTS generation, use streaming APIs
4. **Manage resources properly**: Call `shutdown()` or `cleanup()` when components are no longer needed
5. **Follow capability-based routing**: Use `CapabilityType` to route requests to appropriate runners
6. **Use proper threading**: Offload heavy operations to background threads using coroutines
7. **Validate inputs**: Always validate input data before processing
8. **Log appropriately**: Use appropriate log levels (DEBUG, INFO, WARN, ERROR) for different scenarios

## Migration Guide

For existing code using the legacy architecture:

1. Replace direct calls to `AudioUtil`, `SherpaTtsConfigUtil`, and `AssetCopyUtil` with `EngineUtils`
2. Replace `ModelManager`, `ModelRegistry`, and `ModelVersionStore` with `UnifiedModelManager`
3. Replace direct service access with the new `ServiceOrchestrator` pattern
4. Update configuration management to use `EnhancedConfigurationManager`
5. Use the new `BreezeAppEngineCore` for business logic instead of direct service calls

The new architecture provides better separation of concerns, easier testing, and improved maintainability while preserving all existing functionality.