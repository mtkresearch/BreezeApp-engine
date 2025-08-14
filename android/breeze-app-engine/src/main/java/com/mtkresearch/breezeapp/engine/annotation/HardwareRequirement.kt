package com.mtkresearch.breezeapp.engine.annotation

import android.content.Context

/**
 * Hardware requirements for AI runners.
 * 
 * This enum provides a robust, closed set of hardware requirements that
 * prevents typos and inconsistencies in runner configuration. Each requirement
 * includes validation logic for runtime checking.
 * 
 * **Design Benefits:**
 * - Compile-time validation prevents typos
 * - Standardized requirement definitions
 * - Built-in validation logic
 * - Clear memory/storage thresholds
 * - Extensible for future requirements
 * 
 * **Usage in Runners:**
 * ```kotlin
 * @AIRunner(
 *     hardwareRequirements = [
 *         HardwareRequirement.MTK_NPU,
 *         HardwareRequirement.HIGH_MEMORY,
 *         HardwareRequirement.MICROPHONE
 *     ]
 * )
 * class MediaTekASRRunner : BaseRunner { }
 * ```
 * 
 * @since Engine API v2.0
 * @see AIRunner.hardwareRequirements
 */
enum class HardwareRequirement(
    /**
     * Human-readable name for this requirement.
     */
    val displayName: String,
    
    /**
     * Detailed description of what this requirement means.
     */
    val description: String,
    
    /**
     * Category of this requirement for grouping and UI display.
     */
    val category: RequirementCategory
) {
    // Connectivity Requirements
    
    /**
     * Network internet connection required.
     * 
     * Runner needs active internet connectivity for cloud APIs,
     * model downloads, or online processing.
     */
    INTERNET(
        displayName = "Internet Connection",
        description = "Active network connectivity required for cloud APIs or model downloads",
        category = RequirementCategory.CONNECTIVITY
    ),
    
    // Processing Unit Requirements
    
    /**
     * MediaTek Neural Processing Unit required.
     * 
     * Runner needs MediaTek NPU for hardware-accelerated AI inference.
     * Provides significant performance improvements over CPU-only processing.
     */
    MTK_NPU(
        displayName = "MediaTek NPU",
        description = "MediaTek Neural Processing Unit for hardware-accelerated inference",
        category = RequirementCategory.PROCESSING
    ),
    
    /**
     * Basic CPU processing capability.
     * 
     * This requirement is always satisfied as all devices have CPUs.
     * Explicitly specified for documentation and future-proofing.
     */
    CPU(
        displayName = "CPU Processing",
        description = "Basic CPU processing capability (always available)",
        category = RequirementCategory.PROCESSING
    ),
    
    // Memory Requirements
    
    /**
     * High memory requirement (>8GB RAM).
     * 
     * For large language models or complex AI tasks requiring
     * substantial memory resources.
     */
    HIGH_MEMORY(
        displayName = "High Memory (>8GB)",
        description = "More than 8GB of device RAM for large models and complex processing",
        category = RequirementCategory.MEMORY
    ),
    
    /**
     * Medium memory requirement (>4GB RAM).
     * 
     * For standard AI models that need moderate memory resources
     * but work on most modern devices.
     */
    MEDIUM_MEMORY(
        displayName = "Medium Memory (>4GB)",
        description = "More than 4GB of device RAM for standard AI models",
        category = RequirementCategory.MEMORY
    ),
    
    /**
     * Low memory requirement (>2GB RAM).
     * 
     * For lightweight AI models optimized for resource-constrained devices.
     * Compatible with most Android devices.
     */
    LOW_MEMORY(
        displayName = "Low Memory (>2GB)",
        description = "More than 2GB of device RAM for lightweight AI models",
        category = RequirementCategory.MEMORY
    ),
    
    // Storage Requirements
    
    /**
     * Large storage requirement (>1GB free space).
     * 
     * For runners that need to store large AI models locally,
     * such as LLMs or high-quality TTS voices.
     */
    LARGE_STORAGE(
        displayName = "Large Storage (>1GB)",
        description = "More than 1GB of free storage for large AI models",
        category = RequirementCategory.STORAGE
    ),
    
    /**
     * Medium storage requirement (>500MB free space).
     * 
     * For runners with moderate storage needs, such as
     * medium-sized models or cached data.
     */
    MEDIUM_STORAGE(
        displayName = "Medium Storage (>500MB)",
        description = "More than 500MB of free storage for medium-sized models",
        category = RequirementCategory.STORAGE
    ),
    
    // Sensor Requirements
    
    /**
     * Microphone access required.
     * 
     * For ASR (Automatic Speech Recognition) runners that need
     * to capture audio input from the device microphone.
     */
    MICROPHONE(
        displayName = "Microphone Access",
        description = "Device microphone access for audio input processing",
        category = RequirementCategory.SENSORS
    ),
    
    /**
     * Camera access required.
     * 
     * For VLM (Vision Language Model) runners that need to process
     * visual input from device cameras.
     */
    CAMERA(
        displayName = "Camera Access",
        description = "Device camera access for visual input processing",
        category = RequirementCategory.SENSORS
    );
    
    /**
     * Validates if this requirement is satisfied on the current device.
     * 
     * @param context Android context for accessing system services, or null for basic validation
     * @return true if the requirement is satisfied, false otherwise
     */
    fun isSatisfied(context: Context?): Boolean {
        // Handle null context gracefully
        if (context == null) {
            return when (this) {
                CPU -> true // Always available regardless of context
                MTK_NPU -> checkMediaTekNPU() // NPU check doesn't require context
                HIGH_MEMORY, MEDIUM_MEMORY, LOW_MEMORY -> checkMemoryRequirement(getMemoryThreshold())
                LARGE_STORAGE, MEDIUM_STORAGE -> checkStorageRequirement(getStorageThreshold())
                else -> false // Requirements needing context default to false
            }
        }
        
        return when (this) {
            INTERNET -> checkInternetConnection(context)
            MTK_NPU -> checkMediaTekNPU()
            CPU -> true // Always available
            HIGH_MEMORY -> checkMemoryRequirement(8 * 1024) // 8GB in MB
            MEDIUM_MEMORY -> checkMemoryRequirement(4 * 1024) // 4GB in MB  
            LOW_MEMORY -> checkMemoryRequirement(2 * 1024) // 2GB in MB
            LARGE_STORAGE -> checkStorageRequirement(1024) // 1GB in MB
            MEDIUM_STORAGE -> checkStorageRequirement(512) // 512MB
            MICROPHONE -> checkMicrophonePermission(context)
            CAMERA -> checkCameraPermission(context)
        }
    }
    
    private fun getMemoryThreshold(): Int {
        return when (this) {
            HIGH_MEMORY -> 8 * 1024 // 8GB in MB
            MEDIUM_MEMORY -> 4 * 1024 // 4GB in MB
            LOW_MEMORY -> 2 * 1024 // 2GB in MB
            else -> 0
        }
    }
    
    private fun getStorageThreshold(): Int {
        return when (this) {
            LARGE_STORAGE -> 1024 // 1GB in MB
            MEDIUM_STORAGE -> 512 // 512MB
            else -> 0
        }
    }
    
    /**
     * Returns the minimum threshold value for this requirement.
     * Useful for displaying requirement details to users.
     * 
     * @return Threshold value in appropriate units, or null if not applicable
     */
    fun getThresholdValue(): Long? {
        return when (this) {
            HIGH_MEMORY -> 8 * 1024 * 1024 * 1024L // 8GB in bytes
            MEDIUM_MEMORY -> 4 * 1024 * 1024 * 1024L // 4GB in bytes
            LOW_MEMORY -> 2 * 1024 * 1024 * 1024L // 2GB in bytes
            LARGE_STORAGE -> 1024 * 1024 * 1024L // 1GB in bytes
            MEDIUM_STORAGE -> 512 * 1024 * 1024L // 512MB in bytes
            else -> null
        }
    }
    
    private fun checkInternetConnection(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
                as android.net.ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            networkCapabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun checkMediaTekNPU(): Boolean {
        return try {
            // Check MediaTek NPU availability through system properties
            val npu = System.getProperty("ro.vendor.mtk_nn_support")
            npu == "1"
        } catch (e: Exception) {
            false
        }
    }
    
    private fun checkMemoryRequirement(requiredMB: Int): Boolean {
        return try {
            val runtime = Runtime.getRuntime()
            val maxMemoryMB = runtime.maxMemory() / (1024 * 1024)
            maxMemoryMB >= requiredMB
        } catch (e: Exception) {
            false
        }
    }
    
    private fun checkStorageRequirement(requiredMB: Int): Boolean {
        return try {
            val internalDir = android.os.Environment.getDataDirectory()
            val availableBytes = internalDir.freeSpace
            val availableMB = availableBytes / (1024 * 1024)
            availableMB >= requiredMB
        } catch (e: Exception) {
            false
        }
    }
    
    private fun checkMicrophonePermission(context: Context): Boolean {
        return try {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            // Also check if microphone hardware is available
            val hasHardware = context.packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_MICROPHONE
            )
            
            hasPermission && hasHardware
        } catch (e: Exception) {
            false
        }
    }
    
    private fun checkCameraPermission(context: Context): Boolean {
        return try {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            // Also check if camera hardware is available
            val hasHardware = context.packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_CAMERA_ANY
            )
            
            hasPermission && hasHardware
        } catch (e: Exception) {
            false
        }
    }
    
    companion object {
        /**
         * Returns all requirements in the specified category.
         */
        fun getByCategory(category: RequirementCategory): List<HardwareRequirement> {
            return values().filter { it.category == category }
        }
        
        /**
         * Validates all requirements against the current device.
         * 
         * @param requirements List of requirements to validate
         * @param context Android context for system access
         * @return Map of requirement to satisfaction status
         */
        fun validateAll(
            requirements: List<HardwareRequirement>,
            context: Context
        ): Map<HardwareRequirement, Boolean> {
            return requirements.associateWith { it.isSatisfied(context) }
        }
        
        /**
         * Returns requirements that are not satisfied on the current device.
         * 
         * @param requirements List of requirements to check
         * @param context Android context for system access
         * @return List of unsatisfied requirements
         */
        fun getUnsatisfied(
            requirements: List<HardwareRequirement>,
            context: Context
        ): List<HardwareRequirement> {
            return requirements.filter { !it.isSatisfied(context) }
        }
    }
}

/**
 * Categories for grouping hardware requirements.
 */
enum class RequirementCategory(val displayName: String) {
    CONNECTIVITY("Connectivity"),
    PROCESSING("Processing Units"),
    MEMORY("Memory"),
    STORAGE("Storage"),
    SENSORS("Sensors")
}