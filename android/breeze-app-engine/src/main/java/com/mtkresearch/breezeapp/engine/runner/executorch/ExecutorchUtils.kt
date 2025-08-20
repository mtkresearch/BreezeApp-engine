package com.mtkresearch.breezeapp.engine.runner.executorch

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

object ExecutorchUtils {
    private const val TAG = "ExecutorchUtils"

    data class ModelPaths(val modelPath: String, val tokenizerPath: String)

    fun resolveModelPaths(context: Context, modelId: String): ModelPaths? {
        return try {
            val baseDir = "${context.filesDir.absolutePath}/models/$modelId"
            Log.d(TAG, "Resolving paths from base directory: $baseDir")

            // Find model entry and tokenizer from fullModelList.json
            val jsonString = context.assets.open("fullModelList.json").bufferedReader().use { it.readText() }
            val json = JSONObject(jsonString)
            val models = json.getJSONArray("models")
            var modelFileName: String? = null
            var tokenizerFileName: String? = null

            for (i in 0 until models.length()) {
                val model = models.getJSONObject(i)
                if (model.getString("id") == modelId && model.getString("runner") == "executorch") {
                    // Extract model file name from entry_point
                    val entryPoint = model.getJSONObject("entry_point")
                    when (entryPoint.getString("type")) {
                        "file" -> {
                            modelFileName = entryPoint.getString("value")
                        }
                        else -> {
                            Log.w(TAG, "Unsupported entry point type '${entryPoint.getString("type")}' for model: $modelId")
                            // For unsupported types, we might still be able to find a model file in the files array
                            val files = model.getJSONArray("files")
                            for (j in 0 until files.length()) {
                                val file = files.getJSONObject(j)
                                if (file.optString("type") == "model") {
                                    modelFileName = file.optString("fileName")
                                    if (modelFileName.isNotEmpty()) {
                                        break
                                    }
                                }
                            }
                        }
                    }

                    // Find tokenizer file from files array
                    val files = model.getJSONArray("files")
                    for (j in 0 until files.length()) {
                        val file = files.getJSONObject(j)
                        if (file.optString("type") == "tokenizer") {
                            tokenizerFileName = file.optString("fileName")
                            if (tokenizerFileName.isNotEmpty()) {
                                break
                            }
                        }
                    }
                    break
                }
            }

            if (modelFileName == null || tokenizerFileName == null) {
                Log.e(TAG, "Could not find model entry or tokenizer for modelId: $modelId in fullModelList.json")
                return null
            }

            val modelFile = File(baseDir, modelFileName)
            val tokenizerFile = File(baseDir, tokenizerFileName)

            if (!modelFile.exists()) {
                Log.e(TAG, "Model file does not exist: ${modelFile.absolutePath}")
                return null
            }
            if (!tokenizerFile.exists()) {
                Log.e(TAG, "Tokenizer file does not exist: ${tokenizerFile.absolutePath}")
                return null
            }

            ModelPaths(modelFile.absolutePath, tokenizerFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving default model paths for $modelId", e)
            null
        }
    }
}
