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
import com.mtkresearch.breezeapp.engine.core.download.ModelDownloadService
import com.mtkresearch.breezeapp.engine.core.download.DownloadEventManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
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
            // 1. Guardian Configuration Assessment
            val baseGuardianConfig = runnerManager.getCurrentSettings().guardianConfig
            val effectiveGuardianConfig = guardianPipeline.createEffectiveConfig(baseGuardianConfig, request)
            
            logger.d(TAG, "Guardian mode for request $requestId: ${effectiveGuardianConfig.mode}")
            
            // 2. Input Guardian Check (if enabled)
            if (effectiveGuardianConfig.shouldCheckInput()) {
                logger.d(TAG, "Performing guardian input validation for request $requestId")
                val inputCheckResult = guardianPipeline.checkInput(request, effectiveGuardianConfig)
                if (inputCheckResult is com.mtkresearch.breezeapp.engine.runner.guardian.GuardianCheckResult.Failed) {
                    logger.w(TAG, "Request $requestId blocked by guardian input validation: ${inputCheckResult.analysisResult.status}")
                    return inputCheckResult.toInferenceResult(context)
                }
                logger.d(TAG, "Guardian input validation passed for request $requestId")
            }
            
            // 3. AI Processing with Guardian Mode Awareness
            val aiResult = selectAndLoadRunner(request, capability, preferredRunner).fold(
                onSuccess = { runner ->
                    logger.d(TAG, "Processing request $requestId with runner: ${runner.getRunnerInfo().name}")
                    logger.d(TAG, "Runtime params for $requestId: ${request.params}")
                    runner.run(request)
                },
                onFailure = { error ->
                    val runnerError = if (error is RunnerSelectionException) error.runnerError else RunnerError.processingError(error.message ?: "Unknown runtime error")
                    InferenceResult.error(runnerError)
                }
            )
            
            // 4. Output Guardian Check (mode-dependent)
            when (effectiveGuardianConfig.mode) {
                com.mtkresearch.breezeapp.engine.runner.guardian.GuardianMode.INPUT_ONLY -> {
                    // NEW: Input-only mode - no output processing (recommended)
                    logger.d(TAG, "INPUT_ONLY Guardian mode - skipping output check for request $requestId")
                    aiResult
                }
                
                com.mtkresearch.breezeapp.engine.runner.guardian.GuardianMode.FULL -> {
                    // FULL mode deprecated - no output filtering
                    logger.w(TAG, "FULL Guardian mode deprecated, no output filtering for request $requestId")
                    aiResult
                }
                
                com.mtkresearch.breezeapp.engine.runner.guardian.GuardianMode.DISABLED -> {
                    // No Guardian processing
                    logger.d(TAG, "Guardian disabled - no output check for request $requestId")
                    aiResult
                }
            }
            
        } catch (e: Exception) {
            logger.e(TAG, "Error processing request", e)
            InferenceResult.error(RunnerError.processingError(e.message ?: "Unknown error", e))
        } finally {
            // 使用統一的取消管理器清理請求
            cancellationManager.unregisterRequest(requestId)
        }
    }

    /**
     * 處理串流推論請求 - Simplified Input-Only Guardian
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

        val currentJob = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
        currentJob?.let { job ->
            cancellationManager.registerRequest(requestId, job)
        }

        try {
            // 1. Guardian Configuration Assessment
            val baseGuardianConfig = runnerManager.getCurrentSettings().guardianConfig
            val effectiveGuardianConfig = guardianPipeline.createEffectiveConfig(baseGuardianConfig, request)
            
            logger.d(TAG, "Guardian mode for stream request $requestId: ${effectiveGuardianConfig.mode}")

            // 2. Input Guardian Check (blocking) - Core Guardian responsibility
            if (effectiveGuardianConfig.shouldCheckInput()) {
                logger.d(TAG, "Performing guardian input validation for stream request $requestId")
                val inputCheckResult = guardianPipeline.checkInput(request, effectiveGuardianConfig)
                if (inputCheckResult is com.mtkresearch.breezeapp.engine.runner.guardian.GuardianCheckResult.Failed) {
                    logger.w(TAG, "Stream request $requestId blocked by guardian input validation: ${inputCheckResult.analysisResult.status}")
                    emit(inputCheckResult.toInferenceResult(context))
                    return@flow
                }
                logger.d(TAG, "Guardian input validation passed for stream request $requestId")
            }

            // 3. Select Processing Path Based on Guardian Mode
            selectAndLoadRunner(request, capability, preferredRunner).fold(
                onSuccess = { runner ->
                    if (runner !is FlowStreamingRunner) {
                        emit(InferenceResult.error(RunnerError(RunnerError.Code.STREAMING_NOT_SUPPORTED, "Runner does not support streaming.")))
                        return@fold
                    }

                    // Route to appropriate processing path based on Guardian mode
                    when (effectiveGuardianConfig.mode) {
                        com.mtkresearch.breezeapp.engine.runner.guardian.GuardianMode.INPUT_ONLY -> {
                            // NEW: Simplified direct streaming path (recommended)
                            logger.d(TAG, "Using INPUT_ONLY Guardian mode - direct LLM streaming for request $requestId")
                            runner.runAsFlow(request).collect { result ->
                                emit(result)
                            }
                            logger.d(TAG, "INPUT_ONLY Guardian streaming completed for request $requestId")
                        }
                        
                        com.mtkresearch.breezeapp.engine.runner.guardian.GuardianMode.FULL -> {
                            // FULL mode deprecated - use INPUT_ONLY for better performance
                            logger.w(TAG, "FULL Guardian mode deprecated, falling back to INPUT_ONLY behavior for request $requestId")
                            runner.runAsFlow(request).collect { result ->
                                emit(result)
                            }
                            logger.d(TAG, "FULL Guardian (deprecated) streaming completed for request $requestId")
                        }
                        
                        com.mtkresearch.breezeapp.engine.runner.guardian.GuardianMode.DISABLED -> {
                            // Direct streaming with no Guardian processing
                            logger.d(TAG, "Guardian disabled - direct LLM streaming for request $requestId")
                            runner.runAsFlow(request).collect { result ->
                                emit(result)
                            }
                            logger.d(TAG, "Guardian disabled streaming completed for request $requestId")
                        }
                    }
                },
                onFailure = { error ->
                    val runnerError = if (error is RunnerSelectionException) error.runnerError else RunnerError.processingError(error.message ?: "Unknown runtime error")
                    emit(InferenceResult.error(runnerError))
                }
            )
        } catch (e: CancellationException) {
            logger.d(TAG, "Stream request $requestId was cancelled")
            throw e
        } catch (e: Exception) {
            logger.e(TAG, "Error processing stream request", e)
            emit(InferenceResult.error(RunnerError.processingError(e.message ?: "Unknown error", e)))
        } finally {
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
            return Result.failure(RunnerSelectionException(RunnerError(RunnerError.Code.RUNNER_NOT_FOUND, errorMessage)))
        }

        // 2. Check capability
        if (!runner.getCapabilities().contains(capability)) {
            return Result.failure(RunnerSelectionException(RunnerError(RunnerError.Code.CAPABILITY_NOT_SUPPORTED, "Runner ${runner.getRunnerInfo().name} does not support capability: ${capability.name}")))
        }

        // 3. Runtime hardware validation
        if (!isRunnerSupported(runner)) {
            logger.w(TAG, "Runner ${runner.getRunnerInfo().name} not supported on this device hardware")
            return Result.failure(RunnerSelectionException(RunnerError(RunnerError.Code.HARDWARE_NOT_SUPPORTED, "Runner ${runner.getRunnerInfo().name} hardware requirements not met on this device")))
        }

        // 4. Load runner if needed (or reload if model changed)
        // Determine which model to load. Priority: valid request param -> settings default -> runner default.
        val settings = runnerManager.getCurrentSettings()
        val runnerName = runner.getRunnerInfo().name
        val requestModel = request.params[InferenceRequest.PARAM_MODEL] as? String

        // Get the model that should be used (before loading)
        val isCloudRunner = runnerName.contains("OpenRouter", ignoreCase = true) ||
                           runnerName.contains("cloud", ignoreCase = true)
        val isRequestModelValid = if (!requestModel.isNullOrBlank()) {
            if (isCloudRunner) {
                true
            } else {
                modelRegistryService.getModelDefinition(requestModel) != null
            }
        } else {
            false
        }

        val targetModelId = if (isRequestModelValid) {
            requestModel!!
        } else {
            // Use InferenceRequest.PARAM_MODEL ("model") not "model_id"
            settings.getRunnerParameters(runnerName)[InferenceRequest.PARAM_MODEL] as? String
                ?: getDefaultModelForRunner(runnerName)
        }

        // Check if runner needs to be loaded or reloaded due to model change
        val needsReload = if (!runner.isLoaded()) {
            logger.d(TAG, "Runner ${runner.getRunnerInfo().name} not loaded, needs initial load")
            true
        } else {
            // Runner is loaded - check if model has changed using clean interface method
            val loadedModel = runner.getLoadedModelId()
            val modelChanged = loadedModel.isNotEmpty() && targetModelId.isNotEmpty() && loadedModel != targetModelId
            if (modelChanged) {
                logger.d(TAG, "Model changed from '$loadedModel' to '$targetModelId' - unloading and reloading runner")
                runner.unload()
                // Add small delay to ensure cleanup completes
                delay(100)
                true
            } else {
                logger.d(TAG, "Runner ${runner.getRunnerInfo().name} already loaded with model '$loadedModel'")
                false
            }
        }

        if (needsReload) {
            logger.d(TAG, "Runner ${runner.getRunnerInfo().name} loading/reloading with model '$targetModelId'...")

            // Update request params to reflect the resolved model for consistent logging
            (request.params as? MutableMap<String, Any>)?.let { mutableParams ->
                if (targetModelId.isNotEmpty()) {
                    mutableParams[InferenceRequest.PARAM_MODEL] = targetModelId
                    logger.d(TAG, "Updated request params with resolved model: '$targetModelId' (was: '$requestModel')")
                } else {
                    // Remove invalid model parameter to avoid confusion in logs
                    mutableParams.remove(InferenceRequest.PARAM_MODEL)
                    logger.w(TAG, "No valid model found, removed model parameter from request (was: '$requestModel')")
                }
            } ?: run {
                logger.e(TAG, "Cannot update request params - params is not mutable: ${request.params::class.java}")
            }

            // --- Enhanced Model Download Logic with Progress ---
            val modelManager = ModelManager.getInstance(context)
            val modelState = modelManager.getModelState(targetModelId)

            if (targetModelId.isNotEmpty() && modelState != null && modelState.status !in listOf(ModelManager.ModelState.Status.DOWNLOADED, ModelManager.ModelState.Status.READY)) {
                logger.d(TAG, "Model '$targetModelId' is not downloaded. Starting enhanced download with progress tracking...")

                // Get all files to download
                val filesToDownload = modelState.modelInfo.files
                if (filesToDownload.isEmpty()) {
                    return Result.failure(RunnerSelectionException(RunnerError(RunnerError.Code.MODEL_DOWNLOAD_FAILED, "No files to download for model: $targetModelId")))
                }

                logger.d(TAG, "Model '$targetModelId' has ${filesToDownload.size} files to download")

                // Create fresh model directory (delete any corrupted files from failed downloads)
                val modelDir = java.io.File(context.filesDir, "models/$targetModelId")
                if (modelDir.exists()) {
                    modelDir.deleteRecursively()
                    logger.d(TAG, "Deleted existing model directory for fresh download: $targetModelId")
                }
                modelDir.mkdirs()

                // Notify download started for UI
                val firstFileName = filesToDownload.firstOrNull()?.fileName ?: "$targetModelId.bin"
                DownloadEventManager.notifyDownloadStarted(context, targetModelId, firstFileName)

                // Download all files sequentially
                for ((index, fileInfo) in filesToDownload.withIndex()) {
                    val downloadUrl = fileInfo.urls.firstOrNull()
                        ?: return Result.failure(RunnerSelectionException(RunnerError(RunnerError.Code.MODEL_DOWNLOAD_FAILED, "No download URL available for file: ${fileInfo.fileName}")))
                    val fileName = fileInfo.fileName

                    logger.d(TAG, "Downloading file ${index + 1}/${filesToDownload.size}: $fileName")

                    // Use unique download ID per file to avoid overwrites
                    // ModelDownloadService extracts base modelId for consistent notifications
                    val downloadId = "${targetModelId}_file_$index"
                    ModelDownloadService.startDownload(context, downloadId, downloadUrl, fileName)

                    // Wait for this file to complete before starting next
                    // Check that file size stabilizes (stops growing) to ensure download is complete
                    val targetFile = java.io.File(modelDir, fileName)

                    var fileDownloaded = false
                    var attempts = 0
                    val maxAttempts = 1800 // 30 minutes
                    var lastSize = 0L
                    var stableCount = 0

                    while (!fileDownloaded && attempts < maxAttempts) {
                        delay(1000)
                        if (targetFile.exists()) {
                            val currentSize = targetFile.length()
                            if (currentSize > 0 && currentSize == lastSize) {
                                stableCount++
                                // Consider complete if size stable for 2 seconds
                                if (stableCount >= 2) {
                                    fileDownloaded = true
                                    logger.d(TAG, "File downloaded: $fileName (${currentSize} bytes)")
                                }
                            } else {
                                stableCount = 0
                                lastSize = currentSize
                            }
                        }
                        attempts++
                    }

                    if (!fileDownloaded) {
                        logger.e(TAG, "Failed to download file: $fileName")
                        ModelDownloadService.cancelDownload(context, downloadId)
                        return Result.failure(RunnerSelectionException(RunnerError(RunnerError.Code.MODEL_DOWNLOAD_FAILED, "Failed to download file: $fileName")))
                    }
                }

                // All files downloaded - update ModelManager state
                logger.d(TAG, "All ${filesToDownload.size} files downloaded for model: $targetModelId")
                modelManager.markModelAsDownloaded(targetModelId)
                DownloadEventManager.notifyDownloadCompleted(context, targetModelId)
            }
            // --- End Enhanced Model Download Logic ---

            // Skip RAM check for cloud-based runners (they don't load models into RAM)
            // Note: isCloudRunner is already declared above for model validation
            if (isCloudRunner) {
                logger.d(TAG, "Skipping RAM check for cloud runner: $runnerName")
            } else {
                // Get model info for RAM calculation
                val modelInfo = if (targetModelId.isNotEmpty()) {
                    modelRegistryService.getModelDefinition(targetModelId)
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
                        return Result.failure(RunnerSelectionException(RunnerError(RunnerError.Code.INSUFFICIENT_RESOURCES, errorMessage)))
                    }
                }
            }

            // --- New Model-Aware Loading Logic ---
            logger.d(TAG, "Attempting to load runner $runnerName with model $targetModelId")

            val loaded = runner.load(targetModelId, settings, request.params)
            if (!loaded) {
                return Result.failure(RunnerSelectionException(RunnerError(RunnerError.Code.MODEL_LOAD_FAILED, "Failed to load model '$targetModelId' for runner: $runnerName")))
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
            isSupported
            
        } catch (e: Exception) {
            logger.d(TAG, "Runtime hardware support check failed for ${runner.javaClass.simpleName}, assuming supported")
            true  // Fail safe - if we can't verify support, assume supported for compatibility
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

// Note: StreamingCompletionOrchestrator removed - Guardian now only validates input
// Output filtering and progressive analysis removed for simplified architecture
