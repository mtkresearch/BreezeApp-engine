package com.mtkresearch.breezeapp.engine.core.download

import kotlinx.coroutines.flow.Flow

/**
 * Service interface for handling background model downloads
 * 
 * This interface provides a clean API for download operations with real-time progress tracking.
 * Implementations should handle network operations, file management, and progress notifications.
 */
interface DownloadService {
    
    /**
     * Start downloading a model with real-time progress updates
     * 
     * @param modelId Unique identifier for the model to download
     * @param downloadUrl URL to download the model from
     * @param fileName Optional custom filename for the downloaded file
     * @return Flow of DownloadState providing real-time progress updates
     */
    fun downloadModel(
        modelId: String, 
        downloadUrl: String, 
        fileName: String? = null
    ): Flow<DownloadState>
    
    /**
     * Cancel an ongoing download
     * 
     * @param modelId Unique identifier for the model download to cancel
     * @return true if cancellation was successful, false if download was not found
     */
    suspend fun cancelDownload(modelId: String): Boolean
    
    /**
     * Pause an ongoing download
     * 
     * @param modelId Unique identifier for the model download to pause
     * @return true if pause was successful, false if download was not found or cannot be paused
     */
    suspend fun pauseDownload(modelId: String): Boolean
    
    /**
     * Resume a paused download
     * 
     * @param modelId Unique identifier for the model download to resume
     * @return true if resume was successful, false if download was not found or cannot be resumed
     */
    suspend fun resumeDownload(modelId: String): Boolean
    
    /**
     * Get current state of a specific download
     * 
     * @param modelId Unique identifier for the model download
     * @return Current DownloadState or null if download not found
     */
    suspend fun getDownloadState(modelId: String): DownloadState?
    
    /**
     * Get states of all active downloads
     * 
     * @return Flow of all current download states
     */
    fun getAllDownloadStates(): Flow<List<DownloadState>>
    
    /**
     * Check if a download is currently active for a specific model
     * 
     * @param modelId Unique identifier for the model
     * @return true if download is active (queued, downloading, or paused)
     */
    suspend fun isDownloadActive(modelId: String): Boolean
    
    /**
     * Clear completed or failed downloads from the tracking system
     * This helps maintain clean state and free up memory
     */
    suspend fun clearFinishedDownloads()
    
    /**
     * Get total number of active downloads
     * Useful for UI indicators and resource management
     */
    suspend fun getActiveDownloadCount(): Int
}