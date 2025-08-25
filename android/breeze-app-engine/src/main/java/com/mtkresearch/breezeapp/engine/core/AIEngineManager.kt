package com.mtkresearch.breezeapp.engine.core

import android.app.ActivityManager
import android.content.Context
import com.mtkresearch.breezeapp.engine.runner.core.BaseRunner
import com.mtkresearch.breezeapp.engine.runner.core.FlowStreamingRunner
import com.mtkresearch.breezeapp.engine.model.CapabilityType
import com.mtkresearch.breezeapp.engine.model.EngineSettings
import com.mtkresearch.breezeapp.engine.model.InferenceRequest
import com.mtkresearch.breezeapp.engine.model.InferenceResult
import com.mtkresearch.breezeapp.engine.model.RunnerError
import com.mtkresearch.breezeapp.engine.model.ModelDefinition
import com.mtkresearch.breezeapp.engine.runner.core.RunnerManager
import com.mtkresearch.breezeapp.engine.service.ModelRegistryService
import com.mtkresearch.breezeapp.engine.runner.guardian.GuardianPipeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * AIEngineManager
 *
 * Use Case 層的核心業務邏輯，負責：
 * 1. Runner 的註冊、選擇和管理
 * 2. 推論請求的分發和處理
 * 3. Fallback 機制的實現
 * 4. 並發請求的處理
 *
 * 遵循 Clean Architecture 和 MVVM + Use Case 模式
 */

// Private exception to wrap our custom RunnerError for use with Kotlin's Result type.
private class RunnerSelectionException(val runnerError: RunnerError) : Exception()

// NOTE: In a production environment, these helpers would be in their own files
// and likely injected via a dependency injection framework.
// For this change, they are included here for simplicity.

private interface SystemResourceMonitor {
    fun getAvailableRamGB(): Float
}

private class SystemResourceMonitorImpl(private val context: Context) : SystemResourceMonitor {
    override fun getAvailableRamGB(): Float {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            memoryInfo.availMem / (1024.0f * 1024.0f * 1024.0f)
        } catch (e: Exception) {
            // Return a safe value in case of error
            0.0f
        }
    }
}

