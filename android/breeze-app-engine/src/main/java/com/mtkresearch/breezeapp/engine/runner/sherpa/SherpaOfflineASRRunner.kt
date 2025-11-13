package com.mtkresearch.breezeapp.engine.runner.sherpa

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import com.k2fsa.sherpa.onnx.WaveReader
import com.mtkresearch.breezeapp.engine.annotation.AIRunner
import com.mtkresearch.breezeapp.engine.annotation.RunnerPriority
import com.mtkresearch.breezeapp.engine.annotation.VendorType
import com.mtkresearch.breezeapp.engine.util.EngineUtils
import com.mtkresearch.breezeapp.engine.runner.core.RunnerInfo
import com.mtkresearch.breezeapp.engine.runner.core.ParameterSchema
import com.mtkresearch.breezeapp.engine.runner.core.ParameterType
import com.mtkresearch.breezeapp.engine.runner.core.SelectionOption
import com.mtkresearch.breezeapp.engine.model.*
import com.mtkresearch.breezeapp.engine.runner.sherpa.base.BaseSherpaRunner

/**
 * SherpaOfflineASRRunner - Offline ASR runner using Sherpa ONNX
 *
 * This runner loads a Sherpa ONNX offline ASR model and performs inference on PCM16 audio.
 * Unlike the online version, this processes complete audio files in one go.
 *
 * Model files must be extracted to assets before loading.
 */
@AIRunner(
    vendor = VendorType.SHERPA,
    priority = RunnerPriority.HIGH,
    capabilities = [CapabilityType.ASR],
    defaultModel = "Breeze-ASR-25-onnx"
)
class SherpaOfflineASRRunner(context: Context) : BaseSherpaRunner(context) {
    companion object {
        private const val TAG = "SherpaOfflineASRRunner"
        private const val DEFAULT_SAMPLE_RATE = 16000
    }

    private var recognizer: OfflineRecognizer? = null
    private var modelType: Int = 0 // Default to Breeze-ASR-25-onnx (Type 0)

    override fun getTag(): String = TAG

    override fun load(modelId: String, settings: EngineSettings, initialParams: Map<String, Any>): Boolean {
        modelName = modelId
        return try {
            // Parse model type from config
            modelType = parseModelTypeFromSettings(modelId, settings)
            Log.i(TAG, "Using offline model type $modelType: ${getModelDescription(modelType)}")

            Log.i(TAG, "Start to initialize offline model")
            initModel()
            Log.i(TAG, "Finished initializing offline model")

            // CRITICAL FIX: Set isLoaded flag to true after successful initialization
            isLoaded.set(true)
            Log.i(TAG, "Model loaded successfully - isLoaded flag set to true")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SherpaOfflineASRRunner", e)
            isLoaded.set(false) // Ensure flag is false on failure
            false
        }
    }

    private fun initModel() {
        Log.i(TAG, "Initializing offline model with type $modelType")

        // Create offline recognizer with external storage configuration
        val featConfig = FeatureConfig(sampleRate = DEFAULT_SAMPLE_RATE, featureDim = 80)
        val modelConfig = createOfflineModelConfig(modelType)

        // Validate all required model files exist before initialization
        validateModelFilesExist(modelConfig)

        val config = OfflineRecognizerConfig(
            featConfig = featConfig,
            modelConfig = modelConfig
        )

        recognizer = OfflineRecognizer(config = config)

        Log.i(TAG, "Offline model initialized successfully with type $modelType (${getModelDescription(modelType)})")
    }
    
