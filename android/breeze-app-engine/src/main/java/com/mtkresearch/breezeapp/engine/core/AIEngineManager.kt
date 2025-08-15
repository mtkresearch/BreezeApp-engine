package com.mtkresearch.breezeapp.engine.core

import android.app.ActivityManager
import android.content.Context
import com.mtkresearch.breezeapp.engine.runner.core.BaseRunner
import com.mtkresearch.breezeapp.engine.runner.core.FlowStreamingRunner
import com.mtkresearch.breezeapp.engine.model.CapabilityType
import com.mtkresearch.breezeapp.engine.model.InferenceRequest
import com.mtkresearch.breezeapp.engine.model.InferenceResult
import com.mtkresearch.breezeapp.engine.model.ModelConfig
import com.mtkresearch.breezeapp.engine.model.RunnerError
import com.mtkresearch.breezeapp.engine.runner.core.RunnerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
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

private data class ModelInfo(val id: String, val runner: String, val ramGB: Int)

private interface ModelInfoProvider {
    fun getModelInfoByRunner(runnerName: String): ModelInfo?
}

private class ModelInfoProviderImpl(private val context: Context, private val logger: Logger) : ModelInfoProvider {
    private val modelList: List<ModelInfo> by lazy { parseModelList() }

    private fun parseModelList(): List<ModelInfo> {
        return try {
            val jsonString = context.assets.open("fullModelList.json").bufferedReader().use { it.readText() }
            val json = JSONObject(jsonString)
            val modelsArray = json.getJSONArray("models")
            val list = mutableListOf<ModelInfo>()
            for (i in 0 until modelsArray.length()) {
                val modelObject = modelsArray.getJSONObject(i)
                list.add(ModelInfo(
                    id = modelObject.getString("id"),
                    runner = modelObject.getString("runner"),
                    ramGB = modelObject.getInt("ramGB")
                ))
            }
            list
        } catch (e: Exception) {
            logger.e("ModelInfoProvider", "Failed to parse fullModelList.json", e)
            emptyList()
        }
    }

    override fun getModelInfoByRunner(runnerName: String): ModelInfo? {
        // Find all models for the runner and return the one with the highest RAM requirement.
        return modelList.filter { it.runner == runnerName }.maxByOrNull { it.ramGB }
    }
}


class AIEngineManager(
    private val context: Context,
    private val runnerManager: RunnerManager,
    private val logger: Logger
) {

    companion object {
        private const val TAG = "AIEngineManager"
    }

    private val systemResourceMonitor: SystemResourceMonitor = SystemResourceMonitorImpl(context)
    private val modelInfoProvider: ModelInfoProvider = ModelInfoProviderImpl(context, logger)

    // 執行緒安全的 Runner 儲存
    private val activeRunners = ConcurrentHashMap<String, BaseRunner>()

    // 讀寫鎖保護配置變更
    private val configLock = ReentrantReadWriteLock()

    // 使用統一的取消管理器
    private val cancellationManager = CancellationManager.getInstance()

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
            selectAndLoadRunner(capability, preferredRunner).fold(
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
            selectAndLoadRunner(capability, preferredRunner).fold(
                onSuccess = { runner ->
                    logger.d(TAG, "Processing stream request $requestId with runner: ${runner.getRunnerInfo().name}")
                    logger.d(TAG, "Runtime params for $requestId: ${request.params}")
                    if (runner is FlowStreamingRunner) {
                        runner.runAsFlow(request).collect { emit(it) }
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

        // 3. Load runner if needed
        if (!runner.isLoaded()) {
            logger.d(TAG, "Runner ${runner.getRunnerInfo().name} not loaded, attempting to load...")

            // Memory Management Logic
            val modelInfo = modelInfoProvider.getModelInfoByRunner(runner.getRunnerInfo().name)
            val requiredRamGB = modelInfo?.ramGB ?: 4 // Default to 2GB if model info is not found

            var availableRamGB = systemResourceMonitor.getAvailableRamGB()
            logger.d(TAG, "Runner ${runner.getRunnerInfo().name} requires ~${requiredRamGB}GB RAM. Available: ${"%.2f".format(availableRamGB)}GB.")

            if (availableRamGB < requiredRamGB * 1.2f) {
                logger.w(TAG, "Insufficient RAM. Attempting to unload other loaded runners.")

                val runnersToUnload = activeRunners.values.toList()
                runnersToUnload.forEach { otherRunner ->
                    if (otherRunner != runner && otherRunner.isLoaded()) {
                        logger.d(TAG, "Unloading runner ${otherRunner.getRunnerInfo().name} to free up memory.")
                        try {
                            otherRunner.unload()
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

            val loaded = runner.load(createDefaultConfig(runner.getRunnerInfo().name))
            if (!loaded) {
                return Result.failure(RunnerSelectionException(RunnerError("E501", "Failed to load model for runner: ${runner.getRunnerInfo().name}")))
            }
        }

        return Result.success(runner)
    }

    /**
     * 取得或建立 Runner 實例
     */
    private fun getOrCreateRunner(name: String): BaseRunner? {
        configLock.read {
            activeRunners[name]?.let { return it }
        }

        return configLock.write {
            // Double-check lock
            activeRunners[name]?.let { return@write it }

            // In the new system, runners are already created and managed by RunnerManager
            // We just need to find the existing runner by name
            val runner = runnerManager.getAllRunners().find { it.getRunnerInfo().name == name }
            if (runner != null) {
                activeRunners[name] = runner
                logger.d(TAG, "Using existing runner instance: $name")
            } else {
                logger.e(TAG, "Failed to find runner: $name")
            }
            runner
        }
    }

    /**
     * 建立預設配置
     */
    private fun createDefaultConfig(runnerName: String): ModelConfig {
        // For MTK runners, find the proper model ID from fullModelList.json
        val modelPath = if (runnerName.contains("MTK", ignoreCase = true)) {
            try {
                val jsonString = context.assets.open("fullModelList.json").bufferedReader().use { it.readText() }
                val json = JSONObject(jsonString)
                val modelsArray = json.getJSONArray("models")
                
                // Find MTK model (runner="mediatek") 
                var mtkModelPath: String? = null
                for (i in 0 until modelsArray.length()) {
                    val modelObject = modelsArray.getJSONObject(i)
                    if (modelObject.getString("runner") == "mediatek") {
                        val modelId = modelObject.getString("id")
                        val baseDir = "/data/user/0/com.mtkresearch.breezeapp.engine/files/models"
                        mtkModelPath = "$baseDir/$modelId/config_breezetiny_3b_instruct.yaml"
                        break
                    }
                }
                mtkModelPath ?: "/data/local/tmp/models/$runnerName"
            } catch (e: Exception) {
                logger.e(TAG, "Failed to resolve MTK model path", e)
                "/data/local/tmp/models/$runnerName"
            }
        } else {
            "/data/local/tmp/models/$runnerName"
        }
        
        return ModelConfig(
            modelName = runnerName,
            modelPath = modelPath
        )
    }
}