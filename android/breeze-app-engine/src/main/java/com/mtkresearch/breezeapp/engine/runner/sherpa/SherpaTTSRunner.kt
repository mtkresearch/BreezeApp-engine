package com.mtkresearch.breezeapp.engine.runner.sherpa

import android.content.Context
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.mtkresearch.breezeapp.engine.annotation.AIRunner
import com.mtkresearch.breezeapp.engine.annotation.RunnerPriority
import com.mtkresearch.breezeapp.engine.annotation.VendorType
import com.mtkresearch.breezeapp.engine.runner.core.FlowStreamingRunner
import com.mtkresearch.breezeapp.engine.runner.core.RunnerInfo
import com.mtkresearch.breezeapp.engine.model.*
import com.mtkresearch.breezeapp.engine.runner.sherpa.base.BaseSherpaRunner
import com.mtkresearch.breezeapp.engine.runner.sherpa.base.BaseSherpaTtsRunner
import com.mtkresearch.breezeapp.engine.system.SherpaLibraryManager
import com.mtkresearch.breezeapp.engine.util.SherpaTtsConfigUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

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
 @AIRunner(
    vendor = VendorType.SHERPA,
    capabilities = [CapabilityType.TTS],
    apiLevel = 1,
    enabled = true
)
class SherpaTTSRunner(context: Context) : BaseSherpaTtsRunner(context), FlowStreamingRunner {
    companion object {
        private const val TAG = "SherpaTTSRunner"
    }

    private var tts: OfflineTts? = null

    override fun getTag(): String = TAG

    override fun load(modelId: String, settings: EngineSettings, initialParams: Map<String, Any>): Boolean {
        modelName = modelId
        return try {
            if (!SherpaLibraryManager.initializeGlobally()) throw Exception("Failed to initialize Sherpa ONNX library")
            if (!SherpaLibraryManager.isLibraryReady()) throw Exception("Sherpa ONNX library not ready for use")
            initializeTts()
            warmup()
            
            // CRITICAL FIX: Set isLoaded flag to true after successful initialization
            isLoaded.set(true)
            Log.i(TAG, "Model loaded successfully - isLoaded flag set to true")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SherpaTTSRunner", e)
            isLoaded.set(false) // Ensure flag is false on failure
            false
        }
    }

    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        // Validate model is loaded
        validateModelLoaded()?.let { return it }
        
