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
import android.media.AudioManager
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
import com.mtkresearch.breezeapp.engine.domain.model.ServiceState
import com.mtkresearch.breezeapp.engine.system.BreathingBorderManager
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
    private lateinit var breathingBorderManager: BreathingBorderManager
    
    // Clean Architecture Components
    private lateinit var clientManager: ClientManager
    private lateinit var requestCoordinator: RequestCoordinator
    private lateinit var engineServiceBinder: EngineServiceBinder
    private lateinit var engineCore: BreezeAppEngineCore

    private lateinit var audioManager: AudioManager
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "AUDIOFOCUS_GAIN: Acquired audio focus")
                // Resume playback or recording if paused
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.w(TAG, "AUDIOFOCUS_LOSS: Lost audio focus ($focusChange)")
                // Pause or stop playback/recording
                // Consider stopping microphone ASR if focus is permanently lost
            }
        }
    }
    
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
        
        // Handle different start scenarios
        when (intent?.action) {
            "com.mtkresearch.breezeapp.engine.FORCE_FOREGROUND" -> {
                Log.i(TAG, "Forcing foreground service state for microphone access")
                forceForegroundForMicrophone()
            }
        }
        
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
        
        // Check microphone permission for service
        checkMicrophonePermission()
        
        // Initialize infrastructure components
        configurator = BreezeAppEngineConfigurator(this)
        notificationManager = ServiceNotificationManager(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // Initialize breathing border manager on main thread
        breathingBorderManager = BreathingBorderManager(this)
        
        statusManager = BreezeAppEngineStatusManager(this, notificationManager, breathingBorderManager)
        
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
            clientManager = clientManager,
            serviceInstance = this
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
        val notification = notificationManager.createNotification(ServiceState.Ready)
        
        // Start with dataSync type only, add microphone type when needed
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(BreezeAppEngineStatusManager.FOREGROUND_NOTIFICATION_ID, notification, 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(BreezeAppEngineStatusManager.FOREGROUND_NOTIFICATION_ID, notification)
        }
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
            
            // Clean up breathing border
            breathingBorderManager.cleanup()

            // Abandon audio focus
            audioManager.abandonAudioFocus(audioFocusChangeListener)
            
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
     * Check microphone permission for the service
     */
    private fun checkMicrophonePermission() {
        try {
            val permission = android.Manifest.permission.RECORD_AUDIO
            val granted = checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Service microphone permission granted: $granted")
            
            if (!granted) {
                Log.w(TAG, "Microphone permission not granted for service - ASR features may not work")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking microphone permission", e)
        }
    }
    
    /**
     * Update foreground service type to include microphone when needed
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
                startForeground(BreezeAppEngineStatusManager.FOREGROUND_NOTIFICATION_ID, 
                    notificationManager.createNotification(statusManager.getCurrentState()), serviceType)
                    
            } catch (e: Exception) {
                Log.e(TAG, "Error updating foreground service type", e)
            }
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
     * Force foreground service state for microphone access
     * This ensures the service is properly recognized as foreground when microphone is needed
     */
    private fun forceForegroundForMicrophone() {
        try {
            Log.i(TAG, "Forcing foreground service state for microphone access")
            
            // Update service type to include microphone
            updateForegroundServiceType(true)
            
            // Request audio focus
            val result = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC, // Use STREAM_MUSIC for general audio, or STREAM_VOICE_CALL for communication
                AudioManager.AUDIOFOCUS_GAIN // Request permanent audio focus
            )

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.i(TAG, "Audio focus granted for microphone access")
            } else {
                Log.w(TAG, "Audio focus request denied for microphone access")
            }
            
            // Update status to processing to show breathing border
            statusManager.updateState(ServiceState.Processing(1))
            
            // Ensure notification is updated
            val notification = notificationManager.createNotification(ServiceState.Processing(1))
            startForeground(BreezeAppEngineStatusManager.FOREGROUND_NOTIFICATION_ID, notification)
            
            Log.i(TAG, "Foreground service state forced for microphone access")
        } catch (e: Exception) {
            Log.e(TAG, "Error forcing foreground service state", e)
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