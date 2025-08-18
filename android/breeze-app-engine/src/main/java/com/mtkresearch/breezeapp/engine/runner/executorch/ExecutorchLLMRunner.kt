package com.mtkresearch.breezeapp.engine.runner.executorch

import android.content.Context
import android.util.Log
import com.mtkresearch.breezeapp.engine.annotation.AIRunner
import com.mtkresearch.breezeapp.engine.annotation.RunnerPriority
import com.mtkresearch.breezeapp.engine.annotation.VendorType
import com.mtkresearch.breezeapp.engine.model.*
import com.mtkresearch.breezeapp.engine.runner.core.BaseRunner
import com.mtkresearch.breezeapp.engine.runner.core.FlowStreamingRunner
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.toList
import com.mtkresearch.breezeapp.engine.runner.core.RunnerInfo
import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmModule
import java.util.concurrent.atomic.AtomicBoolean

@AIRunner(
    vendor = VendorType.EXECUTORCH,
    priority = RunnerPriority.HIGH,
    capabilities = [CapabilityType.LLM]
)
class ExecutorchLLMRunner(private val context: Context?) : BaseRunner, FlowStreamingRunner {

    private var llmModule: LlmModule? = null
    private val isLoaded = AtomicBoolean(false)
    private lateinit var modelType: ExecutorchModelType
    private lateinit var stopToken: String

    companion object {
        private const val TAG = "ExecutorchLLMRunner"
        private const val DEFAULT_MODEL_ID = "Llama3_2-3b-4096-spin-250605-cpu"
        private const val DEFAULT_SEQ_LEN = 256
    }

    override fun load(): Boolean {
        if (context == null) {
            Log.e(TAG, "Context is null, cannot perform default load.")
            return false
        }

        val paths = ExecutorchUtils.resolveDefaultModelPaths(context, DEFAULT_MODEL_ID)
        if (paths == null) {
            Log.e(TAG, "Could not resolve paths for default model: $DEFAULT_MODEL_ID")
            return false
        }

        val defaultConfig = ModelConfig(
            modelName = DEFAULT_MODEL_ID,
            modelPath = paths.modelPath,
            parameters = mapOf(
                "tokenizerPath" to paths.tokenizerPath,
                "temperature" to 0.0f
            )
        )
        return load(defaultConfig)
    }

    override fun load(config: ModelConfig): Boolean {
        Log.d(TAG, "Loading Executorch LLM Runner with model: ${config.modelName}")
        if (isLoaded.get()) {
            unload() // Unload previous model if any
        }
        return try {
            modelType = ExecutorchModelType.fromString(config.modelName)
            stopToken = ExecutorchPromptFormatter.getStopToken(modelType)

            val modelPath = config.modelPath ?: throw IllegalArgumentException("Model path is missing in ModelConfig")
            val tokenizerPath = config.parameters["tokenizerPath"] as? String ?: throw IllegalArgumentException("Tokenizer path is missing in ModelConfig parameters")
            val temperature = (config.parameters["temperature"] as? Number)?.toFloat() ?: 0.8f

            llmModule = LlmModule(modelPath, tokenizerPath, temperature)
            val loadResult = llmModule?.load()

            if (loadResult != 0) {
                Log.e(TAG, "Failed to load Executorch model. Error code: $loadResult")
                isLoaded.set(false)
                false
            } else {
                Log.d(TAG, "Executorch model loaded successfully: ${config.modelName}")
                isLoaded.set(true)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Executorch LLM Runner", e)
            isLoaded.set(false)
            false
        }
    }

    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        throw UnsupportedOperationException("Non-streaming run is not supported for LLM models. Use runAsFlow instead.")
    }

