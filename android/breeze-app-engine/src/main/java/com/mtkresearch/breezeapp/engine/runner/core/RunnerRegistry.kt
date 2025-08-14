package com.mtkresearch.breezeapp.engine.runner.core

import com.mtkresearch.breezeapp.engine.core.Logger
import com.mtkresearch.breezeapp.engine.model.CapabilityType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * RunnerRegistry - Thread-Safe Runner Storage and Lookup
 * 
 * Simplified registry that stores and manages runner instances using annotation-based
 * metadata. This replaces the complex configuration-based system with a clean,
 * annotation-only approach.
 * 
 * ## Architecture Changes from Legacy
 * - **REMOVED**: Configuration file parsing and validation
 * - **REMOVED**: Strategy pattern selection complexity  
 * - **REMOVED**: Hardware capability tracking and registration
 * - **REMOVED**: Definition-based registration methods
 * - **KEPT**: Thread-safe storage and capability-based lookup
 * - **ADDED**: Simple annotation-based registration
 * 
 * ## Thread Safety
 * All public methods are thread-safe using ReentrantReadWriteLock:
 * - **Read operations**: Concurrent access allowed
 * - **Write operations**: Exclusive access required
 * 
 * ## Storage Strategy
 * Runners are indexed by:
 * - **Capability**: Fast lookup by AI capability type
 * - **Name**: Direct access by runner name
 * 
 * @param logger Logger instance for debugging and monitoring
 * 
 * @since Engine API v2.0
 */
class RunnerRegistry(private val logger: Logger) {
    
    companion object {
        private const val TAG = "RunnerRegistry"
    }
    
    // Thread-safe storage for runners
    private val runnersByCapability = ConcurrentHashMap<CapabilityType, MutableList<BaseRunner>>()
    private val runnersByName = ConcurrentHashMap<String, BaseRunner>()
    private val indexLock = ReentrantReadWriteLock()
    
    /**
     * Register a runner instance using its @AIRunner annotation for metadata.
     * 
     * @param runner The runner instance to register
     * @throws IllegalArgumentException if runner has no @AIRunner annotation
     */
    fun register(runner: BaseRunner) {
        indexLock.write {
            try {
                val runnerInfo = runner.getRunnerInfo()
                val capabilities = runner.getCapabilities()
                
                if (capabilities.isEmpty()) {
                    logger.w(TAG, "Runner ${runnerInfo.name} has no capabilities, skipping registration")
                    return@write
                }
                
                // Store by name (remove any existing with same name)
                val existingRunner = runnersByName.put(runnerInfo.name, runner)
                if (existingRunner != null) {
                    logger.d(TAG, "Replaced existing runner: ${runnerInfo.name}")
                    removeFromCapabilityIndex(existingRunner)
                }
                
                // Index by capabilities
                capabilities.forEach { capability ->
                    val runnerList = runnersByCapability.getOrPut(capability) { mutableListOf() }
                    runnerList.add(runner)
                }
                
                logger.d(TAG, "Registered runner '${runnerInfo.name}' with capabilities: ${capabilities.joinToString()}")
                
            } catch (e: Exception) {
                logger.e(TAG, "Failed to register runner", e)
                throw e
            }
        }
    }
    
    /**
     * Unregister a runner by name.
     * 
     * @param runnerName The name of the runner to unregister
     * @return true if a runner was removed, false if not found
     */
    fun unregister(runnerName: String): Boolean {
        return indexLock.write {
            val runner = runnersByName.remove(runnerName)
            if (runner != null) {
                removeFromCapabilityIndex(runner)
                logger.d(TAG, "Unregistered runner: $runnerName")
                true
            } else {
                logger.w(TAG, "Attempted to unregister unknown runner: $runnerName")
                false
            }
        }
    }
    
    /**
     * Get the first available runner for a specific capability.
     * Priority-based selection should be handled by RunnerPriorityResolver.
     * 
     * @param capability The AI capability needed
     * @return First available runner supporting the capability, or null if none found
     */
    fun getRunner(capability: CapabilityType): BaseRunner? {
        return indexLock.read {
            val runners = runnersByCapability[capability]
            if (runners.isNullOrEmpty()) {
                logger.d(TAG, "No runners available for capability: $capability")
                null
            } else {
                val runner = runners.first()
                logger.d(TAG, "Found runner '${runner.getRunnerInfo().name}' for capability $capability")
                runner
            }
        }
    }
    
    /**
     * Get all runners that support a specific capability.
     * 
     * @param capability The AI capability to search for
     * @return List of runners supporting the capability (may be empty)
     */
    fun getAllRunners(capability: CapabilityType): List<BaseRunner> {
        return indexLock.read {
            runnersByCapability[capability]?.toList() ?: emptyList()
        }
    }
    
