package com.mtkresearch.breezeapp.engine.system

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import com.mtkresearch.breezeapp.engine.model.ServiceState
import com.mtkresearch.breezeapp.engine.ui.BreathingBorderView

class BreathingBorderManager(private val context: Context) {
    
    private val windowManager: WindowManager by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    
    private var overlayView: BreathingBorderView? = null
    private var isOverlayVisible = false
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // For minimum display time
    private var borderVisibilityStartTime = 0L
    private val hideBorderRunnable = Runnable { hideBreathingBorderInternal() }
    
    companion object {
        private const val TAG = "BreathingBorderManager"
        private const val MINIMUM_BORDER_VISIBLE_MS = 1000L
    }
    
    fun isOverlayVisible(): Boolean {
        return isOverlayVisible && overlayView != null
    }
    
    fun isPermissionGranted(): Boolean {
        return Settings.canDrawOverlays(context)
    }
    
    fun showBreathingBorder(state: ServiceState) {
        if (!isPermissionGranted()) {
            Log.w(TAG, "Cannot show breathing border: SYSTEM_ALERT_WINDOW permission not granted")
            return
        }
        
        if (!state.shouldShowBreathingBorder()) {
            hideBreathingBorder()
            return
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (!isValidStateForOverlay()) {
                Log.w(TAG, "Cannot show breathing border: Invalid state for overlay on Android 15+")
                return
            }
        }
        
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showBreathingBorderInternal(state)
        } else {
            mainHandler.post { showBreathingBorderInternal(state) }
        }
    }
    
    private fun isValidStateForOverlay(): Boolean {
        return try {
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
    
    private fun showBreathingBorderInternal(state: ServiceState) {
        try {
            mainHandler.removeCallbacks(hideBorderRunnable)
            
            if (overlayView == null) {
                createOverlayView()
            }
            
            if (!isOverlayVisible) {
                borderVisibilityStartTime = System.currentTimeMillis()
            }
            
            overlayView?.startAnimation(state.getBreathingBorderColor())
            isOverlayVisible = true
            
            Log.d(TAG, "Breathing border shown for state: ${state::class.simpleName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing breathing border", e)
        }
    }
    
    fun hideBreathingBorder() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            val visibleDuration = System.currentTimeMillis() - borderVisibilityStartTime
            if (isOverlayVisible && visibleDuration < MINIMUM_BORDER_VISIBLE_MS) {
                val delay = MINIMUM_BORDER_VISIBLE_MS - visibleDuration
                mainHandler.postDelayed(hideBorderRunnable, delay)
            } else {
                hideBreathingBorderInternal()
            }
        } else {
            mainHandler.post { hideBreathingBorder() }
        }
    }
    
    private fun hideBreathingBorderInternal() {
        try {
            mainHandler.removeCallbacks(hideBorderRunnable)
            
            overlayView?.stopAnimation()
            removeOverlayView()
            isOverlayVisible = false
            
            Log.d(TAG, "Breathing border hidden")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding breathing border", e)
        }
    }
    
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
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or  // Extend beyond screen bounds
            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,  // Draw under system bars
            PixelFormat.TRANSLUCENT
        )

        // Position at top-left corner of physical screen
        params.x = 0
        params.y = 0

        // For API 21+, ensure we can draw edge-to-edge
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
        }

        windowManager.addView(overlayView, params)
        Log.d(TAG, "Overlay view created and added to window manager with full-screen edge-to-edge layout")
    }
    
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
    
    fun cleanup() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            hideBreathingBorderInternal()
        } else {
            mainHandler.post { hideBreathingBorderInternal() }
        }
        Log.d(TAG, "Breathing border manager cleaned up")
    }
}