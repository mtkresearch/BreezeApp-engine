package com.mtkresearch.breezeapp.engine.service

import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.mtkresearch.breezeapp.edgeai.IBreezeAppEngineService
import com.mtkresearch.breezeapp.edgeai.IBreezeAppEngineListener
import com.mtkresearch.breezeapp.edgeai.ChatRequest
import com.mtkresearch.breezeapp.edgeai.TTSRequest
import com.mtkresearch.breezeapp.edgeai.ASRRequest
import com.mtkresearch.breezeapp.edgeai.AIResponse
import com.mtkresearch.breezeapp.engine.core.AIEngineManager
import com.mtkresearch.breezeapp.engine.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch

/**
 * EngineServiceBinder - Clean AIDL Interface Implementation (Clean Architecture)
 * 
 * Following Clean Architecture and Single Responsibility Principle:
 * - AIDL interface implementation ONLY
 * - Direct integration with AIEngineManager (Domain Layer)
 * - Request/Response conversion (Adapter Pattern)
 * - Input validation and error handling
 * - Coroutine management for async operations
 * 
 * ## Clean Architecture Benefits
 * - Interface Adapter: Converts external AIDL to internal domain models
 * - Single Responsibility: Only AIDL interface concerns
 * - Dependency Rule: Depends on abstractions (AIEngineManager)
 * - Testable: Can be unit tested independently
 * - No Business Logic: Pure translation layer
 */
