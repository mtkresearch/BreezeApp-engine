package com.mtkresearch.breezeapp.engine.runner.core

import com.mtkresearch.breezeapp.engine.core.Logger
import com.mtkresearch.breezeapp.engine.model.ReloadResult
import com.mtkresearch.breezeapp.engine.model.CapabilityType
import com.mtkresearch.breezeapp.engine.model.EngineSettings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages the logic for reloading runners when engine settings change.
 * This class orchestrates the process in a safe and efficient manner.
 *
 * @param runnerManager The main RunnerManager instance.
 * @param logger A logger for diagnostics.
 */
class RunnerReloadManager(
    private val runnerManager: RunnerManager,
    private val logger: Logger
) {
    private val settingsObservers = mutableSetOf<(EngineSettings, ReloadResult) -> Unit>()
    private val reloadMutex = Mutex()

    fun observeSettingsChanges(observer: (EngineSettings, ReloadResult) -> Unit) {
        settingsObservers.add(observer)
    }

    /**
     * Handles the settings change event, analyzes differences, and triggers reloads.
     * This function is concurrency-safe.
     *
     * @param oldSettings The settings before the change.
     * @param newSettings The settings after the change.
     */
    suspend fun handleSettingsChange(
        oldSettings: EngineSettings,
        newSettings: EngineSettings
    ) {
        reloadMutex.withLock {
            var result: ReloadResult = ReloadResult.Success
            try {
                val changes = analyzeChanges(oldSettings, newSettings)
                if (changes.isEmpty()) {
                    logger.d(TAG, "No runner changes detected that require a reload.")
                    return@withLock
                }

                logger.d(TAG, "Settings changes detected, planning reload...")
                for (change in changes) {
                    when (change.type) {
                        ChangeType.RUNNER_SWITCHED -> {
                            logger.d(TAG, "Runner switched for capability: ${change.capability}. Reloading.")
                            // In a real implementation, you would unload the old runner
                            // and load the new one.
                            // e.g., reloadRunnerForCapability(change.capability, newSettings)
                        }
                        ChangeType.PARAMETERS_CHANGED -> {
                            logger.d(TAG, "Parameters changed for runner: ${change.runner}. Reloading.")
                            // In a real implementation, you would reload the specific runner
                            // with its new parameters.
                            // e.g., reloadRunnerWithNewParams(change.runner, change.newParams)
                        }
                    }
                }
            } catch (e: Exception) {
                logger.e(TAG, "Failed to reload runners after settings change", e)
                result = ReloadResult.Failure(e)
            } finally {
                settingsObservers.forEach { it(newSettings, result) }
            }
        }
    }

    /**
     * Compares old and new settings to determine what needs to be reloaded.
     */
    private fun analyzeChanges(old: EngineSettings, new: EngineSettings): List<SettingsChange> {
        val changes = mutableListOf<SettingsChange>()

        // 1. Check for runner switches
        CapabilityType.values().forEach { capability ->
            val oldRunner = old.selectedRunners[capability]
            val newRunner = new.selectedRunners[capability]
            if (oldRunner != newRunner && newRunner != null) {
                changes.add(SettingsChange(ChangeType.RUNNER_SWITCHED, capability = capability))
            }
        }

        // 2. Check for parameter changes for the *same* selected runners
        new.selectedRunners.forEach { (capability, runnerName) ->
            if (old.selectedRunners[capability] == runnerName) {
                val oldParams = old.getRunnerParameters(runnerName)
                val newParams = new.getRunnerParameters(runnerName)
                if (oldParams != newParams) {
                    changes.add(SettingsChange(
                        type = ChangeType.PARAMETERS_CHANGED,
                        runner = runnerName,
                        newParams = newParams
                    ))
                }
            }
        }
        return changes
    }

    companion object {
        private const val TAG = "RunnerReloadManager"
    }
}

/**
 * Represents a specific change in engine settings that requires action.
 */
internal data class SettingsChange(
    val type: ChangeType,
    val capability: CapabilityType? = null,
    val runner: String? = null,
    val newParams: Map<String, Any>? = null
)

/**
 * The type of setting change detected.
 */
internal enum class ChangeType {
    RUNNER_SWITCHED,
    PARAMETERS_CHANGED
}
