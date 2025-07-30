package com.mtkresearch.breezeapp.engine.system

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import com.mtkresearch.breezeapp.engine.domain.model.ServiceState
import com.mtkresearch.breezeapp.engine.ui.BreathingBorderView

/**
 * Breathing Border Manager - Manages the ambient breathing light border
 * 
 * This class handles the overlay window that displays a subtle breathing
 * light border around the screen to indicate BreezeApp Engine service status.
 * 
 * Responsibilities:
 * - Check and request SYSTEM_ALERT_WINDOW permission
 * - Show/hide breathing border based on service state
 * - Manage overlay window lifecycle
 * - Ensure non-intrusive behavior
 */
class BreathingBorderManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BreathingBorderManager"
    }
    
    private val windowManager: WindowManager by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    
    private var overlayView: BreathingBorderView? = null
    private var isOverlayVisible = false
    private val mainHandler = Handler(Looper.getMainLooper())
    
    /**
     * Check if the overlay is currently visible
     */
    fun isOverlayVisible(): Boolean {
        return isOverlayVisible && overlayView != null
    }
    
    /**
     * Check if SYSTEM_ALERT_WINDOW permission is granted
     */
    fun isPermissionGranted(): Boolean {
        return Settings.canDrawOverlays(context)
    }
    
    /**
     * Show breathing border for the given service state
     * Must be called on main thread for UI operations
     * 
     * Android 15 Compliance: Ensure overlay window is visible before showing border
     */
    fun showBreathingBorder(state: ServiceState) {
        if (!isPermissionGranted()) {
            Log.w(TAG, "Cannot show breathing border: SYSTEM_ALERT_WINDOW permission not granted")
            return
        }
        
        if (!state.shouldShowBreathingBorder()) {
            hideBreathingBorder()
            return
        }
        
        // Android 15: Ensure overlay window is visible before showing border
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Check if we're in a valid state to show overlay
            if (!isValidStateForOverlay()) {
                Log.w(TAG, "Cannot show breathing border: Invalid state for overlay on Android 15+")
                return
            }
        }
        
        // Ensure UI operations run on main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showBreathingBorderInternal(state)
        } else {
            mainHandler.post { showBreathingBorderInternal(state) }
        }
    }
    
    /**
     * Check if current state is valid for showing overlay on Android 15+
     */
    private fun isValidStateForOverlay(): Boolean {
        // On Android 15+, overlay windows must be visible and the app must be in foreground
        // or running a foreground service
        return try {
            // Check if we have a valid window or foreground service
            val hasValidWindow = overlayView?.windowVisibility == android.view.View.VISIBLE
            val hasForegroundService = context.getSystemService(android.app.ActivityManager::class.java)
                ?.getRunningServices(Int.MAX_VALUE)
                ?.any { it.service.packageName == context.packageName && it.foreground }
                ?: false
            
            hasValidWindow || hasForegroundService
        } catch (e: Exception) {
            Log.w(TAG, "Error checking overlay state validity", e)
            false
        }
    }
    
    /**
     * Internal method to show breathing border (must be called on main thread)
     */
    private fun showBreathingBorderInternal(state: ServiceState) {
        try {
            if (overlayView == null) {
                createOverlayView()
            }
            
            overlayView?.startAnimation(state.getBreathingBorderColor())
            isOverlayVisible = true
            
            Log.d(TAG, "Breathing border shown for state: ${state::class.simpleName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing breathing border", e)
        }
    }
    
    /**
     * Hide the breathing border
     * Must be called on main thread for UI operations
     */
    fun hideBreathingBorder() {
        // Ensure UI operations run on main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            hideBreathingBorderInternal()
        } else {
            mainHandler.post { hideBreathingBorderInternal() }
        }
    }
    
    /**
     * Internal method to hide breathing border (must be called on main thread)
     */
    private fun hideBreathingBorderInternal() {
        try {
            overlayView?.stopAnimation()
            removeOverlayView()
            isOverlayVisible = false
            
            Log.d(TAG, "Breathing border hidden")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding breathing border", e)
        }
    }
    
    /**
     * Create and add the overlay view to window manager
     */
    private fun createOverlayView() {
        overlayView = BreathingBorderView(context)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            // Critical flags for non-intrusive behavior
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        
        windowManager.addView(overlayView, params)
        Log.d(TAG, "Overlay view created and added to window manager")
    }
    
    /**
     * Remove the overlay view from window manager
     */
    private fun removeOverlayView() {
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
                overlayView = null
                Log.d(TAG, "Overlay view removed from window manager")
            } catch (e: Exception) {
                Log.w(TAG, "Error removing overlay view", e)
            }
        }
    }
    
    /**
     * Clean up resources
     * Must be called on main thread for UI operations
     */
    fun cleanup() {
        // Ensure UI operations run on main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            hideBreathingBorderInternal()
        } else {
            mainHandler.post { hideBreathingBorderInternal() }
        }
        Log.d(TAG, "Breathing border manager cleaned up")
    }
} 