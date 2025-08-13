package com.mtkresearch.breezeapp.engine.core

import android.content.Context
import com.mtkresearch.breezeapp.engine.model.ModelInfo
import com.mtkresearch.breezeapp.engine.model.ModelFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Model Manager - Handles all model-related operations
 * 
 * Single class that handles all model-related operations:
 * - Model registry and discovery
 * - Download management with progress tracking  
 * - Local storage and metadata management
 * - Category-based organization
 * - State management and notifications
 * 
 * Benefits:
 * - Simplified API surface with clear responsibilities
 * - Better resource management and consistency
 * - Easier testing and maintenance
 */
class ModelManager private constructor(
    private val context: Context
) {
    companion object {
        @Volatile
        private var INSTANCE: ModelManager? = null
        
        fun getInstance(context: Context): ModelManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // === Core Dependencies ===
    private val modelsDir: File = File(context.filesDir, "models")
    private val metadataFile: File = File(context.filesDir, "downloadedModelList.json")
    private val assetModelList = "fullModelList.json"
    
    // === Coroutine Scope ===
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // === State Management ===
    private val _modelStates = MutableStateFlow<Map<String, ModelState>>(emptyMap())
    val modelStates: StateFlow<Map<String, ModelState>> = _modelStates.asStateFlow()
    
    private val activeDownloads = ConcurrentHashMap<String, DownloadJob>()
    private val availableModels = mutableListOf<ModelInfo>()
    
    // === Status Manager Integration ===
    private var statusManager: BreezeAppEngineStatusManager? = null
    
    init {
        modelsDir.mkdirs()
        loadAvailableModels()
        refreshModelStates()
    }
    
    // === Model Categories ===
    enum class ModelCategory(val key: String) {
        LLM("llm"),
        ASR("asr"), 
        TTS("tts"),
        VLM("vlm"),
        UNKNOWN("unknown")
    }
    
    // === Model State ===
    data class ModelState(
        val modelInfo: ModelInfo,
        val status: Status,
        val downloadProgress: Int = 0,
        val downloadSpeed: Long = 0,
        val downloadEta: Long = -1,
        val storageSize: Long = 0,
        val category: ModelCategory = ModelCategory.UNKNOWN,
        val isDefault: Boolean = false,
        val lastUpdated: Long = System.currentTimeMillis(),
        val errorMessage: String? = null
    ) {
        enum class Status {
            AVAILABLE,      // Available for download
            DOWNLOADING,    // Currently downloading
            DOWNLOADED,     // Successfully downloaded
            PAUSED,        // Download paused
            ERROR,         // Download/validation error
            INSTALLING,    // Post-download validation/setup
            READY          // Ready for inference
        }
    }
    
    // === Download Job ===
    private data class DownloadJob(
        val modelId: String,
        val job: Job,
        val isPaused: AtomicBoolean = AtomicBoolean(false)
    ) {
        fun cancel() = job.cancel()
        fun pause() = isPaused.set(true)
        fun resume() = isPaused.set(false)
        fun isPaused() = isPaused.get()
    }
    
    // === Configuration ===
    fun setStatusManager(statusManager: BreezeAppEngineStatusManager) {
        this.statusManager = statusManager
    }
    
    // === Public API - Model Discovery ===
    
    /**
     * Get all available models for a category
     */
    fun getAvailableModels(category: ModelCategory): List<ModelState> {
        return _modelStates.value.values
            .filter { it.category == category }
            .sortedWith(compareBy<ModelState> { !it.isDefault }.thenBy { it.modelInfo.name })
    }
    
    /**
     * Get downloaded models for a category
     */
    fun getDownloadedModels(category: ModelCategory): List<ModelState> {
        return getAvailableModels(category)
            .filter { it.status in setOf(ModelState.Status.DOWNLOADED, ModelState.Status.READY) }
    }
    
    /**
     * Get default model for a category
     */
    fun getDefaultModel(category: ModelCategory): ModelState? {
        return getAvailableModels(category).find { it.isDefault }
    }
    
    /**
     * Get model state by ID
     */
    fun getModelState(modelId: String): ModelState? {
        return _modelStates.value[modelId]
    }
    
    /**
     * Get all models organized by category
     */
    fun getModelsByCategory(): Map<ModelCategory, List<ModelState>> {
        return _modelStates.value.values
            .groupBy { it.category }
            .withDefault { emptyList() }
    }
    
    // === Public API - Download Operations ===
    
    /**
     * Download a specific model
     */
    fun downloadModel(
        modelId: String,
        listener: DownloadListener? = null
    ): DownloadHandle {
        val handle = DownloadHandle(modelId, this)
        
        val job = managerScope.launch {
            try {
                updateModelStatus(modelId, ModelState.Status.DOWNLOADING)
                
                val modelInfo = availableModels.find { it.id == modelId }
                if (modelInfo == null) {
                    listener?.onError(modelId, IllegalArgumentException("Model not found: $modelId"), null)
                    return@launch
                }
                
                // Update engine status
                statusManager?.setDownloading(modelInfo.name, 0)
                
                val success = downloadModelFiles(modelInfo, listener)
                
                if (success) {
                    if (validateModelFiles(modelInfo)) {
                        saveModelMetadata(modelInfo)
                        updateModelStatus(modelId, ModelState.Status.DOWNLOADED)
                        statusManager?.setReady()
                        listener?.onCompleted(modelId)
                    } else {
                        removeModel(modelId)
                        updateModelStatus(modelId, ModelState.Status.ERROR, "Validation failed")
                        statusManager?.setReady()
                        listener?.onError(modelId, IllegalStateException("Model validation failed"), null)
                    }
                } else {
                    updateModelStatus(modelId, ModelState.Status.ERROR, "Download failed")
                    statusManager?.setReady()
                }
                
            } catch (e: Exception) {
                updateModelStatus(modelId, ModelState.Status.ERROR, e.message)
                statusManager?.setReady()
                listener?.onError(modelId, e, null)
            } finally {
                activeDownloads.remove(modelId)
            }
        }
        
        activeDownloads[modelId] = DownloadJob(modelId, job)
        return handle
    }
    
    /**
     * Ensure default model for category is ready
     */
    fun ensureDefaultModelReady(
        category: ModelCategory,
        listener: DownloadListener? = null
    ) {
        val defaultModel = getDefaultModel(category)
        if (defaultModel == null) {
            listener?.onError("", IllegalStateException("No default model found for category $category"), null)
            return
        }
        
        when (defaultModel.status) {
            ModelState.Status.DOWNLOADED, ModelState.Status.READY -> {
                listener?.onCompleted(defaultModel.modelInfo.id)
            }
            ModelState.Status.DOWNLOADING -> {
                // Already downloading - could attach listener if needed
            }
            else -> {
                downloadModel(defaultModel.modelInfo.id, listener)
            }
        }
    }
    
    /**
     * Download all default models for specified categories
     */
    fun downloadDefaultModels(
        categories: List<ModelCategory>,
        listener: BulkDownloadListener? = null
    ) {
        val modelsToDownload = categories.mapNotNull { getDefaultModel(it) }
            .filter { it.status !in setOf(ModelState.Status.DOWNLOADED, ModelState.Status.READY) }
        
        if (modelsToDownload.isEmpty()) {
            listener?.onAllCompleted()
            return
        }
        
        val results = ConcurrentHashMap<String, Boolean>()
        
        modelsToDownload.forEach { model ->
            downloadModel(model.modelInfo.id, object : DownloadListener {
                override fun onCompleted(modelId: String) {
                    results[modelId] = true
                    listener?.onModelCompleted(modelId, true)
                    
                    if (results.size == modelsToDownload.size) {
                        listener?.onAllCompleted()
                    }
                }
                
                override fun onError(modelId: String, error: Throwable, fileName: String?) {
                    results[modelId] = false
                    listener?.onModelCompleted(modelId, false)
                    
                    if (results.size == modelsToDownload.size) {
                        listener?.onAllCompleted()
                    }
                }
            })
        }
    }
    
    /**
     * Delete a model
     */
    fun deleteModel(modelId: String): Boolean {
        // Cancel any active download
        activeDownloads[modelId]?.cancel()
        activeDownloads.remove(modelId)
        
        val success = removeModel(modelId)
        if (success) {
            updateModelStatus(modelId, ModelState.Status.AVAILABLE)
            refreshModelStates()
        }
        return success
    }
    
    // === Download Control ===
    
    fun pauseDownload(modelId: String): Boolean {
        activeDownloads[modelId]?.pause()
        updateModelStatus(modelId, ModelState.Status.PAUSED)
        return true
    }
    
    fun resumeDownload(modelId: String): Boolean {
        activeDownloads[modelId]?.resume()
        updateModelStatus(modelId, ModelState.Status.DOWNLOADING)
        return true
    }
    
    fun cancelDownload(modelId: String): Boolean {
        activeDownloads[modelId]?.cancel()
        activeDownloads.remove(modelId)
        updateModelStatus(modelId, ModelState.Status.AVAILABLE)
        return true
    }
    
    // === Storage Management ===
    
    fun calculateTotalStorageUsed(): Long {
        return modelsDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }
    
    fun getStorageUsageByCategory(): Map<ModelCategory, Long> {
        return getModelsByCategory().mapValues { (_, models) ->
            models.filter { it.status in setOf(ModelState.Status.DOWNLOADED, ModelState.Status.READY) }
                .sumOf { it.storageSize }
        }
    }
    
    fun cleanupStorage(): StorageCleanupResult {
        val beforeSize = calculateTotalStorageUsed()
        
        // Clean up temporary files
        val tempFiles = modelsDir.listFiles()
            ?.filter { it.name.endsWith(".part") }
            ?.sumOf { it.length() } ?: 0L
            
        modelsDir.listFiles()
            ?.filter { it.name.endsWith(".part") }
            ?.forEach { it.delete() }
        
        val afterSize = calculateTotalStorageUsed()
        
        return StorageCleanupResult(
            spaceFreed = beforeSize - afterSize,
            tempFilesRemoved = tempFiles,
            modelsCleanedUp = 0 // Could be enhanced
        )
    }
    
    // === Private Implementation ===
    
    private fun loadAvailableModels() {
        try {
            val json = context.assets.open(assetModelList).bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val models = root.optJSONArray("models") ?: JSONArray()
            
            availableModels.clear()
            for (i in 0 until models.length()) {
                val m = models.getJSONObject(i)
                val filesArr = m.optJSONArray("files") ?: JSONArray()
                val files = mutableListOf<ModelFile>()
                
                for (j in 0 until filesArr.length()) {
                    val f = filesArr.getJSONObject(j)
                    files.add(
                        ModelFile(
                            fileName = f.optString("fileName", null),
                            group = f.optString("group", null),
                            pattern = f.optString("pattern", null),
                            type = f.optString("type", "model"),
                            urls = f.optJSONArray("urls")?.let { arr ->
                                List(arr.length()) { arr.getString(it) }
                            }?.toList() ?: emptyList()
                        )
                    )
                }
                
                val entryPoint = m.optJSONObject("entry_point")
                availableModels.add(
                    ModelInfo(
                        id = m.optString("id"),
                        name = m.optString("name", m.optString("id")).ifEmpty { m.optString("id") },
                        version = m.optString("version", ""),
                        runner = m.optString("runner"),
                        files = files,
                        backend = m.optString("backend"),
                        ramGB = m.optInt("ramGB", 0),
                        entryPointType = entryPoint?.optString("type"),
                        entryPointValue = entryPoint?.optString("value")
                    )
                )
            }
        } catch (e: Exception) {
            // Handle gracefully
        }
    }
    
    private fun refreshModelStates() {
        val downloadedModels = getDownloadedModelsFromStorage().associateBy { it.id }
        
        val states = availableModels.associate { model ->
            val category = determineCategory(model)
            val isDownloaded = downloadedModels.containsKey(model.id)
            val storageSize = if (isDownloaded) calculateModelStorageSize(model.id) else 0L
            
            model.id to ModelState(
                modelInfo = model,
                status = if (isDownloaded) ModelState.Status.DOWNLOADED else ModelState.Status.AVAILABLE,
                category = category,
                isDefault = isDefaultModel(model, category),
                storageSize = storageSize
            )
        }
        
        _modelStates.value = states
    }
    
    private fun determineCategory(model: ModelInfo): ModelCategory {
        return when {
            model.runner.contains("asr", ignoreCase = true) -> ModelCategory.ASR
            model.runner.contains("tts", ignoreCase = true) -> ModelCategory.TTS
            model.runner.contains("vlm", ignoreCase = true) -> ModelCategory.VLM
            model.runner in setOf("executorch", "mediatek") -> ModelCategory.LLM
            else -> ModelCategory.UNKNOWN
        }
    }
    
    private fun isDefaultModel(model: ModelInfo, category: ModelCategory): Boolean {
        return when (category) {
            ModelCategory.ASR -> model.id == "Breeze-ASR-25-onnx"
            ModelCategory.LLM -> model.id == "Breeze2-3B-8W16A-250630-npu"
            ModelCategory.TTS -> model.id == "vits-mr-20250709"
            else -> false
        }
    }
    
    private fun calculateModelStorageSize(modelId: String): Long {
        val modelDir = File(modelsDir, modelId)
        return if (modelDir.exists()) {
            modelDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else 0L
    }
    
    private fun updateModelStatus(
        modelId: String, 
        status: ModelState.Status,
        errorMessage: String? = null
    ) {
        val currentStates = _modelStates.value.toMutableMap()
        currentStates[modelId]?.let { currentState ->
            currentStates[modelId] = currentState.copy(
                status = status,
                errorMessage = errorMessage,
                lastUpdated = System.currentTimeMillis()
            )
        }
        _modelStates.value = currentStates
    }
    
    private fun updateModelProgress(modelId: String, percent: Int, speed: Long, eta: Long) {
        val currentStates = _modelStates.value.toMutableMap()
        currentStates[modelId]?.let { currentState ->
            currentStates[modelId] = currentState.copy(
                downloadProgress = percent,
                downloadSpeed = speed,
                downloadEta = eta,
                lastUpdated = System.currentTimeMillis()
            )
        }
        _modelStates.value = currentStates
    }
    
    private suspend fun downloadModelFiles(
        modelInfo: ModelInfo,
        listener: DownloadListener?
    ): Boolean {
        val modelDir = File(modelsDir, modelInfo.id)
        if (!modelDir.exists()) modelDir.mkdirs()
        
        val downloadTasks = expandFiles(modelInfo.files)
        val fileCount = downloadTasks.size
        
        for ((fileIndex, task) in downloadTasks.withIndex()) {
            val fileName = task.fileName
            val url = task.url
            
            val success = downloadSingleFile(
                modelDir, fileName, url, modelInfo.id, 
                fileIndex, fileCount, listener
            )
            
            if (!success) return false
            
            val percent = ((fileIndex + 1) * 100 / fileCount)
            updateModelProgress(modelInfo.id, percent, 0L, -1L)
            listener?.onProgress(modelInfo.id, percent, 0L, -1L)
        }
        
        return true
    }
    
    private suspend fun downloadSingleFile(
        modelDir: File,
        fileName: String,
        url: String,
        modelId: String,
        fileIndex: Int,
        fileCount: Int,
        listener: DownloadListener?
    ): Boolean = withContext(Dispatchers.IO) {
        val destFile = File(modelDir, fileName)
        val tmpFile = File(destFile.absolutePath + ".part")
        
        try {
            val downloadJob = activeDownloads[modelId]
            
            var downloadedBytes = if (tmpFile.exists()) tmpFile.length() else 0L
            val connection = URL(url).openConnection()
            
            if (downloadedBytes > 0) {
                connection.setRequestProperty("Range", "bytes=$downloadedBytes-")
            }
            
            connection.connect()
            val input = connection.getInputStream()
            val output = FileOutputStream(tmpFile, true)
            val buffer = ByteArray(8192)
            var total = downloadedBytes
            var read: Int
            var lastReportTime = System.currentTimeMillis()
            var lastBytes = total
            
            while (input.read(buffer).also { read = it } != -1) {
                // Check for cancellation or pause
                if (downloadJob?.job?.isCancelled == true) {
                    break
                }
                
                while (downloadJob?.isPaused() == true && downloadJob.job.isActive) {
                    delay(100)
                }
                
                output.write(buffer, 0, read)
                total += read
                
                val now = System.currentTimeMillis()
                if (now - lastReportTime > 500) {
                    val speed = if (now > lastReportTime) {
                        ((total - lastBytes) * 1000 / (now - lastReportTime))
                    } else 0L
                    
                    listener?.onFileProgress(
                        modelId, fileName, fileIndex, fileCount,
                        total, 0L, speed, -1L
                    )
                    
                    lastReportTime = now
                    lastBytes = total
                }
            }
            
            output.close()
            input.close()
            
            if (downloadJob?.job?.isCancelled != true) {
                tmpFile.renameTo(destFile)
                listener?.onFileCompleted(modelId, fileName, fileIndex, fileCount)
                true
            } else {
                tmpFile.delete()
                false
            }
            
        } catch (e: Exception) {
            tmpFile.delete()
            listener?.onError(modelId, e, fileName)
            false
        }
    }
    
    private fun validateModelFiles(modelInfo: ModelInfo): Boolean {
        val modelDir = File(modelsDir, modelInfo.id)
        for (file in modelInfo.files) {
            val f = File(modelDir, file.fileName ?: "")
            if (!f.exists()) {
                return false
            }
        }
        return true
    }
    
    private fun saveModelMetadata(modelInfo: ModelInfo): Boolean {
        val models = getDownloadedModelsFromStorage().toMutableList()
        models.removeAll { it.id == modelInfo.id }
        models.add(modelInfo)
        
        val arr = JSONArray()
        for (m in models) {
            val mObj = JSONObject()
            mObj.put("id", m.id)
            mObj.put("name", m.name)
            mObj.put("version", m.version)
            mObj.put("runner", m.runner)
            mObj.put("backend", m.backend)
            mObj.put("ramGB", m.ramGB)
            mObj.put("entryPointType", m.entryPointType)
            mObj.put("entryPointValue", m.entryPointValue)
            
            val filesArr = JSONArray()
            for (f in m.files) {
                val fObj = JSONObject()
                fObj.put("fileName", f.fileName)
                fObj.put("group", f.group)
                fObj.put("pattern", f.pattern)
                fObj.put("type", f.type)
                val urlsArr = JSONArray()
                f.urls.forEach { urlsArr.put(it) }
                fObj.put("urls", urlsArr)
                filesArr.put(fObj)
            }
            mObj.put("files", filesArr)
            arr.put(mObj)
        }
        
        val root = JSONObject()
        root.put("models", arr)
        metadataFile.writeText(root.toString(2))
        return true
    }
    
    private fun removeModel(modelId: String): Boolean {
        val modelDir = File(modelsDir, modelId)
        modelDir.deleteRecursively()
        
        val models = getDownloadedModelsFromStorage().toMutableList()
        val removed = models.removeAll { it.id == modelId }
        
        if (removed) {
            val arr = JSONArray()
            for (m in models) {
                val mObj = JSONObject()
                mObj.put("id", m.id)
                mObj.put("name", m.name)
                mObj.put("version", m.version)
                mObj.put("runner", m.runner)
                mObj.put("backend", m.backend)
                mObj.put("ramGB", m.ramGB)
                mObj.put("entryPointType", m.entryPointType)
                mObj.put("entryPointValue", m.entryPointValue)
                
                val filesArr = JSONArray()
                for (f in m.files) {
                    val fObj = JSONObject()
                    fObj.put("fileName", f.fileName)
                    fObj.put("group", f.group)
                    fObj.put("pattern", f.pattern)
                    fObj.put("type", f.type)
                    val urlsArr = JSONArray()
                    f.urls.forEach { urlsArr.put(it) }
                    fObj.put("urls", urlsArr)
                    filesArr.put(fObj)
                }
                mObj.put("files", filesArr)
                arr.put(mObj)
            }
            
            val root = JSONObject()
            root.put("models", arr)
            metadataFile.writeText(root.toString(2))
        }
        
        return removed
    }
    
    private fun getDownloadedModelsFromStorage(): List<ModelInfo> {
        if (!metadataFile.exists()) return emptyList()
        
        return try {
            val json = metadataFile.readText()
            val root = JSONObject(json)
            val models = root.optJSONArray("models") ?: JSONArray()
            val result = mutableListOf<ModelInfo>()
            
            for (i in 0 until models.length()) {
                val m = models.getJSONObject(i)
                val filesArr = m.optJSONArray("files") ?: JSONArray()
                val files = mutableListOf<ModelFile>()
                
                for (j in 0 until filesArr.length()) {
                    val f = filesArr.getJSONObject(j)
                    val urlsArr = f.optJSONArray("urls") ?: JSONArray()
                    val urls = mutableListOf<String>()
                    for (k in 0 until urlsArr.length()) {
                        urls.add(urlsArr.getString(k))
                    }
                    files.add(
                        ModelFile(
                            fileName = f.optString("fileName", null),
                            group = f.optString("group", null),
                            pattern = f.optString("pattern", null),
                            type = f.optString("type", "model"),
                            urls = urls.toList()
                        )
                    )
                }
                
                result.add(
                    ModelInfo(
                        id = m.optString("id"),
                        name = m.optString("name").ifEmpty { m.optString("id") },
                        version = m.optString("version", ""),
                        runner = m.optString("runner"),
                        files = files,
                        backend = m.optString("backend"),
                        ramGB = m.optInt("ramGB", 0),
                        entryPointType = m.optString("entryPointType", null),
                        entryPointValue = m.optString("entryPointValue", null)
                    )
                )
            }
            
            result
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private data class DownloadTask(val fileName: String, val url: String, val type: String)
    
    private fun expandFiles(modelFiles: List<ModelFile>): List<DownloadTask> {
        val tasks = mutableListOf<DownloadTask>()
        for (file in modelFiles) {
            if (file.urls.isNotEmpty()) {
                if (file.fileName != null) {
                    tasks.add(DownloadTask(file.fileName, file.urls.first(), file.type))
                } else {
                    for (url in file.urls) {
                        val fileName = url.substringAfterLast('/').substringBefore('?')
                        tasks.add(DownloadTask(fileName, url, file.type))
                    }
                }
            }
        }
        return tasks
    }
    
    // === Helper Classes ===
    
    data class DownloadHandle(
        val modelId: String,
        private val manager: ModelManager
    ) {
        fun cancel() = manager.cancelDownload(modelId)
        fun pause() = manager.pauseDownload(modelId)
        fun resume() = manager.resumeDownload(modelId)
    }
    
    data class StorageCleanupResult(
        val spaceFreed: Long,
        val tempFilesRemoved: Long,
        val modelsCleanedUp: Int
    )
    
    // === Listener Interfaces ===
    
    interface DownloadListener {
        fun onProgress(modelId: String, percent: Int, speed: Long, eta: Long) = Unit
        fun onCompleted(modelId: String) = Unit
        fun onError(modelId: String, error: Throwable, fileName: String?) = Unit
        fun onPaused(modelId: String) = Unit
        fun onResumed(modelId: String) = Unit
        fun onCancelled(modelId: String) = Unit
        fun onFileProgress(
            modelId: String, fileName: String, fileIndex: Int, fileCount: Int,
            bytesDownloaded: Long, totalBytes: Long, speed: Long, eta: Long
        ) = Unit
        fun onFileCompleted(modelId: String, fileName: String, fileIndex: Int, fileCount: Int) = Unit
    }
    
    interface BulkDownloadListener {
        fun onModelCompleted(modelId: String, success: Boolean)
        fun onAllCompleted()
    }
}