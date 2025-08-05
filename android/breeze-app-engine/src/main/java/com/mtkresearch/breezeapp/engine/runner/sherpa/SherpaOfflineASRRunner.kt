package com.mtkresearch.breezeapp.engine.runner.sherpa

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import com.mtkresearch.breezeapp.engine.data.runner.core.BaseRunner
import com.mtkresearch.breezeapp.engine.data.runner.core.BaseRunnerCompanion
import com.mtkresearch.breezeapp.engine.data.runner.core.RunnerInfo
import com.mtkresearch.breezeapp.engine.domain.model.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SherpaOfflineASRRunner - Offline ASR runner using Sherpa ONNX
 *
 * This runner loads a Sherpa ONNX offline ASR model and performs inference on PCM16 audio.
 * Unlike the online version, this processes complete audio files in one go.
 *
 * Model files must be extracted to assets before loading.
 */
class SherpaOfflineASRRunner(private val context: Context) : BaseRunner {
    companion object : BaseRunnerCompanion {
        private const val TAG = "SherpaOfflineASRRunner"
        private const val SAMPLE_RATE = 16000

        @JvmStatic
        override fun isSupported(): Boolean = true
    }

    private val isLoaded = AtomicBoolean(false)
    private var recognizer: OfflineRecognizer? = null
    private var modelName: String = ""
    private var modelType: Int = 0 // Default to paraformer zh (Type 0)

    override fun load(config: ModelConfig): Boolean {
        return try {
            Log.d(TAG, "Loading SherpaOfflineASRRunner with config: ${config.modelName}")
            modelName = config.modelName
            
            // Parse model type from config
            modelType = parseModelTypeFromConfig(config)
            Log.i(TAG, "Using offline model type $modelType: ${getModelDescription(modelType)}")
            
            Log.i(TAG, "Start to initialize offline model")
            initModel()
            Log.i(TAG, "Finished initializing offline model")
            
            isLoaded.set(true)
            Log.i(TAG, "SherpaOfflineASRRunner loaded successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load SherpaOfflineASRRunner", e)
            isLoaded.set(false)
            false
        }
    }

    private fun initModel() {
        Log.i(TAG, "Initializing offline model with type $modelType")
        
        // Create offline recognizer with basic configuration
        // Using minimal configuration similar to the official example
        val featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80)
        val modelConfig = createOfflineModelConfig(modelType)
        
        val config = OfflineRecognizerConfig(
            featConfig = featConfig,
            modelConfig = modelConfig
        )

        recognizer = OfflineRecognizer(config = config)
        
        Log.i(TAG, "Offline model initialized successfully with type $modelType (${getModelDescription(modelType)})")
    }
    
    /**
     * Create basic offline model configuration for the given type
     */
    private fun createOfflineModelConfig(type: Int): OfflineModelConfig {
        return when (type) {
            0 -> OfflineModelConfig(
                paraformer = OfflineParaformerModelConfig(
                    model = "model.onnx"
                ),
                tokens = "tokens.txt",
                modelType = "paraformer"
            )
            2 -> OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = "encoder.onnx",
                    decoder = "decoder.onnx"
                ),
                tokens = "tokens.txt",
                modelType = "whisper"
            )
            else -> OfflineModelConfig(
                paraformer = OfflineParaformerModelConfig(
                    model = "model.onnx"
                ),
                tokens = "tokens.txt",
                modelType = "paraformer"
            )
        }
    }

    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        if (!isLoaded.get()) {
            return InferenceResult.error(RunnerError.modelNotLoaded())
        }
        
        return try {
            val audioData = input.inputs[InferenceRequest.INPUT_AUDIO] as? ByteArray
            if (audioData == null) {
                return InferenceResult.error(RunnerError.invalidInput("Audio data required for offline ASR processing"))
            }
            
            Log.d(TAG, "Processing offline ASR with ${audioData.size} bytes of audio data")
            
            val startTime = System.currentTimeMillis()
            
            // Convert ByteArray (PCM16) to FloatArray as required by Sherpa
            val floatSamples = convertPcm16ToFloat(audioData)
            
            Log.d(TAG, "Converted ${floatSamples.size} samples for offline processing")
            
            // Create stream and process following official offline example
            val stream = recognizer!!.createStream()
            stream.acceptWaveform(floatSamples, sampleRate = SAMPLE_RATE)
            recognizer!!.decode(stream)
            
            val result = recognizer!!.getResult(stream)
            val elapsed = System.currentTimeMillis() - startTime
            
            Log.d(TAG, "Offline ASR result: '${result.text}' (${elapsed}ms)")
            
            stream.release()
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

    override fun unload() {
        Log.d(TAG, "Unloading SherpaOfflineASRRunner")
        recognizer?.release()
        recognizer = null
        isLoaded.set(false)
    }

    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.ASR)

    override fun isLoaded(): Boolean = isLoaded.get()

    override fun getRunnerInfo(): RunnerInfo = RunnerInfo(
        name = "SherpaOfflineASRRunner",
        version = "1.0.0",
        capabilities = getCapabilities(),
        description = "Sherpa ONNX offline ASR runner",
        isMock = false
    )

    // ========== Private Helper Methods ==========

    /**
     * Convert PCM16 ByteArray to FloatArray as required by Sherpa ONNX
     * PCM16 format: little-endian 16-bit samples
     */
    private fun convertPcm16ToFloat(audioData: ByteArray): FloatArray {
        return FloatArray(audioData.size / 2) { i ->
            val sample = ((audioData[i * 2 + 1].toInt() and 0xFF) shl 8) or 
                        (audioData[i * 2].toInt() and 0xFF)
            sample / 32768.0f
        }
    }

    /**
     * Parse model type from ModelConfig - using parameters map
     * Defaults to type 0 (paraformer zh) for offline models
     */
    private fun parseModelTypeFromConfig(config: ModelConfig): Int {
        val modelTypeParam = config.parameters["model_type"]
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
        
        // Default fallback to Type 0 (paraformer zh)
        Log.d(TAG, "Using default offline model type 0 (paraformer zh)")
        return 0
    }

    /**
     * Check if offline model type is valid/supported
     */
    private fun isValidOfflineModelType(type: Int): Boolean {
        return type in listOf(0, 2, 5, 6, 15, 21, 24, 25, 31)
    }

    /**
     * Get supported offline model types
     */
    fun getSupportedOfflineModelTypes(): List<Int> {
        return listOf(0, 2, 5, 6, 15, 21, 24, 25, 31)
    }

    /**
     * Get human-readable description of the offline model type
     */
    private fun getModelDescription(type: Int): String {
        return when (type) {
            0 -> "Paraformer zh (2023-09-14)"
            2 -> "Whisper tiny.en"
            5 -> "Zipformer multi zh-hans (2023-9-2)"
            6 -> "NeMo CTC en-citrinet-512"
            15 -> "SenseVoice zh-en-ja-ko-yue (2024-07-17)"
            21 -> "Moonshine tiny en int8"
            24 -> "Fire Red ASR large zh_en (2025-02-16)"
            25 -> "Dolphin base CTC multi-lang int8 (2025-04-02)"
            31 -> "Zipformer CTC zh int8 (2025-07-03)"
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