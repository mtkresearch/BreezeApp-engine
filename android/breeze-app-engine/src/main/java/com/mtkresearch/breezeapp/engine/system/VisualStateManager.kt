package com.mtkresearch.breezeapp.engine.system

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mtkresearch.breezeapp.engine.model.ServiceState

/**
 * Visual State Manager - Centralized visual state management
 * 
 * This class coordinates all visual feedback components based on service state:
 * - Breathing border updates
 * - Notification updates
 * - Status indicators
 * 
 * Responsibilities:
 * - Unified visual state updates
 * - Thread-safe UI operations
 * - Coordinated visual feedback
 */
class VisualStateManager(
    private val context: Context,
    private val breathingBorderManager: BreathingBorderManager,
    private val notificationManager: NotificationManager
) {
    
    companion object {
        private const val TAG = "VisualStateManager"
    }
    
    private val mainHandler = Handler(Looper.getMainLooper())
    
    /**
     * Updates all visual components based on service state
     * Ensures thread-safe UI operations
     */
    fun updateVisualState(state: ServiceState) {
        Log.d(TAG, "Updating visual state: ${state::class.simpleName}")
        
        // Ensure UI operations run on main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            updateVisualStateInternal(state)
        } else {
            mainHandler.post { updateVisualStateInternal(state) }
        }
    }
    
    /**
     * Internal method to update visual state (must be called on main thread)
     */
    private fun updateVisualStateInternal(state: ServiceState) {
        try {
            // Update breathing border
            updateBreathingBorder(state)
            
            // Update notification
            updateNotification(state)
            
            Log.v(TAG, "Visual state updated successfully for: ${state::class.simpleName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating visual state", e)
        }
    }
    
    /**
     * Updates breathing border based on service state
     */
    private fun updateBreathingBorder(state: ServiceState) {
        try {
            if (state.shouldShowBreathingBorder()) {
                breathingBorderManager.showBreathingBorder(state)
                Log.v(TAG, "Breathing border shown for state: ${state::class.simpleName}")
            } else {
                breathingBorderManager.hideBreathingBorder()
                Log.v(TAG, "Breathing border hidden for state: ${state::class.simpleName}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error updating breathing border", e)
        }
    }
    
    /**
     * Updates notification based on service state
     */
    private fun updateNotification(state: ServiceState) {
        try {
            notificationManager.updateNotification(state)
            Log.v(TAG, "Notification updated for state: ${state::class.simpleName}")
        } catch (e: Exception) {
            Log.w(TAG, "Error updating notification", e)
        }
    }
    
    /**
     * Cleanup visual components
     */
    fun cleanup() {
        try {
            breathingBorderManager.cleanup()
            notificationManager.clearNotification()
            Log.d(TAG, "Visual state manager cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up visual state manager", e)
        }
    }
} 