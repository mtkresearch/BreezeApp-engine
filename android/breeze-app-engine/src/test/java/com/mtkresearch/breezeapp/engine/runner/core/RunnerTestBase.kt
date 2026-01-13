package com.mtkresearch.breezeapp.engine.runner.core

import android.util.Log
import com.mtkresearch.breezeapp.engine.model.EngineSettings
import com.mtkresearch.breezeapp.engine.model.InferenceRequest
import com.mtkresearch.breezeapp.engine.model.InferenceResult
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import java.util.UUID

/**
 * RunnerTestBase - 統一的 Runner 測試基礎類別
 * 
 * 為所有 Runner 測試提供標準化的測試設置：
 * - Android Log mocking 基礎設施
 * - 標準化的 setUp/tearDown 流程
 * - 通用測試工具方法
 * - 效能測量工具
 * 
 * ## 使用方式
 * ```kotlin
 * class MyRunnerTest : RunnerTestBase<MyRunner>() {
 *     override fun createRunner() = MyRunner()
 *     override val defaultModelId = "my-model-id"
 *     
 *     @Test
 *     fun `my custom test`() {
 *         runner.load(defaultModelId, testSettings)
 *         // ... test logic
 *     }
 * }
 * ```
 * 
 * @param T Runner 類型，必須實作 BaseRunner
 * @since Engine API v2.2
 */
abstract class RunnerTestBase<T : BaseRunner> {
    
    /**
     * 建立要測試的 Runner 實例
     */
    protected abstract fun createRunner(): T
    
    /**
     * 預設的 Model ID
     */
    protected abstract val defaultModelId: String
    
    /**
     * Runner 實例 - 在 setUp 時初始化
     */
    protected lateinit var runner: T
    
    /**
     * 預設的 Engine 設定
     */
    protected val testSettings = EngineSettings()
    
    /**
     * 測試開始時間戳記 - 用於效能測量
     */
    protected var testStartTime: Long = 0
    
    /**
     * 基礎設置 - 在每個測試前執行
     * 
     * 執行項目：
     * 1. Mock Android Log 類別
     * 2. 建立 Runner 實例
     */
    @Before
    open fun baseSetUp() {
        // Mock Android Log to avoid RuntimeException in JVM tests
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.v(any(), any()) } returns 0
        
        // Create runner instance
        runner = createRunner()
        
        // Record start time
        testStartTime = System.currentTimeMillis()
    }
    
    /**
     * 基礎清理 - 在每個測試後執行
     * 
     * 執行項目：
     * 1. 卸載 Runner (如果已載入)
     * 2. 取消 Log mocking
     */
    @After
    open fun baseTearDown() {
        try {
            if (::runner.isInitialized && runner.isLoaded()) {
                runner.unload()
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
        unmockkStatic(Log::class)
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // Test Utility Methods
    // ═══════════════════════════════════════════════════════════════════
    
    /**
     * 建立標準文字輸入請求
     */
    protected fun createTextRequest(
        text: String = "Hello, world!",
        sessionId: String = UUID.randomUUID().toString()
    ): InferenceRequest {
        return InferenceRequest(
            sessionId = sessionId,
            inputs = mapOf(InferenceRequest.INPUT_TEXT to text)
        )
    }
    
    /**
     * 建立空輸入請求
     */
    protected fun createEmptyRequest(
        sessionId: String = UUID.randomUUID().toString()
    ): InferenceRequest {
        return InferenceRequest(
            sessionId = sessionId,
            inputs = emptyMap()
        )
    }
    
    /**
     * 建立音訊輸入請求 (用於 ASR)
     */
    protected fun createAudioRequest(
        audioData: ByteArray = ByteArray(1024) { it.toByte() },
        sampleRate: Int = 16000,
        sessionId: String = UUID.randomUUID().toString()
    ): InferenceRequest {
        return InferenceRequest(
            sessionId = sessionId,
            inputs = mapOf(
                InferenceRequest.INPUT_AUDIO to audioData,
                "sample_rate" to sampleRate
            )
        )
    }
    
    /**
     * 載入 Runner 並驗證成功
     */
    protected fun loadRunnerSuccessfully(
        modelId: String = defaultModelId,
        settings: EngineSettings = testSettings,
        params: Map<String, Any> = emptyMap()
    ): Boolean {
        val result = runner.load(modelId, settings, params)
        assert(result) { "Runner should load successfully" }
        assert(runner.isLoaded()) { "Runner should be in loaded state" }
        return result
    }
    
    /**
     * 執行推論並驗證基本成功
     */
    protected fun runAndAssertSuccess(request: InferenceRequest): InferenceResult {
        val result = runner.run(request)
        assert(result.error == null) { "Result should not have error: ${result.error}" }
        return result
    }
    
    /**
     * 測量執行時間
     */
    protected fun <R> measureTime(block: () -> R): Pair<R, Long> {
        val start = System.currentTimeMillis()
        val result = block()
        val elapsed = System.currentTimeMillis() - start
        return result to elapsed
    }
    
    /**
     * 取得測試已執行時間
     */
    protected fun getElapsedTestTime(): Long {
        return System.currentTimeMillis() - testStartTime
    }
    
    /**
     * 從結果取得文字輸出
     */
    protected fun getTextOutput(result: InferenceResult): String? {
        return result.outputs[InferenceResult.OUTPUT_TEXT] as? String
    }
    
    /**
     * 從結果取得音訊輸出
     */
    protected fun getAudioOutput(result: InferenceResult): ByteArray? {
        return result.outputs[InferenceResult.OUTPUT_AUDIO] as? ByteArray
    }
    
    /**
     * 驗證結果包含必要的 metadata
     */
    /**
     * 驗證結果包含必要的 metadata
     */
    protected fun assertHasMetadata(result: InferenceResult, vararg keys: String) {
        keys.forEach { key ->
            assert(result.metadata.containsKey(key)) { 
                "Result should contain metadata key: $key" 
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // Data-Driven Tests from Configuration
    // ═══════════════════════════════════════════════════════════════════
    
    @org.junit.Test
    fun `config - execute configured test cases`() {
        val config = RunnerTestConfig.fromSystemProperties()
        
        if (config.testCases.isEmpty()) {
            return
        }

        // Filter based on runner class if specified
        if (config.runnerClass.isNotEmpty()) {
             val currentName = runner::class.simpleName
             val currentFull = runner::class.java.name
             if (config.runnerClass != currentName && config.runnerClass != currentFull) {
                 println("Skipping configured tests: Config targets ${config.runnerClass} but this test is for $currentName")
                 return
             }
        }
        
        println("Executing ${config.testCases.size} configured test cases for ${runner::class.simpleName}...")
        
        // Load runner if not already loaded (or reload with config params)
        if (!runner.isLoaded()) {
            loadRunnerSuccessfully(
                modelId = config.modelId.ifEmpty { defaultModelId },
                params = config.toParameterMap()
            )
        }
        
        val executor = TestCaseExecutor()
        val results = executor.executeTestCasesFromConfig(runner, config.testCases)
        
        // Report
        println(executor.formatResults(results))
        
        val failed = results.filter { !it.passed }
        if (failed.isNotEmpty()) {
            throw AssertionError("Failed ${failed.size} configured test cases. See output for details.")
        }
    }
}
