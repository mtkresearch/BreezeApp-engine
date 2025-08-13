package com.mtkresearch.breezeapp.engine.core

import android.util.Log
import com.mtkresearch.breezeapp.engine.model.InferenceResult
import com.mtkresearch.breezeapp.engine.model.RunnerError
import kotlinx.coroutines.CancellationException

/**
 * ExceptionHandler - Unified exception handling strategy
 * 
 * This class provides consistent exception handling across the entire
 * BreezeApp Engine system, following the same patterns for different
 * types of exceptions.
 */
object ExceptionHandler {
    
    private const val TAG = "ExceptionHandler"
    
    /**
     * Handle exceptions in a consistent way
     */
    fun handleException(e: Exception, requestId: String, operation: String): InferenceResult {
        return when (e) {
            is CancellationException -> {
                Log.d(TAG, "$operation cancelled normally for request: $requestId")
                throw e // Re-throw to propagate cancellation
            }
            is SecurityException -> {
                Log.e(TAG, "Permission denied for $operation: $requestId", e)
                InferenceResult.error(RunnerError.invalidInput("Permission denied: ${e.message}"))
            }
            is IllegalArgumentException -> {
                Log.e(TAG, "Invalid input for $operation: $requestId", e)
                InferenceResult.error(RunnerError.invalidInput(e.message ?: "Invalid input"))
            }
            else -> {
                Log.e(TAG, "Runtime error in $operation: $requestId", e)
                InferenceResult.error(RunnerError.runtimeError(e.message ?: "Unknown error", e))
            }
        }
    }
    
    /**
     * Handle exceptions in Flow context
     */
    fun handleFlowException(e: Exception, requestId: String, operation: String) {
        when (e) {
            is CancellationException -> {
                Log.d(TAG, "$operation Flow cancelled normally for request: $requestId")
                throw e // Re-throw to propagate cancellation
            }
            else -> {
                Log.e(TAG, "Flow error in $operation: $requestId", e)
                // Let the Flow handle the error emission
            }
        }
    }
    
    /**
     * Check if exception is a normal cancellation
     */
    fun isNormalCancellation(e: Exception): Boolean {
        return e is CancellationException
    }
} 