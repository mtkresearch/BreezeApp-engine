package com.mtkresearch.breezeapp.engine.service

import android.content.Context
import android.util.Log
import com.mtkresearch.breezeapp.engine.core.BreezeAppEngineConfigurator
import com.mtkresearch.breezeapp.engine.core.BreezeAppEngineStatusManager
import com.mtkresearch.breezeapp.engine.system.BreathingBorderManager
import com.mtkresearch.breezeapp.engine.system.VisualStateManager
import com.mtkresearch.breezeapp.engine.system.NotificationManager
import com.mtkresearch.breezeapp.engine.system.SherpaLibraryManager
import com.mtkresearch.breezeapp.engine.system.PermissionManager
import com.mtkresearch.breezeapp.engine.model.ServiceState
import java.util.concurrent.ConcurrentHashMap

/**
 * Service Orchestrator - Simplified Service Lifecycle Management (Clean Architecture)
 * 
 * Following Clean Architecture principles, this class handles ONLY essential service
 * initialization and coordination. Redundant components have been removed for clarity.
 * 
 * ## Simplified Responsibilities:
 * - Initialize infrastructure components (notifications, permissions, visual state)
 * - Initialize essential Clean Architecture components (ClientManager, EngineServiceBinder)
 * - Manage service lifecycle and cleanup
 * - Coordinate between infrastructure and core business logic
 * 
 * ## Removed Complexity:
 * - RequestCoordinator (redundant - functionality moved to EngineServiceBinder)
 * - RequestProcessor (redundant - functionality handled by AIEngineManager)
 * - BreezeAppEngineCore (redundant - business logic in AIEngineManager)
 * - Duplicate request tracking (consolidated in AIEngineManager)
 * 
 * ## Clean Architecture Flow:
 * Client → EngineServiceBinder → AIEngineManager (Single Path)
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
    private lateinit var permissionManager: PermissionManager
    
    // Clean Architecture Components (Simplified)
    private lateinit var clientManager: ClientManager
    private lateinit var engineServiceBinder: EngineServiceBinder
    
    // Request lifecycle tracking for breathing border
    private val activeRequests = ConcurrentHashMap<String, Boolean>()
    
    // Service type tracking
    private var currentServiceType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    
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
            
            // AI Engine is initialized by configurator during infrastructure setup
            Log.d(TAG, "AI Engine initialized via configurator")
            
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
     * Get the configurator instance
     */
    fun getConfigurator(): BreezeAppEngineConfigurator = configurator
    
    /**
     * Force foreground service state for microphone access
     */
    fun forceForegroundForMicrophone() {
        try {
            Log.i(TAG, "Forcing foreground service state for microphone access")
            
            // Update service type to include microphone
            updateForegroundServiceType(true)
            
            // Request audio focus through PermissionManager
            val focusGranted = permissionManager.requestAudioFocus()
            
            if (focusGranted) {
                Log.i(TAG, "Audio focus granted for microphone access")
            } else {
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
            // Cleanup service binder
            engineServiceBinder.cleanup()
            
            // Cleanup AI engine manager and runners
            Log.d(TAG, "Cleaning up AI engine manager and runners")
            configurator.engineManager.cleanup()
            
            // Cleanup visual components
            visualStateManager.cleanup()
            
            // Abandon audio focus through PermissionManager
            permissionManager.abandonAudioFocus()
            
            Log.d(TAG, "Simplified service orchestrator cleaned up successfully")
            
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
        
        // Initialize notification manager
        notificationManager = NotificationManager(context)
        notificationManager.createNotificationChannel()
        
        // Initialize permission manager (includes audio management)
        permissionManager = PermissionManager(context)
        
        // Initialize breathing border manager
        breathingBorderManager = BreathingBorderManager(context)
        
        // Initialize visual state manager
        visualStateManager = VisualStateManager(
            context = context,
            breathingBorderManager = breathingBorderManager,
            notificationManager = notificationManager
        )
        
        // Initialize status manager (BEFORE configurator so it can be passed to AIEngineManager)
        statusManager = BreezeAppEngineStatusManager(
            service = null, // Will be set by service
            visualStateManager = visualStateManager
        )
        
        // Initialize configurator (AI processing core)
        configurator = BreezeAppEngineConfigurator(context)
        
        Log.d(TAG, "Infrastructure components initialized")
    }
    
    /**
     * Initialize clean architecture components (Simplified)
     * Following Single Responsibility and Clean Architecture principles.
     */
    private fun initializeCleanArchitectureComponents() {
        Log.d(TAG, "Initializing simplified clean architecture components")
        
        // Initialize client manager (handles client connections only)
        clientManager = ClientManager()
        
        // Initialize service binder (AIDL interface + direct AIEngineManager integration + request tracking)
        engineServiceBinder = EngineServiceBinder(
            clientManager = clientManager,
            aiEngineManager = configurator.engineManager,
            serviceOrchestrator = this
        )
        
        Log.d(TAG, "Simplified clean architecture components initialized")
        Log.d(TAG, "Architecture: Client → EngineServiceBinder → AIEngineManager (Single Path)")
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
    // notifyError method removed - now handled directly by EngineServiceBinder
    
    /**
     * Set service instance for components that need it (Simplified)
     */
    fun setServiceInstance(service: android.app.Service) {
        statusManager.setServiceInstance(service)
        // Note: EngineServiceBinder doesn't need service instance as it's stateless AIDL adapter
        Log.d(TAG, "Service instance set for status manager")
    }
    
    /**
     * Get the status manager for download progress updates
     */
    fun getStatusManager(): BreezeAppEngineStatusManager = statusManager
    
    /**
     * Get current foreground service type
     */
    fun getCurrentServiceType(): Int {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
    }
    
    // ========================================================================
    // REQUEST LIFECYCLE MANAGEMENT FOR BREATHING BORDER
    // ========================================================================
    
    /**
     * Start tracking a request and update breathing border status.
     * Called by EngineServiceBinder when requests begin.
     */
    fun startRequestTracking(requestId: String, requestType: String) {
        try {
            val wasAlreadyTracked = activeRequests.containsKey(requestId)
            activeRequests[requestId] = true
            val activeCount = activeRequests.size
            
            Log.i(TAG, "🚀 Request started: $requestId ($requestType) - Active: $activeCount (wasAlreadyTracked: $wasAlreadyTracked)")
            
            if (wasAlreadyTracked) {
                Log.w(TAG, "⚠️ Request $requestId was already being tracked! Potential duplicate start.")
                Log.w(TAG, "Current active requests: ${activeRequests.keys.toList()}")
            }
            
            // Update status to Processing to show breathing border
            statusManager.setProcessing(activeCount)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting request tracking for $requestId", e)
        }
    }
    
    /**
     * End tracking a request and update breathing border status.
     * Called by EngineServiceBinder when requests complete.
     */
    fun endRequestTracking(requestId: String, requestType: String) {
        try {
            val wasTracked = activeRequests.containsKey(requestId)
            val removedValue = activeRequests.remove(requestId)
            val activeCount = activeRequests.size
            
            Log.i(TAG, "🏁 Request ended: $requestId ($requestType) - Active: $activeCount (wasTracked: $wasTracked)")
            
            if (!wasTracked) {
                Log.w(TAG, "⚠️ Request $requestId was not being tracked! Potential tracking mismatch.")
                Log.w(TAG, "Current active requests: ${activeRequests.keys.toList()}")
            }
            
            // Update status based on remaining active requests
            if (activeCount > 0) {
                Log.d(TAG, "Still have $activeCount active requests, keeping Processing state")
                statusManager.setProcessing(activeCount)
            } else {
                Log.d(TAG, "No more active requests, setting Ready state")
                statusManager.setReady()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error ending request tracking for $requestId", e)
        }
    }
    
    /**
     * Get count of active requests for debugging
     */
    fun getActiveRequestCount(): Int = activeRequests.size
    
}