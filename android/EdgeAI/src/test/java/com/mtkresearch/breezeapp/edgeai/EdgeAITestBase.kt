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
    
    // === Parameter Reading Helpers ===
    
    /**
     * Read test parameter from system properties.
     * 
     * Usage in terminal:
     * ```bash
     * ./gradlew :EdgeAI:test -Dtest.prompt="Hello" --tests MyTest
     * ```
     * 
     * Usage in test:
     * ```kotlin
     * val prompt = getTestParam("prompt", "default value")
     * ```
     */
    protected fun getTestParam(key: String, default: String = ""): String {
        return System.getProperty("test.$key") ?: default
    }
    
    protected fun getTestParamFloat(key: String, default: Float): Float {
        return System.getProperty("test.$key")?.toFloatOrNull() ?: default
    }
    
    protected fun getTestParamInt(key: String, default: Int): Int {
        return System.getProperty("test.$key")?.toIntOrNull() ?: default
    }
    
    protected fun getTestParamBoolean(key: String, default: Boolean): Boolean {
        return System.getProperty("test.$key")?.toBooleanStrictOrNull() ?: default
    }
}
