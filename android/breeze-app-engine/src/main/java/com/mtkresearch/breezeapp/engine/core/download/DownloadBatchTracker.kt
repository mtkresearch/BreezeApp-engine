package com.mtkresearch.breezeapp.engine.core.download

import com.mtkresearch.breezeapp.engine.core.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks multi-file downloads as atomic batches
 * 
 * This prevents the download service from stopping between sequential file downloads
 * by tracking when a batch of files is being downloaded together.
 */
object DownloadBatchTracker {
    private const val TAG = "DownloadBatchTracker"
    private val logger = Logger
    
    private val activeBatches = ConcurrentHashMap<String, BatchInfo>()
    
    data class BatchInfo(
        val batchId: String,
        val modelId: String,
        val totalFiles: Int,
        val completedFiles: AtomicInteger = AtomicInteger(0),
        val startTime: Long = System.currentTimeMillis()
    ) {
        fun isComplete(): Boolean = completedFiles.get() >= totalFiles
        
        fun getProgress(): Int = if (totalFiles > 0) {
            (completedFiles.get() * 100) / totalFiles
        } else 0
    }
    
    /**
     * Start tracking a new batch of file downloads
     */
    fun startBatch(batchId: String, modelId: String, totalFiles: Int) {
        val batch = BatchInfo(batchId, modelId, totalFiles)
        activeBatches[batchId] = batch
        logger.d(TAG, "Started batch: $batchId for model: $modelId ($totalFiles files)")
    }
    
    /**
     * Mark a file as completed in the batch
     */
    fun markFileComplete(batchId: String, fileIndex: Int) {
        val batch = activeBatches[batchId]
        if (batch != null) {
            val completed = batch.completedFiles.incrementAndGet()
            logger.d(TAG, "Batch $batchId: file $fileIndex complete ($completed/${batch.totalFiles})")
            
            if (batch.isComplete()) {
                logger.d(TAG, "Batch $batchId: all files complete!")
            }
        } else {
            logger.w(TAG, "Attempted to mark file complete for unknown batch: $batchId")
        }
    }
    
    /**
     * Complete and remove a batch
     */
    fun completeBatch(batchId: String) {
        val batch = activeBatches.remove(batchId)
        if (batch != null) {
            val duration = System.currentTimeMillis() - batch.startTime
            logger.d(TAG, "Completed batch: $batchId (${batch.totalFiles} files in ${duration}ms)")
        }
    }
    
    /**
     * Cancel a batch
     */
    fun cancelBatch(batchId: String) {
        val batch = activeBatches.remove(batchId)
        if (batch != null) {
            logger.d(TAG, "Cancelled batch: $batchId (${batch.completedFiles.get()}/${batch.totalFiles} files completed)")
        }
    }
    
    /**
     * Check if a specific batch is active
     */
    fun isBatchActive(batchId: String): Boolean {
        return activeBatches.containsKey(batchId)
    }
    
    /**
     * Check if ANY batches are active
     */
    fun hasActiveBatches(): Boolean {
        return activeBatches.isNotEmpty()
    }
    
    /**
     * Get batch info
     */
    fun getBatchInfo(batchId: String): BatchInfo? {
        return activeBatches[batchId]
    }
    
    /**
     * Get all active batches
     */
    fun getActiveBatches(): List<BatchInfo> {
        return activeBatches.values.toList()
    }
    
    /**
     * Clear all batches (for cleanup)
     */
    fun clearAll() {
        logger.d(TAG, "Clearing all batches (${activeBatches.size} active)")
        activeBatches.clear()
    }
}
