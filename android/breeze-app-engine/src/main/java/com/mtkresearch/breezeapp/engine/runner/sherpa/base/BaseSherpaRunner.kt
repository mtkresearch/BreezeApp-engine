package com.mtkresearch.breezeapp.engine.runner.sherpa.base

import android.content.Context
import android.util.Log
import com.mtkresearch.breezeapp.engine.runner.core.BaseRunner
import com.mtkresearch.breezeapp.engine.runner.core.BaseRunnerCompanion
import com.mtkresearch.breezeapp.engine.runner.core.RunnerInfo
import com.mtkresearch.breezeapp.engine.model.*
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
    
    companion object : BaseRunnerCompanion {
        private const val TAG = "BaseSherpaRunner"
        
        @JvmStatic
        override fun isSupported(): Boolean = true
    }
    
    /**
     * Initializes the Sherpa model with the provided configuration
     * 
     * This method should be implemented by subclasses to load their specific models
     * 
     * @param config Model configuration containing paths, parameters, and metadata
     * @return true if the model was successfully loaded, false otherwise
     */
    protected abstract fun initializeModel(config: ModelConfig): Boolean
    
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
    
    override fun load(config: ModelConfig): Boolean {
        return try {
            Log.d(getTag(), "Loading ${getRunnerInfo().name} with config: ${config.modelName}")
            modelName = config.modelName
            
            val result = initializeModel(config)
            isLoaded.set(result)
            
            if (result) {
                Log.i(getTag(), "${getRunnerInfo().name} loaded successfully")
            } else {
                Log.e(getTag(), "Failed to load ${getRunnerInfo().name}")
            }
            
            result
        } catch (e: Exception) {
            Log.e(getTag(), "Exception while loading ${getRunnerInfo().name}", e)
            isLoaded.set(false)
            false
        }
    }
    
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