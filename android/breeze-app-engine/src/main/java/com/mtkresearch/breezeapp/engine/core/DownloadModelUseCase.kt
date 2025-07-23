package com.mtkresearch.breezeapp.engine.core

import android.content.Context
import com.mtkresearch.breezeapp.engine.repository.ModelManager
import com.mtkresearch.breezeapp.engine.repository.ModelVersionStore
import com.mtkresearch.breezeapp.engine.util.ModelDownloader

/**
 * Use case for orchestrating model download and readiness in BreezeAppEngine.
 *
 * Usage:
 *   val useCase = DownloadModelUseCase(context)
 *   useCase.ensureDefaultModelReady(listener) // Ensures default model is present or downloads it
 *   useCase.downloadModel(modelId, listener) // Downloads a specific model on demand
 *
 * The listener receives progress, completion, and error callbacks.
 */
class DownloadModelUseCase(private val context: Context) {
    /**
     * Ensures the default model (first in fullModelList.json) is present.
     * If not, triggers download. If already present, immediately calls onCompleted.
     *
     * @param listener ModelManager.DownloadListener for progress and completion callbacks.
     */
    fun ensureDefaultModelReady(listener: ModelManager.DownloadListener) {
        val versionStore = ModelVersionStore(context)
        val defaultModel = ModelDownloader.listAvailableModels(context).firstOrNull()
        val alreadyDownloaded = defaultModel?.let { model ->
            versionStore.getDownloadedModels().any { it.id == model.id }
        } ?: false

        if (!alreadyDownloaded && defaultModel != null) {
            ModelDownloader.downloadDefaultModel(context, listener)
        } else {
            // If already downloaded, immediately notify completion
            defaultModel?.let { listener.onCompleted(it.id) }
        }
    }

    /**
     * Downloads a specific model by modelId, with progress and error callbacks.
     *
     * @param modelId The id of the model to download (must exist in fullModelList.json)
     * @param listener ModelManager.DownloadListener for progress and completion callbacks.
     */
    fun downloadModel(modelId: String, listener: ModelManager.DownloadListener) {
        ModelDownloader.downloadModel(context, modelId, listener)
    }
} 