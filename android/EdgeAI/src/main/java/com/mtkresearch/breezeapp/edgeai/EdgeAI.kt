package com.mtkresearch.breezeapp.edgeai

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * EdgeAI SDK - Android client library for BreezeApp Engine.
 *
 * Provides a type-safe Kotlin API for Android apps to access AI capabilities
 * (LLM chat, TTS, ASR) via AIDL IPC communication with BreezeApp Engine.
 *
 * ## Quick Start
 *
 * ```kotlin
 * // 1. Initialize SDK
 * EdgeAI.initializeAndWait(context)
 *
 * // 2. Make API calls
 * val request = chatRequest(prompt = "Hello, AI!")
 * EdgeAI.chat(request).collect { response ->
 *     println(response.choices.firstOrNull()?.message?.content)
 * }
 *
 * // 3. Cleanup when done
 * EdgeAI.shutdown()
 * ```
 *
 * ## Architecture
 *
 * ```
 * Your App → EdgeAI SDK → AIDL IPC → BreezeApp Engine → AI Models
 * ```
 *
 * ## Key Features
 *
 * - **Type-safe API**: Kotlin data classes instead of raw AIDL
 * - **Streaming support**: Real-time responses via Kotlin Flow
 * - **Automatic connection**: Manages service binding/unbinding
 * - **Error handling**: Clear exception hierarchy
 * - **Offline-first**: Works without network (on-device models)
 *
 * ## Prerequisites
 *
 * - BreezeApp Engine must be installed on the device
 * - Minimum Android SDK: 34
 *
 * @see chat
 * @see tts
 * @see asr
 * @see initialize
 * @see initializeAndWait
 */
@SuppressLint("StaticFieldLeak")
object EdgeAI {

    private const val TAG = "EdgeAI"
    private const val AI_ROUTER_SERVICE_ACTION = "com.mtkresearch.breezeapp.engine.BreezeAppEngineService"
    private const val AI_ROUTER_SERVICE_PACKAGE = "com.mtkresearch.breezeapp.engine"

    private var isInitialized = false
    private var service: IBreezeAppEngineService? = null
    private var isBound = false
    private var context: Context? = null

    // Track pending requests by their standard API-generated IDs
    private val pendingRequests = ConcurrentHashMap<String, Channel<AIResponse>>()

    // Lock to synchronize access to the listener logic
    private val listenerLock = Any()

