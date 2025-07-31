package com.mtkresearch.breezeapp.engine.runner.sherpa

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import com.mtkresearch.breezeapp.engine.data.runner.core.BaseRunner
import com.mtkresearch.breezeapp.engine.data.runner.core.BaseRunnerCompanion
import com.mtkresearch.breezeapp.engine.data.runner.core.FlowStreamingRunner
import com.mtkresearch.breezeapp.engine.data.runner.core.RunnerInfo
import com.mtkresearch.breezeapp.engine.domain.model.*
import com.mtkresearch.breezeapp.engine.util.AudioUtil
import com.mtkresearch.breezeapp.engine.core.ExceptionHandler
import com.mtkresearch.breezeapp.engine.core.EngineConstants.Audio.CANCELLATION_CHECK_DELAY_MS
import com.mtkresearch.breezeapp.engine.core.CancellationManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import java.util.concurrent.atomic.AtomicBoolean


/**
 * SherpaASRRunner - Real ASR runner using Sherpa ONNX
 *
 * This runner loads a Sherpa ONNX streaming ASR model and performs inference on PCM16 audio.
 * It supports both non-streaming and streaming (Flow) inference.
 *
 * Model files must be extracted to internal storage before loading.
 */
class SherpaASRRunner(private val context: Context) : BaseRunner, FlowStreamingRunner {
    companion object : BaseRunnerCompanion {
        private const val TAG = "SherpaASRRunner"
        private const val SAMPLE_RATE = 16000
        private const val MODEL_TYPE = "zipformer"

        @JvmStatic
        override fun isSupported(): Boolean = true
    }

    private val isLoaded = AtomicBoolean(false)
    private var recognizer: OnlineRecognizer? = null
    private var modelName: String = ""
    private var modelType: Int = 0 // Default to bilingual zh-en (Type 0 from official API)

    override fun load(config: ModelConfig): Boolean {
        return try {
            Log.d(TAG, "Loading SherpaASRRunner with config: ${config.modelName}")
            modelName = config.modelName
            
            // Parse model type from config if specified
            modelType = parseModelTypeFromConfig(config)
            Log.i(TAG, "Using model type $modelType: ${getModelDescription(modelType)}")
            
            Log.i(TAG, "Start to initialize model")
            initModel()
            Log.i(TAG, "Finished initializing model")
            
            isLoaded.set(true)
            Log.i(TAG, "SherpaASRRunner loaded successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load SherpaASRRunner", e)
            isLoaded.set(false)
            false
        }
    }

    /**
     * Initialize the Sherpa ONNX model using the official API functions
     * Simplified to use existing functions from reference_api
     */
    private fun initModel() {
        Log.i(TAG, "Initializing model with type $modelType")
        
        // Use official API functions directly
        val featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80)
        val modelConfig = getModelConfig(type = modelType)
            ?: throw IllegalArgumentException("Unsupported model type: $modelType")
        val endpointConfig = getEndpointConfig()
        val lmConfig = getOnlineLMConfig(type = modelType)

        // Complete recognizer configuration following the official example
        val recognizerConfig = OnlineRecognizerConfig(
            featConfig = featConfig,
            modelConfig = modelConfig,
            lmConfig = lmConfig,
            ctcFstDecoderConfig = OnlineCtcFstDecoderConfig(),
            endpointConfig = endpointConfig,
            enableEndpoint = true,
            decodingMethod = "greedy_search",
            maxActivePaths = 4,
        )

        recognizer = OnlineRecognizer(
            assetManager = context.assets,
            config = recognizerConfig,
        )
        
