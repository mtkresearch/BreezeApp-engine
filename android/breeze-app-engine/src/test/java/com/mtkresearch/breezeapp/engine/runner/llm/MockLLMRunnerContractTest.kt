package com.mtkresearch.breezeapp.engine.runner.llm

import com.mtkresearch.breezeapp.engine.runner.mock.MockLLMRunner
import org.junit.experimental.categories.Category

/**
 * MockLLMRunnerContractTest - MockLLMRunner 合規性測試
 * 
 * 繼承 LLMRunnerContractTest 以確保 MockLLMRunner 符合所有 LLM Runner 介面規範。
 * 
 * 標籤：
 * - runner-contract: 合規性測試
 * - mock: Mock Runner 測試
 * - ci-required: CI 必要測試
 */
@Category(RunnerContractTest::class)
class MockLLMRunnerContractTest : LLMRunnerContractTest<MockLLMRunner>() {
    
    override fun createRunner(): MockLLMRunner = MockLLMRunner()
    
    override val defaultModelId: String = "mock-llm-basic"
}

/**
 * 標記介面 - 用於 JUnit Category 過濾
 */
interface RunnerContractTest
