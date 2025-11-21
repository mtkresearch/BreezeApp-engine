package com.mtkresearch.breezeapp.engine.ui.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mtkresearch.breezeapp.engine.R
import com.mtkresearch.breezeapp.engine.core.Logger
import com.mtkresearch.breezeapp.engine.core.download.DownloadState
import com.mtkresearch.breezeapp.engine.core.download.DownloadEventManager
import com.mtkresearch.breezeapp.engine.core.download.ModelDownloadService
import kotlinx.coroutines.launch

/**
 * Bottom sheet dialog for displaying download progress
 * 
 * This UI component provides an elegant in-app display of download progress
 * with real-time updates, individual download management, and smooth animations.
 */
class DownloadProgressBottomSheet : BottomSheetDialogFragment() {
    
    companion object {
        private const val TAG = "DownloadProgressBottomSheet"
        
        /**
         * Create and show the download progress bottom sheet
         */
        fun show(context: Context, fragmentManager: androidx.fragment.app.FragmentManager): DownloadProgressBottomSheet {
            val bottomSheet = DownloadProgressBottomSheet()
            bottomSheet.show(fragmentManager, TAG)
            return bottomSheet
        }
    }
    
    private lateinit var logger: Logger
    
    // UI components
    private lateinit var textTitle: TextView
    private lateinit var btnClose: Button
    private lateinit var containerDownloadItems: LinearLayout
    private lateinit var btnCancelAll: Button
    private lateinit var btnMinimize: Button
    
    // Download item views cache and state tracking
    private val downloadItemViews = mutableMapOf<String, View>()
    private val downloadStates = mutableMapOf<String, DownloadState>()
    
    // Broadcast receiver for download events
    private val downloadEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { handleDownloadEvent(it) }
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_download_progress, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        logger = Logger
        logger.d(TAG, "DownloadProgressBottomSheet onViewCreated() called")
        
