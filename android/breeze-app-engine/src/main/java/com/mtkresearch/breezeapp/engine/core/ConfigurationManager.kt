package com.mtkresearch.breezeapp.engine.config

import android.content.Context
import com.mtkresearch.breezeapp.engine.core.Logger
import com.mtkresearch.breezeapp.engine.data.runner.core.RunnerFactory
import com.mtkresearch.breezeapp.engine.data.runner.core.RunnerSelectionStrategy
import com.mtkresearch.breezeapp.engine.domain.model.CapabilityType
import com.mtkresearch.breezeapp.engine.runner.core.RunnerRegistry
import kotlinx.serialization.json.Json
import java.io.IOException

private const val TAG = "ConfigManager"

/**
 * Manages loading and registering runners from an external configuration file.
 * This class decouples the runner registration logic from the application's source code,
 * allowing for dynamic configuration without recompiling the app.
 */
class ConfigurationManager(
    private val context: Context,
    private val logger: Logger
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val runnerFactory = RunnerFactory(context, logger)

    /**
     * Reads the runner configuration file from assets, parses it, and registers
     * the runners with the provided [RunnerRegistry].
     *
     * @param registry The [RunnerRegistry] to register runners with.
     */
    fun loadAndRegisterRunners(registry: RunnerRegistry) {
        try {
            val jsonString = readConfigFileFromAssets()
            val configFile = json.decodeFromString<RunnerConfigFile>(jsonString)

            logger.d(TAG, "Loading configuration version: ${configFile.version}")
            
            // Initialize smart selection if v2.0 format
            if (configFile.isV2Format()) {
                initializeSmartSelection(configFile, registry)
            }
            
            // Get all runner definitions regardless of format version
            val runnerDefinitions = configFile.getAllRunnerDefinitions()
            
            runnerDefinitions.forEach { definition ->
                try {
                    registerRunnerFromDefinition(definition, registry)
                } catch (e: Exception) {
                    logger.e(TAG, "Failed to register runner '${definition.name}': ${e.message}", e)
                }
            }
            
            logger.d(TAG, "Successfully registered ${runnerDefinitions.size} runners")
        } catch (e: Exception) {
            logger.e(TAG, "Failed to load or parse runner configuration: ${e.message}", e)
        }
    }

    private fun registerRunnerFromDefinition(definition: RunnerDefinition, registry: RunnerRegistry) {
        // Skip disabled runners
        if (!definition.enabled) {
            logger.d(TAG, "Skipping disabled runner: ${definition.name}")
            return
        }
        
        val runnerClass = Class.forName(definition.className)

        // For "real" runners, check if the device supports them before registering.
        if (definition.isReal) {
            try {
                val isSupportedMethod = runnerClass.getMethod("isSupported")
                val isSupported = isSupportedMethod.invoke(null) as? Boolean ?: false
                if (!isSupported) {
                    logger.d(TAG, "Skipping unsupported real runner: ${definition.name}")
                    return
                }
            } catch (e: NoSuchMethodException) {
                logger.w(TAG, "Runner '${definition.name}' is marked as real but has no static isSupported() method. Skipping.")
                return
            }
        }

        // Use the smart factory to create runners
        val factory = {
            runnerFactory.createRunner(definition)
                ?: throw IllegalStateException("Failed to create runner: ${definition.name}")
        }

        // Convert capability strings to Enum types.
        val capabilities = definition.capabilities.mapNotNull {
            try {
                CapabilityType.valueOf(it)
            } catch (e: IllegalArgumentException) {
                logger.w(TAG, "Unknown capability '${it}' for runner '${definition.name}'. Ignoring.")
                null
            }
        }

        if (capabilities.isEmpty()) {
            logger.w(TAG, "Runner '${definition.name}' has no valid capabilities. Skipping registration.")
            return
        }

        val registration = RunnerRegistry.RunnerRegistration(
            name = definition.name,
            factory = factory,
            capabilities = capabilities,
            priority = definition.priority,
        )
        
        // Use the new registerWithDefinition method for smart selection
        registry.registerWithDefinition(registration, definition)
    }
    
    /**
     * Initialize smart selection features for v2.0 configuration format.
     */
    private fun initializeSmartSelection(configFile: RunnerConfigFile, registry: RunnerRegistry) {
        try {
            // Set selection strategy
            val strategyName = configFile.defaultStrategy ?: "MOCK_FIRST"
            val strategy = RunnerSelectionStrategy.fromString(strategyName)
            registry.setSelectionStrategy(strategy)
            logger.d(TAG, "Initialized selection strategy: $strategyName")
            
            // Initialize hardware capabilities
            val hardwareCapabilities = mutableSetOf<String>()
            
            // Check for MTK NPU
            try {
                val hardwareCompatibility = Class.forName("com.mtkresearch.breezeapp.engine.system.HardwareCompatibility")
                val hasMTKNPUMethod = hardwareCompatibility.getMethod("hasMTKNPU")
                val hasMTKNPU = hasMTKNPUMethod.invoke(null) as? Boolean ?: false
                if (hasMTKNPU) {
                    hardwareCapabilities.add("MTK_NPU")
                }
            } catch (e: Exception) {
                logger.w(TAG, "Could not check MTK NPU availability: ${e.message}")
            }
            
            // Check for internet connection (simplified check)
            hardwareCapabilities.add("INTERNET_CONNECTION") // Assume available for now
            
            registry.updateHardwareCapabilities(hardwareCapabilities)
            logger.d(TAG, "Initialized hardware capabilities: $hardwareCapabilities")
            
        } catch (e: Exception) {
            logger.e(TAG, "Failed to initialize smart selection: ${e.message}", e)
        }
    }

    private fun readConfigFileFromAssets(): String {
        try {
            return context.assets.open("runner_config.json").bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            logger.e(TAG, "Could not read runner_config.json from assets.", e)
            throw e
        }
    }

} 