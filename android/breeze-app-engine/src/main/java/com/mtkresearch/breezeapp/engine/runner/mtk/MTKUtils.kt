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
     * Resolve MTK model path with smart fallback handling.
     * 
     * This function follows the same pattern as Sherpa's getModelConfig() approach:
     * 1. If explicit modelPath provided and valid, use it
     * 2. Otherwise, automatically find MTK model from fullModelList.json
     * 
     * @param context Android context for accessing assets
     * @param explicitPath Optional explicit model path (can be null/blank)
     * @return Resolved model path for MTK runner
     */
    fun resolveModelPath(context: Context, explicitPath: String? = null): String {
        // If explicit path provided and not blank, use it
        if (!explicitPath.isNullOrBlank()) {
            return explicitPath
        }
        
        // Otherwise, find default MTK model from fullModelList.json
        val mtkModelId = findMTKModelId(context)
        val baseDir = "/data/user/0/com.mtkresearch.breezeapp.engine/files/models"
        val modelDir = "$baseDir/$mtkModelId"
        return "$modelDir/config_breezetiny_3b_instruct.yaml"
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
}