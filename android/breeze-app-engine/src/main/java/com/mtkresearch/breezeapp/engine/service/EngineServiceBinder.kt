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
 * - Request lifecycle tracking for breathing border
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
    private val aiEngineManager: AIEngineManager,
    private val serviceOrchestrator: ServiceOrchestrator
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
        
        // Start request tracking for breathing border
        serviceOrchestrator.startRequestTracking(requestId, "Chat")
        
        try {
            // Convert external request to internal domain model (Adapter Pattern)
            val inferenceRequest = convertChatRequestToDomain(request, requestId)
            
            // Detect capability (LLM vs VLM) for proper routing
            val capability = detectVLMCapability(request)
            
            // Determine processing type based on stream parameter
            val isStreaming = request.stream ?: false
            
            if (isStreaming) {
                // Handle streaming chat request with detected capability
                aiEngineManager.processStream(inferenceRequest, capability)
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
                // Handle non-streaming chat request with detected capability
                Log.d(TAG, "💬 Processing non-streaming chat request: $requestId with capability: $capability")
                val result = aiEngineManager.process(inferenceRequest, capability)
                Log.d(TAG, "💬 Chat result received - requestId: $requestId, outputs: ${result.outputs.keys}, error: ${result.error}")
                val aiResponse = convertInferenceResultToAIResponse(result, requestId, "chat")
                Log.d(TAG, "💬 Chat response converted - text.length: ${aiResponse.text.length}, error: ${aiResponse.error}")
                clientManager.notifyChatResponse(aiResponse)
                Log.d(TAG, "💬 Chat response sent to clients")
            }
        } finally {
            // End request tracking for breathing border
            serviceOrchestrator.endRequestTracking(requestId, "Chat")
        }
    }
    
    /**
     * Process TTS request following Clean Architecture principles.
     */
    private suspend fun processTTSRequestInternal(requestId: String, request: TTSRequest) {
        Log.d(TAG, "Processing TTS request internally: $requestId")
        
        // Start request tracking for breathing border
        serviceOrchestrator.startRequestTracking(requestId, "TTS")
        
        try {
            // Convert external request to internal domain model
            val inferenceRequest = convertTTSRequestToDomain(request, requestId)
            
            // TTS is typically streaming for audio output
            aiEngineManager.processStream(inferenceRequest, CapabilityType.TTS)
                .catch { error ->
                    Log.e(TAG, "TTS processing error", error)
                    clientManager.notifyError(requestId, "TTS processing failed: ${error.message}")
                }
                .collect { result ->
                    Log.d(TAG, "🎵 TTS result received - requestId: $requestId, partial: ${result.partial}, outputs: ${result.outputs.keys}")
                    val aiResponse = convertInferenceResultToAIResponse(result, requestId, "tts")
                    Log.d(TAG, "🎵 TTS response converted - audioData: ${aiResponse.audioData?.size} bytes, error: ${aiResponse.error}")
                    clientManager.notifyTTSResponse(aiResponse)
                    Log.d(TAG, "🎵 TTS response sent to clients")
                }
        } finally {
            // End request tracking for breathing border
            serviceOrchestrator.endRequestTracking(requestId, "TTS")
        }
    }
    
    /**
     * Process ASR request following Clean Architecture principles.
     */
    private suspend fun processASRRequestInternal(requestId: String, request: ASRRequest) {
        Log.d(TAG, "Processing ASR request internally: $requestId")
        
        // Start request tracking for breathing border
        serviceOrchestrator.startRequestTracking(requestId, "ASR")
        
        try {
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
                        Log.d(TAG, "🎤 ASR result received - requestId: $requestId, partial: ${result.partial}, outputs: ${result.outputs.keys}")
                        val aiResponse = convertInferenceResultToAIResponse(result, requestId, "asr")
                        Log.d(TAG, "🎤 ASR response converted - text: '${aiResponse.text}', error: ${aiResponse.error}")
                        clientManager.notifyASRResponse(aiResponse)
                        Log.d(TAG, "🎤 ASR response sent to clients")
                    }
            } else {
                // Handle non-streaming ASR (file input)
                Log.d(TAG, "🎤 Processing non-streaming ASR request: $requestId")
                val result = aiEngineManager.process(inferenceRequest, CapabilityType.ASR)
                Log.d(TAG, "🎤 ASR result received - requestId: $requestId, outputs: ${result.outputs.keys}, error: ${result.error}")
                val aiResponse = convertInferenceResultToAIResponse(result, requestId, "asr")
                Log.d(TAG, "🎤 ASR response converted - text: '${aiResponse.text}', error: ${aiResponse.error}")
                clientManager.notifyASRResponse(aiResponse)
                Log.d(TAG, "🎤 ASR response sent to clients")
            }
        } finally {
            // End request tracking for breathing border
            serviceOrchestrator.endRequestTracking(requestId, "ASR")
        }
    }
    
    // ========================================================================
    // CENTRAL PARAMETER MERGING UTILITY (Clean Architecture - Single Responsibility)
    // ========================================================================
    
    /**
     * Complete Parameter Hierarchy: Build parameters with proper 3-layer precedence.
     * This ensures all runners get complete parameter sets with proper defaults.
     * 
     * Parameter Hierarchy (lowest to highest precedence):
     * 1. Runner ParameterSchema Defaults (e.g., temperature=0.8 for ExecutorchLLMRunner)
     * 2. Engine Runtime Settings (from EngineSettingsActivity/SharedPreferences)
     * 3. Selective Client Overrides (limited set only)
     * 
     * @param clientOverrides Client parameters that can override engine settings (limited set)
     * @param capability The AI capability (LLM, VLM, TTS, ASR)
     * @param requestId Request ID for logging
     * @return Complete parameter map with proper hierarchy applied
     */
    private fun buildEngineFirstParameters(
        clientOverrides: Map<String, Any>,
        capability: CapabilityType,
        requestId: String
    ): Map<String, Any> {
        return try {
            val engineSettings = aiEngineManager.getCurrentEngineSettings()
            
            // Get the currently selected runner for this capability
            val selectedRunner = engineSettings.selectedRunners[capability]
            if (selectedRunner != null) {
                // LAYER 1: Start with runner's ParameterSchema defaults
                val runnerDefaults = aiEngineManager.getRunnerParameterDefaults(selectedRunner)
                val finalParams = runnerDefaults.toMutableMap()
                
                // LAYER 2: Apply engine runtime settings (overrides defaults)
                val engineParams = engineSettings.getRunnerParameters(selectedRunner)
                var engineOverrides = 0
                engineParams.forEach { (paramName, paramValue) ->
                    finalParams[paramName] = paramValue
                    engineOverrides++
                }
                
                // LAYER 3: Apply selective client overrides (overrides everything)
                val allowedOverrides = setOf(
                    "stream",  // Streaming mode is a client choice
                    "response_format",  // Response format for TTS/ASR
                    "microphone_mode"   // ASR microphone mode
                )
                
                var clientOverrideCount = 0
                clientOverrides.forEach { (paramName, paramValue) ->
                    if (allowedOverrides.contains(paramName)) {
                        finalParams[paramName] = paramValue
                        clientOverrideCount++
                        Log.d(TAG, "💬 [$capability] Client override allowed: $paramName = $paramValue")
                    } else {
                        Log.d(TAG, "💬 [$capability] Client override ignored: $paramName = $paramValue (using engine/default value)")
                    }
                }
                
                Log.d(TAG, "💬 [$capability] Complete parameter hierarchy for runner: $selectedRunner")
                Log.d(TAG, "💬 [$capability] - Runner defaults: ${runnerDefaults.size} parameters")
                Log.d(TAG, "💬 [$capability] - Engine overrides: $engineOverrides parameters")
                Log.d(TAG, "💬 [$capability] - Client overrides: $clientOverrideCount parameters")
                Log.d(TAG, "💬 [$capability] Final parameters for $requestId: $finalParams")
                
                return finalParams.toMap()
            } else {
                Log.w(TAG, "💬 [$capability] No selected runner found - using client parameters as fallback")
                return clientOverrides
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "💬 [$capability] Failed to build complete parameter hierarchy for $requestId", e)
            return clientOverrides
        }
    }
    
    /**
     * Detect if a ChatRequest should be routed to VLM based on model name or explicit indicators.
     * This supports VLM requests via ChatRequest for unified API.
     */
    private fun detectVLMCapability(request: ChatRequest): CapabilityType {
        val model = request.model?.lowercase() ?: ""
        
        return when {
            // VLM model patterns
            model.contains("vlm") || 
            model.contains("vision") || 
            model.contains("llava") ||
            model.contains("multimodal") -> {
                Log.d(TAG, "💬 Detected VLM capability from model: ${request.model}")
                CapabilityType.VLM
            }
            // Check if request contains image/visual content
            request.messages?.any { message ->
                // Look for image content in messages (OpenAI format)
                message.content?.contains("image") == true ||
                message.content?.contains("vision") == true
            } == true -> {
                Log.d(TAG, "💬 Detected VLM capability from message content")
                CapabilityType.VLM
            }
            // Default to LLM
            else -> {
                Log.d(TAG, "💬 Using LLM capability for model: ${request.model}")
                CapabilityType.LLM
            }
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
        // DEBUG: Log what we received from client
        Log.d(TAG, "💬 ChatRequest from client - model: '${request.model}', temperature: ${request.temperature}, stream: ${request.stream}")
        
        // Extract the last message as the main input text
        val inputText = request.messages?.lastOrNull()?.content ?: ""
        
        // Detect actual capability (LLM vs VLM) based on model and content
        val detectedCapability = detectVLMCapability(request)
        
        // Build client overrides (ONLY allowed parameters)
        val clientOverrides = mutableMapOf<String, Any>()
        
        // Only allow streaming mode as client override
        request.stream?.let { clientOverrides["stream"] = it }
        
        Log.d(TAG, "💬 Client overrides: $clientOverrides")
        
        // Use engine-first parameter strategy
        val finalParams = buildEngineFirstParameters(clientOverrides, detectedCapability, requestId)
        
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
            params = finalParams
        )
    }
    
    /**
     * Convert external TTSRequest to internal InferenceRequest.
     */
    private fun convertTTSRequestToDomain(request: TTSRequest, requestId: String): InferenceRequest {
        // DEBUG: Log what we received from client
        Log.d(TAG, "🎵 TTSRequest from client - voice: '${request.voice}', speed: ${request.speed}, responseFormat: '${request.responseFormat}'")
        
        // Build parameters map with client-provided values
        val clientOverrides = mutableMapOf<String, Any>(
            "voice" to (request.voice ?: "default"),
            "speed" to (request.speed ?: 1.0f),
            "response_format" to (request.responseFormat ?: "pcm")
        )
        
        Log.d(TAG, "🎵 TTS Client overrides: $clientOverrides")
        
        // Use engine-first parameter strategy  
        val finalParams = buildEngineFirstParameters(clientOverrides, CapabilityType.TTS, requestId)
        
        return InferenceRequest(
            sessionId = requestId,
            inputs = mapOf(
                InferenceRequest.INPUT_TEXT to (request.input ?: "")
            ),
            params = finalParams
        )
    }
    
    /**
     * Convert external ASRRequest to internal InferenceRequest.
     */
    private fun convertASRRequestToDomain(request: ASRRequest, requestId: String): InferenceRequest {
        val isMicrophoneMode = detectMicrophoneMode(request)
        
        // DEBUG: Log what we received from client
        Log.d(TAG, "🎤 ASRRequest from client - language: '${request.language}', responseFormat: '${request.responseFormat}', stream: ${request.stream}, fileSize: ${request.file?.size ?: 0}")
        Log.d(TAG, "🎤 Detected microphone mode: $isMicrophoneMode")
        
        // Helper function to create mutable params map with engine settings merged
        fun createMutableParams(baseParams: Map<String, Any>): Map<String, Any> {
            Log.d(TAG, "🎤 ASR Client overrides: $baseParams")
            // Use engine-first parameter strategy
            return buildEngineFirstParameters(baseParams, CapabilityType.ASR, requestId)
        }
        
        return if (isMicrophoneMode) {
            // Microphone mode: streaming ASR
            InferenceRequest(
                sessionId = requestId,
                inputs = mapOf(
                    InferenceRequest.INPUT_TEXT to "microphone_input"
                ),
                params = createMutableParams(mapOf(
                    "language" to (request.language ?: "auto"),
                    "stream" to true,
                    "microphone_mode" to true,
                    "sample_rate" to 16000,
                    "format" to "pcm16"
                ))
            )
        } else {
            // File mode: non-streaming ASR
            InferenceRequest(
                sessionId = requestId,
                inputs = mapOf(
                    InferenceRequest.INPUT_AUDIO to (request.file ?: ByteArray(0))
                ),
                params = createMutableParams(mapOf(
                    "language" to (request.language ?: "auto"),
                    "stream" to false,
                    "input_format" to (request.responseFormat ?: "wav")
                ))
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