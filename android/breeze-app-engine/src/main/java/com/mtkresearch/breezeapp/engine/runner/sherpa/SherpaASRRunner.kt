package com.mtkresearch.breezeapp.engine.runner.sherpa

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.mtkresearch.breezeapp.engine.data.runner.core.BaseRunner
import com.mtkresearch.breezeapp.engine.data.runner.core.BaseRunnerCompanion
import com.mtkresearch.breezeapp.engine.data.runner.core.FlowStreamingRunner
import com.mtkresearch.breezeapp.engine.data.runner.core.RunnerInfo
import com.mtkresearch.breezeapp.engine.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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

    override fun load(config: ModelConfig): Boolean {
        return try {
            Log.d(TAG, "Loading SherpaASRRunner with config: ${'$'}{config.modelName}")
            modelName = config.modelName
            // Use the asset directory and file names as specified in asr_dev.md
            val modelDir = "sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20"
            val encoderPath = "$modelDir/encoder-epoch-99-avg-1.int8.onnx"
            val decoderPath = "$modelDir/decoder-epoch-99-avg-1.onnx"
            val joinerPath = "$modelDir/joiner-epoch-99-avg-1.int8.onnx"
            val tokensPath = "$modelDir/tokens.txt"

            val transducerConfig = OnlineTransducerModelConfig()
            transducerConfig.encoder = encoderPath
            transducerConfig.decoder = decoderPath
            transducerConfig.joiner = joinerPath

            val modelConfig = OnlineModelConfig()
            modelConfig.transducer = transducerConfig
            modelConfig.tokens = tokensPath
            modelConfig.modelType = MODEL_TYPE
            modelConfig.debug = false

            val recognizerConfig = OnlineRecognizerConfig()
            recognizerConfig.modelConfig = modelConfig
            recognizer = OnlineRecognizer(context.assets, recognizerConfig)
            isLoaded.set(true)
            Log.i(TAG, "SherpaASRRunner loaded successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load SherpaASRRunner", e)
            isLoaded.set(false)
            false
        }
    }

    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        if (!isLoaded.get()) {
            return InferenceResult.error(RunnerError.modelNotLoaded())
        }
        return try {
            val audioData = input.inputs[InferenceRequest.INPUT_AUDIO] as? ByteArray
            if (audioData == null) {
                return InferenceResult.error(RunnerError.invalidInput("Audio data required for ASR processing"))
            }
            
            Log.d(TAG, "Processing ASR with ${audioData.size} bytes of audio data")
            
            val startTime = System.currentTimeMillis()
            val streamObj = recognizer!!.createStream("")
            
            // Convert ByteArray (PCM16) to float[] as required by Sherpa
            // PCM16 format: little-endian 16-bit samples
            val floatSamples = FloatArray(audioData.size / 2) { i ->
                val sample = ((audioData[i * 2 + 1].toInt() and 0xFF) shl 8) or 
                           (audioData[i * 2].toInt() and 0xFF)
                sample / 32768.0f
            }
            
            Log.d(TAG, "Converted ${floatSamples.size} samples for processing")
            
            streamObj.acceptWaveform(floatSamples, SAMPLE_RATE)
            while (recognizer!!.isReady(streamObj)) {
                recognizer!!.decode(streamObj)
            }
            
            val text = recognizer!!.getResult(streamObj).text
            val confidence = 0.95 // Sherpa does not provide confidence, so use a placeholder
            val elapsed = System.currentTimeMillis() - startTime
            
            Log.d(TAG, "ASR result: '$text' (${elapsed}ms)")
            
            streamObj.release()
            InferenceResult.success(
                outputs = mapOf(InferenceResult.OUTPUT_TEXT to text),
                metadata = mapOf(
                    InferenceResult.META_CONFIDENCE to confidence,
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
            val audioData = input.inputs[InferenceRequest.INPUT_AUDIO] as? ByteArray
            if (audioData == null) {
                emit(InferenceResult.error(RunnerError.invalidInput("Audio data required for ASR processing")))
                return@flow
            }
            
            Log.d(TAG, "Processing streaming ASR with ${audioData.size} bytes of audio data")
            
            val streamObj = recognizer!!.createStream("")
            
            // Convert ByteArray (PCM16) to float[] as required by Sherpa
            // PCM16 format: little-endian 16-bit samples
            val floatSamples = FloatArray(audioData.size / 2) { i ->
                val sample = ((audioData[i * 2 + 1].toInt() and 0xFF) shl 8) or 
                           (audioData[i * 2].toInt() and 0xFF)
                sample / 32768.0f
            }
            
            Log.d(TAG, "Converted ${floatSamples.size} samples for streaming processing")
            
            // For streaming, split into chunks (simulate real-time)
            val chunkSize = SAMPLE_RATE / 10 // 100ms chunks
            var offset = 0
            var segmentIdx = 0
            val startTime = System.currentTimeMillis()
            
            while (offset < floatSamples.size) {
                val end = (offset + chunkSize).coerceAtMost(floatSamples.size)
                val chunk = floatSamples.sliceArray(offset until end)
                streamObj.acceptWaveform(chunk, SAMPLE_RATE)
                
                while (recognizer!!.isReady(streamObj)) {
                    recognizer!!.decode(streamObj)
                }
                
                val text = recognizer!!.getResult(streamObj).text
                Log.d(TAG, "Streaming ASR segment $segmentIdx: '$text'")
                
                emit(
                    InferenceResult.success(
                        outputs = mapOf(InferenceResult.OUTPUT_TEXT to text),
                        metadata = mapOf(
                            InferenceResult.META_CONFIDENCE to 0.95,
                            InferenceResult.META_SEGMENT_INDEX to segmentIdx,
                            InferenceResult.META_SESSION_ID to input.sessionId,
                            InferenceResult.META_MODEL_NAME to modelName
                        ),
                        partial = true
                    )
                )
                offset = end
                segmentIdx++
            }
            
            // Final result
            val elapsed = System.currentTimeMillis() - startTime
            val finalText = recognizer!!.getResult(streamObj).text
            
            Log.d(TAG, "Final ASR result: '$finalText' (${elapsed}ms)")
            
            emit(
                InferenceResult.success(
                    outputs = mapOf(InferenceResult.OUTPUT_TEXT to finalText),
                    metadata = mapOf(
                        InferenceResult.META_CONFIDENCE to 0.95,
                        InferenceResult.META_PROCESSING_TIME_MS to elapsed,
                        InferenceResult.META_MODEL_NAME to modelName,
                        InferenceResult.META_SESSION_ID to input.sessionId
                    ),
                    partial = false
                )
            )
            streamObj.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error in SherpaASRRunner.runAsFlow", e)
            emit(InferenceResult.error(RunnerError.runtimeError(e.message ?: "Unknown error", e)))
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
}