    /**
     * Create offline model configuration using external storage paths
     * Models are loaded from context.filesDir/models/ directory (downloaded models)
     */
    private fun createOfflineModelConfig(type: Int): OfflineModelConfig {
        val modelsDir = context.filesDir.absolutePath + "/models"

        return when (type) {
            0 -> {
                val modelDir = "$modelsDir/Breeze-ASR-25-onnx"
                // Breeze-ASR-25 is Whisper-compatible but with separate .weights files
                OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = "$modelDir/breeze-asr-25-half-encoder.int8.onnx",
                        decoder = "$modelDir/breeze-asr-25-half-decoder.int8.onnx",
                        language = "zh"
                    ),
                    tokens = "$modelDir/breeze-asr-25-half-tokens.txt",
                    modelType = "whisper"
                )
            }
            1 -> {
                val modelDir = "$modelsDir/sherpa-onnx-whisper-base"
                OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = "$modelDir/base-encoder.onnx",
                        decoder = "$modelDir/base-decoder.onnx"
                    ),
                    tokens = "$modelDir/base-tokens.txt",
                    modelType = "whisper"
                )
            }
            2 -> {
                val modelDir = "$modelsDir/sherpa-onnx-whisper-medium"
                OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = "$modelDir/medium-encoder.int8.onnx",
                        decoder = "$modelDir/medium-decoder.int8.onnx"
                    ),
                    tokens = "$modelDir/medium-tokens.txt",
                    modelType = "whisper"
                )
            }
            else -> {
                throw IllegalArgumentException("Unsupported offline model type: $type")
            }
        }
    }

    /**
     * Validate that all required model files exist before creating OfflineRecognizer
     * This prevents native crashes from invalid recognizer initialization
     */
    private fun validateModelFilesExist(modelConfig: OfflineModelConfig) {
        val filesToCheck = mutableListOf<String>()

        // Add whisper model files
        modelConfig.whisper.encoder.takeIf { it.isNotEmpty() }?.let { filesToCheck.add(it) }
        modelConfig.whisper.decoder.takeIf { it.isNotEmpty() }?.let { filesToCheck.add(it) }

        // Add tokens file
        modelConfig.tokens.takeIf { it.isNotEmpty() }?.let { filesToCheck.add(it) }

        // Check each file
        val missingFiles = filesToCheck.filter { path ->
            val file = java.io.File(path)
            !file.exists()
        }

        if (missingFiles.isNotEmpty()) {
            val errorMsg = "Required ASR model files missing (${missingFiles.size}/${filesToCheck.size}):\n" +
                    missingFiles.joinToString("\n") { "  - ${it.substringAfterLast('/')}" }
            Log.e(TAG, errorMsg)
            throw IllegalStateException(errorMsg)
        }

        Log.d(TAG, "All ${filesToCheck.size} required model files validated successfully")
    }

    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        // Validate model is loaded
        validateModelLoaded()?.let { return it }
        
        // Validate input data
        val (audioData, error) = validateInput<ByteArray>(input, InferenceRequest.INPUT_AUDIO)
        error?.let { return it }
        
        return try {
            Log.d(TAG, "Processing offline ASR with ${audioData!!.size} bytes of audio data")
            
            val startTime = System.currentTimeMillis()
            
            // Centralized ASR input prep using EngineUtils + Sherpa WaveReader (no extra resampling)
            val (floatSamples, effectiveSampleRate) = EngineUtils.prepareAsrFloatSamples(
                context = context,
                sessionId = input.sessionId,
                audioBytes = audioData,
                defaultSampleRate = DEFAULT_SAMPLE_RATE,
                resampleToDefault = false,
                saveDiagnostics = false
            )

            Log.d(TAG, "Prepared ${floatSamples.size} samples for offline processing @ ${effectiveSampleRate} Hz")
            
            // Create stream and process following official offline example with null safety
            val recognizerInstance = recognizer ?: return InferenceResult.error(
                RunnerError.runtimeError("Recognizer not initialized", null)
            )
            
            val stream = recognizerInstance.createStream()
            if (stream == null) {
                return InferenceResult.error(RunnerError.runtimeError("Failed to create stream", null))
            }
            
            // Align with Sherpa official example: pass samples with their true sampleRate.
            // Sherpa may resample internally if needed, matching reference behavior.
            stream.acceptWaveform(floatSamples, sampleRate = effectiveSampleRate)
            recognizerInstance.decode(stream)
            
            val result = recognizerInstance.getResult(stream)
            val elapsed = System.currentTimeMillis() - startTime
            
            Log.d(TAG, "Offline ASR result: '${result?.text ?: "<empty>"}' (${elapsed}ms)")
            
            stream.release()
            
            if (result == null || result.text.isBlank()) {
                return InferenceResult.error(RunnerError.runtimeError("ASR recognition failed or returned empty result"))
            }
            
            InferenceResult.success(
                outputs = mapOf(InferenceResult.OUTPUT_TEXT to result.text),
                metadata = mapOf(
                    InferenceResult.META_CONFIDENCE to 0.95f, // Sherpa does not provide confidence
                    InferenceResult.META_PROCESSING_TIME_MS to elapsed,
                    InferenceResult.META_MODEL_NAME to modelName,
                    InferenceResult.META_SESSION_ID to input.sessionId
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in SherpaOfflineASRRunner.run", e)
            InferenceResult.error(RunnerError.runtimeError(e.message ?: "Unknown error", e))
        }
    }

    override fun releaseModel() {
        recognizer?.release()
        recognizer = null
    }

    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.ASR)

    override fun getRunnerInfo(): RunnerInfo = RunnerInfo(
        name = "SherpaOfflineASRRunner",
        version = "1.0.0",
        capabilities = getCapabilities(),
        description = "Sherpa ONNX offline ASR runner"
    )

    override fun getParameterSchema(): List<ParameterSchema> {
        return listOf(
            ParameterSchema(
                name = "model",
                displayName = "ASR Model",
                description = "Select the Whisper ASR model to use. Higher capacity models provide better accuracy but require more RAM.",
                type = ParameterType.SelectionType(
                    options = listOf(
                        SelectionOption(
                            key = "Breeze-ASR-25-onnx",
                            displayName = "Breeze ASR 2.5 (4GB RAM)",
                            description = "Best accuracy, requires 4GB RAM"
                        ),
                        SelectionOption(
                            key = "sherpa-onnx-whisper-medium",
                            displayName = "Whisper Medium (2GB RAM)",
                            description = "Balanced accuracy and memory usage"
                        ),
                        SelectionOption(
                            key = "sherpa-onnx-whisper-base",
                            displayName = "Whisper Base (1GB RAM)",
                            description = "Basic accuracy, minimal memory usage"
                        )
                    ),
                    allowMultiple = false
                ),
                defaultValue = "Breeze-ASR-25-onnx",
                isRequired = true,
                category = "Model Selection"
            ),
            ParameterSchema(
                name = "language",
                displayName = "Language",
                description = "Speech recognition language. Whisper models support multilingual recognition and automatic language detection.",
                type = ParameterType.SelectionType(
                    options = listOf(
                        SelectionOption(
                            key = "auto",
                            displayName = "Auto Detect",
                            description = "Automatically detect spoken language (Whisper models)"
                        ),
                        SelectionOption(
                            key = "zh",
                            displayName = "中文 (Chinese)",
                            description = "Mandarin Chinese (simplified and traditional)"
                        ),
                        SelectionOption(
                            key = "en",
                            displayName = "English",
                            description = "English language"
                        )
                    ),
                    allowMultiple = false
                ),
                defaultValue = "auto",
                isRequired = false,
                category = "Recognition Settings"
            )
        )
    }

    /**
     * Parse model type from ModelConfig - auto-detect based on model name or parameters
     * Automatically detects model type based on model ID/name
     */
    private fun parseModelTypeFromSettings(modelId: String, settings: EngineSettings): Int {
        // First try to parse from parameters
        val runnerParams = settings.getRunnerParameters("SherpaOfflineASRRunner")
        val modelTypeParam = runnerParams["model_type"]
        if (modelTypeParam != null) {
            val type = when (modelTypeParam) {
                is Int -> modelTypeParam
                is String -> modelTypeParam.toIntOrNull()
                else -> null
            }
            if (type != null && isValidOfflineModelType(type)) {
                Log.d(TAG, "Offline model type $type parsed from parameters: $modelTypeParam")
                return type
            }
        }
        
        // Check for model parameter (standard key) or model_id parameter (legacy key)
        val modelParam = (runnerParams["model"] as? String) ?: (runnerParams["model_id"] as? String)
        if (modelParam != null) {
            val detectedType = when {
                modelParam.contains("breeze-asr-25-onnx", ignoreCase = true) -> 0
                modelParam.contains("sherpa-onnx-whisper-base", ignoreCase = true) -> 1
                modelParam.contains("sherpa-onnx-whisper-medium", ignoreCase = true) -> 2
                else -> -1
            }
            if (detectedType != -1) {
                Log.d(TAG, "Detected offline model type $detectedType from model parameter: $modelParam")
                return detectedType
            }
        }

        // Auto-detect based on model name/ID
        val modelName = modelId.lowercase()
        val detectedType = when {
            modelName.contains("breeze-asr-25-onnx") -> 0
            modelName.contains("sherpa-onnx-whisper-base") -> 1
            modelName.contains("sherpa-onnx-whisper-medium") -> 2
            else -> 0 // Default to whisper-base instead of Breeze-ASR-25
        }
        
        Log.d(TAG, "Auto-detected offline model type $detectedType for model: $modelId")
        return detectedType
    }

    /**
     * Check if offline model type is valid/supported
     */
    private fun isValidOfflineModelType(type: Int): Boolean {
        return type in listOf(0, 1, 2)
    }

    /**
     * Get supported offline model types
     */
    fun getSupportedOfflineModelTypes(): List<Int> {
        return listOf(0, 1, 2)
    }

    /**
     * Get human-readable description of the offline model type
     */
    private fun getModelDescription(type: Int): String {
        return when (type) {
            0 -> "Breeze-ASR-25-onnx"
            1 -> "sherpa-onnx-whisper-base"
            2 -> "sherpa-onnx-whisper-medium"
            else -> "Unknown offline model type"
        }
    }


    /**
     * Public method to set offline model type
     */
    fun setModelType(type: Int) {
        if (!isValidOfflineModelType(type)) {
            throw IllegalArgumentException("Invalid offline model type: $type. Supported types: ${getSupportedOfflineModelTypes()}")
        }
        if (isLoaded.get()) {
            Log.w(TAG, "Model is already loaded. Unload first before changing model type.")
            return
        }
        modelType = type
        Log.i(TAG, "Offline model type set to $type: ${getModelDescription(type)}")
    }
}