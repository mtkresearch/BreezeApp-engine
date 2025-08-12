package com.mtkresearch.breezeapp.engine.util

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig

/**
 * Utility for creating Sherpa TTS configurations
 * Supports various TTS model types including VITS, Matcha, and Kokoro
 */
object SherpaTtsConfigUtil {
    
    /**
     * TTS Model types supported
     */
    enum class TtsModelType {
        VITS_MR_20250709,       // Your custom model
        VITS_MELO_ZH_EN,        // VITS MeloTTS model
        VITS_PIPER_EN_US_AMY,   // English Piper model
        VITS_ICEFALL_ZH,        // Chinese VITS model
        MATCHA_ICEFALL_ZH,      // Chinese Matcha model
        KOKORO_EN,              // English Kokoro model
        CUSTOM                  // Custom configuration
    }
    
    /**
     * TTS Model configuration data class
     */
    data class TtsModelConfig(
        val modelDir: String,
        val modelName: String = "",
        val acousticModelName: String = "",
        val vocoder: String = "",
        val voices: String = "",
        val lexicon: String = "",
        val dataDir: String = "",
        val dictDir: String = "",
        val ruleFsts: String = "",
        val ruleFars: String = "",
        val description: String = ""
    )
    
    /**
     * Get predefined TTS model configuration
     */
    fun getTtsModelConfig(type: TtsModelType): TtsModelConfig {
        return when (type) {
            TtsModelType.VITS_MR_20250709 -> TtsModelConfig(
                modelDir = "vits-mr-20250709",
                modelName = "vits-mr-20250709.onnx",
                lexicon = "lexicon.txt",
                description = "VITS MR custom TTS model (2025-07-09)"
            )
            
            TtsModelType.VITS_MELO_ZH_EN -> TtsModelConfig(
                modelDir = "vits-melo-tts-zh_en",
                modelName = "model.onnx",
                lexicon = "lexicon.txt",
                dictDir = "vits-melo-tts-zh_en/dict",
                description = "VITS MeloTTS Chinese-English bilingual model"
            )
            
            TtsModelType.VITS_PIPER_EN_US_AMY -> TtsModelConfig(
                modelDir = "vits-piper-en_US-amy-low",
                modelName = "en_US-amy-low.onnx",
                dataDir = "vits-piper-en_US-amy-low/espeak-ng-data",
                description = "VITS Piper English Amy voice"
            )
            
            TtsModelType.VITS_ICEFALL_ZH -> TtsModelConfig(
                modelDir = "vits-icefall-zh-aishell3",
                modelName = "model.onnx",
                ruleFars = "vits-icefall-zh-aishell3/rule.far",
                lexicon = "lexicon.txt",
                description = "VITS Icefall Chinese AISHELL3 model"
            )
            
            TtsModelType.MATCHA_ICEFALL_ZH -> TtsModelConfig(
                modelDir = "matcha-icefall-zh-baker",
                acousticModelName = "model-steps-3.onnx",
                vocoder = "vocos-22khz-univ.onnx",
                lexicon = "lexicon.txt",
                dictDir = "matcha-icefall-zh-baker/dict",
                description = "Matcha Icefall Chinese Baker model"
            )
            
            TtsModelType.KOKORO_EN -> TtsModelConfig(
                modelDir = "kokoro-en-v0_19",
                modelName = "model.onnx",
                voices = "voices.bin",
                dataDir = "kokoro-en-v0_19/espeak-ng-data",
                description = "Kokoro English model"
            )
            
            TtsModelType.CUSTOM -> TtsModelConfig(
                modelDir = "",
                description = "Custom TTS model configuration"
            )
        }
    }
    
