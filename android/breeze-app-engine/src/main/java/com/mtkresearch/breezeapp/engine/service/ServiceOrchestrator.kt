package com.mtkresearch.breezeapp.engine.service

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.mtkresearch.breezeapp.engine.model.BreezeAppEngineConfigurator
import com.mtkresearch.breezeapp.engine.core.BreezeAppEngineStatusManager
import com.mtkresearch.breezeapp.engine.system.BreathingBorderManager
import com.mtkresearch.breezeapp.engine.system.VisualStateManager
import com.mtkresearch.breezeapp.engine.system.NotificationManager
import com.mtkresearch.breezeapp.engine.system.SherpaLibraryManager
import com.mtkresearch.breezeapp.engine.domain.model.ServiceState
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap

/**
 * Service Orchestrator - Coordinates service initialization and lifecycle
 * 
 * This class handles the complex initialization and coordination of all service components,
 * following Clean Architecture principles by separating concerns and providing clear interfaces.
 * 
 * Responsibilities:
 * - Initialize all service components
 * - Manage component lifecycle
 * - Coordinate between different subsystems
 * - Provide cleanup and shutdown
 */
class ServiceOrchestrator(private val context: Context) {
    
    companion object {
        private const val TAG = "ServiceOrchestrator"
    }
    
    // Infrastructure Components
    private lateinit var configurator: BreezeAppEngineConfigurator
    private lateinit var statusManager: BreezeAppEngineStatusManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var breathingBorderManager: BreathingBorderManager
    private lateinit var visualStateManager: VisualStateManager
    
    // Clean Architecture Components
    private lateinit var clientManager: ClientManager
    private lateinit var requestCoordinator: RequestCoordinator
    private lateinit var engineServiceBinder: EngineServiceBinder
    private lateinit var engineCore: BreezeAppEngineCore
    
