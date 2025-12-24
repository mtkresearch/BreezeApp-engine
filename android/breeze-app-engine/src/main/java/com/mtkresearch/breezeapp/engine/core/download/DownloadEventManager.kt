package com.mtkresearch.breezeapp.engine.core.download

import android.content.Context
import android.content.Intent
import com.mtkresearch.breezeapp.engine.core.Logger

/**
 * Manages download events and UI visibility across the application
 * 
 * This singleton handles coordination between download services and UI components,
 * ensuring users see download progress regardless of how downloads are triggered.
 */
object DownloadEventManager {
    
    private const val TAG = "DownloadEventManager"
    
    // Broadcast action constants - Mapped to EdgeAI SDK constants
    const val ACTION_DOWNLOAD_STARTED = com.mtkresearch.breezeapp.edgeai.DownloadConstants.ACTION_DOWNLOAD_STARTED
    const val ACTION_DOWNLOAD_PROGRESS = com.mtkresearch.breezeapp.edgeai.DownloadConstants.ACTION_DOWNLOAD_PROGRESS
    const val ACTION_DOWNLOAD_COMPLETED = com.mtkresearch.breezeapp.edgeai.DownloadConstants.ACTION_DOWNLOAD_COMPLETED
    const val ACTION_DOWNLOAD_FAILED = com.mtkresearch.breezeapp.edgeai.DownloadConstants.ACTION_DOWNLOAD_FAILED
    const val ACTION_SHOW_DOWNLOAD_UI = com.mtkresearch.breezeapp.edgeai.DownloadConstants.ACTION_SHOW_DOWNLOAD_UI
    
    // Intent extra keys
    const val EXTRA_MODEL_ID = com.mtkresearch.breezeapp.edgeai.DownloadConstants.EXTRA_MODEL_ID
    const val EXTRA_FILE_NAME = com.mtkresearch.breezeapp.edgeai.DownloadConstants.EXTRA_FILE_NAME
    const val EXTRA_PROGRESS_PERCENTAGE = com.mtkresearch.breezeapp.edgeai.DownloadConstants.EXTRA_PROGRESS_PERCENTAGE
    const val EXTRA_DOWNLOADED_BYTES = com.mtkresearch.breezeapp.edgeai.DownloadConstants.EXTRA_DOWNLOADED_BYTES
    const val EXTRA_TOTAL_BYTES = com.mtkresearch.breezeapp.edgeai.DownloadConstants.EXTRA_TOTAL_BYTES
    const val EXTRA_ERROR_MESSAGE = com.mtkresearch.breezeapp.edgeai.DownloadConstants.EXTRA_ERROR_MESSAGE
    const val EXTRA_CURRENT_FILE_INDEX = "current_file_index"
    const val EXTRA_TOTAL_FILES = "total_files"
    
    private val logger = Logger
    
    /**
     * Notify that a download has started
     * This will trigger UI components to show download progress
     */
    fun notifyDownloadStarted(context: Context, modelId: String, fileName: String? = null, currentFileIndex: Int = -1, totalFiles: Int = -1) {
        logger.d(TAG, "Broadcasting download started for model: $modelId (File ${currentFileIndex + 1}/$totalFiles)")
        
        val intent = Intent(ACTION_DOWNLOAD_STARTED).apply {
            putExtra(EXTRA_MODEL_ID, modelId)
            fileName?.let { putExtra(EXTRA_FILE_NAME, it) }
            if (currentFileIndex >= 0) putExtra(EXTRA_CURRENT_FILE_INDEX, currentFileIndex)
            if (totalFiles > 0) putExtra(EXTRA_TOTAL_FILES, totalFiles)
        }
        
        
        context.sendBroadcast(intent)
    }
    
    /**
     * Notify download progress update
     */
    fun notifyDownloadProgress(
        context: Context,
        modelId: String,
        progressPercentage: Int,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        val intent = Intent(ACTION_DOWNLOAD_PROGRESS).apply {
            putExtra(EXTRA_MODEL_ID, modelId)
            putExtra(EXTRA_PROGRESS_PERCENTAGE, progressPercentage)
            putExtra(EXTRA_DOWNLOADED_BYTES, downloadedBytes)
            putExtra(EXTRA_TOTAL_BYTES, totalBytes)
        }
        
        context.sendBroadcast(intent)
    }
    
    /**
     * Notify download completion
     * @param fileName Optional - if provided, marks a specific file as complete; otherwise marks entire model
     */
    fun notifyDownloadCompleted(context: Context, modelId: String, fileName: String? = null) {
        logger.d(TAG, "Broadcasting download completed for model: $modelId${if (fileName != null) ", file: $fileName" else ""}")
        
        val intent = Intent(ACTION_DOWNLOAD_COMPLETED).apply {
            putExtra(EXTRA_MODEL_ID, modelId)
            fileName?.let { putExtra(EXTRA_FILE_NAME, it) }
        }
        
        context.sendBroadcast(intent)
    }
    
    /**
     * Notify download failure
     */
    fun notifyDownloadFailed(context: Context, modelId: String, errorMessage: String) {
        logger.d(TAG, "Broadcasting download failed for model: $modelId")
        
        val intent = Intent(ACTION_DOWNLOAD_FAILED).apply {
            putExtra(EXTRA_MODEL_ID, modelId)
            putExtra(EXTRA_ERROR_MESSAGE, errorMessage)
        }
        
        context.sendBroadcast(intent)
    }
    
    // New: Global download state for app-wide action blocking
    const val ACTION_GLOBAL_DOWNLOAD_STATE = "com.mtkresearch.breezeapp.GLOBAL_DOWNLOAD_STATE"
    const val EXTRA_IS_DOWNLOADING = "is_downloading"
    
    /**
     * Notify global download state change for app-wide action blocking
     * This allows activities to disable AI actions during downloads
     */
    fun notifyGlobalDownloadState(context: Context, isDownloading: Boolean, modelId: String? = null) {
        logger.d(TAG, "Broadcasting global download state: isDownloading=$isDownloading, modelId=$modelId")
        
        val intent = Intent(ACTION_GLOBAL_DOWNLOAD_STATE).apply {
            putExtra(EXTRA_IS_DOWNLOADING, isDownloading)
            modelId?.let { putExtra(EXTRA_MODEL_ID, it) }
        }
        
        context.sendBroadcast(intent)
    }
    
    /**
     * Request to show download UI (for any listening activities)
     * This is useful when downloads start from service contexts
     */
    fun requestShowDownloadUI(context: Context, modelId: String) {
        logger.d(TAG, "Requesting download UI display for model: $modelId")
        
        val intent = Intent(ACTION_SHOW_DOWNLOAD_UI).apply {
            putExtra(EXTRA_MODEL_ID, modelId)
        }
        
        context.sendBroadcast(intent)
    }
}