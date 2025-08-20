package com.mtkresearch.breezeapp.engine.core

import android.content.Context
import android.content.SharedPreferences
import com.mtkresearch.breezeapp.engine.model.EngineSettings
import com.mtkresearch.breezeapp.engine.model.CapabilityType
import org.json.JSONObject
import org.json.JSONException

/**
 * SharedPreferences-based StorageService implementation for persistent Engine settings.
 * 
 * This implementation properly saves and loads EngineSettings to/from Android SharedPreferences,
 * providing true persistence for runtime parameter settings.
 * 
 * Key features:
 * - Dynamic storage of runner parameters using JSON serialization
 * - Type-safe restoration with fallback handling
 * - Runner-agnostic approach supporting any parameter schema
 * - Backward compatibility with schema changes
 * 
 * Architecture Benefits:
 * - Supports any runner without code changes
 * - Parameters are defined by ParameterSchema, not hardcoded types
 * - Clean separation between storage and parameter validation
 * 
 * @param context Android context for accessing SharedPreferences
 * @param logger Logger instance for debugging and error tracking
 */
class SharedPreferencesStorageService(
    private val context: Context,
    private val logger: Logger
) : StorageService {

    companion object {
        private const val TAG = "SharedPreferencesStorageService"
        private const val PREFS_NAME = "breeze_engine_settings"
        
        // Top-level preference keys
        private const val KEY_SELECTED_RUNNERS = "selected_runners"
        private const val KEY_RUNNER_PARAMETERS = "runner_parameters"
    }

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun saveSettings(settings: EngineSettings) {
        try {
            logger.d(TAG, "Saving EngineSettings to SharedPreferences")
            
            val editor = sharedPreferences.edit()
            
            // Save selected runners as JSON
            val selectedRunnersJson = JSONObject()
            settings.selectedRunners.forEach { (capability, runnerName) ->
                selectedRunnersJson.put(capability.name, runnerName)
            }
            editor.putString(KEY_SELECTED_RUNNERS, selectedRunnersJson.toString())
            
            // Save runner parameters as nested JSON
            val runnerParametersJson = JSONObject()
            settings.runnerParameters.forEach { (runnerName, parameters) ->
                val parameterJson = JSONObject()
                parameters.forEach { (paramName, paramValue) ->
                    try {
                        when (paramValue) {
                            is String -> parameterJson.put(paramName, paramValue)
                            is Int -> parameterJson.put(paramName, paramValue)
                            is Float -> parameterJson.put(paramName, paramValue.toDouble())
                            is Double -> parameterJson.put(paramName, paramValue)
                            is Boolean -> parameterJson.put(paramName, paramValue)
                            is Long -> parameterJson.put(paramName, paramValue)
                            else -> parameterJson.put(paramName, paramValue.toString())
                        }
                    } catch (e: JSONException) {
                        logger.w(TAG, "Failed to serialize parameter $paramName for runner $runnerName: $paramValue")
                    }
                }
                runnerParametersJson.put(runnerName, parameterJson)
            }
            editor.putString(KEY_RUNNER_PARAMETERS, runnerParametersJson.toString())
            
            // Apply changes
            val success = editor.commit()
            if (success) {
                logger.d(TAG, "EngineSettings saved successfully: ${settings.selectedRunners.size} runners, ${settings.runnerParameters.size} parameter sets")
            } else {
                logger.e(TAG, "Failed to commit EngineSettings to SharedPreferences")
            }
            
        } catch (e: Exception) {
            logger.e(TAG, "Error saving EngineSettings to SharedPreferences", e)
        }
    }

    override fun loadSettings(): EngineSettings {
        return try {
            logger.d(TAG, "Loading EngineSettings from SharedPreferences")
            
            // Load selected runners
            val selectedRunners = mutableMapOf<CapabilityType, String>()
            val selectedRunnersString = sharedPreferences.getString(KEY_SELECTED_RUNNERS, null)
            if (!selectedRunnersString.isNullOrEmpty()) {
                try {
                    val selectedRunnersJson = JSONObject(selectedRunnersString)
                    CapabilityType.values().forEach { capability ->
                        if (selectedRunnersJson.has(capability.name)) {
                            val runnerName = selectedRunnersJson.getString(capability.name)
                            if (runnerName.isNotEmpty()) {
                                selectedRunners[capability] = runnerName
                            }
                        }
                    }
                } catch (e: JSONException) {
                    logger.w(TAG, "Failed to parse selected runners JSON, using defaults")
                }
            }
            
            // Load runner parameters
            val runnerParameters = mutableMapOf<String, Map<String, Any>>()
            val runnerParametersString = sharedPreferences.getString(KEY_RUNNER_PARAMETERS, null)
            if (!runnerParametersString.isNullOrEmpty()) {
                try {
                    val runnerParametersJson = JSONObject(runnerParametersString)
                    val runnerNames = runnerParametersJson.keys()
                    while (runnerNames.hasNext()) {
                        val runnerName = runnerNames.next()
                        val parameterJson = runnerParametersJson.getJSONObject(runnerName)
                        val parameters = mutableMapOf<String, Any>()
                        
                        val paramNames = parameterJson.keys()
                        while (paramNames.hasNext()) {
                            val paramName = paramNames.next()
                            try {
                                val value = parameterJson.get(paramName)
                                when (value) {
                                    is String -> parameters[paramName] = value
                                    is Int -> parameters[paramName] = value
                                    is Double -> {
                                        // Handle Float/Double conversion
                                        val floatValue = value.toFloat()
                                        if (floatValue == value) {
                                            parameters[paramName] = floatValue
                                        } else {
                                            parameters[paramName] = value
                                        }
                                    }
                                    is Boolean -> parameters[paramName] = value
                                    is Long -> parameters[paramName] = value
                                    else -> parameters[paramName] = value.toString()
                                }
                            } catch (e: Exception) {
                                logger.w(TAG, "Failed to parse parameter $paramName for runner $runnerName")
                            }
                        }
                        
                        if (parameters.isNotEmpty()) {
                            runnerParameters[runnerName] = parameters
                        }
                    }
                } catch (e: JSONException) {
                    logger.w(TAG, "Failed to parse runner parameters JSON, using defaults")
                }
            }
            
            val settings = EngineSettings(
                selectedRunners = selectedRunners,
                runnerParameters = runnerParameters
            )
            
            logger.d(TAG, "EngineSettings loaded successfully: ${selectedRunners.size} selected runners, ${runnerParameters.size} parameter sets")
            settings
            
        } catch (e: Exception) {
            logger.e(TAG, "Error loading EngineSettings from SharedPreferences, returning defaults", e)
            EngineSettings.default()
        }
    }
}