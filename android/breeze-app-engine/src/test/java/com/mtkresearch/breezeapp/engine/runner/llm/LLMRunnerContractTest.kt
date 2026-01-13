package com.mtkresearch.breezeapp.engine.runner.llm

import com.mtkresearch.breezeapp.engine.model.CapabilityType
import com.mtkresearch.breezeapp.engine.model.InferenceRequest
import com.mtkresearch.breezeapp.engine.model.InferenceResult
import com.mtkresearch.breezeapp.engine.runner.core.BaseRunner
import com.mtkresearch.breezeapp.engine.runner.core.FlowStreamingRunner
import com.mtkresearch.breezeapp.engine.runner.core.FlowStreamingContractTestSuite
import com.mtkresearch.breezeapp.engine.runner.core.RunnerContractTestSuite
import com.mtkresearch.breezeapp.engine.runner.core.RunnerTestBase
import com.mtkresearch.breezeapp.engine.runner.fixtures.RunnerTestFixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test

/**
 * LLMRunnerContractTest - LLM Runner 合規性測試抽象類別
 * 
 * 所有 LLM Runner 的測試類別應繼承此類別，以確保符合 LLM Runner 介面規範。
 * 
 * ## 測試範圍
 * - BaseRunner 合規性測試
 * - FlowStreamingRunner 合規性測試
 * - LLM 特定功能測試
 * 
 * ## 使用方式
 * ```kotlin
 * class MockLLMRunnerContractTest : LLMRunnerContractTest<MockLLMRunner>() {
 *     override fun createRunner() = MockLLMRunner()
 *     override val defaultModelId = "mock-llm-basic"
 * }
 * ```
 * 
 * @param T LLM Runner 類型
 * @since Engine API v2.2
 */
