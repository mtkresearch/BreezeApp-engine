package com.mtkresearch.breezeapp.engine.runner.asr

import com.mtkresearch.breezeapp.engine.runner.llm.RunnerContractTest
import com.mtkresearch.breezeapp.engine.runner.huggingface.HuggingFaceASRRunner
import org.junit.experimental.categories.Category

/**
 * HuggingFaceASRRunnerContractTest - HuggingFaceASRRunner 合規性測試
 *
 * 繼承 ASRRunnerContractTest 以確保 HuggingFaceASRRunner 符合所有 ASR Runner 介面規範。
 */
@Category(RunnerContractTest::class)
class HuggingFaceASRRunnerContractTest : ASRRunnerContractTest<HuggingFaceASRRunner>() {

    override fun createRunner(): HuggingFaceASRRunner = HuggingFaceASRRunner()

    override val defaultModelId: String = "openai/whisper-large-v3"
}