package com.mtkresearch.breezeapp.engine.service

import android.util.Log
import com.mtkresearch.breezeapp.edgeai.ChatRequest
import com.mtkresearch.breezeapp.edgeai.TTSRequest
import com.mtkresearch.breezeapp.edgeai.ASRRequest
import com.mtkresearch.breezeapp.edgeai.AIResponse
import com.mtkresearch.breezeapp.engine.core.AIEngineManager
import com.mtkresearch.breezeapp.engine.service.RequestProcessor
import com.mtkresearch.breezeapp.engine.model.*
import com.mtkresearch.breezeapp.engine.model.InferenceResult
import com.mtkresearch.breezeapp.engine.BreezeAppEngineService

/**
 * RequestCoordinator - Coordinates AI Request Processing
 * 
 * This class coordinates AI request processing, following Single Responsibility Principle.
 * It handles ONLY request coordination concerns:
 * - Converting external requests to internal format
 * - Delegating to existing RequestProcessor
 * - Converting internal responses to external format
 * - Request cancellation coordination
 * - FIX: Proper routing between streaming and non-streaming requests
 * 
 * ## Architecture Benefits
 * - Single Responsibility: Only request coordination
 * - Reuses Existing: Leverages existing RequestProcessor
 * - Clean: No Android Service or client management concerns
 * - Testable: Can be unit tested independently
 */
