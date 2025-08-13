package com.mtkresearch.breezeapp.engine.config

import android.content.Context
import com.mtkresearch.breezeapp.engine.core.Logger
import com.mtkresearch.breezeapp.engine.runner.core.RunnerRegistry

private const val TAG = "ConfigManager"

/**
 * Configuration Manager (Backward Compatible Facade)
 * 
 * This class now serves as a simplified facade over the EnhancedConfigurationManager,
 * providing backward compatibility while offering access to enhanced features.
 * 
 * REFACTORED: Enhanced with:
 * - Dynamic configuration updates
 * - Better error handling and fallback
 * - Real-time configuration validation
 * - Plugin-based configuration sources
 * - Runtime configuration modification
 */
class ConfigurationManager(
    private val context: Context,
    private val logger: Logger
) {
    // Delegate to enhanced manager
    private val enhancedManager = EnhancedConfigurationManager(context, logger)

    /**
     * Reads the runner configuration file from assets, parses it, and registers
     * the runners with the provided [RunnerRegistry].
     *
     * @param registry The [RunnerRegistry] to register runners with.
     */
    fun loadAndRegisterRunners(registry: RunnerRegistry) {
        enhancedManager.loadAndRegisterRunners(registry)
    }
    
    // === Enhanced Features (Optional) ===
    
    /**
     * Access to enhanced configuration features
     */
    fun getEnhancedManager(): EnhancedConfigurationManager = enhancedManager
    
    /**
     * Get current configuration summary
     */
    fun getConfigurationSummary() = enhancedManager.getConfigurationSummary()
    
    /**
     * Enable or disable a runner at runtime
     */
    fun setRunnerEnabled(runnerName: String, enabled: Boolean) = 
        enhancedManager.setRunnerEnabled(runnerName, enabled)
    
    /**
     * Get registered runners information
     */
    fun getRegisteredRunners() = enhancedManager.getRegisteredRunners()

} 