package com.mtkresearch.breezeapp.engine.runner.core

import com.mtkresearch.breezeapp.engine.model.CapabilityType
import com.mtkresearch.breezeapp.engine.model.EngineSettings
import com.mtkresearch.breezeapp.engine.model.InferenceRequest
import com.mtkresearch.breezeapp.engine.model.InferenceResult

/**
 * Core interface for all AI Runner implementations.
 * 
 * This interface defines the contract that all AI runners must implement to integrate
 * with the BreezeApp Engine system. It follows Clean Architecture principles by being
 * defined in the Domain layer while implementations reside in the Data layer.
 * 
 * ## Implementation Guidelines
 * - Runners should be stateless where possible
 * - Load/unload operations should be idempotent
 * - All methods should be thread-safe
 * - Errors should be communicated via InferenceResult.error()
 * 
 * ## Lifecycle
 * 1. **Creation**: Runner is instantiated via reflection
 * 2. **Loading**: [load] is called with model configuration
 * 3. **Processing**: [run] is called for each inference request
 * 4. **Cleanup**: [unload] is called when runner is no longer needed
 * 
 * ## Example Implementation
 * ```kotlin
 * class MyCustomRunner : BaseRunner {
 *     private var isModelLoaded = false
 *     
 *     override fun load(modelId: String, settings: EngineSettings): Boolean {
 *         // Load your AI model using model ID and settings
 *         isModelLoaded = loadModelFromRegistry(modelId)
 *         return isModelLoaded
 *     }
 *     
 *     override fun run(input: InferenceRequest): InferenceResult {
 *         if (!isModelLoaded) return InferenceResult.error("Model not loaded")
 *         // Process the request and return results
 *         return InferenceResult.success(mapOf("text" to processText(input)))
 *     }
 *     
 *     override fun getCapabilities() = listOf(CapabilityType.LLM)
 *     // ... implement other methods
 * }
 * ```
 * 
 * @see InferenceRequest for input format
 * @see InferenceResult for output format
 * @see CapabilityType for supported capabilities
 * @see FlowStreamingRunner for streaming support
 */
interface BaseRunner {
    
    /**
     * Initializes the AI model and allocates necessary resources using model ID.
     *
     * This method loads the AI model from the ModelRegistryService using the provided
     * model ID and prepares it for inference. The implementation should be idempotent -
     * calling load multiple times should not cause issues.
     *
     * @param modelId The model identifier from fullModelList.json
     * @param settings The complete engine settings for runner-specific configurations
     * @return true if the model was successfully loaded, false otherwise
     *
     * @see ModelRegistryService for model resolution
     * @see EngineSettings for runner configuration
     * @see unload to release resources
     * @since Engine API v2.2
     */
    fun load(modelId: String, settings: EngineSettings, initialParams: Map<String, Any> = emptyMap()): Boolean
    
    /**
     * Executes AI inference on the provided input.
     * 
     * This is the core method that processes inference requests. The implementation
     * should handle the input data according to its capabilities and return
     * appropriate results or errors.
     * 
     * @param input The inference request containing input data and parameters
     * @param stream Whether to process in streaming mode (deprecated - use FlowStreamingRunner)
     * @return Inference result containing output data or error information
     * 
     * @see InferenceRequest for input format
     * @see InferenceResult for output format
     * @see FlowStreamingRunner for proper streaming support
     */
    fun run(input: InferenceRequest, stream: Boolean = false): InferenceResult
    
    /**
     * Releases all loaded models and allocated resources.
     * 
     * This method should clean up all resources used by the runner, including
     * model memory, GPU resources, and any temporary files. The implementation
     * should be idempotent and safe to call multiple times.
     * 
     * After calling unload(), the runner should not be used for inference
     * until load() is called again successfully.
     */
    fun unload()
    
    /**
     * Returns the list of AI capabilities supported by this runner.
     * 
     * This method defines what types of AI operations this runner can perform.
     * The engine uses this information for request routing and capability discovery.
     * 
     * @return List of supported capability types (LLM, TTS, ASR, VLM, etc.)
     * @see CapabilityType for available capability types
     */
    fun getCapabilities(): List<CapabilityType>
    
    /**
     * Checks whether the AI model is currently loaded and ready for inference.
     *
     * This method should return true only if the model is fully loaded and
     * ready to process requests. It's used by the engine to determine if
     * load() needs to be called before processing requests.
     *
     * @return true if the model is loaded and ready, false otherwise
     */
    fun isLoaded(): Boolean

