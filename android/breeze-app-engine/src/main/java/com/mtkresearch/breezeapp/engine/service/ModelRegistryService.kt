package com.mtkresearch.breezeapp.engine.service

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.mtkresearch.breezeapp.engine.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Service for managing model definitions from fullModelList.json
 * Loads and caches model definitions from assets/fullModelList.json
 */
class ModelRegistryService(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "ModelRegistryService"
        private const val MODEL_LIST_FILE = "fullModelList.json"
    }
    
    private var modelDefinitions: List<ModelDefinition> = emptyList()
    private var lastLoadTime: Long = 0
    
    init {
        refresh()
    }
    
    /**
     * Get all available models for specific capability
     */
    fun getAvailableModels(capability: CapabilityType): List<ModelDefinition> {
        return modelDefinitions.filter { it.hasCapability(capability) }
    }
    
    /**
     * Get model definition by ID
     */
    fun getModelDefinition(modelId: String): ModelDefinition? {
        return modelDefinitions.find { it.id == modelId }
    }
    
    /**
     * Get models compatible with specific runner
     */
    fun getCompatibleModels(runnerId: String): List<ModelDefinition> {
        return modelDefinitions.filter { it.runner.equals(runnerId, ignoreCase = true) }
    }
    
    /**
     * Get all available models
     */
    fun getAllModels(): List<ModelDefinition> {
        return modelDefinitions
    }
    
    /**
     * Refresh model registry from JSON
     */
    fun refresh(): Boolean {
        return try {
            val jsonString = loadJsonFromAssets()
            val models = parseModelDefinitions(jsonString)
            modelDefinitions = models
            lastLoadTime = System.currentTimeMillis()
            Log.d(TAG, "Loaded ${models.size} model definitions")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model definitions", e)
            false
        }
    }
    
    private fun loadJsonFromAssets(): String {
        return try {
            context.assets.open(MODEL_LIST_FILE).use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: IOException) {
            throw IllegalStateException("Failed to load $MODEL_LIST_FILE from assets", e)
        }
    }
    
    private fun parseModelDefinitions(jsonString: String): List<ModelDefinition> {
        val jsonObject = JSONObject(jsonString)
        val modelsArray = jsonObject.getJSONArray("models")
        val models = mutableListOf<ModelDefinition>()
        
        for (i in 0 until modelsArray.length()) {
            val modelJson = modelsArray.getJSONObject(i)
            try {
                val model = parseModelDefinition(modelJson)
                models.add(model)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse model definition at index $i", e)
            }
        }
        
        return models
    }
    
    private fun parseModelDefinition(json: JSONObject): ModelDefinition {
        val id = json.getString("id")
        val runner = json.getString("runner")
        val backend = json.getString("backend")
        val ramGB = json.getInt("ramGB")
        
        // Parse files
        val filesArray = json.getJSONArray("files")
        val files = parseModelFiles(filesArray)
        
        // Parse entry point
        val entryPointJson = json.getJSONObject("entry_point")
        val entryPoint = EntryPoint(
            type = entryPointJson.getString("type"),
            value = entryPointJson.getString("value")
        )
        
        // Parse capabilities (optional, infer from runner if not present)
        val capabilities = if (json.has("capabilities")) {
            parseCapabilities(json.getJSONArray("capabilities"))
        } else {
            inferCapabilitiesFromRunner(runner)
        }
        
        return ModelDefinition(
            id = id,
            runner = runner,
            backend = backend,
            ramGB = ramGB,
            files = files,
            entryPoint = entryPoint,
            capabilities = capabilities
        )
    }
    
    private fun parseModelFiles(filesArray: JSONArray): List<ModelFile> {
        val files = mutableListOf<ModelFile>()
        
        for (i in 0 until filesArray.length()) {
            val fileJson = filesArray.getJSONObject(i)
            
            val urls = mutableListOf<String>()
            val urlsArray = fileJson.getJSONArray("urls")
            for (j in 0 until urlsArray.length()) {
                urls.add(urlsArray.getString(j))
            }
            
            val file = ModelFile(
                fileName = fileJson.optString("fileName").takeIf { it.isNotEmpty() },
                group = fileJson.optString("group").takeIf { it.isNotEmpty() },
                pattern = fileJson.optString("pattern").takeIf { it.isNotEmpty() },
                type = fileJson.getString("type"),
                urls = urls
            )
            
            files.add(file)
        }
        
        return files
    }
    
    private fun parseCapabilities(capabilitiesArray: JSONArray): List<CapabilityType> {
        val capabilities = mutableListOf<CapabilityType>()
        
        for (i in 0 until capabilitiesArray.length()) {
            val capabilityString = capabilitiesArray.getString(i)
            try {
                val capability = CapabilityType.valueOf(capabilityString.uppercase())
                capabilities.add(capability)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Unknown capability type: $capabilityString")
            }
        }
        
        return capabilities
    }
    
    private fun inferCapabilitiesFromRunner(runner: String): List<CapabilityType> {
        return when {
            runner.contains("llm", ignoreCase = true) -> listOf(CapabilityType.LLM)
            runner.contains("asr", ignoreCase = true) -> listOf(CapabilityType.ASR)
            runner.contains("tts", ignoreCase = true) -> listOf(CapabilityType.TTS)
            runner.contains("vlm", ignoreCase = true) -> listOf(CapabilityType.VLM)
            runner.equals("executorch", ignoreCase = true) -> listOf(CapabilityType.LLM)
            runner.equals("mediatek", ignoreCase = true) -> listOf(CapabilityType.LLM)
            else -> emptyList()
        }
    }
    
    /**
     * Check if a runner is cloud-based by its ID
     */
    private fun isCloudRunner(runnerId: String): Boolean {
        // Simple heuristic: check for "cloud" in the runner ID
        // This can be expanded with a more robust mechanism if needed
        return runnerId.contains("cloud", ignoreCase = true)
    }
}