        return try {
            SherpaLibraryManager.markInferenceStarted()
            
            // Validate input parameters
            val (text, textError) = validateTtsInput(input)
            textError?.let { 
                SherpaLibraryManager.markInferenceCompleted()
                return it
            }
            
            val speakerId = validateSpeakerId(input)
            val speed = validateSpeed(input)
            val startTime = System.currentTimeMillis()
            
            // Call the internal run method with TTFA capture
            var ttfa: Long? = null
            val audioData = run(text ?: "", modelName, "default", speed, speakerId) { capturedTtfa ->
                ttfa = capturedTtfa
            }
            
            val elapsed = System.currentTimeMillis() - startTime
            val outputs = mapOf("audioData" to audioData)
            val metadata = mutableMapOf<String, Any>(
                "sampleRate" to tts!!.sampleRate(),
                "channels" to 1,
                "bitDepth" to 16,
                "format" to "pcm16",
                "durationMs" to (audioData.size / 2 * 1000 / tts!!.sampleRate())
            )
            
            // Add TTFA to metadata if captured
            ttfa?.let { metadata["timeToFirstAudio"] = it }
            
            Log.d(TAG, "TTS run completed - size: ${audioData.size}, elapsed: ${elapsed}ms, TTFA: ${ttfa}ms")
            SherpaLibraryManager.markInferenceCompleted()
            InferenceResult.success(outputs, metadata, partial = false)
        } catch (e: Exception) {
            Log.e(TAG, "TTS run failed", e)
            SherpaLibraryManager.markInferenceCompleted()
            InferenceResult.error(RunnerError.runtimeError("TTS run failed: ${e.message}"))
        }
    }

    fun run(
        text: String,
        modelId: String,
        voiceId: String,
        speed: Float,
        speakerId: Int,
        onTtfa: ((Long) -> Unit)? = null
    ): ByteArray {
        if (tts == null) {
            Log.e(TAG, "TTS not initialized")
            return ByteArray(0)
        }

        try {
            // OPTIMIZATION: Split text into sentences to reduce TTFA (Time To First Audio)
            // Instead of processing the whole paragraph, we process and play sentence by sentence.
            // This avoids the "Head-of-Line Blocking" where the engine waits to encode the full text.
            val sentences = splitIntoSentences(text)
            Log.d(TAG, "Splitting text into ${sentences.size} sentences for low-latency streaming")

            val allAudioBytes = java.io.ByteArrayOutputStream()
            val generationStart = System.currentTimeMillis()
            var firstChunkReported = false
            
            // Initialize audio playback once for the whole session
            initAudioPlayback(tts!!.sampleRate())

            for ((index, sentence) in sentences.withIndex()) {
                if (isStopped.get()) break
                
                // Skip empty sentences
                if (sentence.isBlank()) continue
                
                Log.d(TAG, "Processing sentence ${index + 1}/${sentences.size}: \"${sentence.take(20)}...\"")
                
                // Add spaces for better prosody if needed, though splitting might have removed them
                val textToProcess = sentence.trim()
                
                tts!!.generateWithCallback(
                    text = textToProcess,
                    sid = speakerId,
                    speed = speed,
                    callback = { samples ->
                        if (!firstChunkReported) {
                            val ttfa = System.currentTimeMillis() - generationStart
                            Log.i(TAG, "TTFA (Time To First Audio): ${ttfa}ms")
                            onTtfa?.invoke(ttfa)
                            firstChunkReported = true
                        }

                        // Direct audio playback in callback
                        if (!isStopped.get()) {
                            synchronized(audioLock) {
                                val amplifiedSamples = amplifyVolume(samples, 2.0f)
                                audioTrack?.write(amplifiedSamples, 0, amplifiedSamples.size, AudioTrack.WRITE_BLOCKING)
                                
                                // Convert to PCM16 for returning the full audio data (optional, mostly for file mode)
                                val pcm16 = FloatArray(amplifiedSamples.size) { amplifiedSamples[it] }
                                    .map { (it * 32767).toInt().coerceIn(-32768, 32767).toShort() }
                                    .let { shortArray ->
                                        val byteBuffer = java.nio.ByteBuffer.allocate(shortArray.size * 2)
                                        byteBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                                        shortArray.forEach { s -> byteBuffer.putShort(s) }
                                        byteBuffer.array()
                                    }
                                allAudioBytes.write(pcm16)
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
            }
            
            return allAudioBytes.toByteArray()

        } catch (e: Exception) {
            Log.e(TAG, "TTS generation failed", e)
            return ByteArray(0)
        } finally {
            // We don't stop audioTrack here to allow tail to play, 
            // but we might want to release it if we are sure we are done.
            // For now, keep it open or let the next call reset it.
            // Actually, BaseSherpaTtsRunner.stop() handles cleanup.
        }
    }

    /**
     * Helper to split text into chunks for low-latency streaming.
     * Splits on sentences (.!?) and clauses (,:;) to minimize TTFA.
     * Also separates consecutive digits (e.g. "1997" -> "1 9 9 7") for better TTS pronunciation.
     * Merges very short chunks to preserve basic prosody.
     */
    private fun splitIntoSentences(text: String): List<String> {
        // Preprocess: Insert space between consecutive digits
        val processedText = text.mapIndexed { index, c ->
            if (c.isDigit() && index > 0 && text[index - 1].isDigit()) " $c" else c.toString()
        }.joinToString("")

        // Split by sentence terminators (.!?) AND clause terminators (,:;)
        // Keep delimiters.
        val rawChunks = processedText.split(Regex("(?<=[.!?,,:;])\\s+|(?<=[.!?,,:;])$")).filter { it.isNotBlank() }
        
        val mergedChunks = mutableListOf<String>()
        var currentChunk = ""
        
        for (chunk in rawChunks) {
            currentChunk += chunk
            // Heuristic: If chunk is too short (e.g. "No,"), append to next one to avoid robotic feel.
            // Unless it ends with a strong sentence terminator.
            if (currentChunk.length < 5 && !currentChunk.matches(Regex(".*[.!?]$"))) {
                currentChunk += " " // Add space for merging
                continue
            }
            mergedChunks.add(currentChunk.trim())
            currentChunk = ""
        }
        
        // Add any remaining text
        if (currentChunk.isNotBlank()) {
            mergedChunks.add(currentChunk.trim())
        }
        
        return mergedChunks
    }



    override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = flow {
        // Validate model is loaded
        validateModelLoaded()?.let { 
            emit(it)
            return@flow
        }
        
        try {
            SherpaLibraryManager.markInferenceStarted()
            
            // Validate input parameters
            val (text, textError) = validateTtsInput(input)
            textError?.let { 
                emit(it)
                SherpaLibraryManager.markInferenceCompleted()
                return@flow
            }
            
            val speakerId = validateSpeakerId(input)
            val speed = validateSpeed(input)
            val startTime = System.currentTimeMillis()
            
            // OPTIMIZATION: Split text into sentences for low-latency streaming
            val sentences = splitIntoSentences(text ?: "")
            Log.d(TAG, "Splitting text into ${sentences.size} sentences for flow streaming")

            initAudioPlayback(tts!!.sampleRate())

            // Use a Channel to bridge the blocking callback and the flow
            val channel = kotlinx.coroutines.channels.Channel<InferenceResult>(kotlinx.coroutines.channels.Channel.UNLIMITED)
            
            // Launch generation in a separate coroutine to avoid blocking the flow collector
            val generationJob = kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val generationStart = System.currentTimeMillis()
                    var firstChunkReported = false

                    for ((index, sentence) in sentences.withIndex()) {
                        if (isStopped.get()) break
                        if (sentence.isBlank()) continue

                        Log.d(TAG, "Processing flow sentence ${index + 1}/${sentences.size}")

                        tts!!.generateWithCallback(
                            text = sentence.trim(),
                            sid = speakerId,
                            speed = speed,
                            callback = { samples ->
                                if (!firstChunkReported) {
                                    val ttfa = System.currentTimeMillis() - generationStart
                                    Log.i(TAG, "TTFA (Flow): ${ttfa}ms")
                                    firstChunkReported = true
                                }

                                if (!isStopped.get()) {
                                    synchronized(audioLock) {
                                        val amplifiedSamples = amplifyVolume(samples, 2.0f)
                                        // Play audio directly
                                        audioTrack?.write(amplifiedSamples, 0, amplifiedSamples.size, AudioTrack.WRITE_BLOCKING)
                                        
                                        // Emit partial result via Channel
                                        val pcm16 = FloatArray(amplifiedSamples.size) { amplifiedSamples[it] }
                                            .map { (it * 32767).toInt().coerceIn(-32768, 32767).toShort() }
                                            .let { shortArray ->
                                                val byteBuffer = java.nio.ByteBuffer.allocate(shortArray.size * 2)
                                                byteBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                                                shortArray.forEach { s -> byteBuffer.putShort(s) }
                                                byteBuffer.array()
                                            }
                                        
                                        channel.trySend(InferenceResult.success(
                                            outputs = mapOf("audioChunk" to pcm16),
                                            metadata = mapOf("isPartial" to true),
                                            partial = true
                                        ))
                                    }
                                    return@generateWithCallback 1 // Continue
                                } else {
                                    synchronized(audioLock) {
                                        audioTrack?.stop()
                                    }
                                    return@generateWithCallback 0 // Stop
                                }
                            }
                        )
                    }
                    
                    val elapsed = System.currentTimeMillis() - startTime
                    Log.d(TAG, "TTS Flow completed - elapsed: ${elapsed}ms")
                    
                    // Send completion signal
                    channel.send(InferenceResult.success(
                        outputs = mapOf(),
                        metadata = mapOf("isLastChunk" to true),
                        partial = false
                    ))
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in generation coroutine", e)
                    channel.close(e)
                } finally {
                    channel.close()
                    SherpaLibraryManager.markInferenceCompleted()
                }
            }

            // Collect from channel and emit to flow
            for (result in channel) {
                emit(result)
            }
            
            // Ensure generation job is cancelled if flow collection stops
            generationJob.cancel()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in SherpaTTSRunner.runAsFlow", e)
            stopAudioPlayback()
            emit(InferenceResult.error(RunnerError.runtimeError(e.message ?: "Unknown error", e)))
            SherpaLibraryManager.markInferenceCompleted()
        }
    }

    override fun releaseModel() {
        stopAudioPlayback()
        tts = null
    }

    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.TTS)

    override fun getRunnerInfo(): RunnerInfo = RunnerInfo(
        name = "SherpaTTSRunner",
        version = "1.0.0",
        capabilities = getCapabilities(),
        description = "Sherpa ONNX TTS runner with real-time streaming audio playback"
    )

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
        
        // OPTIMIZATION: Set single thread for lower latency on mobile devices
        // This reduces CPU contention and thermal throttling
        config.model.numThreads = 1
        config.model.debug = false
        
        tts = OfflineTts(assetManager = context.assets, config = config)
    }

    /**
     * Warmup the model with a short inference to ensure ONNX runtime is fully initialized.
     * This performs memory allocation and graph optimization to prevent "cold start" stutter
     * on the first user request.
     */
    private fun warmup() {
        try {
            Log.d(TAG, "Starting TTS model warmup (initializing ONNX Runtime)...")
            val start = System.currentTimeMillis()
            // Generate a very short silent audio
            tts?.generate(text = "a", sid = 0, speed = 1.0f)
            val elapsed = System.currentTimeMillis() - start
            Log.i(TAG, "TTS model warmup completed in ${elapsed}ms - ONNX Runtime ready")
        } catch (e: Exception) {
            Log.w(TAG, "TTS model warmup failed (non-fatal)", e)
        }
    }
}