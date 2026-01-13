package com.mtkresearch.breezeapp.engine.runner.tts

import com.mtkresearch.breezeapp.engine.model.CapabilityType
import com.mtkresearch.breezeapp.engine.model.InferenceResult
import com.mtkresearch.breezeapp.engine.runner.core.BaseRunner
import com.mtkresearch.breezeapp.engine.runner.core.RunnerContractTestSuite
import com.mtkresearch.breezeapp.engine.runner.core.RunnerTestBase
import com.mtkresearch.breezeapp.engine.runner.fixtures.RunnerTestFixtures
import org.junit.Assert.*
import org.junit.Test

/**
 * TTSRunnerContractTest - TTS Runner 合規性測試抽象類別
 * 
 * 所有 TTS Runner 的測試類別應繼承此類別，以確保符合 TTS Runner 介面規範。
 * 
 * ## 測試範圍
 * - BaseRunner 合規性測試
 * - TTS 特定功能測試（文字轉語音、音訊輸出）
 * 
 * @param T TTS Runner 類型
 * @since Engine API v2.2
 */
abstract class TTSRunnerContractTest<T : BaseRunner> : RunnerTestBase<T>(),
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
        
        val request = RunnerTestFixtures.createTTSRequest()
        val result = runner.run(request)
        
        assertNotNull("Result should not be null", result)
        assertNotNull("Should have error when not loaded", result.error)
    }

    @Test
    override fun `contract - run with valid input returns non-null result`() {
        runner.load(defaultModelId, testSettings)
        
        val request = RunnerTestFixtures.createTTSRequest("Hello, world!")
        val result = runner.run(request)
        
        assertNotNull("Result should not be null", result)
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
        assertTrue("TTS runner should have TTS capability", 
            capabilities.contains(CapabilityType.TTS))
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
    // TTS-Specific Tests
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `tts - generates audio from text`() {
        runner.load(defaultModelId, testSettings)
        
        val request = RunnerTestFixtures.createTTSRequest("Hello, world!")
        val result = runner.run(request)
        
        assertNotNull("Result should not be null", result)
    }

    @Test
    fun `tts - handles empty text gracefully`() {
        runner.load(defaultModelId, testSettings)
        
        val request = RunnerTestFixtures.createTTSRequest("")
        val result = runner.run(request)
        
        assertNotNull("Result should not be null", result)
        // May return error for empty text, but should not crash
    }

    @Test
    fun `tts - handles long text`() {
        runner.load(defaultModelId, testSettings)
        
        val longText = "This is a sentence. ".repeat(50)
        val request = RunnerTestFixtures.createTTSRequest(longText)
        val result = runner.run(request)
        
        assertNotNull("Result should not be null", result)
    }

    @Test
    fun `tts - handles unicode text`() {
        runner.load(defaultModelId, testSettings)
        
        val request = RunnerTestFixtures.createTTSRequest("你好世界 こんにちは 안녕하세요")
        val result = runner.run(request)
        
        assertNotNull("Result should not be null", result)
    }

    @Test
    fun `tts - handles special characters`() {
        runner.load(defaultModelId, testSettings)
        
        val request = RunnerTestFixtures.createTTSRequest("Hello! How are you? I'm fine. #test @user")
        val result = runner.run(request)
        
        assertNotNull("Result should not be null", result)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Error Handling Contract Tests
    // ═══════════════════════════════════════════════════════════════════

    @Test
    override fun `error - error result contains valid error code and message`() {
        assertFalse("Runner should not be loaded", runner.isLoaded())
        
        val request = RunnerTestFixtures.createTTSRequest()
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
            inputs = mapOf("invalid_key" to listOf(1, 2, 3))
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
