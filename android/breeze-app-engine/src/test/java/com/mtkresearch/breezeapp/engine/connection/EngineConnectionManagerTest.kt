package com.mtkresearch.breezeapp.engine.connection

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.mtkresearch.breezeapp.engine.model.BindingConfig
import com.mtkresearch.breezeapp.engine.model.ConnectionEvent
import com.mtkresearch.breezeapp.engine.model.EngineConnectionState
import com.mtkresearch.breezeapp.engine.model.EnginePackageInfo
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for EngineConnectionManager implementation.
 * Tests connection lifecycle, retry logic, and package monitoring integration.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EngineConnectionManagerTest {

    private lateinit var context: Context
    private lateinit var packageManager: PackageManager
    private lateinit var manager: EngineConnectionManagerImpl
    private lateinit var config: BindingConfig

    companion object {
        private const val ENGINE_PACKAGE_NAME = "com.mtkresearch.breezeapp.engine"
        private const val VERSION_1_5_0 = "1.5.0"
        private const val VERSION_1_4_0 = "1.4.0"
    }

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        packageManager = mockk(relaxed = true)

        every { context.packageManager } returns packageManager

        config = BindingConfig(
            retryDelayMs = 100L, // Shorter delay for testing
            bindingTimeoutMs = 5000L,
            debounceDelayMs = 50L,
            maxReconnectAttempts = 1
        )

        manager = EngineConnectionManagerImpl(context, config)
    }

    @After
    fun teardown() {
        manager.stopMonitoring()
        clearAllMocks()
    }

    // ========== Initial State Tests ==========

    @Test
    fun `initial state should be Disconnected`() = runTest {
        val initialState = manager.connectionState.value

        assertTrue("Initial state should be Disconnected",
            initialState is EngineConnectionState.Disconnected)
    }

    @Test
    fun `initial event should be null`() = runTest {
        val initialEvent = manager.connectionEvents.value

        assertNull("Initial event should be null", initialEvent)
    }

    // ========== Package Detection Tests (T012) ==========

    @Test
    fun `isEngineInstalled should return true when package exists`() {
        mockEngineInstalled(VERSION_1_5_0)

        val isInstalled = manager.isEngineInstalled()

        assertTrue("Engine should be detected as installed", isInstalled)
    }

    @Test
    fun `isEngineInstalled should return false when package not found`() {
        mockEngineNotInstalled()

        val isInstalled = manager.isEngineInstalled()

        assertFalse("Engine should not be detected", isInstalled)
    }

    @Test
    fun `getEnginePackageInfo should return info when installed`() {
        mockEngineInstalled(VERSION_1_5_0)

        val packageInfo = manager.getEnginePackageInfo()

        assertNotNull("Package info should not be null", packageInfo)
        assertEquals("Package name should match", ENGINE_PACKAGE_NAME, packageInfo?.packageName)
        assertEquals("Version should match", VERSION_1_5_0, packageInfo?.versionName)
        assertTrue("Should be marked as installed", packageInfo?.isInstalled == true)
    }

    @Test
    fun `getEnginePackageInfo should return not installed info when not found`() {
        mockEngineNotInstalled()

        val packageInfo = manager.getEnginePackageInfo()

        assertNotNull("Package info should not be null", packageInfo)
        assertFalse("Should be marked as not installed", packageInfo?.isInstalled == true)
    }

    // ========== Connection Tests ==========

    @Test
    fun `connect should return false when engine not installed`() {
        mockEngineNotInstalled()

        val result = manager.connect()

        assertFalse("Connect should return false when engine not installed", result)
    }

    @Test
    fun `connect should transition to Error state when engine not installed`() = runTest {
        mockEngineNotInstalled()

        manager.connect()

        val state = manager.connectionState.value
        assertTrue("State should be Error", state is EngineConnectionState.Error)
        assertEquals("Error message should mention not installed",
            "BreezeApp Engine is not installed",
            (state as EngineConnectionState.Error).message)
    }

    @Test
    fun `connect should return true when engine installed`() {
        mockEngineInstalled(VERSION_1_5_0)

        val result = manager.connect()

        assertTrue("Connect should return true when engine installed", result)
    }

    @Test
    fun `connect should transition to Connecting state`() = runTest {
        mockEngineInstalled(VERSION_1_5_0)

        manager.connect()

        val state = manager.connectionState.value
        // Should be Connecting or already Connected (depends on timing)
        assertTrue("State should be Connecting or Connected",
            state is EngineConnectionState.Connecting ||
            state is EngineConnectionState.Connected)
    }

    @Test
    fun `disconnect should transition to Disconnected state`() = runTest {
        mockEngineInstalled(VERSION_1_5_0)
        manager.connect()

        manager.disconnect()

        val state = manager.connectionState.value
        assertTrue("State should be Disconnected",
            state is EngineConnectionState.Disconnected)
    }

    // ========== Reconnection Tests ==========

    @Test
    fun `reconnect should transition to Reconnecting state`() = runTest {
        mockEngineInstalled(VERSION_1_5_0)

        manager.reconnect()

        val state = manager.connectionState.value
        // Should be Reconnecting or already Connected (depends on timing)
        assertTrue("State should be Reconnecting or Connected",
            state is EngineConnectionState.Reconnecting ||
            state is EngineConnectionState.Connected)
    }

    // ========== Package Lifecycle Tests ==========

    @Test
    fun `onEngineInstalled should emit PackageInstalled event`() = runTest {
        val packageInfo = EnginePackageInfo(
            packageName = ENGINE_PACKAGE_NAME,
            versionName = VERSION_1_5_0,
            versionCode = 1500L,
            isInstalled = true
        )

        manager.onEngineInstalled(packageInfo)

        val event = manager.connectionEvents.value
        assertTrue("Event should be PackageInstalled",
            event is ConnectionEvent.PackageInstalled)
        assertEquals("Version should match", VERSION_1_5_0,
            (event as ConnectionEvent.PackageInstalled).version)
    }

    @Test
    fun `onEngineUpdated should emit PackageUpdated event`() = runTest {
        manager.onEngineUpdated(VERSION_1_4_0, VERSION_1_5_0)

        val event = manager.connectionEvents.value
        assertTrue("Event should be PackageUpdated",
            event is ConnectionEvent.PackageUpdated)

        val updateEvent = event as ConnectionEvent.PackageUpdated
        assertEquals("Old version should match", VERSION_1_4_0, updateEvent.oldVersion)
        assertEquals("New version should match", VERSION_1_5_0, updateEvent.newVersion)
    }

    @Test
    fun `onEngineRemoved should emit PackageRemoved event`() = runTest {
        manager.onEngineRemoved(VERSION_1_5_0)

        val event = manager.connectionEvents.value
        assertTrue("Event should be PackageRemoved",
            event is ConnectionEvent.PackageRemoved)
        assertEquals("Version should match", VERSION_1_5_0,
            (event as ConnectionEvent.PackageRemoved).lastVersion)
    }

    @Test
    fun `onEngineRemoved should transition to Error state`() = runTest {
        manager.onEngineRemoved(VERSION_1_5_0)

        val state = manager.connectionState.value
        assertTrue("State should be Error", state is EngineConnectionState.Error)
        assertEquals("Error code should be ENGINE_REMOVED",
            "ENGINE_REMOVED",
            (state as EngineConnectionState.Error).errorCode)
    }

    // ========== Monitoring Lifecycle Tests ==========

    @Test
    fun `startMonitoring should emit EngineDetected when installed`() = runTest {
        mockEngineInstalled(VERSION_1_5_0)

        manager.startMonitoring()

        val event = manager.connectionEvents.value
        assertTrue("Event should be EngineDetected",
            event is ConnectionEvent.EngineDetected)
    }

    @Test
    fun `startMonitoring should emit EngineNotFound when not installed`() = runTest {
        mockEngineNotInstalled()

        manager.startMonitoring()

        val event = manager.connectionEvents.value
        assertTrue("Event should be EngineNotFound",
            event is ConnectionEvent.EngineNotFound)
    }

    // ========== Event Callback Tests ==========

    @Test
    fun `addEventCallback should receive events`() {
        var receivedEvent: ConnectionEvent? = null
        val callback: (ConnectionEvent) -> Unit = { event ->
            receivedEvent = event
        }

        manager.addEventCallback(callback)

        val packageInfo = EnginePackageInfo(
            packageName = ENGINE_PACKAGE_NAME,
            versionName = VERSION_1_5_0,
            versionCode = 1500L,
            isInstalled = true
        )
        manager.onEngineInstalled(packageInfo)

        assertNotNull("Callback should receive event", receivedEvent)
        assertTrue("Event should be PackageInstalled",
            receivedEvent is ConnectionEvent.PackageInstalled)
    }

    @Test
    fun `removeEventCallback should stop receiving events`() {
        var callbackInvoked = false
        val callback: (ConnectionEvent) -> Unit = { _ ->
            callbackInvoked = true
        }

        manager.addEventCallback(callback)
        manager.removeEventCallback(callback)

        val packageInfo = EnginePackageInfo(
            packageName = ENGINE_PACKAGE_NAME,
            versionName = VERSION_1_5_0,
            versionCode = 1500L,
            isInstalled = true
        )
        manager.onEngineInstalled(packageInfo)

        assertFalse("Callback should not be invoked after removal", callbackInvoked)
    }

    // ========== Configuration Tests ==========

    @Test
    fun `updateConfig should accept new configuration`() {
        val newConfig = BindingConfig(
            retryDelayMs = 5000L,
            bindingTimeoutMs = 30000L,
            debounceDelayMs = 1000L,
            maxReconnectAttempts = 3
        )

        // Should not throw
        manager.updateConfig(newConfig)
    }

    // ========== State Flow Tests ==========

    @Test
    fun `connectionState flow should emit state changes`() = runTest {
        mockEngineInstalled(VERSION_1_5_0)

        // Initial state
        assertEquals(EngineConnectionState.Disconnected, manager.connectionState.value)

        // Connect
        manager.connect()

        // State should change to Connecting or Connected
        val state = manager.connectionState.value
        assertTrue("State should change after connect",
            state is EngineConnectionState.Connecting ||
            state is EngineConnectionState.Connected)
    }

    @Test
    fun `connectionEvents flow should emit events`() = runTest {
        mockEngineInstalled(VERSION_1_5_0)

        manager.startMonitoring()

        val event = manager.connectionEvents.value
        assertNotNull("Event should be emitted after startMonitoring", event)
        assertTrue("Event should be EngineDetected",
            event is ConnectionEvent.EngineDetected)
    }

    // ========== Helper Methods ==========

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
