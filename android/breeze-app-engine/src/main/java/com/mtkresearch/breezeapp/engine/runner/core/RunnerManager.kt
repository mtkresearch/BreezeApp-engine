package com.mtkresearch.breezeapp.engine.runner.core

import android.content.Context
import com.mtkresearch.breezeapp.engine.core.Logger
import com.mtkresearch.breezeapp.engine.core.StorageService
import com.mtkresearch.breezeapp.engine.model.CapabilityType
import com.mtkresearch.breezeapp.engine.model.EngineSettings
import com.mtkresearch.breezeapp.engine.model.ReloadResult
import com.mtkresearch.breezeapp.engine.service.ModelRegistryService
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * RunnerManager - Main Entry Point for AI Runner System
 *
 * This is the primary interface for all runner operations in BreezeApp Engine.
 * Provides a simple, unified API for runner discovery, registration, and selection.
 *
 * ## Architecture Overview
 * The runner system uses annotation-based auto-discovery to eliminate configuration complexity:
 * - Runners are annotated with @AIRunner containing all metadata
 * - No JSON configuration files needed
 * - Single entry point for all operations
 * - Thread-safe and optimized for performance
 *
 * ## Usage Example
 * ```kotlin
 * val runnerManager = RunnerManager(context, logger, storageService)
 *
 * // Initialize the entire runner system
 * val result = runnerManager.initialize()
 * if (result.isSuccess) {
 *     println("Registered ${result.getOrNull()} runners")
 * }
 *
 * // Get runners for specific capabilities
 * val llmRunner = runnerManager.getRunner(CapabilityType.LLM)
 *
 * // Cleanup when done
 * runnerManager.shutdown()
 * ```
 *
 * @param context Android context for accessing system services and assets
 * @param logger Logger instance for debugging and monitoring
 * @param storageService Service for persisting and loading engine settings.
 * @param enableAutoReload Flag to enable the automatic runner reload mechanism.
 *
 * @since Engine API v2.0
 * @see com.mtkresearch.breezeapp.engine.annotation.AIRunner
 */
