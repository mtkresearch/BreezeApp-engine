package com.mtkresearch.breezeapp.engine.runner.llm

import com.mtkresearch.breezeapp.engine.runner.openrouter.OpenRouterLLMRunner
import com.mtkresearch.breezeapp.engine.test.TestPrerequisites
import org.junit.Before
import org.junit.experimental.categories.Category

/**
 * OpenRouterLLMRunnerContractTest - OpenRouter LLM Runner 合規性測試
 * 
 * Requires: OPENROUTER_API_KEY environment variable
 * Run with: OPENROUTER_API_KEY=sk-xxx ./gradlew test --tests "*OpenRouterLLMRunnerContractTest*"
 */
@Category(RunnerContractTest::class)
class OpenRouterLLMRunnerContractTest : LLMRunnerContractTest<OpenRouterLLMRunner>() {
    
    @Before
    fun checkPrerequisites() {
        TestPrerequisites.requireApiKey("OPENROUTER_API_KEY")
    }
    
    override fun createRunner(): OpenRouterLLMRunner = OpenRouterLLMRunner()
    
    override val defaultModelId: String = "meta-llama/llama-3.2-1b-instruct"
}

