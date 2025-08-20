package com.mtkresearch.breezeapp.engine.runner.executorch

import android.content.Context
import android.util.Log
import com.mtkresearch.breezeapp.engine.annotation.AIRunner
import com.mtkresearch.breezeapp.engine.annotation.RunnerPriority
import com.mtkresearch.breezeapp.engine.annotation.VendorType
import com.mtkresearch.breezeapp.engine.model.*
import com.mtkresearch.breezeapp.engine.service.ModelRegistryService
import com.mtkresearch.breezeapp.engine.runner.core.BaseRunner
import com.mtkresearch.breezeapp.engine.runner.core.FlowStreamingRunner
import com.mtkresearch.breezeapp.engine.runner.core.ParameterSchema
import com.mtkresearch.breezeapp.engine.runner.core.ParameterType
import com.mtkresearch.breezeapp.engine.runner.core.SelectionOption
import com.mtkresearch.breezeapp.engine.runner.core.ValidationResult
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
    capabilities = [CapabilityType.LLM],
    defaultModel = "Llama3_2-3b-4096-spin-250605-cpu"
)
class ExecutorchLLMRunner(
    private val context: Context?,
    private val modelRegistry: ModelRegistryService? = null
) : BaseRunner, FlowStreamingRunner {

    private var llmModule: LlmModule? = null
    private val isLoaded = AtomicBoolean(false)
    private lateinit var modelType: ExecutorchModelType
    private lateinit var stopToken: String

    companion object {
        private const val TAG = "ExecutorchLLMRunner"
        private const val DEFAULT_MODEL_ID = "Llama3_2-3b-4096-spin-250605-cpu"
        private const val DEFAULT_SEQ_LEN = 256
    }

    // Load model using model ID from JSON registry
    override fun load(modelId: String, settings: EngineSettings): Boolean {
        Log.d(TAG, "Loading Executorch LLM Runner with model: $modelId")
        if (isLoaded.get()) {
            unload() // Unload previous model if any
        }
        return try {
            modelType = ExecutorchModelType.fromString(modelId)
            stopToken = ExecutorchPromptFormatter.getStopToken(modelType)

            val modelPath: String
            val tokenizerPath: String
        
            val paths = ExecutorchUtils.resolveModelPaths(context!!, modelId)
                    ?: throw IllegalArgumentException("Could not resolve paths for model: $modelId")
                modelPath = paths.modelPath
                tokenizerPath = paths.tokenizerPath

            // Get temperature from settings
            val runnerParams = settings.getRunnerParameters("ExecutorchLLMRunner")
            val temperature = runnerParams["temperature"] as? Float ?: 0.8f

            llmModule = LlmModule(modelPath, tokenizerPath, temperature)
            val loadResult = llmModule?.load()

            if (loadResult != 0) {
                Log.e(TAG, "Failed to load Executorch model. Error code: $loadResult")
                isLoaded.set(false)
                false
            } else {
                Log.d(TAG, "Executorch model loaded successfully: $modelId")
                isLoaded.set(true)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Executorch LLM Runner", e)
            isLoaded.set(false)
            false
        }
    }
    
    // No longer needed - using ExecutorchUtils instead

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
            // Note: onStats method signature may vary by ExecuTorch version
            // This implementation handles the interface requirement
            fun onStats(stats: Float) {
                Log.v(TAG, "Inference stats: $stats")
            }
            
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

                var tokenToSend: String?

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

    override fun getParameterSchema(): List<ParameterSchema> {
        return listOf(
            ParameterSchema(
                name = "model_id",
                displayName = "Executorch Model",
                description = "Select the Executorch LLM model to use for inference",
                type = ParameterType.SelectionType(
                    options = getSupportedModels().map { model ->
                        SelectionOption(
                            key = model.id,
                            displayName = "${model.id} (${model.ramGB}GB RAM)",
                            description = "Backend: ${model.backend}"
                        )
                    },
                    allowMultiple = false
                ),
                defaultValue = DEFAULT_MODEL_ID,
                isRequired = true,
                category = "Model Configuration"
            ),
            ParameterSchema(
                name = "temperature",
                displayName = "Temperature",
                description = "Controls randomness in text generation. Lower values (0.1) make output more focused, higher values (1.0) make it more creative.",
                type = ParameterType.FloatType(
                    minValue = 0.0,
                    maxValue = 2.0,
                    step = 0.1,
                    precision = 1
                ),
                defaultValue = 0.8f,
                isRequired = false,
                category = "Generation Parameters"
            ),
            ParameterSchema(
                name = "max_sequence_length",
                displayName = "Max Sequence Length",
                description = "Maximum number of tokens to generate in the response.",
                type = ParameterType.IntType(
                    minValue = 1,
                    maxValue = 4096,
                    step = 1
                ),
                defaultValue = DEFAULT_SEQ_LEN,
                isRequired = false,
                category = "Generation Parameters"
            )
        )
    }

    override fun validateParameters(parameters: Map<String, Any>): ValidationResult {
        val temperature = parameters["temperature"] as? Number
        val maxSeqLen = parameters["max_sequence_length"] as? Number

        // Validate temperature range
        temperature?.let { temp ->
            val tempValue = temp.toFloat()
            if (tempValue < 0.0f || tempValue > 2.0f) {
                return ValidationResult.invalid("Temperature must be between 0.0 and 2.0")
            }
        }

        // Validate sequence length
        maxSeqLen?.let { seqLen ->
            val seqLenValue = seqLen.toInt()
            if (seqLenValue < 1 || seqLenValue > 4096) {
                return ValidationResult.invalid("Max sequence length must be between 1 and 4096")
            }
        }

        return ValidationResult.valid()
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
    
    /**
     * Get supported models for Executorch runner
     */
    private fun getSupportedModels(): List<ModelDefinition> {
        return modelRegistry?.getCompatibleModels("executorch") ?: listOf(
            // Fallback models if registry not available
            ModelDefinition(
                id = "Llama3_2-3b-4096-250606-cpu",
                runner = "executorch",
                backend = "cpu",
                ramGB = 7,
                files = emptyList(),
                entryPoint = EntryPoint("file", "llama3_2-4096.pte"),
                capabilities = listOf(CapabilityType.LLM)
            ),
            ModelDefinition(
                id = "Llama3_2-3b-4096-spin-250605-cpu",
                runner = "executorch", 
                backend = "cpu",
                ramGB = 3,
                files = emptyList(),
                entryPoint = EntryPoint("file", "llama3_2-4096-spin.pte"),
                capabilities = listOf(CapabilityType.LLM)
            )
        )
    }
}
