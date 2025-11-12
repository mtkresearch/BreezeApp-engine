package com.mtkresearch.breezeapp.engine.connection

import android.content.Context
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import com.mtkresearch.breezeapp.engine.model.BindingConfig
import com.mtkresearch.breezeapp.engine.model.ConnectionEvent
import com.mtkresearch.breezeapp.engine.model.EngineConnectionState
import com.mtkresearch.breezeapp.engine.ui.ReconnectionSnackbarManager
import com.mtkresearch.breezeapp.engine.ui.dialogs.InstallEngineDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Helper class for managing engine connection lifecycle and UI integration.
 *
 * This class simplifies engine connection management for client apps by:
 * 1. Detecting missing engine on initialization
 * 2. Showing appropriate UI (install dialog) when engine not found
 * 3. Managing connection state and reconnection
 * 4. Providing lifecycle-aware monitoring
 *
 * Usage in client app (MainActivity or Application):
 * ```kotlin
 * class MainActivity : FragmentActivity() {
 *     private lateinit var engineHelper: EngineConnectionHelper
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *
 *         // Initialize helper
 *         engineHelper = EngineConnectionHelper(this)
 *
 *         // Check engine and show install dialog if needed
 *         engineHelper.checkAndConnect(this) { state ->
 *             when (state) {
 *                 is EngineConnectionState.Connected -> {
 *                     // Engine ready, proceed with app
 *                     Log.d(TAG, "Engine connected: ${state.version}")
 *                 }
 *                 is EngineConnectionState.Error -> {
 *                     // Handle error
 *                     Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
 *                 }
 *                 // ... handle other states
 *             }
 *         }
 *     }
 *
 *     override fun onResume() {
 *         super.onResume()
 *         engineHelper.onResume()
 *     }
 *
 *     override fun onPause() {
 *         super.onPause()
 *         engineHelper.onPause()
 *     }
 *
 *     override fun onDestroy() {
 *         super.onDestroy()
 *         engineHelper.onDestroy()
 *     }
 * }
 * ```
 *
 * Implements T019 and T020:
 * - T019: Integrate with client app initialization
 * - T020: Show install dialog when engine not found
 */
class EngineConnectionHelper(
    context: Context,
    config: BindingConfig = BindingConfig()
) {
    private val connectionManager: EngineConnectionManager = EngineConnectionManagerImpl(context, config)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Observable connection state.
     * Client apps can collect this Flow to react to connection changes.
     */
    val connectionState: StateFlow<EngineConnectionState> = connectionManager.connectionState

    /**
     * Observable connection events for logging and analytics.
     */
    val connectionEvents: StateFlow<ConnectionEvent?> = connectionManager.connectionEvents

    /**
     * Check if engine is installed and connect.
     * Shows InstallEngineDialog if engine not found.
     *
     * @param activity FragmentActivity for showing dialogs
     * @param onStateChanged Callback for connection state changes
     * @return true if check completed, false if dialog shown
     */
    fun checkAndConnect(
        activity: FragmentActivity,
        onStateChanged: ((EngineConnectionState) -> Unit)? = null
    ): Boolean {
        // Check if engine is installed
        if (!connectionManager.isEngineInstalled()) {
            // Show install dialog
            showInstallDialog(activity)
            return false
        }

        // Engine installed, attempt connection
        val connected = connectionManager.connect()

        if (connected && onStateChanged != null) {
            // Observe state changes
            scope.launch {
                connectionState.collect { state ->
                    onStateChanged(state)
                }
            }
        }

        return connected
    }

    /**
     * Show the install engine dialog.
     *
     * @param activity FragmentActivity for showing dialog
     */
    fun showInstallDialog(activity: FragmentActivity) {
        val dialog = InstallEngineDialog.newInstance()
        dialog.show(activity.supportFragmentManager, "InstallEngineDialog")
    }

    /**
     * Check if engine is currently installed.
     *
     * @return true if engine package is found, false otherwise
     */
    fun isEngineInstalled(): Boolean {
        return connectionManager.isEngineInstalled()
    }

    /**
     * Manually trigger connection to engine.
     *
     * @return true if connection attempt started, false if engine not installed
     */
    fun connect(): Boolean {
        return connectionManager.connect()
    }

    /**
     * Disconnect from engine service.
     */
    fun disconnect() {
        connectionManager.disconnect()
    }

    /**
     * Manually trigger reconnection.
     * Useful after detecting engine updates or after errors.
     */
    fun reconnect() {
        connectionManager.reconnect()
    }

    /**
     * Called in Activity.onResume() to start monitoring engine lifecycle.
     */
    fun onResume() {
        connectionManager.startMonitoring()
    }

    /**
     * Called in Activity.onPause() to stop monitoring engine lifecycle.
     */
    fun onPause() {
        connectionManager.stopMonitoring()
    }

    /**
     * Called in Activity.onDestroy() to clean up resources.
     */
    fun onDestroy() {
        connectionManager.stopMonitoring()
        scope.cancel()
    }

    /**
     * Add a callback for connection events.
     *
     * @param callback Callback to receive events
     */
    fun addEventCallback(callback: (ConnectionEvent) -> Unit) {
        connectionManager.addEventCallback(callback)
    }

    /**
     * Remove a previously registered event callback.
     *
     * @param callback Callback to remove
     */
    fun removeEventCallback(callback: (ConnectionEvent) -> Unit) {
        connectionManager.removeEventCallback(callback)
    }

    /**
     * Update connection configuration at runtime.
     *
     * @param config New configuration
     */
    fun updateConfig(config: BindingConfig) {
        connectionManager.updateConfig(config)
    }

    /**
     * Get detailed engine package information.
     *
     * @return EnginePackageInfo or null if not installed
     */
    fun getEnginePackageInfo() = connectionManager.getEnginePackageInfo()

    /**
     * Create a ReconnectionSnackbarManager for automatic Snackbar display.
     *
     * Phase 4 (T027-T029): Shows/hides Snackbar based on connection state.
     *
     * @param rootView Root view for Snackbar attachment (usually findViewById(android.R.id.content))
     * @param lifecycle Activity/Fragment lifecycle for automatic cleanup
     * @return Configured ReconnectionSnackbarManager
     */
    fun createSnackbarManager(
        rootView: View,
        lifecycle: Lifecycle
    ): ReconnectionSnackbarManager {
        return ReconnectionSnackbarManager(
            rootView,
            connectionState,
            lifecycle
        )
    }

    /**
     * Create an InFlightOperationManager for handling operation cancellation.
     *
     * Phase 4 (T030): Cancels operations during reconnection.
     *
     * @return Configured InFlightOperationManager
     */
    fun createInFlightOperationManager(): InFlightOperationManager {
        return InFlightOperationManager(connectionState)
    }
}
