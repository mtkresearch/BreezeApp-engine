package com.mtkresearch.breezeapp.engine.runner.llamastack

import android.content.Context
import android.util.Base64
import android.util.Log
import com.mtkresearch.breezeapp.engine.annotation.*
import com.mtkresearch.breezeapp.engine.model.*
import com.mtkresearch.breezeapp.engine.runner.core.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext

@AIRunner(
    vendor = VendorType.EXECUTORCH,
    priority = RunnerPriority.NORMAL,
    capabilities = [CapabilityType.LLM, CapabilityType.VLM],
    defaultModel = "llama-3.2-90b-vision-instruct"
)
class LlamaStackRunner(
    private val context: Context? = null
) : BaseRunner, FlowStreamingRunner {

    companion object {
        private const val TAG = "LlamaStackRunner"
        private const val DEFAULT_MODEL_ID = "llama-3.2-90b-vision-instruct"
    }

    private var client: LlamaStackClient? = null
    private var config: LlamaStackConfig? = null
    private val isLoaded = AtomicBoolean(false)

    override fun load(modelId: String, settings: EngineSettings, initialParams: Map<String, Any>): Boolean {
        Log.d(TAG, "Loading LlamaStack runner with model: $modelId")
        Log.d(TAG, "Initial params: $initialParams")
        
        return try {
            if (isLoaded.get()) {
                unload()
            }

            val runnerParams = settings.getRunnerParameters("llamastack")
                .toMutableMap()
                .apply { putAll(initialParams) }
            
            config = LlamaStackConfig.fromParams(runnerParams, modelId)
            val validationResult = config!!.validateConfiguration()
            
            if (!validationResult.isValid) {
                Log.e(TAG, "Invalid configuration: ${validationResult.errorMessage}")
                return false
            }
            
            // Perform smart compatibility analysis
            SmartRunnerDetection.logCompatibilityAnalysis(config!!.endpoint, "LlamaStackRunner")

            client = LlamaStackClient(config!!)

            Log.i(TAG, "LlamaStack runner loaded successfully")
            Log.d(TAG, "Configuration - Endpoint: ${config!!.endpoint}, Model: ${config!!.modelId}")
            Log.d(TAG, "Capabilities - Vision: ${config!!.isVisionCapable()}, RAG: ${config!!.isRAGCapable()}")
            
            isLoaded.set(true)
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load LlamaStack runner", e)
            isLoaded.set(false)
            false
        }
    }

    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        throw UnsupportedOperationException("Non-streaming run is not supported for LlamaStack. Use runAsFlow instead.")
    }

    override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = callbackFlow {
        if (!isLoaded.get() || client == null || config == null) {
            trySend(InferenceResult.error(RunnerError.modelNotLoaded()))
            close()
            return@callbackFlow
        }

        val startTime = System.currentTimeMillis()

        try {
            val result = when {
                hasVisionInput(input) -> processVisionRequest(input)
                hasRAGRequest(input) -> processRAGRequest(input)
                hasAgentRequest(input) -> processAgentRequest(input)
                else -> processStandardLLMRequest(input)
            }

            val enhancedResult = enhanceResultWithMetrics(result, startTime, getRequestType(input))
            
            if (result.error == null) {
                emitTextAsStream(result.outputs[InferenceResult.OUTPUT_TEXT] as? String ?: "")
            } else {
                trySend(enhancedResult)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing request", e)
            trySend(InferenceResult.error(RunnerError.runtimeError("Processing failed: ${e.message}")))
        }
        
        close()
        
        awaitClose {
            Log.d(TAG, "Flow is closing")
        }
    }

    private suspend fun processVisionRequest(input: InferenceRequest): InferenceResult {
        val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String
        val image = input.inputs[InferenceRequest.INPUT_IMAGE] as? ByteArray

        if (text.isNullOrBlank() || image == null) {
            return InferenceResult.error(RunnerError.invalidInput("VLM requires both text and image input"))
        }

        if (!config!!.isVisionCapable()) {
            return InferenceResult.error(RunnerError.runtimeError("Vision capabilities not enabled"))
        }

        return try {
            val imageBase64 = Base64.encodeToString(image, Base64.NO_WRAP)
            val messages = listOf(
                Message(
                    role = "user",
                    content = listOf(
                        ContentItem(type = "text", text = text),
                        ContentItem(
                            type = "image",
                            image_url = ImageUrl(url = "data:image/jpeg;base64,$imageBase64")
                        )
                    )
                )
            )

            val request = ChatCompletionRequest(
                model = config!!.modelId,
                messages = messages,
                temperature = config!!.temperature,
                max_tokens = config!!.maxTokens
            )

            val response = client!!.chatCompletion(request)
            val responseText = response.choices.firstOrNull()?.message?.content ?: ""

            InferenceResult.textOutput(
                text = responseText,
                metadata = mapOf(
                    InferenceResult.META_MODEL_NAME to config!!.modelId,
                    "inference_mode" to "remote",
                    "capability_type" to "VLM",
                    "image_size_bytes" to image.size,
                    "llamastack_response_id" to response.id,
                    "usage" to (response.usage ?: Usage(0, 0, 0))
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "VLM processing failed", e)
            InferenceResult.error(RunnerError.runtimeError("Vision processing failed: ${e.message}"))
        }
    }

    private suspend fun processStandardLLMRequest(input: InferenceRequest): InferenceResult {
        val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String

        if (text.isNullOrBlank()) {
            return InferenceResult.error(RunnerError.invalidInput("Text input is required"))
        }

        return try {
            val messages = listOf(
                Message(
                    role = "user",
                    content = listOf(ContentItem(type = "text", text = text))
                )
            )

            val request = ChatCompletionRequest(
                model = config!!.modelId,
                messages = messages,
                temperature = config!!.temperature,
                max_tokens = config!!.maxTokens
            )

            val response = client!!.chatCompletion(request)
            val responseText = response.choices.firstOrNull()?.message?.content ?: ""

            InferenceResult.textOutput(
                text = responseText,
                metadata = mapOf(
                    InferenceResult.META_MODEL_NAME to config!!.modelId,
                    "inference_mode" to "remote",
                    "capability_type" to "LLM",
                    "llamastack_response_id" to response.id,
                    "usage" to (response.usage ?: Usage(0, 0, 0))
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "LLM processing failed", e)
            InferenceResult.error(RunnerError.runtimeError("Text processing failed: ${e.message}"))
        }
    }

    private suspend fun processRAGRequest(input: InferenceRequest): InferenceResult {
        if (!config!!.isRAGCapable()) {
            return processStandardLLMRequest(input)
        }

        val query = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
        val ragSources = input.params["rag_sources"] as? List<String> ?: emptyList()

        return try {
            val enhancedPrompt = buildRAGPrompt(query, ragSources)
            val messages = listOf(
                Message(
                    role = "user",
                    content = listOf(ContentItem(type = "text", text = enhancedPrompt))
                )
            )

            val request = ChatCompletionRequest(
                model = config!!.modelId,
                messages = messages,
                temperature = config!!.temperature,
                max_tokens = config!!.maxTokens
            )

            val response = client!!.chatCompletion(request)
            val responseText = response.choices.firstOrNull()?.message?.content ?: ""

            InferenceResult.textOutput(
                text = responseText,
                metadata = mapOf(
                    InferenceResult.META_MODEL_NAME to config!!.modelId,
                    "inference_mode" to "remote",
                    "capability_type" to "RAG",
                    "rag_sources_used" to ragSources.size,
                    "llamastack_response_id" to response.id
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "RAG processing failed", e)
            InferenceResult.error(RunnerError.runtimeError("RAG processing failed: ${e.message}"))
        }
    }

    private suspend fun processAgentRequest(input: InferenceRequest): InferenceResult {
        if (!config!!.isAgentCapable()) {
            return processStandardLLMRequest(input)
        }

        val query = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
        val availableTools = input.params["available_tools"] as? List<String> ?: emptyList()

        return try {
            val agentPrompt = buildAgentPrompt(query, availableTools)
            val messages = listOf(
                Message(
                    role = "user",
                    content = listOf(ContentItem(type = "text", text = agentPrompt))
                )
            )

            val request = ChatCompletionRequest(
                model = config!!.modelId,
                messages = messages,
                temperature = config!!.temperature,
                max_tokens = config!!.maxTokens
            )

            val response = client!!.chatCompletion(request)
            val responseText = response.choices.firstOrNull()?.message?.content ?: ""

            InferenceResult.textOutput(
                text = responseText,
                metadata = mapOf(
                    InferenceResult.META_MODEL_NAME to config!!.modelId,
                    "inference_mode" to "remote",
                    "capability_type" to "AGENT",
                    "tools_available" to availableTools.size,
                    "llamastack_response_id" to response.id
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Agent processing failed", e)
            InferenceResult.error(RunnerError.runtimeError("Agent processing failed: ${e.message}"))
        }
    }

    private suspend fun kotlinx.coroutines.channels.ProducerScope<InferenceResult>.emitTextAsStream(fullText: String) {
        if (fullText.isEmpty()) {
            trySend(InferenceResult.textOutput(text = "", metadata = mapOf(), partial = false))
            return
        }
        
        val words = fullText.split(" ")
        
        words.forEachIndexed { index, word ->
            val tokenToSend = word + if (index < words.size - 1) " " else ""
            
            trySend(InferenceResult.textOutput(
                text = tokenToSend,
                metadata = mapOf("partial_tokens" to (index + 1)),
                partial = true
            ))
            
            if (coroutineContext.isActive) {
                delay(50)
            }
        }
        
        trySend(InferenceResult.textOutput(text = "", metadata = mapOf(), partial = false))
    }

    private fun hasVisionInput(input: InferenceRequest): Boolean {
        return input.inputs.containsKey(InferenceRequest.INPUT_IMAGE) && 
               input.inputs[InferenceRequest.INPUT_IMAGE] is ByteArray
    }

    private fun hasRAGRequest(input: InferenceRequest): Boolean {
        return input.params.containsKey("enable_rag") && 
               input.params["enable_rag"] == true
    }

    private fun hasAgentRequest(input: InferenceRequest): Boolean {
        return input.params.containsKey("enable_agents") && 
               input.params["enable_agents"] == true
    }

    private fun getRequestType(input: InferenceRequest): String {
        return when {
            hasVisionInput(input) -> "VLM"
            hasRAGRequest(input) -> "RAG"
            hasAgentRequest(input) -> "AGENT"
            else -> "LLM"
        }
    }

    private fun buildRAGPrompt(query: String, sources: List<String>): String {
        if (sources.isEmpty()) return query
        
        return buildString {
            appendLine("Context information:")
            sources.forEachIndexed { index, source ->
                appendLine("${index + 1}. $source")
            }
            appendLine()
            appendLine("Based on the context above, please answer the following question:")
            appendLine(query)
        }
    }

    private fun buildAgentPrompt(query: String, tools: List<String>): String {
        if (tools.isEmpty()) return query
        
        return buildString {
            appendLine("You are an AI assistant with access to the following tools:")
            tools.forEach { tool ->
                appendLine("- $tool")
            }
            appendLine()
            appendLine("Please help with the following request:")
            appendLine(query)
        }
    }

    private fun enhanceResultWithMetrics(
        result: InferenceResult,
        startTime: Long,
        requestType: String
    ): InferenceResult {
        val processingTime = System.currentTimeMillis() - startTime
        
        val enhancedMetadata = result.metadata.toMutableMap().apply {
            put(InferenceResult.META_PROCESSING_TIME_MS, processingTime)
            put("request_type", requestType)
            put("endpoint_used", config!!.endpoint)
            put("llamastack_version", "remote-client-1.0")
        }
        
        return result.copy(metadata = enhancedMetadata)
    }

    override fun unload() {
        Log.d(TAG, "Unloading LlamaStack runner")
        client = null
        config = null
        isLoaded.set(false)
    }

    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.LLM, CapabilityType.VLM)

    override fun isLoaded(): Boolean = isLoaded.get()

    override fun getRunnerInfo(): com.mtkresearch.breezeapp.engine.runner.core.RunnerInfo {
        return com.mtkresearch.breezeapp.engine.runner.core.RunnerInfo(
            name = "LlamaStackRunner",
            version = "1.0.0",
            capabilities = getCapabilities(),
            description = "Remote-first LlamaStack runner for VLM, RAG, and Agent capabilities"
        )
    }

    override fun isSupported(): Boolean = true

    override fun getParameterSchema(): List<ParameterSchema> {
        return listOf(
            ParameterSchema(
                name = "endpoint",
                displayName = "API Endpoint",
                description = "OpenAI-compatible API endpoint (LlamaStack, OpenRouter, or other compatible services)",
                type = ParameterType.StringType(
                    minLength = 10,
                    pattern = Regex("^https?://.*")
                ),
                defaultValue = "https://api.llamastack.ai",
                isRequired = true,
                category = "Connection"
            ),
            ParameterSchema(
                name = "api_key",
                displayName = "API Key",
                description = "Authentication key for LlamaStack API (optional for localhost)",
                type = ParameterType.StringType(minLength = 10),
                defaultValue = "",
                isRequired = false,
                isSensitive = true,
                category = "Authentication"
            ),
            ParameterSchema(
                name = "model_id",
                displayName = "Model",
                description = "LlamaStack model to use for inference",
                type = ParameterType.SelectionType(
                    options = listOf(
                        SelectionOption("llama-3.2-90b-vision-instruct", "Llama 3.2 90B Vision", "Best for vision tasks"),
                        SelectionOption("llama-3.2-11b-vision-instruct", "Llama 3.2 11B Vision", "Balanced vision model"),
                        SelectionOption("llama-3.2-3b-instruct", "Llama 3.2 3B", "Lightweight text model"),
                        SelectionOption("llama-3.1-70b-instruct", "Llama 3.1 70B", "Large text model"),
                        SelectionOption("llama-3.1-8b-instruct", "Llama 3.1 8B", "Efficient text model")
                    ),
                    allowMultiple = false
                ),
                defaultValue = DEFAULT_MODEL_ID,
                isRequired = true,
                category = "Model Configuration"
            ),
            ParameterSchema(
                name = "temperature",
                displayName = "Temperature",
                description = "Controls randomness in responses (0.0 = deterministic, 1.0 = creative)",
                type = ParameterType.FloatType(
                    minValue = 0.0,
                    maxValue = 2.0,
                    step = 0.1,
                    precision = 1
                ),
                defaultValue = 0.7f,
                isRequired = false,
                category = "Generation Parameters"
            ),
            ParameterSchema(
                name = "max_tokens",
                displayName = "Max Tokens",
                description = "Maximum number of tokens to generate",
                type = ParameterType.IntType(
                    minValue = 1,
                    maxValue = 32768,
                    step = 256
                ),
                defaultValue = 4096,
                isRequired = false,
                category = "Generation Parameters"
            ),
            ParameterSchema(
                name = "enable_vision",
                displayName = "Enable Vision",
                description = "Enable vision-language model capabilities",
                type = ParameterType.BooleanType,
                defaultValue = true,
                isRequired = false,
                category = "Capabilities"
            ),
            ParameterSchema(
                name = "enable_rag",
                displayName = "Enable RAG",
                description = "Enable Retrieval-Augmented Generation capabilities",
                type = ParameterType.BooleanType,
                defaultValue = false,
                isRequired = false,
                category = "Capabilities"
            ),
            ParameterSchema(
                name = "enable_agents",
                displayName = "Enable Agents",
                description = "Enable AI agent capabilities with tool calling",
                type = ParameterType.BooleanType,
                defaultValue = false,
                isRequired = false,
                category = "Capabilities"
            )
        )
    }

    override fun validateParameters(parameters: Map<String, Any>): com.mtkresearch.breezeapp.engine.runner.core.ValidationResult {
        // Check for endpoint compatibility before validation
        val endpoint = parameters["endpoint"] as? String
        if (endpoint != null) {
            // Provide guidance for OpenRouter endpoints
            if (endpoint.contains("openrouter.ai")) {
                Log.w(TAG, "OpenRouter endpoint detected with LlamaStackRunner. Consider using OpenRouterLLMRunner for optimal compatibility.")
                // Allow but warn - user might want cross-compatibility
            }
            
            // Provide guidance for other incompatible endpoints
            if (endpoint.contains("openai.com") || endpoint.contains("anthropic.com")) {
                return com.mtkresearch.breezeapp.engine.runner.core.ValidationResult.invalid(
                    "Endpoint '${endpoint}' is not compatible with LlamaStackRunner. " +
                    "Please use the appropriate runner for this API or switch to a compatible endpoint."
                )
            }
        }
        
        val config = try {
            LlamaStackConfig.fromParams(parameters, DEFAULT_MODEL_ID)
        } catch (e: Exception) {
            return com.mtkresearch.breezeapp.engine.runner.core.ValidationResult.invalid("Invalid parameter format: ${e.message}")
        }
        
        val result = config.validateConfiguration()
        return if (result.isValid) {
            com.mtkresearch.breezeapp.engine.runner.core.ValidationResult.valid()
        } else {
            com.mtkresearch.breezeapp.engine.runner.core.ValidationResult.invalid(result.errorMessage ?: "Configuration validation failed")
        }
    }
}