    override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = callbackFlow {
        if (!isLoaded.get() || llmModule == null) {
            trySend(InferenceResult.error(RunnerError.modelNotLoaded()))
            close()
            return@callbackFlow
        }

        val prompt = input.inputs[InferenceRequest.INPUT_TEXT] as? String
        if (prompt.isNullOrEmpty()) {
            trySend(InferenceResult.error(RunnerError.invalidInput("Input text is missing or empty.")))
            close()
            return@callbackFlow
        }

        val formattedPrompt = ExecutorchPromptFormatter.formatPrompt(modelType, prompt)
        val modelOutputBuffer = StringBuilder()
        var promptEchoFullyReceived = false

        val callback = object : LlmCallback {
            override fun onResult(result: String) {
                if (result == stopToken) {
                    Log.d(TAG, "Received stop token, sending final result and closing flow.")
                    // Send the final, non-partial result to signal completion
                    trySend(
                        InferenceResult.textOutput(
                            text = "",
                            metadata = mapOf(),
                            partial = false
                        )
                    )
                    close()
                    return
                }

                var tokenToSend: String? = null

                if (promptEchoFullyReceived) {
                    // Echo already handled, send the new token
                    tokenToSend = result
                } else {
                    // Still handling the prompt echo
                    modelOutputBuffer.append(result)
                    val currentOutput = modelOutputBuffer.toString()

                    if (formattedPrompt.startsWith(currentOutput)) {
                        // Still matching the prompt, do nothing until it diverges or matches completely
                        if (formattedPrompt == currentOutput) {
                            promptEchoFullyReceived = true
                        }
                        return
                    } else {
                        // Diverged from prompt, generation has started
                        promptEchoFullyReceived = true
                        val generatedPart = currentOutput.removePrefix(formattedPrompt)
                        tokenToSend = generatedPart
                    }
                }

                // Send only the new token, not accumulated text
                tokenToSend?.let { token ->
                    val sendResult = trySend(
                        InferenceResult.textOutput(
                            text = token,
                            metadata = mapOf(),
                            partial = true
                        )
                    )
                    if (!sendResult.isSuccess) {
                        Log.w(TAG, "Failed to send partial result through flow")
                    }
                }
            }
        }

        Log.d(TAG, "Starting generation with prompt: $formattedPrompt")
        
        val generateResult = llmModule?.generate(formattedPrompt, DEFAULT_SEQ_LEN, callback)

        if (generateResult != 0) {
            Log.e(TAG, "Generation failed with code $generateResult")
            trySend(InferenceResult.error(RunnerError.runtimeError("Generation failed with code $generateResult")))
            close()
        }
        
        awaitClose {
            Log.d(TAG, "Flow is closing. Stopping generation.")
            llmModule?.stop()
        }
    }

    override fun unload() {
        Log.d(TAG, "Unloading Executorch LLM Runner")
        llmModule?.resetNative()
        llmModule = null
        isLoaded.set(false)
    }

    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.LLM)

    override fun isLoaded(): Boolean = isLoaded.get()

    override fun getRunnerInfo(): RunnerInfo {
        return RunnerInfo(
            name = "ExecutorchLLMRunner",
            version = "1.0.0",
            capabilities = getCapabilities(),
            description = "Runner for ExecuTorch LLM models (e.g., Llama3.2, Breeze2)."
        )
    }

    override fun isSupported(): Boolean {
        return true // run using XNNPACK
    }
    
    /**
     * Combines partial results into a single result
     */
    private fun combinePartialResults(partialResults: List<InferenceResult>): InferenceResult {
        if (partialResults.isEmpty()) {
            return InferenceResult.error(RunnerError.runtimeError("No results received from model"))
        }
        
        // Combine all text outputs
        val combinedText = partialResults
            .filter { it.error == null }
            .joinToString("") { it.outputs[InferenceResult.OUTPUT_TEXT] as? String ?: "" }
        
        // Get metadata from the last result if available
        val lastResult = partialResults.lastOrNull { it.error == null }
        val metadata = lastResult?.metadata?.toMutableMap() ?: mutableMapOf()
        metadata["partial"] = false
        
        return InferenceResult.textOutput(
            text = combinedText,
            metadata = metadata,
            partial = false
        )
    }
}
