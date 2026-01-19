package com.mtkresearch.breezeapp.engine.runner.tts

import com.mtkresearch.breezeapp.engine.runner.llm.RunnerContractTest
import com.mtkresearch.breezeapp.engine.runner.elevenlabs.ElevenLabsTTSRunner
import org.junit.experimental.categories.Category

/**
 * HuggingFaceASRRunnerContractTest - HuggingFaceASRRunner 合規性測試
 *
 * 繼承 ASRRunnerContractTest 以確保 HuggingFaceASRRunner 符合所有 ASR Runner 介面規範。
 */
@Category(RunnerContractTest::class)
class vContractTest : TTSRunnerContractTest<ElevenLabsTTSRunner>() {

    override fun createRunner(): ElevenLabsTTSRunner = ElevenLabsTTSRunner()

    override val defaultModelId: String = "eleven-v3"
}