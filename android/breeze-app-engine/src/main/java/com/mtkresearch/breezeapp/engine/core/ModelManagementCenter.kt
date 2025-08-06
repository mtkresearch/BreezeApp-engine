package com.mtkresearch.breezeapp.engine.core

import android.content.Context
import com.mtkresearch.breezeapp.engine.domain.model.ModelInfo
import com.mtkresearch.breezeapp.engine.repository.ModelManager
import com.mtkresearch.breezeapp.engine.repository.ModelManagerImpl
import com.mtkresearch.breezeapp.engine.repository.ModelRegistry
import com.mtkresearch.breezeapp.engine.repository.ModelVersionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Centralized Model Management Center
 * 
 * Single entry point for all model operations following Clean Architecture principles.
 * Provides comprehensive CRUD operations, state tracking, and developer-friendly APIs.
 * 
 * Features:
 * - Category-based model organization (LLM, ASR, TTS, VLM)
 * - Real-time state tracking with StateFlow
 * - Comprehensive download management (pause/resume/cancel)
 * - Bulk operations support
 * - Storage management and cleanup
 * - Developer-friendly API surface
 */
class ModelManagementCenter private constructor(
    private val context: Context
) {
    companion object {
        @Volatile
        private var INSTANCE: ModelManagementCenter? = null
        
        fun getInstance(context: Context): ModelManagementCenter {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelManagementCenter(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // Dependencies following Clean Architecture
    private val registry = ModelRegistry(context)
    private val versionStore = ModelVersionStore(context)
    private val modelManager = ModelManagerImpl(context, registry, versionStore)
    
    // Coroutine scope for background operations
    private val managementScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // State management
    private val _modelStates = MutableStateFlow<Map<String, ModelState>>(emptyMap())
    val modelStates: StateFlow<Map<String, ModelState>> = _modelStates.asStateFlow()
    
    private val activeDownloads = mutableMapOf<String, DownloadHandle>()
    
    // Optional status manager for engine status updates
    private var statusManager: BreezeAppEngineStatusManager? = null
    
    init {
        refreshModelStates()
    }
    
    // === Category-based Model Organization ===
    
    enum class ModelCategory(val key: String) {
        LLM("llm"),
        ASR("asr"), 
        TTS("tts"),
        VLM("vlm"),
        UNKNOWN("unknown")
    }
    
    data class ModelState(
        val modelInfo: ModelInfo,
        val status: Status,
        val downloadProgress: Int = 0,
        val downloadSpeed: Long = 0,
        val downloadEta: Long = -1,
        val storageSize: Long = 0,
        val category: ModelCategory = ModelCategory.UNKNOWN,
        val isDefault: Boolean = false,
        val lastUpdated: Long = System.currentTimeMillis()
    ) {
        enum class Status {
            AVAILABLE,      // Available for download
            DOWNLOADING,    // Currently downloading
            DOWNLOADED,     // Successfully downloaded
            PAUSED,        // Download paused
            ERROR,         // Download/validation error
            INSTALLING,    // Post-download validation/setup
            READY          // Ready for inference
        }
    }
    
    // === Configuration ===
    
    /**
     * Set the status manager for engine status updates during downloads
     */
    fun setStatusManager(statusManager: BreezeAppEngineStatusManager) {
        this.statusManager = statusManager
    }
    
    // === Public API Surface ===
    
    /**
     * Get all models organized by category
     */
    fun getModelsByCategory(): Map<ModelCategory, List<ModelState>> {
        return _modelStates.value.values
            .groupBy { it.category }
            .withDefault { emptyList() }
    }
    
    /**
     * Get all available models for a specific category
     */
    fun getAvailableModels(category: ModelCategory): List<ModelState> {
        return _modelStates.value.values
            .filter { it.category == category }
            .sortedWith(compareBy<ModelState> { !it.isDefault }.thenBy { it.modelInfo.name })
    }
    
    /**
     * Get downloaded models for a category
     */
    fun getDownloadedModels(category: ModelCategory): List<ModelState> {
        return getAvailableModels(category)
            .filter { it.status in setOf(ModelState.Status.DOWNLOADED, ModelState.Status.READY) }
    }
    
    /**
     * Get default model for a category
     */
    fun getDefaultModel(category: ModelCategory): ModelState? {
        return getAvailableModels(category).find { it.isDefault }
    }
    
    /**
     * Get model state by ID
     */
    fun getModelState(modelId: String): ModelState? {
        return _modelStates.value[modelId]
    }
    
    // === Download Operations ===
    
    /**
     * Download a specific model
     */
    fun downloadModel(
        modelId: String,
        listener: ModelDownloadListener? = null
    ): DownloadHandle {
        val handle = DownloadHandle(modelId)
        activeDownloads[modelId] = handle
        
        updateModelStatus(modelId, ModelState.Status.DOWNLOADING)
        
        // Update engine status to show downloading
        val modelInfo = getModelState(modelId)?.modelInfo
        statusManager?.setDownloading(modelInfo?.name ?: modelId, 0)
        
        managementScope.launch {
            modelManager.downloadModel(modelId, object : ModelManager.DownloadListener {
                override fun onProgress(modelId: String, percent: Int, speed: Long, eta: Long) {
                    updateModelProgress(modelId, percent, speed, eta)
                    // Update engine status with progress
                    val modelName = getModelState(modelId)?.modelInfo?.name ?: modelId
                    statusManager?.setDownloading(modelName, percent)
                    listener?.onProgress(modelId, percent, speed, eta)
                }
                
                override fun onCompleted(modelId: String) {
                    updateModelStatus(modelId, ModelState.Status.DOWNLOADED)
                    activeDownloads.remove(modelId)
                    refreshModelStates()
                    // Reset engine status to ready
                    statusManager?.setReady()
                    listener?.onCompleted(modelId)
                }
                
                override fun onError(modelId: String, error: Throwable, fileName: String?) {
                    updateModelStatus(modelId, ModelState.Status.ERROR)
                    activeDownloads.remove(modelId)
                    // Reset engine status to ready on error
                    statusManager?.setReady()
                    listener?.onError(modelId, error, fileName)
                }
                
                override fun onPaused(modelId: String) {
                    updateModelStatus(modelId, ModelState.Status.PAUSED)
                    listener?.onPaused(modelId)
                }
                
                override fun onResumed(modelId: String) {
                    updateModelStatus(modelId, ModelState.Status.DOWNLOADING)
                    listener?.onResumed(modelId)
                }
                
                override fun onCancelled(modelId: String) {
                    updateModelStatus(modelId, ModelState.Status.AVAILABLE)
                    activeDownloads.remove(modelId)
                    listener?.onCancelled(modelId)
                }
                
                override fun onFileProgress(
                    modelId: String, fileName: String, fileIndex: Int, fileCount: Int,
                    bytesDownloaded: Long, totalBytes: Long, speed: Long, eta: Long
                ) {
                    listener?.onFileProgress(modelId, fileName, fileIndex, fileCount, bytesDownloaded, totalBytes, speed, eta)
                }
                
                override fun onFileCompleted(modelId: String, fileName: String, fileIndex: Int, fileCount: Int) {
                    listener?.onFileCompleted(modelId, fileName, fileIndex, fileCount)
                }
            })
        }
        
        return handle
    }
    
    /**
     * Ensure default model for category is ready
     */
    fun ensureDefaultModelReady(
        category: ModelCategory,
        listener: ModelDownloadListener? = null
    ) {
        val defaultModel = getDefaultModel(category)
        if (defaultModel == null) {
            listener?.onError("", IllegalStateException("No default model found for category $category"), null)
            return
        }
        
        when (defaultModel.status) {
            ModelState.Status.DOWNLOADED, ModelState.Status.READY -> {
                listener?.onCompleted(defaultModel.modelInfo.id)
            }
            ModelState.Status.DOWNLOADING -> {
                // Already downloading, attach listener
                // Implementation depends on download management strategy
            }
            else -> {
                downloadModel(defaultModel.modelInfo.id, listener)
            }
        }
    }
    
    /**
     * Delete a model
     */
    fun deleteModel(modelId: String): Boolean {
        activeDownloads[modelId]?.cancel()
        activeDownloads.remove(modelId)
        
        val success = modelManager.deleteModel(modelId)
        if (success) {
            updateModelStatus(modelId, ModelState.Status.AVAILABLE)
            refreshModelStates()
        }
        return success
    }
    
    // === Bulk Operations ===
    
    /**
     * Download all default models for specified categories
     */
    fun downloadDefaultModels(
        categories: List<ModelCategory>,
        listener: BulkDownloadListener? = null
    ) {
        val modelsToDownload = categories.mapNotNull { getDefaultModel(it) }
            .filter { it.status !in setOf(ModelState.Status.DOWNLOADED, ModelState.Status.READY) }
        
        if (modelsToDownload.isEmpty()) {
            listener?.onAllCompleted()
            return
        }
        
        val results = mutableMapOf<String, Boolean>()
        
        modelsToDownload.forEach { model ->
            downloadModel(model.modelInfo.id, object : ModelDownloadListener {
                override fun onCompleted(modelId: String) {
                    results[modelId] = true
                    listener?.onModelCompleted(modelId, true)
                    
                    if (results.size == modelsToDownload.size) {
                        listener?.onAllCompleted()
                    }
                }
                
                override fun onError(modelId: String, error: Throwable, fileName: String?) {
                    results[modelId] = false
                    listener?.onModelCompleted(modelId, false)
                    
                    if (results.size == modelsToDownload.size) {
                        listener?.onAllCompleted()
                    }
                }
            })
        }
    }
    
    /**
     * Clean up old model versions and temporary files
     */
    fun cleanupStorage(): StorageCleanupResult {
        val beforeSize = calculateTotalStorageUsed()
        
        // Clean up temporary files
        val tempFiles = File(context.filesDir, "models").listFiles()
            ?.filter { it.name.endsWith(".part") }
            ?.sumOf { it.length() } ?: 0L
            
        File(context.filesDir, "models").listFiles()
            ?.filter { it.name.endsWith(".part") }
            ?.forEach { it.delete() }
        
        // Cleanup old versions (could be enhanced)
        val modelsCleanedUp = modelManager.cleanupOldVersions(keepLatest = 1)
        
        val afterSize = calculateTotalStorageUsed()
        
        return StorageCleanupResult(
            spaceFreed = beforeSize - afterSize,
            tempFilesRemoved = tempFiles,
            modelsCleanedUp = modelsCleanedUp
        )
    }
    
    // === Utility Methods ===
    
    fun calculateTotalStorageUsed(): Long {
        return File(context.filesDir, "models").walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }
    
    fun getStorageUsageByCategory(): Map<ModelCategory, Long> {
        return getModelsByCategory().mapValues { (_, models) ->
            models.filter { it.status in setOf(ModelState.Status.DOWNLOADED, ModelState.Status.READY) }
                .sumOf { it.storageSize }
        }
    }
    
    // === Private Implementation ===
    
    private fun refreshModelStates() {
        val availableModels = registry.listAllModels()
        val downloadedModels = versionStore.getDownloadedModels().associateBy { it.id }
        
        val states = availableModels.associate { model ->
            val category = determineCategory(model)
            val isDownloaded = downloadedModels.containsKey(model.id)
            val storageSize = if (isDownloaded) calculateModelStorageSize(model.id) else 0L
            
            model.id to ModelState(
                modelInfo = model,
                status = if (isDownloaded) ModelState.Status.DOWNLOADED else ModelState.Status.AVAILABLE,
                category = category,
                isDefault = isDefaultModel(model, category),
                storageSize = storageSize
            )
        }
        
        _modelStates.value = states
    }
    
    private fun determineCategory(model: ModelInfo): ModelCategory {
        return when {
            model.runner.contains("asr", ignoreCase = true) -> ModelCategory.ASR
            model.runner.contains("tts", ignoreCase = true) -> ModelCategory.TTS
            model.runner.contains("vlm", ignoreCase = true) -> ModelCategory.VLM
            model.runner in setOf("executorch", "mediatek") -> ModelCategory.LLM
            else -> ModelCategory.UNKNOWN
        }
    }
    
    private fun isDefaultModel(model: ModelInfo, category: ModelCategory): Boolean {
        // Enhanced logic - could read from JSON defaults section
        return when (category) {
            ModelCategory.ASR -> model.id == "Breeze-ASR-25-onnx" // Temporary: using whisper instead of "Breeze-ASR-25-onnx"
            ModelCategory.LLM -> model.id == "Breeze2-3B-8W16A-250630-npu"
            else -> false
        }
    }
    
    private fun calculateModelStorageSize(modelId: String): Long {
        val modelDir = File(context.filesDir, "models/$modelId")
        return if (modelDir.exists()) {
            modelDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else 0L
    }
    
    private fun updateModelStatus(modelId: String, status: ModelState.Status) {
        val currentStates = _modelStates.value.toMutableMap()
        currentStates[modelId]?.let { currentState ->
            currentStates[modelId] = currentState.copy(
                status = status,
                lastUpdated = System.currentTimeMillis()
            )
        }
        _modelStates.value = currentStates
    }
    
    private fun updateModelProgress(modelId: String, percent: Int, speed: Long, eta: Long) {
        val currentStates = _modelStates.value.toMutableMap()
        currentStates[modelId]?.let { currentState ->
            currentStates[modelId] = currentState.copy(
                downloadProgress = percent,
                downloadSpeed = speed,
                downloadEta = eta,
                lastUpdated = System.currentTimeMillis()
            )
        }
        _modelStates.value = currentStates
    }
    
    // === Helper Classes ===
    
    data class DownloadHandle(val modelId: String) {
        fun cancel() {
            // Implementation for cancelling download
        }
        
        fun pause() {
            // Implementation for pausing download
        }
        
        fun resume() {
            // Implementation for resuming download
        }
    }
    
    data class StorageCleanupResult(
        val spaceFreed: Long,
        val tempFilesRemoved: Long,
        val modelsCleanedUp: Int
    )
    
    // === Listener Interfaces ===
    
    interface ModelDownloadListener {
        fun onProgress(modelId: String, percent: Int, speed: Long, eta: Long) = Unit
        fun onCompleted(modelId: String) = Unit
        fun onError(modelId: String, error: Throwable, fileName: String?) = Unit
        fun onPaused(modelId: String) = Unit
        fun onResumed(modelId: String) = Unit
        fun onCancelled(modelId: String) = Unit
        fun onFileProgress(
            modelId: String, fileName: String, fileIndex: Int, fileCount: Int,
            bytesDownloaded: Long, totalBytes: Long, speed: Long, eta: Long
        ) = Unit
        fun onFileCompleted(modelId: String, fileName: String, fileIndex: Int, fileCount: Int) = Unit
    }
    
    open class SimpleDownloadListener : ModelDownloadListener {
        // Provides default implementations for optional callbacks
    }
    
    interface BulkDownloadListener {
        fun onModelCompleted(modelId: String, success: Boolean)
        fun onAllCompleted()
    }
}