package com.mtkresearch.breezeapp.engine.data.runner.core

import android.content.Context
import com.mtkresearch.breezeapp.engine.config.RunnerDefinition
import com.mtkresearch.breezeapp.engine.config.MTKConfig
import com.mtkresearch.breezeapp.engine.data.runner.mtk.MTKLLMRunner
import com.mtkresearch.breezeapp.engine.core.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * Smart factory for creating runner instances with vendor-specific logic.
 * 
 * This factory handles the complexity of creating different types of runners:
 * - Mock runners: Simple instantiation
 * - MTK runners: Require hardware detection and model configuration
 * - OpenAI runners: Require API keys and network configuration (future)
 * - HuggingFace runners: Require model downloads and caching (future)
 * 
 * The factory uses caching to avoid recreating expensive runner instances.
 */
class RunnerFactory(
    private val context: Context,
    private val logger: Logger
) {
    companion object {
        private const val TAG = "RunnerFactory"
    }
    
    private val runnerCache = ConcurrentHashMap<String, BaseRunner>()
    
    /**
     * Create a runner instance from its definition.
     * 
     * @param definition The runner definition containing class name and configuration
     * @return The created runner instance, or null if creation failed
     */
    fun createRunner(definition: RunnerDefinition): BaseRunner? {
        return runnerCache.getOrPut(definition.name) {
            try {
                logger.d(TAG, "Creating runner: ${definition.name} (${definition.className})")
                
                when {
                    definition.className.contains(".mock.") -> {
                        createMockRunner(definition)
                    }
                    definition.className.contains(".mtk.") -> {
                        createMTKRunner(definition)
                    }
                    definition.className.contains(".openai.") -> {
                        createOpenAIRunner(definition)
                    }
                    definition.className.contains(".huggingface.") -> {
                        createHuggingFaceRunner(definition)
                    }
                    else -> {
                        createGenericRunner(definition)
                    }
                } ?: throw RuntimeException("Failed to create runner: ${definition.name}")
                
            } catch (e: Exception) {
                logger.e(TAG, "Failed to create runner '${definition.name}': ${e.message}", e)
                null
            }
        }
    }
    
    /**
     * Create a mock runner - simple instantiation with no special requirements.
     */
    private fun createMockRunner(definition: RunnerDefinition): BaseRunner? {
        logger.d(TAG, "Creating mock runner: ${definition.name}")
        
        return try {
            val clazz = Class.forName(definition.className)
            clazz.getConstructor().newInstance() as BaseRunner
        } catch (e: Exception) {
            logger.e(TAG, "Failed to create mock runner: ${definition.name}", e)
            null
        }
    }
    
    /**
     * Create an MTK runner - requires hardware detection and model configuration.
     */
    private fun createMTKRunner(definition: RunnerDefinition): BaseRunner? {
        logger.d(TAG, "Creating MTK runner: ${definition.name}")
        
        return try {
            when (definition.className) {
                "com.mtkresearch.breezeapp.engine.data.runner.mtk.MTKLLMRunner" -> {
                    createMTKLLMRunner(definition)
                }
                // Future MTK runners can be added here
                // "com.mtkresearch.breezeapp.engine.data.runner.mtk.MTKASRRunner" -> { ... }
                // "com.mtkresearch.breezeapp.engine.data.runner.mtk.MTKTTSRunner" -> { ... }
                else -> {
                    logger.w(TAG, "Unknown MTK runner type: ${definition.className}")
                    null
                }
            }
        } catch (e: Exception) {
            logger.e(TAG, "Failed to create MTK runner: ${definition.name}", e)
            null
        }
    }
    
    /**
     * Create MTK LLM Runner with proper configuration.
     */
    private fun createMTKLLMRunner(definition: RunnerDefinition): BaseRunner? {
        val modelId = definition.modelId ?: "Breeze2-3B-8W16A-250630-npu"
        
        // Get model entry point path
        val entryPointPath = getLocalEntryPointPath(context, modelId)
        if (entryPointPath == null) {
            logger.w(TAG, "Model entry point not found for $modelId")
            return null
        }
        
        // Create MTK configuration
        val mtkConfig = MTKConfig.createDefault(entryPointPath)
        
        // Create the runner
        return MTKLLMRunner.create(context, mtkConfig)
    }
    
    /**
     * Create an OpenAI runner - requires API configuration (future implementation).
     */
    private fun createOpenAIRunner(definition: RunnerDefinition): BaseRunner? {
        logger.d(TAG, "Creating OpenAI runner: ${definition.name}")
        
        // Future implementation for OpenAI runners
        // This would handle:
        // - API key configuration
        // - Network connectivity checks
        // - Rate limiting setup
        // - Model selection
        
        logger.w(TAG, "OpenAI runners not yet implemented")
        return null
        
        /*
        // Future implementation example:
        return try {
            val apiKey = getOpenAIApiKey()
            val clazz = Class.forName(definition.className)
            val constructor = clazz.getConstructor(String::class.java)
            constructor.newInstance(apiKey) as BaseRunner
        } catch (e: Exception) {
            logger.e(TAG, "Failed to create OpenAI runner: ${definition.name}", e)
            null
        }
        */
    }
    
    /**
     * Create a HuggingFace runner - requires model download and caching (future implementation).
     */
    private fun createHuggingFaceRunner(definition: RunnerDefinition): BaseRunner? {
        logger.d(TAG, "Creating HuggingFace runner: ${definition.name}")
        
        // Future implementation for HuggingFace runners
        // This would handle:
        // - Model download from HuggingFace Hub
        // - Local model caching
        // - Tokenizer setup
        // - Hardware optimization
        
        logger.w(TAG, "HuggingFace runners not yet implemented")
        return null
        
        /*
        // Future implementation example:
        return try {
            val modelPath = downloadHuggingFaceModel(definition.modelId)
            val clazz = Class.forName(definition.className)
            val constructor = clazz.getConstructor(String::class.java)
            constructor.newInstance(modelPath) as BaseRunner
        } catch (e: Exception) {
            logger.e(TAG, "Failed to create HuggingFace runner: ${definition.name}", e)
            null
        }
        */
    }
    
    /**
     * Create a generic runner - fallback for unknown runner types.
     */
    private fun createGenericRunner(definition: RunnerDefinition): BaseRunner? {
        logger.d(TAG, "Creating generic runner: ${definition.name}")
        
        return try {
            val clazz = Class.forName(definition.className)
            clazz.getConstructor().newInstance() as BaseRunner
        } catch (e: Exception) {
            logger.e(TAG, "Failed to create generic runner: ${definition.name}", e)
            null
        }
    }
    
    /**
     * Get the local entry point path for a model.
     * This is used by MTK runners to locate downloaded models.
     */
    private fun getLocalEntryPointPath(context: Context, modelId: String): String? {
        return try {
            val file = java.io.File(context.filesDir, "downloadedModelList.json")
            if (!file.exists()) return null
            
            val json = file.readText()
            val modelList = org.json.JSONObject(json).getJSONArray("models")
            
            for (i in 0 until modelList.length()) {
                val model = modelList.getJSONObject(i)
                if (model.getString("id") == modelId) {
                    val entryPoint = model.getString("entryPointValue")
                    return java.io.File(context.filesDir, "models/$modelId/$entryPoint").absolutePath
                }
            }
            null
        } catch (e: Exception) {
            logger.e(TAG, "Failed to get entry point path for model: $modelId", e)
            null
        }
    }
    
    /**
     * Clear the runner cache. Useful for testing or when configuration changes.
     */
    fun clearCache() {
        logger.d(TAG, "Clearing runner cache")
        runnerCache.clear()
    }
    
    /**
     * Get cache statistics for debugging.
     */
    fun getCacheStats(): Map<String, Any> {
        return mapOf(
            "cacheSize" to runnerCache.size,
            "cachedRunners" to runnerCache.keys.toList()
        )
    }
}