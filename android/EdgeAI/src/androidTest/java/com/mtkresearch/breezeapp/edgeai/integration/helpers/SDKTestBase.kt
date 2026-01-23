package com.mtkresearch.breezeapp.edgeai.integration.helpers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.mtkresearch.breezeapp.edgeai.EdgeAI
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Base class for EdgeAI SDK integration tests.
 * 
 * IMPORTANT: Before running these tests, you MUST:
 * 1. Install BreezeApp Engine: ./gradlew :breeze-app-engine:installDebug
 * 2. Manually start Engine app on device (or use adb command below)
 * 
 * To start Engine via adb:
 * adb shell am start -n com.mtkresearch.breezeapp.engine/.ui.EngineSettingsActivity
 */
abstract class SDKTestBase {
    
    protected lateinit var context: Context
    
    protected fun logReport(message: String) {
        message.lines().forEach { line ->
            System.out.println("[TEST_REPORT] $line")
        }
    }

    @get:Rule
    val watchman = object : TestWatcher() {
        override fun failed(e: Throwable?, description: Description?) {
            logReport("❌ [FAIL] Test ${description?.methodName} failed: ${e?.message}")
        }

        override fun succeeded(description: Description?) {
            logReport("✅ [PASS] Test ${description?.methodName} passed")
        }
    }

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        
        System.out.println("========================================")
        System.out.println("EdgeAI SDK Test Setup")
        System.out.println("========================================")
        
        // Try to start Engine (may fail due to permissions)
        tryStartEngine()
        
        // Wait longer for Service to be ready
        System.out.println("⏳ Waiting for Engine Service to start...")
        Thread.sleep(5000)  // Increased to 5 seconds
        
        // Initialize EdgeAI SDK with retry logic
        runBlocking {
            var attempt = 0
            val maxAttempts = 3
            var lastError: Exception? = null
            
            while (attempt < maxAttempts) {
                attempt++
                System.out.println("🔄 Initialization attempt $attempt/$maxAttempts...")
                
                try {
                    withTimeout(15000) {  // 15 second timeout
                        EdgeAI.initializeAndWait(context, timeoutMs = 15000)
                    }
                    System.out.println("✅ EdgeAI SDK initialized successfully")
                    return@runBlocking
                } catch (e: Exception) {
                    lastError = e
                    System.err.println("❌ Attempt $attempt failed: ${e.message}")
                    
                    if (attempt < maxAttempts) {
                        System.out.println("⏳ Waiting 3 seconds before retry...")
                        delay(3000)
                    }
                }
            }
            
            // All attempts failed
            System.err.println("========================================")
            System.err.println("❌ FAILED TO INITIALIZE EDGEAI SDK")
            System.err.println("========================================")
            System.err.println("Possible causes:")
            System.err.println("1. BreezeApp Engine is not installed")
            System.err.println("   Fix: ./gradlew :breeze-app-engine:installDebug")
            System.err.println("")
            System.err.println("2. Engine Service is not running")
            System.err.println("   Fix: adb shell am start -n com.mtkresearch.breezeapp.engine/.ui.EngineSettingsActivity")
            System.err.println("")
            System.err.println("3. Engine is not configured (no API key)")
            System.err.println("   Fix: Open Engine app and configure in Settings")
            System.err.println("========================================")
            
            throw lastError ?: Exception("Failed to initialize EdgeAI SDK after $maxAttempts attempts")
        }
    }
    
    @After
    fun tearDown() {
        EdgeAI.shutdown()
        System.out.println("🔌 EdgeAI SDK shutdown complete")
    }
    
    /**
     * Try to start Engine Service.
     * This may fail due to permissions, which is OK - user can start manually.
     */
    private fun tryStartEngine() {
        try {
            System.out.println("▶️  Attempting to start BreezeApp Engine...")
            
            val intent = Intent().apply {
                component = ComponentName(
                    "com.mtkresearch.breezeapp.engine",
                    "com.mtkresearch.breezeapp.engine.ui.EngineSettingsActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            
            System.out.println("✅ Engine start command sent")
        } catch (e: Exception) {
            System.err.println("⚠️  Could not auto-start Engine: ${e.message}")
            System.err.println("💡 Please start Engine manually or use:")
            System.err.println("   adb shell am start -n com.mtkresearch.breezeapp.engine/.ui.EngineSettingsActivity")
        }
    }
}
