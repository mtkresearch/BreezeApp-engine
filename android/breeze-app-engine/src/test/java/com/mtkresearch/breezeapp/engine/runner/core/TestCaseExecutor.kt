package com.mtkresearch.breezeapp.engine.runner.core

import com.mtkresearch.breezeapp.engine.model.InferenceRequest
import com.mtkresearch.breezeapp.engine.model.InferenceResult

/**
 * TestCaseExecutor - 執行 JSON 配置中定義的測試案例
 * 
 * 支援的斷言類型：
 * - NO_ERROR: 確認無錯誤
 * - OUTPUT_NOT_EMPTY: 確認指定欄位非空
 * - OUTPUT_EQUALS: 確認輸出完全匹配
 * - OUTPUT_CONTAINS: 確認輸出包含指定文字
 * - OUTPUT_MATCHES: 確認輸出匹配正則表達式
 * - RESPONSE_TIME_UNDER: 確認回應時間在指定毫秒內
 * 
 * @since Engine API v2.2
 */
class TestCaseExecutor {
    
    /**
     * 測試案例定義
     */
    data class TestCase(
        val name: String,
        val description: String = "",
        val input: Map<String, Any>,
        val expectedOutput: Map<String, Any>? = null,
        val assertions: List<Assertion> = emptyList(),
        val tags: List<String> = emptyList()
    )
    
    /**
     * 斷言定義
     */
    data class Assertion(
        val type: AssertionType,
        val field: String = "",
        val expected: String = ""
    )
    
    /**
     * 斷言類型
     */
    enum class AssertionType {
        NO_ERROR,
        OUTPUT_NOT_EMPTY,
        OUTPUT_EQUALS,
        OUTPUT_CONTAINS,
        OUTPUT_MATCHES,
        RESPONSE_TIME_UNDER
    }
    
    /**
     * 測試結果
     */
    data class TestCaseResult(
        val testCase: TestCase,
        val passed: Boolean,
        val actualOutput: Map<String, Any?>,
        val error: String? = null,
        val durationMs: Long,
        val failedAssertions: List<AssertionFailure> = emptyList()
    )
    
    /**
     * 斷言失敗詳情
     */
    data class AssertionFailure(
        val assertion: Assertion,
        val actualValue: Any?,
        val message: String
    )
    
    /**
     * 執行單一測試案例
     */
    fun executeTestCase(
        runner: BaseRunner,
        testCase: TestCase
    ): TestCaseResult {
        val startTime = System.currentTimeMillis()
        
        // 建立 InferenceRequest
        val request = InferenceRequest(
            sessionId = "test-${System.currentTimeMillis()}",
            inputs = testCase.input.mapValues { it.value }
        )
        
        // 執行推論
        val result = try {
            runner.run(request)
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            return TestCaseResult(
                testCase = testCase,
                passed = false,
                actualOutput = emptyMap(),
                error = "Exception: ${e.message}",
                durationMs = duration
            )
        }
        
        val duration = System.currentTimeMillis() - startTime
        val failedAssertions = mutableListOf<AssertionFailure>()
        
        // 執行所有斷言
        for (assertion in testCase.assertions) {
            val failure = checkAssertion(assertion, result, duration)
            if (failure != null) {
                failedAssertions.add(failure)
            }
        }
        
        // 如果有 expectedOutput，也進行比對
        testCase.expectedOutput?.forEach { (field, expected) ->
            val actual = result.outputs[field]
            if (actual?.toString() != expected.toString()) {
                failedAssertions.add(
                    AssertionFailure(
                        assertion = Assertion(AssertionType.OUTPUT_EQUALS, field, expected.toString()),
                        actualValue = actual,
                        message = "Expected '$expected' but got '$actual'"
                    )
                )
            }
        }
        
        return TestCaseResult(
            testCase = testCase,
            passed = failedAssertions.isEmpty() && result.error == null,
            actualOutput = result.outputs,
            error = result.error?.message,
            durationMs = duration,
            failedAssertions = failedAssertions
        )
    }
    
    /**
     * 執行多個測試案例 (使用 TestCaseConfig)
     */
    fun executeTestCasesFromConfig(
        runner: BaseRunner,
        testCases: List<TestCaseConfig>,
        stopOnFirstFailure: Boolean = false
    ): List<TestCaseResult> {
        return executeTestCases(
            runner = runner,
            testCases = testCases.map { mapToTestCase(it) },
            stopOnFirstFailure = stopOnFirstFailure
        )
    }

    /**
     * 執行多個測試案例
     */
    fun executeTestCases(
        runner: BaseRunner,
        testCases: List<TestCase>,
        stopOnFirstFailure: Boolean = false
    ): List<TestCaseResult> {
        val results = mutableListOf<TestCaseResult>()
        
        for (testCase in testCases) {
            val result = executeTestCase(runner, testCase)
            results.add(result)
            
            if (stopOnFirstFailure && !result.passed) {
                break
            }
        }
        
        return results
    }

