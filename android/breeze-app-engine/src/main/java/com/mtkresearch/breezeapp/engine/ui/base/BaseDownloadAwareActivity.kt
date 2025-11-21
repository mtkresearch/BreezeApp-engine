package com.mtkresearch.breezeapp.engine.ui.base

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.mtkresearch.breezeapp.engine.core.Logger
import com.mtkresearch.breezeapp.engine.core.download.DownloadEventManager
import com.mtkresearch.breezeapp.engine.ui.download.DownloadProgressBottomSheet

/**
 * Base activity that automatically handles download progress UI
 * 
 * Any activity that extends this class will automatically show a download progress
 * bottom sheet whenever a download starts, regardless of how it was triggered.
 */
abstract class BaseDownloadAwareActivity : AppCompatActivity() {
    
    private companion object {
        private const val TAG = "BaseDownloadAwareActivity"
    }
    
    private val logger = Logger
    private var downloadProgressBottomSheet: DownloadProgressBottomSheet? = null
    
    private val downloadEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            logger.d(TAG, "BroadcastReceiver.onReceive() called with action: ${intent?.action}")
            when (intent?.action) {
                DownloadEventManager.ACTION_SHOW_DOWNLOAD_UI,
                DownloadEventManager.ACTION_DOWNLOAD_STARTED -> {
                    val modelId = intent.getStringExtra(DownloadEventManager.EXTRA_MODEL_ID)
                    val fileName = intent.getStringExtra(DownloadEventManager.EXTRA_FILE_NAME) ?: "model"
                    logger.d(TAG, "Received request to show download UI for model: $modelId, fileName: $fileName")
                    showDownloadProgressUI()
                }
                else -> {
                    logger.d(TAG, "Received broadcast action: ${intent?.action}")
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger.d(TAG, "BaseDownloadAwareActivity.onCreate() - registering download event receiver")
        registerDownloadEventReceiver()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        unregisterDownloadEventReceiver()
        dismissDownloadProgressUI()
    }
    
    override fun onResume() {
        super.onResume()
        // Re-register receiver in case it was unregistered
        registerDownloadEventReceiver()
    }
    
    override fun onPause() {
        super.onPause()
        // Keep receiver registered so we can show UI when returning to activity
    }
    
    private fun registerDownloadEventReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(DownloadEventManager.ACTION_SHOW_DOWNLOAD_UI)
                addAction(DownloadEventManager.ACTION_DOWNLOAD_STARTED)
            }
            
            LocalBroadcastManager.getInstance(this)
                .registerReceiver(downloadEventReceiver, filter)
            
            logger.d(TAG, "Download event receiver registered")
        } catch (e: Exception) {
            logger.e(TAG, "Failed to register download event receiver", e)
        }
    }
    
    private fun unregisterDownloadEventReceiver() {
        try {
            LocalBroadcastManager.getInstance(this)
                .unregisterReceiver(downloadEventReceiver)
            
            logger.d(TAG, "Download event receiver unregistered")
        } catch (e: Exception) {
            logger.e(TAG, "Failed to unregister download event receiver", e)
        }
    }
    
    private fun showDownloadProgressUI() {
        try {
            // Don't show multiple bottom sheets
            if (downloadProgressBottomSheet?.isVisible == true) {
                logger.d(TAG, "Download progress bottom sheet already visible")
                return
            }
            
            downloadProgressBottomSheet = DownloadProgressBottomSheet.show(this, supportFragmentManager)
            logger.d(TAG, "Download progress bottom sheet shown")
            
        } catch (e: Exception) {
            logger.e(TAG, "Failed to show download progress bottom sheet", e)
        }
    }
    
    private fun dismissDownloadProgressUI() {
        try {
            downloadProgressBottomSheet?.dismiss()
            downloadProgressBottomSheet = null
            logger.d(TAG, "Download progress bottom sheet dismissed")
        } catch (e: Exception) {
            logger.e(TAG, "Failed to dismiss download progress bottom sheet", e)
        }
    }
    
    /**
     * Manually trigger download progress UI
     * Useful for testing or specific use cases
     */
    protected fun requestDownloadProgressUI() {
        showDownloadProgressUI()
    }
}