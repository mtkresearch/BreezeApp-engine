package com.mtkresearch.breezeapp.engine.runner.sherpa

import android.content.Context
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.mtkresearch.breezeapp.engine.data.runner.core.BaseRunner
import com.mtkresearch.breezeapp.engine.data.runner.core.BaseRunnerCompanion
import com.mtkresearch.breezeapp.engine.data.runner.core.FlowStreamingRunner
import com.mtkresearch.breezeapp.engine.data.runner.core.RunnerInfo
import com.mtkresearch.breezeapp.engine.domain.model.*
import com.mtkresearch.breezeapp.engine.system.SherpaLibraryManager
import com.mtkresearch.breezeapp.engine.util.AssetCopyUtil
import com.mtkresearch.breezeapp.engine.util.AudioUtil
import com.mtkresearch.breezeapp.engine.util.SherpaTtsConfigUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * SherpaTTSRunner - Real TTS runner using Sherpa ONNX
 *
 * This runner loads a Sherpa ONNX TTS model and performs text-to-speech synthesis.
 * It supports both non-streaming and streaming (Flow) inference with audio playback.
 *
 * Features:
 * - Multiple TTS model support (VITS, Matcha, Kokoro)
 * - Real-time audio streaming with callback
 * - Audio file generation and playback
 * - Speaker ID and speed control
 * - Global library management integration
 */
class SherpaTTSRunner(private val context: Context) : BaseRunner, FlowStreamingRunner {
    companion object : BaseRunnerCompanion {
        private const val TAG = "SherpaTTSRunner"
        
        @JvmStatic
        override fun isSupported(): Boolean = true
    }

    private val isLoaded = AtomicBoolean(false)
    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    private var mediaPlayer: MediaPlayer? = null
    private var modelName: String = ""
    private var stopped = AtomicBoolean(false)

    override fun load(config: ModelConfig): Boolean {
        return try {
            Log.d(TAG, "Loading SherpaTTSRunner with config: ${config.modelName}")
            modelName = config.modelName

            // Ensure Sherpa library is loaded globally
            if (!SherpaLibraryManager.initializeGlobally()) {
                throw Exception("Failed to initialize Sherpa ONNX library")
            }

            if (!SherpaLibraryManager.isLibraryReady()) {
                throw Exception("Sherpa ONNX library not ready for use")
            }

            // Initialize TTS model
            initializeTts()
            
            // Initialize AudioTrack for real-time playback
            initializeAudioTrack()

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
        if (!isLoaded.get()) {
            return InferenceResult.error(RunnerError.modelNotLoaded())
        }

        return try {
            SherpaLibraryManager.markInferenceStarted()
            
            val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String
            if (text.isNullOrBlank()) {
                return InferenceResult.error(RunnerError.invalidInput("Text input required for TTS processing"))
            }

            // Extract TTS parameters
            val speakerId = (input.inputs["speaker_id"] as? Number)?.toInt() ?: 0
            val speed = (input.inputs["speed"] as? Number)?.toFloat() ?: 1.0f

            val startTime = System.currentTimeMillis()
            
            // Validate parameters
            if (speakerId < 0) {
                return InferenceResult.error(RunnerError.invalidInput("Speaker ID must be non-negative"))
            }
            if (speed <= 0) {
                return InferenceResult.error(RunnerError.invalidInput("Speed must be positive"))
            }

            // Generate audio
            val audio = tts!!.generate(text = text, sid = speakerId, speed = speed)
            
            if (audio.samples.isEmpty()) {
                return InferenceResult.error(RunnerError.runtimeError("Failed to generate audio"))
            }

            // Save audio file
            val filename = "${context.filesDir.absolutePath}/generated_${System.currentTimeMillis()}.wav"
            val saved = audio.save(filename)
            
            if (!saved) {
                Log.w(TAG, "Failed to save audio file, but continuing with audio data")
            }

            val elapsed = System.currentTimeMillis() - startTime
            
            val outputs = mutableMapOf<String, Any>(
                InferenceResult.OUTPUT_AUDIO to audio.samples,
                "sample_rate" to audio.sampleRate,
                "audio_file_path" to filename
            )

            val metadata = mapOf(
                InferenceResult.META_PROCESSING_TIME_MS to elapsed,
                InferenceResult.META_MODEL_NAME to modelName,
                InferenceResult.META_SESSION_ID to input.sessionId,
                "speaker_id" to speakerId,
                "speed" to speed,
                "audio_duration_ms" to (audio.samples.size * 1000 / audio.sampleRate),
                "audio_saved" to saved
            )

            SherpaLibraryManager.markInferenceCompleted()
            InferenceResult.success(outputs = outputs, metadata = metadata)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in SherpaTTSRunner.run", e)
            SherpaLibraryManager.markInferenceCompleted()
            InferenceResult.error(RunnerError.runtimeError(e.message ?: "Unknown error", e))
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

            // Validate parameters
            if (speakerId < 0) {
                emit(InferenceResult.error(RunnerError.invalidInput("Speaker ID must be non-negative")))
                return@flow
            }
            if (speed <= 0) {
                emit(InferenceResult.error(RunnerError.invalidInput("Speed must be positive")))
                return@flow
            }

            // Prepare audio track for streaming
            audioTrack?.let { AudioUtil.prepareForPlayback(it) }
            stopped.set(false)

            val startTime = System.currentTimeMillis()

            // Generate audio with streaming callback
            val audio = tts!!.generateWithCallback(
                text = text,
                sid = speakerId,
                speed = speed,
                callback = { samples ->
                    // This callback is called from native code for real-time audio streaming
                    audioTrack?.let { track ->
                        AudioUtil.writeAudioSamples(track, samples, stopped.get())
                    }
                    if (!stopped.get()) 1 else 0
                }
            )

            // Save final audio file
            val filename = "${context.filesDir.absolutePath}/generated_${System.currentTimeMillis()}.wav"
            val saved = audio.samples.isNotEmpty() && audio.save(filename)
            val elapsed = System.currentTimeMillis() - startTime

            // Emit final result
            emit(InferenceResult.success(
                outputs = mapOf(
                    InferenceResult.OUTPUT_AUDIO to audio.samples,
                    "sample_rate" to audio.sampleRate,
                    "audio_file_path" to filename
                ),
                metadata = mapOf(
                    InferenceResult.META_PROCESSING_TIME_MS to elapsed,
                    InferenceResult.META_MODEL_NAME to modelName,
                    InferenceResult.META_SESSION_ID to input.sessionId,
                    "speaker_id" to speakerId,
                    "speed" to speed,
                    "audio_duration_ms" to (audio.samples.size * 1000 / audio.sampleRate),
                    "audio_saved" to saved
                ),
                partial = false
            ))
            
            // Stop audio track
            audioTrack?.let { AudioUtil.stopAndCleanup(it) }
                    
        } catch (e: Exception) {
            Log.e(TAG, "Error in SherpaTTSRunner.runAsFlow", e)
            emit(InferenceResult.error(RunnerError.runtimeError(e.message ?: "Unknown error", e)))
        } finally {
            SherpaLibraryManager.markInferenceCompleted()
        }
    }

    /**
     * Play generated audio file using MediaPlayer
     */
    fun playAudioFile(filePath: String): Boolean {
        return try {
            mediaPlayer?.stop()
            mediaPlayer = MediaPlayer.create(context, Uri.fromFile(File(filePath)))
            mediaPlayer?.start()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play audio file: $filePath", e)
            false
        }
    }

    /**
     * Stop current audio playback and generation
     */
    fun stopAudio() {
        stopped.set(true)
        audioTrack?.let { AudioUtil.stopAndCleanup(it) }
        mediaPlayer?.stop()
        mediaPlayer = null
    }

    override fun unload() {
        Log.d(TAG, "Unloading SherpaTTSRunner")
        stopAudio()
        audioTrack = null
        tts = null
        isLoaded.set(false)
    }

    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.TTS)