    private fun mapToTestCase(config: TestCaseConfig): TestCase {
        return TestCase(
            name = config.name,
            description = config.description,
            input = config.toInputMap(),
            expectedOutput = config.expectedOutput?.mapValues { it.value },
            assertions = config.assertions.map { mapAssertion(it) },
            tags = emptyList() // Config doesn't have tags per case yet
        )
    }

    private fun mapAssertion(config: AssertionConfig): Assertion {
        val type = when(config.type) {
            com.mtkresearch.breezeapp.engine.runner.core.AssertionType.NO_ERROR -> AssertionType.NO_ERROR
            com.mtkresearch.breezeapp.engine.runner.core.AssertionType.OUTPUT_NOT_EMPTY -> AssertionType.OUTPUT_NOT_EMPTY
            com.mtkresearch.breezeapp.engine.runner.core.AssertionType.OUTPUT_CONTAINS -> AssertionType.OUTPUT_CONTAINS
            com.mtkresearch.breezeapp.engine.runner.core.AssertionType.OUTPUT_MATCHES -> AssertionType.OUTPUT_MATCHES
            com.mtkresearch.breezeapp.engine.runner.core.AssertionType.RESPONSE_TIME_UNDER -> AssertionType.RESPONSE_TIME_UNDER
            // Map others if possible or default
            else -> AssertionType.NO_ERROR // Fallback or throw
        }
        return Assertion(
            type = type,
            field = config.field,
            expected = config.expected ?: ""
        )
    }
    
    /**
     * 檢查單一斷言
     */
    private fun checkAssertion(
        assertion: Assertion,
        result: InferenceResult,
        durationMs: Long
    ): AssertionFailure? {
        return when (assertion.type) {
            AssertionType.NO_ERROR -> {
                if (result.error != null) {
                    AssertionFailure(
                        assertion = assertion,
                        actualValue = result.error,
                        message = "Expected no error but got: ${result.error?.message}"
                    )
                } else null
            }
            
            AssertionType.OUTPUT_NOT_EMPTY -> {
                val value = result.outputs[assertion.field]
                if (value == null || value.toString().isEmpty()) {
                    AssertionFailure(
                        assertion = assertion,
                        actualValue = value,
                        message = "Expected '${assertion.field}' to be non-empty"
                    )
                } else null
            }
            
            AssertionType.OUTPUT_EQUALS -> {
                val actual = result.outputs[assertion.field]?.toString() ?: ""
                if (actual != assertion.expected) {
                    AssertionFailure(
                        assertion = assertion,
                        actualValue = actual,
                        message = "Expected '${assertion.expected}' but got '$actual'"
                    )
                } else null
            }
            
            AssertionType.OUTPUT_CONTAINS -> {
                val actual = result.outputs[assertion.field]?.toString() ?: ""
                if (!actual.contains(assertion.expected)) {
                    AssertionFailure(
                        assertion = assertion,
                        actualValue = actual,
                        message = "Expected to contain '${assertion.expected}' but got '$actual'"
                    )
                } else null
            }
            
            AssertionType.OUTPUT_MATCHES -> {
                val actual = result.outputs[assertion.field]?.toString() ?: ""
                val regex = Regex(assertion.expected)
                if (!regex.matches(actual)) {
                    AssertionFailure(
                        assertion = assertion,
                        actualValue = actual,
                        message = "Expected to match '${assertion.expected}' but got '$actual'"
                    )
                } else null
            }
            
            AssertionType.RESPONSE_TIME_UNDER -> {
                val maxMs = assertion.expected.toLongOrNull() ?: Long.MAX_VALUE
                if (durationMs > maxMs) {
                    AssertionFailure(
                        assertion = assertion,
                        actualValue = durationMs,
                        message = "Expected response time under ${maxMs}ms but took ${durationMs}ms"
                    )
                } else null
            }
        }
    }
    
    /**
     * 格式化測試結果為可讀字串
     */
    fun formatResults(results: List<TestCaseResult>): String {
        val sb = StringBuilder()
        val passed = results.count { it.passed }
        val failed = results.size - passed
        
        sb.appendLine("═══════════════════════════════════════════════════════════════")
        sb.appendLine("  Test Results: $passed passed, $failed failed (${results.size} total)")
        sb.appendLine("═══════════════════════════════════════════════════════════════")
        sb.appendLine()
        
        results.forEachIndexed { index, result ->
            val status = if (result.passed) "✓ PASS" else "✗ FAIL"
            sb.appendLine("${index + 1}. [${status}] ${result.testCase.name} (${result.durationMs}ms)")
            
            if (!result.passed) {
                result.error?.let { sb.appendLine("   Error: $it") }
                result.failedAssertions.forEach { failure ->
                    sb.appendLine("   - ${failure.message}")
                }
            }
        }
        
        sb.appendLine()
        sb.appendLine("═══════════════════════════════════════════════════════════════")
        
        return sb.toString()
    }
}
