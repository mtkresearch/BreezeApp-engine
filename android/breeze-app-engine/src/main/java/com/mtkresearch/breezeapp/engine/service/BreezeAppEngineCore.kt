package com.mtkresearch.breezeapp.engine.service

import android.content.Context
import android.util.Log
import com.mtkresearch.breezeapp.engine.runner.core.RunnerManager
import com.mtkresearch.breezeapp.engine.core.AIEngineManager
import com.mtkresearch.breezeapp.engine.core.InMemoryStorageService
import com.mtkresearch.breezeapp.engine.core.Logger
import com.mtkresearch.breezeapp.engine.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap

/**
 * BreezeAppEngineCore - Business Logic Coordinator
 * 
 * This class contains the core business logic extracted from BreezeAppEngineService,
 * following Clean Architecture principles. It coordinates between different use cases
 * and manages the AI processing pipeline without Android Service lifecycle concerns.
 * 
 * ## Responsibilities
 * - AI request processing coordination
 * - Request tracking and status management
 * - Runner management using annotation-based discovery
 * - Error handling and recovery
 * - Performance metrics collection
 * 
 * ## Architecture Benefits
 * - Separation of Concerns: Business logic separated from Android Service
 * - Testability: Can be unit tested without Android dependencies
 * - Reusability: Can be used in different contexts (Service, Activity, etc.)
 */
