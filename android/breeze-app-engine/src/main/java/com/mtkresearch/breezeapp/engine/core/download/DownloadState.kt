package com.mtkresearch.breezeapp.engine.core.download

/**
 * Represents the current state of a model download operation
 * 
 * This data class provides comprehensive information about download progress,
 * including status, progress percentage, speed, and error handling.
 */
data class DownloadState(
    val modelId: String,
    val status: Status,
    val progress: Float = 0f, // 0.0 to 1.0
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSecond: Long = 0L,
    val errorMessage: String? = null,
    val estimatedTimeRemainingMs: Long = 0L,
    val fileName: String? = null,
    val startTimeMs: Long = System.currentTimeMillis()
) {
    enum class Status {
        QUEUED,
        DOWNLOADING,
        PAUSED,
        COMPLETED,
        FAILED,
        CANCELLED
    }
    
    /**
     * Get download progress as percentage (0-100)
     */
    val progressPercentage: Int
        get() = (progress * 100).toInt()
    
    /**
     * Get human-readable file size for downloaded bytes
     */
    val downloadedSizeFormatted: String
        get() = formatFileSize(downloadedBytes)
    
    /**
     * Get human-readable file size for total bytes
     */
    val totalSizeFormatted: String
        get() = formatFileSize(totalBytes)
    
    /**
     * Get human-readable download speed
     */
    val speedFormatted: String
        get() = "${formatFileSize(speedBytesPerSecond)}/s"
    
    /**
     * Get estimated time remaining in human-readable format
     */
    val estimatedTimeRemainingFormatted: String
        get() = formatTime(estimatedTimeRemainingMs)
    
    /**
     * Check if download is currently active
     */
    val isActive: Boolean
        get() = status in listOf(Status.QUEUED, Status.DOWNLOADING)
    
    /**
     * Check if download has finished (completed or failed)
     */
    val isFinished: Boolean
        get() = status in listOf(Status.COMPLETED, Status.FAILED, Status.CANCELLED)
    
    /**
     * Create a new DownloadState with updated progress
     */
    fun withProgress(
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSecond: Long = this.speedBytesPerSecond
    ): DownloadState {
        val newProgress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
        val timeRemainingMs = if (speedBytesPerSecond > 0) {
            ((totalBytes - downloadedBytes) * 1000L) / speedBytesPerSecond
        } else 0L
        
        return copy(
            status = Status.DOWNLOADING,
            progress = newProgress.coerceIn(0f, 1f),
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            speedBytesPerSecond = speedBytesPerSecond,
            estimatedTimeRemainingMs = timeRemainingMs
        )
    }
    
    /**
     * Create a new DownloadState with completed status
     */
    fun withCompleted(): DownloadState = copy(
        status = Status.COMPLETED,
        progress = 1f,
        downloadedBytes = totalBytes,
        speedBytesPerSecond = 0L,
        estimatedTimeRemainingMs = 0L
    )
    
    /**
     * Create a new DownloadState with failed status
     */
    fun withError(errorMessage: String): DownloadState = copy(
        status = Status.FAILED,
        errorMessage = errorMessage,
        speedBytesPerSecond = 0L,
        estimatedTimeRemainingMs = 0L
    )
    
    /**
     * Create a new DownloadState with cancelled status
     */
    fun withCancelled(): DownloadState = copy(
        status = Status.CANCELLED,
        speedBytesPerSecond = 0L,
        estimatedTimeRemainingMs = 0L
    )
    
    companion object {
        /**
         * Create initial download state for a new download
         */
        fun initial(modelId: String, fileName: String? = null): DownloadState = DownloadState(
            modelId = modelId,
            status = Status.QUEUED,
            fileName = fileName
        )
        
        /**
         * Format bytes into human-readable file size
         */
        private fun formatFileSize(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
        
        /**
         * Format milliseconds into human-readable time
         */
        private fun formatTime(ms: Long): String {
            if (ms <= 0) return "Unknown"
            
            val seconds = ms / 1000
            return when {
                seconds < 60 -> "${seconds}s"
                seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
                else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
            }
        }
    }
}