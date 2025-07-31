package com.mtkresearch.breezeapp.engine.core

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import java.util.concurrent.ConcurrentHashMap

/**
 * CancellationManager - Centralized cancellation management
 * 
 * This class provides a unified approach to handle request cancellation
 * across the entire BreezeApp Engine system.
 * 
 * Responsibilities:
 * - Track active requests with their associated Jobs
 * - Provide consistent cancellation checking
 * - Handle cancellation exceptions uniformly
 * - Clean up resources on cancellation
 */
class CancellationManager {
    
    companion object {
        private const val TAG = "CancellationManager"
        
        // Shared instance for singleton pattern
        @Volatile
        private var INSTANCE: CancellationManager? = null
        
        fun getInstance(): CancellationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CancellationManager().also { INSTANCE = it }
            }
        }
    }
    
    // Thread-safe request tracking
    private val activeRequests = ConcurrentHashMap<String, Job>()
    
    /**
     * Register a request for cancellation tracking
     */
    fun registerRequest(requestId: String, job: Job) {
        activeRequests[requestId] = job
        Log.d(TAG, "Registered request for cancellation: $requestId")
    }
    
    /**
     * Unregister a request from cancellation tracking
     */
    fun unregisterRequest(requestId: String) {
        activeRequests.remove(requestId)
        Log.d(TAG, "Unregistered request from cancellation: $requestId")
    }
    
    /**
     * Cancel a specific request
     */
    fun cancelRequest(requestId: String): Boolean {
        return try {
            val job = activeRequests.remove(requestId)
            if (job != null) {
                job.cancel()
                Log.d(TAG, "Cancelled request: $requestId")
                true
            } else {
                Log.w(TAG, "Request not found for cancellation: $requestId")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling request: $requestId", e)
            false
        }
    }
    
    /**
     * Check if current coroutine context is active
     */
    suspend fun isContextActive(): Boolean {
        val job = currentCoroutineContext()[Job]
        return job?.isActive == true
    }
    
    /**
     * Safe cancellation check with logging
     */
    suspend fun checkCancellation(requestId: String): Boolean {
        if (!isContextActive()) {
            Log.d(TAG, "Request $requestId cancelled by client")
            return true
        }
        return false
    }
    
    /**
     * Handle cancellation exception consistently
     */
    fun handleCancellationException(e: CancellationException, requestId: String) {
        Log.d(TAG, "Request $requestId cancelled normally")
        throw e // Re-throw to propagate cancellation
    }
    
    /**
     * Clean up all active requests
     */
    fun cleanup() {
        activeRequests.values.forEach { job ->
            try {
                job.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "Error cancelling job during cleanup", e)
            }
        }
        activeRequests.clear()
        Log.d(TAG, "CancellationManager cleaned up")
    }
    
    /**
     * Get active request count
     */
    fun getActiveRequestCount(): Int = activeRequests.size
} 