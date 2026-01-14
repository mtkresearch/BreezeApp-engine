package com.mtkresearch.breezeapp.engine.runner.llm

import com.mtkresearch.breezeapp.engine.runner.core.QuickTestRunner
import com.mtkresearch.breezeapp.engine.runner.core.TestCaseExecutor
import com.mtkresearch.breezeapp.engine.runner.core.TestCaseExecutor.*
import com.mtkresearch.breezeapp.engine.runner.core.quickTest
import com.mtkresearch.breezeapp.engine.runner.mock.MockLLMRunner
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * QuickTestDemo - 展示 QuickTestRunner 和 TestCaseExecutor 的使用方式
 * 
 * 這個測試類別示範如何快速驗證 Runner 的輸入/輸出行為。
 */
class QuickTestDemo {
    
    private lateinit var runner: MockLLMRunner
    
    @Before
    fun setUp() {
        runner = MockLLMRunner()
    }
    
    @After
    fun tearDown() {
        runner.unload()
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // QuickTestRunner 使用範例
    // ═══════════════════════════════════════════════════════════════════
    
    @Test
    fun `demo - quick test single input`() {
        val quickTest = QuickTestRunner(runner, "mock-llm-basic")
        
        val result = quickTest.test("Hello, world!")
        
        println(result)
        
        assertTrue("Should have output", result.success)
        quickTest.assertNoError(result)
        
        quickTest.close()
    }
    
    @Test
    fun `demo - quick test with assertion`() {
        val quickTest = QuickTestRunner(runner, "mock-llm-basic")
        
        val result = quickTest.test("Tell me about AI")
        
        // 驗證輸出包含特定文字
        quickTest.assertNoError(result)
        
        quickTest.close()
    }
    
    @Test
    fun `demo - quick test batch with expected outputs`() {
        val quickTest = QuickTestRunner(runner, "mock-llm-basic")
        
        // 批次測試：輸入 → 期望輸出包含的文字
        val testCases = mapOf(
            "Hello" to "",      // MockLLMRunner 會回應任何文字
            "How are you?" to ""
        )
        
        val failed = quickTest.testBatchWithExpected(testCases)
        
        assertEquals("All tests should pass", 0, failed)
        
        quickTest.close()
    }
    
    @Test
    fun `demo - extension function quick test`() {
        // 使用便利的擴展函數
        val result = runner.quickTest("What is AI?", "mock-llm-basic")
        
        println("Quick test result: $result")
        
        assertTrue("Should succeed", result.success)
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // TestCaseExecutor 使用範例
    // ═══════════════════════════════════════════════════════════════════
    
    @Test
    fun `demo - test case executor with assertions`() {
        runner.load("mock-llm-basic", com.mtkresearch.breezeapp.engine.model.EngineSettings())
        
        val executor = TestCaseExecutor()
        
        val testCase = TestCase(
            name = "基本文字回應測試",
            description = "驗證 MockLLMRunner 能正確回應文字輸入",
            input = mapOf("text" to "Hello, how are you?"),
            assertions = listOf(
                Assertion(AssertionType.NO_ERROR),
                Assertion(AssertionType.OUTPUT_NOT_EMPTY, field = "text")
            )
        )
        
        val result = executor.executeTestCase(runner, testCase)
        
        println("Test: ${result.testCase.name}")
        println("Passed: ${result.passed}")
        println("Duration: ${result.durationMs}ms")
        println("Output: ${result.actualOutput}")
        
        assertTrue("Test should pass", result.passed)
    }
    
    @Test
    fun `demo - test case executor with multiple cases`() {
        runner.load("mock-llm-basic", com.mtkresearch.breezeapp.engine.model.EngineSettings())
        
        val executor = TestCaseExecutor()
        
        val testCases = listOf(
            TestCase(
                name = "測試 1: 基本問候",
                input = mapOf("text" to "Hello"),
                assertions = listOf(
                    Assertion(AssertionType.NO_ERROR),
                    Assertion(AssertionType.OUTPUT_NOT_EMPTY, "text")
                )
            ),
            TestCase(
                name = "測試 2: 長文本輸入",
                input = mapOf("text" to "This is a longer input text that should be processed correctly."),
                assertions = listOf(
                    Assertion(AssertionType.NO_ERROR),
                    Assertion(AssertionType.RESPONSE_TIME_UNDER, expected = "5000")
                )
            ),
            TestCase(
                name = "測試 3: Unicode 輸入",
                input = mapOf("text" to "你好，世界！"),
                assertions = listOf(
                    Assertion(AssertionType.NO_ERROR)
                )
            )
        )
        
        val results = executor.executeTestCases(runner, testCases)
        
        println(executor.formatResults(results))
        
        val allPassed = results.all { it.passed }
        assertTrue("All tests should pass", allPassed)
    }
}
