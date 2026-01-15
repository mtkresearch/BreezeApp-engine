package com.mtkresearch.breezeapp.engine.runner.llamastack

import android.content.Context
import android.util.Base64
import android.util.Log
import com.llama.llamastack.client.LlamaStackClientClient
import com.llama.llamastack.client.okhttp.LlamaStackClientOkHttpClient
import com.llama.llamastack.models.*
import com.mtkresearch.breezeapp.engine.annotation.*
import com.mtkresearch.breezeapp.engine.model.*
import com.mtkresearch.breezeapp.engine.runner.core.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay

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
        private const val DEFAULT_MODEL_ID = "llama3.2:3b"
    }

    private var llamaStackClient: LlamaStackClientClient? = null
    private var config: LlamaStackConfig? = null
    private val isLoaded = AtomicBoolean(false)
    private var loadedModelId: String = ""  // Track loaded model for change detection

    override fun load(modelId: String, settings: EngineSettings, initialParams: Map<String, Any>): Boolean {
        Log.d(TAG, "Loading LlamaStack runner with model: $modelId")
        Log.d(TAG, "Initial params: $initialParams")
        Log.d(TAG, "Endpoint from initialParams: ${initialParams["endpoint"]}")
        
        return try {
            if (isLoaded.get()) {
                Log.d(TAG, "Runner already loaded, unloading to apply new configuration")
                unload()
            }

            val runnerParams = settings.getRunnerParameters("LlamaStackRunner")
                .toMutableMap()
                .apply { putAll(initialParams) }
            
            config = LlamaStackConfig.fromParams(runnerParams, modelId)
            Log.d(TAG, "Final config - Endpoint: ${config!!.endpoint}, Model: ${config!!.modelId}")
            
            val validationResult = config!!.validateConfiguration()
            
            if (!validationResult.isValid) {
                Log.e(TAG, "Invalid configuration: ${validationResult.errorMessage}")
                return false
            }
            
            // Create official LlamaStack client
            llamaStackClient = createOfficialClient(config!!)

            Log.i(TAG, "LlamaStack runner loaded successfully with official SDK")
            Log.d(TAG, "Configuration - Endpoint: ${config!!.endpoint}, Model: ${config!!.modelId}")
            Log.d(TAG, "Capabilities - Vision: ${config!!.isVisionCapable()}, RAG: ${config!!.isRAGCapable()}")

            isLoaded.set(true)
            loadedModelId = modelId
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load LlamaStack runner", e)
            isLoaded.set(false)
            loadedModelId = ""
            false
        }
    }

    private fun createOfficialClient(config: LlamaStackConfig): LlamaStackClientClient {
        Log.d(TAG, "Creating official LlamaStack client for endpoint: ${config.endpoint}")
        
        val clientBuilder = LlamaStackClientOkHttpClient.builder()
            .baseUrl(config.endpoint)
            .headers(mapOf("x-llamastack-client-version" to listOf("0.2.14")))
        
        // Add API key if provided
        if (!config.apiKey.isNullOrBlank()) {
            clientBuilder.headers(mapOf(
                "x-llamastack-client-version" to listOf("0.2.14"),
                "Authorization" to listOf("Bearer ${config.apiKey}")
            ))
        }
        
        return clientBuilder.build()
    }

    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        if (!isLoaded.get() || llamaStackClient == null || config == null) {
            return InferenceResult.error(RunnerError.modelNotLoaded())
        }

        val startTime = System.currentTimeMillis()
        return try {
            val result = runBlocking {
                when {
                    hasVisionInput(input) -> processVisionRequest(input)
                    hasRAGRequest(input) -> processRAGRequest(input)
                    hasAgentRequest(input) -> processAgentRequest(input)
                    else -> processStandardLLMRequest(input)
                }
            }
            
            enhanceResultWithMetrics(result, startTime, getRequestType(input), input)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing request", e)
            InferenceResult.error(RunnerError.runtimeError("Processing failed: ${e.message}"))
        }
    }

    /**
     * STREAMING NOT YET SUPPORTED BY LLAMASTACK SDK
     * 
     * According to Llama Stack official documentation (as of v0.2.14):
     * "Streaming response is a work-in-progress for local and remote inference"
     * 
     * Current behavior: Falls back to non-streaming run() and emits single complete result.
     * 
     * TODO: Update this when LlamaStack SDK adds proper streaming support:
     * - Replace chatCompletion() with chatCompletionStreaming() 
     * - Handle streaming response chunks properly
     * - Remove this fallback implementation
     * 
     * Track: https://github.com/meta-llama/llama-stack-client-kotlin/issues
     */
    override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = callbackFlow {
        if (!isLoaded.get() || llamaStackClient == null || config == null) {
            trySend(InferenceResult.error(RunnerError.modelNotLoaded()))
            close()
            return@callbackFlow
        }

        val startTime = System.currentTimeMillis()

        try {
            when {
                hasVisionInput(input) -> {
                    // VLM: Use non-streaming (Guardian-like behavior)
                    Log.d(TAG, "VLM request: Using non-streaming mode")
                    val result = processVisionRequest(input)
                    trySend(enhanceResultWithMetrics(result, startTime, getRequestType(input), input))
                }
                hasRAGRequest(input) -> {
                    // RAG: Use non-streaming (Guardian-like behavior)
                    Log.d(TAG, "RAG request: Using non-streaming mode")
                    val result = processRAGRequest(input)
                    trySend(enhanceResultWithMetrics(result, startTime, getRequestType(input), input))
                }
                hasAgentRequest(input) -> {
                    // Agent: Use non-streaming (Guardian-like behavior)
                    Log.d(TAG, "Agent request: Using non-streaming mode")
                    val result = processAgentRequest(input)
                    trySend(enhanceResultWithMetrics(result, startTime, getRequestType(input), input))
                }
                else -> {
                    // Standard LLM chat: Use proper streaming
                    Log.d(TAG, "Standard LLM chat: Using streaming mode")
                    processStandardLLMRequestStreaming(input, startTime) { result ->
                        trySend(result)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Streaming error", e)
            trySend(InferenceResult.error(RunnerError.runtimeError("Streaming failed: ${e.message}")))
        }

        close()
        awaitClose {
            Log.d(TAG, "Streaming flow closed")
        }
    }

    private suspend fun processVisionRequest(input: InferenceRequest): InferenceResult {
        val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String
        val image = input.inputs[InferenceRequest.INPUT_IMAGE] as? ByteArray

        if (text.isNullOrBlank() || image == null) {
            return InferenceResult.error(RunnerError.invalidInput("VLM requires both text and image input"))
        }

        val runtimeConfig = resolveRuntimeParameters(input)
        val clientForRequest = getClientForRequest(runtimeConfig)

        if (!runtimeConfig.enableVision || !runtimeConfig.modelId.contains("vision", ignoreCase = true)) {
            return InferenceResult.error(RunnerError.runtimeError("Vision capabilities not enabled for model: ${runtimeConfig.modelId}"))
        }

        return try {
            Log.d(TAG, "Processing vision request with official SDK")
            Log.d(TAG, "Using runtime model: ${runtimeConfig.modelId}")
            
            val imageBase64 = Base64.encodeToString(image, Base64.NO_WRAP)
            
            // Create messages using official SDK - following documentation pattern
            val userMessage = UserMessage.builder()
                .content(InterleavedContent.ofString("$text [Image: data:image/jpeg;base64,$imageBase64]"))
                .build()
            val messages = listOf(Message.ofUser(userMessage))

            // Use official SDK inference().chatCompletion() with runtime parameters
            val chatCompletionParams = InferenceChatCompletionParams.builder()
                .modelId(runtimeConfig.modelId)
                .messages(messages.toList())
                .build()
            val response = clientForRequest.inference().chatCompletion(chatCompletionParams)

            val responseText = response.completionMessage().content().string() ?: ""

            InferenceResult.textOutput(
                text = responseText,
                metadata = mapOf(
                    InferenceResult.META_MODEL_NAME to runtimeConfig.modelId,
                    "inference_mode" to "remote",
                    "capability_type" to "VLM",
                    "image_size_bytes" to image.size,
                    "endpoint_used" to runtimeConfig.endpoint,
                    "sdk_version" to "official-0.2.14",
                    "runtime_applied" to true
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "VLM processing failed with official SDK", e)
            InferenceResult.error(RunnerError.runtimeError("Vision processing failed: ${e.message}"))
        }
    }

    private suspend fun processStandardLLMRequest(input: InferenceRequest): InferenceResult {
        val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String

        if (text.isNullOrBlank()) {
            return InferenceResult.error(RunnerError.invalidInput("Text input is required"))
        }

        val runtimeConfig = resolveRuntimeParameters(input)
        val clientForRequest = getClientForRequest(runtimeConfig)
        
        return try {
            Log.d(TAG, "Processing LLM request with official SDK")
            Log.d(TAG, "Using runtime model: ${runtimeConfig.modelId}, temp: ${runtimeConfig.temperature}, maxTokens: ${runtimeConfig.maxTokens}")
            Log.d(TAG, "Client endpoint: ${runtimeConfig.endpoint}")
            Log.d(TAG, "Request text length: ${text.length}")
            
            val systemPrompt = input.params[InferenceRequest.PARAM_SYSTEM_PROMPT] as? String
            val fullText = if (!systemPrompt.isNullOrBlank()) {
                "$systemPrompt\n\nUser: $text"
            } else {
                text
            }
            
            // Create messages using official SDK - following documentation pattern
            val userMessage = UserMessage.builder()
                .content(InterleavedContent.ofString(fullText))
                .build()
            val messages = listOf(Message.ofUser(userMessage))

            Log.d(TAG, "Built messages successfully, calling API...")
            
            // Use official SDK inference().chatCompletion() with runtime parameters
            val chatCompletionParams = InferenceChatCompletionParams.builder()
                .modelId(runtimeConfig.modelId)
                .messages(messages.toList())
                .build()
                
            Log.d(TAG, "Calling LlamaStack API with model: ${runtimeConfig.modelId}")
            val response = clientForRequest.inference().chatCompletion(chatCompletionParams)
            Log.d(TAG, "LlamaStack API call successful")

            val responseText = response.completionMessage().content().string() ?: ""

            InferenceResult.textOutput(
                text = responseText,
                metadata = mapOf(
                    InferenceResult.META_MODEL_NAME to runtimeConfig.modelId,
                    "inference_mode" to "remote",
                    "capability_type" to "LLM",
                    "endpoint_used" to runtimeConfig.endpoint,
                    "sdk_version" to "official-0.2.14",
                    "runtime_applied" to true,
                    "runtime_temperature" to runtimeConfig.temperature,
                    "runtime_max_tokens" to runtimeConfig.maxTokens
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "LLM processing failed with official SDK", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Using endpoint: ${runtimeConfig.endpoint}")
            Log.e(TAG, "Using model: ${runtimeConfig.modelId}")
            
            if (e.cause != null) {
                Log.e(TAG, "Root cause: ${e.cause?.javaClass?.simpleName}: ${e.cause?.message}")
            }
            
            InferenceResult.error(RunnerError.runtimeError("Text processing failed: ${e.message}"))
        }
    }

    private suspend fun processRAGRequest(input: InferenceRequest): InferenceResult {
        val runtimeConfig = resolveRuntimeParameters(input)
        val clientForRequest = getClientForRequest(runtimeConfig)

        if (!runtimeConfig.enableRAG) {
            return processStandardLLMRequest(input)
        }

        val query = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
        val ragSources = input.params["rag_sources"] as? List<String> ?: emptyList()

        return try {
            Log.d(TAG, "Processing RAG request with official SDK")
            Log.d(TAG, "Using runtime model: ${runtimeConfig.modelId}")
            
            val enhancedPrompt = buildRAGPrompt(query, ragSources)
            
            val userMessage = UserMessage.builder()
                .content(InterleavedContent.ofString(enhancedPrompt))
                .build()
            val messages = listOf(Message.ofUser(userMessage))

            val chatCompletionParams = InferenceChatCompletionParams.builder()
                .modelId(runtimeConfig.modelId)
                .messages(messages.toList())
                .build()
            val response = clientForRequest.inference().chatCompletion(chatCompletionParams)

            val responseText = response.completionMessage().content().string() ?: ""

            InferenceResult.textOutput(
                text = responseText,
                metadata = mapOf(
                    InferenceResult.META_MODEL_NAME to runtimeConfig.modelId,
                    "inference_mode" to "remote",
                    "capability_type" to "RAG",
                    "endpoint_used" to runtimeConfig.endpoint,
                    "rag_sources_used" to ragSources.size,
                    "sdk_version" to "official-0.2.14",
                    "runtime_applied" to true
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "RAG processing failed with official SDK", e)
            InferenceResult.error(RunnerError.runtimeError("RAG processing failed: ${e.message}"))
        }
    }

    private suspend fun processAgentRequest(input: InferenceRequest): InferenceResult {
        val runtimeConfig = resolveRuntimeParameters(input)
        val clientForRequest = getClientForRequest(runtimeConfig)

        if (!runtimeConfig.enableAgents) {
            return processStandardLLMRequest(input)
        }

        val query = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
        val availableTools = input.params["available_tools"] as? List<String> ?: emptyList()

        return try {
            Log.d(TAG, "Processing Agent request with official SDK")
            Log.d(TAG, "Using runtime model: ${runtimeConfig.modelId}")
            
            val agentPrompt = buildAgentPrompt(query, availableTools)
            
            val userMessage = UserMessage.builder()
                .content(InterleavedContent.ofString(agentPrompt))
                .build()
            val messages = listOf(Message.ofUser(userMessage))

            val chatCompletionParams = InferenceChatCompletionParams.builder()
                .modelId(runtimeConfig.modelId)
                .messages(messages.toList())
                .build()
            val response = clientForRequest.inference().chatCompletion(chatCompletionParams)

            val responseText = response.completionMessage().content().string() ?: ""

            InferenceResult.textOutput(
                text = responseText,
                metadata = mapOf(
                    InferenceResult.META_MODEL_NAME to runtimeConfig.modelId,
                    "inference_mode" to "remote",
                    "capability_type" to "AGENT",
                    "endpoint_used" to runtimeConfig.endpoint,
                    "tools_available" to availableTools.size,
                    "sdk_version" to "official-0.2.14",
                    "runtime_applied" to true
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Agent processing failed with official SDK", e)
            InferenceResult.error(RunnerError.runtimeError("Agent processing failed: ${e.message}"))
        }
    }

    private suspend fun processStandardLLMRequestStreaming(
        input: InferenceRequest,
        startTime: Long,
        emit: (InferenceResult) -> Unit
    ) {
        val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String

        if (text.isNullOrBlank()) {
            emit(InferenceResult.error(RunnerError.invalidInput("Text input is required")))
            return
        }

        val runtimeConfig = resolveRuntimeParameters(input)
        val clientForRequest = getClientForRequest(runtimeConfig)

        try {
            Log.d(TAG, "Starting streaming LLM request: model=${runtimeConfig.modelId}")

            val systemPrompt = input.params[InferenceRequest.PARAM_SYSTEM_PROMPT] as? String
            val fullText = if (!systemPrompt.isNullOrBlank()) {
                "$systemPrompt\n\nUser: $text"
            } else {
                text
            }

            // Create messages using same pattern as non-streaming
            val userMessage = UserMessage.builder()
                .content(InterleavedContent.ofString(fullText))
                .build()
            val messages = listOf(Message.ofUser(userMessage))

            val chatCompletionParams = InferenceChatCompletionParams.builder()
                .modelId(runtimeConfig.modelId)
                .messages(messages.toList())
                .build()

            // Use streaming API from official LlamaStack SDK
            val responseStream = clientForRequest.inference().chatCompletionStreaming(chatCompletionParams)
            var accumulatedText = ""

            // Use the official ChatCompletionResponseStreamChunk API
            responseStream.use { stream ->
                stream.asSequence().forEach { chunk ->
                    val event = chunk.event()
                    val eventType = event.eventType()
                    val stopReason = event.stopReason()

                    when (eventType) {
                        ChatCompletionResponseStreamChunk.Event.EventType.START -> {
                            // START event - just log, don't emit content
                        }
                        ChatCompletionResponseStreamChunk.Event.EventType.PROGRESS -> {
                            // PROGRESS event - extract and emit new content
                            val delta = event.delta()
                            val textContent = try {
                                // Try to extract text content from delta
                                when {
                                    delta.text() != null -> delta.text()!!.text()
                                    else -> ""
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to extract text from delta: ${e.message}")
                                ""
                            }

                            if (textContent.isNotEmpty()) {
                                accumulatedText += textContent

                                // Send partial result with ONLY the new delta content (like OpenRouter)
                                emit(InferenceResult.textOutput(
                                    text = textContent,
                                    metadata = mapOf(
                                        InferenceResult.META_MODEL_NAME to runtimeConfig.modelId,
                                        InferenceResult.META_SESSION_ID to input.sessionId,
                                        "inference_mode" to "remote_streaming",
                                        "capability_type" to "LLM",
                                        "endpoint_used" to runtimeConfig.endpoint,
                                        "sdk_version" to "official-0.2.14"
                                    ),
                                    partial = true
                                ))
                            }
                        }
                        ChatCompletionResponseStreamChunk.Event.EventType.COMPLETE -> {
                            // COMPLETE event - send final SUCCESS SIGNAL (no content to avoid duplication)
                            // Clients accumulate deltas themselves, so we only signal completion
                            Log.d(TAG, "Streaming complete: ${accumulatedText.length} characters total")

                            emit(InferenceResult.success(
                                outputs = emptyMap(), // No content - clients already accumulated deltas
                                metadata = mapOf(
                                    InferenceResult.META_MODEL_NAME to runtimeConfig.modelId,
                                    InferenceResult.META_SESSION_ID to input.sessionId,
                                    "inference_mode" to "remote_streaming",
                                    "capability_type" to "LLM",
                                    "endpoint_used" to runtimeConfig.endpoint,
                                    "sdk_version" to "official-0.2.14",
                                    InferenceResult.META_FINISH_REASON to (stopReason?.toString() ?: "stop"),
                                    InferenceResult.META_PROCESSING_TIME_MS to (System.currentTimeMillis() - startTime),
                                    "total_characters" to accumulatedText.length
                                ),
                                partial = false
                            ))
                            return@forEach // Exit after completion
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Streaming LLM processing failed", e)
            Log.w(TAG, "Falling back to non-streaming for this request")

            // Fallback to non-streaming if streaming fails
            val result = processStandardLLMRequest(input)
            emit(enhanceResultWithMetrics(result, startTime, "LLM", input))
        }
    }


    private fun hasVisionInput(input: InferenceRequest): Boolean {
        return input.inputs.containsKey(InferenceRequest.INPUT_IMAGE) && 
               input.inputs[InferenceRequest.INPUT_IMAGE] is ByteArray
    }

    private fun hasRAGRequest(input: InferenceRequest): Boolean {
        val runtimeConfig = resolveRuntimeParameters(input)
        return runtimeConfig.enableRAG
    }

    private fun hasAgentRequest(input: InferenceRequest): Boolean {
        val runtimeConfig = resolveRuntimeParameters(input)
        return runtimeConfig.enableAgents
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

    /**
     * Gets the shared client if the endpoint is the same, otherwise creates a new temporary one.
     */
    private fun getClientForRequest(runtimeConfig: RuntimeConfig): LlamaStackClientClient {
        return if (runtimeConfig.endpoint == config?.endpoint && runtimeConfig.apiKey == config?.apiKey) {
            llamaStackClient!!
        } else {
            Log.d(TAG, "Runtime endpoint or API key differs from loaded config. Creating temporary client.")
            val tempConfig = LlamaStackConfig(
                endpoint = runtimeConfig.endpoint,
                apiKey = runtimeConfig.apiKey,
                modelId = runtimeConfig.modelId,
                temperature = runtimeConfig.temperature,
                maxTokens = runtimeConfig.maxTokens,
                enableVision = runtimeConfig.enableVision,
                enableRAG = runtimeConfig.enableRAG,
                enableAgents = runtimeConfig.enableAgents
            )
            createOfficialClient(tempConfig)
        }
    }

    /**
     * Resolve runtime parameters with fallback to configuration
     * This ensures runtime parameter changes take precedence over load-time config
     */
    private fun resolveRuntimeParameters(input: InferenceRequest): RuntimeConfig {
        val runtimeParams = input.params
        
        return RuntimeConfig(
            endpoint = runtimeParams["endpoint"] as? String ?: config!!.endpoint,
            apiKey = runtimeParams["api_key"] as? String ?: config!!.apiKey,
            modelId = runtimeParams["model_id"] as? String ?: config!!.modelId,
            temperature = (runtimeParams["temperature"] as? Number)?.toFloat() ?: config!!.temperature,
            maxTokens = (runtimeParams["max_tokens"] as? Number)?.toInt() ?: config!!.maxTokens,
            enableVision = runtimeParams["enable_vision"] as? Boolean ?: config!!.enableVision,
            enableRAG = runtimeParams["enable_rag"] as? Boolean ?: config!!.enableRAG,
            enableAgents = runtimeParams["enable_agents"] as? Boolean ?: config!!.enableAgents
        )
    }
    
    /**
     * Runtime configuration that merges load-time config with runtime parameters
     */
    private data class RuntimeConfig(
        val endpoint: String,
        val apiKey: String?,
        val modelId: String,
        val temperature: Float,
        val maxTokens: Int,
        val enableVision: Boolean,
        val enableRAG: Boolean,
        val enableAgents: Boolean
    )
    
    private fun enhanceResultWithMetrics(
        result: InferenceResult,
        startTime: Long,
        requestType: String,
        input: InferenceRequest
    ): InferenceResult {
        val processingTime = System.currentTimeMillis() - startTime
        val runtimeConfig = resolveRuntimeParameters(input)
        
        val enhancedMetadata = result.metadata.toMutableMap().apply {
            put(InferenceResult.META_PROCESSING_TIME_MS, processingTime)
            put("request_type", requestType)
            put("endpoint_used", runtimeConfig.endpoint)
            put("sdk_version", "official-0.2.14")
            put("runtime_model_id", runtimeConfig.modelId)
            put("runtime_temperature", runtimeConfig.temperature)
            put("runtime_max_tokens", runtimeConfig.maxTokens)
        }
        
        return result.copy(metadata = enhancedMetadata)
    }

    override fun unload() {
        Log.d(TAG, "Unloading LlamaStack runner")
        llamaStackClient = null
        config = null
        isLoaded.set(false)
        loadedModelId = ""
    }

    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.LLM, CapabilityType.VLM)

    override fun isLoaded(): Boolean = isLoaded.get()

    override fun getLoadedModelId(): String = loadedModelId

    override fun getRunnerInfo(): com.mtkresearch.breezeapp.engine.runner.core.RunnerInfo {
        return com.mtkresearch.breezeapp.engine.runner.core.RunnerInfo(
            name = "LlamaStackRunner",
            version = "1.0.0-official",
            capabilities = getCapabilities(),
            description = "Official LlamaStack SDK runner for VLM, RAG, and Agent capabilities"
        )
    }

    override fun isSupported(): Boolean = true

    override fun getParameterSchema(): List<ParameterSchema> {
        return listOf(
            ParameterSchema(
                name = "endpoint",
                displayName = "API Endpoint",
                description = "LlamaStack API endpoint URL",
                type = ParameterType.StringType(
                    minLength = 10,
                    pattern = Regex("^https?://.*")
                ),
                defaultValue = "http://127.0.0.1:8321",
                isRequired = true,
                category = "Connection"
            ),
            ParameterSchema(
                name = "api_key",
                displayName = "API Key",
                description = "Authentication key for LlamaStack API (optional for localhost)",
                type = ParameterType.StringType(minLength = 0),
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
                        SelectionOption("llama3.2:3b", "Llama 3.2 3B", "Lightweight text model"),
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