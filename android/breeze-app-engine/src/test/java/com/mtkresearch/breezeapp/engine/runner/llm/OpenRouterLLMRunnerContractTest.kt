package com.mtkresearch.breezeapp.engine.runner.llm

import com.mtkresearch.breezeapp.engine.runner.openrouter.OpenRouterLLMRunner
import org.junit.experimental.categories.Category

/**
 * OpenRouterLLMRunnerContractTest - OpenRouter LLM Runner 合規性測試
 */
@Category(RunnerContractTest::class)
class OpenRouterLLMRunnerContractTest : LLMRunnerContractTest<OpenRouterLLMRunner>() {
    
    override fun createRunner(): OpenRouterLLMRunner = OpenRouterLLMRunner()
    
    override val defaultModelId: String = "meta-llama/llama-3.2-1b-instruct"
}
