package com.mtkresearch.breezeapp.engine.repository

import java.io.File
import android.content.Context
import com.mtkresearch.breezeapp.engine.domain.model.ModelFile
import com.mtkresearch.breezeapp.engine.domain.model.ModelInfo
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

interface IModelVersionStore {
    fun getDownloadedModels(): List<ModelInfo>
    fun getModelFiles(modelId: String): List<File>
    fun saveModelMetadata(modelInfo: ModelInfo): Boolean
    fun removeModel(modelId: String): Boolean
    fun getCurrentModelId(runner: String): String?
    fun setCurrentModelId(runner: String, modelId: String): Boolean
    fun validateModelFiles(modelInfo: ModelInfo): Boolean
}

class ModelVersionStore(private val context: Context) : IModelVersionStore {
    private val modelsDir: File get() = File(context.filesDir, "models")
    private val metadataFile: File get() = File(context.filesDir, "downloadedModelList.json")

    override fun getDownloadedModels(): List<ModelInfo> {
        if (!metadataFile.exists()) return emptyList()
        val json = metadataFile.readText()
        val result = mutableListOf<ModelInfo>()
        try {
            val root = JSONObject(json)
            val models = root.optJSONArray("models") ?: JSONArray()
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
                            urls = urls
                        )
                    )
                }
                result.add(
                    ModelInfo(
                        id = m.optString("id"),
                        name = m.optString("name"),
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
        } catch (e: Exception) {
            return emptyList()
        }
        return result
    }

    override fun getModelFiles(modelId: String): List<File> {
        val modelDir = File(modelsDir, modelId)
        return modelDir.listFiles()?.toList() ?: emptyList()
    }

    override fun saveModelMetadata(modelInfo: ModelInfo): Boolean {
        val models = getDownloadedModels().toMutableList()
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

    override fun removeModel(modelId: String): Boolean {
        val modelDir = File(modelsDir, modelId)
        modelDir.deleteRecursively()
        val models = getDownloadedModels().toMutableList()
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

    override fun getCurrentModelId(runner: String): String? {
        return getDownloadedModels().firstOrNull()?.id
    }

    override fun setCurrentModelId(runner: String, modelId: String): Boolean {
        return true // 可擴充為寫入偏好設定或 metadata
    }

    override fun validateModelFiles(modelInfo: ModelInfo): Boolean {
        val modelDir = File(modelsDir, modelInfo.id)
        for (file in modelInfo.files) {
            val f = File(modelDir, file.fileName ?: "")
            if (!f.exists()) {
                return false
            }
        }
        return true
    }

    private fun checkFileHash(file: File, expectedHash: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        return hash.equals(expectedHash, ignoreCase = true)
    }
} 