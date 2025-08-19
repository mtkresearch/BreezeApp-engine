package com.mtkresearch.breezeapp.engine.model

/**
 * Model definition loaded from fullModelList.json
 * Represents complete model metadata including files, capabilities, and requirements
 */
data class ModelDefinition(
    val id: String,
    val runner: String,
    val backend: String,
    val ramGB: Int,
    val files: List<ModelFile>,
    val entryPoint: EntryPoint,
    val capabilities: List<CapabilityType> = emptyList()
) {
    /**
     * Get file by type (model, tokenizer, config, etc.)
     */
    fun getFileByType(type: String): ModelFile? = files.find { it.type == type }
    
    /**
     * Get all files of specified type
     */
    fun getFilesByType(type: String): List<ModelFile> = files.filter { it.type == type }
    
    /**
     * Check if model supports capability
     */
    fun hasCapability(capability: CapabilityType): Boolean = capabilities.contains(capability)
    
    /**
     * Get model directory path for local storage
     */
    fun getModelDirectory(baseDir: String): String = "$baseDir/models/$id"
}


/**
 * Entry point definition for model loading
 */
data class EntryPoint(
    val type: String,
    val value: String
)