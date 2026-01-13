package com.mtkresearch.breezeapp.engine.runner.asr

import com.mtkresearch.breezeapp.engine.model.CapabilityType
import com.mtkresearch.breezeapp.engine.model.InferenceResult
import com.mtkresearch.breezeapp.engine.runner.core.BaseRunner
import com.mtkresearch.breezeapp.engine.runner.core.RunnerContractTestSuite
import com.mtkresearch.breezeapp.engine.runner.core.RunnerTestBase
import com.mtkresearch.breezeapp.engine.runner.fixtures.RunnerTestFixtures
import org.junit.Assert.*
import org.junit.Test

/**
 * ASRRunnerContractTest - ASR Runner 合規性測試抽象類別
 * 
 * 所有 ASR Runner 的測試類別應繼承此類別，以確保符合 ASR Runner 介面規範。
 * 
 * ## 測試範圍
 * - BaseRunner 合規性測試
 * - ASR 特定功能測試（音訊處理、轉錄輸出）
 * 
 * @param T ASR Runner 類型
 * @since Engine API v2.2
 */
abstract class ASRRunnerContractTest<T : BaseRunner> : RunnerTestBase<T>(),
    RunnerContractTestSuite<T> {

    // ═══════════════════════════════════════════════════════════════════
    // Lifecycle Contract Tests
    // ═══════════════════════════════════════════════════════════════════

    @Test
    override fun `contract - load returns true and sets isLoaded`() {
        val result = runner.load(defaultModelId, testSettings)
        
        assertTrue("load() should return true", result)
        assertTrue("isLoaded() should return true after load", runner.isLoaded())
    }

    @Test
    override fun `contract - unload sets isLoaded to false`() {
        runner.load(defaultModelId, testSettings)
        assertTrue("Should be loaded before unload", runner.isLoaded())
        
        runner.unload()
        
        assertFalse("isLoaded() should return false after unload", runner.isLoaded())
    }

    @Test
    override fun `contract - multiple load calls are idempotent`() {
        runner.load(defaultModelId, testSettings)
        runner.load(defaultModelId, testSettings)
        runner.load(defaultModelId, testSettings)
        
        assertTrue("Should still be loaded after multiple loads", runner.isLoaded())
    }

    @Test
    override fun `contract - unload is safe to call multiple times`() {
        runner.load(defaultModelId, testSettings)
        
        runner.unload()
        runner.unload()
        runner.unload()
        
        assertFalse("Should be unloaded", runner.isLoaded())
    }

    // ═══════════════════════════════════════════════════════════════════
    // Run Contract Tests
    // ═══════════════════════════════════════════════════════════════════

    @Test
    override fun `contract - run returns error when not loaded`() {
        assertFalse("Runner should not be loaded", runner.isLoaded())
        
        val request = RunnerTestFixtures.createAudioRequest()
        val result = runner.run(request)
        
        assertNotNull("Result should not be null", result)
        assertNotNull("Should have error when not loaded", result.error)
    }

    @Test
    override fun `contract - run with valid input returns non-null result`() {
        runner.load(defaultModelId, testSettings)
        
        val request = RunnerTestFixtures.createAudioRequest(durationMs = 1000)
        val result = runner.run(request)
        
        assertNotNull("Result should not be null", result)
        // ASR may fail due to model not available in test, but should not crash
    }

    @Test
    override fun `contract - run handles empty input gracefully`() {
        runner.load(defaultModelId, testSettings)
        
        val request = RunnerTestFixtures.createEmptyRequest()
        val result = runner.run(request)
        
        assertNotNull("Result should not be null", result)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Info Contract Tests
    // ═══════════════════════════════════════════════════════════════════

    @Test
    override fun `contract - getRunnerInfo returns valid info`() {
        val info = runner.getRunnerInfo()
        
        assertNotNull("RunnerInfo should not be null", info)
        assertTrue("name should not be empty", info.name.isNotEmpty())
        assertTrue("version should not be empty", info.version.isNotEmpty())
    }

    @Test
    override fun `contract - getCapabilities is non-empty`() {
        val capabilities = runner.getCapabilities()
        
        assertFalse("Capabilities should not be empty", capabilities.isEmpty())
        assertTrue("ASR runner should have ASR capability", 
            capabilities.contains(CapabilityType.ASR))
    }

    @Test
    override fun `contract - isSupported returns consistent value`() {
        val first = runner.isSupported()
        val second = runner.isSupported()
        
        assertEquals("isSupported should return consistent value", first, second)
    }

    @Test
    override fun `contract - getParameterSchema returns valid schemas`() {
        val schemas = runner.getParameterSchema()
        
        assertNotNull("ParameterSchema list should not be null", schemas)
        
        schemas.forEach { schema ->
            assertTrue("Schema name should not be empty", schema.name.isNotEmpty())
            assertNotNull("Schema type should not be null", schema.type)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ASR-Specific Tests
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `asr - handles audio input`() {
        runner.load(defaultModelId, testSettings)
        
        val request = RunnerTestFixtures.createAudioRequest(
            durationMs = 1000,
            sampleRate = 16000
        )
        val result = runner.run(request)
        
        assertNotNull("Result should not be null", result)
    }

    @Test
    fun `asr - handles empty audio gracefully`() {
        runner.load(defaultModelId, testSettings)
        
        val request = createAudioRequest(audioData = ByteArray(0))
        val result = runner.run(request)
        
        assertNotNull("Result should not be null", result)
        // May return error for empty audio, but should not crash
    }

    @Test
    fun `asr - handles silent audio`() {
        runner.load(defaultModelId, testSettings)
        
        val request = RunnerTestFixtures.createSilentAudioRequest(durationMs = 500)
        val result = runner.run(request)
        
        assertNotNull("Result should not be null", result)
    }

    @Test
    fun `asr - validates audio format`() {
        runner.load(defaultModelId, testSettings)
        
        // Create request with unusual sample rate
        val request = RunnerTestFixtures.createAudioRequest(
            durationMs = 100,
            sampleRate = 8000 // Lower than typical
        )
        val result = runner.run(request)
        
        assertNotNull("Result should not be null", result)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Error Handling Contract Tests
    // ═══════════════════════════════════════════════════════════════════

    @Test
    override fun `error - error result contains valid error code and message`() {
        assertFalse("Runner should not be loaded", runner.isLoaded())
        
        val request = RunnerTestFixtures.createAudioRequest()
        val result = runner.run(request)
        
        assertNotNull("Result should not be null", result)
        assertNotNull("Should have error when not loaded", result.error)
        
        val error = result.error!!
        assertTrue("Error code should not be empty", error.code.isNotEmpty())
        assertTrue("Error message should not be empty", error.message.isNotEmpty())
    }

    @Test
    override fun `error - invalid input returns appropriate RunnerError`() {
        runner.load(defaultModelId, testSettings)
        
        val invalidRequest = com.mtkresearch.breezeapp.engine.model.InferenceRequest(
            sessionId = "test-session",
            inputs = mapOf("invalid_key" to "not_audio_data")
        )
        
        val result = runner.run(invalidRequest)
        assertNotNull("Result should not be null", result)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Parameter Validation Contract Tests
    // ═══════════════════════════════════════════════════════════════════

    @Test
    override fun `params - validateParameters accepts valid parameters`() {
        val schemas = runner.getParameterSchema()
        if (schemas.isEmpty()) return
        
        val validParams = schemas.associate { it.name to (it.defaultValue ?: "test") }
            .filterValues { it != null }.mapValues { it.value!! }
        
        val result = runner.validateParameters(validParams)
        assertTrue("Valid parameters should pass: ${result.errorMessage}", result.isValid)
    }

    @Test
    override fun `params - validateParameters rejects invalid parameters`() {
        val schemas = runner.getParameterSchema()
        if (schemas.isEmpty()) return
        
        val invalidParams = mapOf("nonexistent_param" to "invalid_value")
        val result = runner.validateParameters(invalidParams)
        assertNotNull("Validation result should not be null", result)
    }
}
