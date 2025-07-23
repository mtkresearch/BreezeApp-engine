package com.mtkresearch.breezeapp.engine.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Enhanced runner configuration supporting both legacy and new formats.
 * Version 2.0 introduces capability-based organization and smart selection strategies.
 */
@Serializable
data class RunnerConfigFile(
    /** Configuration version - determines parsing strategy */
    val version: String = "1.0",
    
    /** Legacy format: List of runner definitions (v1.0) */
    val runners: List<RunnerDefinition>? = null,
    
    /** New format: Strategy for runner selection (v2.0) */
    val defaultStrategy: String? = null,
    
    /** New format: Global configuration settings (v2.0) */
    val globalSettings: GlobalSettings? = null,
    
    /** New format: Capability-based runner organization (v2.0) */
    val capabilities: Map<String, CapabilityConfig>? = null
) {
    /**
     * Check if this is the new v2.0 format
     */
    fun isV2Format(): Boolean = version == "2.0" && capabilities != null
    
    /**
     * Get all runner definitions regardless of format version
     */
    fun getAllRunnerDefinitions(): List<RunnerDefinition> {
        return if (isV2Format()) {
            // Convert v2.0 format to runner definitions
            capabilities!!.flatMap { (capabilityName, capabilityConfig) ->
                capabilityConfig.runners.map { (runnerName, runnerConfig) ->
                    RunnerDefinition(
                        name = runnerName,
                        className = runnerConfig.className,
                        capabilities = listOf(capabilityName),
                        priority = runnerConfig.priority,
                        isReal = runnerConfig.type != "MOCK",
                        modelId = runnerConfig.modelId,
                        enabled = runnerConfig.enabled,
                        requirements = runnerConfig.requirements,
                        alwaysAvailable = runnerConfig.alwaysAvailable
                    )
                }
            }
        } else {
            // Legacy v1.0 format
            runners ?: emptyList()
        }
    }
}

/**
 * Global settings for runner management (v2.0)
 */
@Serializable
data class GlobalSettings(
    val enableHardwareDetection: Boolean = true,
    val fallbackToMock: Boolean = true,
    val maxInitRetries: Int = 3,
    val defaultTimeoutMs: Long = 30000
)

/**
 * Configuration for a specific capability (v2.0)
 */
@Serializable
data class CapabilityConfig(
    val defaultRunner: String,
    val runners: Map<String, RunnerConfig>
)

/**
 * Configuration for a specific runner (v2.0)
 */
@Serializable
data class RunnerConfig(
    @SerialName("class")
    val className: String,
    val priority: Int,
    val type: String, // "MOCK", "HARDWARE", "CLOUD", etc.
    val enabled: Boolean = true,
    val requirements: List<String>? = null,
    val alwaysAvailable: Boolean? = null,
    val modelId: String? = null
)

/**
 * Enhanced runner definition supporting both legacy and new formats.
 */
@Serializable
data class RunnerDefinition(
    /** The unique name/identifier for the runner. */
    val name: String,

    /** The fully qualified class name of the runner implementation. */
    @SerialName("class")
    val className: String,

    /** A list of capability names (e.g., "LLM", "ASR") that this runner supports. */
    val capabilities: List<String>,

    /** The priority of the runner (lower is higher). */
    val priority: Int,

    /** A flag to indicate if this is a "real" runner that requires a support check. */
    @SerialName("is_real")
    val isReal: Boolean = false,

    /** The default model ID that this runner supports. */
    val modelId: String? = null,
    
    /** Whether this runner is currently enabled (v2.0) */
    val enabled: Boolean = true,
    
    /** Hardware/software requirements for this runner (v2.0) */
    val requirements: List<String>? = null,
    
    /** Whether this runner is always available regardless of requirements (v2.0) */
    val alwaysAvailable: Boolean? = null
) 