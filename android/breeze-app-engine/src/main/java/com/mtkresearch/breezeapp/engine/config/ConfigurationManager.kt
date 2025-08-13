package com.mtkresearch.breezeapp.engine.config

import android.content.Context
import com.mtkresearch.breezeapp.engine.core.Logger
import com.mtkresearch.breezeapp.engine.runner.core.RunnerFactory
import com.mtkresearch.breezeapp.engine.runner.core.RunnerSelectionStrategy
import com.mtkresearch.breezeapp.engine.domain.model.CapabilityType
import com.mtkresearch.breezeapp.engine.runner.core.RunnerRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Configuration Manager - Manages runner configurations with flexibility and dynamic updates
 * 
 * Key Features:
 * - Support for dynamic configuration updates
 * - Better error handling and fallback mechanisms
 * - Real-time configuration validation
 * - Plugin-based configuration sources
 * - Configuration change notifications
 * - Runtime configuration modification
 * - Better separation of concerns
 */
class ConfigurationManager(
    private val context: Context,
    private val logger: Logger
) {
    companion object {
        private const val TAG = "ConfigurationManager"
        private const val DEFAULT_CONFIG_FILE = "runner_config.json"
        private const val FALLBACK_CONFIG_FILE = "runner_config_fallback.json"
    }
    
    // Configuration state management
    private val _configurationState = MutableStateFlow<ConfigurationState>(ConfigurationState.Loading)
    val configurationState: StateFlow<ConfigurationState> = _configurationState.asStateFlow()
    
    private val _activeConfiguration = MutableStateFlow<RunnerConfigFile?>(null)
    val activeConfiguration: StateFlow<RunnerConfigFile?> = _activeConfiguration.asStateFlow()
    
    // Configuration sources
    private val configurationSources = mutableListOf<ConfigurationSource>()
    private val registeredRunners = ConcurrentHashMap<String, RunnerRegistrationInfo>()
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = false
        prettyPrint = true
    }
    private val runnerFactory = RunnerFactory(context, logger)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    init {
        // Initialize default configuration sources
        addConfigurationSource(AssetConfigurationSource(context, DEFAULT_CONFIG_FILE))
        addConfigurationSource(AssetConfigurationSource(context, FALLBACK_CONFIG_FILE))
        
        // Start configuration monitoring
        startConfigurationMonitoring()
    }
    
    // === Configuration Sources ===
    
    /**
     * Add a configuration source (e.g., assets, remote, preferences)
     */
    fun addConfigurationSource(source: ConfigurationSource) {
        configurationSources.add(source)
        logger.d(TAG, "Added configuration source: ${source.name}")
    }
    
    /**
     * Remove a configuration source
     */
    fun removeConfigurationSource(source: ConfigurationSource) {
        configurationSources.remove(source)
        logger.d(TAG, "Removed configuration source: ${source.name}")
    }
    
    // === Enhanced Loading ===
    
    /**
     * Load and register runners with enhanced error handling and fallback
     */
    suspend fun loadAndRegisterRunnersAsync(registry: RunnerRegistry): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                _configurationState.value = ConfigurationState.Loading
                
                val configFile = loadConfigurationWithFallback()
                    ?: return@withContext Result.failure(
                        IllegalStateException("No valid configuration found from any source")
                    )
                
                _activeConfiguration.value = configFile
                _configurationState.value = ConfigurationState.Validating
                
                // Validate configuration
                val validationResult = validateConfiguration(configFile)
                if (!validationResult.isSuccess) {
                    val error = (validationResult as ValidationResult.Failure).error
                    _configurationState.value = ConfigurationState.Error(error)
                    return@withContext Result.failure(error)
                }
                
                // Initialize smart selection for v2.0 format
                if (configFile.isV2Format()) {
                    initializeSmartSelection(configFile, registry)
                }
                
                // Register runners with enhanced tracking
                val registeredCount = registerRunnersWithTracking(configFile, registry)
                
                _configurationState.value = ConfigurationState.Ready(registeredCount)
                logger.d(TAG, "Successfully loaded and registered $registeredCount runners")
                
                Result.success(registeredCount)
                
            } catch (e: Exception) {
                val errorState = ConfigurationState.Error(e)
                _configurationState.value = errorState
                logger.e(TAG, "Failed to load configuration", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Synchronous version for backward compatibility
     */
    fun loadAndRegisterRunners(registry: RunnerRegistry) {
        runBlocking {
            loadAndRegisterRunnersAsync(registry)
        }
    }
    
    // === Dynamic Configuration Updates ===
    
    /**
     * Update configuration at runtime
     */
    suspend fun updateConfiguration(newConfig: RunnerConfigFile): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                _configurationState.value = ConfigurationState.Updating
                
                // Validate new configuration
                val validationResult = validateConfiguration(newConfig)
                if (!validationResult.isSuccess) {
                    val error = (validationResult as ValidationResult.Failure).error
                    _configurationState.value = ConfigurationState.Error(error)
                    return@withContext Result.failure(error)
                }
                
                // Update active configuration
                _activeConfiguration.value = newConfig
                _configurationState.value = ConfigurationState.Ready(0) // Will be updated after re-registration
                
                logger.d(TAG, "Configuration updated successfully")
                Result.success(0)
                
            } catch (e: Exception) {
                _configurationState.value = ConfigurationState.Error(e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Enable or disable a specific runner at runtime
     */
    fun setRunnerEnabled(runnerName: String, enabled: Boolean): Boolean {
        val registration = registeredRunners[runnerName]
        if (registration != null) {
            registration.enabled = enabled
            logger.d(TAG, "${if (enabled) "Enabled" else "Disabled"} runner: $runnerName")
            return true
        }
        return false
    }
    
    /**
     * Update runner priority at runtime
     */
    fun setRunnerPriority(runnerName: String, priority: Int): Boolean {
        val registration = registeredRunners[runnerName]
        if (registration != null) {
            registration.priority = priority
            logger.d(TAG, "Updated priority for runner $runnerName to $priority")
            return true
        }
        return false
    }
    
    // === Configuration Queries ===
    
    /**
     * Get current configuration summary
     */
    fun getConfigurationSummary(): ConfigurationSummary {
        val config = _activeConfiguration.value
        return ConfigurationSummary(
            version = config?.version ?: "unknown",
            totalRunners = registeredRunners.size,
            enabledRunners = registeredRunners.values.count { it.enabled },
            capabilities = registeredRunners.values.flatMap { it.capabilities }.distinct(),
            lastUpdated = System.currentTimeMillis()
        )
    }
    
    /**
     * Get detailed information about registered runners
     */
    fun getRegisteredRunners(): Map<String, RunnerRegistrationInfo> {
        return registeredRunners.toMap()
    }
    
    /**
     * Get runners for a specific capability
     */
    fun getRunnersForCapability(capability: CapabilityType): List<RunnerRegistrationInfo> {
        return registeredRunners.values
            .filter { it.capabilities.contains(capability) && it.enabled }
            .sortedBy { it.priority }
    }
    
    // === Private Implementation ===
    
    private suspend fun loadConfigurationWithFallback(): RunnerConfigFile? {
        for (source in configurationSources) {
            try {
                val configContent = source.loadConfiguration()
                if (configContent != null) {
                    val config = json.decodeFromString<RunnerConfigFile>(configContent)
                    logger.d(TAG, "Loaded configuration from source: ${source.name}")
                    return config
                }
            } catch (e: Exception) {
                logger.w(TAG, "Failed to load from source ${source.name}: ${e.message}")
            }
        }
        return null
    }
    
    private fun validateConfiguration(config: RunnerConfigFile): ValidationResult {
        try {
            val errors = mutableListOf<String>()
            
            // Version validation
            if (config.version !in listOf("1.0", "2.0")) {
                errors.add("Unsupported configuration version: ${config.version}")
            }
            
            // Format-specific validation
            if (config.isV2Format()) {
                validateV2Configuration(config, errors)
            } else {
                validateV1Configuration(config, errors)
            }
            
            return if (errors.isEmpty()) {
                ValidationResult.Success
            } else {
                ValidationResult.Failure(IllegalArgumentException("Configuration validation failed: ${errors.joinToString("; ")}"))
            }
            
        } catch (e: Exception) {
            return ValidationResult.Failure(e)
        }
    }
    
    private fun validateV1Configuration(config: RunnerConfigFile, errors: MutableList<String>) {
        val runners = config.runners
        if (runners.isNullOrEmpty()) {
            errors.add("No runners defined in v1.0 configuration")
            return
        }
        
        val runnerNames = mutableSetOf<String>()
        runners.forEach { runner ->
            if (runner.name in runnerNames) {
                errors.add("Duplicate runner name: ${runner.name}")
            }
            runnerNames.add(runner.name)
            
            if (runner.className.isBlank()) {
                errors.add("Runner ${runner.name} has blank class name")
            }
            
            if (runner.capabilities.isEmpty()) {
                errors.add("Runner ${runner.name} has no capabilities")
            }
        }
    }
    
    private fun validateV2Configuration(config: RunnerConfigFile, errors: MutableList<String>) {
        val capabilities = config.capabilities
        if (capabilities.isNullOrEmpty()) {
            errors.add("No capabilities defined in v2.0 configuration")
            return
        }
        
        capabilities.forEach { (capabilityName, capabilityConfig) ->
            if (capabilityConfig.runners.isEmpty()) {
                errors.add("Capability $capabilityName has no runners")
            }
            
            capabilityConfig.runners.forEach { (runnerName, runnerConfig) ->
                if (runnerConfig.className.isBlank()) {
                    errors.add("Runner $runnerName in capability $capabilityName has blank class name")
                }
            }
        }
    }
    
    private suspend fun registerRunnersWithTracking(
        config: RunnerConfigFile, 
        registry: RunnerRegistry
    ): Int {
        val runnerDefinitions = config.getAllRunnerDefinitions()
        var registeredCount = 0
        
        runnerDefinitions.forEach { definition ->
            try {
                if (registerRunnerFromDefinition(definition, registry)) {
                    // Track registration
                    registeredRunners[definition.name] = RunnerRegistrationInfo(
                        name = definition.name,
                        className = definition.className,
                        capabilities = definition.capabilities.mapNotNull { 
                            try { CapabilityType.valueOf(it) } catch (e: Exception) { null }
                        },
                        priority = definition.priority,
                        isReal = definition.isReal,
                        enabled = definition.enabled,
                        registeredAt = System.currentTimeMillis()
                    )
                    registeredCount++
                }
            } catch (e: Exception) {
                logger.e(TAG, "Failed to register runner '${definition.name}': ${e.message}", e)
            }
        }
        
        return registeredCount
    }
    
    private fun registerRunnerFromDefinition(definition: RunnerDefinition, registry: RunnerRegistry): Boolean {
        // Skip disabled runners
        if (!definition.enabled) {
            logger.d(TAG, "Skipping disabled runner: ${definition.name}")
            return false
        }
        
        val runnerClass = try {
            Class.forName(definition.className)
        } catch (e: ClassNotFoundException) {
            logger.e(TAG, "Runner class not found: ${definition.className}")
            return false
        }

        // For "real" runners, check if the device supports them before registering
        if (definition.isReal) {
            try {
                val isSupportedMethod = runnerClass.getMethod("isSupported")
                val isSupported = isSupportedMethod.invoke(null) as? Boolean ?: false
                if (!isSupported) {
                    logger.d(TAG, "Skipping unsupported real runner: ${definition.name}")
                    return false
                }
            } catch (e: NoSuchMethodException) {
                logger.w(TAG, "Runner '${definition.name}' is marked as real but has no static isSupported() method. Skipping.")
                return false
            }
        }

        // Use the smart factory to create runners
        val factory = {
            runnerFactory.createRunner(definition)
                ?: throw IllegalStateException("Failed to create runner: ${definition.name}")
        }

        // Convert capability strings to Enum types
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
            return false
        }

        val registration = RunnerRegistry.RunnerRegistration(
            name = definition.name,
            factory = factory,
            capabilities = capabilities,
            priority = definition.priority,
        )
        
        // Use the new registerWithDefinition method for smart selection
        registry.registerWithDefinition(registration, definition)
        return true
    }
    
    private fun initializeSmartSelection(configFile: RunnerConfigFile, registry: RunnerRegistry) {
        try {
            // Set selection strategy
            val strategyName = configFile.defaultStrategy ?: "MOCK_FIRST"
            val strategy = RunnerSelectionStrategy.fromString(strategyName)
            registry.setSelectionStrategy(strategy)
            logger.d(TAG, "Initialized selection strategy: $strategyName")
            
            // Initialize hardware capabilities detection
            val hardwareCapabilities = detectHardwareCapabilities()
            registry.updateHardwareCapabilities(hardwareCapabilities)
            logger.d(TAG, "Initialized hardware capabilities: $hardwareCapabilities")
            
        } catch (e: Exception) {
            logger.e(TAG, "Failed to initialize smart selection: ${e.message}", e)
        }
    }
    
    private fun detectHardwareCapabilities(): Set<String> {
        val capabilities = mutableSetOf<String>()
        
        // Check for MTK NPU
        try {
            val hardwareCompatibility = Class.forName("com.mtkresearch.breezeapp.engine.system.HardwareCompatibility")
            val hasMTKNPUMethod = hardwareCompatibility.getMethod("hasMTKNPU")
            val hasMTKNPU = hasMTKNPUMethod.invoke(null) as? Boolean ?: false
            if (hasMTKNPU) {
                capabilities.add("MTK_NPU")
            }
        } catch (e: Exception) {
            logger.w(TAG, "Could not check MTK NPU availability: ${e.message}")
        }
        
        // Check CPU capabilities
        capabilities.add("CPU") // Always available
        
        // Check for internet connection (simplified)
        capabilities.add("INTERNET_CONNECTION") // Assume available
        
        // Check available RAM
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024) // MB
        when {
            maxMemory >= 8192 -> capabilities.add("HIGH_MEMORY")
            maxMemory >= 4096 -> capabilities.add("MEDIUM_MEMORY")
            else -> capabilities.add("LOW_MEMORY")
        }
        
        return capabilities
    }
    
    private fun startConfigurationMonitoring() {
        scope.launch {
            // Monitor configuration state changes
            configurationState.collect { state ->
                logger.d(TAG, "Configuration state changed: $state")
            }
        }
    }
    
    fun shutdown() {
        scope.cancel()
    }
    
    // === Data Classes ===
    
    sealed class ConfigurationState {
        object Loading : ConfigurationState()
        object Validating : ConfigurationState()
        object Updating : ConfigurationState()
        data class Ready(val runnerCount: Int) : ConfigurationState()
        data class Error(val exception: Throwable) : ConfigurationState()
    }
    
    data class ConfigurationSummary(
        val version: String,
        val totalRunners: Int,
        val enabledRunners: Int,
        val capabilities: List<CapabilityType>,
        val lastUpdated: Long
    )
    
    data class RunnerRegistrationInfo(
        val name: String,
        val className: String,
        val capabilities: List<CapabilityType>,
        var priority: Int,
        val isReal: Boolean,
        var enabled: Boolean,
        val registeredAt: Long
    )
    
    sealed class ValidationResult {
        object Success : ValidationResult()
        data class Failure(val error: Throwable) : ValidationResult()
        
        val isSuccess: Boolean get() = this is Success
    }
    
    // === Configuration Sources ===
    
    interface ConfigurationSource {
        val name: String
        suspend fun loadConfiguration(): String?
    }
    
    class AssetConfigurationSource(
        private val context: Context,
        private val fileName: String
    ) : ConfigurationSource {
        override val name: String = "Asset:$fileName"
        
        override suspend fun loadConfiguration(): String? {
            return try {
                context.assets.open(fileName).bufferedReader().use { it.readText() }
            } catch (e: IOException) {
                null
            }
        }
    }
    
    class RemoteConfigurationSource(
        private val url: String
    ) : ConfigurationSource {
        override val name: String = "Remote:$url"
        
        override suspend fun loadConfiguration(): String? {
            // Implementation for remote configuration loading
            // Could use HTTP client, etc.
            return null // Placeholder
        }
    }
    
    class PreferencesConfigurationSource(
        private val context: Context,
        private val key: String
    ) : ConfigurationSource {
        override val name: String = "Preferences:$key"
        
        override suspend fun loadConfiguration(): String? {
            return try {
                val prefs = context.getSharedPreferences("runner_config", Context.MODE_PRIVATE)
                prefs.getString(key, null)
            } catch (e: Exception) {
                null
            }
        }
    }
}