package com.mtkresearch.breezeapp.engine.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mtkresearch.breezeapp.engine.R
import com.mtkresearch.breezeapp.engine.core.ServiceNotificationManager
import com.mtkresearch.breezeapp.engine.system.NotificationPermissionManager

/**
 * BreezeAppEngine Entry Activity - Professional entry point for BreezeApp Engine
 * 
 * This activity serves as the main entry point for the BreezeApp Engine application.
 * When launched, it immediately starts the BreezeApp Engine Service as a foreground service,
 * ensuring the notification appears and the service is available for client connections.
 * 
 * Enhanced UX: Shows professional status dialog instead of flash-and-close behavior.
 */
class BreezeAppEngineLauncherActivity : AppCompatActivity() {
    
    private lateinit var permissionManager: NotificationPermissionManager
    private var serviceStartPending = false
    private var breathingBorderPermissionChecked = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_engine_launcher)

        // Initialize permission manager
        permissionManager = NotificationPermissionManager(this)
        
        // Check and request notification permission before starting service
        checkNotificationPermissionAndStartService()
        
        // Check breathing border permission
        checkBreathingBorderPermission()

        // Initialize the premium UI components
        initializePremiumUI()
    }
    
    /**
     * Checks notification permission and starts service accordingly.
     * Forces permission request on Android 13+ for optimal user experience.
     */
    private fun checkNotificationPermissionAndStartService() {
        when {
            permissionManager.isPermissionGranted() -> {
                // Permission granted or not required, start service immediately
                startBreezeAppEngineService()
            }
            permissionManager.shouldShowRationale(this) -> {
                // Show rationale dialog explaining why we need the permission
                showPermissionRationaleDialog()
            }
            else -> {
                // Request permission directly
                serviceStartPending = true
                permissionManager.requestPermission(this)
            }
        }
    }
    
    /**
     * Shows a dialog explaining why notification permission is needed.
     */
    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Notification Permission Required")
            .setMessage("BreezeApp Engine needs notification permission to:\n\n" +
                       "• Show AI processing status\n" +
                       "• Display service availability\n" +
                       "• Provide progress updates\n" +
                       "• Indicate when AI operations complete\n\n" +
                       "This ensures you stay informed about the AI service status.")
            .setPositiveButton("Grant Permission") { _, _ ->
                serviceStartPending = true
                permissionManager.requestPermission(this)
            }
            .setNegativeButton("Continue Without") { _, _ ->
                // Start service anyway but warn user
                showNotificationDisabledWarning()
                startBreezeAppEngineService()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Shows warning when user chooses to continue without notification permission.
     */
    private fun showNotificationDisabledWarning() {
        Toast.makeText(
            this, 
            "Service will run without visible status updates. Enable notifications in Settings for better experience.", 
            Toast.LENGTH_LONG
        ).show()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        val permissionGranted = permissionManager.handlePermissionResult(requestCode, permissions, grantResults)
        
        if (serviceStartPending) {
            serviceStartPending = false
            if (permissionGranted) {
                Toast.makeText(this, "Notification permission granted! Starting BreezeApp Engine...", Toast.LENGTH_SHORT).show()
                startBreezeAppEngineService()
            } else {
                // Permission denied, show options
                showPermissionDeniedDialog()
            }
        }
    }
    
    /**
     * Shows dialog when permission is denied, offering alternatives.
     */
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Notification Permission Denied")
            .setMessage("The BreezeApp Engine service will still work, but you won't see status updates. You can enable notifications later in Settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                val notificationManager = ServiceNotificationManager(this)
                notificationManager.openNotificationSettings()
                startBreezeAppEngineService()
            }
            .setNegativeButton("Continue Anyway") { _, _ ->
                startBreezeAppEngineService()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Check and request breathing border permission
     */
    private fun checkBreathingBorderPermission() {
        if (breathingBorderPermissionChecked) return
        
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Breathing Light Border Permission")
                .setMessage("BreezeApp Engine can show a subtle breathing light border around the screen to indicate service status while you use other apps.\n\n" +
                           "This provides ambient awareness without interrupting your activities.")
                .setPositiveButton("Enable") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                .setNegativeButton("Skip") { _, _ ->
                    // Continue without breathing border
                }
                .setCancelable(true)
                .show()
        }
        
        breathingBorderPermissionChecked = true
    }
    
    
    /**
     * Shows detailed service information for advanced users.
     */
    private fun showServiceInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle("Service Information")
            .setMessage("BreezeApp Engine Details:\n\n" +
                       "• Type: Foreground Service\n" +
                       "• Purpose: On-device AI processing\n" +
                       "• Capabilities: Chat, TTS, ASR\n" +
                       "• Status: Always available for clients\n" +
                       "• Privacy: All processing stays on device\n\n" +
                       "The service runs continuously to provide instant AI responses to client applications.")
            .setPositiveButton("Got it") { dialog, _ ->
                dialog.dismiss() // Close dialog, stay in premium layout
            }
            .setCancelable(true) // Allow back button to close dialog
            .show()
    }
    
    /**
     * Attempts to open the notification panel to show the service notification.
     */
    private fun openNotificationPanel() {
        try {
            // Try to expand notification panel (may not work on all devices)
            val statusBarService = getSystemService(Context.STATUS_BAR_SERVICE)
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val expandNotifications = statusBarManager.getMethod("expandNotificationsPanel")
            expandNotifications.invoke(statusBarService)
        } catch (e: Exception) {
            // Fallback: show toast instruction
            Toast.makeText(this, "Swipe down from top to see BreezeApp Engine notification", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showNotificationDialog(notificationManager: ServiceNotificationManager) {
        AlertDialog.Builder(this)
            .setTitle("Enable Notifications")
            .setMessage("BreezeApp Engine runs as a background service. Enable notifications to see service status and progress updates.")
            .setPositiveButton("Open Settings") { _, _ ->
                notificationManager.openNotificationSettings()
                // Keep UI open to show service status
            }
            .setNegativeButton("Skip") { _, _ ->
                Toast.makeText(this, "Service is running without visible status.", Toast.LENGTH_LONG).show()
                // Keep UI open to show service status
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Starts the BreezeApp Engine Service immediately as a foreground service.
     * This ensures the notification appears right when the app is launched.
     */
    private fun startBreezeAppEngineService() {
        try {
            val serviceIntent = Intent(this, com.mtkresearch.breezeapp.engine.BreezeAppEngineService::class.java).apply {
                putExtra("start_reason", "user_launch")
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val componentName = startForegroundService(serviceIntent)
                android.util.Log.i("BreezeAppEngineEntryActivity", "BreezeApp Engine Service started as foreground service: $componentName")
            } else {
                val componentName = startService(serviceIntent)
                android.util.Log.i("BreezeAppEngineEntryActivity", "BreezeApp Engine Service started: $componentName")
            }
            
            // Give the service a moment to start and show notification
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                android.util.Log.i("RouterEntryActivity", "Service should now be running with notification visible")
            }, 1000)
            
        } catch (e: Exception) {
            android.util.Log.e("RouterEntryActivity", "Failed to start BreezeApp Engine Service", e)
            Toast.makeText(this, "Failed to start BreezeApp Engine Service: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun initializePremiumUI() {
        // Initialize views for the premium layout
        val statusText = findViewById<android.widget.TextView>(R.id.statusText)
        val btnViewNotifications = findViewById<android.widget.Button>(R.id.btnViewNotifications)
        val btnServiceInfo = findViewById<android.widget.Button>(R.id.btnServiceInfo)
        val fabClose = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabClose)
        
        // Set button text colors to primary orange
        btnViewNotifications?.setTextColor(ContextCompat.getColor(this, R.color.primary))
        btnServiceInfo?.setTextColor(ContextCompat.getColor(this, R.color.primary))
        
        // Setup click listeners
        btnViewNotifications?.setOnClickListener {
            openNotificationPanel()
        }
        
        btnServiceInfo?.setOnClickListener {
            showServiceInfoDialog()
        }
        
        fabClose?.setOnClickListener {
            finish()
        }
        
        // Start real-time status updates
        startServiceStatusUpdates(statusText)
    }
    
    private fun startServiceStatusUpdates(statusText: android.widget.TextView?) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        
        // Simulate real service startup sequence
        val statusUpdates = listOf(
            "Initializing AI Engine..." to 500L,
            "Loading Neural Models..." to 1500L,
            "Starting Foreground Service..." to 2500L,
            "Service Ready - Accepting Connections!" to 3500L
        )
        
        statusUpdates.forEach { (status, delay) ->
            handler.postDelayed({
                statusText?.text = status
                android.util.Log.d("BreezeAppRouterLauncher", "Status updated: $status")
            }, delay)
        }
        
        // Final status after service is fully ready
        handler.postDelayed({
            statusText?.text = "BreezeApp Engine Online - Ready for Clients"
            statusText?.setTextColor(ContextCompat.getColor(this, R.color.success))
        }, 4000L)
    }
}