package com.mtkresearch.breezeapp.engine.runner.llm

import android.content.Context
import com.mtkresearch.breezeapp.engine.runner.executorch.ExecutorchLLMRunner
import com.mtkresearch.breezeapp.engine.test.TestPrerequisites
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.experimental.categories.Category
import java.io.File

/**
 * ExecutorchLLMRunnerContractTest - Executorch LLM Runner 合規性測試
 * 
 * Requires: Executorch model files (.pte) and native libraries
 * Run with: Model files in place + ./gradlew test --tests "*ExecutorchLLMRunnerContractTest*"
 */
@Category(RunnerContractTest::class)
class ExecutorchLLMRunnerContractTest : LLMRunnerContractTest<ExecutorchLLMRunner>() {
    
    private val mockContext: Context = mockk(relaxed = true) {
        every { filesDir } returns File(System.getProperty("java.io.tmpdir"), "test-files")
        every { cacheDir } returns File(System.getProperty("java.io.tmpdir"), "test-cache")
        every { applicationContext } returns this
    }
    
    @Before
    fun checkPrerequisites() {
        TestPrerequisites.requireNativeLibrary("executorch_jni")
    }
    
    override fun createRunner(): ExecutorchLLMRunner = ExecutorchLLMRunner(mockContext)
    
    override val defaultModelId: String = "llama-3.2-1b"
}

