package com.mtkresearch.breezeapp.engine.system

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mtkresearch.breezeapp.engine.model.PermissionState

/**
 * Permission Manager - Unified permission management
 * 
 * This class handles all required permissions for the BreezeApp Engine:
 * - Notification permission (Android 13+)
 * - Microphone permission (for ASR)
 * - Overlay permission (for breathing border)
 * 
 * Responsibilities:
 * - Check permission status
 * - Request permissions with rationale
 * - Handle permission results
 * - Provide permission state updates
 */
class PermissionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "PermissionManager"
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
        const val MICROPHONE_PERMISSION_REQUEST_CODE = 1002
        const val OVERLAY_PERMISSION_REQUEST_CODE = 1003
        private const val PERMISSION_POST_NOTIFICATIONS = Manifest.permission.POST_NOTIFICATIONS
        private const val PERMISSION_RECORD_AUDIO = Manifest.permission.RECORD_AUDIO
    }
    
    /**
     * Checks if notification permission is required for the current Android version
     */
    fun isNotificationPermissionRequired(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }
    
    /**
     * Checks if notification permission is currently granted
     */
    fun isNotificationPermissionGranted(): Boolean {
        return if (isNotificationPermissionRequired()) {
            ContextCompat.checkSelfPermission(context, PERMISSION_POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            // Permission not required on Android < 13
            true
        }
    }
    
    /**
     * Checks if microphone permission is currently granted
     */
    fun isMicrophonePermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(context, PERMISSION_RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Checks if overlay permission is currently granted
     */
    fun isOverlayPermissionGranted(): Boolean {
        return Settings.canDrawOverlays(context)
    }
    
    /**
     * Gets current permission state
     */
    fun getCurrentPermissionState(): PermissionState {
        return PermissionState(
            notificationGranted = isNotificationPermissionGranted(),
            microphoneGranted = isMicrophonePermissionGranted(),
            overlayGranted = isOverlayPermissionGranted()
        )
    }
    
    /**
     * Checks if all required permissions are granted
     */
    fun isAllRequiredPermissionsGranted(): Boolean {
        return getCurrentPermissionState().isAllGranted()
    }
    
    /**
     * Checks if we should show rationale for notification permission
     */
    fun shouldShowNotificationRationale(activity: Activity): Boolean {
        return if (isNotificationPermissionRequired()) {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, PERMISSION_POST_NOTIFICATIONS)
        } else {
            false
        }
    }
    
    /**
     * Checks if we should show rationale for microphone permission
     */
    fun shouldShowMicrophoneRationale(activity: Activity): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, PERMISSION_RECORD_AUDIO)
    }
    
    /**
     * Requests notification permission
     */
    fun requestNotificationPermission(activity: Activity) {
        if (isNotificationPermissionRequired()) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(PERMISSION_POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        }
    }
    
    /**
     * Requests microphone permission
     */
    fun requestMicrophonePermission(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(PERMISSION_RECORD_AUDIO),
            MICROPHONE_PERMISSION_REQUEST_CODE
        )
    }
    
    /**
     * Opens overlay permission settings
     */
    fun openOverlayPermissionSettings(activity: Activity) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}")
        )
        activity.startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
    }
    
    /**
     * Requests all required permissions
     */
    fun requestAllPermissions(activity: Activity) {
        Log.d(TAG, "Requesting all required permissions")
        
        // Request notification permission if needed
        if (!isNotificationPermissionGranted()) {
            requestNotificationPermission(activity)
        }
        
        // Request microphone permission if needed
        if (!isMicrophonePermissionGranted()) {
            requestMicrophonePermission(activity)
        }
        
        // Note: Overlay permission requires manual settings navigation
        if (!isOverlayPermissionGranted()) {
            Log.d(TAG, "Overlay permission requires manual settings navigation")
        }
    }
    
    /**
     * Handles permission request results
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        return when (requestCode) {
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                val permissionIndex = permissions.indexOf(PERMISSION_POST_NOTIFICATIONS)
                if (permissionIndex >= 0 && permissionIndex < grantResults.size) {
                    val granted = grantResults[permissionIndex] == PackageManager.PERMISSION_GRANTED
                    Log.d(TAG, "Notification permission result: $granted")
                    granted
                } else {
                    false
                }
            }
            MICROPHONE_PERMISSION_REQUEST_CODE -> {
                val permissionIndex = permissions.indexOf(PERMISSION_RECORD_AUDIO)
                if (permissionIndex >= 0 && permissionIndex < grantResults.size) {
                    val granted = grantResults[permissionIndex] == PackageManager.PERMISSION_GRANTED
                    Log.d(TAG, "Microphone permission result: $granted")
                    granted
                } else {
                    false
                }
            }
            else -> false
        }
    }
    
    /**
     * Gets permission descriptions for UI display
     */
    fun getPermissionDescriptions(): Map<String, String> {
        return mapOf(
            "Notification" to "Required for service status notifications",
            "Microphone" to "Required for speech recognition features",
            "Overlay" to "Required for breathing border display"
        )
    }
} 