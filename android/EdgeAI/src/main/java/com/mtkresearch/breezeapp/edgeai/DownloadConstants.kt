package com.mtkresearch.breezeapp.edgeai

/**
 * Constants for download events broadcast by the engine.
 * Clients can listen to these broadcasts to show download progress UI.
 */
object DownloadConstants {
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
}
