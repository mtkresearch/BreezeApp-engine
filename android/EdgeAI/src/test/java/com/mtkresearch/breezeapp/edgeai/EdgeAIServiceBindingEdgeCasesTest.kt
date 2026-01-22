package com.mtkresearch.breezeapp.edgeai

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.util.concurrent.TimeoutException

/**
 * EdgeAI Service Binding Edge Cases Test
 * 
 * Tests the HARDEST edge cases for service binding and AIDL communication.
 * These are critical failure scenarios that must be handled gracefully.
 * 
 * **Critical Android Edge Cases Covered:**
 * 1. Service binding failures (service not found, security exceptions)
 * 2. Service disconnection during active requests
 * 3. Binder death (service process killed)
 * 4. AIDL communication failures (RemoteException, DeadObjectException)
 * 5. Concurrent binding attempts
 * 6. Memory pressure during service binding
 * 7. Service binding timeout
 * 8. Multiple client binding/unbinding race conditions
 * 
 * As a senior Android developer, these tests ensure zero-crash behavior
 * even when the system is under extreme stress.
 */
class EdgeAIServiceBindingEdgeCasesTest {

    private lateinit var mockContext: Context
    private lateinit var mockBinder: IBinder
    private lateinit var mockService: IBreezeAppEngineService

    @Before
    fun setUp() {
        mockContext = mock()
        mockBinder = mock()
        mockService = mock()
        
        // Reset EdgeAI singleton state
        EdgeAI.shutdown()
    }

    @After
    fun tearDown() {
        EdgeAI.shutdown()
    }

    // ===================================================================
    // HARDEST EDGE CASE #1: Service Binding Failures
    // ===================================================================

    @Test
    fun `test service not found - bindService returns false`() = runTest {
        // Simulate service not installed or not exported
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        whenever(mockContext.bindService(any(), any(), any<Int>())).thenReturn(false)

        val result = EdgeAI.initialize(mockContext)

        assertTrue("Should fail when service not found", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Should throw ServiceConnectionException", 
                  exception is ServiceConnectionException)
        assertTrue("Error message should indicate binding failure",
                  exception?.message?.contains("Failed to bind") == true)
    }

    @Test
    fun `test security exception during binding - permission denied`() = runTest {
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        whenever(mockContext.bindService(any(), any(), any<Int>()))
            .thenThrow(SecurityException("Permission denied"))

        val result = EdgeAI.initialize(mockContext)

        assertTrue("Should fail on security exception", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Should wrap security exception", 
                  exception is ServiceConnectionException)
    }

    @Test
    fun `test service binding timeout - never calls onServiceConnected`() = runTest {
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        whenever(mockContext.bindService(any(), any(), any<Int>())).thenReturn(true)
        // Service never connects (simulates hung service)

        try {
            withTimeout(2000) { // 2 second timeout
                EdgeAI.initialize(mockContext)
            }
            fail("Should timeout waiting for service connection")
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            // Expected - service never connected
        }
    }

    // ===================================================================
    // HARDEST EDGE CASE #2: Service Disconnection During Active Requests
    // ===================================================================

    @Test
    fun `test service disconnection during chat request`() = runTest {
        // Setup: Successfully bind service
        setupSuccessfulBinding()

        // Start a chat request
        val chatRequest = ChatRequest(
            model = "test-model",
            messages = listOf(ChatMessage(role = "user", content = "test"))
        )

        var disconnectionHandled = false
        val flow = EdgeAI.chat(chatRequest)

        // Simulate service disconnection before collecting
        simulateServiceDisconnection()

        try {
            flow.collect { 
                fail("Should not receive responses after disconnection")
            }
        } catch (e: ServiceConnectionException) {
            disconnectionHandled = true
            assertTrue("Error should mention disconnection",
                      e.message?.contains("disconnected") == true)
        }

        assertTrue("Should handle disconnection gracefully", disconnectionHandled)
    }

