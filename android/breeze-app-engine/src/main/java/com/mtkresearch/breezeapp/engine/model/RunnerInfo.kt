package com.mtkresearch.breezeapp.engine.model

import com.mtkresearch.breezeapp.engine.annotation.VendorType
import com.mtkresearch.breezeapp.engine.annotation.RunnerPriority
import com.mtkresearch.breezeapp.engine.annotation.HardwareRequirement
import kotlinx.parcelize.Parcelize
import android.os.Parcelable
import kotlinx.parcelize.RawValue

/**
 * Comprehensive information about an AI runner.
 * 
 * This data class provides all metadata about a runner including its capabilities,
 * vendor information, hardware requirements, and runtime status. Used throughout
 * the system for runner selection, validation, and monitoring.
 * 
 * **Thread Safety:** This class is immutable and thread-safe.
 * **Serialization:** Implements Parcelable for efficient IPC and bundle storage.
 * 
 * @since Engine API v2.0
 * @see com.mtkresearch.breezeapp.engine.runner.core.BaseRunner.getRunnerInfo
 */
@Parcelize
data class RunnerInfo(
    /**
     * Unique identifier for this runner.
     * 
     * Should be stable across app restarts and uniquely identify
     * this specific runner implementation.
     * 
     * Format: "{Vendor}-{Capability}-{Variant}" (e.g., "MediaTek-LLM-Premium")
     */
    val name: String,
    
    /**
     * Human-readable display name for this runner.
     * 
     * Used in UI and logs for user-friendly identification.
     * Should be descriptive but concise.
     */
    val displayName: String = name,
    
    /**
     * Version of this runner implementation.
     * 
     * Follows semantic versioning (MAJOR.MINOR.PATCH).
     * Used for compatibility checking and debugging.
     */
    val version: String,
    
    /**
     * List of AI capabilities this runner supports.
     * 
     * A runner may support multiple capabilities (e.g., both LLM and TTS).
     * Used for capability-based runner selection.
     */
    val capabilities: List<CapabilityType>,
    
    /**
     * The AI provider/technology vendor for this runner.
     * 
     * Identifies the underlying AI technology being used.
     */
    val vendor: VendorType,
    
    /**
     * Priority level of this runner within its vendor category.
     * 
     * Used for selection when multiple runners from the same vendor
     * support the same capability.
     */
    val priority: RunnerPriority,
    
    /**
     * Hardware requirements needed by this runner.
     * 
     * Used for device compatibility validation during registration.
     */
    val hardwareRequirements: List<HardwareRequirement>,
    
    /**
     * Detailed description of this runner's functionality.
     * 
     * Provides context about what this runner does, its strengths,
     * and appropriate use cases.
     */
    val description: String,
    
    /**
     * Whether this runner is currently enabled for use.
     * 
     * Disabled runners are discovered but not available for selection.
     * Can be controlled via feature flags or configuration.
     */
    val enabled: Boolean = true,
    
    /**
     * API level this runner was designed for.
     * 
     * Used for compatibility checking and migration support.
     */
    val apiLevel: Int = 1,
    
    /**
     * Additional metadata about this runner.
     * 
     * Extensible map for runner-specific information such as:
     * - Model names and versions
     * - Configuration parameters
     * - Performance characteristics
     * - Vendor-specific features
     */
    val metadata: @RawValue Map<String, Any> = emptyMap(),
    
    /**
     * Timestamp when this runner info was created.
     * 
     * Used for tracking registration time and debugging.
     */
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable {
    
    /**
     * Returns true if this runner supports the specified capability.
     * 
     * @param capability The capability to check
     * @return true if the capability is supported
     */
    fun supportsCapability(capability: CapabilityType): Boolean {
        return capabilities.contains(capability)
    }
    
    /**
     * Returns true if this runner supports any of the specified capabilities.
     * 
     * @param capabilities The capabilities to check
     * @return true if any capability is supported
     */
    fun supportsAnyCapability(vararg capabilities: CapabilityType): Boolean {
        return capabilities.any { supportsCapability(it) }
    }
    
    /**
     * Returns true if this runner supports all of the specified capabilities.
     * 
     * @param capabilities The capabilities to check
     * @return true if all capabilities are supported
     */
    fun supportsAllCapabilities(vararg capabilities: CapabilityType): Boolean {
        return capabilities.all { supportsCapability(it) }
    }
    
    /**
     * Returns metadata value cast to the specified type.
     * 
     * @param key The metadata key
     * @return The metadata value cast to type T, or null if not found or wrong type
     */
    inline fun <reified T> getMetadata(key: String): T? {
        return metadata[key] as? T
    }
    
    /**
     * Returns metadata value with a default fallback.
     * 
     * @param key The metadata key
     * @param defaultValue The default value if key not found
     * @return The metadata value or default value
     */
    inline fun <reified T> getMetadata(key: String, defaultValue: T): T {
        return getMetadata<T>(key) ?: defaultValue
    }
    
    /**
     * Creates a copy of this RunnerInfo with updated metadata.
     * 
     * @param additionalMetadata Additional metadata to merge
     * @return New RunnerInfo instance with merged metadata
     */
    fun withMetadata(additionalMetadata: Map<String, Any>): RunnerInfo {
        return copy(metadata = metadata + additionalMetadata)
    }
    
    /**
     * Creates a copy of this RunnerInfo with updated enabled status.
     * 
     * @param enabled Whether the runner should be enabled
     * @return New RunnerInfo instance with updated status
     */
    fun withEnabled(enabled: Boolean): RunnerInfo {
        return copy(enabled = enabled)
    }
    
    /**
     * Returns a summary string for logging and debugging.
     */
    fun toSummaryString(): String {
        return "RunnerInfo(name='$name', vendor=$vendor, priority=$priority, " +
                "capabilities=${capabilities.joinToString()}, enabled=$enabled)"
    }
    
    companion object {
        /**
         * Common metadata keys for standardized information.
         */
        object MetadataKeys {
            const val MODEL_NAME = "model_name"
            const val MODEL_VERSION = "model_version"
            const val MODEL_SIZE = "model_size"
            const val CONTEXT_LENGTH = "context_length"
            const val VOCAB_SIZE = "vocab_size"
            const val BACKEND_NAME = "backend_name"
            const val BACKEND_VERSION = "backend_version"
            const val PERFORMANCE_TIER = "performance_tier"
            const val MEMORY_USAGE = "memory_usage"
            const val SUPPORTED_LANGUAGES = "supported_languages"
            const val FEATURES = "features"
        }
        
        /**
         * Creates a RunnerInfo builder for complex construction.
         * 
         * @param name The runner name
         * @param version The runner version
         * @return RunnerInfoBuilder instance
         */
        fun builder(name: String, version: String): RunnerInfoBuilder {
            return RunnerInfoBuilder(name, version)
        }
        
        /**
         * Creates a minimal RunnerInfo for testing or mock runners.
         * 
         * @param name The runner name
         * @param capability Primary capability
         * @param vendor Vendor type
         * @return Minimal RunnerInfo instance
         */
        fun minimal(
            name: String,
            capability: CapabilityType,
            vendor: VendorType = VendorType.UNKNOWN
        ): RunnerInfo {
            return RunnerInfo(
                name = name,
                version = "1.0.0",
                capabilities = listOf(capability),
                vendor = vendor,
                priority = RunnerPriority.NORMAL,
                hardwareRequirements = emptyList(),
                description = "Basic $name runner for ${capability.name}"
            )
        }
    }
}

/**
 * Builder pattern for constructing complex RunnerInfo instances.
 * 
 * Provides a fluent API for setting optional properties and metadata.
 */
class RunnerInfoBuilder internal constructor(
    private val name: String,
    private val version: String
) {
    private var displayName: String = name
    private var capabilities: MutableList<CapabilityType> = mutableListOf()
    private var vendor: VendorType = VendorType.UNKNOWN
    private var priority: RunnerPriority = RunnerPriority.NORMAL
    private var hardwareRequirements: MutableList<HardwareRequirement> = mutableListOf()
    private var description: String = ""
    private var enabled: Boolean = true
    private var apiLevel: Int = 1
    private var metadata: MutableMap<String, Any> = mutableMapOf()
    
    fun displayName(displayName: String) = apply { this.displayName = displayName }
    fun capabilities(vararg capabilities: CapabilityType) = apply { 
        this.capabilities.addAll(capabilities) 
    }
    fun vendor(vendor: VendorType) = apply { this.vendor = vendor }
    fun priority(priority: RunnerPriority) = apply { this.priority = priority }
    fun hardwareRequirements(vararg requirements: HardwareRequirement) = apply {
        this.hardwareRequirements.addAll(requirements)
    }
    fun description(description: String) = apply { this.description = description }
    fun enabled(enabled: Boolean) = apply { this.enabled = enabled }
    fun apiLevel(apiLevel: Int) = apply { this.apiLevel = apiLevel }
    fun metadata(key: String, value: Any) = apply { this.metadata[key] = value }
    fun metadata(metadata: Map<String, Any>) = apply { this.metadata.putAll(metadata) }
    
    fun build(): RunnerInfo {
        require(capabilities.isNotEmpty()) { "Runner must support at least one capability" }
        require(description.isNotBlank()) { "Runner description cannot be blank" }
        
        return RunnerInfo(
            name = name,
            displayName = displayName,
            version = version,
            capabilities = capabilities.toList(),
            vendor = vendor,
            priority = priority,
            hardwareRequirements = hardwareRequirements.toList(),
            description = description,
            enabled = enabled,
            apiLevel = apiLevel,
            metadata = metadata.toMap()
        )
    }
}