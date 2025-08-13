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
import com.mtkresearch.breezeapp.engine.core.ModelManagementCenter
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
     * Returns true if essential models (LLM + ASR) are downloaded and ready for inference.
     * Call this before processing any inference requests.
     */
    fun isModelReadyForInference(): Boolean = isModelReady
    
    /**
     * Check if a specific category of model is ready for inference
     */
    fun isCategoryReadyForInference(category: ModelManagementCenter.ModelCategory): Boolean {
        val modelCenter = ModelManagementCenter.getInstance(this)
        return modelCenter.getDefaultModel(category)?.status in setOf(
            ModelManagementCenter.ModelState.Status.DOWNLOADED,
            ModelManagementCenter.ModelState.Status.READY
        )
    }
    
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
     * Ensures essential models are ready using the new ModelManagementCenter.
     * Downloads LLM (Breeze2-3B-8W16A-250630-npu) and ASR (Breeze-ASR-25-onnx) models.
     */
    private fun ensureDefaultModelReadyWithLogging() {
        val modelCenter = ModelManagementCenter.getInstance(this)
        
        // Set status manager for download progress updates
        modelCenter.setStatusManager(serviceOrchestrator.getStatusManager())
        
        // Download essential categories: LLM and ASR
        val essentialCategories = listOf(
            ModelManagementCenter.ModelCategory.LLM,
            ModelManagementCenter.ModelCategory.ASR,
            ModelManagementCenter.ModelCategory.TTS
        )
        
        // Log which default models will be used
        essentialCategories.forEach { category ->
            val defaultModel = modelCenter.getDefaultModel(category)
            if (defaultModel != null) {
                Log.i(TAG, "Default ${category.name} model: ${defaultModel.modelInfo.id}")
                if (defaultModel.status == ModelManagementCenter.ModelState.Status.DOWNLOADED) {
                    Log.i(TAG, "${category.name} model ${defaultModel.modelInfo.id} already downloaded")
                }
            } else {
                Log.w(TAG, "No default model found for category ${category.name}")
            }
        }
        
        modelCenter.downloadDefaultModels(essentialCategories, object : ModelManagementCenter.BulkDownloadListener {
            override fun onModelCompleted(modelId: String, success: Boolean) {
                if (success) {
                    Log.i(TAG, "Essential model $modelId download completed and ready.")
                } else {
                    Log.e(TAG, "Failed to download essential model $modelId")
                }
                
                // Check if all essential models are ready
                val allEssentialReady = essentialCategories.all { category ->
                    val model = modelCenter.getDefaultModel(category)
                    val ready = model?.status in setOf(
                        ModelManagementCenter.ModelState.Status.DOWNLOADED,
                        ModelManagementCenter.ModelState.Status.READY
                    )
                    Log.d(TAG, "${category.name} model ready: $ready (${model?.modelInfo?.id})")
                    ready
                }
                
                isModelReady = allEssentialReady
                Log.i(TAG, "All essential models ready: $allEssentialReady")
            }
            
            override fun onAllCompleted() {
                // Only mark ready if all models are actually downloaded/ready
                val essentialCategories = listOf(
                    ModelManagementCenter.ModelCategory.LLM,
                    ModelManagementCenter.ModelCategory.ASR,
                    ModelManagementCenter.ModelCategory.TTS
                )
                
                val allEssentialReady = essentialCategories.all { category ->
                    val model = modelCenter.getDefaultModel(category)
                    model?.status in setOf(
                        ModelManagementCenter.ModelState.Status.DOWNLOADED,
                        ModelManagementCenter.ModelState.Status.READY
                    )
                }
                
                if (allEssentialReady) {
                    Log.i(TAG, "All essential models (Breeze2 LLM + Breeze-ASR-25-onnx + vits-mr-20250709) ready for inference.")
                    isModelReady = true
                } else {
                    Log.w(TAG, "onAllCompleted called but not all models are ready yet.")
                }
            }
        })
    }
}