    /**
     * Create Sherpa OfflineTtsConfig from model configuration
     * Uses the official Sherpa getOfflineTtsConfig function
     * Handles asset copying and path resolution
     */
    fun createOfflineTtsConfig(
        context: Context,
        modelConfig: TtsModelConfig,
        useExternalStorage: Boolean = true
    ): com.k2fsa.sherpa.onnx.OfflineTtsConfig? {
        return try {
            var finalDataDir = ""
            var finalDictDir = ""
            var finalRuleFsts = modelConfig.ruleFsts
            
            // Handle data directory copying
            if (modelConfig.dataDir.isNotEmpty()) {
                val newDir = if (useExternalStorage) {
                    EngineUtils.copyAssetsToExternalFiles(context, modelConfig.dataDir)
                } else {
                    EngineUtils.copyAssetsToInternalFiles(context, modelConfig.dataDir)
                }
                finalDataDir = "$newDir/${modelConfig.dataDir}"
            }
            
            // Handle dictionary directory copying
            if (modelConfig.dictDir.isNotEmpty()) {
                val newDir = if (useExternalStorage) {
                    EngineUtils.copyAssetsToExternalFiles(context, modelConfig.dictDir)
                } else {
                    EngineUtils.copyAssetsToInternalFiles(context, modelConfig.dictDir)
                }
                finalDictDir = "$newDir/${modelConfig.dictDir}"
                
                // Set default rule FSTs if not specified
                if (finalRuleFsts.isEmpty()) {
                    finalRuleFsts = "${modelConfig.modelDir}/phone.fst,${modelConfig.modelDir}/date.fst,${modelConfig.modelDir}/number.fst"
                }
            }
            
            // Use the official Sherpa getOfflineTtsConfig function
            com.k2fsa.sherpa.onnx.getOfflineTtsConfig(
                modelDir = modelConfig.modelDir,
                modelName = modelConfig.modelName,
                acousticModelName = modelConfig.acousticModelName,
                vocoder = modelConfig.vocoder,
                voices = modelConfig.voices,
                lexicon = modelConfig.lexicon,
                dataDir = finalDataDir,
                dictDir = finalDictDir,
                ruleFsts = finalRuleFsts,
                ruleFars = modelConfig.ruleFars
            )
        } catch (e: Exception) {
            android.util.Log.e("SherpaTtsConfigUtil", "Failed to create TTS config", e)
            null
        }
    }
    
    /**
     * Create custom TTS configuration using official Sherpa function
     */
    fun createCustomConfig(
        context: Context,
        modelDir: String,
        modelName: String = "",
        acousticModelName: String = "",
        vocoder: String = "",
        voices: String = "",
        lexicon: String = "",
        dataDir: String = "",
        dictDir: String = "",
        ruleFsts: String = "",
        ruleFars: String = "",
        useExternalStorage: Boolean = true
    ): com.k2fsa.sherpa.onnx.OfflineTtsConfig? {
        val customConfig = TtsModelConfig(
            modelDir = modelDir,
            modelName = modelName,
            acousticModelName = acousticModelName,
            vocoder = vocoder,
            voices = voices,
            lexicon = lexicon,
            dataDir = dataDir,
            dictDir = dictDir,
            ruleFsts = ruleFsts,
            ruleFars = ruleFars,
            description = "Custom TTS configuration"
        )
        
        return createOfflineTtsConfig(context, customConfig, useExternalStorage)
    }
    
    /**
     * Get all available model configurations
     */
    fun getAllModelConfigs(): List<Pair<TtsModelType, TtsModelConfig>> {
        return TtsModelType.values().filter { it != TtsModelType.CUSTOM }.map { type ->
            type to getTtsModelConfig(type)
        }
    }
    
    /**
     * Validate if model assets exist in the assets folder
     */
    fun validateModelAssets(context: Context, modelConfig: TtsModelConfig): Boolean {
        return try {
            val assetManager = context.assets
            
            // Check if model directory exists
            val modelDirAssets = assetManager.list(modelConfig.modelDir)
            if (modelDirAssets.isNullOrEmpty()) {
                android.util.Log.w("SherpaTtsConfigUtil", "Model directory not found: ${modelConfig.modelDir}")
                return false
            }
            
            // Check required files
            val requiredFiles = mutableListOf<String>()
            
            if (modelConfig.modelName.isNotEmpty()) {
                requiredFiles.add("${modelConfig.modelDir}/${modelConfig.modelName}")
            }
            
            if (modelConfig.lexicon.isNotEmpty()) {
                requiredFiles.add("${modelConfig.modelDir}/${modelConfig.lexicon}")
            }
            
            if (modelConfig.voices.isNotEmpty()) {
                requiredFiles.add("${modelConfig.modelDir}/${modelConfig.voices}")
            }
            
            // Validate each required file
            for (file in requiredFiles) {
                try {
                    assetManager.open(file).close()
                } catch (e: Exception) {
                    android.util.Log.w("SherpaTtsConfigUtil", "Required file not found: $file")
                    return false
                }
            }
            
            true
        } catch (e: Exception) {
            android.util.Log.e("SherpaTtsConfigUtil", "Error validating model assets", e)
            false
        }
    }
}