class RequestCoordinator(
    private val requestProcessor: RequestProcessor,
    private val engineManager: AIEngineManager,
    private val clientManager: ClientManager,
    private var serviceInstance: BreezeAppEngineService? = null
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
                requestProcessor.processStreamingRequest(
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
                val result = requestProcessor.processNonStreamingRequest(
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
     * Now optimized for engine-side audio playback.
     */
    suspend fun processTTSRequest(requestId: String, request: TTSRequest) {
        Log.d(TAG, "Processing TTS request: $requestId")
        
        try {
            // Convert external request to internal format
            val inferenceRequest = convertTTSRequest(request, requestId)
            
            // Always use streaming for TTS to enable real-time engine playback
            Log.d(TAG, "Processing TTS as streaming for real-time engine playback")
            requestProcessor.processStreamingRequest(
                requestId = requestId,
                inferenceRequest = inferenceRequest,
                capability = CapabilityType.TTS,
                requestType = "TTS"
            ) { result ->
                // Convert each streaming result and notify clients
                val response = convertToAIResponse(result, requestId, isStreaming = true)
                clientManager.notifyTTSResponse(response)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing TTS request: $requestId", e)
            clientManager.notifyError(requestId, "TTS processing failed: ${e.message}")
        }
    }
    
    /**
     * Process an ASR request through the AI engine.
     * Enhanced to support microphone mode for real-time ASR processing.
     */
    suspend fun processASRRequest(requestId: String, request: ASRRequest) {
        Log.d(TAG, "Processing ASR request: $requestId (streaming: ${request.stream})")
        
        try {
            // Convert external request to internal format
            val inferenceRequest = convertASRRequest(request, requestId)
            
            // Check if this is microphone mode
            val isMicrophoneMode = inferenceRequest.params["microphone_mode"] as? Boolean ?: false
            
            if (isMicrophoneMode) {
                // Microphone mode always requires streaming
                Log.d(TAG, "Processing microphone mode ASR request: $requestId")
                
                // Update foreground service type to include microphone
                serviceInstance?.updateForegroundServiceType(true)
                
                requestProcessor.processStreamingRequest(
                    requestId = requestId,
                    inferenceRequest = inferenceRequest,
                    capability = CapabilityType.ASR,
                    requestType = "ASR-Microphone"
                ) { result ->
                    // Convert each streaming result and notify clients
                    val response = convertToAIResponse(result, requestId, isStreaming = true)
                    clientManager.notifyASRResponse(response)
                    
                    // If this is the final result, restore service type
                    if (!result.partial) {
                        serviceInstance?.updateForegroundServiceType(false)
                    }
                }
            } else if (request.stream == true) {
                // Process as streaming ASR request (file-based)
                Log.d(TAG, "Routing to streaming ASR processing for request: $requestId")
                requestProcessor.processStreamingRequest(
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
                // Process as non-streaming ASR request (file-based)
                Log.d(TAG, "Routing to non-streaming ASR processing for request: $requestId")
                val result = requestProcessor.processNonStreamingRequest(
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
     * Sets the service instance for components that need it
     */
    fun setServiceInstance(service: BreezeAppEngineService) {
        this.serviceInstance = service
        Log.d(TAG, "Service instance set for RequestCoordinator")
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
        
        return requestProcessor.processNonStreamingRequest(
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

        // Map standard and extended parameters\n        val finalTemperature = request.temperature ?: 1.0f\n        Log.d(TAG, \"TEMPERATURE FIX: Converting ${request.temperature} -> $finalTemperature\")
        val params = mutableMapOf<String, Any>(
            InferenceRequest.PARAM_TEMPERATURE to (request.temperature ?: 1.0f).also { temp ->
                Log.d(TAG, "TEMPERATURE FIX: request.temperature=${request.temperature} -> final=$temp")},
            "stream" to (request.stream ?: false),
            "model" to request.model
        )

        // max tokens (ChatRequest uses maxCompletionTokens)
        request.maxCompletionTokens?.let { params[InferenceRequest.PARAM_MAX_TOKENS] = it }

        // top_p (if provided)
        request.topP?.let { params["top_p"] = it }

        // Non-standard fields passed via metadata: top_k, repetition_penalty
        val metadata = request.metadata
        val topK = metadata?.get("top_k")?.toIntOrNull()
        val repetitionPenalty = metadata?.get("repetition_penalty")?.toFloatOrNull()
        topK?.let { params["top_k"] = it }
        repetitionPenalty?.let { params["repetition_penalty"] = it }

        return InferenceRequest(
            sessionId = requestId,
            inputs = mapOf(
                InferenceRequest.INPUT_TEXT to inputText
            ),
            params = params
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
     * Enhanced to support microphone mode detection and configuration.
     */
    private fun convertASRRequest(request: ASRRequest, requestId: String): InferenceRequest {
        // Detect microphone mode based on request characteristics
        val isMicrophoneMode = detectMicrophoneMode(request)
        
        return if (isMicrophoneMode) {
            // Microphone mode: no audio file, streaming enabled
            Log.d(TAG, "Converting ASR request to microphone mode for request: $requestId")
            InferenceRequest(
                sessionId = requestId,
                inputs = mapOf(
                    // No audio input for microphone mode - will be captured by engine
                    InferenceRequest.INPUT_TEXT to "microphone_input"
                ),
                params = mapOf(
                    "language" to (request.language ?: "auto"),
                    "stream" to true, // Microphone mode is always streaming
                    "microphone_mode" to true,
                    "sample_rate" to 16000,
                    "format" to "pcm16"
                )
            )
        } else {
            // File mode: traditional audio file processing
            Log.d(TAG, "Converting ASR request to file mode for request: $requestId")
            InferenceRequest(
                sessionId = requestId,
                inputs = mapOf(
                    "audio" to (request.file ?: byteArrayOf())
                ),
                params = mapOf(
                    "language" to (request.language ?: "auto"),
                    "stream" to (request.stream ?: false),
                    "microphone_mode" to false
                )
            )
        }
    }
    
    /**
     * Detect if this is a microphone mode request based on request characteristics.
     * Microphone mode is detected when:
     * 1. No audio file is provided (file is null or empty)
     * 2. Stream is enabled (true)
     * 3. Or explicitly marked as microphone mode in future enhancements
     */
    private fun detectMicrophoneMode(request: ASRRequest): Boolean {
        // Primary detection: no audio file + streaming enabled
        val hasNoAudioFile = request.file == null || request.file.isEmpty()
        val isStreaming = request.stream == true
        
        // Future enhancement: explicit microphone mode flag
        // val explicitMicMode = request.microphoneMode == true
        
        val isMicrophoneMode = hasNoAudioFile && isStreaming
        
        Log.d(TAG, "Microphone mode detection - hasNoAudioFile: $hasNoAudioFile, isStreaming: $isStreaming, result: $isMicrophoneMode")
        
        return isMicrophoneMode
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
            // 修復：正確提取音頻數據和元數據
            val audioData = result.outputs["audioData"] as? ByteArray ?: result.outputs["audio"] as? ByteArray
            val sampleRate = result.metadata["sampleRate"] as? Int ?: 16000
            val channels = result.metadata["channels"] as? Int ?: 1
            val bitDepth = result.metadata["bitDepth"] as? Int ?: 16
            val format = result.metadata["format"] as? String ?: "pcm16"
            val chunkIndex = result.metadata["chunkIndex"] as? Int ?: 0
            val isLastChunk = result.metadata["isLastChunk"] as? Boolean ?: true
            val durationMs = result.metadata["durationMs"] as? Int ?: 0
            
            AIResponse(
                requestId = requestId,
                text = result.outputs[InferenceResult.OUTPUT_TEXT] as? String ?: "",
                isComplete = !result.partial, // Use result.partial to determine completion
                state = if (result.partial) AIResponse.ResponseState.STREAMING else AIResponse.ResponseState.COMPLETED,
                error = null,
                audioData = audioData,
                chunkIndex = chunkIndex,
                isLastChunk = isLastChunk,
                format = format,
                sampleRate = sampleRate,
                channels = channels,
                bitDepth = bitDepth,
                durationMs = durationMs
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