package com.mtkresearch.breezeapp.engine

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.mtkresearch.breezeapp.engine.model.BreezeAppEngineConfigurator
import com.mtkresearch.breezeapp.engine.core.BreezeAppEngineStatusManager
import com.mtkresearch.breezeapp.engine.core.ServiceNotificationManager
import com.mtkresearch.breezeapp.engine.error.RequestProcessingHelper
import com.mtkresearch.breezeapp.engine.service.ClientManager
import com.mtkresearch.breezeapp.engine.service.RequestCoordinator
import com.mtkresearch.breezeapp.engine.service.EngineServiceBinder
import com.mtkresearch.breezeapp.engine.service.BreezeAppEngineCore
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap
import com.mtkresearch.breezeapp.engine.repository.ModelManager
import com.mtkresearch.breezeapp.engine.repository.ModelVersionStore
import com.mtkresearch.breezeapp.engine.util.ModelDownloader
import com.mtkresearch.breezeapp.engine.core.DownloadModelUseCase
import com.mtkresearch.breezeapp.engine.system.SherpaLibraryManager

/**
 * BreezeAppEngineService - Clean Android Service Implementation
 * 
 * This service follows Clean Architecture principles with ONLY Android Service responsibilities:
 * - Service lifecycle management (onCreate, onBind, onDestroy)
 * - Foreground service management
 * - Component initialization and cleanup
 * 
 * All business logic is delegated to specialized components:
 * - EngineServiceBinder: AIDL interface implementation
 * - ClientManager: Client connection management  
 * - RequestCoordinator: Request processing coordination
 * - BreezeAppEngineCore: Business logic execution
 * 
 * Result: ~100 lines vs 919 lines monolithic service
 */
class BreezeAppEngineService : Service() {
    
    companion object {
        private const val TAG = "BreezeAppEngineService"
        private const val PERMISSION = "com.mtkresearch.breezeapp.permission.BIND_AI_ROUTER_SERVICE"
    }
    
    // Infrastructure Components
    private lateinit var configurator: BreezeAppEngineConfigurator
    private lateinit var statusManager: BreezeAppEngineStatusManager
    private lateinit var notificationManager: ServiceNotificationManager
    
    // Clean Architecture Components
    private lateinit var clientManager: ClientManager
    private lateinit var requestCoordinator: RequestCoordinator
    private lateinit var engineServiceBinder: EngineServiceBinder
    private lateinit var engineCore: BreezeAppEngineCore
    
    // Request tracking for RequestProcessingHelper
    private val activeRequestCount = AtomicInteger(0)
    private val requestTracker = ConcurrentHashMap<String, Long>()
    