    override fun isLoaded(): Boolean = isLoaded.get()

    override fun getRunnerInfo(): RunnerInfo = RunnerInfo(
        name = "SherpaTTSRunner",
        version = "1.0.0",
        capabilities = getCapabilities(),
        description = "Sherpa ONNX TTS runner with real-time audio streaming",
        isMock = false
    )

    /**
     * Initialize TTS model based on configuration
     * Supports multiple TTS model types including your vits-melo-tts-zh_en model
     */
    private fun initializeTts() {
        // Determine model type from modelName or use default
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
            else -> SherpaTtsConfigUtil.TtsModelType.VITS_MR_20250709 // Default to your model
        }
        
        Log.d(TAG, "Initializing TTS with model type: $modelType")
        
        // Get model configuration
        val modelConfig = SherpaTtsConfigUtil.getTtsModelConfig(modelType)
        
        // Validate that model assets exist
        if (!SherpaTtsConfigUtil.validateModelAssets(context, modelConfig)) {
            Log.w(TAG, "Model assets validation failed for ${modelConfig.modelDir}, trying fallback...")
            
            // Try fallback configuration if primary model not found
            val fallbackConfig = SherpaTtsConfigUtil.getTtsModelConfig(SherpaTtsConfigUtil.TtsModelType.VITS_PIPER_EN_US_AMY)
            if (!SherpaTtsConfigUtil.validateModelAssets(context, fallbackConfig)) {
                throw Exception("Neither primary model (${modelConfig.modelDir}) nor fallback model (${fallbackConfig.modelDir}) assets found")
            }
            
            Log.i(TAG, "Using fallback model: ${fallbackConfig.modelDir}")
            initializeTtsWithConfig(fallbackConfig)
        } else {
            Log.i(TAG, "Using primary model: ${modelConfig.modelDir}")
            initializeTtsWithConfig(modelConfig)
        }
    }
    
    /**
     * Initialize TTS with specific model configuration
     */
    private fun initializeTtsWithConfig(modelConfig: SherpaTtsConfigUtil.TtsModelConfig) {
        Log.d(TAG, "Initializing TTS with config: ${modelConfig.description}")
        Log.d(TAG, "Model directory: ${modelConfig.modelDir}")
        Log.d(TAG, "Model name: ${modelConfig.modelName}")
        Log.d(TAG, "Lexicon: ${modelConfig.lexicon}")
        Log.d(TAG, "Dict directory: ${modelConfig.dictDir}")
        
        // Create Sherpa TTS configuration
        val config = SherpaTtsConfigUtil.createOfflineTtsConfig(
            context = context,
            modelConfig = modelConfig,
            useExternalStorage = true
        ) ?: throw Exception("Failed to create TTS config for ${modelConfig.modelDir}")

        // Initialize TTS
        tts = OfflineTts(assetManager = context.assets, config = config)
        Log.i(TAG, "TTS initialized successfully with sample rate: ${tts!!.sampleRate()}")
        Log.i(TAG, "Model description: ${modelConfig.description}")
    }

    /**
     * Initialize AudioTrack for real-time audio playback
     */
    private fun initializeAudioTrack() {
        val sampleRate = tts?.sampleRate() ?: 22050
        audioTrack = AudioUtil.createAudioTrack(sampleRate)
        Log.d(TAG, "AudioTrack initialized for sample rate: $sampleRate")
    }
}