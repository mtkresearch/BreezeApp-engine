package com.mtkresearch.breezeapp.engine.runner.llm

import com.mtkresearch.breezeapp.engine.runner.mtk.MTKLLMRunner
import com.mtkresearch.breezeapp.engine.test.TestPrerequisites
import org.junit.Before
import org.junit.experimental.categories.Category

/**
 * MTKLLMRunnerContractTest - MediaTek NPU LLM Runner 合規性測試
 * 
 * Requires: MTK NPU hardware and native libraries (libllm_jni)
 * Run with: ./gradlew connectedAndroidTest on MTK device
 */
@Category(RunnerContractTest::class)
class MTKLLMRunnerContractTest : LLMRunnerContractTest<MTKLLMRunner>() {
    
    @Before
    fun checkPrerequisites() {
        TestPrerequisites.requireMTKNPU()
    }
    
    override fun createRunner(): MTKLLMRunner = MTKLLMRunner()
    
    override val defaultModelId: String = "breeze-instruct"
}

