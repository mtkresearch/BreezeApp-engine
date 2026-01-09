package com.mtkresearch.breezeapp.edgeai

import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Base class for EdgeAI tests.
 * 
 * Provides common setup/teardown and test infrastructure using Robolectric.
 * All example tests extend this class to ensure consistent test environment.
 * 
 * ## Features
 * - Automatic SDK shutdown before/after each test
 * - Robolectric test runner for Android components
 * - Configured for SDK 28 (Android 9.0)
 * 
 * ## Usage
 * ```kotlin
 * @RunWith(RobolectricTestRunner::class)
 * class MyTest : EdgeAITestBase() {
 *     @Test
 *     fun myTest() {
 *         // Test code here
 *         // SDK is automatically cleaned up
 *     }
 * }
 * ```
 * 
 * @see ChatExamples
 * @see TTSExamples
 * @see ASRExamples
 * @see SDKLifecycleExamples
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
abstract class EdgeAITestBase {
    
    /**
     * Set up test environment before each test.
     * 
     * Ensures SDK is in clean state by shutting down any previous instance.
     */
    @Before
    fun baseSetUp() {
        // Shutdown before each test to ensure clean state
        EdgeAI.shutdown()
    }
    
    /**
     * Clean up test environment after each test.
     * 
     * Ensures SDK resources are properly released.
     */
    @After
    fun baseTearDown() {
        // Cleanup after each test
        EdgeAI.shutdown()
    }
}
