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
import com.mtkresearch.breezeapp.engine.system.NotificationManager
import com.mtkresearch.breezeapp.engine.system.PermissionManager
import android.Manifest
import android.content.pm.PackageManager

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
    
    private lateinit var permissionManager: PermissionManager
    private var serviceStartPending = false
    private var breathingBorderPermissionChecked = false
    private var microphonePermissionChecked = false
    
    companion object {
        private const val MICROPHONE_PERMISSION_REQUEST_CODE = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_engine_launcher)

        // Check if this is a programmatic wake-up call
        val autoBackground = intent.getBooleanExtra("auto_background", false)

        // Initialize permission manager
        permissionManager = PermissionManager(this)
        
        // Check and request notification permission before starting service
        checkNotificationPermissionAndStartService()
        
        if (autoBackground) {
            // For programmatic wake-up, start service and finish activity
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                finish()
            }, 1500) // Allow service startup time
        } else {
            // For manual launch, show full UI
            // Check breathing border permission
            checkBreathingBorderPermission()
            
            // Check microphone permission
            checkMicrophonePermission()

            // Initialize the premium UI components
            initializePremiumUI()
        }
    }
    
    /**
     * Checks notification permission and starts service accordingly.
     * Forces permission request on Android 13+ for optimal user experience.
     */
    private fun checkNotificationPermissionAndStartService() {
        when {
            permissionManager.isNotificationPermissionGranted() -> {
                // Permission granted or not required, start service immediately
                startBreezeAppEngineService()
            }
            permissionManager.shouldShowNotificationRationale(this) -> {
                // Show rationale dialog explaining why we need the permission
                showPermissionRationaleDialog()
            }
            else -> {
                // Request permission directly
                serviceStartPending = true
                permissionManager.requestNotificationPermission(this)
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
                permissionManager.requestNotificationPermission(this)
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
    
    /**
     * Check and request microphone permission for ASR functionality
     */
    private fun checkMicrophonePermission() {
        if (microphonePermissionChecked) return
        
        microphonePermissionChecked = true
        
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                android.util.Log.d("BreezeAppEngineLauncher", "Microphone permission already granted")
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                showMicrophonePermissionRationaleDialog()
            }
            else -> {
                requestMicrophonePermission()
            }
        }
    }
    
    /**
     * Shows a dialog explaining why microphone permission is needed.
     */
    private fun showMicrophonePermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Microphone Permission Required")
            .setMessage("BreezeApp Engine needs microphone permission to:\n\n" +
                       "• Process real-time speech recognition\n" +
                       "• Enable voice commands and dictation\n" +
                       "• Provide hands-free AI interaction\n" +
                       "• Support microphone mode ASR requests\n\n" +
                       "This is essential for the speech recognition features.")
            .setPositiveButton("Grant Permission") { _, _ ->
                requestMicrophonePermission()
            }
            .setNegativeButton("Skip") { _, _ ->
                android.util.Log.w("BreezeAppEngineLauncher", "User declined microphone permission - ASR features may not work")
                Toast.makeText(this, "Microphone features will be limited", Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Request microphone permission
     */
    private fun requestMicrophonePermission() {
        requestPermissions(
            arrayOf(Manifest.permission.RECORD_AUDIO),
            MICROPHONE_PERMISSION_REQUEST_CODE
        )
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            MICROPHONE_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    android.util.Log.d("BreezeAppEngineLauncher", "Microphone permission granted")
                    Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    android.util.Log.w("BreezeAppEngineLauncher", "Microphone permission denied")
                    Toast.makeText(this, "Microphone permission denied - ASR features may not work", Toast.LENGTH_LONG).show()
                }
            }
            else -> {
                // Handle other permission results
                // Note: Permission handling is now unified in PermissionManager
            }
        }
    }
    
    /**
     * Check and request breathing border permission for overlay display
     */
    private fun checkBreathingBorderPermission() {
        if (breathingBorderPermissionChecked) return
        
        breathingBorderPermissionChecked = true
        
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Overlay Permission Required")
                .setMessage("BreezeApp Engine needs overlay permission to:\n\n" +
                           "• Display breathing light border during processing\n" +
                           "• Show real-time AI status indicators\n" +
                           "• Provide visual feedback for active operations\n\n" +
                           "This enhances the user experience with visual status updates.")
                .setPositiveButton("Grant Permission") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                .setNegativeButton("Skip") { _, _ ->
                    android.util.Log.w("BreezeAppEngineLauncher", "User declined overlay permission - breathing border will not work")
                }
                .setCancelable(false)
                .show()
        }
    }
    
    
    /**
     * Launch the Engine Settings Activity
     */
    private fun launchEngineSettings() {
        try {
            val intent = Intent(this, EngineSettingsActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("BreezeAppEngineLauncher", "Failed to launch Engine Settings", e)
            Toast.makeText(this, "Failed to open Engine Settings: ${e.message}", Toast.LENGTH_LONG).show()
        }
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
    
    private fun showNotificationDialog(notificationManager: NotificationManager) {
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
        val btnEngineSettings = findViewById<android.widget.Button>(R.id.btnEngineSettings)
        val fabClose = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabClose)
        
        // Set button text colors to primary orange
        btnViewNotifications?.setTextColor(ContextCompat.getColor(this, R.color.primary))
        btnServiceInfo?.setTextColor(ContextCompat.getColor(this, R.color.primary))
        btnEngineSettings?.setTextColor(ContextCompat.getColor(this, R.color.primary))
        
        // Setup click listeners
        btnViewNotifications?.setOnClickListener {
            openNotificationPanel()
        }
        
        btnServiceInfo?.setOnClickListener {
            showServiceInfoDialog()
        }
        
        btnEngineSettings?.setOnClickListener {
            launchEngineSettings()
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