package com.mtkresearch.breezeapp.engine.runner.llm

import com.mtkresearch.breezeapp.engine.runner.llamastack.LlamaStackRunner
import com.mtkresearch.breezeapp.engine.test.TestPrerequisites
import org.junit.Before
import org.junit.experimental.categories.Category

/**
 * LlamaStackRunnerContractTest - LlamaStack LLM Runner 合規性測試
 * 
 * Requires: LlamaStack server running and network access
 * Run with: LlamaStack server + ./gradlew test --tests "*LlamaStackRunnerContractTest*"
 */
@Category(RunnerContractTest::class)
class LlamaStackRunnerContractTest : LLMRunnerContractTest<LlamaStackRunner>() {
    
    @Before
    fun checkPrerequisites() {
        TestPrerequisites.requireNetwork()
        // LlamaStack server should be running - check via env var or assume network test is enough
    }
    
    override fun createRunner(): LlamaStackRunner = LlamaStackRunner()
    
    override val defaultModelId: String = "llama-3.2-1b"
}

