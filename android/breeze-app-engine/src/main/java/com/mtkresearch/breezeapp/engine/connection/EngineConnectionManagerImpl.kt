package com.mtkresearch.breezeapp.engine.connection

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mtkresearch.breezeapp.engine.model.BindingConfig
import com.mtkresearch.breezeapp.engine.model.ConnectionEvent
import com.mtkresearch.breezeapp.engine.model.EngineConnectionState
import com.mtkresearch.breezeapp.engine.model.EnginePackageInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/**
 * Implementation of EngineConnectionManager.
 *
 * Manages engine package monitoring, AIDL binding, and connection state.
 * Uses StateFlow for reactive state observation and weak references for callbacks.
 *
 * @param context Application or Activity context
 * @param config Initial binding configuration (retry delays, timeouts, etc.)
 */
class EngineConnectionManagerImpl(
    context: Context,
    private var config: BindingConfig = BindingConfig()
) : EngineConnectionManager, PackageMonitorListener {

    private val contextRef = WeakReference(context)
    private val packageMonitor = PackageMonitor(context)
    private val handler = Handler(Looper.getMainLooper())

    // State management
    private val _connectionState = MutableStateFlow<EngineConnectionState>(
        EngineConnectionState.Disconnected
    )
    override val connectionState: StateFlow<EngineConnectionState> = _connectionState.asStateFlow()

    private val _connectionEvents = MutableStateFlow<ConnectionEvent?>(null)
    override val connectionEvents: StateFlow<ConnectionEvent?> = _connectionEvents.asStateFlow()

    // Callbacks
    private val eventCallbacks = mutableListOf<WeakReference<(ConnectionEvent) -> Unit>>()

    // Retry tracking
    private var retryCount = 0
    private var reconnectionStartTime = 0L
    private var pendingRetryRunnable: Runnable? = null

    // Service binding state
    private var isServiceBound = false

    companion object {
        private const val TAG = "EngineConnectionMgr"
        private const val ENGINE_PACKAGE_NAME = EnginePackageInfo.ENGINE_PACKAGE_NAME
    }

    init {
        packageMonitor.addListener(this)
        Log.d(TAG, "EngineConnectionManager initialized with config: $config")
    }

    // ========== Lifecycle Management ==========

    override fun startMonitoring() {
        Log.d(TAG, "Starting engine package monitoring")
        packageMonitor.register()

        // Emit initial detection event
        if (isEngineInstalled()) {
            emitEvent(ConnectionEvent.EngineDetected)
        } else {
            emitEvent(ConnectionEvent.EngineNotFound)
        }
    }

    override fun stopMonitoring() {
        Log.d(TAG, "Stopping engine package monitoring")
        packageMonitor.unregister()

        // Cancel any pending retries
        cancelPendingRetry()
    }

    // ========== Connection Management ==========

    override fun connect(): Boolean {
        val context = contextRef.get()
        if (context == null) {
            Log.w(TAG, "Context is null, cannot connect")
            return false
        }

        if (!isEngineInstalled()) {
            Log.w(TAG, "Engine not installed, cannot connect")
            val errorState = EngineConnectionState.Error(
                message = "BreezeApp Engine is not installed",
                errorCode = "ENGINE_NOT_FOUND",
                timestamp = System.currentTimeMillis()
            )
            _connectionState.value = errorState
            emitEvent(ConnectionEvent.EngineNotFound)
            return false
        }

        Log.d(TAG, "Initiating connection to engine")
        _connectionState.value = EngineConnectionState.Connecting
        emitEvent(ConnectionEvent.BindingStarted)

        // Attempt binding
        retryCount = 0
        performBinding()

        return true
    }

    override fun disconnect() {
        Log.d(TAG, "Disconnecting from engine")

        cancelPendingRetry()

        if (isServiceBound) {
            // TODO: Unbind from AIDL service (integration with existing binding code)
            isServiceBound = false
            Log.d(TAG, "Service unbound")
        }

        _connectionState.value = EngineConnectionState.Disconnected
    }

    override fun reconnect() {
        Log.d(TAG, "Reconnecting to engine")

        // Cancel any pending operations
        cancelPendingRetry()

        // Unbind first if currently bound
        if (isServiceBound) {
            disconnect()
        }

        // Transition to reconnecting state
        _connectionState.value = EngineConnectionState.Reconnecting
        emitEvent(ConnectionEvent.ReconnectionStarted)
        reconnectionStartTime = System.currentTimeMillis()

        // Reset retry count for reconnection
        retryCount = 0

        // Add debounce delay before reconnecting (prevents UI flicker)
        handler.postDelayed({
            performBinding()
        }, config.debounceDelayMs)
    }

    // ========== Package Detection (T012) ==========

    override fun isEngineInstalled(): Boolean {
        val context = contextRef.get() ?: return false

        return try {
            context.packageManager.getPackageInfo(ENGINE_PACKAGE_NAME, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    override fun getEnginePackageInfo(): EnginePackageInfo? {
        val context = contextRef.get() ?: return null

        return try {
            val packageInfo = context.packageManager.getPackageInfo(ENGINE_PACKAGE_NAME, 0)
            EnginePackageInfo(
                packageName = ENGINE_PACKAGE_NAME,
                versionName = packageInfo.versionName ?: "unknown",
                versionCode = packageInfo.longVersionCode,
                isInstalled = true,
                installTime = packageInfo.firstInstallTime,
                updateTime = packageInfo.lastUpdateTime
            )
        } catch (e: PackageManager.NameNotFoundException) {
            Log.d(TAG, "Engine package not found")
            EnginePackageInfo(
                packageName = ENGINE_PACKAGE_NAME,
                versionName = "unknown",
                versionCode = 0,
                isInstalled = false
            )
        }
    }

    // ========== Binding Logic with Retry (T013) ==========

    /**
     * Perform AIDL service binding with timeout and retry logic.
     */
    private fun performBinding() {
        val context = contextRef.get()
        if (context == null) {
            Log.w(TAG, "Context is null, cannot bind")
            handleBindingFailure("Context is null", false)
            return
        }

        Log.d(TAG, "Attempting binding (attempt ${retryCount + 1}/${config.maxReconnectAttempts + 1})")

        // TODO: Integrate with existing AIDL binding code
        // For now, simulate binding behavior

        // Simulate binding success after delay
        handler.postDelayed({
            simulateBindingResult()
        }, 500L) // Simulate network/binding delay
    }

    /**
     * Simulate binding result for testing.
     * TODO: Replace with actual AIDL binding integration.
     */
    private fun simulateBindingResult() {
        // Simulate success for now
        // In real implementation, this would be called from ServiceConnection callbacks

        val engineInfo = getEnginePackageInfo()
        if (engineInfo != null && engineInfo.isInstalled) {
            handleBindingSuccess(engineInfo.versionName)
        } else {
            handleBindingFailure("Engine not found", true)
        }
    }

    /**
     * Handle successful binding.
     */
    private fun handleBindingSuccess(version: String) {
        Log.d(TAG, "Binding succeeded, engine version: $version")

        isServiceBound = true

        // Calculate reconnection duration if applicable
        val wasReconnecting = _connectionState.value is EngineConnectionState.Reconnecting
        if (wasReconnecting) {
            val duration = System.currentTimeMillis() - reconnectionStartTime
            emitEvent(ConnectionEvent.ReconnectionCompleted(duration))
        }

        _connectionState.value = EngineConnectionState.Connected(
            version = version,
            timestamp = System.currentTimeMillis()
        )
        emitEvent(ConnectionEvent.BindingSucceeded(version))
    }

    /**
     * Handle binding failure with retry logic.
     */
    private fun handleBindingFailure(reason: String, shouldRetry: Boolean) {
        Log.w(TAG, "Binding failed: $reason (retry attempt $retryCount/${config.maxReconnectAttempts})")

        val willRetry = shouldRetry && retryCount < config.maxReconnectAttempts
        emitEvent(ConnectionEvent.BindingFailed(reason, willRetry))

        if (willRetry) {
            retryCount++
            scheduleRetry()
        } else {
            // Max retries exceeded or should not retry
            _connectionState.value = EngineConnectionState.Error(
                message = "Failed to connect to engine: $reason",
                errorCode = "BINDING_FAILED",
                timestamp = System.currentTimeMillis()
            )
            retryCount = 0
        }
    }

    /**
     * Schedule retry after configured delay (default 2 seconds).
     */
    private fun scheduleRetry() {
        Log.d(TAG, "Scheduling retry in ${config.retryDelayMs}ms")

        pendingRetryRunnable = Runnable {
            Log.d(TAG, "Executing scheduled retry")
            performBinding()
        }

        handler.postDelayed(pendingRetryRunnable!!, config.retryDelayMs)
    }

    /**
     * Cancel any pending retry attempts.
     */
    private fun cancelPendingRetry() {
        pendingRetryRunnable?.let {
            handler.removeCallbacks(it)
            pendingRetryRunnable = null
            Log.d(TAG, "Cancelled pending retry")
        }
    }

    // ========== Package Lifecycle Callbacks ==========

    override fun onEngineInstalled(packageInfo: EnginePackageInfo) {
        Log.d(TAG, "Engine installed: ${packageInfo.versionName}")
        emitEvent(ConnectionEvent.PackageInstalled(packageInfo.versionName))

        // Automatically connect if we were in error state due to missing engine
        if (_connectionState.value is EngineConnectionState.Error) {
            connect()
        }
    }

    override fun onEngineUpdated(oldVersion: String, newVersion: String) {
        Log.d(TAG, "Engine updated: $oldVersion → $newVersion")
        emitEvent(ConnectionEvent.PackageUpdated(oldVersion, newVersion))

        // Automatically reconnect to use new version
        reconnect()
    }

    override fun onEngineRemoved(lastVersion: String) {
        Log.d(TAG, "Engine removed: $lastVersion")
        emitEvent(ConnectionEvent.PackageRemoved(lastVersion))

        // Disconnect and transition to error state
        disconnect()
        _connectionState.value = EngineConnectionState.Error(
            message = "BreezeApp Engine was removed",
            errorCode = "ENGINE_REMOVED",
            timestamp = System.currentTimeMillis()
        )
    }

    // ========== Configuration ==========

    override fun updateConfig(config: BindingConfig) {
        Log.d(TAG, "Updating configuration: $config")
        this.config = config
    }

    // ========== Event Callbacks ==========

    override fun addEventCallback(callback: (ConnectionEvent) -> Unit) {
        eventCallbacks.removeAll { it.get() == null } // Cleanup dead references
        eventCallbacks.add(WeakReference(callback))
        Log.d(TAG, "Event callback added. Total callbacks: ${eventCallbacks.size}")
    }

    override fun removeEventCallback(callback: (ConnectionEvent) -> Unit) {
        eventCallbacks.removeAll { it.get() == callback || it.get() == null }
        Log.d(TAG, "Event callback removed. Total callbacks: ${eventCallbacks.size}")
    }

    /**
     * Emit a connection event to all registered callbacks and the StateFlow.
     */
    private fun emitEvent(event: ConnectionEvent) {
        Log.d(TAG, "Event: ${event::class.simpleName}")
        _connectionEvents.value = event

        // Notify callbacks
        eventCallbacks.removeAll { weakRef ->
            val callback = weakRef.get()
            if (callback == null) {
                true // Remove dead reference
            } else {
                try {
                    callback(event)
                    false // Keep valid reference
                } catch (e: Exception) {
                    Log.e(TAG, "Error in event callback", e)
                    false
                }
            }
        }
    }
}