    // Audio management
    private lateinit var audioManager: AudioManager
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                Log.d(TAG, "AUDIOFOCUS_GAIN: Acquired audio focus")
                // Resume microphone recording if it was paused
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                Log.w(TAG, "AUDIOFOCUS_LOSS: Lost audio focus permanently")
                // Stop microphone recording permanently
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                Log.w(TAG, "AUDIOFOCUS_LOSS_TRANSIENT: Lost audio focus temporarily")
                // Pause microphone recording temporarily
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasAudioFocus = false
                Log.w(TAG, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: Lost audio focus, can duck")
                // Lower microphone volume but continue recording
            }
        }
    }
    
    // Request tracking
    private val activeRequestCount = AtomicInteger(0)
    private val requestTracker = ConcurrentHashMap<String, Long>()
    
    // Service type tracking
    private var currentServiceType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    
    // Audio focus tracking
    private var hasAudioFocus = false
    
    /**
     * Initializes all service components
     */
    fun initialize() {
        Log.d(TAG, "Initializing service orchestrator")
        
        try {
            // Check microphone permission for service
            checkMicrophonePermission()
            
            // Initialize infrastructure components
            initializeInfrastructureComponents()
            
            // Initialize clean architecture components
            initializeCleanArchitectureComponents()
            
            // Initialize engine core
            engineCore.initialize()
            
            Log.i(TAG, "Service orchestrator initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing service orchestrator", e)
            throw e
        }
    }
    
    /**
     * Gets the service binder for client connections
     */
    fun getServiceBinder() = engineServiceBinder.getBinder()
    
    /**
     * Updates foreground service type for microphone access
     */
    fun updateForegroundServiceType(includeMicrophone: Boolean) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val serviceType = if (includeMicrophone) {
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or 
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                }
                
                Log.d(TAG, "Updating foreground service type: ${if (includeMicrophone) "with microphone" else "dataSync only"}")
                
                // Store the service type for later use when service instance is available
                currentServiceType = serviceType
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating foreground service type", e)
        }
    }
    
    /**
     * Gets the current service type for microphone access
     */
    fun getCurrentServiceType(): Int {
        return currentServiceType
    }
    
    /**
     * Force foreground service state for microphone access
     */
    fun forceForegroundForMicrophone() {
        try {
            Log.i(TAG, "Forcing foreground service state for microphone access")
            
            // Update service type to include microphone
            updateForegroundServiceType(true)
            
            // Request audio focus with proper attributes for microphone recording
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
                
            val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()

            val result = audioManager.requestAudioFocus(focusRequest)

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                hasAudioFocus = true
                Log.i(TAG, "Audio focus granted for microphone access")
            } else {
                hasAudioFocus = false
                Log.w(TAG, "Audio focus request denied for microphone access")
            }
            
            // Update status to processing to show breathing border
            statusManager.updateState(ServiceState.Processing(1))
            
            Log.i(TAG, "Foreground service state forced for microphone access")
        } catch (e: Exception) {
            Log.e(TAG, "Error forcing foreground service state", e)
        }
    }
    
    /**
     * Cleanup all resources
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up service orchestrator")
        
        try {
            // Shutdown engine core
            engineCore.shutdown()
            
            // Cleanup AI engine manager and runners
            Log.d(TAG, "Cleaning up AI engine manager and runners")
            configurator.engineManager.cleanup()
            
            // Cleanup visual components
            visualStateManager.cleanup()
            
            // Abandon audio focus
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val audioAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                    
                val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
                    
                audioManager.abandonAudioFocusRequest(focusRequest)
            } else {
                audioManager.abandonAudioFocus(audioFocusChangeListener)
            }
            
            Log.d(TAG, "Service orchestrator cleaned up successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up service orchestrator", e)
            
            // Force cleanup on error to prevent resource leaks
            try {
                Log.w(TAG, "Attempting force cleanup due to error")
                configurator.engineManager.forceCleanupAll()
            } catch (fe: Exception) {
                Log.e(TAG, "Force cleanup also failed", fe)
            }
        }
    }
    
    /**
     * Initialize infrastructure components
     */
    private fun initializeInfrastructureComponents() {
        Log.d(TAG, "Initializing infrastructure components")
        
        // Initialize complete global library system
        SherpaLibraryManager.initializeCompleteSystem(context)
        
        // Initialize configurator
        configurator = BreezeAppEngineConfigurator(context)
        
        // Initialize notification manager
        notificationManager = NotificationManager(context)
        notificationManager.createNotificationChannel()
        
        // Initialize audio manager
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // Initialize breathing border manager
        breathingBorderManager = BreathingBorderManager(context)
        
        // Initialize visual state manager
        visualStateManager = VisualStateManager(
            context = context,
            breathingBorderManager = breathingBorderManager,
            notificationManager = notificationManager
        )
        
        // Initialize status manager
        statusManager = BreezeAppEngineStatusManager(
            service = null, // Will be set by service
            visualStateManager = visualStateManager
        )
        
        Log.d(TAG, "Infrastructure components initialized")
    }
    
    /**
     * Initialize clean architecture components
     */
    private fun initializeCleanArchitectureComponents() {
        Log.d(TAG, "Initializing clean architecture components")
        
        // Initialize client manager
        clientManager = ClientManager()
        
        // Initialize engine core
        engineCore = BreezeAppEngineCore(context)
        
        // Initialize request processor
        val requestProcessor = RequestProcessor(
            engineManager = configurator.engineManager,
            statusManager = statusManager,
            activeRequestCount = activeRequestCount,
            requestTracker = requestTracker,
            updateStatusAfterRequestCompletion = { statusManager.updateState(ServiceState.Ready) },
            notifyError = ::notifyError
        )
        
        // Initialize request coordinator
        requestCoordinator = RequestCoordinator(
            requestProcessor = requestProcessor,
            engineManager = configurator.engineManager,
            clientManager = clientManager,
            serviceInstance = null // Will be set by service
        )
        
        // Initialize service binder
        engineServiceBinder = EngineServiceBinder(
            clientManager = clientManager,
            requestCoordinator = requestCoordinator
        )
        
        Log.d(TAG, "Clean architecture components initialized")
    }
    
    /**
     * Check microphone permission for the service
     */
    private fun checkMicrophonePermission() {
        try {
            val permission = android.Manifest.permission.RECORD_AUDIO
            val granted = context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Service microphone permission granted: $granted")
            
            if (!granted) {
                Log.w(TAG, "Microphone permission not granted for service - ASR features may not work")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking microphone permission", e)
        }
    }
    
    /**
     * Notify error to clients
     */
    private fun notifyError(requestId: String, error: String) {
        clientManager.notifyError(requestId, error)
    }
    
    /**
     * Set service instance for components that need it
     */
    fun setServiceInstance(service: android.app.Service) {
        statusManager.setServiceInstance(service)
        // Set the service instance for RequestCoordinator if it's the correct type
        if (service is com.mtkresearch.breezeapp.engine.BreezeAppEngineService) {
            requestCoordinator.setServiceInstance(service)
        }
    }
    
    /**
     * Get the status manager for download progress updates
     */
    fun getStatusManager(): BreezeAppEngineStatusManager = statusManager
} 