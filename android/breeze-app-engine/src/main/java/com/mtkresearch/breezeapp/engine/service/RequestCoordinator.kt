package com.mtkresearch.breezeapp.engine.service

import android.util.Log
import com.mtkresearch.breezeapp.edgeai.ChatRequest
import com.mtkresearch.breezeapp.edgeai.TTSRequest
import com.mtkresearch.breezeapp.edgeai.ASRRequest
import com.mtkresearch.breezeapp.edgeai.AIResponse
import com.mtkresearch.breezeapp.engine.core.AIEngineManager
import com.mtkresearch.breezeapp.engine.error.RequestProcessingHelper
import com.mtkresearch.breezeapp.engine.domain.model.*
import com.mtkresearch.breezeapp.engine.domain.model.InferenceResult

/**
 * RequestCoordinator - Coordinates AI Request Processing
 * 
 * This class coordinates AI request processing, following Single Responsibility Principle.
 * It handles ONLY request coordination concerns:
 * - Converting external requests to internal format
 * - Delegating to existing RequestProcessingHelper
 * - Converting internal responses to external format
 * - Request cancellation coordination
 * - FIX: Proper routing between streaming and non-streaming requests
 * 
 * ## Architecture Benefits
 * - Single Responsibility: Only request coordination
 * - Reuses Existing: Leverages existing RequestProcessingHelper
 * - Clean: No Android Service or client management concerns
 * - Testable: Can be unit tested independently
 */
