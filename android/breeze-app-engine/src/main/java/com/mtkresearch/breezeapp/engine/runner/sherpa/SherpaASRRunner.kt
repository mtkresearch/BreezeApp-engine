package com.mtkresearch.breezeapp.engine.runner.sherpa

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import com.mtkresearch.breezeapp.engine.annotation.AIRunner
import com.mtkresearch.breezeapp.engine.annotation.RunnerPriority
import com.mtkresearch.breezeapp.engine.annotation.VendorType
import com.mtkresearch.breezeapp.engine.runner.core.FlowStreamingRunner
import com.mtkresearch.breezeapp.engine.runner.core.RunnerInfo
import com.mtkresearch.breezeapp.engine.model.*
import com.mtkresearch.breezeapp.engine.runner.sherpa.base.BaseSherpaAsrRunner
import com.mtkresearch.breezeapp.engine.core.ExceptionHandler
import com.mtkresearch.breezeapp.engine.runner.sherpa.base.BaseSherpaRunner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


/**
 * SherpaASRRunner - Real ASR runner using Sherpa ONNX
 *
 * This runner loads a Sherpa ONNX streaming ASR model and performs inference on PCM16 audio.
 * It supports both non-streaming and streaming (Flow) inference.
 *
 * Model files must be extracted to internal storage before loading.
 */
 @AIRunner(
    vendor = VendorType.SHERPA,
    priority = RunnerPriority.NORMAL,
    capabilities = [CapabilityType.ASR]
)
class SherpaASRRunner(context: Context) : BaseSherpaAsrRunner(context), FlowStreamingRunner {
    companion object {
        private const val TAG = "SherpaASRRunner"
        private const val MODEL_TYPE = "zipformer"
    }

    private var modelType: Int = 0 // Default to bilingual zh-en (Type 0 from official API)

    override fun getTag(): String = TAG

    override fun load(modelId: String, settings: EngineSettings): Boolean {
        modelName = modelId
        return try {
            // Parse model type from config if specified
            modelType = parseModelTypeFromSettings(settings)
            Log.i(TAG, "Using model type $modelType: ${getModelDescription(modelType)}")
            
            Log.i(TAG, "Start to initialize model")
            initModel()
            Log.i(TAG, "Finished initializing model")
            
            // CRITICAL FIX: Set isLoaded flag to true after successful initialization
            isLoaded.set(true)
            Log.i(TAG, "Model loaded successfully - isLoaded flag set to true")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SherpaASRRunner", e)
            isLoaded.set(false) // Ensure flag is false on failure
            false
        }
    }

    /**
     * Initialize the Sherpa ONNX model using external storage configuration
     * Following SherpaOfflineASRRunner pattern for consistency
     */
    private fun initModel() {
        Log.i(TAG, "Initializing model with type $modelType")
        
        // Create online recognizer with external storage configuration
        val featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80)
        val modelConfig = createOnlineModelConfig(modelType)
        
        val recognizerConfig = OnlineRecognizerConfig(
            featConfig = featConfig,
            modelConfig = modelConfig,
            ctcFstDecoderConfig = OnlineCtcFstDecoderConfig(),
            enableEndpoint = true,
            decodingMethod = "greedy_search",
            maxActivePaths = 4,
        )

        recognizer = OnlineRecognizer(config = recognizerConfig)
        
