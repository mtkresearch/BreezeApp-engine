# Model Download UI Tutorial

Simple guide to implement model download UI in your client app using ModelManager.

## 1. Basic Setup

### Add to your Activity/Fragment
```kotlin
class SettingsActivity : AppCompatActivity() {
    private lateinit var modelManager: ModelManager
    private lateinit var adapter: ModelListAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        modelManager = ModelManager.getInstance(this)
        setupRecyclerView()
        observeModelStates()
    }
}
```

## 2. Create Model Item Layout

### layout/item_model.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:padding="16dp">

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/tvModelName"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="16sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/tvModelStatus"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="14sp"
            android:textColor="@android:color/darker_gray" />

        <ProgressBar
            android:id="@+id/progressBar"
            style="?android:attr/progressBarStyleHorizontal"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:visibility="gone" />

    </LinearLayout>

    <Button
        android:id="@+id/btnAction"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="16dp" />

</LinearLayout>
```

## 3. Create Simple Adapter

```kotlin
class ModelListAdapter : RecyclerView.Adapter<ModelListAdapter.ViewHolder>() {
    private var models = listOf<ModelManager.ModelState>()
    var onActionClick: ((ModelManager.ModelState) -> Unit)? = null

    fun updateModels(newModels: List<ModelManager.ModelState>) {
        models = newModels
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_model, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(models[position])
    }

    override fun getItemCount() = models.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvModelName: TextView = itemView.findViewById(R.id.tvModelName)
        private val tvModelStatus: TextView = itemView.findViewById(R.id.tvModelStatus)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
        private val btnAction: Button = itemView.findViewById(R.id.btnAction)

        fun bind(modelState: ModelManager.ModelState) {
            tvModelName.text = modelState.modelInfo.name
            
            when (modelState.status) {
                ModelManager.ModelState.Status.AVAILABLE -> {
                    tvModelStatus.text = "Available for download"
                    btnAction.text = "Download"
                    progressBar.visibility = View.GONE
                }
                ModelManager.ModelState.Status.DOWNLOADING -> {
                    tvModelStatus.text = "Downloading... ${modelState.downloadProgress}%"
                    btnAction.text = "Cancel"
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = modelState.downloadProgress
                }
                ModelManager.ModelState.Status.DOWNLOADED -> {
                    tvModelStatus.text = "Downloaded"
                    btnAction.text = "Delete"
                    progressBar.visibility = View.GONE
                }
                else -> {
                    tvModelStatus.text = modelState.status.name.lowercase()
                    btnAction.text = "Retry"
                    progressBar.visibility = View.GONE
                }
            }

            btnAction.setOnClickListener {
                onActionClick?.invoke(modelState)
            }
        }
    }
}
```

## 4. Setup RecyclerView and Observe States

```kotlin
class SettingsActivity : AppCompatActivity() {
    // ... previous code

    private fun setupRecyclerView() {
        adapter = ModelListAdapter()
        adapter.onActionClick = { modelState ->
            handleModelAction(modelState)
        }
        
        val recyclerView: RecyclerView = findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun observeModelStates() {
        lifecycleScope.launch {
            modelManager.modelStates.collect { states ->
                val modelsList = states.values.toList()
                    .sortedBy { it.category.name }
                adapter.updateModels(modelsList)
            }
        }
    }

    private fun handleModelAction(modelState: ModelManager.ModelState) {
        when (modelState.status) {
            ModelManager.ModelState.Status.AVAILABLE -> {
                downloadModel(modelState.modelInfo.id)
            }
            ModelManager.ModelState.Status.DOWNLOADING -> {
                // Cancel download (implementation depends on your needs)
                Toast.makeText(this, "Cancel not implemented", Toast.LENGTH_SHORT).show()
            }
            ModelManager.ModelState.Status.DOWNLOADED -> {
                deleteModel(modelState.modelInfo.id)
            }
            else -> {
                downloadModel(modelState.modelInfo.id) // Retry
            }
        }
    }

    private fun downloadModel(modelId: String) {
        modelManager.downloadModel(modelId, object : ModelManager.DownloadListener {
            override fun onCompleted(modelId: String) {
                runOnUiThread {
                    Toast.makeText(this@SettingsActivity, "Download completed", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(modelId: String, error: Throwable, fileName: String?) {
                runOnUiThread {
                    Toast.makeText(this@SettingsActivity, "Download failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun deleteModel(modelId: String) {
        val success = modelManager.deleteModel(modelId)
        Toast.makeText(this, if (success) "Model deleted" else "Delete failed", Toast.LENGTH_SHORT).show()
    }
}
```

## 5. Category-Based UI (Optional)

If you want to organize by categories:

```kotlin
private fun observeModelStates() {
    lifecycleScope.launch {
        modelManager.modelStates.collect { states ->
            // Get models for a specific category
            val asrModels = modelManager.getAvailableModels(ModelManager.ModelCategory.ASR)
            adapter.updateModels(asrModels)
        }
    }
}
```

## 6. Download Default Models Button

```kotlin
// Add this to your activity
private fun downloadEssentials() {
    val essentialCategories = listOf(
        ModelManager.ModelCategory.LLM,
        ModelManager.ModelCategory.ASR
    )
    
    modelManager.downloadDefaultModels(essentialCategories, object : ModelManager.BulkDownloadListener {
        override fun onModelCompleted(modelId: String, success: Boolean) {
            runOnUiThread {
                Toast.makeText(this@SettingsActivity, 
                    "$modelId: ${if (success) "✓" else "✗"}", Toast.LENGTH_SHORT).show()
            }
        }
        
        override fun onAllCompleted() {
            runOnUiThread {
                Toast.makeText(this@SettingsActivity, "All essential models ready!", Toast.LENGTH_LONG).show()
            }
        }
    })
}
```

## 7. Advanced Features

### Progress Tracking
```kotlin
// Detailed progress tracking
modelManager.downloadModel(modelId, object : ModelManager.DownloadListener {
    override fun onProgress(modelId: String, percent: Int, speed: Long, eta: Long) {
        // Update UI with detailed progress information
    }
    
    override fun onFileProgress(
        modelId: String, fileName: String, fileIndex: Int, fileCount: Int,
        bytesDownloaded: Long, totalBytes: Long, speed: Long, eta: Long
    ) {
        // Track individual file download progress
    }
})
```

### Download Control
```kotlin
// Get a download handle for control
val downloadHandle = modelManager.downloadModel(modelId)

// Later, you can pause/resume/cancel
downloadHandle.pause()
downloadHandle.resume()
downloadHandle.cancel()
```

## That's it!

Your app now has:
- ✅ Model list with download/delete buttons
- ✅ Real-time progress updates  
- ✅ Automatic state management
- ✅ Category-based organization
- ✅ Bulk download support
- ✅ Detailed progress tracking
- ✅ Download control (pause/resume/cancel)

The UI automatically updates when model states change, and all downloads happen in the background through the ModelManager.