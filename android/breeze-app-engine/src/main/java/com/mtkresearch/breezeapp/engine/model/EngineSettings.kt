package com.mtkresearch.breezeapp.engine.model

import com.mtkresearch.breezeapp.engine.model.CapabilityType
import com.mtkresearch.breezeapp.engine.runner.guardian.GuardianPipelineConfig

/**
 * Engine Settings Model
 * 
 * Represents the configuration settings for the BreezeApp Engine, including
 * runner selections, their parameters, and guardian pipeline configuration.
 * 
 * This model is used to persist and retrieve engine settings.
 */
data class EngineSettings(
    val selectedRunners: Map<CapabilityType, String> = emptyMap(),
    val runnerParameters: Map<String, Map<String, Any>> = emptyMap(),
    val guardianConfig: GuardianPipelineConfig = GuardianPipelineConfig.DEFAULT_SAFE
) {
    /**
     * Get parameters for a specific runner
     */
    fun getRunnerParameters(runnerName: String): Map<String, Any> {
        return runnerParameters[runnerName] ?: emptyMap()
    }

    /**
     * Get the selected model ID for a specific runner
     * Returns null if no model is explicitly set (will use runner's default)
     */
    fun getSelectedModel(runnerName: String): String? {
        return runnerParameters[runnerName]?.get("model") as? String
    }

    /**
     * Set the model ID for a specific runner
     * This creates a new EngineSettings with the model parameter added to the runner's parameters
     *
     * Example usage:
     * ```
     * val settings = currentSettings.withSelectedModel("SherpaOfflineASRRunner", "sherpa-onnx-whisper-medium")
     * ```
     */
    fun withSelectedModel(runnerName: String, modelId: String): EngineSettings {
        val currentParams = runnerParameters[runnerName]?.toMutableMap() ?: mutableMapOf()
        currentParams["model"] = modelId

        val newRunnerParameters = runnerParameters.toMutableMap().apply {
            put(runnerName, currentParams)
        }
        return this.copy(runnerParameters = newRunnerParameters)
    }
    
    /**
     * Create a new EngineSettings with updated runner selection
     */
    fun withRunnerSelection(capability: CapabilityType, runnerName: String): EngineSettings {
        val newSelectedRunners = selectedRunners.toMutableMap().apply {
            put(capability, runnerName)
        }
        return this.copy(selectedRunners = newSelectedRunners)
    }
    
    /**
     * Create a new EngineSettings with updated runner parameters
     */
    fun withRunnerParameters(runnerName: String, parameters: Map<String, Any>): EngineSettings {
        val newRunnerParameters = runnerParameters.toMutableMap().apply {
            put(runnerName, parameters)
        }
        return this.copy(runnerParameters = newRunnerParameters)
    }
    
    /**
     * Create a new EngineSettings with updated guardian configuration
     */
    fun withGuardianConfig(config: GuardianPipelineConfig): EngineSettings {
        return this.copy(guardianConfig = config)
    }
    
    /**
     * Check if migration from legacy format is needed
     */
    fun needsMigration(): Boolean {
        // For now, no migration needed as this is a new model
        return false
    }
    
    /**
     * Migrate from legacy format to new dynamic format
     */
    fun migrateFromLegacy(): EngineSettings {
        // For now, no migration needed as this is a new model
        return this
    }
    
    companion object {
        /**
         * Create default engine settings
         */
        fun default(): EngineSettings {
            return EngineSettings(
                selectedRunners = emptyMap(),
                runnerParameters = emptyMap(),
                guardianConfig = GuardianPipelineConfig.DEFAULT_SAFE
            )
        }
    }
}