        Log.i(TAG, "Model initialized successfully with type $modelType (${getModelDescription(modelType)})")
    }
    
    /**
     * Create online model configuration using external storage paths
     * Models are loaded from context.filesDir/models/ directory (downloaded models)
     */
    private fun createOnlineModelConfig(type: Int): OnlineModelConfig {
        val modelsDir = context.filesDir.absolutePath + "/models"
        
        return when (type) {
            0 -> {
                val modelDir = "$modelsDir/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20"
                OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = "$modelDir/encoder-epoch-99-avg-1.int8.onnx",
                        decoder = "$modelDir/decoder-epoch-99-avg-1.onnx",
                        joiner = "$modelDir/joiner-epoch-99-avg-1.int8.onnx"
                    ),
                    tokens = "$modelDir/tokens.txt",
                    modelType = "zipformer"
                )
            }
            else -> {
                throw IllegalArgumentException("Unsupported online model type: $type")
            }
        }
    }

    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        // Validate model is loaded
        validateModelLoaded()?.let { return it }
        
        return try {
            // Check if this is microphone mode
            val isMicrophoneMode = input.params["microphone_mode"] as? Boolean ?: false
            
            if (isMicrophoneMode) {
                // For microphone mode, we should use streaming instead
                Log.w(TAG, "Microphone mode requested in non-streaming context. Consider using runAsFlow() instead.")
                return InferenceResult.error(RunnerError.invalidInput("Microphone mode requires streaming. Use runAsFlow() instead."))
            }
            
            // Validate input data
            val (audioData, error) = validateInput<ByteArray>(input, InferenceRequest.INPUT_AUDIO)
            error?.let { return it }
            
            Log.d(TAG, "Processing ASR with ${audioData!!.size} bytes of audio data")
            
            val startTime = System.currentTimeMillis()
            val streamObj = recognizer!!.createStream()
            
            // Convert ByteArray (PCM16) to float[] as required by Sherpa
            val floatSamples = convertPcm16ToFloat(audioData)
            
            Log.d(TAG, "Converted ${floatSamples.size} samples for processing")
            
            // Process audio following the official example pattern
            val result = processSamplesForSingleInference(streamObj, floatSamples)
            val elapsed = System.currentTimeMillis() - startTime
            
            Log.d(TAG, "ASR result: '${result.text}' (${elapsed}ms)")
            
            streamObj.release()
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
            Log.e(TAG, "Error in SherpaASRRunner.run", e)
            InferenceResult.error(RunnerError.runtimeError(e.message ?: "Unknown error", e))
        }
    }

    override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = flow {
        // Validate model is loaded
        validateModelLoaded()?.let { 
            emit(it)
            return@flow
        }
        
        try {
            // Check if this is microphone mode
            val isMicrophoneMode = input.params["microphone_mode"] as? Boolean ?: false
            
            if (isMicrophoneMode) {
                Log.i(TAG, "Starting microphone mode ASR processing")
                
                // Process microphone input following Sherpa-onnx official example
                processMicrophoneAsFlow(input.sessionId ?: "mic-session", modelName).collect { result ->
                    emit(result)
                }
            } else {
                // Validate input data
                val (audioData, error) = validateInput<ByteArray>(input, InferenceRequest.INPUT_AUDIO)
                error?.let { 
                    emit(it)
                    return@flow
                }
                
                Log.d(TAG, "Processing streaming ASR with ${audioData!!.size} bytes of audio data")
                
                val streamObj = recognizer!!.createStream()
                val floatSamples = convertPcm16ToFloat(audioData)
                
                Log.d(TAG, "Converted ${floatSamples.size} samples for streaming processing")
                
                // Process samples following the official example's streaming pattern
                processSamplesAsFlow(streamObj, floatSamples, input.sessionId ?: "file-session", modelName).collect { result ->
                    emit(result)
                }
                
                streamObj.release()
            }
        } catch (e: Exception) {
            ExceptionHandler.handleFlowException(e, input.sessionId ?: "unknown", "SherpaASRRunner.runAsFlow")
            emit(ExceptionHandler.handleException(e, input.sessionId ?: "unknown", "SherpaASRRunner.runAsFlow"))
        }
    }

    override fun releaseModel() {
        recognizer = null
    }

    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.ASR)

    override fun getRunnerInfo(): RunnerInfo = RunnerInfo(
        name = "SherpaASRRunner",
        version = "1.0.0",
        capabilities = getCapabilities(),
        description = "Sherpa ONNX streaming ASR runner"
    )

    // ========== Configuration Functions (from reference API) ==========

    /**
     * Parse model type from EngineSettings - using parameters map
     * 1. parameters["model_type"] as integer (e.g., 0, 8, 14)
     * 2. Falls back to default type 0 (bilingual zh-en)
     */
    private fun parseModelTypeFromSettings(settings: EngineSettings): Int {
        // Check if parameters contains model_type as integer
        val runnerParams = settings.getRunnerParameters("SherpaASRRunner")
        val modelTypeParam = runnerParams["model_type"]
        if (modelTypeParam != null) {
            val type = when (modelTypeParam) {
                is Int -> modelTypeParam
                is String -> modelTypeParam.toIntOrNull()
                else -> null
            }
            if (type != null && isValidModelType(type)) {
                Log.d(TAG, "Model type $type parsed from parameters: $modelTypeParam")
                return type
            }
        }
        
        // Default fallback to Type 0 (official bilingual zh-en model)
        Log.d(TAG, "Using default model type 0 (bilingual zh-en)")
        return 0
    }

    /**
     * Check if model type is valid/supported
     */
    private fun isValidModelType(type: Int): Boolean {
        return type in listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 1000, 1001)
    }

    /**
     * Public method to set model type (for advanced users)
     */
    fun setModelType(type: Int) {
        if (!isValidModelType(type)) {
            throw IllegalArgumentException("Invalid model type: $type. Supported types: ${getSupportedModelTypes()}")
        }
        if (isLoaded.get()) {
            Log.w(TAG, "Model is already loaded. Unload first before changing model type.")
            return
        }
        modelType = type
        Log.i(TAG, "Model type set to $type: ${getModelDescription(type)}")
    }

    /**
     * Get list of supported model types
     */
    fun getSupportedModelTypes(): List<Int> {
        return listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 1000, 1001)
    }

    /**
     * Get human-readable description of the model type
     */
    private fun getModelDescription(type: Int): String {
        return when (type) {
            0 -> "Bilingual zh-en zipformer (float32 encoder)"
            1 -> "Chinese LSTM"
            2 -> "English LSTM"
            3 -> "Chinese zipformer2 (int8 encoder)"
            4 -> "Chinese zipformer2 (float32 encoder)"
            5 -> "Bilingual zh-en paraformer"
            6 -> "English zipformer2"
            7 -> "French zipformer"
            8 -> "Bilingual zh-en zipformer (int8 encoder) - RECOMMENDED"
            9 -> "Chinese zipformer 14M"
            10 -> "English zipformer 20M"
            11 -> "English NeMo CTC 80ms"
            12 -> "English NeMo CTC 480ms"
            13 -> "English NeMo CTC 1040ms"
            14 -> "Korean zipformer"
            15 -> "Chinese zipformer CTC (int8)"
            16 -> "Chinese zipformer CTC (float32)"
            17 -> "Chinese zipformer CTC (int8) v2"
            18 -> "Chinese zipformer CTC (float32) v2"
            19 -> "Chinese zipformer CTC (fp16)"
            20 -> "Chinese zipformer (int8) v2"
            1000 -> "Bilingual zh-en zipformer (RKNN for RK3588)"
            1001 -> "Bilingual zh-en zipformer small (RKNN for RK3588)"
            else -> "Unknown model type"
        }
    }
}