package com.mtkresearch.breezeapp.engine.runner.tts

import android.content.Context
import com.mtkresearch.breezeapp.engine.runner.llm.RunnerContractTest
import com.mtkresearch.breezeapp.engine.runner.sherpa.SherpaTTSRunner
import io.mockk.every
import io.mockk.mockk
import org.junit.experimental.categories.Category
import java.io.File

/**
 * SherpaTTSRunnerContractTest - Sherpa ONNX TTS Runner 合規性測試
 * 
 * 注意：這個 Runner 需要 Android Context，使用 MockK 模擬
 */
@Category(RunnerContractTest::class)
class SherpaTTSRunnerContractTest : TTSRunnerContractTest<SherpaTTSRunner>() {
    
    private val mockContext: Context = mockk(relaxed = true) {
        every { filesDir } returns File(System.getProperty("java.io.tmpdir"), "test-files")
        every { cacheDir } returns File(System.getProperty("java.io.tmpdir"), "test-cache")
        every { applicationContext } returns this
    }
    
    override fun createRunner(): SherpaTTSRunner = SherpaTTSRunner(mockContext)
    
    override val defaultModelId: String = "sherpa-tts"
}
