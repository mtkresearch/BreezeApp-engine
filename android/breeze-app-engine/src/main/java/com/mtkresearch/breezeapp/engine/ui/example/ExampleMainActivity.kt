package com.mtkresearch.breezeapp.engine.ui.example

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.mtkresearch.breezeapp.engine.R
import com.mtkresearch.breezeapp.engine.core.download.ModelDownloadService
import com.mtkresearch.breezeapp.engine.ui.base.BaseDownloadAwareActivity

/**
 * Example activity demonstrating automatic download progress UI
 * 
 * This shows how any activity in the main BreezeApp can extend BaseDownloadAwareActivity
 * to automatically show download progress when models are downloaded in the background.
 * 
 * Usage in main BreezeApp:
 * 1. Extend BaseDownloadAwareActivity instead of AppCompatActivity
 * 2. Download progress UI will appear automatically when downloads start
 * 3. No additional code needed - it's all handled by the base class
 */
class ExampleMainActivity : BaseDownloadAwareActivity() {
    
    private lateinit var titleText: TextView
    private lateinit var triggerDownloadButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_example_main)
        
        initViews()
        setupListeners()
    }
    
    private fun initViews() {
        titleText = findViewById(R.id.titleText)
        triggerDownloadButton = findViewById(R.id.triggerDownloadButton)
        
        titleText.text = "BreezeApp Main Activity"
    }
    
    private fun setupListeners() {
        triggerDownloadButton.setOnClickListener {
            // Simulate a download triggered by user action in main app
            triggerExampleDownload()
        }
    }
    
    private fun triggerExampleDownload() {
        // Example: User triggers a feature that requires model download
        // This could be starting a chat, voice recording, etc.
        
        val modelId = "example-feature-model"
        val downloadUrl = "https://example.com/model.bin"
        val fileName = "feature-model.bin"
        
        // Start download - UI will appear automatically due to BaseDownloadAwareActivity
        ModelDownloadService.startDownload(this, modelId, downloadUrl, fileName)
        
        // Show feedback to user
        titleText.text = "Feature triggered - downloading required model..."
    }
}