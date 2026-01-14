package com.mtkresearch.breezeapp.engine.runner.llm

import com.mtkresearch.breezeapp.engine.runner.mtk.MTKLLMRunner
import org.junit.experimental.categories.Category

/**
 * MTKLLMRunnerContractTest - MediaTek NPU LLM Runner 合規性測試
 */
@Category(RunnerContractTest::class)
class MTKLLMRunnerContractTest : LLMRunnerContractTest<MTKLLMRunner>() {
    
    override fun createRunner(): MTKLLMRunner = MTKLLMRunner()
    
    override val defaultModelId: String = "breeze-instruct"
}
