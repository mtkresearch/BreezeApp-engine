package com.mtkresearch.breezeapp.engine.core.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mtkresearch.breezeapp.engine.R
import com.mtkresearch.breezeapp.engine.core.Logger
import com.mtkresearch.breezeapp.engine.core.ModelManager
import com.mtkresearch.breezeapp.engine.core.download.DownloadEventManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/**
 * Foreground service for handling model downloads with progress notifications
 * 
 * This service runs in the background and provides:
 * - Persistent download notifications with progress updates
 * - Download management (pause/resume/cancel)
 * - Automatic service lifecycle management
 * - Integration with Android's notification system
 */
class ModelDownloadService : Service(), CoroutineScope {
    
    companion object {
        private const val TAG = "ModelDownloadService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "model_download_channel"
        private const val CHANNEL_NAME = "Model Downloads"
        
        // Intent actions
        const val ACTION_START_DOWNLOAD = "START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "CANCEL_DOWNLOAD"
        const val ACTION_PAUSE_DOWNLOAD = "PAUSE_DOWNLOAD"
        const val ACTION_RESUME_DOWNLOAD = "RESUME_DOWNLOAD"
        
        // Intent extras
        const val EXTRA_MODEL_ID = "model_id"
        const val EXTRA_DOWNLOAD_URL = "download_url"
        const val EXTRA_FILE_NAME = "file_name"
        
        /**
         * Start the download service for a specific model
         */
        fun startDownload(context: Context, modelId: String, downloadUrl: String, fileName: String? = null) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_MODEL_ID, modelId)
                putExtra(EXTRA_DOWNLOAD_URL, downloadUrl)
                fileName?.let { putExtra(EXTRA_FILE_NAME, it) }
            }
            ContextCompat.startForegroundService(context, intent)
        }
        
        /**
         * Cancel a download
         */
        fun cancelDownload(context: Context, modelId: String) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra(EXTRA_MODEL_ID, modelId)
            }
            context.startService(intent)
        }
    }
    
    // Coroutine scope management
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job
    
    private lateinit var logger: Logger
    private lateinit var notificationManager: NotificationManager
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var modelManager: ModelManager
    
    // Download state management
    private val downloadStates = ConcurrentHashMap<String, MutableStateFlow<DownloadState>>()
    private val downloadJobs = ConcurrentHashMap<String, Job>()
    
    override fun onCreate() {
        super.onCreate()
        logger = Logger
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        okHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(5, java.util.concurrent.TimeUnit.MINUTES)
            .writeTimeout(5, java.util.concurrent.TimeUnit.MINUTES)
            .build()
        modelManager = ModelManager.getInstance(this)
        
        createNotificationChannel()
        logger.d(TAG, "ModelDownloadService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val modelId = intent.getStringExtra(EXTRA_MODEL_ID) ?: return START_NOT_STICKY
                val downloadUrl = intent.getStringExtra(EXTRA_DOWNLOAD_URL) ?: return START_NOT_STICKY
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME)
                
                launch {
                    startDownloadInternal(modelId, downloadUrl, fileName)
                }
            }
            ACTION_CANCEL_DOWNLOAD -> {
                val modelId = intent.getStringExtra(EXTRA_MODEL_ID) ?: return START_NOT_STICKY
                launch {
                    cancelDownloadInternal(modelId)
                }
            }
            ACTION_PAUSE_DOWNLOAD -> {
                val modelId = intent.getStringExtra(EXTRA_MODEL_ID) ?: return START_NOT_STICKY
                launch {
                    // For now, pause is the same as cancel
                    cancelDownloadInternal(modelId)
                }
            }
            ACTION_RESUME_DOWNLOAD -> {
                val modelId = intent.getStringExtra(EXTRA_MODEL_ID) ?: return START_NOT_STICKY
                launch {
                    // Resume not implemented yet
                    logger.w(TAG, "Resume download not implemented for: $modelId")
                }
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        job.cancel()
        okHttpClient.dispatcher.cancelAll()
        logger.d(TAG, "ModelDownloadService destroyed")
        super.onDestroy()
    }
    
    // Public methods for managing downloads
    
    fun getDownloadFlow(modelId: String): Flow<DownloadState> {
        return getOrCreateStateFlow(modelId).asStateFlow()
    }
    
    suspend fun cancelDownloadInternal(modelId: String): Boolean {
        val job = downloadJobs[modelId]
        return if (job != null) {
            job.cancel()
            downloadJobs.remove(modelId)
            updateDownloadState(modelId) { it.withCancelled() }
            checkAndStopService()
            true
        } else {
            false
        }
    }
    
    suspend fun getDownloadStateInternal(modelId: String): DownloadState? {
        return downloadStates[modelId]?.value
    }
    
    fun getAllDownloadStatesFlow(): Flow<List<DownloadState>> {
        return flow {
            emit(downloadStates.values.map { it.value })
        }
    }
    
    suspend fun isDownloadActiveInternal(modelId: String): Boolean {
        return downloadStates[modelId]?.value?.isActive == true
    }
    
    suspend fun getActiveDownloadCountInternal(): Int {
        return downloadStates.count { (_, stateFlow) -> stateFlow.value.isActive }
    }
    
    // Private implementation methods
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for model download progress"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun getOrCreateStateFlow(modelId: String): MutableStateFlow<DownloadState> {
        return downloadStates.getOrPut(modelId) {
            MutableStateFlow(DownloadState.initial(modelId))
        }
    }
    
    private fun updateDownloadState(modelId: String, update: (DownloadState) -> DownloadState) {
        downloadStates[modelId]?.let { stateFlow ->
            stateFlow.value = update(stateFlow.value)
            updateNotification(stateFlow.value)
        }
    }
    
    private suspend fun startDownloadInternal(modelId: String, downloadUrl: String, fileName: String?) {
        val stateFlow = getOrCreateStateFlow(modelId)
        stateFlow.value = DownloadState.initial(modelId, fileName)
        
        // Start foreground service with initial notification
        val initialNotification = createProgressNotification(stateFlow.value)
        startForeground(NOTIFICATION_ID, initialNotification)
        
        // Notify download started via event manager
        DownloadEventManager.notifyDownloadStarted(this, modelId, fileName)
        
        val downloadJob = launch {
            try {
                performDownload(modelId, downloadUrl, fileName)
            } catch (e: Exception) {
                logger.e(TAG, "Download failed for model: $modelId", e)
                updateDownloadState(modelId) { it.withError(e.message ?: "Unknown error") }
                
                // Notify failure via event manager
                DownloadEventManager.notifyDownloadFailed(this@ModelDownloadService, modelId, e.message ?: "Unknown error")
                
                // Update ModelManager state for error
                try {
                    modelManager.markModelDownloadFailed(modelId, e.message ?: "Unknown error")
                    logger.d(TAG, "Notified ModelManager of download failure for: $modelId")
                } catch (ex: Exception) {
                    logger.e(TAG, "Failed to notify ModelManager of download failure", ex)
                }
            } finally {
                downloadJobs.remove(modelId)
                checkAndStopService()
            }
        }
        
        downloadJobs[modelId] = downloadJob
    }
    
    private suspend fun performDownload(modelId: String, downloadUrl: String, fileName: String?) {
        val request = Request.Builder()
            .url(downloadUrl)
            .build()
        
        val response: Response = okHttpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw IOException("Download failed with code: ${response.code}")
        }
        
        val contentLength = response.body?.contentLength() ?: -1L
        // Extract base model ID (remove _file_N suffix if present)
        val baseModelId = if (modelId.contains("_file_")) {
            modelId.substringBefore("_file_")
        } else {
            modelId
        }
        val downloadDir = File(applicationContext.filesDir, "models/$baseModelId")
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }

        val targetFile = File(downloadDir, fileName ?: "$modelId.bin")
        val sink = targetFile.sink().buffer()
        
        try {
            response.body?.source()?.let { source ->
                var totalBytesRead = 0L
                val bufferSize = 8192L
                val startTime = System.currentTimeMillis()
                var lastUpdateTime = startTime
                
                while (true) {
                    val bytesRead = source.read(sink.buffer, bufferSize)
                    if (bytesRead == -1L) break
                    
                    totalBytesRead += bytesRead
                    sink.emit()
                    
                    // Update progress every 500ms
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUpdateTime >= 500) {
                        val speed = if (currentTime > startTime) {
                            (totalBytesRead * 1000L) / (currentTime - startTime)
                        } else 0L
                        
                        updateDownloadState(modelId) { state ->
                            state.withProgress(totalBytesRead, contentLength, speed)
                        }
                        
                        // Notify progress via event manager
                        val progressPercentage = if (contentLength > 0) {
                            ((totalBytesRead * 100) / contentLength).toInt()
                        } else 0
                        DownloadEventManager.notifyDownloadProgress(
                            this@ModelDownloadService, 
                            modelId, 
                            progressPercentage, 
                            totalBytesRead, 
                            contentLength
                        )
                        lastUpdateTime = currentTime
                    }
                    
                    // Check for cancellation
                    ensureActive()
                }
                
                // Download completed - validate file content
                if (targetFile.length() < 1000 && fileName?.endsWith(".onnx") == true) {
                    // ONNX files should be much larger than 1KB - likely HTML error page
                    val content = targetFile.readText().take(100)
                    if (content.contains("<!DOCTYPE") || content.contains("<html") || content.contains("<HTML")) {
                        throw IOException("Downloaded file appears to be HTML error page, not model file: ${targetFile.name}")
                    }
                }

                updateDownloadState(modelId) { it.withCompleted() }
                logger.d(TAG, "Download completed for model: $modelId (${targetFile.length()} bytes)")

                // Notify completion via event manager
                DownloadEventManager.notifyDownloadCompleted(this@ModelDownloadService, modelId)

                // Update ModelManager state to DOWNLOADED
                try {
                    modelManager.markModelAsDownloaded(modelId)
                    logger.d(TAG, "Notified ModelManager of download completion for: $modelId")
                } catch (e: Exception) {
                    logger.e(TAG, "Failed to notify ModelManager of download completion", e)
                }
                
            } ?: throw IOException("Response body is null")
            
        } finally {
            sink.close()
        }
    }
    
    private fun updateNotification(state: DownloadState) {
        val notification = createProgressNotification(state)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun createProgressNotification(state: DownloadState): Notification {
        val cancelIntent = Intent(this, ModelDownloadService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
            putExtra(EXTRA_MODEL_ID, state.modelId)
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create intent to launch Engine Settings when user taps notification
        val viewProgressIntent = Intent(this, com.mtkresearch.breezeapp.engine.ui.EngineSettingsActivity::class.java)
        val viewProgressPendingIntent = PendingIntent.getActivity(
            this, 1, viewProgressIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Downloading ${state.fileName ?: state.modelId}")
            .setOngoing(state.isActive)
            .setContentIntent(viewProgressPendingIntent) // Main tap action - show progress UI
            .addAction(R.drawable.ic_cancel, "Cancel", cancelPendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "View Progress", viewProgressPendingIntent)
        
        when (state.status) {
            DownloadState.Status.QUEUED -> {
                builder.setContentText("Queued for download...")
                    .setProgress(0, 0, true)
            }
            DownloadState.Status.DOWNLOADING -> {
                builder.setContentText("${state.downloadedSizeFormatted} / ${state.totalSizeFormatted} • ${state.speedFormatted}")
                    .setProgress(100, state.progressPercentage, false)
            }
            DownloadState.Status.COMPLETED -> {
                builder.setContentText("Download completed • ${state.totalSizeFormatted}")
                    .setProgress(0, 0, false)
                    .setOngoing(false)
            }
            DownloadState.Status.FAILED -> {
                builder.setContentText("Download failed: ${state.errorMessage}")
                    .setProgress(0, 0, false)
                    .setOngoing(false)
            }
            DownloadState.Status.CANCELLED -> {
                builder.setContentText("Download cancelled")
                    .setProgress(0, 0, false)
                    .setOngoing(false)
            }
            DownloadState.Status.PAUSED -> {
                builder.setContentText("Download paused • ${state.downloadedSizeFormatted} / ${state.totalSizeFormatted}")
                    .setProgress(100, state.progressPercentage, false)
            }
        }
        
        return builder.build()
    }
    
    private suspend fun checkAndStopService() {
        if (getActiveDownloadCountInternal() == 0) {
            delay(2000) // Give time for UI to process final updates
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
}