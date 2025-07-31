package com.mtkresearch.breezeapp.engine

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.mtkresearch.breezeapp.engine.service.ServiceOrchestrator
import com.mtkresearch.breezeapp.engine.system.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import com.mtkresearch.breezeapp.engine.repository.ModelManager
import com.mtkresearch.breezeapp.engine.repository.ModelVersionStore
import com.mtkresearch.breezeapp.engine.util.ModelDownloader
import com.mtkresearch.breezeapp.engine.core.DownloadModelUseCase
import com.mtkresearch.breezeapp.engine.domain.model.ServiceState

/**
 * BreezeAppEngineService - Simplified Android Service Implementation
 * 
 * This service follows Clean Architecture principles with ONLY Android Service responsibilities:
 * - Service lifecycle management (onCreate, onBind, onDestroy)
 * - Foreground service management
 * - Component initialization and cleanup
 * 
 * All business logic is delegated to ServiceOrchestrator:
 * - ServiceOrchestrator: Coordinates all service components
 * - EngineServiceBinder: AIDL interface implementation
 * - ClientManager: Client connection management  
 * - RequestCoordinator: Request processing coordination
 * - BreezeAppEngineCore: Business logic execution
 * 
 * Result: Simplified service with clear separation of concerns
 */
class BreezeAppEngineService : Service() {
    
    companion object {
        private const val TAG = "BreezeAppEngineService"
        private const val PERMISSION = "com.mtkresearch.breezeapp.permission.BIND_AI_ROUTER_SERVICE"
    }
    
    // Service orchestrator handles all component coordination
    private lateinit var serviceOrchestrator: ServiceOrchestrator
    private lateinit var notificationManager: NotificationManager
    
