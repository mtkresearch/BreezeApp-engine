package com.mtkresearch.breezeapp.engine.connection

import com.mtkresearch.breezeapp.engine.model.BindingConfig
import com.mtkresearch.breezeapp.engine.model.ConnectionEvent
import com.mtkresearch.breezeapp.engine.model.EngineConnectionState
import com.mtkresearch.breezeapp.engine.model.EnginePackageInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for managing AIDL service binding to the BreezeApp-engine.
 *
 * This manager handles:
 * - Automatic detection of engine package installation/updates/removal
 * - AIDL service binding with retry logic
 * - Connection state management and observation
 * - Automatic reconnection after engine updates
 *
 * Usage:
 * ```kotlin
 * val manager = EngineConnectionManagerImpl(context, config)
 *
 * // Observe connection state
 * lifecycleScope.launch {
 *     manager.connectionState.collect { state ->
 *         when (state) {
 *             is EngineConnectionState.Connected -> {
 *                 // Use engine service
 *                 Log.d(TAG, "Connected to engine: ${state.version}")
 *             }
 *             is EngineConnectionState.Error -> {
 *                 // Show error UI
 *                 showErrorDialog(state.message)
 *             }
 *             // ... handle other states
 *         }
 *     }
 * }
 *
 * // Start monitoring
 * manager.startMonitoring()
 *
 * // Connect to engine
 * manager.connect()
 *
 * // Stop monitoring when done
 * manager.stopMonitoring()
 * ```
 */
interface EngineConnectionManager {

    /**
     * Observable connection state using Kotlin Flow.
     *
     * Emits state changes whenever the connection status changes:
     * - Disconnected: Initial state or after unbinding
     * - Connecting: Attempting initial bind
     * - Connected: Successfully bound to engine
     * - Reconnecting: Rebinding after engine update
     * - Error: Binding failed after retries
     */
    val connectionState: StateFlow<EngineConnectionState>

    /**
     * Observable connection events for logging and analytics.
     *
     * Emits events like:
     * - EngineDetected, EngineNotFound
     * - PackageInstalled, PackageUpdated, PackageRemoved
     * - BindingStarted, BindingSucceeded, BindingFailed
     * - ReconnectionStarted, ReconnectionCompleted
     */
    val connectionEvents: StateFlow<ConnectionEvent?>

    /**
     * Start monitoring the engine package lifecycle.
     *
     * This should be called in Activity.onResume() or when the manager
     * needs to actively monitor for engine changes. Registers internal
     * BroadcastReceiver to detect package events.
     */
    fun startMonitoring()

    /**
     * Stop monitoring the engine package lifecycle.
     *
     * This should be called in Activity.onPause() or when monitoring
     * is no longer needed. Unregisters internal BroadcastReceiver.
     */
    fun stopMonitoring()

    /**
     * Attempt to connect to the engine service.
     *
     * If the engine is not installed, emits EngineConnectionState.Error
     * with appropriate message. If binding fails, retries once after
     * the configured retry delay (default 2 seconds).
     *
     * @return true if connection attempt started, false if engine not installed
     */
    fun connect(): Boolean

    /**
     * Disconnect from the engine service.
     *
     * Unbinds from the AIDL service and transitions to Disconnected state.
     * Safe to call even if not currently connected.
     */
    fun disconnect()

    /**
     * Attempt to reconnect to the engine service.
     *
     * Used internally after detecting engine updates, and can be called
     * externally to manually retry connection after errors.
     *
     * Transitions to Reconnecting state during the reconnection attempt.
     */
    fun reconnect()

    /**
     * Check if the engine package is currently installed.
     *
     * @return true if engine package is found on device, false otherwise
     */
    fun isEngineInstalled(): Boolean

    /**
     * Get detailed information about the installed engine package.
     *
     * @return EnginePackageInfo if installed, null otherwise
     */
    fun getEnginePackageInfo(): EnginePackageInfo?

    /**
     * Update the binding configuration at runtime.
     *
     * Allows changing retry delay, timeout, and other parameters
     * without recreating the manager.
     *
     * @param config New configuration to apply
     */
    fun updateConfig(config: BindingConfig)

    /**
     * Register a callback for connection events.
     *
     * In addition to observing connectionEvents Flow, clients can
     * register callbacks for specific event handling.
     *
     * @param callback Callback to receive events
     */
    fun addEventCallback(callback: (ConnectionEvent) -> Unit)

    /**
     * Unregister a previously registered event callback.
     *
     * @param callback Callback to remove
     */
    fun removeEventCallback(callback: (ConnectionEvent) -> Unit)
}
