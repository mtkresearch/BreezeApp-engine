package com.mtkresearch.breezeapp.engine.test

import org.junit.Assume.assumeTrue
import java.io.File

/**
 * Test Prerequisites Helper
 * 
 * Provides self-documenting condition checks for tests that require external resources.
 * When a prerequisite is not met, the test is SKIPPED (not failed) with a clear message
 * telling the developer exactly how to enable the test.
 * 
 * Usage:
 * ```kotlin
 * @Test
 * fun openRouter_returnsValidResponse() {
 *     TestPrerequisites.requireApiKey("OPENROUTER_API_KEY")
 *     // Test executes only if API key is set
 * }
 * ```
 */
object TestPrerequisites {
    
    /**
     * Requires an environment variable to be set.
     * 
     * @param envVar The environment variable name (e.g. "OPENROUTER_API_KEY")
     * @return The value of the environment variable
     * 
     * Skip message shown to developer:
     * "Skipping: Set OPENROUTER_API_KEY environment variable to run this test"
     */
    fun requireApiKey(envVar: String): String {
        val value = System.getenv(envVar)
        assumeTrue(
            "Set $envVar environment variable to run this test. " +
            "Example: $envVar=sk-xxx ./gradlew test --tests \"*YourTest*\"",
            value != null && value.isNotBlank()
        )
        return value!!
    }
    
    /**
     * Requires a file to exist.
     * 
     * @param filePath Path to the required file
     * @param description Human-readable description of what the file is
     * 
     * Skip message shown to developer:
     * "Skipping: Model file 'models/sherpa.onnx' not found. Download from: ..."
     */
    fun requireFile(filePath: String, description: String = "Required file") {
        val file = File(filePath)
        assumeTrue(
            "$description not found at: $filePath",
            file.exists()
        )
    }
    
    /**
     * Requires a model file to be present.
     * 
     * @param modelPath Path to the model file
     * @param downloadUrl Optional URL where model can be downloaded
     */
    fun requireModel(modelPath: String, downloadUrl: String? = null) {
        val file = File(modelPath)
        val message = if (downloadUrl != null) {
            "Model file not found: $modelPath. Download from: $downloadUrl"
        } else {
            "Model file not found: $modelPath"
        }
        assumeTrue(message, file.exists())
    }
    
    /**
     * Requires a native library to be available.
     * 
     * @param libraryName Name of the native library (e.g. "llm_jni")
     */
    fun requireNativeLibrary(libraryName: String) {
        val available = try {
            System.loadLibrary(libraryName)
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
        assumeTrue(
            "Native library '$libraryName' not available. Build the native libraries first.",
            available
        )
    }
    
    /**
     * Requires network connectivity.
     * Tests basic internet access.
     */
    fun requireNetwork() {
        val hasNetwork = try {
            java.net.InetAddress.getByName("google.com").isReachable(3000)
        } catch (e: Exception) {
            false
        }
        assumeTrue(
            "Network connectivity required. Check internet connection.",
            hasNetwork
        )
    }
    
    /**
     * Requires specific hardware (MTK NPU).
     * Only passes when running on actual MTK device with NPU support.
     */
    fun requireMTKNPU() {
        val hasMTK = try {
            Class.forName("com.mtkresearch.breezeapp.engine.runner.mtk.MTKUtils")
                .getMethod("isMTKNPUSupported")
                .invoke(null) as? Boolean ?: false
        } catch (e: Exception) {
            false
        }
        assumeTrue(
            "MTK NPU hardware required. Run on MTK device with NPU support.",
            hasMTK
        )
    }
    
    /**
     * Requires an Android Context (fails in pure JVM tests).
     * Use this for tests that need Robolectric or instrumentation.
     */
    fun requireAndroidContext() {
        val hasContext = try {
            Class.forName("android.content.Context")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
        assumeTrue(
            "Android Context required. Run with Robolectric or as instrumented test.",
            hasContext
        )
    }
    
    /**
     * Custom prerequisite with custom message.
     * 
     * @param condition The condition that must be true
     * @param skipMessage Message shown when condition is false
     */
    fun require(condition: Boolean, skipMessage: String) {
        assumeTrue(skipMessage, condition)
    }
}