        try {
            // Initialize UI components
            initializeViews(view)
            logger.d(TAG, "UI views initialized successfully")
            
            setupClickListeners()
            logger.d(TAG, "Click listeners set up successfully")
            
            // Connect to download service via broadcast receiver
            registerDownloadEventReceiver()
            logger.d(TAG, "Download event receiver registered successfully")
            
            // Initialize with any existing downloads (if any)
            initializeExistingDownloads()
            logger.d(TAG, "Existing downloads initialized successfully")
            
        } catch (e: Exception) {
            logger.e(TAG, "Error in DownloadProgressBottomSheet onViewCreated()", e)
            throw e
        }
    }
    
    private fun initializeViews(view: View) {
        textTitle = view.findViewById(R.id.textTitle)
        btnClose = view.findViewById(R.id.btnClose)
        containerDownloadItems = view.findViewById(R.id.containerDownloadItems)
        btnCancelAll = view.findViewById(R.id.btnCancelAll)
        btnMinimize = view.findViewById(R.id.btnMinimize)
    }
    
    private fun setupClickListeners() {
        btnClose.setOnClickListener {
            dismiss()
        }
        
        btnMinimize.setOnClickListener {
            dismiss()
        }
        
        btnCancelAll.setOnClickListener {
            cancelAllDownloads()
        }
    }
    
    private fun registerDownloadEventReceiver() {
        val filter = IntentFilter().apply {
            addAction(DownloadEventManager.ACTION_DOWNLOAD_STARTED)
            addAction(DownloadEventManager.ACTION_DOWNLOAD_PROGRESS)
            addAction(DownloadEventManager.ACTION_DOWNLOAD_COMPLETED)
            addAction(DownloadEventManager.ACTION_DOWNLOAD_FAILED)
        }
        
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(downloadEventReceiver, filter)
            
        logger.d(TAG, "Registered download event receiver")
    }
    
    private fun initializeExistingDownloads() {
        // Show initial state (empty downloads)
        updateDownloadList(downloadStates.values.toList())
        logger.d(TAG, "Initialized with ${downloadStates.size} existing downloads")
    }
    
    private fun handleDownloadEvent(intent: Intent) {
        val modelId = intent.getStringExtra(DownloadEventManager.EXTRA_MODEL_ID) ?: return
        
        when (intent.action) {
            DownloadEventManager.ACTION_DOWNLOAD_STARTED -> {
                val fileName = intent.getStringExtra(DownloadEventManager.EXTRA_FILE_NAME)
                val newState = DownloadState.initial(modelId, fileName)
                downloadStates[modelId] = newState
                logger.d(TAG, "Download started for model: $modelId")
            }
            
            DownloadEventManager.ACTION_DOWNLOAD_PROGRESS -> {
                val progressPercentage = intent.getIntExtra(DownloadEventManager.EXTRA_PROGRESS_PERCENTAGE, 0)
                val downloadedBytes = intent.getLongExtra(DownloadEventManager.EXTRA_DOWNLOADED_BYTES, 0L)
                val totalBytes = intent.getLongExtra(DownloadEventManager.EXTRA_TOTAL_BYTES, 0L)
                
                val currentState = downloadStates[modelId]
                if (currentState != null) {
                    val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                    downloadStates[modelId] = currentState.copy(
                        status = DownloadState.Status.DOWNLOADING,
                        progress = progress,
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes
                    )
                    logger.d(TAG, "Download progress for model $modelId: $progressPercentage%")
                }
            }
            
            DownloadEventManager.ACTION_DOWNLOAD_COMPLETED -> {
                val currentState = downloadStates[modelId]
                if (currentState != null) {
                    downloadStates[modelId] = currentState.withCompleted()
                    logger.d(TAG, "Download completed for model: $modelId")
                }
            }
            
            DownloadEventManager.ACTION_DOWNLOAD_FAILED -> {
                val errorMessage = intent.getStringExtra(DownloadEventManager.EXTRA_ERROR_MESSAGE) ?: "Unknown error"
                val currentState = downloadStates[modelId]
                if (currentState != null) {
                    downloadStates[modelId] = currentState.withError(errorMessage)
                    logger.d(TAG, "Download failed for model $modelId: $errorMessage")
                }
            }
        }
        
        // Update UI with new states
        updateDownloadList(downloadStates.values.toList())
    }
    
    private fun updateDownloadList(downloadStates: List<DownloadState>) {
        // Remove finished downloads that are no longer needed
        downloadItemViews.entries.removeAll { (modelId, _) ->
            downloadStates.none { it.modelId == modelId }
        }
        
        // Update title based on download count
        val activeCount = downloadStates.count { it.isActive }
        textTitle.text = when {
            activeCount == 0 && downloadStates.isNotEmpty() -> "Downloads Complete"
            activeCount == 1 -> "Downloading 1 Model"
            activeCount > 1 -> "Downloading $activeCount Models"
            else -> "No Active Downloads"
        }
        
        // Show/hide cancel all button
        btnCancelAll.visibility = if (activeCount > 1) View.VISIBLE else View.GONE
        
        // Update or create download item views
        downloadStates.forEach { state ->
            val itemView = downloadItemViews.getOrPut(state.modelId) {
                createDownloadItemView(state)
            }
            updateDownloadItemView(itemView, state)
        }
        
        // Auto-dismiss if no downloads remain
        if (downloadStates.isEmpty()) {
            dismiss()
        }
    }
    
    private fun createDownloadItemView(state: DownloadState): View {
        val inflater = LayoutInflater.from(context)
        val itemView = inflater.inflate(R.layout.item_download_progress, containerDownloadItems, false)
        
        // Set up cancel button
        val btnCancel: ImageButton = itemView.findViewById(R.id.btnCancel)
        btnCancel.setOnClickListener {
            cancelDownload(state.modelId)
        }
        
        containerDownloadItems.addView(itemView)
        return itemView
    }
    
    private fun updateDownloadItemView(itemView: View, state: DownloadState) {
        val iconStatus: ImageView = itemView.findViewById(R.id.iconStatus)
        val textModelName: TextView = itemView.findViewById(R.id.textModelName)
        val textProgress: TextView = itemView.findViewById(R.id.textProgress)
        val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
        val textStatus: TextView = itemView.findViewById(R.id.textStatus)
        val btnCancel: ImageButton = itemView.findViewById(R.id.btnCancel)
        
        // Update model name
        textModelName.text = state.fileName ?: state.modelId
        
        // Update progress
        textProgress.text = "${state.progressPercentage}%"
        progressBar.progress = state.progressPercentage
        progressBar.isIndeterminate = state.status == DownloadState.Status.QUEUED
        
        // Update status icon and text
        when (state.status) {
            DownloadState.Status.QUEUED -> {
                iconStatus.setImageResource(R.drawable.ic_queued)
                textStatus.text = "Queued for download..."
                btnCancel.visibility = View.VISIBLE
            }
            DownloadState.Status.DOWNLOADING -> {
                iconStatus.setImageResource(R.drawable.ic_download)
                textStatus.text = "${state.downloadedSizeFormatted} / ${state.totalSizeFormatted} • ${state.speedFormatted}"
                btnCancel.visibility = View.VISIBLE
            }
            DownloadState.Status.PAUSED -> {
                iconStatus.setImageResource(R.drawable.ic_pause)
                textStatus.text = "Paused • ${state.downloadedSizeFormatted} / ${state.totalSizeFormatted}"
                btnCancel.visibility = View.VISIBLE
            }
            DownloadState.Status.COMPLETED -> {
                iconStatus.setImageResource(R.drawable.ic_check_circle)
                textStatus.text = "Download completed • ${state.totalSizeFormatted}"
                btnCancel.visibility = View.GONE
            }
            DownloadState.Status.FAILED -> {
                iconStatus.setImageResource(R.drawable.ic_error)
                textStatus.text = "Failed: ${state.errorMessage}"
                btnCancel.visibility = View.GONE
            }
            DownloadState.Status.CANCELLED -> {
                iconStatus.setImageResource(R.drawable.ic_cancel)
                textStatus.text = "Download cancelled"
                btnCancel.visibility = View.GONE
            }
        }
    }
    
    private fun cancelDownload(modelId: String) {
        lifecycleScope.launch {
            try {
                ModelDownloadService.cancelDownload(requireContext(), modelId)
                logger.d(TAG, "Cancelled download for model: $modelId")
            } catch (e: Exception) {
                logger.e(TAG, "Failed to cancel download for model: $modelId", e)
                Toast.makeText(context, "Failed to cancel download", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun cancelAllDownloads() {
        lifecycleScope.launch {
            try {
                downloadItemViews.keys.forEach { modelId ->
                    ModelDownloadService.cancelDownload(requireContext(), modelId)
                }
                logger.d(TAG, "Cancelled all downloads")
                Toast.makeText(context, "All downloads cancelled", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                logger.e(TAG, "Failed to cancel all downloads", e)
                Toast.makeText(context, "Failed to cancel downloads", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onDestroy() {
        // Unregister broadcast receiver
        try {
            val context = context
            if (context != null) {
                LocalBroadcastManager.getInstance(context)
                    .unregisterReceiver(downloadEventReceiver)
                logger.d(TAG, "Unregistered download event receiver")
            } else {
                logger.w(TAG, "Context is null, cannot unregister download event receiver")
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error unregistering download event receiver", e)
        }
        super.onDestroy()
    }
}