package com.mtkresearch.breezeapp.engine.runner.asr

import com.mtkresearch.breezeapp.engine.runner.llm.RunnerContractTest
import com.mtkresearch.breezeapp.engine.runner.selfhosted.SelfHostedASRRunner
import org.junit.experimental.categories.Category

/**
 * SelfHostedASRRunnerContractTest - SelfHostedASRRunner 合規性測試
 *
 * 繼承 ASRRunnerContractTest 以確保 SelfHostedASRRunner 符合所有 ASR Runner 介面規範。
 */
@Category(RunnerContractTest::class)
class SelfHostedASRRunnerContractTest : ASRRunnerContractTest<SelfHostedASRRunner>() {

    override fun createRunner(): SelfHostedASRRunner = SelfHostedASRRunner()

    override val defaultModelId: String = "Taigi"
}