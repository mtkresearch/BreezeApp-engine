package com.mtkresearch.breezeapp.engine.service

import android.util.Log
import com.mtkresearch.breezeapp.engine.core.AIEngineManager
import com.mtkresearch.breezeapp.engine.domain.model.InferenceRequest
import com.mtkresearch.breezeapp.engine.domain.model.InferenceResult
import com.mtkresearch.breezeapp.engine.domain.model.CapabilityType
import com.mtkresearch.breezeapp.engine.core.BreezeAppEngineStatusManager
import com.mtkresearch.breezeapp.engine.core.CancellationManager
import kotlinx.coroutines.flow.catch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Request Processor - Centralized request processing logic
 * 
 * This class handles all AI request processing with robust error handling
 * and cleanup. It was moved from core/RequestProcessingHelper to service/
 * to better reflect its role in the service layer.
 * 
 * Responsibilities:
 * - Process streaming and non-streaming requests
 * - Handle request lifecycle and cleanup
 * - Provide unified error handling
 * - Track request status and performance
 */
class RequestProcessor(
    private val engineManager: AIEngineManager,
    private val statusManager: BreezeAppEngineStatusManager,
    private val activeRequestCount: AtomicInteger,
    private val requestTracker: ConcurrentHashMap<String, Long>,
    private val updateStatusAfterRequestCompletion: (Int) -> Unit,
    private val notifyError: (String, String) -> Unit
) {
    
    companion object {
        private const val TAG = "RequestProcessor"
    }
    
    /**
     * Processes a non-streaming request with unified error handling and cleanup.
     */
    suspend fun processNonStreamingRequest(
        requestId: String,
        inferenceRequest: InferenceRequest,
        capability: CapabilityType,
        requestType: String
    ): InferenceResult? {
        val startTime = System.currentTimeMillis()
        requestTracker[requestId] = startTime
        val currentActiveRequests = activeRequestCount.incrementAndGet()
        statusManager.updateState(com.mtkresearch.breezeapp.engine.domain.model.ServiceState.Processing(currentActiveRequests))
        
        Log.d(TAG, "Started processing $requestType request $requestId (active: $currentActiveRequests)")
        
        return try {
            val result = engineManager.process(inferenceRequest, capability)
            Log.d(TAG, "$requestType request $requestId processed successfully")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error processing $requestType request $requestId", e)
            statusManager.setError("$requestType processing failed: ${e.message}")
            notifyError(requestId, e.message ?: "Unknown $requestType processing error")
            null
        } finally {
            // Always clean up, regardless of success or failure
            requestTracker.remove(requestId)
            val remainingRequests = activeRequestCount.decrementAndGet()
            val processingTime = System.currentTimeMillis() - startTime
            
            Log.d(TAG, "Completed $requestType request $requestId in ${processingTime}ms (remaining: $remainingRequests)")
            updateStatusAfterRequestCompletion(remainingRequests)
        }
    }
    
    /**
     * Processes a streaming request with proper completion detection and cleanup.
     */
    suspend fun processStreamingRequest(
        requestId: String,
        inferenceRequest: InferenceRequest,
        capability: CapabilityType,
        requestType: String,
        onResult: (InferenceResult) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        requestTracker[requestId] = startTime
        val currentActiveRequests = activeRequestCount.incrementAndGet()
        statusManager.updateState(com.mtkresearch.breezeapp.engine.domain.model.ServiceState.Processing(currentActiveRequests))
        
        Log.d(TAG, "Started processing streaming $requestType request $requestId (active: $currentActiveRequests)")
        
        // Use unified cancellation manager to track requests
        val currentJob = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
        currentJob?.let { job ->
            CancellationManager.getInstance().registerRequest(requestId, job)
        }
        
        var streamCompleted = false
        
        try {
            engineManager.processStream(inferenceRequest, capability)
                .catch { error ->
                    if (error is kotlinx.coroutines.CancellationException) {
                        Log.d(TAG, "$requestType stream cancelled (normal shutdown)")
                    } else {
                        Log.e(TAG, "$requestType stream processing error", error)
                        notifyError(requestId, error.message ?: "$requestType stream processing failed")
                    }
                    streamCompleted = true
                    // Clean up on error
                    cleanupStreamingRequest(requestId, startTime, requestType)
                }
                .collect { result ->
                    onResult(result)
                    
                    // Check if this is the final response in the stream
                    if (!result.partial) {
                        if (!streamCompleted) {
                            streamCompleted = true
                            Log.d(TAG, "Stream completed for $requestType request $requestId")
                            cleanupStreamingRequest(requestId, startTime, requestType)
                        } else {
                            Log.d(TAG, "Ignoring duplicate final result for $requestType request $requestId")
                        }
                    }
                }
            
            // Safety check: if stream ended without completion signal
            if (!streamCompleted) {
                Log.w(TAG, "Stream ended without completion signal for $requestType request $requestId")
                cleanupStreamingRequest(requestId, startTime, requestType)
            }
            
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.d(TAG, "Streaming $requestType request $requestId cancelled (normal shutdown)")
            // Ensure cleanup on cancellation
            if (!streamCompleted) {
                cleanupStreamingRequest(requestId, startTime, requestType)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing streaming $requestType request $requestId", e)
            statusManager.setError("$requestType streaming failed: ${e.message}")
            notifyError(requestId, e.message ?: "Unknown $requestType streaming error")
            
            // Ensure cleanup on exception
            if (!streamCompleted) {
                cleanupStreamingRequest(requestId, startTime, requestType)
            }
        }
    }
    
    /**
     * Centralized cleanup for streaming requests to avoid code duplication.
     */
    private fun cleanupStreamingRequest(requestId: String, startTime: Long, requestType: String) {
        requestTracker.remove(requestId)
        val remainingRequests = activeRequestCount.decrementAndGet()
        val processingTime = System.currentTimeMillis() - startTime
        
        Log.d(TAG, "Completed streaming $requestType request $requestId in ${processingTime}ms (remaining: $remainingRequests)")
        updateStatusAfterRequestCompletion(remainingRequests)
    }
} 