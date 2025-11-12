package com.mtkresearch.breezeapp.engine.connection

import android.util.Log
import com.mtkresearch.breezeapp.engine.model.EngineConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Manager for handling in-flight operations during engine reconnection.
 *
 * When the engine is reconnecting (due to package update/replacement), this manager:
 * 1. Cancels all active AI inference operations
 * 2. Notifies registered listeners about the cancellation
 * 3. Prevents new operations from starting until reconnection completes
 *
 * Integration with AIEngineManager:
 * - Observes EngineConnectionState
 * - Triggers cancellation when state becomes Reconnecting
 * - Can be extended to use existing CancellationManager
 *
 * Usage:
 * ```kotlin
 * class MyService {
 *     private val inFlightManager = InFlightOperationManager(
 *         connectionHelper.connectionState
 *     )
 *
 *     init {
 *         // Register cancellation callback
 *         inFlightManager.addCancellationListener { reason ->
 *             Log.d(TAG, "Operations cancelled: $reason")
 *             // Notify UI, clear pending requests, etc.
 *         }
 *     }
 *
 *     fun cleanup() {
 *         inFlightManager.destroy()
 *     }
 * }
 * ```
 *
 * Implements T030: Cancel in-flight operations when state becomes Reconnecting
 */
class InFlightOperationManager(
    private val connectionState: StateFlow<EngineConnectionState>
) {
    private val cancellationListeners = mutableListOf<WeakReference<(String) -> Unit>>()
    private var scope: CoroutineScope? = null
    private var lastState: EngineConnectionState? = null

    // Track if operations are currently allowed
    private var operationsAllowed = true

    companion object {
        private const val TAG = "InFlightOpManager"
        private const val RECONNECTION_REASON = "Engine is reconnecting after update"
        private const val ERROR_REASON = "Engine connection failed"
        private const val REMOVAL_REASON = "Engine was removed"
    }

    init {
        startObserving()
    }

    /**
     * Start observing connection state for cancellation triggers.
     */
    private fun startObserving() {
        scope = CoroutineScope(Dispatchers.Main + Job())

        scope?.launch {
            connectionState.collect { state ->
                handleStateChange(state)
            }
        }
    }

    /**
     * Handle connection state changes and trigger cancellations.
     *
     * T030: Cancel operations when entering Reconnecting state
     */
    private fun handleStateChange(newState: EngineConnectionState) {
        val previousState = lastState
        lastState = newState

        when (newState) {
            is EngineConnectionState.Reconnecting -> {
                // T030: Cancel all in-flight operations
                if (previousState is EngineConnectionState.Connected) {
                    Log.w(TAG, "Engine reconnecting - cancelling in-flight operations")
                    operationsAllowed = false
                    notifyCancellation(RECONNECTION_REASON)
                }
            }

            is EngineConnectionState.Connected -> {
                // Reconnection complete - allow operations again
                Log.d(TAG, "Engine reconnected - operations allowed")
                operationsAllowed = true
            }

            is EngineConnectionState.Error -> {
                // Connection failed - cancel operations
                if (previousState is EngineConnectionState.Connected ||
                    previousState is EngineConnectionState.Reconnecting
                ) {
                    Log.w(TAG, "Engine connection error - cancelling operations")
                    operationsAllowed = false
                    notifyCancellation(ERROR_REASON)
                }
            }

            is EngineConnectionState.Disconnected -> {
                // Check if this is due to engine removal
                if (previousState is EngineConnectionState.Connected) {
                    Log.w(TAG, "Engine disconnected - cancelling operations")
                    operationsAllowed = false
                    notifyCancellation(REMOVAL_REASON)
                }
            }

            is EngineConnectionState.Connecting -> {
                // Initial connection attempt - operations not yet allowed
                operationsAllowed = false
            }
        }
    }

    /**
     * Notify all registered listeners about operation cancellation.
     */
    private fun notifyCancellation(reason: String) {
        Log.d(TAG, "Notifying ${cancellationListeners.size} listeners: $reason")

        cancellationListeners.removeAll { weakRef ->
            val listener = weakRef.get()
            if (listener == null) {
                true // Remove dead reference
            } else {
                try {
                    listener(reason)
                    false // Keep valid reference
                } catch (e: Exception) {
                    Log.e(TAG, "Error in cancellation listener", e)
                    false
                }
            }
        }
    }

    /**
     * Register a listener for cancellation events.
     *
     * The listener receives a string reason for the cancellation.
     * Stored as weak reference to prevent memory leaks.
     *
     * @param listener Callback invoked when operations are cancelled
     */
    fun addCancellationListener(listener: (String) -> Unit) {
        cancellationListeners.removeAll { it.get() == null } // Cleanup
        cancellationListeners.add(WeakReference(listener))
        Log.d(TAG, "Cancellation listener added. Total: ${cancellationListeners.size}")
    }

    /**
     * Remove a previously registered listener.
     *
     * @param listener Listener to remove
     */
    fun removeCancellationListener(listener: (String) -> Unit) {
        cancellationListeners.removeAll { it.get() == listener || it.get() == null }
        Log.d(TAG, "Cancellation listener removed. Total: ${cancellationListeners.size}")
    }

    /**
     * Check if new operations are currently allowed.
     *
     * @return true if connected and operations allowed, false otherwise
     */
    fun areOperationsAllowed(): Boolean {
        return operationsAllowed && connectionState.value is EngineConnectionState.Connected
    }

    /**
     * Get current operation status as string (for debugging/UI).
     */
    fun getOperationStatus(): String {
        return when (connectionState.value) {
            is EngineConnectionState.Connected -> "Operations allowed"
            is EngineConnectionState.Reconnecting -> "Reconnecting - operations paused"
            is EngineConnectionState.Error -> "Connection error - operations blocked"
            is EngineConnectionState.Connecting -> "Connecting - operations pending"
            is EngineConnectionState.Disconnected -> "Disconnected - operations blocked"
        }
    }

    /**
     * Manually trigger cancellation (for testing or manual intervention).
     *
     * @param reason Custom reason for cancellation
     */
    fun triggerCancellation(reason: String = "Manual cancellation") {
        Log.w(TAG, "Manual cancellation triggered: $reason")
        operationsAllowed = false
        notifyCancellation(reason)
    }

    /**
     * Clean up resources.
     */
    fun destroy() {
        Log.d(TAG, "Destroying InFlightOperationManager")
        scope?.cancel()
        scope = null
        cancellationListeners.clear()
    }
}
