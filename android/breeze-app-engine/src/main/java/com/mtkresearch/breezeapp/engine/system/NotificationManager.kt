package com.mtkresearch.breezeapp.engine.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mtkresearch.breezeapp.engine.R
import com.mtkresearch.breezeapp.engine.model.NotificationPriority
import com.mtkresearch.breezeapp.engine.model.ServiceState
import com.mtkresearch.breezeapp.engine.ui.BreezeAppEngineLauncherActivity
import android.util.Log

/**
 * Notification Manager - Unified notification management
 * 
 * This class combines notification permission management and notification creation
 * into a single, cohesive component.
 * 
 * Responsibilities:
 * - Create and manage notification channels
 * - Build notifications based on service state
 * - Handle notification permission requests
 * - Provide notification status updates
 */
class NotificationManager(private val context: Context) {
    
    companion object {
        private const val TAG = "NotificationManager"
        const val CHANNEL_ID = "ai_engine_service_channel"
        const val CHANNEL_NAME = "BreezeApp Engine Service"
        const val CHANNEL_DESCRIPTION = "Shows BreezeApp Engine service status and progress"
        
        private const val REQUEST_CODE_MAIN = 1001
        private const val REQUEST_CODE_STOP = 1002
        const val ACTION_STOP_SERVICE = "com.mtkresearch.breezeapp.engine.STOP_SERVICE"
        const val ACTION_VIEW_HEALTH = "com.mtkresearch.breezeapp.engine.VIEW_HEALTH"
    }
    
    private val systemNotificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    
    private val permissionManager = PermissionManager(context)
    
    /**
     * Creates the notification channel required for foreground service
     */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                setBypassDnd(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            systemNotificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Checks if notifications are enabled for this app
     */
    fun areNotificationsEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = systemNotificationManager.getNotificationChannel(CHANNEL_ID)
            systemNotificationManager.areNotificationsEnabled() && 
            (channel?.importance != NotificationManager.IMPORTANCE_NONE)
        } else {
            systemNotificationManager.areNotificationsEnabled()
        }
    }
    
    /**
     * Opens the app's notification settings
     */
    fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
        context.startActivity(intent)
    }
    
    /**
     * Creates a notification based on service state
     */
    fun createNotification(state: ServiceState): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(state.getIcon())
            .setContentTitle("BreezeApp Engine")
            .setContentText(state.getDisplayText())
            .setPriority(getNotificationPriority(state))
            .setOngoing(state.isOngoing())
            .setAutoCancel(false)
        
        // Add progress indicator if needed
        if (state.showProgress()) {
            if (state.isIndeterminate()) {
                builder.setProgress(0, 0, true)
            } else {
                builder.setProgress(state.getProgressMax(), state.getProgressValue(), false)
            }
        }
        
        // Add action buttons
        addNotificationActions(builder, state)
        
        return builder.build()
    }
    
    /**
     * Updates the current notification
     */
    fun updateNotification(state: ServiceState) {
        try {
            val notification = createNotification(state)
            systemNotificationManager.notify(
                com.mtkresearch.breezeapp.engine.core.BreezeAppEngineStatusManager.FOREGROUND_NOTIFICATION_ID,
                notification
            )
            Log.v(TAG, "Notification updated for state: ${state::class.simpleName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification", e)
        }
    }
    
    /**
     * Clears the current notification
     */
    fun clearNotification() {
        try {
            systemNotificationManager.cancel(
                com.mtkresearch.breezeapp.engine.core.BreezeAppEngineStatusManager.FOREGROUND_NOTIFICATION_ID
            )
            Log.d(TAG, "Notification cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing notification", e)
        }
    }
    
    /**
     * Gets notification priority based on service state
     */
    private fun getNotificationPriority(state: ServiceState): Int {
        return when (state.getNotificationPriority()) {
            NotificationPriority.LOW -> NotificationCompat.PRIORITY_LOW
            NotificationPriority.DEFAULT -> NotificationCompat.PRIORITY_DEFAULT
            NotificationPriority.HIGH -> NotificationCompat.PRIORITY_HIGH
        }
    }
    
    /**
     * Adds action buttons to notification
     */
    private fun addNotificationActions(builder: NotificationCompat.Builder, state: ServiceState) {
        // Stop service action
        val stopIntent = Intent(ACTION_STOP_SERVICE).apply {
            `package` = context.packageName
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_STOP,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Service", stopPendingIntent)
        
        // Open app action
        val openIntent = Intent(context, BreezeAppEngineLauncherActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_MAIN,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(openPendingIntent)
    }
    
    // Permission management methods (delegated to PermissionManager)
    
    /**
     * Checks if notification permission is required
     */
    fun isNotificationPermissionRequired(): Boolean = 
        permissionManager.isNotificationPermissionRequired()
    
    /**
     * Checks if notification permission is granted
     */
    fun isNotificationPermissionGranted(): Boolean = 
        permissionManager.isNotificationPermissionGranted()
    
    /**
     * Checks if we should show rationale for notification permission
     */
    fun shouldShowNotificationRationale(activity: android.app.Activity): Boolean = 
        permissionManager.shouldShowNotificationRationale(activity)
    
    /**
     * Requests notification permission
     */
    fun requestNotificationPermission(activity: android.app.Activity) = 
        permissionManager.requestNotificationPermission(activity)
    
    /**
     * Handles notification permission result
     */
    fun handleNotificationPermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean = 
        permissionManager.handlePermissionResult(requestCode, permissions, grantResults)
} 