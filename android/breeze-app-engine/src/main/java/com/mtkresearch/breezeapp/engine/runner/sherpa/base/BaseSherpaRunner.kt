package com.mtkresearch.breezeapp.engine.runner.sherpa.base

import android.content.Context
import android.util.Log
import com.mtkresearch.breezeapp.engine.runner.core.BaseRunner
import com.mtkresearch.breezeapp.engine.runner.core.RunnerInfo
import com.mtkresearch.breezeapp.engine.model.CapabilityType
import com.mtkresearch.breezeapp.engine.model.EngineSettings
import com.mtkresearch.breezeapp.engine.model.InferenceRequest
import com.mtkresearch.breezeapp.engine.model.InferenceResult
import com.mtkresearch.breezeapp.engine.model.RunnerError
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Base class for all Sherpa ONNX runners
 * 
 * This abstract class provides common functionality for all Sherpa-based runners,
 * including model loading state management, logging, and base runner info.
 * 
 * Following Clean Architecture principles:
 * - Separates core logic from implementation details
 * - Provides a consistent interface for all Sherpa runners
 * - Handles common error cases and logging
 */
abstract class BaseSherpaRunner(protected val context: Context) : BaseRunner {
    protected val isLoaded = AtomicBoolean(false)
    protected var modelName: String = ""
    
    companion object {
        private const val TAG = "BaseSherpaRunner"
    }
    
    /**
     * Initializes the Sherpa model with the provided model ID
     * 
     * This method should be implemented by subclasses to load their specific models
     * 
     * @param modelId Model identifier from fullModelList.json
     * @param settings Engine settings containing runner-specific parameters
     * @return true if the model was successfully loaded, false otherwise
     */
    // Removed abstract initializeModel - subclasses now implement load directly
    
    /**
     * Releases all loaded models and allocated resources
     * 
     * This method should be implemented by subclasses to clean up their specific resources
     */
    protected abstract fun releaseModel()
    
    /**
     * Returns the list of AI capabilities supported by this runner
     * 
     * @return List of supported capability types
     */
    abstract override fun getCapabilities(): List<CapabilityType>
    
    /**
     * Returns metadata information about this runner
     * 
     * @return Runner information containing name, version, and capabilities
     */
    abstract override fun getRunnerInfo(): RunnerInfo
    
    override fun isSupported(): Boolean {
        // Sherpa runners are generally supported on most devices
        // Subclasses can override for specific hardware requirements
        return true
    }
    
    // Removed default load implementation - subclasses now implement load directly with modelId
    
    override fun unload() {
        try {
            Log.d(getTag(), "Unloading ${getRunnerInfo().name}")
            releaseModel()
            isLoaded.set(false)
            Log.d(getTag(), "${getRunnerInfo().name} unloaded successfully")
        } catch (e: Exception) {
            Log.e(getTag(), "Exception while unloading ${getRunnerInfo().name}", e)
        }
    }
    
    override fun isLoaded(): Boolean = isLoaded.get()

    override fun getLoadedModelId(): String = modelName

    /**
     * Get the tag for logging
     *
     * @return The tag to use for logging messages
     */
    protected open fun getTag(): String = this::class.java.simpleName
    
    /**
     * Validate that the model is loaded before processing
     * 
     * @return InferenceResult.error if model is not loaded, null if model is loaded
     */
    protected fun validateModelLoaded(): InferenceResult? {
        return if (!isLoaded.get()) {
            InferenceResult.error(RunnerError.modelNotLoaded())
        } else {
            null
        }
    }
    
    /**
     * Validate input data for processing
     * 
     * @param input The inference request containing input data and parameters
     * @param requiredInputKey The key for the required input data
     * @return Pair of (inputData, null) if valid, or (null, errorResult) if invalid
     */
    protected inline fun <reified T> validateInput(
        input: InferenceRequest,
        requiredInputKey: String
    ): Pair<T?, InferenceResult?> {
        try {
            val inputData = input.inputs[requiredInputKey] as? T
            return if (inputData != null) {
                inputData to null
            } else {
                null to InferenceResult.error(
                    RunnerError.invalidInput("Required input '$requiredInputKey' of type ${T::class.java.simpleName} not provided")
                )
            }
        } catch (e: Exception) {
            return null to InferenceResult.error(
                RunnerError.invalidInput("Error parsing input '$requiredInputKey': ${e.message}")
            )
        }
    }
}