class AIEngineManager(
    private val context: Context,
    private val runnerManager: RunnerManager,
    private val modelRegistryService: ModelRegistryService,
    private val logger: Logger
) {

    companion object {
        private const val TAG = "AIEngineManager"
    }

    private val systemResourceMonitor: SystemResourceMonitor = SystemResourceMonitorImpl(context)

    // 執行緒安全的 Runner 儲存
    private val activeRunners = ConcurrentHashMap<String, BaseRunner>()
    
    // 讀寫鎖保護配置變更
    private val configLock = ReentrantReadWriteLock()

    // 使用統一的取消管理器
    private val cancellationManager = CancellationManager.getInstance()
    
    // Guardian 管道處理器
    private val guardianPipeline = GuardianPipeline(runnerManager, logger)

    /**
     * 處理推論請求
     * @param request 推論請求
     * @param capability 所需能力
     * @param preferredRunner 偏好的 Runner (可選)
     * @return 推論結果
     */
    suspend fun process(
        request: InferenceRequest,
        capability: CapabilityType,
        preferredRunner: String? = null
    ): InferenceResult {
        val requestId = request.sessionId ?: "request-${System.currentTimeMillis()}"

        // 使用統一的取消管理器追蹤請求
        val currentJob = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
        currentJob?.let { job ->
            cancellationManager.registerRequest(requestId, job)
        }

        return try {
            // 1. Get effective guardian configuration
            val baseGuardianConfig = runnerManager.getCurrentSettings().guardianConfig
            val effectiveGuardianConfig = guardianPipeline.createEffectiveConfig(baseGuardianConfig, request)
            
            // 2. Input Guardian Check (if enabled)
            if (effectiveGuardianConfig.shouldCheckInput()) {
                logger.d(TAG, "Performing guardian input validation for request $requestId")
                val inputCheckResult = guardianPipeline.checkInput(request, effectiveGuardianConfig)
                if (inputCheckResult.shouldBlock()) {
                    logger.w(TAG, "Request $requestId blocked by guardian input validation")
                    return inputCheckResult.toInferenceResult()
                }
                if (inputCheckResult.shouldWarn()) {
                    logger.w(TAG, "Request $requestId triggered guardian warning during input validation")
                }
            }
            
            // 3. Normal AI Processing
            val aiResult = selectAndLoadRunner(request, capability, preferredRunner).fold(
                onSuccess = { runner ->
                    logger.d(TAG, "Processing request $requestId with runner: ${runner.getRunnerInfo().name}")
                    logger.d(TAG, "Runtime params for $requestId: ${request.params}")
                    runner.run(request)
                },
                onFailure = { error ->
                    val runnerError = if (error is RunnerSelectionException) error.runnerError else RunnerError("E101", error.message ?: "Unknown runtime error")
                    InferenceResult.error(runnerError)
                }
            )
            
            // 4. Output Guardian Check (if enabled and AI succeeded)
            if (effectiveGuardianConfig.shouldCheckOutput() && aiResult.error == null) {
                logger.d(TAG, "Performing guardian output filtering for request $requestId")
                val outputCheckResult = guardianPipeline.checkOutput(aiResult, effectiveGuardianConfig)
                return outputCheckResult.applyToResult(aiResult)
            }
            
            aiResult
            
        } catch (e: Exception) {
            logger.e(TAG, "Error processing request", e)
            InferenceResult.error(RunnerError("E101", e.message ?: "Unknown error", true, e))
        } finally {
            // 使用統一的取消管理器清理請求
            cancellationManager.unregisterRequest(requestId)
        }
    }

    /**
     * 處理串流推論請求
     * @param request 推論請求
     * @param capability 所需能力
     * @param preferredRunner 偏好的 Runner (可選)
     * @return 推論結果的 Flow
     */
    fun processStream(
        request: InferenceRequest,
        capability: CapabilityType,
        preferredRunner: String? = null
    ): Flow<InferenceResult> = flow {
        val requestId = request.sessionId ?: "stream-${System.currentTimeMillis()}"

        // 使用統一的取消管理器追蹤請求
        val currentJob = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
        currentJob?.let { job ->
            cancellationManager.registerRequest(requestId, job)
        }

        try {
            // 1. Get effective guardian configuration
            val baseGuardianConfig = runnerManager.getCurrentSettings().guardianConfig
            val effectiveGuardianConfig = guardianPipeline.createEffectiveConfig(baseGuardianConfig, request)
            
            // 2. Input Guardian Check (if enabled)
            if (effectiveGuardianConfig.shouldCheckInput()) {
                logger.d(TAG, "Performing guardian input validation for stream request $requestId")
                val inputCheckResult = guardianPipeline.checkInput(request, effectiveGuardianConfig)
                if (inputCheckResult.shouldBlock()) {
                    logger.w(TAG, "Stream request $requestId blocked by guardian input validation")
                    emit(inputCheckResult.toInferenceResult())
                    return@flow
                }
                if (inputCheckResult.shouldWarn()) {
                    logger.w(TAG, "Stream request $requestId triggered guardian warning during input validation")
                }
            }
            
            // 3. Stream AI Processing with Guardian Filtering
            selectAndLoadRunner(request, capability, preferredRunner).fold(
                onSuccess = { runner ->
                    logger.d(TAG, "Processing stream request $requestId with runner: ${runner.getRunnerInfo().name}")
                    logger.d(TAG, "Runtime params for $requestId: ${request.params}")
                    if (runner is FlowStreamingRunner) {
                        runner.runAsFlow(request)
                            .guardianFilter(effectiveGuardianConfig, guardianPipeline)
                            .collect { emit(it) }
                    } else {
                        emit(InferenceResult.error(RunnerError("E406", "Runner does not support streaming.")))
                    }
                },
                onFailure = { error ->
                    val runnerError = if (error is RunnerSelectionException) error.runnerError else RunnerError("E101", error.message ?: "Unknown runtime error")
                    emit(InferenceResult.error(runnerError))
                }
            )
        } catch (e: CancellationException) {
            logger.d(TAG, "Stream request $requestId was cancelled")
            throw e
        } catch (e: Exception) {
            logger.e(TAG, "Error processing stream request", e)
            emit(InferenceResult.error(RunnerError("E101", e.message ?: "Unknown error", true, e)))
        } finally {
            // 使用統一的取消管理器清理請求
            cancellationManager.unregisterRequest(requestId)
        }
    }

    /**
     * 取消正在進行的請求
     * @param requestId 請求ID
     * @return 是否成功取消
     */
    fun cancelRequest(requestId: String): Boolean {
        return cancellationManager.cancelRequest(requestId)
    }

    /**
     * 清理所有活躍的 Runner
     */
    fun cleanup() {
        configLock.write {
            activeRunners.values.forEach { runner ->
                try {
                    runner.unload()
                } catch (e: Exception) {
                    logger.e(TAG, "Error unloading runner: ${runner.getRunnerInfo().name}", e)
                }
            }
            activeRunners.clear()
            logger.d(TAG, "Cleaned up all active runners")
        }
        // 清理取消管理器
        cancellationManager.cleanup()
    }

    /**
     * Unloads all models to save memory while keeping runners in registry.
     * Used when no clients are connected for extended periods.
     */
    fun unloadAllModels() {
        configLock.write {
            activeRunners.values.forEach { runner ->
                try {
                    if (runner.isLoaded()) {
                        runner.unload()
                        logger.d(TAG, "Unloaded model: ${runner.getRunnerInfo().name}")
                    }
                } catch (e: Exception) {
                    logger.e(TAG, "Error unloading model", e)
                }
            }
            logger.d(TAG, "All models unloaded due to no client activity")
        }
    }

    /**
     * Force cleanup all resources for abnormal termination scenarios.
     * Used during emergency shutdown (IDE rebuild, force kill, etc.).
     */
    fun forceCleanupAll() {
        try {
            activeRunners.values.forEach { runner ->
                try {
                    runner.unload()
                } catch (e: Exception) {
                    // Ignore individual errors during force cleanup
                    logger.e(TAG, "Force cleanup error for runner", e)
                }
            }
            activeRunners.clear()
            logger.w(TAG, "Force cleanup completed")
        } catch (e: Exception) {
            logger.e(TAG, "Force cleanup failed", e)
        }
    }

    /**
     * 選擇合適的 Runner
     * 實現 Fallback 策略
     */
    private suspend fun selectAndLoadRunner(
        request: InferenceRequest,
        capability: CapabilityType,
        preferredRunner: String?
    ): Result<BaseRunner> {
        // 1. Select runner using the new priority-based selection
        val runner = if (preferredRunner != null) {
            // Find specific runner by name
            runnerManager.getAllRunners().find { it.getRunnerInfo().name == preferredRunner }
        } else {
            // Use priority-based selection
            runnerManager.getRunner(capability)
        }

        if (runner == null) {
            val errorMessage = if (preferredRunner != null) {
                "Runner not found: $preferredRunner"
            } else {
                "No runner found for capability: ${capability.name}"
            }
            return Result.failure(RunnerSelectionException(RunnerError("E404", errorMessage)))
        }

        // 2. Check capability
        if (!runner.getCapabilities().contains(capability)) {
            return Result.failure(RunnerSelectionException(RunnerError("E405", "Runner ${runner.getRunnerInfo().name} does not support capability: ${capability.name}")))
        }

        // 3. Runtime hardware validation
        if (!isRunnerSupported(runner)) {
            logger.w(TAG, "Runner ${runner.getRunnerInfo().name} not supported on this device hardware")
            return Result.failure(RunnerSelectionException(RunnerError("E503", "Runner ${runner.getRunnerInfo().name} hardware requirements not met on this device")))
        }

        // 4. Load runner if needed
        if (!runner.isLoaded()) {
            logger.d(TAG, "Runner ${runner.getRunnerInfo().name} not loaded, attempting to load...")

            // Determine which model to load. Priority: valid request param -> settings default -> runner default.
            val settings = runnerManager.getCurrentSettings()
            val runnerName = runner.getRunnerInfo().name
            val requestModel = request.params[InferenceRequest.PARAM_MODEL] as? String

            // A model from the request is only used if it's a valid, known model ID.
            val isRequestModelValid = if (!requestModel.isNullOrBlank()) {
                modelRegistryService.getModelDefinition(requestModel) != null
            } else {
                false
            }

            val modelId = if (isRequestModelValid) {
                // Use the valid model from the request as an override.
                logger.d(TAG, "Using valid model override from request: '$requestModel'")
                requestModel!!
            } else {
                // If the request model was invalid or not provided, use settings or the runner's default.
                if (!requestModel.isNullOrBlank()) {
                    logger.w(TAG, "Model '$requestModel' from request is not a valid model ID. Falling back to settings/default.")
                }
                val resolvedModel = settings.getRunnerParameters(runnerName)["model_id"] as? String
                    ?: getDefaultModelForRunner(runnerName)
                
                // Update request params to reflect the resolved model for consistent logging
                (request.params as? MutableMap<String, Any>)?.let { mutableParams ->
                    if (resolvedModel.isNotEmpty()) {
                        mutableParams[InferenceRequest.PARAM_MODEL] = resolvedModel
                        logger.d(TAG, "Updated request params with resolved model: '$resolvedModel' (was: '$requestModel')")
                    } else {
                        // Remove invalid model parameter to avoid confusion in logs
                        mutableParams.remove(InferenceRequest.PARAM_MODEL)
                        logger.w(TAG, "No valid model found, removed model parameter from request (was: '$requestModel')")
                    }
                } ?: run {
                    logger.e(TAG, "Cannot update request params - params is not mutable: ${request.params::class.java}")
                }
                
                resolvedModel
            }

            // Get model info for RAM calculation
            val modelInfo = if (modelId.isNotEmpty()) {
                modelRegistryService.getModelDefinition(modelId)
            } else {
                // Find all models for the runner and return the one with the highest RAM requirement.
                modelRegistryService.getCompatibleModels(runnerName).maxByOrNull { it.ramGB }
            }
            
            val requiredRamGB = modelInfo?.ramGB ?: 2 // Default to 2GB if model info is not found

            var availableRamGB = systemResourceMonitor.getAvailableRamGB()
            logger.d(TAG, "Runner ${runner.getRunnerInfo().name} requires ~${requiredRamGB}GB RAM. Available: ${"%.2f".format(availableRamGB)}GB.")

            if (availableRamGB < requiredRamGB * 1.2f) {
                logger.w(TAG, "Insufficient RAM. Attempting to unload other loaded runners.")

                val runnersToUnload = activeRunners.entries.toList()
                runnersToUnload.forEach { (runnerName, otherRunner) ->
                    if (otherRunner != runner && otherRunner.isLoaded()) {
                        logger.d(TAG, "Unloading runner ${otherRunner.getRunnerInfo().name} to free up memory.")
                        try {
                            otherRunner.unload()
                            // Remove unloaded runner from active collection to free memory references
                            activeRunners.remove(runnerName)
                            logger.d(TAG, "Removed unloaded runner ${otherRunner.getRunnerInfo().name} from active collection")
                        } catch (e: Exception) {
                            logger.e(TAG, "Error unloading runner ${otherRunner.getRunnerInfo().name}", e)
                        }
                    }
                }

                delay(2000)
                availableRamGB = systemResourceMonitor.getAvailableRamGB()
                logger.d(TAG, "RAM available after unloading: ${"%.2f".format(availableRamGB)}GB.")

                if (availableRamGB < requiredRamGB) {
                    val errorMessage = "OOM Risk: Not enough RAM for runner ${runner.getRunnerInfo().name}. Required: ${requiredRamGB}GB, Available: ${"%.2f".format(availableRamGB)}GB."
                    logger.e(TAG, errorMessage)
                    return Result.failure(RunnerSelectionException(RunnerError("E502", errorMessage)))
                }
            }

            // --- New Model-Aware Loading Logic ---
            logger.d(TAG, "Attempting to load runner $runnerName with model $modelId")

            val loaded = runner.load(modelId, settings)
            if (!loaded) {
                return Result.failure(RunnerSelectionException(RunnerError("E501", "Failed to load model '$modelId' for runner: $runnerName")))
            }
        }

        // Register successfully loaded runner in activeRunners collection
        configLock.write {
            activeRunners[runner.getRunnerInfo().name] = runner
        }
        
        return Result.success(runner)
    }

    /**
     * Runtime hardware validation for runners.
     * 
     * Validates that a runner's hardware requirements are met on the current device.
     * This provides an additional safety check beyond discovery-time validation.
     * Uses clean architecture approach with direct method calls instead of reflection.
     * 
     * @param runner The runner to validate
     * @return true if the runner is supported on current hardware, false otherwise
     */
    private fun isRunnerSupported(runner: BaseRunner): Boolean {
        return try {
            // Direct method call - clean architecture approach
            val isSupported = runner.isSupported()
            logger.d(TAG, "Runtime hardware support check for ${runner.javaClass.simpleName}: $isSupported")
            return isSupported
            
        } catch (e: Exception) {
            logger.d(TAG, "Runtime hardware support check failed for ${runner.javaClass.simpleName}, assuming supported")
            return true  // Fail safe - if we can't verify support, assume supported for compatibility
        }
    }

    /**
     * Get the default model ID for a given runner.
     * This method uses a robust selection strategy based on model registry data.
     *
     * @param runnerName The name of the runner
     * @return The default model ID for the runner, or empty string if none found
     */
    private fun getDefaultModelForRunner(runnerName: String): String {
        return try {
            // 1. Try to get the runner instance to access its annotation
            val runner = runnerManager.getAllRunners().find { it.getRunnerInfo().name == runnerName }
            if (runner != null) {
                // Get the AIRunner annotation from the runner's class
                val annotation = runner.javaClass.getAnnotation(com.mtkresearch.breezeapp.engine.annotation.AIRunner::class.java)
                if (annotation != null && annotation.defaultModel.isNotEmpty()) {
                    // Verify the annotated default model exists in registry
                    val annotatedModel = modelRegistryService.getModelDefinition(annotation.defaultModel)
                    if (annotatedModel != null) {
                        logger.d(TAG, "Using annotated default model ${annotation.defaultModel} for runner $runnerName")
                        return annotation.defaultModel
                    } else {
                        logger.w(TAG, "Annotated default model ${annotation.defaultModel} not found in registry for runner $runnerName")
                    }
                }
            }

            // 2. Get compatible models from registry
            val compatibleModels = modelRegistryService.getCompatibleModels(runnerName)
            if (compatibleModels.isEmpty()) {
                logger.w(TAG, "No compatible models found for runner $runnerName")
                return ""
            }

            // 3. Select the most appropriate model based on criteria:
            // - First, look for models with "default" or "base" in their name
            val defaultModel = compatibleModels.find { model ->
                model.id.contains("default", ignoreCase = true) || 
                model.id.contains("base", ignoreCase = true) ||
                model.id.contains("spin", ignoreCase = true) // Prefer spin quantized models for lower RAM
            }
            
            if (defaultModel != null) {
                logger.d(TAG, "Using named default model ${defaultModel.id} for runner $runnerName")
                return defaultModel.id
            }

            // 4. If no explicit default, choose based on RAM requirements:
            // For local runners, prefer models with lower RAM requirements
            // For cloud runners, any model is fine
            val isCloudRunner = runnerName.contains("openrouter", ignoreCase = true) ||
                               runnerName.contains("cloud", ignoreCase = true)
            
            val selectedModel = if (isCloudRunner) {
                // For cloud runners, use the first available model
                compatibleModels.firstOrNull()
            } else {
                // For local runners, prefer models with lower RAM requirements
                compatibleModels.minByOrNull { it.ramGB }
            }
            
            if (selectedModel != null) {
                logger.d(TAG, "Using ${if (isCloudRunner) "first" else "lightest"} model ${selectedModel.id} (${selectedModel.ramGB}GB) as default for runner $runnerName")
                return selectedModel.id
            }

            // 5. Fallback to empty string
            logger.w(TAG, "Could not determine default model for runner $runnerName")
            ""
        } catch (e: Exception) {
            logger.e(TAG, "Error getting default model for runner $runnerName", e)
            ""
        }
    }

    /**
     * Get current engine settings for parameter merging.
     * Used by EngineServiceBinder to merge runtime settings into requests.
     */
    fun getCurrentEngineSettings() = runnerManager.getCurrentSettings()

    /**
     * Update engine settings (public method for guardian configuration).
     * Used by guardian examples and external configuration.
     */
    suspend fun updateSettings(settings: EngineSettings) {
        runnerManager.saveSettings(settings)
    }
    
    /**
     * Get parameter schema defaults for a specific runner.
     * Used to ensure all runner parameters have proper default values.
     */
    fun getRunnerParameterDefaults(runnerName: String): Map<String, Any> {
        return try {
            val runner = runnerManager.getAllRunners().find { it.getRunnerInfo().name == runnerName }
            if (runner != null) {
                val defaults = mutableMapOf<String, Any>()
                runner.getParameterSchema().forEach { schema ->
                    schema.defaultValue?.let { defaultValue ->
                        defaults[schema.name] = defaultValue
                    }
                }
                logger.d(TAG, "Retrieved ${defaults.size} parameter defaults for runner: $runnerName")
                defaults
            } else {
                logger.w(TAG, "Runner not found for parameter defaults: $runnerName")
                emptyMap()
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error getting parameter defaults for runner: $runnerName", e)
            emptyMap()
        }
    }
}

/**
 * Guardian Filter Extension for Flow<InferenceResult>
 * 
 * Applies real-time guardian filtering to streaming inference results.
 * Each result in the stream is checked against the guardian configuration
 * and filtered/blocked accordingly.
 */
suspend fun Flow<InferenceResult>.guardianFilter(
    config: com.mtkresearch.breezeapp.engine.runner.guardian.GuardianPipelineConfig,
    guardianPipeline: GuardianPipeline
): Flow<InferenceResult> {
    
    if (!config.shouldCheckOutput()) {
        return this
    }
    
    return transform { result ->
        if (result.error == null) {
            // Apply guardian filtering to successful results
            val checkResult = guardianPipeline.checkOutput(result, config)
            emit(checkResult.applyToResult(result))
        } else {
            // Pass through errors unchanged
            emit(result)
        }
    }
}