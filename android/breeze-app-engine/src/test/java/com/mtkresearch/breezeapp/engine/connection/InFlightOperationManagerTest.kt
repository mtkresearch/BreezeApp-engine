package com.mtkresearch.breezeapp.engine.connection

import com.mtkresearch.breezeapp.engine.model.EngineConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for InFlightOperationManager.
 * Tests operation cancellation logic during reconnection.
 *
 * Implements T032: Test reconnection flow with operation cancellation
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InFlightOperationManagerTest {

    private lateinit var connectionStateFlow: MutableStateFlow<EngineConnectionState>
    private lateinit var operationManager: InFlightOperationManager

    @Before
    fun setup() {
        connectionStateFlow = MutableStateFlow(EngineConnectionState.Disconnected)
        operationManager = InFlightOperationManager(connectionStateFlow)
    }

    @After
    fun teardown() {
        operationManager.destroy()
    }

    // ========== Initial State Tests ==========

    @Test
    fun `operations should not be allowed initially when Disconnected`() {
        assertFalse("Operations should not be allowed when Disconnected",
            operationManager.areOperationsAllowed())
    }

    @Test
    fun `status should reflect Disconnected state`() {
        val status = operationManager.getOperationStatus()

        assertTrue("Status should mention disconnected",
            status.contains("Disconnected", ignoreCase = true))
    }

    // ========== Connection State Transitions ==========

    @Test
    fun `operations should be allowed when Connected`() = runTest {
        connectionStateFlow.value = EngineConnectionState.Connected(
            version = "1.5.0",
            timestamp = System.currentTimeMillis()
        )

        advanceTimeBy(100L)

        assertTrue("Operations should be allowed when Connected",
            operationManager.areOperationsAllowed())
    }

    @Test
    fun `operations should be blocked when Connecting`() = runTest {
        connectionStateFlow.value = EngineConnectionState.Connecting

        advanceTimeBy(100L)

        assertFalse("Operations should be blocked when Connecting",
            operationManager.areOperationsAllowed())
    }

    @Test
    fun `operations should be blocked when Error`() = runTest {
        // First connect
        connectionStateFlow.value = EngineConnectionState.Connected(
            "1.5.0",
            System.currentTimeMillis()
        )
        advanceTimeBy(100L)

        // Then error
        connectionStateFlow.value = EngineConnectionState.Error(
            message = "Connection failed",
            errorCode = "ERR_TEST",
            timestamp = System.currentTimeMillis()
        )
        advanceTimeBy(100L)

        assertFalse("Operations should be blocked when Error",
            operationManager.areOperationsAllowed())
    }

    // ========== Reconnection and Cancellation Tests (T030, T032) ==========

    @Test
    fun `should cancel operations when transitioning to Reconnecting`() = runTest {
        var cancellationCalled = false
        var cancellationReason = ""

        // First connect
        connectionStateFlow.value = EngineConnectionState.Connected(
            "1.5.0",
            System.currentTimeMillis()
        )
        advanceTimeBy(100L)

        // Register listener
        operationManager.addCancellationListener { reason ->
            cancellationCalled = true
            cancellationReason = reason
        }

        // Trigger reconnection
        connectionStateFlow.value = EngineConnectionState.Reconnecting
        advanceTimeBy(100L)

        assertTrue("Cancellation should be triggered", cancellationCalled)
        assertTrue("Reason should mention reconnecting",
            cancellationReason.contains("reconnecting", ignoreCase = true))
        assertFalse("Operations should be blocked during reconnection",
            operationManager.areOperationsAllowed())
    }

    @Test
    fun `should allow operations after successful reconnection`() = runTest {
        // Connect → Reconnecting → Connected
        connectionStateFlow.value = EngineConnectionState.Connected(
            "1.4.0",
            System.currentTimeMillis()
        )
        advanceTimeBy(100L)

        connectionStateFlow.value = EngineConnectionState.Reconnecting
        advanceTimeBy(100L)

        assertFalse("Operations blocked during reconnection",
            operationManager.areOperationsAllowed())

        connectionStateFlow.value = EngineConnectionState.Connected(
            "1.5.0",
            System.currentTimeMillis()
        )
        advanceTimeBy(100L)

        assertTrue("Operations should be allowed after reconnection",
            operationManager.areOperationsAllowed())
    }

    @Test
    fun `should cancel operations when engine removed`() = runTest {
        var cancellationCalled = false

        // Connected
        connectionStateFlow.value = EngineConnectionState.Connected(
            "1.5.0",
            System.currentTimeMillis()
        )
        advanceTimeBy(100L)

        operationManager.addCancellationListener { _ ->
            cancellationCalled = true
        }

        // Engine removed
        connectionStateFlow.value = EngineConnectionState.Disconnected
        advanceTimeBy(100L)

        assertTrue("Cancellation should be triggered on removal",
            cancellationCalled)
        assertFalse("Operations should be blocked after removal",
            operationManager.areOperationsAllowed())
    }

    // ========== Listener Management Tests ==========

    @Test
    fun `should notify all registered listeners`() = runTest {
        var listener1Called = false
        var listener2Called = false

        connectionStateFlow.value = EngineConnectionState.Connected(
            "1.5.0",
            System.currentTimeMillis()
        )
        advanceTimeBy(100L)

        operationManager.addCancellationListener { _ -> listener1Called = true }
        operationManager.addCancellationListener { _ -> listener2Called = true }

        connectionStateFlow.value = EngineConnectionState.Reconnecting
        advanceTimeBy(100L)

        assertTrue("Listener 1 should be called", listener1Called)
        assertTrue("Listener 2 should be called", listener2Called)
    }

    @Test
    fun `should not notify removed listeners`() = runTest {
        var listenerCalled = false
        val listener: (String) -> Unit = { _ -> listenerCalled = true }

        connectionStateFlow.value = EngineConnectionState.Connected(
            "1.5.0",
            System.currentTimeMillis()
        )
        advanceTimeBy(100L)

        operationManager.addCancellationListener(listener)
        operationManager.removeCancellationListener(listener)

        connectionStateFlow.value = EngineConnectionState.Reconnecting
        advanceTimeBy(100L)

        assertFalse("Removed listener should not be called", listenerCalled)
    }

    @Test
    fun `should clean up dead weak references`() = runTest {
        connectionStateFlow.value = EngineConnectionState.Connected(
            "1.5.0",
            System.currentTimeMillis()
        )
        advanceTimeBy(100L)

        // Add listener that will be garbage collected
        operationManager.addCancellationListener { _ -> }

        // Trigger state change to clean up dead references
        connectionStateFlow.value = EngineConnectionState.Reconnecting
        advanceTimeBy(100L)

        // Should not throw or cause issues
    }

    // ========== Manual Cancellation Tests ==========

    @Test
    fun `manual cancellation should trigger listeners`() {
        var cancellationCalled = false
        var reason = ""

        operationManager.addCancellationListener { r ->
            cancellationCalled = true
            reason = r
        }

        operationManager.triggerCancellation("Test cancellation")

        assertTrue("Listener should be called", cancellationCalled)
        assertEquals("Custom reason should be passed",
            "Test cancellation",
            reason)
    }

    @Test
    fun `manual cancellation should block operations`() {
        // Even when connected
        connectionStateFlow.value = EngineConnectionState.Connected(
            "1.5.0",
            System.currentTimeMillis()
        )

        operationManager.triggerCancellation()

        assertFalse("Operations should be blocked after manual cancellation",
            operationManager.areOperationsAllowed())
    }

    // ========== Status Tests ==========

    @Test
    fun `status should reflect current state correctly`() = runTest {
        // Disconnected
        var status = operationManager.getOperationStatus()
        assertTrue("Should mention blocked/disconnected",
            status.contains("blocked", ignoreCase = true) ||
            status.contains("Disconnected", ignoreCase = true))

        // Connected
        connectionStateFlow.value = EngineConnectionState.Connected(
            "1.5.0",
            System.currentTimeMillis()
        )
        advanceTimeBy(100L)

        status = operationManager.getOperationStatus()
        assertTrue("Should mention allowed/operations",
            status.contains("allowed", ignoreCase = true) ||
            status.contains("Operations", ignoreCase = true))

        // Reconnecting
        connectionStateFlow.value = EngineConnectionState.Reconnecting
        advanceTimeBy(100L)

        status = operationManager.getOperationStatus()
        assertTrue("Should mention reconnecting/paused",
            status.contains("Reconnecting", ignoreCase = true) ||
            status.contains("paused", ignoreCase = true))
    }

    // ========== Lifecycle Tests ==========

    @Test
    fun `destroy should clean up resources`() = runTest {
        var listenerCalled = false

        operationManager.addCancellationListener { _ ->
            listenerCalled = true
        }

        operationManager.destroy()

        // Trigger state change after destroy
        connectionStateFlow.value = EngineConnectionState.Reconnecting
        advanceTimeBy(100L)

        assertFalse("Listener should not be called after destroy",
            listenerCalled)
    }

    @Test
    fun `multiple destroys should be safe`() {
        // Should not throw
        operationManager.destroy()
        operationManager.destroy()
        operationManager.destroy()
    }

    // ========== Edge Case Tests ==========

    @Test
    fun `should not cancel when transitioning from Disconnected to Reconnecting`() = runTest {
        var cancellationCalled = false

        operationManager.addCancellationListener { _ ->
            cancellationCalled = true
        }

        // Disconnected → Reconnecting (not from Connected)
        connectionStateFlow.value = EngineConnectionState.Reconnecting
        advanceTimeBy(100L)

        assertFalse("Should not cancel if not previously connected",
            cancellationCalled)
    }

    @Test
    fun `should cancel when transitioning from Reconnecting to Error`() = runTest {
        var cancellationCalled = false

        // Connected → Reconnecting
        connectionStateFlow.value = EngineConnectionState.Connected(
            "1.5.0",
            System.currentTimeMillis()
        )
        advanceTimeBy(100L)

        connectionStateFlow.value = EngineConnectionState.Reconnecting
        advanceTimeBy(100L)

        operationManager.addCancellationListener { _ ->
            cancellationCalled = true
        }

        // Reconnecting → Error
        connectionStateFlow.value = EngineConnectionState.Error(
            "Failed",
            "ERR_TEST",
            System.currentTimeMillis()
        )
        advanceTimeBy(100L)

        assertTrue("Should cancel when reconnection fails",
            cancellationCalled)
    }
}
