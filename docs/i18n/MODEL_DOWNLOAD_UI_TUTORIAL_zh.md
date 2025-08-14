# 模型下載 UI 教程

簡單指南，介紹如何在您的客戶端應用中使用 ModelManager 實作模型下載 UI。

## 1. 基本設置

### 添加到您的 Activity/Fragment
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

## 2. 創建模型項目佈局

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

## 3. 創建簡單的 Adapter

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
                    tvModelStatus.text = "可下載"
                    btnAction.text = "下載"
                    progressBar.visibility = View.GONE
                }
                ModelManager.ModelState.Status.DOWNLOADING -> {
                    tvModelStatus.text = "下載中... ${modelState.downloadProgress}%"
                    btnAction.text = "取消"
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = modelState.downloadProgress
                }
                ModelManager.ModelState.Status.DOWNLOADED -> {
                    tvModelStatus.text = "已下載"
                    btnAction.text = "刪除"
                    progressBar.visibility = View.GONE
                }
                else -> {
                    tvModelStatus.text = modelState.status.name.lowercase()
                    btnAction.text = "重試"
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

## 4. 設置 RecyclerView 並觀察狀態

```kotlin
class SettingsActivity : AppCompatActivity() {
    // ... 之前的代碼

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
                // 取消下載（實現取決於您的需求）
                Toast.makeText(this, "取消功能未實現", Toast.LENGTH_SHORT).show()
            }
            ModelManager.ModelState.Status.DOWNLOADED -> {
                deleteModel(modelState.modelInfo.id)
            }
            else -> {
                downloadModel(modelState.modelInfo.id) // 重試
            }
        }
    }

    private fun downloadModel(modelId: String) {
        modelManager.downloadModel(modelId, object : ModelManager.DownloadListener {
            override fun onCompleted(modelId: String) {
                runOnUiThread {
                    Toast.makeText(this@SettingsActivity, "下載完成", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(modelId: String, error: Throwable, fileName: String?) {
                runOnUiThread {
                    Toast.makeText(this@SettingsActivity, "下載失敗: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun deleteModel(modelId: String) {
        val success = modelManager.deleteModel(modelId)
        Toast.makeText(this, if (success) "模型已刪除" else "刪除失敗", Toast.LENGTH_SHORT).show()
    }
}
```

## 5. 基於類別的 UI（可選）

如果您想按類別組織：

```kotlin
private fun observeModelStates() {
    lifecycleScope.launch {
        modelManager.modelStates.collect { states ->
            // 獲取特定類別的模型
            val asrModels = modelManager.getAvailableModels(ModelManager.ModelCategory.ASR)
            adapter.updateModels(asrModels)
        }
    }
}
```

## 6. 下載預設模型按鈕

```kotlin
// 添加到您的 activity
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
                Toast.makeText(this@SettingsActivity, "所有必要模型已準備就緒！", Toast.LENGTH_LONG).show()
            }
        }
    })
}
```

## 7. 進階功能

### 進度追蹤
```kotlin
// 詳細進度追蹤
modelManager.downloadModel(modelId, object : ModelManager.DownloadListener {
    override fun onProgress(modelId: String, percent: Int, speed: Long, eta: Long) {
        // 使用詳細進度信息更新 UI
    }
    
    override fun onFileProgress(
        modelId: String, fileName: String, fileIndex: Int, fileCount: Int,
        bytesDownloaded: Long, totalBytes: Long, speed: Long, eta: Long
    ) {
        // 追蹤單個文件下載進度
    }
})
```

### 下載控制
```kotlin
// 獲取下載控制柄
val downloadHandle = modelManager.downloadModel(modelId)

// 稍後，您可以暫停/恢復/取消
downloadHandle.pause()
downloadHandle.resume()
downloadHandle.cancel()
```

## 就這樣！

您的應用現在具有：
- ✅ 帶下載/刪除按鈕的模型列表
- ✅ 實時進度更新
- ✅ 自動狀態管理
- ✅ 基於類別的組織
- ✅ 批量下載支持
- ✅ 詳細進度追蹤
- ✅ 下載控制（暫停/恢復/取消）

當模型狀態更改時，UI 會自動更新，所有下載都在後台通過 ModelManager 進行。