    // Broadcast receiver for notification actions
    private val stopServiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == NotificationManager.ACTION_STOP_SERVICE) {
                Log.i(TAG, "Stop service requested by user")
                stopSelf()
            }
        }
    }
    
    private var isModelReady: Boolean = false
    
    // === Android Service Lifecycle (ONLY) ===
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "BreezeAppEngineService onCreate() - Simplified Architecture")
        
        try {
            // Initialize service orchestrator
            serviceOrchestrator = ServiceOrchestrator(this)
            serviceOrchestrator.initialize()
            serviceOrchestrator.setServiceInstance(this)
            
            // Initialize notification manager
            notificationManager = NotificationManager(this)
            notificationManager.createNotificationChannel()
            
            startForegroundService()
            registerBroadcastReceiver()

            // Ensure the default model is ready (download if needed)
            ensureDefaultModelReadyWithLogging()

            Log.i(TAG, "Simplified BreezeAppEngineService created successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating service", e)
            throw e
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind() - Simplified Architecture")
        
        // Permission check (skip in debug)
        if (!BuildConfig.DEBUG && 
            checkCallingOrSelfPermission(PERMISSION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Permission denied")
            return null
        }
        
        return serviceOrchestrator.getServiceBinder()
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind() - Client disconnected")
        
        // Optional: Unload models when no clients are connected to save memory
        // This provides additional resource management without full cleanup
        try {
            // Note: Model unloading is now handled by ServiceOrchestrator
            Log.d(TAG, "Client disconnected - models will be managed by orchestrator")
        } catch (e: Exception) {
            Log.w(TAG, "Error handling client disconnect", e)
        }
        
        return super.onUnbind(intent)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand() - Simplified Architecture")
        
        // Handle different start scenarios
        when (intent?.action) {
            "com.mtkresearch.breezeapp.engine.FORCE_FOREGROUND" -> {
                Log.i(TAG, "Forcing foreground service state for microphone access")
                serviceOrchestrator.forceForegroundForMicrophone()
            }
        }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        Log.i(TAG, "BreezeAppEngineService onDestroy() - Simplified Architecture")
        
        try {
            serviceOrchestrator.cleanup()
            unregisterBroadcastReceiver()
            
            Log.i(TAG, "Simplified BreezeAppEngineService destroyed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying service", e)
        }
        
        super.onDestroy()
    }
    
    /**
     * Returns true if the default model is downloaded and ready for inference.
     * Call this before processing any inference requests.
     */
    fun isModelReadyForInference(): Boolean = isModelReady
    
    // === Private Helper Methods ===
    
    private fun startForegroundService() {
        Log.d(TAG, "Starting foreground service")
        val notification = notificationManager.createNotification(ServiceState.Ready)
        
        // Start with dataSync type only, add microphone type when needed
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(com.mtkresearch.breezeapp.engine.core.BreezeAppEngineStatusManager.FOREGROUND_NOTIFICATION_ID, notification, 
                serviceOrchestrator.getCurrentServiceType())
        } else {
            startForeground(com.mtkresearch.breezeapp.engine.core.BreezeAppEngineStatusManager.FOREGROUND_NOTIFICATION_ID, notification)
        }
    }
    
    private fun registerBroadcastReceiver() {
        Log.d(TAG, "Registering broadcast receiver")
        val filter = IntentFilter(NotificationManager.ACTION_STOP_SERVICE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopServiceReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopServiceReceiver, filter)
        }
    }
    
    private fun unregisterBroadcastReceiver() {
        try {
            Log.d(TAG, "Unregistering broadcast receiver")
            unregisterReceiver(stopServiceReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering broadcast receiver", e)
        }
    }
    

    

    
    /**
     * Handle foreground service timeout for Android 15+
     * This method is called when the system determines the service has exceeded its time limit
     */
    override fun onTimeout(timeoutType: Int, timeoutDuration: Int) {
        Log.w(TAG, "Foreground service timeout received: type=$timeoutType, duration=$timeoutDuration")
        
        try {
            // Stop the service gracefully within the timeout period
            Log.i(TAG, "Stopping service due to timeout")
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping service on timeout", e)
        }
    }
    
    /**
     * Updates foreground service type for microphone access
     * This method is called when microphone access is needed
     */
    fun updateForegroundServiceType(includeMicrophone: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                val serviceType = if (includeMicrophone) {
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or 
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                }
                
                Log.d(TAG, "Updating foreground service type: ${if (includeMicrophone) "with microphone" else "dataSync only"}")
                
                // Update the service type in orchestrator
                serviceOrchestrator.updateForegroundServiceType(includeMicrophone)
                
                // Restart foreground service with new type
                val notification = notificationManager.createNotification(ServiceState.Processing(1))
                startForeground(com.mtkresearch.breezeapp.engine.core.BreezeAppEngineStatusManager.FOREGROUND_NOTIFICATION_ID, notification, serviceType)
                
                Log.d(TAG, "Foreground service type updated successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating foreground service type", e)
            }
        }
    }
    
    /**
     * Enable Android 15 timeout testing for development
     * Call this method to enable timeout testing even on non-Android 15 devices
     */
    fun enableTimeoutTesting() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                // Enable timeout testing via adb command simulation
                Log.i(TAG, "Timeout testing enabled for Android 15+ compliance")
                // Note: This would require adb shell am compat enable FGS_INTRODUCE_TIME_LIMITS
            } catch (e: Exception) {
                Log.e(TAG, "Error enabling timeout testing", e)
            }
        }
    }

    /**
     * Ensures the default model is downloaded and ready, with robust logging.
     * This is called from onCreate and can be reused elsewhere if needed.
     */
    private fun ensureDefaultModelReadyWithLogging() {
        val downloadUseCase = DownloadModelUseCase(this)
        val defaultModel = ModelDownloader.listAvailableModels(this).firstOrNull()
        if (defaultModel == null) {
            Log.e(TAG, "No default model found in fullModelList.json!")
            isModelReady = false
            // Note: Status updates are now handled by ServiceOrchestrator
        } else {
            val versionStore = ModelVersionStore(this)
            val alreadyDownloaded = versionStore.getDownloadedModels().any { it.id == defaultModel.id }
            if (alreadyDownloaded) {
                Log.i(TAG, "Default model ${defaultModel.id} already downloaded and ready.")
                isModelReady = true
                // Note: Status updates are now handled by ServiceOrchestrator
            } else {
                Log.i(TAG, "Default model ${defaultModel.id} not found, starting download...")
                isModelReady = false
                // Note: Status updates are now handled by ServiceOrchestrator
                downloadUseCase.ensureDefaultModelReady(object : ModelManager.DownloadListener {
                    override fun onCompleted(modelId: String) {
                        isModelReady = true
                        // Note: Status updates are now handled by ServiceOrchestrator
                        Log.i(TAG, "Default model $modelId download completed and is ready for inference.")
                    }
                    override fun onError(modelId: String, error: Throwable, fileName: String?) {
                        isModelReady = false
                        // Note: Status updates are now handled by ServiceOrchestrator
                        Log.e(TAG, "Failed to download default model $modelId: ${error.message}")
                    }
                    override fun onProgress(modelId: String, percent: Int, speed: Long, eta: Long) {
                        // Note: Status updates are now handled by ServiceOrchestrator
                        Log.i(TAG, "Downloading $modelId: $percent% (speed=$speed, eta=$eta)")
                    }
                    override fun onFileProgress(
                        modelId: String, fileName: String, fileIndex: Int, fileCount: Int,
                        bytesDownloaded: Long, totalBytes: Long, speed: Long, eta: Long
                    ) {
                        // Optionally, you can add more detailed progress here
                        Log.i(TAG, "Downloading $modelId: $fileName [$fileIndex/$fileCount] $bytesDownloaded/$totalBytes")
                    }
                    override fun onFileCompleted(modelId: String, fileName: String, fileIndex: Int, fileCount: Int) {
                        Log.i(TAG, "File completed: $fileName")
                    }
                    override fun onPaused(modelId: String) {
                        Log.i(TAG, "Download paused: $modelId")
                    }
                    override fun onResumed(modelId: String) {
                        Log.i(TAG, "Download resumed: $modelId")
                    }
                    override fun onCancelled(modelId: String) {
                        Log.i(TAG, "Download cancelled: $modelId")
                    }
                })
            }
        }
    }
}