class BreezeAppEngineCore(
    private val context: Context
) {
    companion object {
        private const val TAG = "BreezeAppEngineCore"
        private const val MAX_TRACKED_REQUESTS = 100
        private const val REQUEST_RETENTION_TIME_MS = 5 * 60 * 1000L // 5 minutes
    }
    
    // Core dependencies - using our enhanced runner system
    private val logger = Logger
    
    private val storageService = InMemoryStorageService(logger)
    private val modelRegistryService = ModelRegistryService(context)
    private val runnerManager = RunnerManager(context, logger, storageService, modelRegistryService)
    private val aiEngineManager = AIEngineManager(context, runnerManager, modelRegistryService, logger)
    
    // Request tracking for status management
    private val requestIdGenerator = AtomicInteger(0)
    private val activeRequests = ConcurrentHashMap<String, RequestInfo>()
    private val requestMetrics = ConcurrentHashMap<String, RequestMetrics>()
    
    // Coroutine scope for background work
    private val coreJob = SupervisorJob()
    private val coreScope = CoroutineScope(Dispatchers.IO + coreJob)
    
    // Lifecycle management
    @Volatile
    private var isInitialized = false
    
    /**
     * Initialize the engine core and start background monitoring.
     * This replaces the initialization logic from BreezeAppEngineService.
     */
    fun initialize() {
        if (isInitialized) return
        
        try {
            Log.d(TAG, "Initializing BreezeAppEngineCore with annotation-based runner system")
            
            // Initialize the annotation-based runner system
            runnerManager.initializeBlocking()
            
            // Start background request cleanup
            startRequestCleanup()
            
            isInitialized = true
            Log.i(TAG, "BreezeAppEngineCore initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize BreezeAppEngineCore", e)
            throw e
        }
    }
    
    /**
     * Shutdown the engine core and cleanup resources.
     */
    fun shutdown() {
        if (!isInitialized) return
        
        try {
            Log.d(TAG, "Shutting down BreezeAppEngineCore")
            
            // Cancel all background work
            coreJob.cancel()
            
            // Clear tracking data
            activeRequests.clear()
            requestMetrics.clear()
            
            isInitialized = false
            Log.i(TAG, "BreezeAppEngineCore shutdown completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during BreezeAppEngineCore shutdown", e)
        }
    }
    
    /**
     * Process an inference request through the AI engine.
     * This handles non-streaming requests.
     */
    suspend fun processInferenceRequest(request: InferenceRequest): InferenceResult {
        val requestId = request.sessionId
        val startTime = System.currentTimeMillis()
        
        return try {
            Log.d(TAG, "Processing inference request: $requestId")
            
            // Track request start
            trackRequestStart(requestId, "INFERENCE", request.toString())
            
            // Process through AI engine manager
            val result = aiEngineManager.process(request, CapabilityType.LLM)
            
            // Track completion
            trackRequestCompletion(requestId, result != null, System.currentTimeMillis() - startTime)
            
            Log.d(TAG, "Inference request completed: $requestId, result: ${result != null}")
            result ?: InferenceResult.error(RunnerError.runtimeError("No result returned"))
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing inference request: $requestId", e)
            trackRequestCompletion(requestId, false, System.currentTimeMillis() - startTime)
            InferenceResult.error(RunnerError.runtimeError(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * Process a streaming inference request through the AI engine.
     * This handles real-time streaming responses.
     * FIX: Now properly uses processStream() for streaming support
     */
    suspend fun processStreamingRequest(request: InferenceRequest): Flow<InferenceResult> {
        val requestId = request.sessionId
        val startTime = System.currentTimeMillis()
        
        return try {
            Log.d(TAG, "Processing streaming inference request: $requestId")
            
            // Track request start
            trackRequestStart(requestId, "STREAMING", request.toString())
            
            // FIX: Use processStream() for proper streaming support
            aiEngineManager.processStream(request, CapabilityType.LLM)
                .onCompletion { throwable ->
                    // Track completion when stream ends
                    val success = throwable == null
                    val duration = System.currentTimeMillis() - startTime
                    trackRequestCompletion(requestId, success, duration)
                    
                    if (throwable == null) {
                        Log.d(TAG, "Streaming request $requestId completed successfully in ${duration}ms")
                    } else {
                        Log.e(TAG, "Streaming request $requestId failed after ${duration}ms", throwable)
                    }
                }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing streaming inference request: $requestId", e)
            trackRequestCompletion(requestId, false, System.currentTimeMillis() - startTime)
            
            kotlinx.coroutines.flow.flowOf(
                InferenceResult.error(RunnerError.runtimeError(e.message ?: "Unknown error", e))
            )
        }
    }
    
    /**
     * Get current engine status and metrics.
     */
    fun getEngineStatus(): Map<String, Any> {
        return mapOf(
            "initialized" to isInitialized,
            "activeRequests" to activeRequests.size,
            "totalRequestsProcessed" to requestMetrics.size,
            "runnerStats" to runnerManager.getStats()
        )
    }
    
    /**
     * Get detailed performance metrics.
     */
    fun getPerformanceMetrics(): Map<String, Any> {
        val successfulRequests = requestMetrics.values.count { it.success }
        val failedRequests = requestMetrics.values.count { !it.success }
        val avgDuration = if (requestMetrics.isNotEmpty()) {
            requestMetrics.values.map { it.duration }.average()
        } else 0.0
        
        return mapOf(
            "totalRequests" to requestMetrics.size,
            "successfulRequests" to successfulRequests,
            "failedRequests" to failedRequests,
            "successRate" to if (requestMetrics.isNotEmpty()) {
                (successfulRequests.toDouble() / requestMetrics.size * 100)
            } else 0.0,
            "averageDuration" to avgDuration
        )
    }
    
    /**
     * Generate a unique request ID.
     */
    fun generateRequestId(): String {
        return "req_${System.currentTimeMillis()}_${requestIdGenerator.incrementAndGet()}"
    }
    
    private fun trackRequestStart(requestId: String, type: String, details: String) {
        activeRequests[requestId] = RequestInfo(
            id = requestId,
            type = type,
            startTime = System.currentTimeMillis(),
            details = details
        )
        Log.d(TAG, "Request started: $requestId ($type)")
    }
    
    private fun trackRequestCompletion(requestId: String, success: Boolean, duration: Long) {
        activeRequests.remove(requestId)
        requestMetrics[requestId] = RequestMetrics(
            id = requestId,
            success = success,
            duration = duration,
            completedAt = System.currentTimeMillis()
        )
        Log.d(TAG, "Request completed: $requestId, success: $success, duration: ${duration}ms")
    }
    
    private fun startRequestCleanup() {
        coreScope.launch {
            while (isActive) {
                try {
                    delay(60_000) // Clean up every minute
                    cleanupOldRequests()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in request cleanup", e)
                }
            }
        }
    }
    
    private fun cleanupOldRequests() {
        val currentTime = System.currentTimeMillis()
        val cutoffTime = currentTime - REQUEST_RETENTION_TIME_MS
        
        // Remove old metrics
        val oldSize = requestMetrics.size
        requestMetrics.entries.removeAll { (_, metrics) ->
            metrics.completedAt < cutoffTime
        }
        val removedCount = oldSize - requestMetrics.size
        
        if (removedCount > 0) {
            Log.d(TAG, "Cleaned up $removedCount old request metrics")
        }
        
        // Keep only recent requests for tracking
        if (requestMetrics.size > MAX_TRACKED_REQUESTS) {
            val sortedEntries = requestMetrics.entries.sortedByDescending { it.value.completedAt }
            requestMetrics.clear()
            sortedEntries.take(MAX_TRACKED_REQUESTS).forEach { (key, value) ->
                requestMetrics[key] = value
            }
            Log.d(TAG, "Trimmed request metrics to $MAX_TRACKED_REQUESTS entries")
        }
    }
    
    // Data classes for tracking
    private data class RequestInfo(
        val id: String,
        val type: String,
        val startTime: Long,
        val details: String
    )
    
    private data class RequestMetrics(
        val id: String,
        val success: Boolean,
        val duration: Long,
        val completedAt: Long
    )
}