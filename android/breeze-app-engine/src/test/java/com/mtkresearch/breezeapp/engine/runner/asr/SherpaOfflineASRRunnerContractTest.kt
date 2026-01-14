package com.mtkresearch.breezeapp.engine.runner.asr

import android.content.Context
import com.mtkresearch.breezeapp.engine.runner.llm.RunnerContractTest
import com.mtkresearch.breezeapp.engine.runner.sherpa.SherpaOfflineASRRunner
import com.mtkresearch.breezeapp.engine.test.TestPrerequisites
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.experimental.categories.Category
import java.io.File

/**
 * SherpaOfflineASRRunnerContractTest - Sherpa ONNX Offline ASR Runner 合規性測試
 * 
 * Requires: Sherpa ONNX model files and native libraries
 */
@Category(RunnerContractTest::class)
class SherpaOfflineASRRunnerContractTest : ASRRunnerContractTest<SherpaOfflineASRRunner>() {
    
    private val mockContext: Context = mockk(relaxed = true) {
        every { filesDir } returns File(System.getProperty("java.io.tmpdir"), "test-files")
        every { cacheDir } returns File(System.getProperty("java.io.tmpdir"), "test-cache")
        every { applicationContext } returns this
    }
    
    @Before
    fun checkPrerequisites() {
        TestPrerequisites.requireNativeLibrary("sherpa-onnx")
    }
    
    override fun createRunner(): SherpaOfflineASRRunner = SherpaOfflineASRRunner(mockContext)
    
    override val defaultModelId: String = "sherpa-asr-offline"
}

