package com.mtkresearch.breezeapp.engine.connection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import com.mtkresearch.breezeapp.engine.model.EnginePackageInfo
import java.lang.ref.WeakReference

/**
 * BroadcastReceiver that monitors the BreezeApp-engine package lifecycle.
 *
 * Listens for package installation, update, and removal events and notifies
 * registered listeners. Uses weak references to prevent memory leaks.
 *
 * Usage:
 * ```kotlin
 * val packageMonitor = PackageMonitor(context)
 *
 * // Register listener
 * packageMonitor.addListener(listener)
 *
 * // Register receiver (in onResume)
 * packageMonitor.register()
 *
 * // Unregister receiver (in onPause)
 * packageMonitor.unregister()
 * ```
 */
class PackageMonitor(context: Context) : BroadcastReceiver() {

    private val contextRef = WeakReference(context)
    private val listeners = mutableListOf<WeakReference<PackageMonitorListener>>()
    private var isRegistered = false
    private var lastKnownVersion: String? = null

    companion object {
        private const val TAG = "PackageMonitor"
        private const val ENGINE_PACKAGE_NAME = EnginePackageInfo.ENGINE_PACKAGE_NAME
    }

    /**
     * Add a listener to receive package lifecycle events.
     *
     * @param listener The listener to add. Stored as weak reference to prevent leaks.
     */
    fun addListener(listener: PackageMonitorListener) {
        // Remove any existing weak references to the same listener
        listeners.removeAll { it.get() == listener || it.get() == null }
        listeners.add(WeakReference(listener))
        Log.d(TAG, "Listener added. Total listeners: ${listeners.size}")
    }

    /**
     * Remove a listener from receiving package lifecycle events.
     *
     * @param listener The listener to remove
     */
    fun removeListener(listener: PackageMonitorListener) {
        listeners.removeAll { it.get() == listener || it.get() == null }
        Log.d(TAG, "Listener removed. Total listeners: ${listeners.size}")
    }

    /**
     * Register this BroadcastReceiver to start monitoring package events.
     *
     * Should be called in onResume() to ensure monitoring is active when
     * the app is in the foreground.
     */
    fun register() {
        val context = contextRef.get() ?: run {
            Log.w(TAG, "Context is null, cannot register receiver")
            return
        }

        if (isRegistered) {
            Log.d(TAG, "Receiver already registered")
            return
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }

        context.registerReceiver(this, filter)
        isRegistered = true

        // Store current version for update detection
        lastKnownVersion = getInstalledEngineVersion(context)

        Log.d(TAG, "PackageMonitor registered. Last known version: $lastKnownVersion")
    }

    /**
     * Unregister this BroadcastReceiver to stop monitoring package events.
     *
     * Should be called in onPause() to avoid leaking the receiver.
     */
    fun unregister() {
        val context = contextRef.get() ?: run {
            Log.w(TAG, "Context is null, cannot unregister receiver")
            return
        }

        if (!isRegistered) {
            Log.d(TAG, "Receiver not registered")
            return
        }

        try {
            context.unregisterReceiver(this)
            isRegistered = false
            Log.d(TAG, "PackageMonitor unregistered")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver was not registered", e)
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Log.w(TAG, "Received null context or intent")
            return
        }

        val packageName = intent.data?.schemeSpecificPart
        if (packageName != ENGINE_PACKAGE_NAME) {
            // Not our package, ignore
            return
        }

        Log.d(TAG, "Received package event: ${intent.action} for $packageName")

        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> handlePackageAdded(context)
            Intent.ACTION_PACKAGE_REPLACED -> handlePackageReplaced(context)
            Intent.ACTION_PACKAGE_REMOVED -> handlePackageRemoved()
        }
    }

    /**
     * Handle ACTION_PACKAGE_ADDED event.
     *
     * Checks if the engine was newly installed (not an update) and notifies listeners.
     */
    private fun handlePackageAdded(context: Context) {
        val packageInfo = getEnginePackageInfo(context)
        if (packageInfo != null && packageInfo.isInstalled) {
            Log.d(TAG, "Engine installed: ${packageInfo.versionName}")
            notifyListeners { it.onEngineInstalled(packageInfo) }
            lastKnownVersion = packageInfo.versionName
        }
    }

    /**
     * Handle ACTION_PACKAGE_REPLACED event.
     *
     * Detects engine updates and notifies listeners with old and new versions.
     */
    private fun handlePackageReplaced(context: Context) {
        val newPackageInfo = getEnginePackageInfo(context)
        val oldVersion = lastKnownVersion ?: "unknown"

        if (newPackageInfo != null && newPackageInfo.isInstalled) {
            val newVersion = newPackageInfo.versionName
            Log.d(TAG, "Engine updated: $oldVersion → $newVersion")
            notifyListeners { it.onEngineUpdated(oldVersion, newVersion) }
            lastKnownVersion = newVersion
        }
    }

    /**
     * Handle ACTION_PACKAGE_REMOVED event.
     *
     * Notifies listeners that the engine was uninstalled.
     */
    private fun handlePackageRemoved() {
        val removedVersion = lastKnownVersion ?: "unknown"
        Log.d(TAG, "Engine removed: $removedVersion")
        notifyListeners { it.onEngineRemoved(removedVersion) }
        lastKnownVersion = null
    }

    /**
     * Notify all registered listeners with a callback action.
     *
     * Automatically removes listeners that have been garbage collected.
     */
    private fun notifyListeners(action: (PackageMonitorListener) -> Unit) {
        // Remove dead weak references and notify live listeners
        listeners.removeAll { weakRef ->
            val listener = weakRef.get()
            if (listener == null) {
                true // Remove null reference
            } else {
                try {
                    action(listener)
                    false // Keep valid reference
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying listener", e)
                    false
                }
            }
        }
    }

    /**
     * Get the currently installed engine version.
     *
     * @return Version name if installed, null otherwise
     */
    private fun getInstalledEngineVersion(context: Context): String? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(ENGINE_PACKAGE_NAME, 0)
            packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Get detailed package information for the engine.
     *
     * @return EnginePackageInfo if installed, null otherwise
     */
    private fun getEnginePackageInfo(context: Context): EnginePackageInfo? {
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
            null
        }
    }
}