class EngineServiceBinder(
    private val clientManager: ClientManager,
    private val aiEngineManager: AIEngineManager
) {
    companion object {
        private const val TAG = "EngineServiceBinder"
        private const val API_VERSION = 1
    }
    
    // Coroutine scope for async operations
    private val binderJob = SupervisorJob()
    private val binderScope = CoroutineScope(Dispatchers.IO + binderJob)
    
    // AIDL Binder Implementation
    private val binder = object : IBreezeAppEngineService.Stub() {
        
        /**
         * Returns the current API version of the BreezeApp Engine Service.
         */
        override fun getApiVersion(): Int {
            Log.d(TAG, "getApiVersion() called")
            return API_VERSION
        }
        
        /**
         * Processes a chat completion request asynchronously.
         */
        override fun sendChatRequest(requestId: String?, request: ChatRequest?) {
            Log.d(TAG, "sendChatRequest() called: requestId=$requestId")
            
            // Input validation
            if (requestId == null || request == null) {
                Log.w(TAG, "sendChatRequest received null parameters")
                clientManager.notifyError(requestId ?: "unknown", "Invalid request parameters")
                return
            }
            
            // Process request directly through AIEngineManager (Clean Architecture)
            binderScope.launch {
                try {
                    processChatRequestInternal(requestId, request)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing chat request: $requestId", e)
                    clientManager.notifyError(requestId, "Chat request processing failed: ${e.message}")
                }
            }
        }
        
        /**
         * Converts text to speech and returns audio data.
         */
        override fun sendTTSRequest(requestId: String?, request: TTSRequest?) {
            Log.d(TAG, "sendTTSRequest() called: requestId=$requestId")
            
            // Input validation
            if (requestId == null || request == null) {
                Log.w(TAG, "sendTTSRequest received null parameters")
                clientManager.notifyError(requestId ?: "unknown", "Invalid request parameters")
                return
            }
            
            // Process request directly through AIEngineManager (Clean Architecture)
            binderScope.launch {
                try {
                    processTTSRequestInternal(requestId, request)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing TTS request: $requestId", e)
                    clientManager.notifyError(requestId, "TTS request processing failed: ${e.message}")
                }
            }
        }
        
        /**
         * Converts speech to text and returns transcription.
         */
        override fun sendASRRequest(requestId: String?, request: ASRRequest?) {
            Log.d(TAG, "sendASRRequest() called: requestId=$requestId")
            
            // Input validation
            if (requestId == null || request == null) {
                Log.w(TAG, "sendASRRequest received null parameters")
                clientManager.notifyError(requestId ?: "unknown", "Invalid request parameters")
                return
            }
            
            // Process request directly through AIEngineManager (Clean Architecture)
            binderScope.launch {
                try {
                    processASRRequestInternal(requestId, request)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing ASR request: $requestId", e)
                    clientManager.notifyError(requestId, "ASR request processing failed: ${e.message}")
                }
            }
        }
        
        /**
         * Cancels an active request by its ID.
         */
        override fun cancelRequest(requestId: String?): Boolean {
            Log.d(TAG, "cancelRequest() called: $requestId")
            
            if (requestId == null) {
                Log.w(TAG, "cancelRequest received null requestId")
                return false
            }
            
            return try {
                // Cancel request directly through AIEngineManager
                aiEngineManager.cancelRequest(requestId)
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling request: $requestId", e)
                false
            }
        }
        
        /**
         * Registers a listener to receive AI processing responses.
         */
        override fun registerListener(listener: IBreezeAppEngineListener?) {
            Log.d(TAG, "registerListener() called")
            
            if (listener != null) {
                try {
                    clientManager.registerListener(listener)
                    Log.d(TAG, "Client listener registered successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error registering listener", e)
                }
            } else {
                Log.w(TAG, "registerListener received null listener")
            }
        }
        
        /**
         * Unregisters a previously registered listener.
         */
        override fun unregisterListener(listener: IBreezeAppEngineListener?) {
            Log.d(TAG, "unregisterListener() called")
            
            if (listener != null) {
                try {
                    clientManager.unregisterListener(listener)
                    Log.d(TAG, "Client listener unregistered successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error unregistering listener", e)
                }
            } else {
                Log.w(TAG, "unregisterListener received null listener")
            }
        }
        
        /**
         * Checks if the service has a specific capability.
         */
        override fun hasCapability(capabilityName: String?): Boolean {
            Log.d(TAG, "hasCapability() called: $capabilityName")
            
            return when (capabilityName?.lowercase()) {
                "llm", "chat" -> true
                "tts" -> true  
                "asr" -> true
                "vlm" -> true
                else -> false
            }
        }
        
    }
    
    /**
     * Get the IBinder for service binding.
     */
    fun getBinder(): IBinder = binder
    
    /**
     * Cleanup resources when service is destroyed.
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up EngineServiceBinder")
        binderJob.cancel()
    }
    
    // ========================================================================
    // PRIVATE IMPLEMENTATION - Request Processing (Clean Architecture)
    // ========================================================================
    
    /**
     * Process chat request following Clean Architecture principles.
     * Converts external ChatRequest to internal InferenceRequest and delegates to domain layer.
     */
    private suspend fun processChatRequestInternal(requestId: String, request: ChatRequest) {
        Log.d(TAG, "Processing chat request internally: $requestId (streaming: ${request.stream})")
        
        // Convert external request to internal domain model (Adapter Pattern)
        val inferenceRequest = convertChatRequestToDomain(request, requestId)
        
        // Determine processing type based on stream parameter
        val isStreaming = request.stream ?: false
        
        if (isStreaming) {
            // Handle streaming chat request
            aiEngineManager.processStream(inferenceRequest, CapabilityType.LLM)
                .catch { error ->
                    Log.e(TAG, "Chat stream processing error", error)
                    clientManager.notifyError(requestId, "Chat streaming failed: ${error.message}")
                }
                .collect { result ->
                    // Convert internal result to external response (Adapter Pattern)
                    val aiResponse = convertInferenceResultToAIResponse(result, requestId, "chat")
                    clientManager.notifyChatResponse(aiResponse)
                }
        } else {
            // Handle non-streaming chat request
            val result = aiEngineManager.process(inferenceRequest, CapabilityType.LLM)
            val aiResponse = convertInferenceResultToAIResponse(result, requestId, "chat")
            clientManager.notifyChatResponse(aiResponse)
        }
    }
    
    /**
     * Process TTS request following Clean Architecture principles.
     */
    private suspend fun processTTSRequestInternal(requestId: String, request: TTSRequest) {
        Log.d(TAG, "Processing TTS request internally: $requestId")
        
        // Convert external request to internal domain model
        val inferenceRequest = convertTTSRequestToDomain(request, requestId)
        
        // TTS is typically streaming for audio output
        aiEngineManager.processStream(inferenceRequest, CapabilityType.TTS)
            .catch { error ->
                Log.e(TAG, "TTS processing error", error)
                clientManager.notifyError(requestId, "TTS processing failed: ${error.message}")
            }
            .collect { result ->
                val aiResponse = convertInferenceResultToAIResponse(result, requestId, "tts")
                clientManager.notifyTTSResponse(aiResponse)
            }
    }
    
    /**
     * Process ASR request following Clean Architecture principles.
     */
    private suspend fun processASRRequestInternal(requestId: String, request: ASRRequest) {
        Log.d(TAG, "Processing ASR request internally: $requestId")
        
        // Convert external request to internal domain model
        val inferenceRequest = convertASRRequestToDomain(request, requestId)
        
        // Determine if this is microphone mode (streaming) or file mode
        val isMicrophoneMode = detectMicrophoneMode(request)
        
        if (isMicrophoneMode) {
            // Handle streaming ASR (microphone input)
            aiEngineManager.processStream(inferenceRequest, CapabilityType.ASR)
                .catch { error ->
                    Log.e(TAG, "ASR stream processing error", error)
                    clientManager.notifyError(requestId, "ASR streaming failed: ${error.message}")
                }
                .collect { result ->
                    val aiResponse = convertInferenceResultToAIResponse(result, requestId, "asr")
                    clientManager.notifyASRResponse(aiResponse)
                }
        } else {
            // Handle non-streaming ASR (file input)
            val result = aiEngineManager.process(inferenceRequest, CapabilityType.ASR)
            val aiResponse = convertInferenceResultToAIResponse(result, requestId, "asr")
            clientManager.notifyASRResponse(aiResponse)
        }
    }
    
    // ========================================================================
    // REQUEST CONVERSION METHODS (Clean Architecture - Adapter Pattern)
    // ========================================================================
    
    /**
     * Convert external ChatRequest to internal InferenceRequest (Domain Model).
     * Following Clean Architecture principles: External -> Internal conversion.
     */
    private fun convertChatRequestToDomain(request: ChatRequest, requestId: String): InferenceRequest {
        // Extract the last message as the main input text
        val inputText = request.messages?.lastOrNull()?.content ?: ""
        
        // Build parameters map with proper defaults
        val params = mutableMapOf<String, Any>(
            InferenceRequest.PARAM_TEMPERATURE to (request.temperature ?: 1.0f),
            "stream" to (request.stream ?: false)
        )
        
        // Only add model parameter if provided - let engine decide default
        request.model?.let { params[InferenceRequest.PARAM_MODEL] = it }
        request.maxCompletionTokens?.let { params[InferenceRequest.PARAM_MAX_TOKENS] = it }
        request.topP?.let { params["top_p"] = it }
        
        // Include all messages for context (not just last one)
        val inputs = mutableMapOf<String, Any>(
            InferenceRequest.INPUT_TEXT to inputText
        )
        
        // Add full conversation history if multiple messages
        if (request.messages?.size ?: 0 > 1) {
            inputs["conversation_history"] = request.messages?.toList() ?: emptyList<Any>()
        }
        
        return InferenceRequest(
            sessionId = requestId,
            inputs = inputs,
            params = params
        )
    }
    
    /**
     * Convert external TTSRequest to internal InferenceRequest.
     */
    private fun convertTTSRequestToDomain(request: TTSRequest, requestId: String): InferenceRequest {
        return InferenceRequest(
            sessionId = requestId,
            inputs = mapOf(
                InferenceRequest.INPUT_TEXT to (request.input ?: "")
            ),
            params = mapOf(
                "voice" to (request.voice ?: "default"),
                "speed" to (request.speed ?: 1.0f),
                "response_format" to (request.responseFormat ?: "pcm")
            )
        )
    }
    
    /**
     * Convert external ASRRequest to internal InferenceRequest.
     */
    private fun convertASRRequestToDomain(request: ASRRequest, requestId: String): InferenceRequest {
        val isMicrophoneMode = detectMicrophoneMode(request)
        
        return if (isMicrophoneMode) {
            // Microphone mode: streaming ASR
            InferenceRequest(
                sessionId = requestId,
                inputs = mapOf(
                    InferenceRequest.INPUT_TEXT to "microphone_input"
                ),
                params = mapOf(
                    "language" to (request.language ?: "auto"),
                    "stream" to true,
                    "microphone_mode" to true,
                    "sample_rate" to 16000,
                    "format" to "pcm16"
                )
            )
        } else {
            // File mode: non-streaming ASR
            InferenceRequest(
                sessionId = requestId,
                inputs = mapOf(
                    InferenceRequest.INPUT_AUDIO to (request.file ?: ByteArray(0))
                ),
                params = mapOf(
                    "language" to (request.language ?: "auto"),
                    "stream" to false,
                    "input_format" to (request.responseFormat ?: "wav")
                )
            )
        }
    }
    
    /**
     * Detect if ASR request is for microphone mode (streaming) vs file mode.
     */
    private fun detectMicrophoneMode(request: ASRRequest): Boolean {
        // Microphone mode indicators:
        // 1. No audio file provided
        // 2. Explicitly marked as streaming
        // 3. Format indicates real-time input
        
        val hasNoFile = request.file == null || request.file?.isEmpty() == true
        val isStreamingEnabled = request.stream ?: false
        val isMicrophoneFormat = request.responseFormat?.lowercase()?.contains("microphone") ?: false
        
        return hasNoFile || isStreamingEnabled || isMicrophoneFormat
    }
    
    /**
     * Convert internal InferenceResult to external AIResponse.
     * Following Clean Architecture: Internal -> External conversion.
     */
    private fun convertInferenceResultToAIResponse(
        result: InferenceResult, 
        requestId: String, 
        requestType: String
    ): AIResponse {
        return when (requestType.lowercase()) {
            "chat" -> {
                val content = result.outputs["content"] as? String ?: result.outputs["text"] as? String ?: ""
                val isComplete = !result.partial
                val state = if (isComplete) AIResponse.ResponseState.COMPLETED else AIResponse.ResponseState.STREAMING
                val error = if (result.error != null) result.error!!.message else null
                
                AIResponse(
                    requestId = requestId,
                    text = content,
                    isComplete = isComplete,
                    state = state,
                    error = error
                )
            }
            "tts" -> {
                val audioData = result.outputs["audio_data"] as? ByteArray
                val isComplete = !result.partial
                val state = if (isComplete) AIResponse.ResponseState.COMPLETED else AIResponse.ResponseState.STREAMING
                val error = if (result.error != null) result.error!!.message else null
                val sampleRate = result.outputs["sample_rate"] as? Int ?: 16000
                
                AIResponse(
                    requestId = requestId,
                    text = "", // TTS doesn't return text
                    isComplete = isComplete,
                    state = state,
                    error = error,
                    audioData = audioData,
                    sampleRate = sampleRate
                )
            }
            "asr" -> {
                val text = result.outputs["text"] as? String ?: ""
                val isComplete = !result.partial
                val state = if (isComplete) AIResponse.ResponseState.COMPLETED else AIResponse.ResponseState.STREAMING
                val error = if (result.error != null) result.error!!.message else null
                
                AIResponse(
                    requestId = requestId,
                    text = text,
                    isComplete = isComplete,
                    state = state,
                    error = error
                )
            }
            else -> {
                // Generic response
                val text = result.outputs["text"] as? String ?: result.outputs["content"] as? String ?: ""
                val isComplete = !result.partial
                val state = if (isComplete) AIResponse.ResponseState.COMPLETED else AIResponse.ResponseState.STREAMING
                val error = if (result.error != null) result.error!!.message else null
                
                AIResponse(
                    requestId = requestId,
                    text = text,
                    isComplete = isComplete,
                    state = state,
                    error = error
                )
            }
        }
    }
}