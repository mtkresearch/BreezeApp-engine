package com.mtkresearch.breezeapp.engine.runner.sherpa.base

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineCtcFstDecoderConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.mtkresearch.breezeapp.engine.core.ExceptionHandler
import com.mtkresearch.breezeapp.engine.model.InferenceRequest
import com.mtkresearch.breezeapp.engine.model.InferenceResult
import com.mtkresearch.breezeapp.engine.model.RunnerError
import com.mtkresearch.breezeapp.engine.util.EngineUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Base class for Sherpa ASR runners
 * 
 * This abstract class provides common functionality for all Sherpa ASR runners,
 * including audio processing, streaming support, and microphone handling.
 */
abstract class BaseSherpaAsrRunner(context: Context) : BaseSherpaRunner(context) {
    companion object {
        const val SAMPLE_RATE = 16000
        const val TAG = "BaseSherpaAsrRunner"
    }
    
    protected var recognizer: OnlineRecognizer? = null
    
    override fun getTag(): String = TAG
    
    /**
     * Process samples for single inference
     * 
     * @param stream The OnlineStream to process
     * @param samples The audio samples to process
     * @return The recognition result
     */
    protected fun processSamplesForSingleInference(stream: OnlineStream, samples: FloatArray): com.k2fsa.sherpa.onnx.OnlineRecognizerResult {
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
    protected fun processSamplesAsFlow(
        stream: OnlineStream, 
        samples: FloatArray, 
        sessionId: String,
        modelName: String
    ): Flow<InferenceResult> = flow {
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
            val text = recognizer!!.getResult(stream).text
            
            // Emit partial result for each chunk
            if (text.isNotBlank() && text != lastText) {
                emit(
                    InferenceResult.success(
                        outputs = mapOf(InferenceResult.OUTPUT_TEXT to text),
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
                lastText = text
            }
            
            // Reset stream at endpoint and update accumulated text
            if (isEndpoint) {
                recognizer!!.reset(stream)
                if (text.isNotBlank()) {
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
        val finalResult = recognizer!!.getResult(stream).text
        
        val elapsed = System.currentTimeMillis() - startTime
        emit(
            InferenceResult.success(
                outputs = mapOf(InferenceResult.OUTPUT_TEXT to finalResult),
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
     */
    protected fun processMicrophoneAsFlow(
        sessionId: String,
        modelName: String
    ): Flow<InferenceResult> = flow {
        Log.i(TAG, "Starting microphone ASR processing")
        
        // Check permission first
        if (!EngineUtils.hasRecordAudioPermission(context)) {
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
        val audioRecord = EngineUtils.createAudioRecord(context)
        if (audioRecord == null) {
            Log.e(TAG, "Failed to create AudioRecord")
            emit(InferenceResult.error(RunnerError.runtimeError("Failed to create AudioRecord")))
            return@flow
        }
        
        Log.i(TAG, "AudioRecord created successfully")
        
        if (!EngineUtils.startRecording(audioRecord)) {
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
                
                val samplesRead = EngineUtils.readAudioSamples(audioRecord, buffer)
                
                if (samplesRead > 0) {
                    Log.d(TAG, "Read $samplesRead audio samples")
                    
                    // Convert to float array for Sherpa processing
                    val audioChunk = buffer.copyOf(samplesRead)
                    val floatSamples = EngineUtils.convertPcm16ToFloat(audioChunk)
                    
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
                kotlinx.coroutines.delay(com.mtkresearch.breezeapp.engine.core.EngineConstants.Audio.CANCELLATION_CHECK_DELAY_MS)
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
            EngineUtils.stopAndReleaseAudioRecord(audioRecord)
            streamObj.release()
            Log.i(TAG, "Microphone ASR processing completed")
        }
    }
    
    /**
     * Convert PCM16 ByteArray to FloatArray as required by Sherpa ONNX
     * PCM16 format: little-endian 16-bit samples
     */
    protected fun convertPcm16ToFloat(audioData: ByteArray): FloatArray {
        return FloatArray(audioData.size / 2) { i ->
            val sample = ((audioData[i * 2 + 1].toInt() and 0xFF) shl 8) or 
                        (audioData[i * 2].toInt() and 0xFF)
            sample / 32768.0f
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