    /**
     * Returns the ID of the currently loaded model.
     *
     * This method allows the engine to detect when a different model needs to be loaded,
     * enabling proper model change handling (unload old model, load new model).
     *
     * ## Model Change Detection Example
     * ```kotlin
     * val currentModel = runner.getLoadedModelId()
     * val targetModel = settings.getRunnerParameters(runnerName)["model"]
     * if (currentModel != targetModel) {
     *     runner.unload()
     *     runner.load(targetModel, settings)
     * }
     * ```
     *
     * @return The model ID of the currently loaded model, or empty string if no model is loaded
     * @since Engine API v2.3
     */
    fun getLoadedModelId(): String = ""
    
    /**
     * Returns metadata information about this runner.
     * 
     * Provides descriptive information about the runner including its name,
     * version, capabilities, and other metadata useful for debugging and
     * system introspection.
     * 
     * @return Runner information containing name, version, and capabilities
     * @see RunnerInfo for metadata format
     */
    fun getRunnerInfo(): RunnerInfo

    /**
     * Checks if this runner is supported on the current hardware.
     * 
     * This method should validate hardware requirements such as:
     * - Required hardware availability (NPU, GPU, etc.)
     * - Native library availability  
     * - System API compatibility
     * - Memory requirements
     * 
     * @return true if runner can operate on current device, false otherwise
     */
    fun isSupported(): Boolean

    /**
     * Returns the parameter schema for this runner's configuration.
     * 
     * This method enables self-describing runners that declare their own
     * configuration parameters dynamically. The UI can use this schema to
     * automatically generate parameter forms without hardcoded knowledge
     * of each runner's specific requirements.
     * 
     * ## Benefits
     * - **Zero UI Development**: Parameters automatically appear in settings
     * - **Type Safety**: Parameter validation enforced by schema
     * - **Maintainability**: Parameter changes only require runner updates
     * - **Extensibility**: New runners automatically get UI support
     * 
     * ## Example Implementation
     * ```kotlin
     * override fun getParameterSchema(): List<ParameterSchema> {
     *     return listOf(
     *         ParameterSchema(
     *             name = "api_key",
     *             displayName = "API Key",
     *             description = "Your API key for authentication",
     *             type = ParameterType.StringType(minLength = 10),
     *             defaultValue = "",
     *             isRequired = true,
     *             isSensitive = true,
     *             category = "Authentication"
     *         ),
     *         ParameterSchema(
     *             name = "temperature",
     *             displayName = "Temperature",
     *             description = "Controls randomness in responses",
     *             type = ParameterType.FloatType(minValue = 0.0, maxValue = 2.0),
     *             defaultValue = 0.7f,
     *             category = "Generation"
     *         )
     *     )
     * }
     * ```
     * 
     * @return List of parameter schemas, or empty list if no additional parameters needed
     * @see ParameterSchema for parameter definition format
     * @see ParameterType for available parameter types
     * @since Engine API v2.1
     */
    fun getParameterSchema(): List<ParameterSchema> = emptyList()

    /**
     * Validates user-provided parameters against this runner's requirements.
     * 
     * This method allows runners to perform custom validation logic beyond
     * the basic type checking provided by ParameterSchema. Runners can
     * validate parameter combinations, check external dependencies, or
     * perform any other validation logic specific to their implementation.
     * 
     * ## Validation Process
     * 1. UI validates individual parameters using ParameterSchema
     * 2. This method validates the complete parameter set
     * 3. Parameters are stored only if both validations pass
     * 
     * ## Example Implementation
     * ```kotlin
     * override fun validateParameters(parameters: Map<String, Any>): ValidationResult {
     *     val apiKey = parameters["api_key"] as? String
     *     if (apiKey?.startsWith("sk-") != true) {
     *         return ValidationResult.invalid("API key must start with 'sk-'")
     *     }
     *     
     *     val temperature = parameters["temperature"] as? Float ?: 0.7f
     *     val topP = parameters["top_p"] as? Float ?: 0.9f
     *     if (temperature > 1.0f && topP > 0.95f) {
     *         return ValidationResult.invalid("High temperature and high top_p may produce incoherent results")
     *     }
     *     
     *     return ValidationResult.valid()
     * }
     * ```
     * 
     * @param parameters Map of parameter names to values to validate
     * @return ValidationResult indicating success or failure with error message
     * @see ValidationResult for result format
     * @see getParameterSchema for parameter definitions
     * @since Engine API v2.1
     */
    fun validateParameters(parameters: Map<String, Any>): ValidationResult = ValidationResult.valid()

}

/**
 * Runner 資訊資料類別
 */
data class RunnerInfo(
    val name: String,
    val version: String,
    val capabilities: List<CapabilityType>,
    val description: String = ""
) 