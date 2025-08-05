package com.mtkresearch.breezeapp.engine.core

import android.content.Context
import com.mtkresearch.breezeapp.engine.repository.ModelManager

/**
 * Use case for orchestrating model download and readiness in BreezeAppEngine.
 * 
 * DEPRECATED: Use ModelManagementCenter for new implementations.
 * This class is maintained for backward compatibility only.
 *
 * Usage:
 *   val useCase = DownloadModelUseCase(context)
 *   useCase.ensureDefaultModelReady(listener) // Ensures default model is present or downloads it
 *   useCase.downloadModel(modelId, listener) // Downloads a specific model on demand
 *
 * The listener receives progress, completion, and error callbacks.
 */
@Deprecated("Use ModelManagementCenter instead", ReplaceWith("ModelManagementCenter.getInstance(context)"))
class DownloadModelUseCase(private val context: Context) {
    
    private val modelManagementCenter = ModelManagementCenter.getInstance(context)
    
    /**
     * Ensures the default LLM model is ready.
     * For category-specific defaults, use ModelManagementCenter directly.
     *
     * @param listener ModelManager.DownloadListener for progress and completion callbacks.
     */
    fun ensureDefaultModelReady(listener: ModelManager.DownloadListener) {
        // Default to LLM category for backward compatibility
        modelManagementCenter.ensureDefaultModelReady(
            ModelManagementCenter.ModelCategory.LLM,
            object : ModelManagementCenter.ModelDownloadListener {
                override fun onProgress(modelId: String, percent: Int, speed: Long, eta: Long) {
                    listener.onProgress(modelId, percent, speed, eta)
                }
                
                override fun onCompleted(modelId: String) {
                    listener.onCompleted(modelId)
                }
                
                override fun onError(modelId: String, error: Throwable, fileName: String?) {
                    listener.onError(modelId, error, fileName)
                }
                
                override fun onPaused(modelId: String) {
                    listener.onPaused(modelId)
                }
                
                override fun onResumed(modelId: String) {
                    listener.onResumed(modelId)
                }
                
                override fun onCancelled(modelId: String) {
                    listener.onCancelled(modelId)
                }
                
                override fun onFileProgress(
                    modelId: String, fileName: String, fileIndex: Int, fileCount: Int,
                    bytesDownloaded: Long, totalBytes: Long, speed: Long, eta: Long
                ) {
                    listener.onFileProgress(modelId, fileName, fileIndex, fileCount, bytesDownloaded, totalBytes, speed, eta)
                }
                
                override fun onFileCompleted(modelId: String, fileName: String, fileIndex: Int, fileCount: Int) {
                    listener.onFileCompleted(modelId, fileName, fileIndex, fileCount)
                }
            }
        )
    }

    /**
     * Downloads a specific model by modelId, with progress and error callbacks.
     *
     * @param modelId The id of the model to download (must exist in fullModelList.json)
     * @param listener ModelManager.DownloadListener for progress and completion callbacks.
     */
    fun downloadModel(modelId: String, listener: ModelManager.DownloadListener) {
        modelManagementCenter.downloadModel(modelId, object : ModelManagementCenter.ModelDownloadListener {
            override fun onProgress(modelId: String, percent: Int, speed: Long, eta: Long) {
                listener.onProgress(modelId, percent, speed, eta)
            }
            
            override fun onCompleted(modelId: String) {
                listener.onCompleted(modelId)
            }
            
            override fun onError(modelId: String, error: Throwable, fileName: String?) {
                listener.onError(modelId, error, fileName)
            }
            
            override fun onPaused(modelId: String) {
                listener.onPaused(modelId)
            }
            
            override fun onResumed(modelId: String) {
                listener.onResumed(modelId)
            }
            
            override fun onCancelled(modelId: String) {
                listener.onCancelled(modelId)
            }
            
            override fun onFileProgress(
                modelId: String, fileName: String, fileIndex: Int, fileCount: Int,
                bytesDownloaded: Long, totalBytes: Long, speed: Long, eta: Long
            ) {
                listener.onFileProgress(modelId, fileName, fileIndex, fileCount, bytesDownloaded, totalBytes, speed, eta)
            }
            
            override fun onFileCompleted(modelId: String, fileName: String, fileIndex: Int, fileCount: Int) {
                listener.onFileCompleted(modelId, fileName, fileIndex, fileCount)
            }
        })
    }
} 