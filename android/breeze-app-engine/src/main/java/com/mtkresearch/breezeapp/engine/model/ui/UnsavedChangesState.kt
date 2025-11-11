package com.mtkresearch.breezeapp.engine.model.ui

import com.mtkresearch.breezeapp.engine.model.CapabilityType

/**
 * Tracks modification state for all runner/capability combinations.
 *
 * Stores original and current parameter values to detect changes and support restoration.
 * This class maintains dirty state tracking throughout the Activity lifecycle.
 *
 * Thread Safety: Main thread only (UI-scoped)
 * Lifecycle: Activity-scoped, cleared on save/discard
 */
data class UnsavedChangesState(
    private val dirtyState: MutableMap<CapabilityType, RunnerState> = mutableMapOf()
) {
    /**
     * Internal state for a specific runner
     */
    data class RunnerState(
        val runnerName: String,
        val parameters: MutableMap<String, ParameterChange> = mutableMapOf()
    )

    /**
     * Represents a parameter modification
     */
    data class ParameterChange(
        val original: Any?,
        val current: Any?
    ) {
        fun isDirty(): Boolean = original != current
    }

    /**
     * Track a parameter change for a specific runner/capability
     *
     * @param capability The capability type (LLM, ASR, TTS, VLM, Guardian)
     * @param runnerName The runner identifier
     * @param parameterName The parameter being modified
     * @param originalValue The value when settings were last saved
     * @param currentValue The current value in the UI
     */
    fun trackChange(
        capability: CapabilityType,
        runnerName: String,
        parameterName: String,
        originalValue: Any?,
        currentValue: Any?
    ) {
        val runnerState = dirtyState.getOrPut(capability) {
            RunnerState(runnerName)
        }

        runnerState.parameters[parameterName] = ParameterChange(originalValue, currentValue)
    }

    /**
     * Check if a specific runner has unsaved changes
     *
     * @param capability The capability type
     * @param runnerName The runner identifier
     * @return true if any parameter differs from its original value
     */
    fun isDirty(capability: CapabilityType, runnerName: String): Boolean {
        val runnerState = dirtyState[capability] ?: return false
        if (runnerState.runnerName != runnerName) return false

        return runnerState.parameters.values.any { it.isDirty() }
    }

    /**
     * Check if a specific runner within a capability has unsaved changes
     *
     * @param capability The capability type
     * @param runnerName The runner identifier
     * @return true if any parameter for this runner differs from its original value
     */
    fun hasChangesForRunner(capability: CapabilityType, runnerName: String): Boolean {
        return isDirty(capability, runnerName)
    }

    /**
     * Check if any runner across all capabilities has unsaved changes
     *
     * @return true if at least one parameter in any runner is dirty
     */
    fun hasAnyUnsavedChanges(): Boolean {
        return dirtyState.values.any { runnerState ->
            runnerState.parameters.values.any { it.isDirty() }
        }
    }

    /**
     * Get all modified parameters for a runner (current values only)
     *
     * @param capability The capability type
     * @param runnerName The runner identifier
     * @return Map of parameter name -> current value (only dirty parameters included)
     */
    fun getModifiedParameters(
        capability: CapabilityType,
        runnerName: String
    ): Map<String, Any?> {
        val runnerState = dirtyState[capability] ?: return emptyMap()
        if (runnerState.runnerName != runnerName) return emptyMap()

        return runnerState.parameters
            .filter { it.value.isDirty() }
            .mapValues { it.value.current }
    }

    /**
     * Get original parameter values for a runner
     *
     * @param capability The capability type
     * @param runnerName The runner identifier
     * @return Map of parameter name -> original value (only tracked parameters)
     */
    fun getOriginalParameters(
        capability: CapabilityType,
        runnerName: String
    ): Map<String, Any?> {
        val runnerState = dirtyState[capability] ?: return emptyMap()
        if (runnerState.runnerName != runnerName) return emptyMap()

        return runnerState.parameters.mapValues { it.value.original }
    }

    /**
     * Clear dirty state for a specific runner
     *
     * @param capability The capability type
     * @param runnerName The runner identifier
     */
    fun clear(capability: CapabilityType, runnerName: String) {
        val runnerState = dirtyState[capability]
        if (runnerState?.runnerName == runnerName) {
            dirtyState.remove(capability)
        }
    }

    /**
     * Clear all dirty state across all runners
     */
    fun clearAll() {
        dirtyState.clear()
    }

    /**
     * Get list of all runners with unsaved changes
     *
     * @return List of (CapabilityType, RunnerName) pairs for all dirty runners
     */
    fun getDirtyRunners(): List<Pair<CapabilityType, String>> {
        return dirtyState
            .filter { (_, runnerState) ->
                runnerState.parameters.values.any { it.isDirty() }
            }
            .map { (capability, runnerState) ->
                Pair(capability, runnerState.runnerName)
            }
    }
}
