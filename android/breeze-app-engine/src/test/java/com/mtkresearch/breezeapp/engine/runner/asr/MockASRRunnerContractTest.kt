package com.mtkresearch.breezeapp.engine.runner.asr

import com.mtkresearch.breezeapp.engine.runner.llm.RunnerContractTest
import com.mtkresearch.breezeapp.engine.runner.mock.MockASRRunner
import org.junit.experimental.categories.Category

/**
 * MockASRRunnerContractTest - MockASRRunner 合規性測試
 * 
 * 繼承 ASRRunnerContractTest 以確保 MockASRRunner 符合所有 ASR Runner 介面規範。
 */
@Category(RunnerContractTest::class)
class MockASRRunnerContractTest : ASRRunnerContractTest<MockASRRunner>() {
    
    override fun createRunner(): MockASRRunner = MockASRRunner()
    
    override val defaultModelId: String = "mock-asr-basic"
}