    // Pending initialization completion
    private var initializationCompletion: ((Result<Unit>) -> Unit)? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "Service connected: $name")
            service = IBreezeAppEngineService.Stub.asInterface(binder)
            isBound = true

            // Register our listener
            service?.registerListener(breezeAppEngineListener)

            Log.i(TAG, "EdgeAI SDK connected to BreezeApp Engine Service")

            // Complete initialization
            initializationCompletion?.invoke(Result.success(Unit))
            initializationCompletion = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected: $name")

            // Safe cleanup: Don't call methods on potentially dead binder
            try {
                service?.unregisterListener(breezeAppEngineListener)
            } catch (e: android.os.DeadObjectException) {
                Log.d(TAG, "Service binder already dead, skipping unregister")
            } catch (e: Exception) {
                Log.w(TAG, "Error during listener unregistration: ${e.message}")
            }

            service = null
            isBound = false

            // Cancel all pending requests
            pendingRequests.values.forEach { channel ->
                channel.close(ServiceConnectionException("Service disconnected"))
            }
            pendingRequests.clear()

            // If initialization was pending, complete it with error
            initializationCompletion?.invoke(Result.failure(ServiceConnectionException("Service disconnected during initialization")))
            initializationCompletion = null

            Log.w(TAG, "EdgeAI SDK disconnected from BreezeApp Engine Service")
        }
    }

    private val breezeAppEngineListener = object : IBreezeAppEngineListener.Stub() {
        override fun onResponse(response: AIResponse?) {
            response?.let { aiResponse ->
                // Synchronize to prevent race conditions from concurrent binder thread calls
                synchronized(listenerLock) {
                    Log.d(TAG, "Received response for request: ${aiResponse.requestId}")

                    val channel = pendingRequests[aiResponse.requestId]
                    if (channel != null) {
                        // It's possible the channel was closed by a previous final response,
                        // so we check before sending.
                        if (!channel.isClosedForSend) {
                            if (aiResponse.error != null) {
                                channel.close(InternalErrorException(aiResponse.error))
                            } else {
                                val result = channel.trySend(aiResponse)
                                if (result.isFailure) {
                                    // This might happen if the channel is closed between the check and the send,
                                    // which is unlikely with the lock but good to log.
                                    Log.e(TAG, "Failed to send response to channel for ${aiResponse.requestId}: ${result.exceptionOrNull()}", result.exceptionOrNull())
                                }
                            }
                        }

                        // If this is the final response, close the channel and remove it from the map.
                        // The consumer will drain any buffered items before the flow completes.
                        if (aiResponse.isComplete || aiResponse.state == AIResponse.ResponseState.ERROR) {
                            channel.close()
                            pendingRequests.remove(aiResponse.requestId)
                            Log.d(TAG, "Closed and removed channel for completed request: ${aiResponse.requestId}")
                        } else {
                            // This else is required for the if to not be considered an expression.
                        }
                    } else {
                        Log.w(TAG, "Received response for unknown or already completed request: ${aiResponse.requestId}")
                    }
                }
            }
        }
    }

    /**
     * Initializes the EdgeAI SDK by binding to BreezeApp Engine Service.
     *
     * This is a suspend function that returns a [Result] indicating success or failure.
     * For simpler usage, consider using [initializeAndWait] which throws exceptions.
     *
     * ## Behavior
     *
     * - First call: Binds to BreezeApp Engine Service
     * - Subsequent calls: Returns immediately with success
     * - Cancellable: Can be cancelled via coroutine cancellation
     *
     * ## Example Usage
     *
     * ```kotlin
     * lifecycleScope.launch {
     *     val result = EdgeAI.initialize(context)
     *     result.onSuccess {
     *         println("SDK initialized successfully")
     *     }.onFailure { error ->
     *         println("Initialization failed: ${error.message}")
     *     }
     * }
     * ```
     *
     * @param context Android application context. Will be converted to application context internally.
     * @return [Result]<Unit> indicating success or failure
     *
     * @see initializeAndWait
     * @see shutdown
     */
    suspend fun initialize(context: Context): Result<Unit> = suspendCancellableCoroutine { continuation ->
        if (isInitialized) {
            continuation.resume(Result.success(Unit))
            return@suspendCancellableCoroutine
        }

        this.context = context.applicationContext

        // Set up completion callback
        initializationCompletion = { result ->
            isInitialized = result.isSuccess
            continuation.resume(result)
        }

        // Bind to service
        val intent = Intent(AI_ROUTER_SERVICE_ACTION).apply {
            setPackage(AI_ROUTER_SERVICE_PACKAGE)
        }

        val bound = try {
            context.applicationContext.bindService(
                intent,
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind to service", e)
            false
        }

        if (!bound) {
            val error = ServiceConnectionException("Failed to bind to BreezeApp Engine Service")
            initializationCompletion?.invoke(Result.failure(error))
            initializationCompletion = null
        }

        // Handle cancellation
        continuation.invokeOnCancellation {
            initializationCompletion = null
            if (isBound) {
                try {
                    context.applicationContext.unbindService(serviceConnection)
                } catch (e: Exception) {
                    Log.w(TAG, "Error unbinding service during cancellation: ${e.message}")
                }
            }
        }
    }

    /**
     * Get information about the currently selected runner for a capability
     * 
     * This allows clients to query runner capabilities (e.g., streaming support)
     * to adapt their behavior accordingly.
     * 
     * @param capability The capability type ("ASR", "TTS", "LLM", etc.)
     * @return RunnerInfo containing runner details, or null if not available
     * @throws ServiceConnectionException if service is not connected
     */
    suspend fun getSelectedRunnerInfo(capability: String): RunnerInfo? {
        validateConnection()
        
        return try {
            val runnerInfoParcel = service?.getSelectedRunnerInfo(capability)
            runnerInfoParcel?.let {
                RunnerInfo(
                    name = it.name,
                    supportsStreaming = it.supportsStreaming,
                    capabilities = it.capabilities.toList(),
                    vendor = it.vendor
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get runner info for capability: $capability", e)
            null
        }
    }

    /**
     * Initializes the EdgeAI SDK and waits for connection (simplified API).
     *
     * This is a convenience wrapper around [initialize] that throws exceptions
     * instead of returning [Result]. Recommended for most use cases.
     *
     * ## Example Usage
     *
     * ```kotlin
     * lifecycleScope.launch {
     *     try {
     *         EdgeAI.initializeAndWait(context, timeoutMs = 10000)
     *         println("SDK ready")
     *     } catch (e: ServiceConnectionException) {
     *         println("BreezeApp Engine not available: ${e.message}")
     *     }
     * }
     * ```
     *
     * @param context Android application context
     * @param timeoutMs Timeout in milliseconds (currently unused, kept for API compatibility)
     * @throws ServiceConnectionException if BreezeApp Engine is not available or initialization fails
     *
     * @see initialize
     * @see shutdown
     */
    suspend fun initializeAndWait(context: Context, timeoutMs: Long = 10000) {
        val result = initialize(context)
        result.getOrElse { error ->
            throw error as? ServiceConnectionException 
                ?: ServiceConnectionException("Initialization failed: ${error.message}")
        }
    }

    /**
     * Sends a chat completion request to the AI model.
     *
     * Supports both streaming and non-streaming responses. For streaming responses,
     * set `stream = true` in [ChatRequest] to receive token-by-token updates via Flow.
     *
     * ## Streaming vs Non-Streaming
     *
     * **Streaming** (`stream = true`):
     * - Emits multiple [ChatResponse] objects with incremental content in `delta`
     * - Use `finishReason == null` to detect ongoing stream
     * - Final response has `finishReason` set ("stop", "length", etc.)
     *
     * **Non-Streaming** (`stream = false`):
     * - Emits single [ChatResponse] with complete content in `message`
     * - Faster for short responses, better for long responses with streaming
     *
     * ## Example Usage
     *
     * ```kotlin
     * // Simple chat
     * val request = chatRequest(prompt = "Hello, AI!")
     * EdgeAI.chat(request).collect { response ->
     *     println(response.choices.firstOrNull()?.message?.content)
     * }
     *
     * // Streaming chat
     * val streamRequest = chatRequest(prompt = "Tell me a story", stream = true)
     * EdgeAI.chat(streamRequest).collect { response ->
     *     val choice = response.choices.firstOrNull()
     *     if (choice?.finishReason == null) {
     *         // Still streaming
     *         choice?.delta?.content?.let { print(it) }
     *     } else {
     *         // Stream complete
     *         println("\nFinished: ${choice.finishReason}")
     *     }
     * }
     * ```
     *
     * @param request The chat request containing messages and generation parameters.
     *                Use [chatRequest] builder for simple cases or construct [ChatRequest]
     *                directly for advanced configuration.
     * @return Flow emitting [ChatResponse] objects. For streaming requests, emits multiple
     *         responses with incremental content. For non-streaming, emits single final response.
     * @throws ServiceConnectionException if BreezeApp Engine is not connected
     * @throws InvalidRequestException if request parameters are invalid
     * @throws TimeoutException if request exceeds timeout
     * @throws InternalErrorException if an unexpected error occurs
     *
     * @see ChatRequest
     * @see ChatResponse
     * @see chatRequest
     * @see chatRequestWithHistory
     */
    fun chat(request: ChatRequest): Flow<ChatResponse> {
        return channelFlow {
            validateConnection()

            val requestId = generateRequestId()
            lastRequestId = requestId  // Track for cancellation

            // Use a buffered channel to handle backpressure
            val responseChannel = Channel<AIResponse>(UNLIMITED)
            pendingRequests[requestId] = responseChannel

            try {
                // 🚀 NEW SIMPLIFIED API: Direct service call with client-generated requestId
                service?.sendChatRequest(requestId, request)

                // Process responses
                for (aiResponse in responseChannel) {
                    val chatResponse = convertAIResponseToChatResponse(aiResponse, request.stream ?: false, request.model)
                    send(chatResponse)
                }

            } catch (e: Exception) {
                throw when (e) {
                    is EdgeAIException -> e
                    else -> InternalErrorException("Chat completion failed: ${e.message}", e)
                }
            } finally {
                // Clean up local state only - don't cancel the request on service side
                pendingRequests.remove(requestId)
                responseChannel.close()
                // NOTE: Not calling service?.cancelRequest() - let service complete the request
            }
        }
    }

    /**
     * Converts text to speech audio.
     *
     * Generates audio from input text using the specified voice and model.
     * Supports multiple audio formats and playback speeds.
     *
     * ## Supported Voices
     *
     * - `alloy` - Neutral, balanced voice
     * - `echo` - Clear, articulate voice
     * - `fable` - Warm, expressive voice
     * - `onyx` - Deep, authoritative voice
     * - `nova` - Energetic, friendly voice
     * - `shimmer` - Soft, gentle voice
     *
     * ## Audio Formats
     *
     * - `mp3` - Compressed, good for streaming
     * - `wav` - Uncompressed, high quality
     * - `pcm` - Raw audio data
     * - `opus`, `aac`, `flac` - Various compressed formats
     *
     * ## Example Usage
     *
     * ```kotlin
     * val request = ttsRequest(
     *     input = "Hello, this is a test message",
     *     voice = "alloy",
     *     speed = 1.0f
     * )
     *
     * EdgeAI.tts(request).collect { response ->
     *     playAudio(response.audioData)
     * }
     * ```
     *
     * @param request The TTS request containing text and voice parameters.
     *                Use [ttsRequest] builder for simple cases.
     * @return Flow emitting [TTSResponse] objects containing audio data.
     *         May emit multiple chunks for streaming audio.
     * @throws ServiceConnectionException if BreezeApp Engine is not connected
     * @throws InvalidRequestException if request parameters are invalid (e.g., text too long)
     * @throws InternalErrorException if an unexpected error occurs
     *
     * @see TTSRequest
     * @see TTSResponse
     * @see ttsRequest
     */
    fun tts(request: TTSRequest): Flow<TTSResponse> {
        return channelFlow {
            validateConnection()

            val requestId = generateRequestId()
            lastRequestId = requestId  // Track for cancellation

            // Use a buffered channel to handle backpressure
            val responseChannel = Channel<AIResponse>(UNLIMITED)
            pendingRequests[requestId] = responseChannel

            try {
                // 🚀 Direct service call with client-generated requestId
                service?.sendTTSRequest(requestId, request)

                // Process responses
                for (aiResponse in responseChannel) {
                    val ttsResponse = convertAIResponseToTTSResponse(aiResponse)
                    send(ttsResponse)
                }

            } catch (e: Exception) {
                throw when (e) {
                    is EdgeAIException -> e
                    else -> InternalErrorException("TTS request failed: ${e.message}", e)
                }
            } finally {
                // Clean up local state only - don't cancel the request on service side
                pendingRequests.remove(requestId)
                responseChannel.close()
                // NOTE: Not calling service?.cancelRequest() - let service complete the request
            }
        }
    }

    /**
     * Transcribes audio to text (Automatic Speech Recognition).
     *
     * Converts audio data to text using the specified model and language.
     * Supports multiple audio formats and response formats including timestamps.
     *
     * ## Language Detection
     *
     * - Set `language` to specific code (e.g., "en", "zh") for better accuracy
     * - Set `language = null` for automatic language detection
     *
     * ## Response Formats
     *
     * - `json` - Simple JSON with transcribed text
     * - `text` - Plain text only
     * - `verbose_json` - Detailed JSON with segments and timestamps
     * - `srt` - SubRip subtitle format
     * - `vtt` - WebVTT subtitle format
     *
     * ## Example Usage
     *
     * ```kotlin
     * // Basic transcription
     * val request = asrRequest(
     *     audioBytes = audioData,
     *     model = "whisper-1",
     *     language = "en"
     * )
     *
     * EdgeAI.asr(request).collect { response ->
     *     println("Transcription: ${response.text}")
     * }
     *
     * // With word-level timestamps
     * val detailedRequest = asrRequestDetailed(
     *     audioBytes = audioData,
     *     includeWordTimestamps = true
     * )
     *
     * EdgeAI.asr(detailedRequest).collect { response ->
     *     response.segments?.forEach { segment ->
     *         println("${segment.start}-${segment.end}: ${segment.text}")
     *     }
     * }
     * ```
     *
     * @param request The ASR request containing audio data and transcription parameters.
     *                Use [asrRequest] or [asrRequestDetailed] builders.
     * @return Flow emitting [ASRResponse] objects containing transcribed text.
     *         May emit multiple chunks for streaming transcription.
     * @throws ServiceConnectionException if BreezeApp Engine is not connected
     * @throws InvalidRequestException if request parameters are invalid
     * @throws InternalErrorException if an unexpected error occurs
     *
     * @see ASRRequest
     * @see ASRResponse
     * @see asrRequest
     * @see asrRequestDetailed
     */
    fun asr(request: ASRRequest): Flow<ASRResponse> {
        return channelFlow {
            validateConnection()

            val requestId = generateRequestId()
            lastRequestId = requestId  // Track for cancellation

            // Use a buffered channel to handle backpressure
            val responseChannel = Channel<AIResponse>(UNLIMITED)
            pendingRequests[requestId] = responseChannel

            try {
                // 🚀 Direct service call with client-generated requestId
                service?.sendASRRequest(requestId, request)

                // Process responses
                for (aiResponse in responseChannel) {
                    val asrResponse = convertAIResponseToASRResponse(aiResponse)
                    send(asrResponse)
                }

            } catch (e: Exception) {
                throw when (e) {
                    is EdgeAIException -> e
                    else -> InternalErrorException("ASR request failed: ${e.message}", e)
                }
            } finally {
                // Clean up local state only - don't cancel the request on service side
                // The service will complete the request and send results even if client stopped collecting
                pendingRequests.remove(requestId)
                responseChannel.close()
                
                // NOTE: We intentionally DO NOT call service?.cancelRequest() here
                // Reason: If the client's collecting coroutine is cancelled (e.g., due to UI lifecycle),
                // we still want the service to complete the request and deliver results.
                // This allows late-arriving results to be logged/stored even if the UI is gone.
            }
        }
    }

    /**
     * SIMPLIFIED: Direct guardrail/content safety request (no intermediate conversion)
     */
    fun guardrail(request: GuardrailRequest): Flow<GuardrailResponse> {
        return channelFlow {
            validateConnection()

            val requestId = generateRequestId()
            lastRequestId = requestId  // Track for cancellation

            // Use a buffered channel to handle backpressure
            val responseChannel = Channel<AIResponse>(UNLIMITED)
            pendingRequests[requestId] = responseChannel

            try {
                // 🚀 Direct service call with client-generated requestId
                service?.sendGuardrailRequest(requestId, request)

                // Process responses
                for (aiResponse in responseChannel) {
                    val guardrailResponse = convertAIResponseToGuardrailResponse(aiResponse)
                    send(guardrailResponse)
                }

            } catch (e: Exception) {
                throw when (e) {
                    is EdgeAIException -> e
                    else -> InternalErrorException("Guardrail request failed: ${e.message}", e)
                }
            } finally {
                // Clean up local state only - don't cancel the request on service side
                pendingRequests.remove(requestId)
                responseChannel.close()
                // NOTE: Not calling service?.cancelRequest() - let service complete the request
            }
        }
    }

    /**
     * Cancel an active request by its ID.
     * This is the simplest and most robust way to stop streaming requests.
     *
     * @param requestId The ID of the request to cancel
     * @return true if the request was successfully cancelled, false otherwise
     */
    fun cancelRequest(requestId: String): Boolean {
        return try {
            validateConnection()

            // 1. Cancel the pending request channel
            pendingRequests[requestId]?.let { channel ->
                channel.close()
                pendingRequests.remove(requestId)
                Log.d(TAG, "Cancelled pending request channel: $requestId")
            }

            // 2. Call service to cancel the request on engine side
            val cancelled = service?.cancelRequest(requestId) ?: false

            if (cancelled) {
                Log.d(TAG, "Successfully cancelled request: $requestId")
            } else {
                Log.w(TAG, "Failed to cancel request on service side: $requestId")
            }

            cancelled
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling request: $requestId", e)
            false
        }
    }

    /**
     * Get the current request ID for the last initiated request.
     * Useful for cancellation when the client doesn't track request IDs.
     */
    private var lastRequestId: String? = null

    /**
     * Cancel the last initiated request.
     * Convenience method when client doesn't track request IDs.
     */
    fun cancelLastRequest(): Boolean {
        return lastRequestId?.let { cancelRequest(it) } ?: false
    }

    // === HELPER METHODS ===

    /** @suppress */
    private fun validateConnection() {
        if (!isInitialized) {
            throw ServiceConnectionException("EdgeAI SDK is not initialized. Call EdgeAI.initializeAndWait(context) first.")
        }

        if (!isBound || service == null) {
            throw ServiceConnectionException("EdgeAI SDK is not connected to the BreezeApp Engine Service.")
        }
    }

    /** @suppress */
    private fun generateRequestId(): String = UUID.randomUUID().toString()

    /**
     * Minimal conversion - only from internal AIResponse to standard format
     * (Previously had 3 conversion steps, now only 1)
     * @suppress
     */
    private fun convertAIResponseToChatResponse(
        aiResponse: AIResponse,
        isStreaming: Boolean,
        modelName: String? = null
    ): ChatResponse {
        val choice = if (isStreaming) {
            Choice(
                index = 0,
                delta = ChatMessage(role = "assistant", content = aiResponse.text),
                finishReason = if (aiResponse.isComplete) "stop" else null
            )
        } else {
            Choice(
                index = 0,
                message = ChatMessage(role = "assistant", content = aiResponse.text),
                finishReason = "stop"
            )
        }

        return ChatResponse(
            id = aiResponse.requestId,
            `object` = if (isStreaming) "chat.completion.chunk" else "chat.completion",
            created = System.currentTimeMillis() / 1000,
            model = modelName ?: "default-llm",
            choices = listOf(choice),
            metrics = aiResponse.metrics
        )
    }

    /**
     * Convert internal AIResponse to ASRResponse
     */
    /** @suppress */
    private fun convertAIResponseToASRResponse(
        aiResponse: AIResponse
    ): ASRResponse {
        return ASRResponse(
            text = aiResponse.text,
            isChunk = !aiResponse.isComplete,
            metrics = aiResponse.metrics
        )
    }

    /**
     * Convert internal AIResponse to TTSResponse
     * Simplified for engine-side audio playback
     */
    /** @suppress */
    private fun convertAIResponseToTTSResponse(
        aiResponse: AIResponse
    ): TTSResponse {
        return TTSResponse(
            audioData = byteArrayOf(), // Engine 端直接播放，client 不需要 audio 數據
            format = "engine_playback", // 標示為 engine 端播放
            sampleRate = aiResponse.sampleRate,
            channels = aiResponse.channels,
            bitDepth = aiResponse.bitDepth,
            chunkIndex = aiResponse.chunkIndex,
            isLastChunk = aiResponse.isLastChunk,
            durationMs = aiResponse.durationMs.toLong(),
            metrics = aiResponse.metrics
        )
    }

    /**
     * Convert internal AIResponse to GuardrailResponse
     * 
     * Parses the structured guardrail results from the AIResponse text field.
     */
    /** @suppress */
    private fun convertAIResponseToGuardrailResponse(
        aiResponse: AIResponse
    ): GuardrailResponse {
        // Parse the structured result from the text field
        val text = aiResponse.text
        
        if (text.startsWith("GUARDRAIL_RESULT{") && text.endsWith("}")) {
            try {
                // Extract the JSON-like content
                val jsonContent = text.substring(17, text.length - 1) // Remove "GUARDRAIL_RESULT{" and "}"
                
                // Parse the key-value pairs (simple parsing for this format)
                val safetyStatus = extractValue(jsonContent, "safetyStatus") ?: "unknown"
                val riskScore = extractValue(jsonContent, "riskScore")?.toDoubleOrNull() ?: 0.0
                val actionRequired = extractValue(jsonContent, "actionRequired") ?: "none"
                val filteredText = extractValue(jsonContent, "filteredText") ?: ""
                
                return GuardrailResponse(
                    safetyStatus = safetyStatus,
                    riskScore = riskScore,
                    riskCategories = if (safetyStatus != "safe") listOf("general") else emptyList(),
                    actionRequired = actionRequired,
                    filteredText = filteredText,
                    detectedIssues = if (safetyStatus != "safe") listOf("content_risk") else emptyList(),
                    confidence = 0.95,
                    processingTimeMs = aiResponse.durationMs.toLong()
                )
            } catch (e: Exception) {
                // Fallback if parsing fails
            }
        }
        
        // Fallback response
        return GuardrailResponse(
            safetyStatus = if (aiResponse.error != null) "error" else "safe",
            riskScore = 0.0,
            riskCategories = emptyList(),
            actionRequired = "none",
            filteredText = aiResponse.text,
            detectedIssues = if (aiResponse.error != null) listOf("processing_error") else emptyList(),
            confidence = 0.95,
            processingTimeMs = aiResponse.durationMs.toLong()
        )
    }
    
    /**
     * Extract value from simple JSON-like format
     */
    /** @suppress */
    private fun extractValue(content: String, key: String): String? {
        val pattern = "\"$key\":\"([^\"]*)\"|\"$key\":([^,}]*)"
        val regex = Regex(pattern)
        val match = regex.find(content)
        return match?.groups?.get(1)?.value ?: match?.groups?.get(2)?.value?.trim()
    }

    /**
     * Shuts down the EdgeAI SDK and releases all resources.
     *
     * This method:
     * - Unregisters listeners from BreezeApp Engine
     * - Unbinds from the service
     * - Cancels all pending requests
     * - Clears internal state
     *
     * ## When to Call
     *
     * - In `Application.onTerminate()` for app-wide cleanup
     * - In `ViewModel.onCleared()` for scoped cleanup
     * - When switching between different AI services
     *
     * ## Example Usage
     *
     * ```kotlin
     * class MyApplication : Application() {
     *     override fun onTerminate() {
     *         super.onTerminate()
     *         EdgeAI.shutdown()
     *     }
     * }
     * ```
     *
     * @see initialize
     * @see initializeAndWait
     */
    fun shutdown() {
        context?.let { ctx ->
            if (isBound) {
                service?.unregisterListener(breezeAppEngineListener)
                ctx.unbindService(serviceConnection)
            }
        }

        // Cancel all pending requests
        pendingRequests.values.forEach { channel ->
            channel.close()
        }
        pendingRequests.clear()

        service = null
        isBound = false
        isInitialized = false
        context = null
    }

    fun isInitialized(): Boolean = isInitialized
    fun isReady(): Boolean = isInitialized && isBound && service != null
}