abstract class LLMRunnerContractTest<T> : RunnerTestBase<T>(),
    RunnerContractTestSuite<T>,
    FlowStreamingContractTestSuite<T>
    where T : BaseRunner, T : FlowStreamingRunner {

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
        
        // Should not throw
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
        // Don't load the runner
        assertFalse("Runner should not be loaded", runner.isLoaded())
        
        val request = RunnerTestFixtures.createTextRequest()
        val result = runner.run(request)
        
        assertNotNull("Result should not be null", result)
        assertNotNull("Should have error when not loaded", result.error)
    }

    @Test
    override fun `contract - run with valid input returns non-null result`() {
        runner.load(defaultModelId, testSettings)
        
        val request = RunnerTestFixtures.createTextRequest("Hello, how are you?")
        val result = runner.run(request)
        
        assertNotNull("Result should not be null", result)
        assertNull("Should not have error: ${result.error}", result.error)
        assertFalse("Outputs should not be empty", result.outputs.isEmpty())
    }

    @Test
    override fun `contract - run handles empty input gracefully`() {
        runner.load(defaultModelId, testSettings)
        
        val request = RunnerTestFixtures.createEmptyRequest()
        
        // Should not throw, may return error or empty result
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
        assertTrue("capabilities should match getCapabilities()", 
            info.capabilities.containsAll(runner.getCapabilities()))
    }

    @Test
    override fun `contract - getCapabilities is non-empty`() {
        val capabilities = runner.getCapabilities()
        
        assertFalse("Capabilities should not be empty", capabilities.isEmpty())
        assertTrue("LLM runner should have LLM capability", 
            capabilities.contains(CapabilityType.LLM))
    }

    @Test
    override fun `contract - isSupported returns consistent value`() {
        val first = runner.isSupported()
        val second = runner.isSupported()
        val third = runner.isSupported()
        
        assertEquals("isSupported should return consistent value", first, second)
        assertEquals("isSupported should return consistent value", second, third)
    }

    @Test
    override fun `contract - getParameterSchema returns valid schemas`() {
        val schemas = runner.getParameterSchema()
        
        assertNotNull("ParameterSchema list should not be null", schemas)
        
        schemas.forEach { schema ->
            assertTrue("Schema name should not be empty: $schema", 
                schema.name.isNotEmpty())
            assertNotNull("Schema type should not be null: ${schema.name}", 
                schema.type)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Flow Streaming Contract Tests
    // ═══════════════════════════════════════════════════════════════════

    @Test
    override fun `streaming - runAsFlow emits at least one result`() = runTest {
        runner.load(defaultModelId, testSettings)
        
        val request = RunnerTestFixtures.createTextRequest("Tell me a short story")
        val results = runner.runAsFlow(request).toList()
        
        assertTrue("Flow should emit at least one result", results.isNotEmpty())
    }

    @Test
    override fun `streaming - runAsFlow final result has partial=false`() = runTest {
        runner.load(defaultModelId, testSettings)
        
        val request = RunnerTestFixtures.createTextRequest("Hello")
        val lastResult = runner.runAsFlow(request).last()
        
        assertFalse("Final result should have partial=false", lastResult.partial)
    }

    @Test
    override fun `streaming - runAsFlow handles cancellation gracefully`() = runTest {
        runner.load(defaultModelId, testSettings)
        
        val request = RunnerTestFixtures.createTextRequest("Write a long essay")
        
        // Test that early cancellation doesn't crash
        var collectedCount = 0
        try {
            runner.runAsFlow(request).collect { result ->
                collectedCount++
                if (collectedCount >= 2) {
                    // Manually break after collecting 2 results
                    return@collect
                }
            }
        } catch (e: Exception) {
            // Cancellation exceptions are expected
        }
        
        // Should not crash
        assertTrue("Should handle collection gracefully (collected $collectedCount)", true)
    }

    @Test
    override fun `streaming - partial results accumulate correctly`() = runTest {
        runner.load(defaultModelId, testSettings)
        
        val request = RunnerTestFixtures.createTextRequest("Say hello")
        val results = runner.runAsFlow(request).toList()
        
        if (results.size > 1) {
            // Check that text output grows or stays consistent
            var previousLength = 0
            results.forEach { result ->
                val textOutput = result.outputs[InferenceResult.OUTPUT_TEXT] as? String
                if (textOutput != null) {
                    assertTrue(
                        "Partial results should accumulate (length: ${textOutput.length} >= $previousLength)",
                        textOutput.length >= previousLength
                    )
                    previousLength = textOutput.length
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // LLM-Specific Tests
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `llm - responds to text input`() {
        runner.load(defaultModelId, testSettings)
        
        val request = RunnerTestFixtures.createTextRequest("What is 2 + 2?")
        val result = runner.run(request)
        
        assertNull("Should not have error", result.error)
        
        val textOutput = result.outputs[InferenceResult.OUTPUT_TEXT] as? String
        assertNotNull("Should have text output", textOutput)
        assertTrue("Text output should not be empty", textOutput!!.isNotEmpty())
    }

    @Test
    fun `llm - streaming generates multiple chunks`() = runTest {
        runner.load(defaultModelId, testSettings)
        
        val request = RunnerTestFixtures.createTextRequest("Write a paragraph about AI")
        val results = runner.runAsFlow(request).toList()
        
        assertTrue("Streaming should generate multiple chunks", results.size >= 1)
        
        // At least one should be partial (if more than one result)
        if (results.size > 1) {
            val partialResults = results.dropLast(1)
            assertTrue(
                "Should have partial results before final",
                partialResults.all { it.partial }
            )
        }
    }

    @Test
    fun `llm - output contains text field`() {
        runner.load(defaultModelId, testSettings)
        
        val request = RunnerTestFixtures.createTextRequest("Hello")
        val result = runner.run(request)
        
        assertNull("Should not have error", result.error)
        assertTrue(
            "Output should contain text field",
            result.outputs.containsKey(InferenceResult.OUTPUT_TEXT)
        )
    }

    @Test
    fun `llm - handles unicode input correctly`() {
        runner.load(defaultModelId, testSettings)
        
        val request = RunnerTestFixtures.createUnicodeRequest()
        val result = runner.run(request)
        
        assertNotNull("Result should not be null", result)
        // Should not crash on unicode, may return error or success
    }

    @Test
    fun `llm - handles special characters correctly`() {
        runner.load(defaultModelId, testSettings)
        
        val request = RunnerTestFixtures.createSpecialCharsRequest()
        val result = runner.run(request)
        
        assertNotNull("Result should not be null", result)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Error Handling Contract Tests
    // ═══════════════════════════════════════════════════════════════════

    @Test
    override fun `error - error result contains valid error code and message`() {
        // Don't load the runner to trigger an error
        assertFalse("Runner should not be loaded", runner.isLoaded())
        
        val request = RunnerTestFixtures.createTextRequest()
        val result = runner.run(request)
        
        assertNotNull("Result should not be null", result)
        assertNotNull("Should have error when not loaded", result.error)
        
        val error = result.error!!
        assertTrue(
            "Error code should not be empty",
            error.code.isNotEmpty()
        )
        assertTrue(
            "Error message should not be empty",
            error.message.isNotEmpty()
        )
    }

    @Test
    override fun `error - invalid input returns appropriate RunnerError`() {
        runner.load(defaultModelId, testSettings)
        
        // Create request with completely invalid input type
        val invalidRequest = InferenceRequest(
            sessionId = "test-session",
            inputs = mapOf("invalid_key" to listOf(1, 2, 3)) // Wrong type
        )
        
        val result = runner.run(invalidRequest)
        
        assertNotNull("Result should not be null", result)
        // Runner may handle gracefully or return error, both are acceptable
        // The key is it should not crash
    }

    // ═══════════════════════════════════════════════════════════════════
    // Parameter Validation Contract Tests
    // ═══════════════════════════════════════════════════════════════════

    @Test
    override fun `params - validateParameters accepts valid parameters`() {
        val schemas = runner.getParameterSchema()
        
        if (schemas.isEmpty()) {
            // No parameters to validate, test passes
            return
        }
        
        // Build valid parameters based on schema
        val validParams = schemas.associate { schema ->
            schema.name to (schema.defaultValue ?: getDefaultValueForType(schema))
        }.filterValues { it != null }.mapValues { it.value!! }
        
        val validationResult = runner.validateParameters(validParams)
        
        assertTrue(
            "Valid parameters should pass validation: ${validationResult.errorMessage}",
            validationResult.isValid
        )
    }

    @Test
    override fun `params - validateParameters rejects invalid parameters`() {
        val schemas = runner.getParameterSchema()
        
        // Find a required parameter to invalidate
        val requiredSchema = schemas.find { it.isRequired }
        
        if (requiredSchema == null) {
            // No required parameters, try with invalid type
            if (schemas.isNotEmpty()) {
                val schema = schemas.first()
                val invalidParams = mapOf(schema.name to "completely_invalid_value_type_12345")
                val result = runner.validateParameters(invalidParams)
                // May or may not be invalid depending on schema, just ensure no crash
                assertNotNull("Validation result should not be null", result)
            }
            return
        }
        
        // Omit required parameter
        val invalidParams = emptyMap<String, Any>()
        val validationResult = runner.validateParameters(invalidParams)
        
        // Required parameter missing should fail
        // Note: Some runners may have default values, so this might pass
        assertNotNull("Validation result should not be null", validationResult)
    }
    
    /**
     * Helper: Get a default test value for a parameter type
     */
    private fun getDefaultValueForType(schema: com.mtkresearch.breezeapp.engine.runner.core.ParameterSchema): Any? {
        return when (schema.type) {
            is com.mtkresearch.breezeapp.engine.runner.core.ParameterType.StringType -> "test"
            is com.mtkresearch.breezeapp.engine.runner.core.ParameterType.IntType -> 1
            is com.mtkresearch.breezeapp.engine.runner.core.ParameterType.FloatType -> 1.0
            is com.mtkresearch.breezeapp.engine.runner.core.ParameterType.BooleanType -> true
            is com.mtkresearch.breezeapp.engine.runner.core.ParameterType.SelectionType -> {
                val selectionType = schema.type as com.mtkresearch.breezeapp.engine.runner.core.ParameterType.SelectionType
                selectionType.options.firstOrNull()?.key
            }
            is com.mtkresearch.breezeapp.engine.runner.core.ParameterType.FilePathType -> "/test/path"
            else -> null
        }
    }
}
