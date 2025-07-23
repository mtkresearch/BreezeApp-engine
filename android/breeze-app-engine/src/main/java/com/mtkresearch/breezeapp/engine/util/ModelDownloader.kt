package com.mtkresearch.breezeapp.engine.util

import android.content.Context
import com.mtkresearch.breezeapp.engine.domain.model.ModelInfo
import com.mtkresearch.breezeapp.engine.repository.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

object ModelDownloader {
    private const val MODEL_LIST_ASSET = "fullModelList.json"

    fun listAvailableModels(context: Context): List<ModelInfo> {
        val registry = ModelRegistry(context)
        return registry.listAllModels()
    }

    fun getModelInfo(context: Context, modelId: String): ModelInfo? {
        val registry = ModelRegistry(context)
        return registry.getModelInfo(modelId)
    }

    fun downloadModel(
        context: Context,
        modelId: String,
        listener: ModelManager.DownloadListener,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    ) {
        val modelInfo = getModelInfo(context, modelId)
        if (modelInfo == null) {
            listener.onError(modelId, IllegalArgumentException("Model $modelId not found in fullModelList.json"))
            return
        }
        scope.launch {
            val registry = ModelRegistry(context)
            val versionStore = ModelVersionStore(context)
            val manager = ModelManagerImpl(context, registry, versionStore)
            manager.downloadModel(modelId, listener)
        }
    }

    fun downloadDefaultModel(
        context: Context,
        listener: ModelManager.DownloadListener,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    ) {
        val models = listAvailableModels(context)
        if (models.isEmpty()) {
            listener.onError("", IllegalStateException("No models found in fullModelList.json"))
            return
        }
        downloadModel(context, models.first().id, listener, scope)
    }
} 