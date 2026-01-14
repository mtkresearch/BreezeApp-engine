package com.mtkresearch.breezeapp.engine.runner.tts

import com.mtkresearch.breezeapp.engine.runner.llm.RunnerContractTest
import com.mtkresearch.breezeapp.engine.runner.mock.MockTTSRunner
import org.junit.experimental.categories.Category

/**
 * MockTTSRunnerContractTest - MockTTSRunner 合規性測試
 * 
 * 繼承 TTSRunnerContractTest 以確保 MockTTSRunner 符合所有 TTS Runner 介面規範。
 */
@Category(RunnerContractTest::class)
class MockTTSRunnerContractTest : TTSRunnerContractTest<MockTTSRunner>() {
    
    override fun createRunner(): MockTTSRunner = MockTTSRunner()
    
    override val defaultModelId: String = "mock-tts-basic"
}