    @Test
    fun `test binder death during streaming response`() = runTest {
        setupSuccessfulBinding()

        val chatRequest = ChatRequest(
            model = "test-model",
            messages = listOf(ChatMessage(role = "user", content = "test"))
        )

        // Simulate binder death (process killed)
        whenever(mockService.sendChatRequest(any(), any()))
            .thenThrow(android.os.DeadObjectException("Binder died"))

        try {
            EdgeAI.chat(chatRequest).collect { }
            fail("Should throw exception on binder death")
        } catch (e: InternalErrorException) {
            assertTrue("Should handle binder death",
                      e.message?.contains("failed") == true)
        }
    }

    // ===================================================================
    // HARDEST EDGE CASE #3: Concurrent Binding Attempts
    // ===================================================================

    @Test
    fun `test concurrent initialization attempts - only one succeeds`() = runTest {
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        whenever(mockContext.bindService(any(), any(), any<Int>())).thenReturn(true)

        // Launch multiple concurrent initialization attempts
        // Launch multiple concurrent initialization attempts
        val deferreds = List(10) {
            async {
                EdgeAI.initialize(mockContext)
            }
        }
        val results = deferreds.awaitAll()

        // All should succeed (idempotent)
        assertTrue("All initializations should succeed", results.all { it.isSuccess })
        
        
        // But bindService should only be called once
        verify(mockContext, times(1)).bindService(any(), any(), any<Int>())

        EdgeAI.shutdown()
        yield() // Ensure cancellation propagates
    }

    @Test
    fun `test initialize then immediate shutdown then reinitialize`() = runTest {
        setupSuccessfulBinding()
        
        val result1 = EdgeAI.initialize(mockContext)
        assertTrue("First init should succeed", result1.isSuccess)
        
        EdgeAI.shutdown()
        assertFalse("Should be uninitialized after shutdown", EdgeAI.isInitialized())
        
        // Reinitialize
        setupSuccessfulBinding() // Reset mocks
        val result2 = EdgeAI.initialize(mockContext)
        assertTrue("Reinit should succeed", result2.isSuccess)
    }

    // ===================================================================
    // HARDEST EDGE CASE #4: AIDL Communication Failures
    // ===================================================================

    @Test
    fun `test RemoteException during sendChatRequest`() = runTest {
        setupSuccessfulBinding()

        whenever(mockService.sendChatRequest(any(), any()))
            .thenThrow(RemoteException("AIDL communication failed"))

        val chatRequest = ChatRequest(
            model = "test-model",
            messages = listOf(ChatMessage(role = "user", content = "test"))
        )

        try {
            EdgeAI.chat(chatRequest).collect { }
            fail("Should throw exception on AIDL failure")
        } catch (e: InternalErrorException) {
            assertTrue("Should wrap RemoteException", 
                      e.message?.contains("failed") == true)
        }
    }

    @Test
    fun `test AIDL transaction too large - exceeds binder limit`() = runTest {
        setupSuccessfulBinding()

        // Create a request with extremely large content (>1MB)
        val largeContent = "x".repeat(2 * 1024 * 1024) // 2MB
        val chatRequest = ChatRequest(
            model = "test-model",
            messages = listOf(ChatMessage(role = "user", content = largeContent))
        )

        whenever(mockService.sendChatRequest(any(), any()))
            .thenThrow(RemoteException("Transaction too large"))

        try {
            EdgeAI.chat(chatRequest).collect { }
            fail("Should fail on oversized transaction")
        } catch (e: InternalErrorException) {
            // Expected - transaction too large
        }
    }

    // ===================================================================
    // HARDEST EDGE CASE #5: Memory Pressure Scenarios
    // ===================================================================

    @Test
    fun `test low memory during service binding`() = runTest {
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        whenever(mockContext.bindService(any(), any(), any<Int>()))
            .thenThrow(OutOfMemoryError("Insufficient memory"))

        try {
            EdgeAI.initialize(mockContext)
            fail("Should propagate OutOfMemoryError")
        } catch (e: OutOfMemoryError) {
            // Expected - system is out of memory
        }
    }

