package com.mtkresearch.breezeapp.engine.runner.mock

import android.util.Log
import com.mtkresearch.breezeapp.engine.annotation.AIRunner
import com.mtkresearch.breezeapp.engine.annotation.RunnerPriority
import com.mtkresearch.breezeapp.engine.annotation.VendorType
import com.mtkresearch.breezeapp.engine.runner.core.BaseRunner
import com.mtkresearch.breezeapp.engine.runner.core.FlowStreamingRunner
import com.mtkresearch.breezeapp.engine.runner.core.RunnerInfo
import com.mtkresearch.breezeapp.engine.runner.core.ParameterSchema
import com.mtkresearch.breezeapp.engine.runner.core.ParameterType
import com.mtkresearch.breezeapp.engine.runner.core.SelectionOption
import com.mtkresearch.breezeapp.engine.runner.core.ValidationResult
import com.mtkresearch.breezeapp.engine.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MockLLMRunner
 * 
 * 模擬大語言模型推論的 Runner 實作
 * 支援串流文字生成、可配置延遲和預定義回應
 * 
 * 功能特性：
 * - 支援串流和非串流模式
 * - 可配置的回應延遲
 * - 預定義回應庫
 * - 錯誤情況模擬
 * - 線程安全設計
 */
@AIRunner(
    vendor = VendorType.UNKNOWN,
    priority = RunnerPriority.LOW,
    capabilities = [CapabilityType.LLM]
)
class MockLLMRunner : BaseRunner, FlowStreamingRunner {
    
    companion object {
        private const val TAG = "MockLLMRunner"
        private const val DEFAULT_RESPONSE_DELAY = 100L
        private const val DEFAULT_STREAM_CHUNK_DELAY = 50L
    }
    
    private val isLoaded = AtomicBoolean(false)
    private var responseDelay = DEFAULT_RESPONSE_DELAY
    private var predefinedResponses = listOf(
        "這是一個模擬的 Language Model 回應。我正在協助您測試 Engine 架構的功能。",
        "我是 Mock Language Model Runner，專門用於驗證系統的擴展性和穩定性。",
        "Engine 架構運作正常！您的訊息已被成功處理。",
        "感謝您使用 Engine。系統正在使用模擬引擎進行回應。",
        "這是一個測試回應，用於驗證 Mock Runner 的串流功能是否正常運作。"
    )
    
    // Load model using model ID from JSON registry
    override fun load(modelId: String, settings: EngineSettings, initialParams: Map<String, Any>): Boolean {
        Log.d(TAG, "Loading MockLLMRunner with model: $modelId")
        
        // Extract parameters from settings for this runner
        val runnerParams = settings.getRunnerParameters("MockLLMRunner")
        responseDelay = runnerParams["response_delay_ms"] as? Long ?: DEFAULT_RESPONSE_DELAY
        val customResponses = runnerParams["predefined_responses"] as? List<String>
        if (customResponses != null) {
            predefinedResponses = customResponses
        }
        
        isLoaded.set(true)
        Log.d(TAG, "MockLLMRunner loaded successfully")
        return true
    }
    
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        if (!isLoaded.get()) {
            return InferenceResult.error(RunnerError.resourceUnavailable())
        }
        
        val prompt = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
        
        return try {
            // 模擬處理延遲
            Thread.sleep(responseDelay)
            
            val response = selectResponseFor(prompt)
            
            InferenceResult.success(
                outputs = mapOf(InferenceResult.OUTPUT_TEXT to response),
                metadata = mapOf(
                    InferenceResult.META_MODEL_NAME to "mock-llm-v1",
                    InferenceResult.META_PROCESSING_TIME_MS to responseDelay,
                    InferenceResult.META_TOKEN_COUNT to response.split(" ").size,
                    InferenceResult.META_SESSION_ID to input.sessionId,
                    InferenceResult.META_STREAM_MODE to stream
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in MockLLMRunner.run", e)
            InferenceResult.error(RunnerError.processingError(e.message ?: "Unknown error", e))
        }
    }
    
    override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = flow {
        if (!isLoaded.get()) {
            emit(InferenceResult.error(RunnerError.modelNotLoaded()))
            return@flow
        }
        
        val prompt = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
        
        try {
            val response = selectResponseFor(prompt)
            val words = response.split(" ")
            
            Log.d(TAG, "Starting stream response for session: ${input.sessionId}")
            
            // 模擬串流回應
            for ((index, word) in words.withIndex()) {
                delay(DEFAULT_STREAM_CHUNK_DELAY)
                
                val partialText = words.take(index + 1).joinToString(" ")
                val isPartial = index < words.size - 1
                
                emit(InferenceResult.success(
                    outputs = mapOf(InferenceResult.OUTPUT_TEXT to partialText),
                    metadata = mapOf(
                        InferenceResult.META_PARTIAL_TOKENS to index + 1,
                        InferenceResult.META_SESSION_ID to input.sessionId,
                        InferenceResult.META_MODEL_NAME to "mock-llm-v1"
                    ),
                    partial = isPartial
                ))
                
                // 提前結束條件檢查
                if (Thread.currentThread().isInterrupted) {
                    Log.d(TAG, "Stream interrupted for session: ${input.sessionId}")
                    break
                }
            }
            
            Log.d(TAG, "Stream completed for session: ${input.sessionId}")
        } catch (e: Exception) {
            Log.e(TAG, "Error in MockLLMRunner.runAsFlow", e)
            emit(InferenceResult.error(RunnerError.processingError(e.message ?: "Unknown error", e)))
        }
    }
    
    override fun unload() {
        Log.d(TAG, "Unloading MockLLMRunner")
        isLoaded.set(false)
    }
    
    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.LLM)
    
