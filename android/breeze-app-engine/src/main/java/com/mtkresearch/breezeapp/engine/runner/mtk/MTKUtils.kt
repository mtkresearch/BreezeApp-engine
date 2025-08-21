package com.mtkresearch.breezeapp.engine.runner.mtk

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File

/**
 * MTK Runner Utilities
 * 
 * Simple utility class for MTK-specific functionality.
 */
object MTKUtils {
    
    private val MTK_NPU_CHIPSETS = setOf("mt6991", "mt6989", "mt6988")
    
    /**
     * Check if MTK NPU is supported on current device
     */
    fun isMTKNPUSupported(): Boolean {
        val hardware = Build.HARDWARE.lowercase()
        return MTK_NPU_CHIPSETS.any { hardware.contains(it) }
    }
    
    
    
    /**
     * Find MTK model ID from fullModelList.json by looking for runner="mediatek"
     */
    private fun findMTKModelId(context: Context): String {
        val json = context.assets.open("fullModelList.json").bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val models = root.optJSONArray("models")
        
        if (models != null) {
            for (i in 0 until models.length()) {
                val model = models.getJSONObject(i)
                if (model.optString("runner") == "mediatek") {
                    return model.optString("id")
                }
            }
        }
        
        throw IllegalStateException("No MTK model found in fullModelList.json")
    }

    /**
     * Resolves the absolute path to the model's entry point file.
     *
     * @param modelDef The definition of the model.
     * @param context The application context to access the filesystem.
     * @return The absolute path to the entry point file, or null if not found.
     */
    fun resolveModelPath(modelDef: com.mtkresearch.breezeapp.engine.model.ModelDefinition, context: Context): String? {
        val modelsDir = File(context.filesDir, "models")
        val modelDir = File(modelsDir, modelDef.id)

        if (!modelDir.exists() || !modelDir.isDirectory) {
            android.util.Log.e("MTKUtils", "Model directory does not exist: ${modelDir.absolutePath}")
            return null
        }

        val entryPointFile = modelDef.entryPoint?.value
        if (entryPointFile.isNullOrEmpty()) {
            android.util.Log.e("MTKUtils", "ModelDefinition for ${modelDef.id} has no entryPoint defined.")
            return null
        }

        val modelFile = File(modelDir, entryPointFile)
        if (!modelFile.exists()) {
            android.util.Log.e("MTKUtils", "EntryPoint file does not exist: ${modelFile.absolutePath}")
            return null
        }

        return modelFile.absolutePath
    }
}