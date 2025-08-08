package com.mtkresearch.breezeapp.engine.runner.sherpa

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.mtkresearch.breezeapp.engine.runner.core.BaseRunner
import com.mtkresearch.breezeapp.engine.runner.core.BaseRunnerCompanion
import com.mtkresearch.breezeapp.engine.runner.core.FlowStreamingRunner
import com.mtkresearch.breezeapp.engine.runner.core.RunnerInfo
import com.mtkresearch.breezeapp.engine.domain.model.*
import com.mtkresearch.breezeapp.engine.system.SherpaLibraryManager
import com.mtkresearch.breezeapp.engine.util.SherpaTtsConfigUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SherpaTTSRunner - Real TTS runner using Sherpa ONNX with direct audio playback
 *
 * This runner loads a Sherpa ONNX TTS model and performs text-to-speech synthesis
 * with real-time streaming audio playback directly in the engine.
 *
 * Features:
 * - Multiple TTS model support (VITS, Matcha, Kokoro)
 * - Real-time audio streaming with direct playback
 * - Audio file generation and playback
 * - Speaker ID and speed control
 * - Global library management integration
 * - Robust audio playback management
 */
class SherpaTTSRunner(private val context: Context) : BaseRunner, FlowStreamingRunner {
    companion object : BaseRunnerCompanion {
        private const val TAG = "SherpaTTSRunner"
        
        @JvmStatic
        override fun isSupported(): Boolean = true
    }

    private val isLoaded = AtomicBoolean(false)
    private var tts: OfflineTts? = null
    private var modelName: String = ""
    
    // Audio playback components with thread safety
    private var audioTrack: AudioTrack? = null
    private var sampleRate: Int = 22050
    private val isPlaying = AtomicBoolean(false)
    private val isStopped = AtomicBoolean(false)
    private val audioLock = Any()