class RunnerManager(
    private val context: Context,
    private val logger: Logger,
    private val storageService: StorageService, // NEW: For loading initial state
    private val modelRegistry: ModelRegistryService? = null, // NEW: For JSON-based model loading
    private val enableAutoReload: Boolean = true
) {
    companion object {
        private const val TAG = "RunnerManager"
        private const val INITIALIZATION_TIMEOUT_MS = 30000L // 30 seconds
    }

    // Core components
    private val discovery = RunnerAnnotationDiscovery(context, logger, modelRegistry)
    private val registry = RunnerRegistry(logger)
    private val priorityResolver = RunnerPriorityResolver
    private val reloadManager = if (enableAutoReload) {
        RunnerReloadManager(this, logger)
    } else null

    // State management
    private val isInitialized = AtomicBoolean(false)
    private val isShuttingDown = AtomicBoolean(false)
    private var initializationJob: Job? = null
    private var currentSettings: EngineSettings = storageService.loadSettings()

    /**
     * Initialize the runner system by discovering and registering all annotated runners.
     *
     * This method performs the following operations:
     * 1. Scans the classpath for @AIRunner annotated classes
     * 2. Validates each runner's annotation and hardware requirements
     * 3. Creates and registers runner instances
     * 4. Sets up priority-based selection
     *
     * @return Result containing the number of successfully registered runners, or error
     */
    suspend fun initialize(): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                if (isInitialized.get()) {
                    logger.w(TAG, "RunnerManager already initialized")
                    return@withContext Result.success(registry.getTotalRunnerCount())
                }

                if (isShuttingDown.get()) {
                    return@withContext Result.failure(
                        IllegalStateException("Cannot initialize during shutdown")
                    )
                }

                logger.d(TAG, "Initializing RunnerManager with annotation-based discovery")
                val startTime = System.currentTimeMillis()

                // Discover and register all annotated runners
                val registrationResult = discovery.discoverAndRegister(registry)

                if (registrationResult.isFailure) {
                    logger.e(TAG, "Failed to discover and register runners",
                        registrationResult.exceptionOrNull())
                    return@withContext registrationResult
                }

                val registeredCount = registrationResult.getOrNull() ?: 0
                val initTime = System.currentTimeMillis() - startTime

                isInitialized.set(true)
                logger.d(TAG, "RunnerManager initialized successfully: $registeredCount runners in ${initTime}ms")

                // Log summary for debugging
                logInitializationSummary(registeredCount)

                Result.success(registeredCount)

            } catch (e: Exception) {
                logger.e(TAG, "RunnerManager initialization failed", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Synchronous initialization with timeout.
     *
     * @param timeoutMs Maximum time to wait for initialization (default: 30 seconds)
     * @return Result containing the number of registered runners, or timeout error
     */
    fun initializeBlocking(timeoutMs: Long = INITIALIZATION_TIMEOUT_MS): Result<Int> {
        return runBlocking {
            try {
                withTimeout(timeoutMs) {
                    initialize()
                }
            } catch (e: TimeoutCancellationException) {
                logger.e(TAG, "RunnerManager initialization timed out after ${timeoutMs}ms")
                Result.failure(IllegalStateException("Initialization timeout"))
            }
        }
    }

    /**
     * Get the best available runner for a specific capability based on current settings.
     *
     * @param capability The AI capability needed (LLM, ASR, TTS, etc.)
     * @return The highest priority runner supporting the capability, or null if none available
     */
    fun getRunner(capability: CapabilityType): BaseRunner? {
        if (!isInitialized.get()) {
            logger.w(TAG, "Attempting to get runner before initialization. Call initialize() first.")
            return null
        }

        val selectedRunnerName = currentSettings.selectedRunners[capability]
        if (selectedRunnerName != null) {
            val runner = registry.getRunnerByName(selectedRunnerName)
            if (runner != null) {
                logger.d(TAG, "Returning user-selected runner '${runner.getRunnerInfo().name}' for $capability")
                return runner
            }
            logger.w(TAG, "Selected runner '$selectedRunnerName' not found, falling back to default.")
        }

        val candidates = registry.getAllRunners(capability)
        if (candidates.isEmpty()) {
            logger.w(TAG, "No runners available for capability: $capability")
            return null
        }

        val bestRunner = priorityResolver.selectBestRunner(candidates)
        if (bestRunner != null) {
            logger.d(TAG, "Selected default runner '${bestRunner.getRunnerInfo().name}' for capability $capability")
        }

        return bestRunner
    }

    /**
     * Get all available runners for a specific capability, sorted by priority.
     *
     * @param capability The AI capability to search for
     * @return List of runners supporting the capability, sorted by priority (best first)
     */
    fun getAllRunners(capability: CapabilityType): List<BaseRunner> {
        if (!isInitialized.get()) {
            logger.w(TAG, "Attempting to get runners before initialization")
            return emptyList()
        }

        val runners = registry.getAllRunners(capability)
        return priorityResolver.sortByPriority(runners)
    }

    /**
     * Get all registered runners regardless of capability.
     *
     * @return List of all registered runners
     */
    fun getAllRunners(): List<BaseRunner> {
        if (!isInitialized.get()) {
            logger.w(TAG, "Attempting to get all runners before initialization")
            return emptyList()
        }

        return registry.getAllRunners()
    }

    /**
     * Get runner system statistics.
     *
     * @return RunnerStats containing counts and capabilities
     */
    fun getStats(): RunnerStats {
        return RunnerStats(
            totalRunners = registry.getTotalRunnerCount(),
            runnersPerCapability = CapabilityType.values().associateWith { capability ->
                registry.getAllRunners(capability).size
            },
            supportedCapabilities = registry.getSupportedCapabilities(),
            isInitialized = isInitialized.get()
        )
    }

    /**
     * Check if a specific capability is supported.
     *
     * @param capability The capability to check
     * @return true if at least one runner supports the capability
     */
    fun isCapabilitySupported(capability: CapabilityType): Boolean {
        return getRunner(capability) != null
    }

    /**
     * Re-initialize the runner system.
     * This will clear all existing registrations and re-discover runners.
     *
     * @return Result containing the number of newly registered runners
     */
    suspend fun reinitialize(): Result<Int> {
        logger.d(TAG, "Re-initializing RunnerManager")

        // Clear existing state
        registry.clear()
        isInitialized.set(false)

        // Re-initialize
        return initialize()
    }

    /**
     * Shutdown the runner system and cleanup resources.
     * This should be called when the runner system is no longer needed.
     */
    fun shutdown() {
        if (isShuttingDown.getAndSet(true)) {
            logger.w(TAG, "RunnerManager already shutting down")
            return
        }

        logger.d(TAG, "Shutting down RunnerManager")

        try {
            // Cancel any ongoing initialization
            initializationJob?.cancel()

            // Cleanup registry
            registry.clear()

            // Reset state
            isInitialized.set(false)

            logger.d(TAG, "RunnerManager shutdown complete")

        } catch (e: Exception) {
            logger.e(TAG, "Error during RunnerManager shutdown", e)
        } finally {
            isShuttingDown.set(false)
        }
    }

    /**
     * Check if the runner system is initialized and ready to use.
     *
     * @return true if initialized, false otherwise
     */
    fun isInitialized(): Boolean = isInitialized.get()

    /**
     * Get all available runners regardless of capability.
     *
     * @return List of all registered runners
     */
    fun getAvailableRunners(): List<BaseRunner> {
        if (!isInitialized.get()) {
            logger.w(TAG, "Attempting to get all runners before initialization")
            return emptyList()
        }

        return registry.getAllRunners()
    }

    /**
     * Get parameter schema for a specific runner.
     *
     * @param runnerName The name of the runner to get parameters for
     * @return List of parameter schemas for the runner, or empty list if runner not found
     */
    fun getRunnerParameters(runnerName: String): List<ParameterSchema> {
        if (!isInitialized.get()) {
            logger.w(TAG, "Attempting to get runner parameters before initialization")
            return emptyList()
        }

        return try {
            val runner = registry.getRunnerByName(runnerName)
            runner?.getParameterSchema() ?: emptyList()
        } catch (e: Exception) {
            logger.e(TAG, "Error getting runner parameters for $runnerName", e)
            emptyList()
        }
    }

    /**
     * Get current engine settings.
     *
     * @return Current EngineSettings
     */
    fun getCurrentSettings(): EngineSettings {
        return currentSettings
    }

    /**
     * Saves engine settings and triggers the runner reload mechanism.
     *
     * @param settings The EngineSettings to save.
     */
    suspend fun saveSettings(settings: EngineSettings) {
        val oldSettings = currentSettings

        // 1. Persist the new settings
        storageService.saveSettings(settings)
        this.currentSettings = settings

        // 2. Trigger the reload process (now an async operation)
        reloadManager?.handleSettingsChange(oldSettings, settings)

        logger.d(TAG, "Settings saved and reload process initiated.")
    }

    /**
     * Allows UI components to observe the results of settings changes.
     *
     * @param observer A callback function to handle the reload result.
     */
    fun observeSettingsChanges(observer: (EngineSettings, ReloadResult) -> Unit) {
        reloadManager?.observeSettingsChanges(observer)
    }

    // Private helper methods

    private fun logInitializationSummary(registeredCount: Int) {
        val stats = getStats()
        val summary = buildString {
            appendLine("=== RunnerManager Initialization Summary ===")
            appendLine("Total runners registered: $registeredCount")
            appendLine("Supported capabilities: ${stats.supportedCapabilities.joinToString()}")
            stats.runnersPerCapability.forEach { (capability, count) ->
                if (count > 0) {
                    appendLine("  $capability: $count runners")
                }
            }
            appendLine("============================================")
        }
        logger.d(TAG, summary)
    }
}

/**
 * Data class containing runner system statistics.
 */
data class RunnerStats(
    val totalRunners: Int,
    val runnersPerCapability: Map<CapabilityType, Int>,
    val supportedCapabilities: List<CapabilityType>,
    val isInitialized: Boolean
)