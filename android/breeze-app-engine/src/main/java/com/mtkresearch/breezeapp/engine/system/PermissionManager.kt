package com.mtkresearch.breezeapp.engine.system

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mtkresearch.breezeapp.engine.model.PermissionState

/**
 * Permission Manager - Unified permission and audio focus management
 * 
 * This class handles all required permissions and audio focus for the BreezeApp Engine:
 * - Notification permission (Android 13+)
 * - Microphone permission (for ASR)
 * - Overlay permission (for breathing border)
 * - Audio focus management (for microphone recording)
 * 
 * Responsibilities:
 * - Check permission status
 * - Request permissions with rationale
 * - Handle permission results
 * - Manage audio focus for recording
 * - Provide unified permission state updates
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
    
    // Audio management
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus: Boolean = false
    
    // Audio focus change listener
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                Log.d(TAG, "AUDIOFOCUS_GAIN: Acquired audio focus")
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                Log.w(TAG, "AUDIOFOCUS_LOSS: Lost audio focus permanently")
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                Log.w(TAG, "AUDIOFOCUS_LOSS_TRANSIENT: Lost audio focus temporarily")
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasAudioFocus = false
                Log.w(TAG, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: Lost audio focus, can duck")
            }
        }
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
     * Checks if audio focus is currently granted
     */
    fun hasAudioFocus(): Boolean {
        return hasAudioFocus
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
     * Requests audio focus for microphone recording
     */
    fun requestAudioFocus(): Boolean {
        return try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
                
            val focusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
            } else {
                // For older Android versions, we'll create a temporary request object
                null
            }
            
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.requestAudioFocus(focusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
            
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                hasAudioFocus = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    this.audioFocusRequest = focusRequest
                }
                Log.i(TAG, "Audio focus granted for microphone access")
                true
            } else {
                hasAudioFocus = false
                Log.w(TAG, "Audio focus request denied for microphone access")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting audio focus", e)
            false
        }
    }
    
    /**
     * Abandons audio focus
     */
    fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { request ->
                    audioManager.abandonAudioFocusRequest(request)
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(audioFocusChangeListener)
            }
            hasAudioFocus = false
            audioFocusRequest = null
            Log.d(TAG, "Audio focus abandoned")
        } catch (e: Exception) {
            Log.e(TAG, "Error abandoning audio focus", e)
        }
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