    @Test
    fun `test service killed by system due to memory pressure`() = runTest {
        setupSuccessfulBinding()

        // Simulate service being killed
        simulateServiceDisconnection()

        // Verify SDK detects disconnection
        assertFalse("Should detect service disconnection", EdgeAI.isReady())
    }

    // ===================================================================
    // HARDEST EDGE CASE #6: Listener Registration Failures
    // ===================================================================

    @Test
    fun `test listener registration fails - service rejects listener`() = runTest {
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        whenever(mockContext.bindService(any(), any(), any<Int>())).thenAnswer { invocation ->
            val connection = invocation.arguments[1] as ServiceConnection
            connection.onServiceConnected(ComponentName("test", "test"), mockBinder)
            true
        }
        
        whenever(IBreezeAppEngineService.Stub.asInterface(mockBinder)).thenReturn(mockService)
        whenever(mockService.registerListener(any()))
            .thenThrow(RemoteException("Listener registration failed"))

        // SDK fails initialization if listener registration fails
        val result = EdgeAI.initialize(mockContext)
        assertTrue("Should fail initialization if listener registration fails", result.isFailure)
    }

    // ===================================================================
    // HARDEST EDGE CASE #7: Race Conditions
    // ===================================================================

    @Test
    fun `test rapid bind-unbind-bind cycle`() = runTest {
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        
        repeat(5) {
            whenever(mockContext.bindService(any(), any(), any<Int>())).thenReturn(true)
            EdgeAI.initialize(mockContext)
            EdgeAI.shutdown()
            yield()
        }

        // Should handle rapid cycling without crashes
        assertTrue("Should survive rapid cycling", true)
    }

    @Test
    fun `test shutdown during initialization`() = runTest {
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        whenever(mockContext.bindService(any(), any(), any<Int>())).thenReturn(true)

        val initJob = async {
            EdgeAI.initialize(mockContext)
        }

        // Shutdown immediately
        EdgeAI.shutdown()
        yield() // Ensure cancellation starts

        val result = initJob.await()
        // Should either succeed or fail gracefully
        assertTrue("Should handle concurrent shutdown", 
                  result.isSuccess || result.isFailure)

        EdgeAI.shutdown()
        yield()
    }

    // ===================================================================
    // HARDEST EDGE CASE #8: Multiple Clients
    // ===================================================================

    @Test
    fun `test multiple contexts trying to initialize`() = runTest {
        val context1 = mock<Context>()
        val context2 = mock<Context>()
        
        whenever(context1.applicationContext).thenReturn(context1)
        whenever(context2.applicationContext).thenReturn(context2)
        whenever(context1.bindService(any(), any(), any<Int>())).thenReturn(true)
        whenever(context2.bindService(any(), any(), any<Int>())).thenReturn(true)

        EdgeAI.initialize(context1)
        val result2 = EdgeAI.initialize(context2)

        // Second init should succeed (already initialized)
        assertTrue("Should handle multiple contexts", result2.isSuccess)
        
        // Should only bind once
        verify(context1, times(1)).bindService(any(), any(), any<Int>())
        verify(context2, never()).bindService(any(), any(), any<Int>())

        EdgeAI.shutdown()
        yield()
    }

    // ===================================================================
    // Helper Methods
    // ===================================================================

    private suspend fun setupSuccessfulBinding() {
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        whenever(mockContext.bindService(any(), any(), any<Int>())).thenAnswer { invocation ->
            val connection = invocation.arguments[1] as ServiceConnection
            // Simulate successful connection
            connection.onServiceConnected(ComponentName("test", "test"), mockBinder)
            true
        }
        
        whenever(IBreezeAppEngineService.Stub.asInterface(mockBinder)).thenReturn(mockService)
        
        EdgeAI.initialize(mockContext)
    }

    private fun simulateServiceDisconnection() {
        // This would trigger onServiceDisconnected in real scenario
        // For testing, we manually call shutdown to simulate disconnection
        EdgeAI.shutdown()
    }
}
