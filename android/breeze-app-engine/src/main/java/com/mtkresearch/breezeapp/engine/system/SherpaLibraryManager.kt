package com.mtkresearch.breezeapp.engine.system

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Global manager for Sherpa ONNX native libraries
 * Ensures libraries are loaded only once across the application lifecycle
 * Integrates with existing GlobalLibraryTracker and NativeLibraryGuardian
 */
object SherpaLibraryManager {
    private const val TAG = "SherpaLibraryManager"
    private const val SHERPA_LIBRARY_ID = "sherpa-onnx-jni"
    
    private val isLibraryLoaded = AtomicBoolean(false)
    private val isInitialized = AtomicBoolean(false)
    
    /**
     * Initialize complete global library system (Sherpa + GlobalLibraryTracker)
     * This should be called once during service startup
     */
    fun initializeCompleteSystem(context: android.content.Context): Boolean {
        return try {
            Log.d(TAG, "Initializing complete global library system...")
            
            // Initialize global library tracking system first
            GlobalLibraryTracker.initialize(context)
            Log.d(TAG, "Global library tracker initialized successfully")
            
            // Then initialize Sherpa ONNX library
            val sherpaSuccess = initializeGlobally()
            if (sherpaSuccess) {
                Log.i(TAG, "Complete global library system initialized successfully")
            } else {
                Log.e(TAG, "Sherpa library initialization failed")
            }
            
            sherpaSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Exception during complete system initialization", e)
            false
        }
    }
    
    /**
     * Initialize and load Sherpa ONNX library globally
     * This should be called once during application startup
     */
    fun initializeGlobally(): Boolean {
        if (isInitialized.get()) {
            Log.d(TAG, "Sherpa library already initialized")
            return isLibraryLoaded.get()
        }
        
        return try {
            Log.d(TAG, "Loading Sherpa ONNX native library...")
            System.loadLibrary(SHERPA_LIBRARY_ID)
            
            // Register with existing library tracking system
            NativeLibraryGuardian.registerLibrary(
                libraryId = SHERPA_LIBRARY_ID,
                cleanupHandler = { cleanup() },
                metadata = mapOf(
                    "type" to "sherpa-onnx",
                    "version" to "1.12.6",
                    "capabilities" to listOf("ASR", "TTS")
                )
            )
            
            isLibraryLoaded.set(true)
            isInitialized.set(true)
            Log.i(TAG, "Sherpa ONNX library loaded successfully")
            true
        } catch (e: UnsatisfiedLinkError) {
            val errorMsg = e.message?.lowercase() ?: ""
            if (errorMsg.contains("already loaded")) {
                Log.d(TAG, "Sherpa library already loaded in process")
                isLibraryLoaded.set(true)
                isInitialized.set(true)
                true
            } else {
                Log.e(TAG, "Failed to load Sherpa ONNX library", e)
                isLibraryLoaded.set(false)
                isInitialized.set(true)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error loading Sherpa library", e)
            isLibraryLoaded.set(false)
            isInitialized.set(true)
            false
        }
    }
    
    /**
     * Check if Sherpa library is loaded and ready for use
     */
    fun isLibraryReady(): Boolean {
        val libraryLoaded = isLibraryLoaded.get()
        val globalUsable = GlobalLibraryTracker.isLibraryUsable()
        val result = libraryLoaded && globalUsable
        Log.d(TAG, "Library ready check: sherpaLoaded=$libraryLoaded, globalUsable=$globalUsable, result=$result")
        return result
    }
    
    /**
     * Mark that Sherpa inference is starting
     * Integrates with GlobalLibraryTracker for resource management
     */
    fun markInferenceStarted() {
        GlobalLibraryTracker.markInferenceStarted()
        Log.d(TAG, "Sherpa inference started")
    }
    
    /**
     * Mark that Sherpa inference has completed
     */
    fun markInferenceCompleted() {
        GlobalLibraryTracker.markInferenceCompleted()
        Log.d(TAG, "Sherpa inference completed")
    }
    
    /**
     * Get diagnostic information about library state
     */
    fun getDiagnosticInfo(): Map<String, Any> {
        val globalInfo = GlobalLibraryTracker.getDiagnosticInfo()
        return globalInfo + mapOf(
            "sherpaLibraryLoaded" to isLibraryLoaded.get(),
            "sherpaInitialized" to isInitialized.get(),
            "sherpaLibraryId" to SHERPA_LIBRARY_ID
        )
    }
    
    /**
     * Cleanup resources (called by NativeLibraryGuardian)
     */
    private fun cleanup() {
        Log.d(TAG, "Cleaning up Sherpa library resources")
        // Note: We don't unload the native library as it may be used by other components
        // Just mark inference as completed if it was running
        markInferenceCompleted()
    }
    
    /**
     * Force cleanup all Sherpa resources
     * Should only be used in emergency situations
     */
    fun forceCleanup() {
        cleanup()
        NativeLibraryGuardian.unregisterLibrary(SHERPA_LIBRARY_ID)
        Log.w(TAG, "Force cleanup completed")
    }
}