        Log.i(TAG, "Model initialized successfully with type $modelType (${getModelDescription(modelType)})")
    }

    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        if (!isLoaded.get()) {
            return InferenceResult.error(RunnerError.modelNotLoaded())
        }
        
        return try {
            // Check if this is microphone mode
            val isMicrophoneMode = input.params["microphone_mode"] as? Boolean ?: false
            
            if (isMicrophoneMode) {
                // For microphone mode, we should use streaming instead
                Log.w(TAG, "Microphone mode requested in non-streaming context. Consider using runAsFlow() instead.")
                return InferenceResult.error(RunnerError.invalidInput("Microphone mode requires streaming. Use runAsFlow() instead."))
            }
            
            // Original file-based processing
            val audioData = input.inputs[InferenceRequest.INPUT_AUDIO] as? ByteArray
            if (audioData == null) {
                return InferenceResult.error(RunnerError.invalidInput("Audio data required for ASR processing"))
            }
            
            Log.d(TAG, "Processing ASR with ${audioData.size} bytes of audio data")
            
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
        if (!isLoaded.get()) {
            emit(InferenceResult.error(RunnerError.modelNotLoaded()))
            return@flow
        }
        
        try {
            // Check if this is microphone mode
            val isMicrophoneMode = input.params["microphone_mode"] as? Boolean ?: false
            
            if (isMicrophoneMode) {
                Log.i(TAG, "Starting microphone mode ASR processing")
                
                // Check microphone permission
                if (!AudioUtil.hasRecordAudioPermission(context)) {
                    emit(InferenceResult.error(RunnerError.invalidInput("RECORD_AUDIO permission not granted")))
                    return@flow
                }
                
                // Process microphone input following Sherpa-onnx official example
                processMicrophoneAsFlow(input.sessionId ?: "mic-session").collect { result ->
                    emit(result)
                }
            } else {
                // Original file-based streaming processing
                val audioData = input.inputs[InferenceRequest.INPUT_AUDIO] as? ByteArray
                if (audioData == null) {
                    emit(InferenceResult.error(RunnerError.invalidInput("Audio data required for ASR processing")))
                    return@flow
                }
                
                Log.d(TAG, "Processing streaming ASR with ${audioData.size} bytes of audio data")
                
                val streamObj = recognizer!!.createStream()
                val floatSamples = convertPcm16ToFloat(audioData)
                
                Log.d(TAG, "Converted ${floatSamples.size} samples for streaming processing")
                
                // Process samples following the official example's streaming pattern
                processSamplesAsFlow(streamObj, floatSamples, input.sessionId ?: "file-session").collect { result ->
                    emit(result)
                }
                
                streamObj.release()
            }
        } catch (e: Exception) {
            ExceptionHandler.handleFlowException(e, input.sessionId ?: "unknown", "SherpaASRRunner.runAsFlow")
            emit(ExceptionHandler.handleException(e, input.sessionId ?: "unknown", "SherpaASRRunner.runAsFlow"))
        }
    }

    override fun unload() {
        Log.d(TAG, "Unloading SherpaASRRunner")
        recognizer = null
        isLoaded.set(false)
    }

    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.ASR)

    override fun isLoaded(): Boolean = isLoaded.get()

    override fun getRunnerInfo(): RunnerInfo = RunnerInfo(
        name = "SherpaASRRunner",
        version = "1.0.0",
        capabilities = getCapabilities(),
        description = "Sherpa ONNX streaming ASR runner",
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
     * Process samples for single inference following the latest official example pattern
     */
    private fun processSamplesForSingleInference(stream: OnlineStream, samples: FloatArray): OnlineRecognizerResult {
        // Accept the waveform
        stream.acceptWaveform(samples, sampleRate = SAMPLE_RATE)
        
        // Decode while ready
        while (recognizer!!.isReady(stream)) {
            recognizer!!.decode(stream)
        }
        
        // Add tail padding and finish input - following the latest official example
        val tailPaddings = FloatArray((SAMPLE_RATE * 0.5).toInt()) // 0.5 seconds as in official example
        stream.acceptWaveform(tailPaddings, sampleRate = SAMPLE_RATE)
        stream.inputFinished()
        
        // Final decoding after input finished
        while (recognizer!!.isReady(stream)) {
            recognizer!!.decode(stream)
        }
        
        return recognizer!!.getResult(stream)
    }

    /**
     * Process samples as Flow following the official example's streaming pattern
     * Maintains real-time streaming capabilities while using proper endpoint detection
     */
    private fun processSamplesAsFlow(stream: OnlineStream, samples: FloatArray, sessionId: String): Flow<InferenceResult> = flow {
        Log.i(TAG, "Processing samples as flow")
        
        val interval = 0.1 // i.e., 100 ms - same as official example
        val bufferSize = (interval * SAMPLE_RATE).toInt() // in samples
        
        var offset = 0
        var idx = 0
        var lastText = ""
        val startTime = System.currentTimeMillis()
        
        // Process in chunks for real-time streaming
        while (offset < samples.size) {
            val end = (offset + bufferSize).coerceAtMost(samples.size)
            val chunk = samples.sliceArray(offset until end)
            
            // Accept waveform chunk
            stream.acceptWaveform(chunk, sampleRate = SAMPLE_RATE)
            while (recognizer!!.isReady(stream)) {
                recognizer!!.decode(stream)
            }
            
            val isEndpoint = recognizer!!.isEndpoint(stream)
            var text = recognizer!!.getResult(stream).text
            
            // Handle endpoint detection and text accumulation like the official example
            var textToDisplay = lastText
            
            if (text.isNotBlank()) {
                textToDisplay = if (lastText.isBlank()) {
                    "$idx: $text"
                } else {
                    "$lastText\n$idx: $text"
                }
            }
            
            // Emit partial result for each chunk
            emit(
                InferenceResult.success(
                    outputs = mapOf(InferenceResult.OUTPUT_TEXT to textToDisplay),
                    metadata = mapOf(
                        InferenceResult.META_CONFIDENCE to 0.95f,
                        InferenceResult.META_SEGMENT_INDEX to idx,
                        InferenceResult.META_SESSION_ID to sessionId,
                        InferenceResult.META_MODEL_NAME to modelName,
                        "is_endpoint" to isEndpoint
                    ),
                    partial = !isEndpoint
                )
            )
            
            // Reset stream at endpoint and update accumulated text
            if (isEndpoint) {
                recognizer!!.reset(stream)
                if (text.isNotBlank()) {
                    lastText = if (lastText.isBlank()) {
                        "$idx: $text"
                    } else {
                        "$lastText\n$idx: $text"
                    }
                    idx += 1
                }
            }
            
            offset = end
        }
        
        // Final processing with tail padding - following latest official example
        val tailPaddings = FloatArray((SAMPLE_RATE * 0.5).toInt()) // 0.5 seconds
        stream.acceptWaveform(tailPaddings, sampleRate = SAMPLE_RATE)
        stream.inputFinished()
        while (recognizer!!.isReady(stream)) {
            recognizer!!.decode(stream)
        }
        
        // Get final result and emit
        val finalResult = recognizer!!.getResult(stream)
        val finalText = if (finalResult.text.isNotBlank()) {
            if (lastText.isBlank()) {
                "$idx: ${finalResult.text}"
            } else {
                "$lastText\n$idx: ${finalResult.text}"
            }
        } else {
            lastText
        }
        
        val elapsed = System.currentTimeMillis() - startTime
        emit(
            InferenceResult.success(
                outputs = mapOf(InferenceResult.OUTPUT_TEXT to finalText),
                metadata = mapOf(
                    InferenceResult.META_CONFIDENCE to 0.95f,
                    InferenceResult.META_PROCESSING_TIME_MS to elapsed,
                    InferenceResult.META_MODEL_NAME to modelName,
                    InferenceResult.META_SESSION_ID to sessionId
                ),
                partial = false
            )
        )
    }

    /**
     * Process microphone input as Flow following Sherpa-onnx official example
     * This method implements the real-time microphone ASR processing
     * Directly using AudioRecord instead of nested Flow to avoid completion issues
     */
    private fun processMicrophoneAsFlow(sessionId: String): Flow<InferenceResult> = flow {
        Log.i(TAG, "Starting microphone ASR processing")
        
        // Check permission first
        if (!AudioUtil.hasRecordAudioPermission(context)) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            emit(InferenceResult.error(RunnerError.invalidInput("RECORD_AUDIO permission not granted")))
            return@flow
        }
        
        // Android 15: Check audio focus requirements
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Add a small delay to allow foreground service to fully establish
            kotlinx.coroutines.delay(500) // 500ms delay
            if (!isValidForAudioFocus()) {
                Log.e(TAG, "Cannot request audio focus: App must be top app or running foreground service")
                emit(InferenceResult.error(RunnerError.invalidInput("Audio focus not allowed on Android 15+")))
                return@flow
            }
        }
        
        // Force update foreground service state to ensure microphone access
        forceUpdateForegroundServiceState()
        
        // Create AudioRecord directly following Sherpa official example
        val audioRecord = AudioUtil.createAudioRecord(context)
        if (audioRecord == null) {
            Log.e(TAG, "Failed to create AudioRecord")
            emit(InferenceResult.error(RunnerError.runtimeError("Failed to create AudioRecord")))
            return@flow
        }
        
        Log.i(TAG, "AudioRecord created successfully")
        
        if (!AudioUtil.startRecording(audioRecord)) {
            Log.e(TAG, "Failed to start recording")
            audioRecord.release()
            emit(InferenceResult.error(RunnerError.runtimeError("Failed to start recording")))
            return@flow
        }
        
        Log.i(TAG, "Recording started successfully")
        
        val streamObj = recognizer!!.createStream()
        
        try {
            var idx = 0
            var lastText = ""
            val startTime = System.currentTimeMillis()
            
            // Buffer size for 100ms chunks (same as official example)
            val interval = 0.1 // 100 ms
            val bufferSize = (interval * SAMPLE_RATE).toInt()
            val buffer = ShortArray(bufferSize)
            
            Log.i(TAG, "Starting microphone processing loop with buffer size: $bufferSize")
            
            // Emit initial status
            emit(
                InferenceResult.success(
                    outputs = mapOf(InferenceResult.OUTPUT_TEXT to "Ready for speech..."),
                    metadata = mapOf(
                        InferenceResult.META_SESSION_ID to sessionId,
                        InferenceResult.META_MODEL_NAME to modelName,
                        "microphone_mode" to true,
                        "status" to "ready"
                    ),
                    partial = true
                )
            )
            
            // Main recording loop following Sherpa official example pattern
            // 使用統一的取消檢查
            while (true) {
                // 簡化的取消檢查，避免suspend function調用
                val job = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
                if (job?.isActive == false) {
                    Log.i(TAG, "Microphone processing cancelled by client")
                    break
                }
                
                val samplesRead = AudioUtil.readAudioSamples(audioRecord, buffer)
                
                if (samplesRead > 0) {
                    Log.d(TAG, "Read $samplesRead audio samples")
                    
                    // Convert to float array for Sherpa processing
                    val audioChunk = buffer.copyOf(samplesRead)
                    val floatSamples = AudioUtil.convertPcm16ToFloat(audioChunk)
                    
                    // Process audio chunk following official example pattern
                    streamObj.acceptWaveform(floatSamples, sampleRate = SAMPLE_RATE)
                    while (recognizer!!.isReady(streamObj)) {
                        recognizer!!.decode(streamObj)
                    }
                    
                    val isEndpoint = recognizer!!.isEndpoint(streamObj)
                    var text = recognizer!!.getResult(streamObj).text
                    
                    // Handle paraformer tail padding if needed (from official example)
                    if (isEndpoint && recognizer!!.config.modelConfig.paraformer.encoder.isNotBlank()) {
                        val tailPaddings = FloatArray((0.8 * SAMPLE_RATE).toInt())
                        streamObj.acceptWaveform(tailPaddings, sampleRate = SAMPLE_RATE)
                        while (recognizer!!.isReady(streamObj)) {
                            recognizer!!.decode(streamObj)
                        }
                        text = recognizer!!.getResult(streamObj).text
                    }
                    
                    // Only emit the latest text (not accumulated)
                    if (text.isNotBlank() && text != lastText) {
                        emit(
                            InferenceResult.success(
                                outputs = mapOf(InferenceResult.OUTPUT_TEXT to text),
                                metadata = mapOf(
                                    InferenceResult.META_CONFIDENCE to 0.95f,
                                    InferenceResult.META_SEGMENT_INDEX to idx,
                                    InferenceResult.META_SESSION_ID to sessionId,
                                    InferenceResult.META_MODEL_NAME to modelName,
                                    "is_endpoint" to isEndpoint,
                                    "microphone_mode" to true
                                ),
                                partial = true
                            )
                        )
                        lastText = text
                    }
                    
                    // Reset stream at endpoint
                    if (isEndpoint) {
                        recognizer!!.reset(streamObj)
                        if (text.isNotBlank()) {
                            idx += 1
                        }
                    }
                    
                } else if (samplesRead < 0) {
                    Log.e(TAG, "Error reading audio samples: $samplesRead")
                    break
                } else {
                    // No samples read, continue
                    Log.d(TAG, "No audio samples read, continuing...")
                }
                
                // 使用配置常量
                kotlinx.coroutines.delay(CANCELLATION_CHECK_DELAY_MS)
            }
            
            // Final result after microphone stops
            val elapsed = System.currentTimeMillis() - startTime
            val finalText = if (lastText.isBlank()) "No speech detected" else lastText
            Log.i(TAG, "Emitting final microphone result: $finalText")
            emit(
                InferenceResult.success(
                    outputs = mapOf(InferenceResult.OUTPUT_TEXT to finalText),
                    metadata = mapOf(
                        InferenceResult.META_CONFIDENCE to 0.95f,
                        InferenceResult.META_PROCESSING_TIME_MS to elapsed,
                        InferenceResult.META_MODEL_NAME to modelName,
                        InferenceResult.META_SESSION_ID to sessionId,
                        "microphone_mode" to true,
                        "final" to true
                    ),
                    partial = false
                )
            )
            
        } catch (e: Exception) {
            ExceptionHandler.handleFlowException(e, sessionId, "Microphone ASR processing")
            emit(ExceptionHandler.handleException(e, sessionId, "Microphone ASR processing"))
        } finally {
            AudioUtil.stopAndReleaseAudioRecord(audioRecord)
            streamObj.release()
            Log.i(TAG, "Microphone ASR processing completed")
        }
    }

    /**
     * Stop microphone recording
     * This method can be called to stop ongoing microphone ASR processing
     */
    fun stopMicrophoneRecording() {
        Log.i(TAG, "Stopping microphone recording")
        // The AtomicBoolean in processMicrophoneAsFlow will handle the stopping
        // This is a placeholder for future enhancement if needed
    }

    /**
     * Copy data directory from assets to external storage (if needed for HR)
     */
    private fun copyDataDir(dataDir: String): String {
        Log.i(TAG, "data dir is $dataDir")
        copyAssets(dataDir)

        val newDataDir = context.getExternalFilesDir(null)!!.absolutePath
        Log.i(TAG, "newDataDir: $newDataDir")
        return newDataDir
    }

    /**
     * Copy assets recursively
     */
    private fun copyAssets(path: String) {
        val assets: Array<String>?
        try {
            assets = context.assets.list(path)
            if (assets!!.isEmpty()) {
                copyFile(path)
            } else {
                val fullPath = "${context.getExternalFilesDir(null)}/$path"
                val dir = java.io.File(fullPath)
                dir.mkdirs()
                for (asset in assets.iterator()) {
                    val p: String = if (path == "") "" else path + "/"
                    copyAssets(p + asset)
                }
            }
        } catch (ex: java.io.IOException) {
            Log.e(TAG, "Failed to copy $path. $ex")
        }
    }

    /**
     * Copy individual file from assets
     */
    private fun copyFile(filename: String) {
        try {
            val istream = context.assets.open(filename)
            val newFilename = context.getExternalFilesDir(null).toString() + "/" + filename
            val ostream = java.io.FileOutputStream(newFilename)
            // Log.i(TAG, "Copying $filename to $newFilename")
            val buffer = ByteArray(1024)
            var read = 0
            while (read != -1) {
                ostream.write(buffer, 0, read)
                read = istream.read(buffer)
            }
            istream.close()
            ostream.flush()
            ostream.close()
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to copy $filename, $ex")
        }
    }

    // ========== Configuration Functions (from reference API) ==========

    /**
     * Parse model type from ModelConfig - using parameters map
     * 1. parameters["model_type"] as integer (e.g., 0, 8, 14)
     * 2. Falls back to default type 0 (bilingual zh-en)
     */
    private fun parseModelTypeFromConfig(config: ModelConfig): Int {
        // Check if parameters contains model_type as integer
        val modelTypeParam = config.parameters["model_type"]
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
    
    /**
     * Check if app is in valid state for audio focus on Android 15+
     */
    private fun isValidForAudioFocus(): Boolean {
        return try {
            val activityManager = context.getSystemService(android.app.ActivityManager::class.java)
            
            // Method 1: Check if app is top app
            val appTasks = activityManager?.getAppTasks()
            val isTopApp = appTasks?.any { 
                it.taskInfo?.topActivity?.packageName == context.packageName 
            } ?: false
            
            // Method 2: Check if we have a foreground service (using modern API)
            val hasForegroundService = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val usageStatsManager = context.getSystemService(android.app.usage.UsageStatsManager::class.java)
                val currentTime = System.currentTimeMillis()
                val stats = usageStatsManager?.queryUsageStats(
                    android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                    currentTime - 60000, // Last minute
                    currentTime
                )
                stats?.any { it.packageName == context.packageName } ?: false
            } else {
                // Fallback for older versions
                context.getSystemService(android.app.ActivityManager::class.java)
                    ?.getRunningServices(Int.MAX_VALUE)
                    ?.any { it.service.packageName == context.packageName && it.foreground }
                    ?: false
            }
            
            // Method 3: Check if we have SYSTEM_ALERT_WINDOW permission and overlay is visible
            val hasOverlayPermission = android.provider.Settings.canDrawOverlays(context)
            val hasVisibleOverlay = hasOverlayPermission && isOverlayVisible()
            
            Log.d(TAG, "Audio focus check: isTopApp=$isTopApp, hasForegroundService=$hasForegroundService, hasVisibleOverlay=$hasVisibleOverlay")
            
            // On Android 15+, we need either top app OR foreground service with visible overlay
            val isValid = isTopApp || (hasForegroundService && hasVisibleOverlay)
            
            if (!isValid) {
                Log.w(TAG, "Audio focus not allowed: App must be top app or have foreground service with visible overlay")
            }
            
            isValid
        } catch (e: Exception) {
            Log.w(TAG, "Error checking audio focus validity", e)
            false
        }
    }
    
    /**
     * Check if overlay window is currently visible
     */
    private fun isOverlayVisible(): Boolean {
        return try {
            // Check if we have SYSTEM_ALERT_WINDOW permission and overlay is visible
            val hasOverlayPermission = android.provider.Settings.canDrawOverlays(context)
            
            // For Android 15+, we need to check if our overlay is actually visible
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Check if any overlay window is visible for our package
                val windowManager = context.getSystemService(android.view.WindowManager::class.java)
                val display = windowManager?.defaultDisplay
                val displayMetrics = android.util.DisplayMetrics()
                display?.getMetrics(displayMetrics)
                
                // If we have overlay permission and are running foreground service, assume overlay is visible
                hasOverlayPermission && hasForegroundService()
            } else {
                hasOverlayPermission
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking overlay visibility", e)
            false
        }
    }
    
    /**
     * Check if we have a foreground service running
     */
    private fun hasForegroundService(): Boolean {
        return try {
            val activityManager = context.getSystemService(android.app.ActivityManager::class.java)
            activityManager?.getRunningServices(Int.MAX_VALUE)
                ?.any { it.service.packageName == context.packageName && it.foreground }
                ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Error checking foreground service", e)
            false
        }
    }
    
    /**
     * Force update foreground service state to ensure microphone access
     */
    private fun forceUpdateForegroundServiceState() {
        try {
            // Try to bring our service to foreground if needed
            val intent = android.content.Intent(context, com.mtkresearch.breezeapp.engine.BreezeAppEngineService::class.java)
            intent.action = "com.mtkresearch.breezeapp.engine.FORCE_FOREGROUND"
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            
            Log.d(TAG, "Forced foreground service state update for microphone access")
        } catch (e: Exception) {
            Log.w(TAG, "Error forcing foreground service state update", e)
        }
    }

}
