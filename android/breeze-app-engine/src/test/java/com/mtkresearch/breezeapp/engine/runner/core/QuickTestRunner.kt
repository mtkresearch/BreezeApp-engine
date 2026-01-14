package com.mtkresearch.breezeapp.engine.runner.core

import com.mtkresearch.breezeapp.engine.model.EngineSettings
import com.mtkresearch.breezeapp.engine.model.InferenceRequest
import com.mtkresearch.breezeapp.engine.model.InferenceResult

/**
 * QuickTestRunner - 快速測試單一 Runner 的輸入/輸出
 * 
 * 用途：開發時快速驗證 Runner 行為，不需要完整的測試框架。
 * 
 * 使用方式：
 * ```kotlin
 * val quickTest = QuickTestRunner(MockLLMRunner())
 * val result = quickTest.test("What is 2+2?")
 * quickTest.assertContains(result, "4")
 * ```
 * 
 * @since Engine API v2.2
 */
class QuickTestRunner<T : BaseRunner>(
    private val runner: T,
    private val modelId: String = "default",
    private val settings: EngineSettings = EngineSettings()
) {
    
    private var isLoaded = false
    
    /**
     * 快速測試結果
     */
    data class QuickTestResult(
        val input: String,
        val output: String?,
        val error: String?,
        val durationMs: Long,
        val raw: InferenceResult
    ) {
        val success: Boolean get() = error == null && output != null
        
        override fun toString(): String {
            return buildString {
                appendLine("─────────────────────────────────────")
                appendLine("Input:  $input")
                appendLine("Output: ${output ?: "(none)"}")
                if (error != null) appendLine("Error:  $error")
                appendLine("Time:   ${durationMs}ms")
                appendLine("─────────────────────────────────────")
            }
        }
    }
    
    /**
     * 確保 Runner 已載入
     */
    private fun ensureLoaded() {
        if (!isLoaded) {
            if (runner.load(modelId, settings)) {
                isLoaded = true
            } else {
                throw IllegalStateException("Failed to load runner '$modelId'. Check logs for details.")
            }
        }
    }
    
    /**
     * 快速測試文字輸入
     */
    fun test(input: String): QuickTestResult {
        ensureLoaded()
        
        val request = InferenceRequest(
            sessionId = "quick-test-${System.currentTimeMillis()}",
            inputs = mapOf(InferenceRequest.INPUT_TEXT to input)
        )
        
        val startTime = System.currentTimeMillis()
        val result = runner.run(request)
        val duration = System.currentTimeMillis() - startTime
        
        val textOutput = result.outputs[InferenceResult.OUTPUT_TEXT] as? String
        
        return QuickTestResult(
            input = input,
            output = textOutput,
            error = result.error?.message,
            durationMs = duration,
            raw = result
        )
    }
    
    /**
     * 快速測試音訊輸入 (for ASR)
     */
    fun testAudio(audioData: ByteArray, sampleRate: Int = 16000): QuickTestResult {
        ensureLoaded()
        
        val request = InferenceRequest(
            sessionId = "quick-test-${System.currentTimeMillis()}",
            inputs = mapOf(
                InferenceRequest.INPUT_AUDIO to audioData,
                "sample_rate" to sampleRate
            )
        )
        
        val startTime = System.currentTimeMillis()
        val result = runner.run(request)
        val duration = System.currentTimeMillis() - startTime
        
        val textOutput = result.outputs[InferenceResult.OUTPUT_TEXT] as? String
        
        return QuickTestResult(
            input = "[audio: ${audioData.size} bytes @ ${sampleRate}Hz]",
            output = textOutput,
            error = result.error?.message,
            durationMs = duration,
            raw = result
        )
    }
    
    /**
     * 斷言輸出完全等於期望值
     */
    fun assertEquals(result: QuickTestResult, expected: String): Boolean {
        val passed = result.output == expected
        if (!passed) {
            println("❌ FAIL: Expected '$expected' but got '${result.output}'")
        } else {
            println("✅ PASS: Output equals '$expected'")
        }
        return passed
    }
    
    /**
     * 斷言輸出包含指定文字
     */
    fun assertContains(result: QuickTestResult, substring: String): Boolean {
        val passed = result.output?.contains(substring) == true
        if (!passed) {
            println("❌ FAIL: Expected to contain '$substring' but got '${result.output}'")
        } else {
            println("✅ PASS: Output contains '$substring'")
        }
        return passed
    }
    
    /**
     * 斷言無錯誤
     */
    fun assertNoError(result: QuickTestResult): Boolean {
        val passed = result.error == null
        if (!passed) {
            println("❌ FAIL: Expected no error but got '${result.error}'")
        } else {
            println("✅ PASS: No error")
        }
        return passed
    }
    
    /**
     * 斷言回應時間在指定毫秒內
     */
    fun assertTimeUnder(result: QuickTestResult, maxMs: Long): Boolean {
        val passed = result.durationMs <= maxMs
        if (!passed) {
            println("❌ FAIL: Expected response time under ${maxMs}ms but took ${result.durationMs}ms")
        } else {
            println("✅ PASS: Response time ${result.durationMs}ms <= ${maxMs}ms")
        }
        return passed
    }
    
    /**
     * 批次測試多個輸入
     */
    fun testBatch(inputs: List<String>): List<QuickTestResult> {
        return inputs.map { input ->
            val result = test(input)
            println(result)
            result
        }
    }
    
    /**
     * 批次測試並驗證期望輸出
     */
    fun testBatchWithExpected(testCases: Map<String, String>): Int {
        var passed = 0
        var failed = 0
        
        testCases.forEach { (input, expected) ->
            val result = test(input)
            if (assertContains(result, expected)) {
                passed++
            } else {
                failed++
            }
        }
        
        println("\n══════════════════════════════════════")
        println("  Results: $passed passed, $failed failed")
        println("══════════════════════════════════════")
        
        return failed
    }
    
    /**
     * 釋放資源
     */
    fun close() {
        if (isLoaded) {
            runner.unload()
            isLoaded = false
        }
    }
}

/**
 * 便利函數：快速測試 LLM Runner
 */
fun <T : BaseRunner> T.quickTest(
    input: String,
    modelId: String = "default"
): QuickTestRunner.QuickTestResult {
    val tester = QuickTestRunner(this, modelId)
    return try {
        tester.test(input)
    } finally {
        tester.close()
    }
}