    /**
     * Get all registered runners regardless of capability.
     * 
     * @return List of all registered runners
     */
    fun getAllRunners(): List<BaseRunner> {
        return indexLock.read {
            runnersByName.values.toList()
        }
    }
    
    /**
     * Get a runner by its exact name.
     * 
     * @param runnerName The name of the runner to find
     * @return The runner with the specified name, or null if not found
     */
    fun getRunnerByName(runnerName: String): BaseRunner? {
        return indexLock.read {
            runnersByName[runnerName]
        }
    }
    
    /**
     * Check if a runner with the specified name is registered.
     * 
     * @param runnerName The runner name to check
     * @return true if a runner with that name is registered
     */
    fun isRegistered(runnerName: String): Boolean {
        return runnersByName.containsKey(runnerName)
    }
    
    /**
     * Get the total number of registered runners.
     * 
     * @return Total count of registered runners
     */
    fun getTotalRunnerCount(): Int {
        return runnersByName.size
    }
    
    /**
     * Get the list of capabilities that have at least one registered runner.
     * 
     * @return List of supported capability types
     */
    fun getSupportedCapabilities(): List<CapabilityType> {
        return indexLock.read {
            runnersByCapability.keys
                .filter { capability -> runnersByCapability[capability]?.isNotEmpty() == true }
                .toList()
        }
    }
    
    /**
     * Get the names of all registered runners.
     * 
     * @return List of runner names
     */
    fun getRegisteredRunnerNames(): List<String> {
        return runnersByName.keys.toList()
    }
    
    /**
     * Get registry statistics for debugging and monitoring.
     * 
     * @return RegistryStats containing current registry state
     */
    fun getStats(): RegistryStats {
        return indexLock.read {
            RegistryStats(
                totalRunners = runnersByName.size,
                capabilityCount = runnersByCapability.size,
                runnersPerCapability = runnersByCapability.mapValues { it.value.size },
                supportedCapabilities = getSupportedCapabilities()
            )
        }
    }
    
    /**
     * Clear all registered runners.
     * This is typically used during shutdown or testing.
     */
    fun clear() {
        indexLock.write {
            runnersByName.clear()
            runnersByCapability.clear()
            logger.d(TAG, "Cleared all registered runners")
        }
    }
    
    /**
     * Check if the registry is empty.
     * 
     * @return true if no runners are registered
     */
    fun isEmpty(): Boolean {
        return runnersByName.isEmpty()
    }
    
    /**
     * Validate the internal consistency of the registry.
     * This is primarily used for debugging and testing.
     * 
     * @return ValidationResult indicating if the registry is consistent
     */
    fun validateConsistency(): ValidationResult {
        return indexLock.read {
            val errors = mutableListOf<String>()
            
            // Check that all runners in capability index are also in name index
            runnersByCapability.forEach { (capability, runners) ->
                runners.forEach { runner ->
                    val runnerName = runner.getRunnerInfo().name
                    if (!runnersByName.containsKey(runnerName)) {
                        errors.add("Runner '$runnerName' in capability $capability but not in name index")
                    }
                }
            }
            
            // Check that all runners in name index support their indexed capabilities
            runnersByName.forEach { (name, runner) ->
                val capabilities = runner.getCapabilities()
                capabilities.forEach { capability ->
                    val runnersForCapability = runnersByCapability[capability] ?: emptyList()
                    if (!runnersForCapability.contains(runner)) {
                        errors.add("Runner '$name' supports $capability but not indexed for it")
                    }
                }
            }
            
            if (errors.isEmpty()) {
                ValidationResult.success("Registry consistency validation passed")
            } else {
                ValidationResult.failure("Registry consistency errors: ${errors.joinToString("; ")}")
            }
        }
    }
    
    // Private helper methods
    
    /**
     * Remove a runner from all capability indexes.
     */
    private fun removeFromCapabilityIndex(runner: BaseRunner) {
        val capabilities = runner.getCapabilities()
        capabilities.forEach { capability ->
            runnersByCapability[capability]?.remove(runner)
            // Clean up empty lists
            if (runnersByCapability[capability]?.isEmpty() == true) {
                runnersByCapability.remove(capability)
            }
        }
    }
}

/**
 * Data class containing registry statistics.
 */
data class RegistryStats(
    val totalRunners: Int,
    val capabilityCount: Int,
    val runnersPerCapability: Map<CapabilityType, Int>,
    val supportedCapabilities: List<CapabilityType>
)

/**
 * Data class representing validation results.
 */
data class ValidationResult(
    val isValid: Boolean,
    val message: String,
    val metadata: Map<String, Any> = emptyMap()
) {
    companion object {
        fun success(message: String, metadata: Map<String, Any> = emptyMap()) = 
            ValidationResult(true, message, metadata)
        
        fun failure(message: String) = ValidationResult(false, message)
    }
}