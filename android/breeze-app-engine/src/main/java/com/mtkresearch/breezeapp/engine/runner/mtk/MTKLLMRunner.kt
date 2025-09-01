package com.mtkresearch.breezeapp.engine.runner.mtk

import android.content.Context
import android.util.Log
import com.mtkresearch.breezeapp.engine.annotation.AIRunner
import com.mtkresearch.breezeapp.engine.annotation.RunnerPriority
import com.mtkresearch.breezeapp.engine.annotation.VendorType
import com.mtkresearch.breezeapp.engine.model.*
import com.mtkresearch.breezeapp.engine.service.ModelRegistryService
import com.mtkresearch.breezeapp.engine.runner.core.BaseRunner
import com.mtkresearch.breezeapp.engine.runner.core.FlowStreamingRunner
import com.mtkresearch.breezeapp.engine.runner.core.RunnerInfo
import com.mtkresearch.breezeapp.engine.runner.core.ParameterSchema
import com.mtkresearch.breezeapp.engine.runner.core.ParameterType
import com.mtkresearch.breezeapp.engine.runner.core.ValidationResult
import com.mtkresearch.breezeapp.engine.system.NativeLibraryManager
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

/**
 * MTKLLMRunner - MTK NPU 加速的 AI Runner 實作
 * 
 * 基於 BreezeApp Engine 架構，支援 MTK NPU 硬體加速的大語言模型推論
 * 
 * 特性：
 * - 支援 MTK NPU 硬體加速
 * - 串流推論與 Flow-based API
 * - 重試機制與錯誤恢復
 * - 資源管理與清理
 * - 線程安全設計
 * 
 * 改進點：
 * - 移除 libsigchain 依賴
 * - 移除 SharedPreferences 濫用
 * - 統一配置管理
 * - 現代化的 Kotlin 協程支援
 */
