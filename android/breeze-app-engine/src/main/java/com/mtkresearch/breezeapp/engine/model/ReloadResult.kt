package com.mtkresearch.breezeapp.engine.model

/**
 * Represents the outcome of an asynchronous operation, typically one that involves
 * reloading or re-initializing components.
 */
sealed class ReloadResult {
    /**
     * Indicates that the operation completed successfully.
     */
    data object Success : ReloadResult()

    /**
     * Indicates that the operation failed.
     * @param error The exception or throwable that caused the failure.
     */
    data class Failure(val error: Throwable) : ReloadResult()
}
