package com.mtkresearch.breezeapp.engine.core.download

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.mtkresearch.breezeapp.engine.core.Logger

/**
 * Manages download events and UI visibility across the application
 * 
 * This singleton handles coordination between download services and UI components,
 * ensuring users see download progress regardless of how downloads are triggered.
 */
object DownloadEventManager {
    
    private const val TAG = "DownloadEventManager"
    
    // Broadcast action constants
    const val ACTION_DOWNLOAD_STARTED = "com.mtkresearch.breezeapp.engine.DOWNLOAD_STARTED"
    const val ACTION_DOWNLOAD_PROGRESS = "com.mtkresearch.breezeapp.engine.DOWNLOAD_PROGRESS"
    const val ACTION_DOWNLOAD_COMPLETED = "com.mtkresearch.breezeapp.engine.DOWNLOAD_COMPLETED"
    const val ACTION_DOWNLOAD_FAILED = "com.mtkresearch.breezeapp.engine.DOWNLOAD_FAILED"
    const val ACTION_SHOW_DOWNLOAD_UI = "com.mtkresearch.breezeapp.engine.SHOW_DOWNLOAD_UI"
    
    // Intent extra keys
    const val EXTRA_MODEL_ID = "model_id"
    const val EXTRA_FILE_NAME = "file_name"
    const val EXTRA_PROGRESS_PERCENTAGE = "progress_percentage"
    const val EXTRA_DOWNLOADED_BYTES = "downloaded_bytes"
    const val EXTRA_TOTAL_BYTES = "total_bytes"
    const val EXTRA_ERROR_MESSAGE = "error_message"
    
    private val logger = Logger
    
    /**
     * Notify that a download has started
     * This will trigger UI components to show download progress
     */
    fun notifyDownloadStarted(context: Context, modelId: String, fileName: String? = null) {
        logger.d(TAG, "Broadcasting download started for model: $modelId")
        
        val intent = Intent(ACTION_DOWNLOAD_STARTED).apply {
            putExtra(EXTRA_MODEL_ID, modelId)
            fileName?.let { putExtra(EXTRA_FILE_NAME, it) }
        }
        
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        
        // Also send show UI broadcast for immediate UI display
        val showUIIntent = Intent(ACTION_SHOW_DOWNLOAD_UI).apply {
            putExtra(EXTRA_MODEL_ID, modelId)
            fileName?.let { putExtra(EXTRA_FILE_NAME, it) }
        }
        
        LocalBroadcastManager.getInstance(context).sendBroadcast(showUIIntent)
        
        // NOTE: Removed flawed DownloadUIManager approach
        // UI is now shown via notification action button that launches DownloadProgressActivity
        // This is much more robust than trying to find "recent activities"
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
        
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }
    
    /**
     * Notify download completion
     */
    fun notifyDownloadCompleted(context: Context, modelId: String) {
        logger.d(TAG, "Broadcasting download completed for model: $modelId")
        
        val intent = Intent(ACTION_DOWNLOAD_COMPLETED).apply {
            putExtra(EXTRA_MODEL_ID, modelId)
        }
        
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
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
        
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
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
        
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }
}