@AIRunner(
    vendor = VendorType.MEDIATEK,
    priority = RunnerPriority.HIGH,
    capabilities = [CapabilityType.LLM],
    defaultModel = "Breeze2-3B-8W16A-250630-npu"
)
class MTKLLMRunner(
    private val context: Context? = null,
    private val modelRegistry: ModelRegistryService? = null
) : BaseRunner, FlowStreamingRunner {

    private val isLoaded = AtomicBoolean(false)
    private val isGenerating = AtomicBoolean(false)
    private val isCleaningUp = AtomicBoolean(false)
    private var initializationResult: InitializationResult? = null
    private var resolvedModelPath: String? = null

    // Dependencies are now initialized later or are singletons
    private lateinit var config: MTKConfig
    private val nativeLibraryManager = NativeLibraryManager.getInstance()

    companion object {
        private const val TAG = "MTKLLMRunner"
        private const val MODEL_NAME = "Breeze2-3B-8W16A-250630-npu"
        private const val RUNNER_VERSION = "1.0.0"
        private val initAttemptCount = AtomicInteger(0)
    }
    
    // Load model using model ID from JSON registry
    override fun load(modelId: String, settings: EngineSettings, initialParams: Map<String, Any>): Boolean {
        Log.d(TAG, "Loading MTKLLMRunner with model: $modelId")
        if (isLoaded.get()) return true

        // Get model definition from registry
        val modelDef = modelRegistry?.getModelDefinition(modelId)
        if (modelDef == null) {
            Log.e(TAG, "Model definition not found for ID: $modelId")
            return false
        }

        // Resolve model path using the enhanced utility function
        val modelPath = MTKUtils.resolveModelPath(modelDef, context!!)
        if (modelPath == null) {
            Log.e(TAG, "Failed to resolve model path for ${modelDef.id} using entry point.")
            return false
        }

        // Initialize MTK config directly from model path
        this.config = MTKConfig.createDefault(modelPath)

        // 1. 驗證硬體支援
        if (!isSupported()) {
            Log.e(TAG, "MTK NPU not supported on this device")
            return false
        }

        // 2. 載入原生庫
        val libraryResult = nativeLibraryManager.loadMTKLibrary()
        if (libraryResult.isError()) {
            Log.e(TAG, "Failed to load MTK library: ${libraryResult.getErrorMessage()}")
            return false
        }

        // 3. 解析模型路徑 (Smart path resolution like Sherpa's approach)  
        if (!File(modelPath).exists()) {
            Log.e(TAG, "Model path does not exist: $modelPath")
            return false
        }
        resolvedModelPath = modelPath
        Log.d(TAG, "Model path resolved: $resolvedModelPath")

        // 4. 初始化 MTK 後端
        val initResult = initializeMTKBackend()
        if (initResult.isSuccess()) {
            isLoaded.set(true)
            Log.i(TAG, "MTKLLMRunner loaded successfully")
            return true
        } else {
            Log.e(TAG, "MTK backend initialization failed: ${initResult.getErrorMessage()}")
            return false
        }
    }
    
    private fun parseInferenceParameters(input: InferenceRequest): InferenceParameters {
        val temperature = when (val temp = input.params[InferenceRequest.PARAM_TEMPERATURE]) {
            is Number -> temp.toFloat()
            is String -> temp.toFloatOrNull()
            else -> null
        } ?: config.defaultTemperature

        val maxTokens = when (val tokens = input.params[InferenceRequest.PARAM_MAX_TOKENS]) {
            is Number -> tokens.toInt()
            is String -> tokens.toIntOrNull()
            else -> null
        } ?: config.defaultMaxTokens

        val topK = when (val k = input.params["top_k"]) {
            is Number -> k.toInt()
            is String -> k.toIntOrNull()
            else -> null
        } ?: config.defaultTopK

        val repetitionPenalty = when (val penalty = input.params["repetition_penalty"]) {
            is Number -> penalty.toFloat()
            is String -> penalty.toFloatOrNull()
            else -> null
        } ?: config.defaultRepetitionPenalty

        return InferenceParameters(
            temperature = temperature,
            topK = topK,
            topP = config.defaultTopP, // topP not handled as runtime param in this runner
            repetitionPenalty = repetitionPenalty,
            maxTokens = maxTokens
        )
    }

    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        if (!isLoaded.get()) return InferenceResult.error(RunnerError.resourceUnavailable())
        return try {
            val inputText = input.inputs[InferenceRequest.INPUT_TEXT] as? String
                ?: return InferenceResult.error(RunnerError.invalidInput("Missing text input"))

            val params = parseInferenceParameters(input)

            val response = if (stream) {
                performStreamingInference(inputText, params.maxTokens, params.temperature, params.topK, params.repetitionPenalty)
            } else {
                performSingleInference(inputText, params.maxTokens, params.temperature, params.topK, params.repetitionPenalty)
            }
            InferenceResult.textOutput(
                text = response,
                metadata = mapOf(
                    InferenceResult.META_MODEL_NAME to MODEL_NAME,
                    InferenceResult.META_PROCESSING_TIME_MS to System.currentTimeMillis(),
                    "temperature" to params.temperature,
                    "max_tokens" to params.maxTokens,
                    "top_k" to params.topK,
                    "repetition_penalty" to params.repetitionPenalty
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during inference", e)
            InferenceResult.error(RunnerError.processingError("Inference failed: ${e.message}", e))
        } finally {
            isGenerating.set(false)
            performSafeCleanup("single inference")
        }
    }
    
    override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = callbackFlow {
        if (!isLoaded.get()) {
            trySend(InferenceResult.error(RunnerError.resourceUnavailable()))
            close()
            return@callbackFlow
        }
        if (!isGenerating.compareAndSet(false, true)) {
            trySend(InferenceResult.error(RunnerError.processingError("Another inference is in progress")))
            close()
            return@callbackFlow
        }
        try {
            val inputText = input.inputs[InferenceRequest.INPUT_TEXT] as? String
            if (inputText == null) {
                trySend(InferenceResult.error(RunnerError.invalidInput("Missing text input")))
                close()
                return@callbackFlow
            }

            val params = parseInferenceParameters(input)

            val fullResponse = StringBuilder()
            val startTime = System.currentTimeMillis()
            val callback = object : TokenCallback {
                override fun onToken(token: String) {
                    fullResponse.append(token)
                    trySend(
                        InferenceResult.textOutput(
                            text = token, // Send only the new token as the main text
                            metadata = emptyMap(), // OPTIMIZED: Avoid creating new maps for every token.
                            partial = true
                        )
                    )
                }
            }
            val response = nativeStreamingInference(
                inputText,
                params.maxTokens,
                false,
                callback,
                params.temperature,
                params.topK,
                params.repetitionPenalty
            )

            isGenerating.set(false)
            performSafeCleanup("streaming inference mid-flow")
            // ---
            val processingTime = System.currentTimeMillis() - startTime
            trySend(
                InferenceResult.textOutput(
                    text = response,
                    metadata = mapOf(
                        InferenceResult.META_MODEL_NAME to MODEL_NAME,
                        InferenceResult.META_PROCESSING_TIME_MS to processingTime,
                        InferenceResult.META_TOKEN_COUNT to fullResponse.length,
                        "temperature" to params.temperature,
                        "max_tokens" to params.maxTokens,
                        "top_k" to params.topK,
                        "repetition_penalty" to params.repetitionPenalty
                    ),
                    partial = false
                )
            )
            close() // Close immediately after final result
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Normal cancellation - don't log as error or send error result
            Log.d(TAG, "Streaming inference cancelled (normal shutdown)")
            close()
        } catch (e: Exception) {
            Log.e(TAG, "Error during streaming inference", e)
            trySend(InferenceResult.error(RunnerError.processingError("Streaming inference failed: ${e.message}", e)))
            close()
        } finally {
            isGenerating.set(false)
            performSafeCleanup("streaming inference")
        }
    }
    
    override fun unload() {
        Log.d(TAG, "Unloading MTKLLMRunner")
        if (!isLoaded.get()) return
        
        // Prevent concurrent cleanup
        if (!isCleaningUp.compareAndSet(false, true)) {
            Log.d(TAG, "Cleanup already in progress, skipping unload")
            return
        }
        
        try {
            isGenerating.set(false)
            nativeResetLlm()
            nativeReleaseLlm()
            isLoaded.set(false)
            initializationResult = null
            resolvedModelPath = null
            initAttemptCount.set(0)
            Log.i(TAG, "MTKLLMRunner unloaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during MTKLLMRunner unload", e)
            // Force state reset even on error to prevent stuck states
            isLoaded.set(false)
        } finally {
            isCleaningUp.set(false)
        }
    }
    
    /**
     * Finalize method for emergency cleanup when object is garbage collected.
     * This provides a safety net for abnormal termination scenarios.
     */
    protected fun finalize() {
        try {
            if (isLoaded.get()) {
                Log.w(TAG, "MTKLLMRunner finalize() called - emergency cleanup")
                nativeResetLlm()
                nativeReleaseLlm()
                isLoaded.set(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in finalize cleanup", e)
        }
    }
    
    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.LLM)
    override fun isLoaded(): Boolean = isLoaded.get()
    override fun getRunnerInfo(): RunnerInfo = RunnerInfo(
        name = MTKLLMRunner::class.java.simpleName, // Use the class name for clarity
        version = RUNNER_VERSION,
        capabilities = getCapabilities(),
        description = "MTK NPU accelerated language model runner with streaming support"
    )
    
    override fun isSupported(): Boolean {
        return try {
            val hardwareSupported = MTKUtils.isMTKNPUSupported()
            val libraryAvailable = NativeLibraryManager.getInstance().isLibraryAvailable("llm_jni")
            val supported = hardwareSupported && libraryAvailable
            Log.d(TAG, "MTK NPU support check: hardware=$hardwareSupported, library=$libraryAvailable, result=$supported")
            supported
        } catch (e: Exception) {
            Log.e(TAG, "Error checking MTK NPU support", e)
            false
        }
    }

    override fun getParameterSchema(): List<ParameterSchema> {
        return listOf(
            ParameterSchema(
                name = "temperature",
                displayName = "Temperature",
                description = "Controls randomness in text generation. Lower values (0.1) make output more focused, higher values (1.0) make it more creative.",
                type = ParameterType.FloatType(
                    minValue = 0.0,
                    maxValue = 2.0,
                    step = 0.1,
                    precision = 1
                ),
                defaultValue = 0.8f,
                isRequired = false,
                category = "Generation Parameters"
            ),
            ParameterSchema(
                name = "max_tokens",
                displayName = "Max Tokens",
                description = "Maximum number of tokens to generate in the response.",
                type = ParameterType.IntType(
                    minValue = 1,
                    maxValue = 4096,
                    step = 1
                ),
                defaultValue = 256,
                isRequired = false,
                category = "Generation Parameters"
            ),
            ParameterSchema(
                name = "top_k",
                displayName = "Top K",
                description = "Limits the model to consider only the top K most likely tokens at each step.",
                type = ParameterType.IntType(
                    minValue = 1,
                    maxValue = 100,
                    step = 1
                ),
                defaultValue = 40,
                isRequired = false,
                category = "Generation Parameters"
            ),
            ParameterSchema(
                name = "repetition_penalty",
                displayName = "Repetition Penalty",
                description = "Penalty applied to repeated tokens to reduce repetitive output. Values > 1.0 discourage repetition.",
                type = ParameterType.FloatType(
                    minValue = 0.1,
                    maxValue = 2.0,
                    step = 0.1,
                    precision = 1
                ),
                defaultValue = 1.1f,
                isRequired = false,
                category = "Generation Parameters"
            )
        )
    }

    override fun validateParameters(parameters: Map<String, Any>): ValidationResult {
        val temperature = parameters["temperature"] as? Number
        val maxTokens = parameters["max_tokens"] as? Number
        val topK = parameters["top_k"] as? Number
        val repetitionPenalty = parameters["repetition_penalty"] as? Number

        // Validate temperature
        temperature?.let { temp ->
            val tempValue = temp.toFloat()
            if (tempValue < 0.0f || tempValue > 2.0f) {
                return ValidationResult.invalid("Temperature must be between 0.0 and 2.0")
            }
        }

        // Validate max tokens
        maxTokens?.let { tokens ->
            val tokensValue = tokens.toInt()
            if (tokensValue < 1 || tokensValue > 4096) {
                return ValidationResult.invalid("Max tokens must be between 1 and 4096")
            }
        }

        // Validate top_k
        topK?.let { k ->
            val kValue = k.toInt()
            if (kValue < 1 || kValue > 100) {
                return ValidationResult.invalid("Top K must be between 1 and 100")
            }
        }

        // Validate repetition penalty
        repetitionPenalty?.let { penalty ->
            val penaltyValue = penalty.toFloat()
            if (penaltyValue < 0.1f || penaltyValue > 2.0f) {
                return ValidationResult.invalid("Repetition penalty must be between 0.1 and 2.0")
            }
        }

        // Cross-parameter validation
        temperature?.let { temp ->
            val tempValue = temp.toFloat()
            if (tempValue > 1.5f) {
                topK?.let { k ->
                    val kValue = k.toInt()
                    if (kValue > 80) {
                        return ValidationResult.invalid("High temperature (>1.5) with high top_k (>80) may produce incoherent results. Consider reducing one of these values.")
                    }
                }
            }
        }

        return ValidationResult.valid()
    }

    // --- 與舊版 LLMEngineService 對應的初始化邏輯 ---
    private fun initializeMTKBackend(): InitializationResult {
        val currentAttempt = initAttemptCount.incrementAndGet()
        val maxAttempts = config.maxInitAttempts
        Log.d(TAG, "MTK backend initialization attempt $currentAttempt/$maxAttempts")
        if (currentAttempt > maxAttempts) {
            Log.e(TAG, "MTK initialization exceeded max attempts")
            return InitializationResult.Error("Exceeded maximum initialization attempts")
        }
        return try {
            val initDelay = config.initDelayMs
            if (initDelay > 0) Thread.sleep(initDelay)
            nativeResetLlm()
            Thread.sleep(100)
            val success = nativeInitLlm(resolvedModelPath!!, config.preloadSharedWeights)
            if (success) {
                Log.i(TAG, "MTK backend initialized successfully on attempt $currentAttempt")
                InitializationResult.Success("MTK backend initialized successfully")
            } else {
                Log.e(TAG, "MTK backend initialization returned false")
                cleanupAfterError()
                if (currentAttempt < maxAttempts) {
                    Thread.sleep(config.retryDelayMs)
                    return initializeMTKBackend()
                } else {
                    InitializationResult.Error("MTK backend initialization failed after $currentAttempt attempts")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during MTK backend initialization", e)
            cleanupAfterError()
            if (currentAttempt < maxAttempts) {
                try {
                    Thread.sleep(config.retryDelayMs)
                    return initializeMTKBackend()
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return InitializationResult.Error("Initialization interrupted")
                }
            } else {
                InitializationResult.Error("MTK backend initialization failed: ${e.message}")
            }
        }
    }

    private fun cleanupAfterError() {
        try {
            nativeResetLlm()
            Thread.sleep(100)
            nativeReleaseLlm()
        } catch (e: Exception) {
            Log.w(TAG, "Error during cleanup after error", e)
        }
    }

    private fun performSingleInference(
        inputText: String,
        maxTokens: Int,
        temperature: Float,
        topK: Int,
        repetitionPenalty: Float
    ): String {
        return nativeStreamingInference(
            inputText,
            maxTokens,
            false,
            object : TokenCallback {
                override fun onToken(token: String) {}
            },
            temperature,
            topK,
            repetitionPenalty
        )
    }

    private fun performStreamingInference(
        inputText: String,
        maxTokens: Int,
        temperature: Float,
        topK: Int,
        repetitionPenalty: Float
    ): String {
        val response = StringBuilder()
        nativeStreamingInference(
            inputText,
            maxTokens,
            false,
            object : TokenCallback {
                override fun onToken(token: String) {
                    response.append(token)
                }
            },
            temperature,
            topK,
            repetitionPenalty
        )
        return response.toString()
    }

    sealed class InitializationResult {
        data class Success(val message: String) : InitializationResult()
        data class Error(val message: String) : InitializationResult()
        fun isSuccess(): Boolean = this is Success
        fun isError(): Boolean = this is Error
        fun getErrorMessage(): String? = (this as? Error)?.message
    }

    /**
     * Token 回調介面
     * 用於接收串流推論過程中的 token 回調
     */
    interface TokenCallback {
        /**
         * 當收到新的 token 時調用
         * @param token 新生成的 token
         */
        fun onToken(token: String)
    }

    // JNI 庫載入狀態
    private var isLibraryLoaded = false
    init {
        try {
            System.loadLibrary("llm_jni")
            isLibraryLoaded = true
            Log.d(TAG, "MTK JNI library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            isLibraryLoaded = false
            Log.e(TAG, "Failed to load MTK JNI library", e)
        }
    }

    private fun nativeInitLlm(modelPath: String, preloadSharedWeights: Boolean): Boolean {
        if (!isLibraryLoaded) {
            Log.e(TAG, "JNI library not loaded, cannot initialize LLM")
            return false
        }
        Log.d(TAG, "Initializing LLM with path: $modelPath, preloadSharedWeights: $preloadSharedWeights")
        return try {
            val result = nativeInitLlmImpl(modelPath, preloadSharedWeights)
            Log.d(TAG, "LLM initialization result: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error during LLM initialization", e)
            false
        }
    }

    private fun nativeStreamingInference(
        prompt: String,
        maxTokens: Int,
        parsePromptTokens: Boolean,
        callback: TokenCallback,
        temperature: Float,
        topK: Int,
        repetitionPenalty: Float
    ): String {
        if (!isLibraryLoaded) {
            Log.e(TAG, "JNI library not loaded, cannot perform inference")
            return ""
        }
        Log.d(TAG, "Starting streaming inference - prompt length: ${prompt.length}, maxTokens: $maxTokens, parsePromptTokens: $parsePromptTokens")
        Log.d(TAG, "Inference parameters - temperature: $temperature, topK: $topK, repetitionPenalty: $repetitionPenalty")
        return try {
            val result = nativeStreamingInferenceImpl(
                prompt,
                maxTokens,
                parsePromptTokens,
                callback,
                temperature,
                topK,
                repetitionPenalty
            )
            Log.d(TAG, "Streaming inference completed, result length: ${result.length}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error during streaming inference", e)
            ""
        }
    }

    private fun nativeResetLlm() {
        if (!isLibraryLoaded) {
            Log.w(TAG, "JNI library not loaded, cannot reset LLM")
            return
        }
        Log.d(TAG, "Resetting LLM state")
        try {
            nativeResetLlmImpl()
            Log.d(TAG, "LLM state reset successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during LLM reset", e)
        }
    }

    private fun nativeReleaseLlm() {
        if (!isLibraryLoaded) {
            Log.w(TAG, "JNI library not loaded, cannot release LLM")
            return
        }
        Log.d(TAG, "Releasing LLM resources")
        try {
            nativeReleaseLlmImpl()
            Log.d(TAG, "LLM resources released successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during LLM release", e)
        }
    }

    private fun nativeSwapModel(tokenSize: Int): Boolean {
        if (!isLibraryLoaded) {
            Log.w(TAG, "JNI library not loaded, cannot swap model")
            return false
        }
        Log.d(TAG, "Swapping model with cache size: $tokenSize tokens")
        return try {
            val result = nativeSwapModelImpl(tokenSize)
            Log.d(TAG, "Model swap result: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error during model swap", e)
            false
        }
    }

    /**
     * Perform safe cleanup to prevent concurrent access to native resources
     */
    private fun performSafeCleanup(context: String) {
        // Skip cleanup if already cleaning up or if service is shutting down
        if (isCleaningUp.get()) {
            Log.d(TAG, "Skipping cleanup for $context - already cleaning up")
            return
        }
        
        try {
            Log.d(TAG, "Performing safe cleanup for $context")
            nativeResetLlm()
            nativeSwapModel(config.modelTokenSize)
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up MTK state for $context", e)
        }
    }

    // ============== Native 方法實作 ==============
    private external fun nativeInitLlmImpl(modelPath: String, preloadSharedWeights: Boolean): Boolean
    private external fun nativeStreamingInferenceImpl(
        prompt: String,
        maxTokens: Int,
        parsePromptTokens: Boolean,
        callback: TokenCallback,
        temperature: Float,
        topK: Int,
        repetitionPenalty: Float
    ): String
    private external fun nativeResetLlmImpl()
    private external fun nativeReleaseLlmImpl()
    private external fun nativeSwapModelImpl(tokenSize: Int): Boolean
    
    // Helper methods for model configuration
    // Removed ModelConfig helper methods - no longer needed with JSON-based architecture
} 