class RequestCoordinator(
    private val requestProcessingHelper: RequestProcessingHelper,
    private val engineManager: AIEngineManager,
    private val clientManager: ClientManager
) {
    companion object {
        private const val TAG = "RequestCoordinator"
    }
    
    /**
     * Process a chat request through the AI engine.
     * FIX: Now properly routes based on stream parameter
     */
    suspend fun processChatRequest(requestId: String, request: ChatRequest) {
        Log.d(TAG, "Processing chat request: $requestId (streaming: ${request.stream})")
        
        try {
            // Convert external request to internal format
            val inferenceRequest = convertChatRequest(request, requestId)
            
            // FIX: Route based on stream parameter
            if (request.stream == true) {
                // Process as streaming request
                Log.d(TAG, "Routing to streaming processing for request: $requestId")
                requestProcessingHelper.processStreamingRequest(
                    requestId = requestId,
                    inferenceRequest = inferenceRequest,
                    capability = CapabilityType.LLM,
                    requestType = "Chat"
                ) { result ->
                    // Convert each streaming result and notify clients
                    val response = convertToAIResponse(result, requestId, isStreaming = true)
                    clientManager.notifyChatResponse(response)
                }
            } else {
                // Process as non-streaming request
                Log.d(TAG, "Routing to non-streaming processing for request: $requestId")
                val result = requestProcessingHelper.processNonStreamingRequest(
                    requestId = requestId,
                    inferenceRequest = inferenceRequest,
                    capability = CapabilityType.LLM,
                    requestType = "Chat"
                )
                
                // Convert result to external format and notify clients
                val response = convertToAIResponse(result, requestId, isStreaming = false)
                clientManager.notifyChatResponse(response)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing chat request: $requestId", e)
            clientManager.notifyError(requestId, "Chat processing failed: ${e.message}")
        }
    }
    
    /**
     * Process a TTS request through the AI engine.
     */
    suspend fun processTTSRequest(requestId: String, request: TTSRequest) {
        Log.d(TAG, "Processing TTS request: $requestId")
        
        try {
            // Convert external request to internal format
            val inferenceRequest = convertTTSRequest(request, requestId)
            
            // Process through existing RequestProcessingHelper
            val result = requestProcessingHelper.processNonStreamingRequest(
                requestId = requestId,
                inferenceRequest = inferenceRequest,
                capability = CapabilityType.TTS,
                requestType = "TTS"
            )
            
            // Convert result to external format and notify clients
            val response = convertToAIResponse(result, requestId)
            clientManager.notifyTTSResponse(response)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing TTS request: $requestId", e)
            clientManager.notifyError(requestId, "TTS processing failed: ${e.message}")
        }
    }
    
    /**
     * Process an ASR request through the AI engine.
     */
    suspend fun processASRRequest(requestId: String, request: ASRRequest) {
        Log.d(TAG, "Processing ASR request: $requestId (streaming: ${request.stream})")
        
        try {
            // Convert external request to internal format
            val inferenceRequest = convertASRRequest(request, requestId)
            
            // FIX: Route ASR requests based on stream parameter as well
            if (request.stream == true) {
                // Process as streaming ASR request
                Log.d(TAG, "Routing to streaming ASR processing for request: $requestId")
                requestProcessingHelper.processStreamingRequest(
                    requestId = requestId,
                    inferenceRequest = inferenceRequest,
                    capability = CapabilityType.ASR,
                    requestType = "ASR"
                ) { result ->
                    // Convert each streaming result and notify clients
                    val response = convertToAIResponse(result, requestId, isStreaming = true)
                    clientManager.notifyASRResponse(response)
                }
            } else {
                // Process as non-streaming ASR request
                Log.d(TAG, "Routing to non-streaming ASR processing for request: $requestId")
                val result = requestProcessingHelper.processNonStreamingRequest(
                    requestId = requestId,
                    inferenceRequest = inferenceRequest,
                    capability = CapabilityType.ASR,
                    requestType = "ASR"
                )
                
                // Convert result to external format and notify clients
                val response = convertToAIResponse(result, requestId, isStreaming = false)
                clientManager.notifyASRResponse(response)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing ASR request: $requestId", e)
            clientManager.notifyError(requestId, "ASR processing failed: ${e.message}")
        }
    }
    
    /**
     * Cancel an active request.
     */
    fun cancelRequest(requestId: String): Boolean {
        Log.d(TAG, "Cancelling request: $requestId")
        
        return try {
            // Delegate to engine manager for cancellation
            engineManager.cancelRequest(requestId)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling request: $requestId", e)
            false
        }
    }
    
    /**
     * Process a capability request by name.
     */
    suspend fun processCapabilityRequest(requestId: String, capabilityName: String, input: String): InferenceResult? {
        val capability = when (capabilityName.lowercase()) {
            "llm", "chat" -> CapabilityType.LLM
            "tts" -> CapabilityType.TTS
            "asr" -> CapabilityType.ASR
            "vlm" -> CapabilityType.VLM
            else -> {
                Log.w(TAG, "Unknown capability requested: $capabilityName")
                return null
            }
        }
        
        val inferenceRequest = InferenceRequest(
            sessionId = requestId,
            inputs = mapOf(InferenceRequest.INPUT_TEXT to input),
            params = emptyMap()
        )
        
        return requestProcessingHelper.processNonStreamingRequest(
            requestId = requestId,
            inferenceRequest = inferenceRequest,
            capability = capability,
            requestType = capabilityName
        )
    }
    
    /**
     * Convert external ChatRequest to internal InferenceRequest.
     */
    private fun convertChatRequest(request: ChatRequest, requestId: String): InferenceRequest {
        // Extract the last message as the main input text
        val inputText = request.messages?.lastOrNull()?.content ?: ""
        
        return InferenceRequest(
            sessionId = requestId,
            inputs = mapOf(
                InferenceRequest.INPUT_TEXT to inputText
            ),
            params = mapOf(
                "temperature" to (request.temperature ?: 0.7f),
                "max_tokens" to 2048,
                "stream" to (request.stream ?: false),
                "model" to request.model
            )
        )
    }
    
    /**
     * Convert external TTSRequest to internal InferenceRequest.
     */
    private fun convertTTSRequest(request: TTSRequest, requestId: String): InferenceRequest {
        return InferenceRequest(
            sessionId = requestId,
            inputs = mapOf(
                InferenceRequest.INPUT_TEXT to (request.input ?: "")
            ),
            params = mapOf(
                "voice" to (request.voice ?: "default"),
                "speed" to (request.speed ?: 1.0f)
            )
        )
    }
    
    /**
     * Convert external ASRRequest to internal InferenceRequest.
     */
    private fun convertASRRequest(request: ASRRequest, requestId: String): InferenceRequest {
        return InferenceRequest(
            sessionId = requestId,
            inputs = mapOf(
                "audio" to (request.file ?: byteArrayOf())
            ),
            params = mapOf(
                "language" to (request.language ?: "auto"),
                "stream" to (request.stream ?: false)
            )
        )
    }
    
    /**
     * Convert internal InferenceResult to external AIResponse.
     * FIX: Added isStreaming parameter to properly handle streaming responses
     */
    private fun convertToAIResponse(
        result: InferenceResult?, 
        requestId: String, 
        isStreaming: Boolean = false
    ): AIResponse {
        return if (result != null && result.error == null) {
            AIResponse(
                requestId = requestId,
                text = result.outputs[InferenceResult.OUTPUT_TEXT] as? String ?: "",
                isComplete = !result.partial, // Use result.partial to determine completion
                state = if (result.partial) AIResponse.ResponseState.STREAMING else AIResponse.ResponseState.COMPLETED,
                error = null,
                audioData = result.outputs["audio"] as? ByteArray
            )
        } else {
            AIResponse(
                requestId = requestId,
                text = "",
                isComplete = true,
                state = AIResponse.ResponseState.ERROR,
                error = result?.error?.message ?: "No result returned from processing"
            )
        }
    }
    
    /**
     * Overloaded method for backward compatibility
     */
    private fun convertToAIResponse(result: InferenceResult?, requestId: String): AIResponse {
        return convertToAIResponse(result, requestId, isStreaming = false)
    }
}