    // Broadcast receiver for notification actions
    private val stopServiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ServiceNotificationManager.ACTION_STOP_SERVICE) {
                Log.i(TAG, "Stop service requested by user")
                stopSelf()
            }
        }
    }
    
    private var isModelReady: Boolean = false
    
    // === Android Service Lifecycle (ONLY) ===
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "BreezeAppEngineService onCreate() - Clean Architecture")
        
        try {
            // Initialize complete global library system (Sherpa + GlobalLibraryTracker)
            SherpaLibraryManager.initializeCompleteSystem(this)
            
            initializeComponents()
            startForegroundService()
            registerBroadcastReceiver()

            // Ensure the default model is ready (download if needed)
            ensureDefaultModelReadyWithLogging()

            Log.i(TAG, "Clean BreezeAppEngineService created successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating service", e)
            throw e
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind() - Clean Architecture")
        
        // Permission check (skip in debug)
        if (!BuildConfig.DEBUG && 
            checkCallingOrSelfPermission(PERMISSION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Permission denied")
            return null
        }
        
        return engineServiceBinder.getBinder()
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind() - Client disconnected")
        
        // Optional: Unload models when no clients are connected to save memory
        // This provides additional resource management without full cleanup
        try {
            configurator.engineManager.unloadAllModels()
            Log.d(TAG, "Models unloaded due to client disconnect")
        } catch (e: Exception) {
            Log.w(TAG, "Error unloading models on unbind", e)
        }
        
        return super.onUnbind(intent)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand() - Clean Architecture")
        return START_STICKY
    }
    
    override fun onDestroy() {
        Log.i(TAG, "BreezeAppEngineService onDestroy() - Clean Architecture")
        
        try {
            cleanupResources()
            unregisterBroadcastReceiver()
            
            Log.i(TAG, "Clean BreezeAppEngineService destroyed successfully")
            
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
    
    private fun initializeComponents() {
        Log.d(TAG, "Initializing clean architecture components")
        
        // Initialize infrastructure components
        configurator = BreezeAppEngineConfigurator(this)
        notificationManager = ServiceNotificationManager(this)
        statusManager = BreezeAppEngineStatusManager(this, notificationManager)
        
        // Initialize clean architecture components
        clientManager = ClientManager()
        engineCore = BreezeAppEngineCore(this)
        
        // Initialize request processing helper
        val requestHelper = RequestProcessingHelper(
            engineManager = configurator.engineManager,
            statusManager = statusManager,
            activeRequestCount = activeRequestCount,
            requestTracker = requestTracker,
            updateStatusAfterRequestCompletion = { statusManager.updateState(com.mtkresearch.breezeapp.engine.domain.model.ServiceState.Ready) },
            notifyError = ::notifyError
        )
        
        // Initialize request coordinator
        requestCoordinator = RequestCoordinator(
            requestProcessingHelper = requestHelper,
            engineManager = configurator.engineManager,
            clientManager = clientManager
        )
        
        // Initialize service binder
        engineServiceBinder = EngineServiceBinder(
            clientManager = clientManager,
            requestCoordinator = requestCoordinator
        )
        
        // Initialize engine core
        engineCore.initialize()
        
        Log.d(TAG, "All clean architecture components initialized")
    }
    
    private fun startForegroundService() {
        Log.d(TAG, "Starting foreground service")
        notificationManager.createNotificationChannel()
        val notification = notificationManager.createNotification(com.mtkresearch.breezeapp.engine.domain.model.ServiceState.Ready)
        startForeground(com.mtkresearch.breezeapp.engine.core.BreezeAppEngineStatusManager.FOREGROUND_NOTIFICATION_ID, notification)
    }
    
    private fun registerBroadcastReceiver() {
        Log.d(TAG, "Registering broadcast receiver")
        val filter = IntentFilter(ServiceNotificationManager.ACTION_STOP_SERVICE)
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
    
    private fun cleanupResources() {
        Log.d(TAG, "Cleaning up resources")
        
        try {
            // Shutdown engine core
            engineCore.shutdown()
            
            // FIX: Add missing AIEngineManager cleanup to release runner resources
            Log.d(TAG, "Cleaning up AI engine manager and runners")
            configurator.engineManager.cleanup()
            
            // Cleanup notification
            notificationManager.clearNotification()
            
            Log.d(TAG, "Resources cleaned up successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up resources", e)
            
            // FIX: Force cleanup on error to prevent resource leaks
            try {
                Log.w(TAG, "Attempting force cleanup due to error")
                configurator.engineManager.forceCleanupAll()
            } catch (fe: Exception) {
                Log.e(TAG, "Force cleanup also failed", fe)
            }
        }
    }
    
    private fun notifyError(requestId: String, error: String) {
        clientManager.notifyError(requestId, error)
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
            statusManager.setError("No default model found in fullModelList.json!")
        } else {
            val versionStore = ModelVersionStore(this)
            val alreadyDownloaded = versionStore.getDownloadedModels().any { it.id == defaultModel.id }
            if (alreadyDownloaded) {
                Log.i(TAG, "Default model ${defaultModel.id} already downloaded and ready.")
                isModelReady = true
                statusManager.setReady()
            } else {
                Log.i(TAG, "Default model ${defaultModel.id} not found, starting download...")
                isModelReady = false
                statusManager.setDownloading(modelName = defaultModel.id, progress = 0)
                downloadUseCase.ensureDefaultModelReady(object : ModelManager.DownloadListener {
                    override fun onCompleted(modelId: String) {
                        isModelReady = true
                        statusManager.setReady()
                        Log.i(TAG, "Default model $modelId download completed and is ready for inference.")
                    }
                    override fun onError(modelId: String, error: Throwable, fileName: String?) {
                        isModelReady = false
                        statusManager.setError("Model download failed: ${error.message}")
                        Log.e(TAG, "Failed to download default model $modelId: ${error.message}")
                    }
                    override fun onProgress(modelId: String, percent: Int, speed: Long, eta: Long) {
                        statusManager.setDownloading(modelName = modelId, progress = percent)
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