    override fun load(config: ModelConfig): Boolean {
        return try {
            Log.d(TAG, "Loading SherpaTTSRunner with config: ${config.modelName}")
            modelName = config.modelName
            
            if (!SherpaLibraryManager.initializeGlobally()) throw Exception("Failed to initialize Sherpa ONNX library")
            if (!SherpaLibraryManager.isLibraryReady()) throw Exception("Sherpa ONNX library not ready for use")
            initializeTts()
            isLoaded.set(true)
            Log.i(TAG, "SherpaTTSRunner loaded successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load SherpaTTSRunner", e)
            isLoaded.set(false)
            false
        }
    }

    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        if (!isLoaded.get()) return InferenceResult.error(RunnerError.modelNotLoaded())
        return try {
            SherpaLibraryManager.markInferenceStarted()
            val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String
            if (text.isNullOrBlank()) return InferenceResult.error(RunnerError.invalidInput("Text input required for TTS processing"))
            val speakerId = (input.inputs["speaker_id"] as? Number)?.toInt() ?: 0
            val speed = (input.inputs["speed"] as? Number)?.toFloat() ?: 1.0f
            val startTime = System.currentTimeMillis()
            if (speakerId < 0) return InferenceResult.error(RunnerError.invalidInput("Speaker ID must be non-negative"))
            if (speed <= 0) return InferenceResult.error(RunnerError.invalidInput("Speed must be positive"))
            
            Log.d(TAG, "Starting TTS generation - text: '$text', speakerId: $speakerId, speed: $speed")
            
            // Initialize audio playback
            initAudioPlayback()
            
            // Use generateWithCallback for real streaming with direct playback
            val audio = tts!!.generateWithCallback(
                text = text, 
                sid = speakerId, 
                speed = speed,
                callback = { samples ->
                    // Direct audio playback in callback
                    if (!isStopped.get()) {
                        synchronized(audioLock) {
                            audioTrack?.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                        }
                        return@generateWithCallback 1 // Continue generation
                    } else {
                        synchronized(audioLock) {
                            audioTrack?.stop()
                        }
                        return@generateWithCallback 0 // Stop generation
                    }
                }
            )
            
            Log.d(TAG, "TTS generation completed - samples: ${audio.samples.size}, sampleRate: ${audio.sampleRate}")
            
            if (audio.samples.isEmpty()) {
                Log.e(TAG, "Generated audio samples is empty!")
                return InferenceResult.error(RunnerError.runtimeError("Failed to generate audio - samples is empty"))
            }
            
            // Stop audio playback
            stopAudioPlayback()
            
            val pcm16 = floatArrayToPCM16(audio.samples)
            Log.d(TAG, "Converted to PCM16 - size: ${pcm16.size} bytes")
            
            val elapsed = System.currentTimeMillis() - startTime
            val outputs = mapOf("audioData" to pcm16)
            val metadata = mapOf(
                "sampleRate" to audio.sampleRate,
                "channels" to 1,
                "bitDepth" to 16,
                "format" to "pcm16",
                "durationMs" to (pcm16.size / 2 * 1000 / audio.sampleRate),
                "chunkIndex" to 0,
                "isLastChunk" to true
            )
            
            Log.d(TAG, "TTS result - duration: ${pcm16.size / 2 * 1000 / audio.sampleRate}ms, elapsed: ${elapsed}ms")
            SherpaLibraryManager.markInferenceCompleted()
            InferenceResult.success(outputs, metadata, partial = false)
        } catch (e: Exception) {
            Log.e(TAG, "TTS generation failed", e)
            stopAudioPlayback()
            SherpaLibraryManager.markInferenceCompleted()
            InferenceResult.error(RunnerError.runtimeError("TTS generation failed: ${e.message}"))
        }
    }

    override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = flow {
        if (!isLoaded.get()) {
            emit(InferenceResult.error(RunnerError.modelNotLoaded()))
            return@flow
        }
        try {
            SherpaLibraryManager.markInferenceStarted()
            val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String
            if (text.isNullOrBlank()) {
                emit(InferenceResult.error(RunnerError.invalidInput("Text input required for TTS processing")))
                return@flow
            }
            val speakerId = (input.inputs["speaker_id"] as? Number)?.toInt() ?: 0
            val speed = (input.inputs["speed"] as? Number)?.toFloat() ?: 1.0f
            if (speakerId < 0) {
                emit(InferenceResult.error(RunnerError.invalidInput("Speaker ID must be non-negative")))
                return@flow
            }
            if (speed <= 0) {
                emit(InferenceResult.error(RunnerError.invalidInput("Speed must be positive")))
                return@flow
            }
            val startTime = System.currentTimeMillis()
            Log.d(TAG, "Starting TTS generation (Flow) - text: '$text', speakerId: $speakerId, speed: $speed")
            
            // Initialize audio playback for streaming
            initAudioPlayback()
            
            // Use generateWithCallback for real streaming with direct playback
            val audio = tts!!.generateWithCallback(
                text = text,
                sid = speakerId,
                speed = speed,
                callback = { samples ->
                    // Direct audio playback in callback
                    if (!isStopped.get()) {
                        synchronized(audioLock) {
                            audioTrack?.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                        }
                        return@generateWithCallback 1 // Continue generation
                    } else {
                        synchronized(audioLock) {
                            audioTrack?.stop()
                        }
                        return@generateWithCallback 0 // Stop generation
                    }
                }
            )
            
            Log.d(TAG, "TTS generation completed (Flow) - samples: ${audio.samples.size}, sampleRate: ${audio.sampleRate}")
            
            if (audio.samples.isEmpty()) {
                Log.e(TAG, "Generated audio samples is empty (Flow)!")
                emit(InferenceResult.error(RunnerError.runtimeError("Failed to generate audio - samples is empty")))
                return@flow
            }
            
            // Stop audio playback
            stopAudioPlayback()
            
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "TTS Flow completed - elapsed: ${elapsed}ms")
            
            // Emit completion signal
            emit(InferenceResult.success(
                outputs = mapOf(),
                metadata = mapOf("isLastChunk" to true),
                partial = false
            ))
            
            SherpaLibraryManager.markInferenceCompleted()
        } catch (e: Exception) {
            Log.e(TAG, "Error in SherpaTTSRunner.runAsFlow", e)
            stopAudioPlayback()
            emit(InferenceResult.error(RunnerError.runtimeError(e.message ?: "Unknown error", e)))
            SherpaLibraryManager.markInferenceCompleted()
        }
    }

    override fun unload() {
        Log.d(TAG, "Unloading SherpaTTSRunner")
        stopAudioPlayback()
        tts = null
        isLoaded.set(false)
    }

    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.TTS)
    override fun isLoaded(): Boolean = isLoaded.get()
    override fun getRunnerInfo(): RunnerInfo = RunnerInfo(
        name = "SherpaTTSRunner",
        version = "1.0.0",
        capabilities = getCapabilities(),
        description = "Sherpa ONNX TTS runner with real-time streaming audio playback",
        isMock = false
    )

    /**
     * Initialize audio playback with robust error handling
     */
    private fun initAudioPlayback() {
        try {
            Log.d(TAG, "Initializing audio playback")
            isStopped.set(false)
            isPlaying.set(false)
            
            val sr = tts?.sampleRate() ?: 22050
            synchronized(audioLock) {
                if (audioTrack == null || sampleRate != sr) {
                    audioTrack?.stop()
                    audioTrack?.release()
                    sampleRate = sr
                    
                    val bufLength = AudioTrack.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_FLOAT
                    )
                    
                    if (bufLength == AudioTrack.ERROR_BAD_VALUE || bufLength == AudioTrack.ERROR) {
                        throw Exception("Invalid audio parameters for AudioTrack")
                    }
                    
                    val attr = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()

                    val format = AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setSampleRate(sampleRate)
                        .build()

                    audioTrack = AudioTrack(
                        attr, format, bufLength, AudioTrack.MODE_STREAM,
                        AudioManager.AUDIO_SESSION_ID_GENERATE
                    )
                    
                    if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                        throw Exception("Failed to initialize AudioTrack")
                    }
                    
                    audioTrack?.setVolume(1.0f)
                    audioTrack?.play()
                    isPlaying.set(true)
                    
                    Log.d(TAG, "Audio playback initialized - sampleRate: $sampleRate")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio playback", e)
            throw e
        }
    }
    
    /**
     * Stop audio playback with robust cleanup
     */
    private fun stopAudioPlayback() {
        try {
            isStopped.set(true)
            synchronized(audioLock) {
                audioTrack?.let { track ->
                    try {
                        track.pause()
                        track.flush()
                        track.stop()
                        track.release()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during AudioTrack cleanup", e)
                    }
                }
                audioTrack = null
                isPlaying.set(false)
            }
            Log.d(TAG, "Audio playback stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio playback", e)
        }
    }

    private fun initializeTts() {
        val modelType = when {
            modelName.contains("vits-mr-20250709", ignoreCase = true) ||
            modelName.contains("mr-20250709", ignoreCase = true) ||
            modelName.contains("vits-mr", ignoreCase = true) -> SherpaTtsConfigUtil.TtsModelType.VITS_MR_20250709
            modelName.contains("melo", ignoreCase = true) || 
            modelName.contains("zh_en", ignoreCase = true) -> SherpaTtsConfigUtil.TtsModelType.VITS_MELO_ZH_EN
            modelName.contains("piper", ignoreCase = true) -> SherpaTtsConfigUtil.TtsModelType.VITS_PIPER_EN_US_AMY
            modelName.contains("icefall", ignoreCase = true) && 
            modelName.contains("zh", ignoreCase = true) -> SherpaTtsConfigUtil.TtsModelType.VITS_ICEFALL_ZH
            modelName.contains("matcha", ignoreCase = true) -> SherpaTtsConfigUtil.TtsModelType.MATCHA_ICEFALL_ZH
            modelName.contains("kokoro", ignoreCase = true) -> SherpaTtsConfigUtil.TtsModelType.KOKORO_EN
            else -> SherpaTtsConfigUtil.TtsModelType.VITS_MR_20250709
        }
        val modelConfig = SherpaTtsConfigUtil.getTtsModelConfig(modelType)
        if (!SherpaTtsConfigUtil.validateModelAssets(context, modelConfig)) {
            val fallbackConfig = SherpaTtsConfigUtil.getTtsModelConfig(SherpaTtsConfigUtil.TtsModelType.VITS_PIPER_EN_US_AMY)
            if (!SherpaTtsConfigUtil.validateModelAssets(context, fallbackConfig)) {
                throw Exception("Neither primary model nor fallback model assets found")
            }
            initializeTtsWithConfig(fallbackConfig)
        } else {
            initializeTtsWithConfig(modelConfig)
        }
    }

    private fun initializeTtsWithConfig(modelConfig: SherpaTtsConfigUtil.TtsModelConfig) {
        val config = SherpaTtsConfigUtil.createOfflineTtsConfig(
            context = context,
            modelConfig = modelConfig,
            useExternalStorage = true
        ) ?: throw Exception("Failed to create TTS config for ${modelConfig.modelDir}")
        tts = OfflineTts(assetManager = context.assets, config = config)
    }

    /**
     * PCM float [-1,1] 轉 PCM 16bit Little Endian ByteArray
     */
    private fun floatArrayToPCM16(samples: FloatArray): ByteArray {
        val out = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = (samples[i].coerceIn(-1f, 1f) * 32767).toInt()
            out[i * 2] = (v and 0xFF).toByte()
            out[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return out
    }
}