    override fun isLoaded(): Boolean = isLoaded.get()
    
    override fun getRunnerInfo(): RunnerInfo = RunnerInfo(
        name = "MockLLMRunner",
        version = "1.0.0",
        capabilities = getCapabilities(),
        description = "Mock implementation for Large Language Model inference"
    )
    
    override fun isSupported(): Boolean = true // Mock runners always supported
    
    override fun getParameterSchema(): List<ParameterSchema> {
        return listOf(
            ParameterSchema(
                name = "model_id",
                displayName = "Mock Model",
                description = "Select a mock model for testing (all models behave identically)",
                type = ParameterType.SelectionType(
                    options = listOf(
                        SelectionOption("mock-llm-basic", "Mock LLM Basic", "Basic mock language model"),
                        SelectionOption("mock-llm-advanced", "Mock LLM Advanced", "Advanced mock language model"),
                        SelectionOption("mock-llm-creative", "Mock LLM Creative", "Creative mock language model")
                    ),
                    allowMultiple = false
                ),
                defaultValue = "mock-llm-basic",
                isRequired = true,
                category = "Model Configuration"
            ),
            ParameterSchema(
                name = "response_delay_ms",
                displayName = "Response Delay (ms)",
                description = "Simulated delay before generating response (for testing purposes)",
                type = ParameterType.IntType(
                    minValue = 0,
                    maxValue = 5000,
                    step = 100
                ),
                defaultValue = DEFAULT_RESPONSE_DELAY,
                isRequired = false,
                category = "Simulation"
            ),
            ParameterSchema(
                name = "stream_chunk_delay_ms",
                displayName = "Stream Chunk Delay (ms)",
                description = "Delay between streaming chunks (simulates real-time generation)",
                type = ParameterType.IntType(
                    minValue = 10,
                    maxValue = 1000,
                    step = 10
                ),
                defaultValue = DEFAULT_STREAM_CHUNK_DELAY,
                isRequired = false,
                category = "Simulation"
            ),
            ParameterSchema(
                name = "response_style",
                displayName = "Response Style",
                description = "Style of mock responses to generate",
                type = ParameterType.SelectionType(
                    options = listOf(
                        SelectionOption("formal", "Formal", "Professional, structured responses"),
                        SelectionOption("casual", "Casual", "Friendly, conversational responses"),
                        SelectionOption("technical", "Technical", "Detailed technical explanations"),
                        SelectionOption("creative", "Creative", "Imaginative and varied responses"),
                        SelectionOption("random", "Random", "Randomly selected from predefined responses")
                    )
                ),
                defaultValue = "random",
                isRequired = false,
                category = "Content"
            ),
            ParameterSchema(
                name = "simulate_errors",
                displayName = "Simulate Errors",
                description = "Enable simulation of random errors for testing error handling",
                type = ParameterType.BooleanType,
                defaultValue = false,
                isRequired = false,
                category = "Testing"
            ),
            ParameterSchema(
                name = "error_rate",
                displayName = "Error Rate (%)",
                description = "Percentage chance of simulating an error (0-100)",
                type = ParameterType.IntType(
                    minValue = 0,
                    maxValue = 100,
                    step = 5
                ),
                defaultValue = 10,
                isRequired = false,
                category = "Testing"
            )
        )
    }

    override fun validateParameters(parameters: Map<String, Any>): ValidationResult {
        // Validate error simulation parameters
        val simulateErrors = parameters["simulate_errors"] as? Boolean ?: false
        val errorRate = (parameters["error_rate"] as? Number)?.toInt() ?: 10
        
        if (simulateErrors && errorRate > 50) {
            return ValidationResult.invalid("Error rate above 50% may make testing difficult")
        }
        
        // Validate delay parameters for reasonable testing
        val responseDelay = (parameters["response_delay_ms"] as? Number)?.toLong() ?: DEFAULT_RESPONSE_DELAY
        val streamDelay = (parameters["stream_chunk_delay_ms"] as? Number)?.toLong() ?: DEFAULT_STREAM_CHUNK_DELAY
        
        if (responseDelay > 3000) {
            return ValidationResult.invalid("Response delay above 3000ms may timeout in tests")
        }
        
        if (streamDelay > 500) {
            return ValidationResult.invalid("Stream chunk delay above 500ms creates poor user experience")
        }
        
        return ValidationResult.valid()
    }
    
    /**
     * 根據輸入選擇適當的回應
     */
    private fun selectResponseFor(prompt: String): String {
        return when {
            prompt.contains("測試", ignoreCase = true) -> 
                "這是一個測試回應，用於驗證 Mock Runner 的功能。測試進行中..."
            
            prompt.contains("錯誤", ignoreCase = true) -> 
                throw RuntimeException("模擬錯誤：這是一個測試用的錯誤情況，用於驗證錯誤處理機制。")
            
            prompt.contains("串流", ignoreCase = true) || prompt.contains("stream", ignoreCase = true) -> 
                "這是串流模式的測試回應。每個詞語都會逐步發送，模擬真實的 Language Model Runner推論過程。"
            
            prompt.contains("BreezeApp", ignoreCase = true) -> 
                "此APP是一個先進的 A I 應用程式，使用模組化的 Engine 架構來管理不同的 A I 引擎。"
            
            prompt.isEmpty() -> 
                "您好！我是 A I 助手。請問有什麼我可以協助您的嗎？"
            
            else -> predefinedResponses.random()
        }
    }
}