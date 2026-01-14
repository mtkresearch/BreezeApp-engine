package com.mtkresearch.breezeapp.engine.runner.llm

import com.mtkresearch.breezeapp.engine.runner.llamastack.LlamaStackRunner
import org.junit.experimental.categories.Category

/**
 * LlamaStackRunnerContractTest - LlamaStack LLM Runner 合規性測試
 */
@Category(RunnerContractTest::class)
class LlamaStackRunnerContractTest : LLMRunnerContractTest<LlamaStackRunner>() {
    
    override fun createRunner(): LlamaStackRunner = LlamaStackRunner()
    
    override val defaultModelId: String = "llama-3.2-1b"
}
