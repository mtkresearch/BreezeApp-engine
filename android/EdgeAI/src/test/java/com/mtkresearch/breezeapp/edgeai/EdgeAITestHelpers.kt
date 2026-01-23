package com.mtkresearch.breezeapp.edgeai

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import org.mockito.kotlin.*

/**
 * Helper class to set up a mocked EdgeAI environment for unit tests.
 */
object EdgeAITestHelpers {

    /**
     * Initializes the EdgeAI SDK with mocked components.
     * 
     * @param context Mocked Context
     * @param service Mocked IBreezeAppEngineService
     * @return The mocked service for further configuration
     */
    suspend fun setupMockEdgeAI(
        context: Context = mock(),
        service: IBreezeAppEngineService = mock()
    ): IBreezeAppEngineService {
        var capturedListener: IBreezeAppEngineListener? = null
        
        // Mock registerListener to capture the listener
        whenever(service.registerListener(any())).thenAnswer { invocation ->
            capturedListener = invocation.arguments[0] as IBreezeAppEngineListener
            Unit
        }

        // Auto-respond to ASR requests
        whenever(service.sendASRRequest(any(), any())).thenAnswer { invocation ->
            val requestId = invocation.arguments[0] as String
            val request = invocation.arguments[1] as ASRRequest
            
            // Use metrics to pass additional ASR info since EdgeAI's conversion doesn't populate language/segments fields
            val metrics = mutableMapOf<String, String>(
                "language" to "en"
            )
            
            // Add segments info based on request format
            if (request.timestampGranularities?.contains("word") == true || 
                request.responseFormat == "verbose_json") {
                metrics["has_segments"] = "true"
            }
            
            val response = AIResponse(
                requestId = requestId,
                text = "Mock ASR Transcription",
                isComplete = true,
                state = AIResponse.ResponseState.COMPLETED,
                metrics = metrics
            )
            capturedListener?.onResponse(response)
            Unit
        }

        // Auto-respond to Chat requests
        whenever(service.sendChatRequest(any(), any())).thenAnswer { invocation ->
            val requestId = invocation.arguments[0] as String
            val request = invocation.arguments[1] as ChatRequest
            val prompt = request.messages.lastOrNull()?.content ?: ""
            
            val responseText = if (prompt.contains("3+3")) {
                "The result of 3+3 is 6."
            } else {
                "Mock Chat Response"
            }
            
            if (request.stream == true) {
                // Send multiple chunks for streaming tests
                val chunks = responseText.split(" ")
                chunks.forEachIndexed { index, chunk ->
                    val isLast = index == chunks.size - 1
                    val response = AIResponse(
                        requestId = requestId,
                        text = if (isLast) chunk else "$chunk ",
                        isComplete = isLast,
                        state = if (isLast) AIResponse.ResponseState.COMPLETED else AIResponse.ResponseState.STREAMING
                    )
                    capturedListener?.onResponse(response)
                }
            } else {
                // Single response for non-streaming
                val response = AIResponse(
                    requestId = requestId,
                    text = responseText,
                    isComplete = true,
                    state = AIResponse.ResponseState.COMPLETED
                )
                capturedListener?.onResponse(response)
            }
            Unit
        }

        // Auto-respond to TTS requests  
        whenever(service.sendTTSRequest(any(), any())).thenAnswer { invocation ->
            val requestId = invocation.arguments[0] as String
            val ttsRequest = invocation.arguments[1] as TTSRequest
            
            // Generate mock audio data (at least 1KB for realistic tests)
            val mockAudioData = ByteArray(1024) { it.toByte() }
            // TTSRequest field is responseFormat, not format
            val audioFormat = ttsRequest.responseFormat ?: "mp3"
            
            val response = AIResponse(
                requestId = requestId,
                text = "Mock TTS complete",
                audioData = mockAudioData,
                isComplete = true,
                state = AIResponse.ResponseState.COMPLETED,
                format = audioFormat
            )
            capturedListener?.onResponse(response)
            Unit
        }
        
        // Inject mock service through reflection
        val serviceField = EdgeAI::class.java.getDeclaredField("service")
        serviceField.isAccessible = true
        serviceField.set(EdgeAI, service)
        
        val isBoundField = EdgeAI::class.java.getDeclaredField("isBound")
        isBoundField.isAccessible = true
        isBoundField.set(EdgeAI, true)
        
        val isInitializedField = EdgeAI::class.java.getDeclaredField("isInitialized")
        isInitializedField.isAccessible = true
        isInitializedField.set(EdgeAI, true)

        val contextField = EdgeAI::class.java.getDeclaredField("context")
        contextField.isAccessible = true
        contextField.set(EdgeAI, context)
        
        // Manually trigger listener registration since we skipped initialize()
        val listenerField = EdgeAI::class.java.getDeclaredField("breezeAppEngineListener")
        listenerField.isAccessible = true
        val listener = listenerField.get(EdgeAI) as IBreezeAppEngineListener
        service.registerListener(listener)
        
        return service
    }
}
