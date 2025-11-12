package com.mtkresearch.breezeapp.engine.connection

import com.mtkresearch.breezeapp.engine.model.EnginePackageInfo

/**
 * Callback interface for monitoring BreezeApp-engine package lifecycle events.
 *
 * This interface allows clients to receive notifications when the engine package
 * is installed, updated, or removed. Implementations should be prepared to handle
 * these callbacks on the main thread.
 *
 * Usage:
 * ```kotlin
 * val listener = object : PackageMonitorListener {
 *     override fun onEngineInstalled(packageInfo: EnginePackageInfo) {
 *         // Handle engine installation
 *         Log.d(TAG, "Engine installed: ${packageInfo.versionName}")
 *     }
 *
 *     override fun onEngineUpdated(oldVersion: String, newVersion: String) {
 *         // Handle engine update
 *         Log.d(TAG, "Engine updated from $oldVersion to $newVersion")
 *     }
 *
 *     override fun onEngineRemoved(lastVersion: String) {
 *         // Handle engine removal
 *         Log.d(TAG, "Engine removed: version $lastVersion")
 *     }
 * }
 * ```
 */
interface PackageMonitorListener {

    /**
     * Called when the BreezeApp-engine package is first installed.
     *
     * @param packageInfo Metadata about the newly installed package, including
     *                    version name, version code, and installation timestamp.
     */
    fun onEngineInstalled(packageInfo: EnginePackageInfo)

    /**
     * Called when the BreezeApp-engine package is updated to a new version.
     *
     * This callback is triggered when an existing engine installation is replaced
     * with a newer version. Clients should rebind to the service to ensure they're
     * using the latest version.
     *
     * @param oldVersion The previous version name (e.g., "1.4.0")
     * @param newVersion The new version name (e.g., "1.5.0")
     */
    fun onEngineUpdated(oldVersion: String, newVersion: String)

    /**
     * Called when the BreezeApp-engine package is uninstalled.
     *
     * This callback is triggered when the engine package is removed from the device.
     * Clients should unbind from the service and show appropriate UI to guide users
     * to reinstall the engine.
     *
     * @param lastVersion The version name of the engine before it was removed
     */
    fun onEngineRemoved(lastVersion: String)
}
