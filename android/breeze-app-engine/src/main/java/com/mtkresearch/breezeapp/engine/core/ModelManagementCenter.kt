package com.mtkresearch.breezeapp.engine.core

import android.content.Context
import com.mtkresearch.breezeapp.engine.domain.model.ModelInfo
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.SharingStarted

/**
 * Centralized Model Management Center (Simplified Facade)
 * 
 * This class now serves as a simplified facade over the UnifiedModelManager,
 * providing backward compatibility while reducing complexity.
 * 
 * REFACTORED: Consolidated ModelManagementCenter, ModelManager, ModelRegistry, 
 * and ModelVersionStore into a single UnifiedModelManager for:
 * - Reduced code duplication
 * - Simplified maintenance
 * - Better resource management
 * - Cleaner API surface
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
    
    // Delegate to unified manager
    private val unifiedManager = UnifiedModelManager.getInstance(context)
    
    // === Backward Compatibility Types ===
    
    enum class ModelCategory(val key: String) {
        LLM("llm"),
        ASR("asr"), 
        TTS("tts"),
        VLM("vlm"),
        UNKNOWN("unknown");
        
        fun toUnified(): UnifiedModelManager.ModelCategory {
            return when (this) {
                LLM -> UnifiedModelManager.ModelCategory.LLM
                ASR -> UnifiedModelManager.ModelCategory.ASR
                TTS -> UnifiedModelManager.ModelCategory.TTS
                VLM -> UnifiedModelManager.ModelCategory.VLM
                UNKNOWN -> UnifiedModelManager.ModelCategory.UNKNOWN
            }
        }
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
    
    // Expose state flow from unified manager with conversion
    val modelStates: StateFlow<Map<String, ModelState>> = unifiedManager.modelStates.map { unifiedStates ->
        unifiedStates.mapValues { (_, unifiedState) ->
            ModelState(
                modelInfo = unifiedState.modelInfo,
                status = when (unifiedState.status) {
                    UnifiedModelManager.ModelState.Status.AVAILABLE -> ModelState.Status.AVAILABLE
                    UnifiedModelManager.ModelState.Status.DOWNLOADING -> ModelState.Status.DOWNLOADING
                    UnifiedModelManager.ModelState.Status.DOWNLOADED -> ModelState.Status.DOWNLOADED
                    UnifiedModelManager.ModelState.Status.PAUSED -> ModelState.Status.PAUSED
                    UnifiedModelManager.ModelState.Status.ERROR -> ModelState.Status.ERROR
                    UnifiedModelManager.ModelState.Status.INSTALLING -> ModelState.Status.INSTALLING
                    UnifiedModelManager.ModelState.Status.READY -> ModelState.Status.READY
                },
                downloadProgress = unifiedState.downloadProgress,
                downloadSpeed = unifiedState.downloadSpeed,
                downloadEta = unifiedState.downloadEta,
                storageSize = unifiedState.storageSize,
                category = when (unifiedState.category) {
                    UnifiedModelManager.ModelCategory.LLM -> ModelCategory.LLM
                    UnifiedModelManager.ModelCategory.ASR -> ModelCategory.ASR
                    UnifiedModelManager.ModelCategory.TTS -> ModelCategory.TTS
                    UnifiedModelManager.ModelCategory.VLM -> ModelCategory.VLM
                    UnifiedModelManager.ModelCategory.UNKNOWN -> ModelCategory.UNKNOWN
                },
                isDefault = unifiedState.isDefault,
                lastUpdated = unifiedState.lastUpdated
            )
        }
    }.stateIn(GlobalScope, SharingStarted.Eagerly, emptyMap())
    
    // === Configuration ===
    
    /**
     * Set the status manager for engine status updates during downloads
     */
    fun setStatusManager(statusManager: BreezeAppEngineStatusManager) {
        unifiedManager.setStatusManager(statusManager)
    }
    
    // === Public API Surface (Delegated) ===
    
    /**
     * Get all models organized by category
     */
    fun getModelsByCategory(): Map<ModelCategory, List<ModelState>> {
        return unifiedManager.getModelsByCategory().mapKeys { (unifiedCategory, _) ->
            when (unifiedCategory) {
                UnifiedModelManager.ModelCategory.LLM -> ModelCategory.LLM
                UnifiedModelManager.ModelCategory.ASR -> ModelCategory.ASR
                UnifiedModelManager.ModelCategory.TTS -> ModelCategory.TTS
                UnifiedModelManager.ModelCategory.VLM -> ModelCategory.VLM
                UnifiedModelManager.ModelCategory.UNKNOWN -> ModelCategory.UNKNOWN
            }
        }.mapValues { (_, unifiedStates) ->
            unifiedStates.map { convertToLegacyState(it) }
        }
    }
    
    /**
     * Get all available models for a specific category
     */
    fun getAvailableModels(category: ModelCategory): List<ModelState> = 
        unifiedManager.getAvailableModels(category.toUnified()).map { convertToLegacyState(it) }
    
    /**
     * Get downloaded models for a category
     */
    fun getDownloadedModels(category: ModelCategory): List<ModelState> = 
        unifiedManager.getDownloadedModels(category.toUnified()).map { convertToLegacyState(it) }
    
    /**
     * Get default model for a category
     */
    fun getDefaultModel(category: ModelCategory): ModelState? = 
        unifiedManager.getDefaultModel(category.toUnified())?.let { convertToLegacyState(it) }
    
    /**
     * Get model state by ID
     */
    fun getModelState(modelId: String): ModelState? = 
        unifiedManager.getModelState(modelId)?.let { convertToLegacyState(it) }
    
    // === Download Operations (Delegated) ===
    
    /**
     * Download a specific model
     */
    fun downloadModel(
        modelId: String,
        listener: ModelDownloadListener? = null
    ): DownloadHandle {
        val unifiedHandle = unifiedManager.downloadModel(modelId, object : UnifiedModelManager.DownloadListener {
            override fun onProgress(modelId: String, percent: Int, speed: Long, eta: Long) {
                listener?.onProgress(modelId, percent, speed, eta)
            }
            override fun onCompleted(modelId: String) {
                listener?.onCompleted(modelId)
            }
            override fun onError(modelId: String, error: Throwable, fileName: String?) {
                listener?.onError(modelId, error, fileName)
            }
            override fun onPaused(modelId: String) {
                listener?.onPaused(modelId)
            }
            override fun onResumed(modelId: String) {
                listener?.onResumed(modelId)
            }
            override fun onCancelled(modelId: String) {
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
        
        // Convert unified handle to legacy handle
        return DownloadHandle(modelId)
    }
    
    /**
     * Ensure default model for category is ready
     */
    fun ensureDefaultModelReady(
        category: ModelCategory,
        listener: ModelDownloadListener? = null
    ) {
        unifiedManager.ensureDefaultModelReady(category.toUnified(), object : UnifiedModelManager.DownloadListener {
            override fun onCompleted(modelId: String) {
                listener?.onCompleted(modelId)
            }
            override fun onError(modelId: String, error: Throwable, fileName: String?) {
                listener?.onError(modelId, error, fileName)
            }
        })
    }
    
    /**
     * Delete a model
     */
    fun deleteModel(modelId: String): Boolean = unifiedManager.deleteModel(modelId)
    
    // === Bulk Operations (Delegated) ===
    
    /**
     * Download all default models for specified categories
     */
    fun downloadDefaultModels(
        categories: List<ModelCategory>,
        listener: BulkDownloadListener? = null
    ) {
        val unifiedCategories = categories.map { it.toUnified() }
        unifiedManager.downloadDefaultModels(unifiedCategories, object : UnifiedModelManager.BulkDownloadListener {
            override fun onModelCompleted(modelId: String, success: Boolean) {
                listener?.onModelCompleted(modelId, success)
            }
            override fun onAllCompleted() {
                listener?.onAllCompleted()
            }
        })
    }
    
    /**
     * Clean up old model versions and temporary files
     */
    fun cleanupStorage(): StorageCleanupResult {
        val result = unifiedManager.cleanupStorage()
        return StorageCleanupResult(
            spaceFreed = result.spaceFreed,
            tempFilesRemoved = result.tempFilesRemoved,
            modelsCleanedUp = result.modelsCleanedUp
        )
    }
    
    // === Utility Methods (Delegated) ===
    
    fun calculateTotalStorageUsed(): Long = unifiedManager.calculateTotalStorageUsed()
    
    fun getStorageUsageByCategory(): Map<ModelCategory, Long> {
        return unifiedManager.getStorageUsageByCategory().mapKeys { (unifiedCategory, _) ->
            when (unifiedCategory) {
                UnifiedModelManager.ModelCategory.LLM -> ModelCategory.LLM
                UnifiedModelManager.ModelCategory.ASR -> ModelCategory.ASR
                UnifiedModelManager.ModelCategory.TTS -> ModelCategory.TTS
                UnifiedModelManager.ModelCategory.VLM -> ModelCategory.VLM
                UnifiedModelManager.ModelCategory.UNKNOWN -> ModelCategory.UNKNOWN
            }
        }
    }
    
    // === Private Helper Methods ===
    
    private fun convertToLegacyState(unifiedState: UnifiedModelManager.ModelState): ModelState {
        return ModelState(
            modelInfo = unifiedState.modelInfo,
            status = when (unifiedState.status) {
                UnifiedModelManager.ModelState.Status.AVAILABLE -> ModelState.Status.AVAILABLE
                UnifiedModelManager.ModelState.Status.DOWNLOADING -> ModelState.Status.DOWNLOADING
                UnifiedModelManager.ModelState.Status.DOWNLOADED -> ModelState.Status.DOWNLOADED
                UnifiedModelManager.ModelState.Status.PAUSED -> ModelState.Status.PAUSED
                UnifiedModelManager.ModelState.Status.ERROR -> ModelState.Status.ERROR
                UnifiedModelManager.ModelState.Status.INSTALLING -> ModelState.Status.INSTALLING
                UnifiedModelManager.ModelState.Status.READY -> ModelState.Status.READY
            },
            downloadProgress = unifiedState.downloadProgress,
            downloadSpeed = unifiedState.downloadSpeed,
            downloadEta = unifiedState.downloadEta,
            storageSize = unifiedState.storageSize,
            category = when (unifiedState.category) {
                UnifiedModelManager.ModelCategory.LLM -> ModelCategory.LLM
                UnifiedModelManager.ModelCategory.ASR -> ModelCategory.ASR
                UnifiedModelManager.ModelCategory.TTS -> ModelCategory.TTS
                UnifiedModelManager.ModelCategory.VLM -> ModelCategory.VLM
                UnifiedModelManager.ModelCategory.UNKNOWN -> ModelCategory.UNKNOWN
            },
            isDefault = unifiedState.isDefault,
            lastUpdated = unifiedState.lastUpdated
        )
    }
    
    // === Helper Classes (Backward Compatibility) ===
    
    data class DownloadHandle(val modelId: String) {
        fun cancel() {
            // Legacy handle - no implementation needed as UnifiedModelManager handles this
        }
        
        fun pause() {
            // Legacy handle - no implementation needed
        }
        
        fun resume() {
            // Legacy handle - no implementation needed
        }
    }
    
    data class StorageCleanupResult(
        val spaceFreed: Long,
        val tempFilesRemoved: Long,
        val modelsCleanedUp: Int
    )
    
    // === Listener Interfaces (Backward Compatibility) ===
    
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