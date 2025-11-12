package com.mtkresearch.breezeapp.engine.connection

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import com.mtkresearch.breezeapp.engine.model.EnginePackageInfo
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for PackageMonitor BroadcastReceiver.
 * Tests listener management, package event filtering, and callback invocations.
 */
class PackageMonitorTest {

    private lateinit var context: Context
    private lateinit var packageManager: PackageManager
    private lateinit var packageMonitor: PackageMonitor
    private lateinit var listener: PackageMonitorListener

    companion object {
        private const val ENGINE_PACKAGE_NAME = "com.mtkresearch.breezeapp.engine"
        private const val OTHER_PACKAGE_NAME = "com.example.other"
        private const val VERSION_1_4_0 = "1.4.0"
        private const val VERSION_1_5_0 = "1.5.0"
    }

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        packageManager = mockk(relaxed = true)
        listener = mockk(relaxed = true)

        every { context.packageManager } returns packageManager

        packageMonitor = PackageMonitor(context)
    }

    @After
    fun teardown() {
        clearAllMocks()
    }

    // ========== Listener Management Tests ==========

    @Test
    fun `addListener should store listener`() {
        packageMonitor.addListener(listener)

        // Trigger an event to verify listener was added
        val intent = createPackageIntent(Intent.ACTION_PACKAGE_ADDED, ENGINE_PACKAGE_NAME)
        mockEngineInstalled(VERSION_1_5_0)

        packageMonitor.onReceive(context, intent)

        verify { listener.onEngineInstalled(any()) }
    }

    @Test
    fun `removeListener should stop notifications`() {
        packageMonitor.addListener(listener)
        packageMonitor.removeListener(listener)

        val intent = createPackageIntent(Intent.ACTION_PACKAGE_ADDED, ENGINE_PACKAGE_NAME)
        mockEngineInstalled(VERSION_1_5_0)

        packageMonitor.onReceive(context, intent)

        verify(exactly = 0) { listener.onEngineInstalled(any()) }
    }

    @Test
    fun `addListener should remove duplicate listeners`() {
        val listener1 = mockk<PackageMonitorListener>(relaxed = true)

        packageMonitor.addListener(listener1)
        packageMonitor.addListener(listener1) // Add same listener again

        val intent = createPackageIntent(Intent.ACTION_PACKAGE_ADDED, ENGINE_PACKAGE_NAME)
        mockEngineInstalled(VERSION_1_5_0)

        packageMonitor.onReceive(context, intent)

        // Should only notify once (no duplicates)
        verify(exactly = 1) { listener1.onEngineInstalled(any()) }
    }

    @Test
    fun `multiple listeners should all receive notifications`() {
        val listener1 = mockk<PackageMonitorListener>(relaxed = true)
        val listener2 = mockk<PackageMonitorListener>(relaxed = true)

        packageMonitor.addListener(listener1)
        packageMonitor.addListener(listener2)

        val intent = createPackageIntent(Intent.ACTION_PACKAGE_ADDED, ENGINE_PACKAGE_NAME)
        mockEngineInstalled(VERSION_1_5_0)

        packageMonitor.onReceive(context, intent)

        verify { listener1.onEngineInstalled(any()) }
        verify { listener2.onEngineInstalled(any()) }
    }

    // ========== Package Event Filtering Tests ==========

    @Test
    fun `onReceive should ignore events for other packages`() {
        packageMonitor.addListener(listener)

        val intent = createPackageIntent(Intent.ACTION_PACKAGE_ADDED, OTHER_PACKAGE_NAME)

        packageMonitor.onReceive(context, intent)

        verify(exactly = 0) { listener.onEngineInstalled(any()) }
    }

    @Test
    fun `onReceive should handle null context gracefully`() {
        packageMonitor.addListener(listener)

        val intent = createPackageIntent(Intent.ACTION_PACKAGE_ADDED, ENGINE_PACKAGE_NAME)

        packageMonitor.onReceive(null, intent)

        verify(exactly = 0) { listener.onEngineInstalled(any()) }
    }

    @Test
    fun `onReceive should handle null intent gracefully`() {
        packageMonitor.addListener(listener)

        packageMonitor.onReceive(context, null)

        verify(exactly = 0) { listener.onEngineInstalled(any()) }
    }

    @Test
    fun `onReceive should handle intent with null data`() {
        packageMonitor.addListener(listener)

        val intent = Intent(Intent.ACTION_PACKAGE_ADDED)
        // No data URI set

        packageMonitor.onReceive(context, intent)

        verify(exactly = 0) { listener.onEngineInstalled(any()) }
    }

    // ========== ACTION_PACKAGE_ADDED Tests ==========

    @Test
    fun `ACTION_PACKAGE_ADDED should trigger onEngineInstalled callback`() {
        packageMonitor.addListener(listener)

        val intent = createPackageIntent(Intent.ACTION_PACKAGE_ADDED, ENGINE_PACKAGE_NAME)
        mockEngineInstalled(VERSION_1_5_0)

        packageMonitor.onReceive(context, intent)

        verify {
            listener.onEngineInstalled(
                match { info ->
                    info.packageName == ENGINE_PACKAGE_NAME &&
                    info.versionName == VERSION_1_5_0 &&
                    info.isInstalled
                }
            )
        }
    }

    @Test
    fun `ACTION_PACKAGE_ADDED should not trigger callback if package not found`() {
        packageMonitor.addListener(listener)

        val intent = createPackageIntent(Intent.ACTION_PACKAGE_ADDED, ENGINE_PACKAGE_NAME)
        mockEngineNotInstalled()

        packageMonitor.onReceive(context, intent)

        verify(exactly = 0) { listener.onEngineInstalled(any()) }
    }

    // ========== ACTION_PACKAGE_REPLACED Tests ==========

    @Test
    fun `ACTION_PACKAGE_REPLACED should trigger onEngineUpdated callback`() {
        packageMonitor.addListener(listener)

        // Simulate initial installation
        val addIntent = createPackageIntent(Intent.ACTION_PACKAGE_ADDED, ENGINE_PACKAGE_NAME)
        mockEngineInstalled(VERSION_1_4_0)
        packageMonitor.onReceive(context, addIntent)

        // Simulate update
        val updateIntent = createPackageIntent(Intent.ACTION_PACKAGE_REPLACED, ENGINE_PACKAGE_NAME)
        mockEngineInstalled(VERSION_1_5_0)
        packageMonitor.onReceive(context, updateIntent)

        verify {
            listener.onEngineUpdated(VERSION_1_4_0, VERSION_1_5_0)
        }
    }

    @Test
    fun `ACTION_PACKAGE_REPLACED without prior install should use unknown version`() {
        packageMonitor.addListener(listener)

        val intent = createPackageIntent(Intent.ACTION_PACKAGE_REPLACED, ENGINE_PACKAGE_NAME)
        mockEngineInstalled(VERSION_1_5_0)

        packageMonitor.onReceive(context, intent)

        verify {
            listener.onEngineUpdated("unknown", VERSION_1_5_0)
        }
    }

    // ========== ACTION_PACKAGE_REMOVED Tests ==========

    @Test
    fun `ACTION_PACKAGE_REMOVED should trigger onEngineRemoved callback`() {
        packageMonitor.addListener(listener)

        // Simulate installation first
        val addIntent = createPackageIntent(Intent.ACTION_PACKAGE_ADDED, ENGINE_PACKAGE_NAME)
        mockEngineInstalled(VERSION_1_5_0)
        packageMonitor.onReceive(context, addIntent)

        // Simulate removal
        val removeIntent = createPackageIntent(Intent.ACTION_PACKAGE_REMOVED, ENGINE_PACKAGE_NAME)
        packageMonitor.onReceive(context, removeIntent)

        verify {
            listener.onEngineRemoved(VERSION_1_5_0)
        }
    }

    @Test
    fun `ACTION_PACKAGE_REMOVED without prior install should use unknown version`() {
        packageMonitor.addListener(listener)

        val intent = createPackageIntent(Intent.ACTION_PACKAGE_REMOVED, ENGINE_PACKAGE_NAME)

        packageMonitor.onReceive(context, intent)

        verify {
            listener.onEngineRemoved("unknown")
        }
    }

    // ========== Lifecycle Tests ==========

    @Test
    fun `register should register BroadcastReceiver`() {
        packageMonitor.register()

        verify {
            context.registerReceiver(
                packageMonitor,
                match { filter ->
                    filter.hasAction(Intent.ACTION_PACKAGE_ADDED) &&
                    filter.hasAction(Intent.ACTION_PACKAGE_REPLACED) &&
                    filter.hasAction(Intent.ACTION_PACKAGE_REMOVED)
                }
            )
        }
    }

    @Test
    fun `unregister should unregister BroadcastReceiver`() {
        packageMonitor.register()
        packageMonitor.unregister()

        verify { context.unregisterReceiver(packageMonitor) }
    }

    @Test
    fun `register twice should only register once`() {
        packageMonitor.register()
        packageMonitor.register()

        verify(exactly = 1) {
            context.registerReceiver(any(), any())
        }
    }

    @Test
    fun `unregister without register should not throw exception`() {
        every { context.unregisterReceiver(any()) } throws IllegalArgumentException("Receiver not registered")

        // Should not throw
        packageMonitor.unregister()
    }

    // ========== Helper Methods ==========

    private fun createPackageIntent(action: String, packageName: String): Intent {
        return Intent(action).apply {
            data = Uri.parse("package:$packageName")
        }
    }

    private fun mockEngineInstalled(version: String) {
        val packageInfo = PackageInfo().apply {
            this.versionName = version
            this.longVersionCode = 1500L
            this.firstInstallTime = System.currentTimeMillis()
            this.lastUpdateTime = System.currentTimeMillis()
        }

        every { packageManager.getPackageInfo(ENGINE_PACKAGE_NAME, 0) } returns packageInfo
    }

    private fun mockEngineNotInstalled() {
        every {
            packageManager.getPackageInfo(ENGINE_PACKAGE_NAME, 0)
        } throws PackageManager.NameNotFoundException()
    }
}
