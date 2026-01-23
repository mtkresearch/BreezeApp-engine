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
import com.mtkresearch.breezeapp.engine.runner.core.RunnerInfo
import kotlinx.coroutines.isActive
import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmModule
import java.util.concurrent.atomic.AtomicBoolean

@AIRunner(
    vendor = VendorType.EXECUTORCH,
    priority = RunnerPriority.LOW,
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
    private var modelPath: String? = null
    private var tokenizerPath: String? = null
    private var currentTemperature: Float? = null
    private var loadedModelId: String = ""  // Track loaded model for change detection

    companion object {
        private const val TAG = "ExecutorchLLMRunner"
        private const val DEFAULT_MODEL_ID = "Llama3_2-3b-4096-spin-250605-cpu"
        private const val DEFAULT_SEQ_LEN = 256
        private const val DEFAULT_TEMPERATURE = 0.8f
    }

    override fun load(modelId: String, settings: EngineSettings, initialParams: Map<String, Any>): Boolean {
        Log.d(TAG, "load() received initialParams: $initialParams")
        Log.d(TAG, "Loading Executorch LLM Runner with model: $modelId")
        if (isLoaded.get()) {
            unload()
        }
        return try {
            modelType = ExecutorchModelType.fromString(modelId)
            stopToken = ExecutorchPromptFormatter.getStopToken(modelType)

            val paths = ExecutorchUtils.resolveModelPaths(context!!, modelId)
                    ?: throw IllegalArgumentException("Could not resolve paths for model: $modelId")
            this.modelPath = paths.modelPath
            this.tokenizerPath = paths.tokenizerPath

            val runnerParams = settings.getRunnerParameters("ExecutorchLLMRunner")
            val requestedTemperature = when (val temp = initialParams["temperature"]) {
                is Number -> temp.toFloat()
                is String -> temp.toFloatOrNull()
                else -> null
            }
            val settingsTemperature = when (val temp = runnerParams["temperature"]) {
                is Number -> temp.toFloat()
                is String -> temp.toFloatOrNull()
                else -> null
            }

            val temperature = requestedTemperature ?: settingsTemperature ?: DEFAULT_TEMPERATURE
            this.currentTemperature = temperature

            llmModule = LlmModule(modelPath!!, tokenizerPath!!, temperature)
            val loadResult = llmModule?.load()

            if (loadResult != 0) {
                Log.e(TAG, "Failed to load Executorch model. Error code: $loadResult")
                isLoaded.set(false)
                loadedModelId = ""
                false
            } else {
                Log.d(TAG, "Executorch model loaded successfully: $modelId with temperature: $temperature")
                isLoaded.set(true)
                loadedModelId = modelId
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Executorch LLM Runner", e)
            isLoaded.set(false)
            loadedModelId = ""
            false
        }
    }

    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        throw UnsupportedOperationException("Non-streaming run is not supported for LLM models. Use runAsFlow instead.")
    }

    override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = callbackFlow {
        if (!isLoaded.get() || llmModule == null || modelPath == null || tokenizerPath == null) {
            trySend(InferenceResult.error(RunnerError.resourceUnavailable()))
            close()
            return@callbackFlow
        }

        val prompt = input.inputs[InferenceRequest.INPUT_TEXT] as? String
        if (prompt.isNullOrEmpty()) {
            trySend(InferenceResult.error(RunnerError.invalidInput("Input text is missing or empty.")))
            close()
            return@callbackFlow
        }

        // Check if client provided a system prompt - prepend it to user input for on-device models
        val systemPrompt = input.params[InferenceRequest.PARAM_SYSTEM_PROMPT] as? String
        val fullPrompt = if (!systemPrompt.isNullOrBlank()) {
            "$systemPrompt\n\nUser: $prompt"
        } else {
            prompt
        }

        val requestedTemperature = when (val temp = input.params["temperature"]) {
            is Number -> temp.toFloat()
            is String -> temp.toFloatOrNull()
            else -> null
        } ?: currentTemperature ?: DEFAULT_TEMPERATURE

        val maxSeqLen = when (val len = input.params["max_sequence_length"]) {
            is Number -> len.toInt()
            is String -> len.toIntOrNull()
            else -> null
        } ?: DEFAULT_SEQ_LEN

        if (requestedTemperature != currentTemperature) {
            Log.d(TAG, "Temperature changed from $currentTemperature to $requestedTemperature. Reloading module.")
            llmModule?.resetNative()
            llmModule = LlmModule(modelPath!!, tokenizerPath!!, requestedTemperature)
            val loadResult = llmModule?.load()
            if (loadResult != 0) {
                Log.e(TAG, "Failed to reload Executorch model. Error code: $loadResult")
                trySend(InferenceResult.error(RunnerError.processingError("Failed to reload model for new temperature.")))
                close()
                return@callbackFlow
            }
            currentTemperature = requestedTemperature
        }

        val formattedPrompt = ExecutorchPromptFormatter.formatPrompt(modelType, fullPrompt)
        val modelOutputBuffer = StringBuilder()
        var promptEchoFullyReceived = false

        val callback = object : LlmCallback {
            fun onStats(stats: Float) {
                Log.v(TAG, "Inference stats: $stats")
            }
            
            override fun onResult(result: String) {
                if (result == stopToken) {
                    Log.d(TAG, "Received stop token, sending final result and closing flow.")
                    trySend(InferenceResult.textOutput(text = "", metadata = mapOf(), partial = false))
                    close()
                    return
                }

                var tokenToSend: String?
                if (promptEchoFullyReceived) {
                    tokenToSend = result
                } else {
                    modelOutputBuffer.append(result)
                    val currentOutput = modelOutputBuffer.toString()
                    if (formattedPrompt.startsWith(currentOutput)) {
                        if (formattedPrompt == currentOutput) promptEchoFullyReceived = true
                        return
                    } else {
                        promptEchoFullyReceived = true
                        tokenToSend = currentOutput.removePrefix(formattedPrompt)
                    }
                }

                tokenToSend?.let { token ->
                    trySend(InferenceResult.textOutput(text = token, metadata = mapOf(), partial = true))
                }
            }
        }

        Log.d(TAG, "Starting generation with prompt: $formattedPrompt")
        val generateResult = llmModule?.generate(formattedPrompt, maxSeqLen, callback)

        if (generateResult != 0) {
            Log.e(TAG, "Generation failed with code $generateResult")
            trySend(InferenceResult.error(RunnerError.processingError("Generation failed with code $generateResult")))
            close()
        } else {
            // This block is executed after llmModule.generate() completes.
            // If the flow is still active, it means the stop token was not received
            // and we need to manually close the stream.
            if (isActive) {
                Log.d(TAG, "Generation completed without a stop token. Closing flow.")
                trySend(InferenceResult.textOutput(text = "", metadata = mapOf(), partial = false))
                close()
            }
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
        loadedModelId = ""
        currentTemperature = null
        modelPath = null
        tokenizerPath = null
    }

    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.LLM)

    override fun isLoaded(): Boolean = isLoaded.get()

    override fun getLoadedModelId(): String = loadedModelId

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

        temperature?.let { temp ->
            val tempValue = temp.toFloat()
            if (tempValue < 0.0f || tempValue > 2.0f) {
                return ValidationResult.invalid("Temperature must be between 0.0 and 2.0")
            }
        }

        maxSeqLen?.let { seqLen ->
            val seqLenValue = seqLen.toInt()
            if (seqLenValue < 1 || seqLenValue > 4096) {
                return ValidationResult.invalid("Max sequence length must be between 1 and 4096")
            }
        }

        return ValidationResult.valid()
    }
    
    private fun getSupportedModels(): List<ModelDefinition> {
        return modelRegistry?.getCompatibleModels("executorch") ?: listOf(
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