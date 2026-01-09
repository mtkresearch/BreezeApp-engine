package com.mtkresearch.breezeapp.edgeai.examples

import com.mtkresearch.breezeapp.edgeai.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * SDK Lifecycle examples for EdgeAI SDK.
 *
 * Complete examples for SDK **initialization, state management, and cleanup**.
 *
 * ## Topics Covered
 * - SDK initialization (initialize vs initializeAndWait)
 * - State checking (isInitialized, isReady)
 * - Resource cleanup (shutdown)
 * - Error handling during initialization
 * - Best practices for lifecycle management
 *
 * ## For Client Developers
 * Read these examples to learn how to properly manage the SDK lifecycle.
 * Proper initialization and cleanup prevent resource leaks and crashes.
 *
 * ## For Maintainers
 * When modifying SDK lifecycle, update these examples.
 * All examples must pass CI.
 *
 * @see EdgeAI.initialize
 * @see EdgeAI.initializeAndWait
 * @see EdgeAI.shutdown
 * @see ChatExamples for chat API
 * @see TTSExamples for text-to-speech
 * @see ASRExamples for speech-to-text
 */
@RunWith(RobolectricTestRunner::class)
class SDKLifecycleExamples : EdgeAITestBase() {
    // setUp/tearDown inherited from EdgeAITestBase

    /**
     * Example 01: Initialize with Result (no exceptions)
     *
     * Shows how to initialize SDK using Result return type.
     *
     * ## When to use
     * - When you want to handle errors explicitly
     * - When you don't want exceptions
     * - When you need fine-grained error control
     *
     * ## How it works
     * - Returns `Result<Unit>`
     * - Success: `Result.success(Unit)`
     * - Failure: `Result.failure(exception)`
     * - Use `onSuccess {}` and `onFailure {}` to handle
     *
     * @see EdgeAI.initialize
     */
    @Test
    fun `01 - initialize with Result`() = runTest {
        val result = EdgeAI.initialize(
            context = mockContext(),
            timeoutMs = 10000
        )

        result.onSuccess {
            println("✓ SDK initialized successfully")
            assertTrue("Should be initialized", EdgeAI.isInitialized())
            assertTrue("Should be ready", EdgeAI.isReady())
        }.onFailure { error ->
            println("✗ Initialization failed: ${error.message}")
            // Handle error without crashing
        }
    }

    /**
     * Example 02: Initialize and wait (throws exceptions)
     *
     * Shows how to initialize SDK with exception-based error handling.
     *
     * ## When to use
     * - When you want simple error handling
     * - When exceptions are acceptable
     * - Most common use case
     *
     * ## How it works
     * - Suspends until initialized
     * - Throws exception on failure
     * - Use try-catch to handle errors
     *
     * @see EdgeAI.initializeAndWait
     */
    @Test
    fun `02 - initialize and wait`() = runTest {
        // Initialize with timeout
        EdgeAI.initializeAndWait(mockContext())

        println("✓ SDK initialized successfully")
        assertTrue("Should be initialized", EdgeAI.isInitialized())
        assertTrue("Should be ready", EdgeAI.isReady())
    }

    /**
     * Example 03: Check SDK state
     *
     * Shows how to check if SDK is initialized and ready.
     *
     * ## State methods
     * - `isInitialized()`: SDK has been initialized
     * - `isReady()`: SDK is connected and ready to use
     *
     * ## When to check
     * - Before making API calls
     * - In UI to enable/disable features
     * - For debugging
     *
     * ## State transitions
     * - Not initialized → Initialize → Initialized & Ready
     * - Initialized & Ready → Shutdown → Not initialized
     *
     */
    @Test
    fun `03 - check SDK state`() {
        // Initial state
        assertFalse("Should start uninitialized", EdgeAI.isInitialized())
        assertFalse("Should start not ready", EdgeAI.isReady())

        // After initialization (in real app)
        // EdgeAI.initializeAndWait(context)
        // assertTrue("Should be initialized", EdgeAI.isInitialized())
        // assertTrue("Should be ready", EdgeAI.isReady())

        // After shutdown
        EdgeAI.shutdown()
        assertFalse("Should be uninitialized after shutdown", EdgeAI.isInitialized())
        assertFalse("Should not be ready after shutdown", EdgeAI.isReady())
    }

    /**
     * Example 04: Resource cleanup (shutdown)
     *
     * Shows how to properly cleanup SDK resources.
     *
     * ## When to call shutdown
     * - In `Activity.onDestroy()`
     * - In `ViewModel.onCleared()`
     * - When app no longer needs AI features
     * - Before re-initializing
     *
     * ## What shutdown does
     * - Releases service connection
     * - Cancels pending requests
     * - Cleans up resources
     * - Safe to call multiple times
     *
     * @see EdgeAI.shutdown
     */
    @Test
    fun `04 - resource cleanup`() {
        // Shutdown SDK
        EdgeAI.shutdown()

        // Verify shutdown
        assertFalse("Should not be initialized", EdgeAI.isInitialized())
        assertFalse("Should not be ready", EdgeAI.isReady())

        // Safe to call multiple times
        EdgeAI.shutdown()
        EdgeAI.shutdown()
        EdgeAI.shutdown()

        println("✓ Cleanup complete")
    }

    /**
     * Example 05: Application-wide initialization
     *
     * Shows how to initialize EdgeAI once for the entire application.
     *
     * ## When to use
     * - Initialize in Application.onCreate()
     * - Single initialization for all activities
     * - Shared across app lifecycle
     *
     * ## Best practices
     * - Initialize early (Application.onCreate)
     * - Handle initialization errors
     * - Cleanup in Application.onTerminate() (rarely called)
     */
    @Test
    fun `05 - application-wide initialization`() = runTest {
        val context = mockContext()

        // Simulate Application.onCreate()
        class MyApplication {
            fun onCreate() {
                launch {
                    try {
                        EdgeAI.initializeAndWait(context)
                        println("✓ EdgeAI initialized for app")
                    } catch (e: Exception) {
                        println("✗ Failed to initialize: ${e.message}")
                    }
                }
            }

            fun onTerminate() {
                EdgeAI.shutdown()
                println("✓ EdgeAI cleaned up")
            }
        }

        val app = MyApplication()
        app.onCreate()

        // Wait for initialization
        while (!EdgeAI.isReady()) {
            delay(100)
        }

        assertTrue("Should be initialized", EdgeAI.isInitialized())
        assertTrue("Should be ready", EdgeAI.isReady())
    }

    // === HELPER FUNCTIONS ===

    private fun mockContext(): android.content.Context {
        return org.mockito.Mockito.mock(android.content.Context::class.java)
    }
}
