package com.mtkresearch.breezeapp.engine.runner.llamastack

import com.mtkresearch.breezeapp.engine.model.*
import com.mtkresearch.breezeapp.engine.runner.core.ValidationResult
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LlamaStackRunnerTest {

    private lateinit var runner: LlamaStackRunner
    private lateinit var mockSettings: EngineSettings

    @Before
    fun setup() {
        runner = LlamaStackRunner(context = null)
        mockSettings = EngineSettings().withRunnerParameters("llamastack", mapOf(
            "endpoint" to "http://localhost:8080",
            "model_id" to "llama-3.2-11b-vision-instruct",
            "temperature" to 0.7f,
            "enable_vision" to true
        ))
    }

    @Test
    fun `should return correct runner info`() {
        val info = runner.getRunnerInfo()
        
        assertEquals("LlamaStackRunner", info.name)
        assertEquals("1.0.0", info.version)
        assertEquals(2, info.capabilities.size)
        assertTrue(info.capabilities.contains(CapabilityType.LLM))
        assertTrue(info.capabilities.contains(CapabilityType.VLM))
        assertTrue(info.description.contains("Remote-first"))
    }

    @Test
    fun `should validate correct parameters`() {
        val validParams = mapOf(
            "endpoint" to "https://api.llamastack.ai",
            "api_key" to "test-key-123",
            "model_id" to "llama-3.2-90b-vision-instruct",
            "temperature" to 0.8f,
            "max_tokens" to 4096,
            "enable_vision" to true
        )

        val result = runner.validateParameters(validParams)
        assertTrue("Should validate correct parameters", result.isValid)
    }

    @Test
    fun `should reject invalid temperature`() {
        val invalidParams = mapOf(
            "endpoint" to "https://api.llamastack.ai",
            "temperature" to 3.0f // Invalid: > 2.0
        )

        val result = runner.validateParameters(invalidParams)
        assertFalse("Should reject invalid temperature", result.isValid)
        assertTrue("Error message should mention temperature", 
            result.errorMessage?.contains("temperature", ignoreCase = true) == true)
    }

    @Test
    fun `should reject empty endpoint`() {
        val invalidParams = mapOf(
            "endpoint" to "",
            "temperature" to 0.7f
        )

        val result = runner.validateParameters(invalidParams)
        assertFalse("Should reject empty endpoint", result.isValid)
    }

    @Test
    fun `should reject invalid max_tokens`() {
        val invalidParams = mapOf(
            "endpoint" to "https://api.llamastack.ai",
            "max_tokens" to 100000 // Invalid: > 32768
        )

        val result = runner.validateParameters(invalidParams)
        assertFalse("Should reject invalid max_tokens", result.isValid)
    }

    @Test
    fun `should have correct capabilities`() {
        val capabilities = runner.getCapabilities()
        
        assertEquals(2, capabilities.size)
        assertTrue("Should support LLM", capabilities.contains(CapabilityType.LLM))
        assertTrue("Should support VLM", capabilities.contains(CapabilityType.VLM))
    }

    @Test
    fun `should be supported on all devices`() {
        assertTrue("Should be supported (no hardware requirements)", runner.isSupported())
    }

    @Test
    fun `should not be loaded initially`() {
        assertFalse("Should not be loaded initially", runner.isLoaded())
    }

    @Test
    fun `should have comprehensive parameter schema`() {
        val schema = runner.getParameterSchema()
        
        assertTrue("Should have at least 7 parameters", schema.size >= 7)
        
        val parameterNames = schema.map { it.name }
        assertTrue("Should have endpoint parameter", parameterNames.contains("endpoint"))
        assertTrue("Should have api_key parameter", parameterNames.contains("api_key"))
        assertTrue("Should have model_id parameter", parameterNames.contains("model_id"))
        assertTrue("Should have temperature parameter", parameterNames.contains("temperature"))
        assertTrue("Should have max_tokens parameter", parameterNames.contains("max_tokens"))
        assertTrue("Should have enable_vision parameter", parameterNames.contains("enable_vision"))
        
        // Check that API key is marked as sensitive
        val apiKeyParam = schema.find { it.name == "api_key" }
        assertNotNull("API key parameter should exist", apiKeyParam)
        assertTrue("API key should be marked as sensitive", apiKeyParam?.isSensitive == true)
        
        // Check that endpoint is required
        val endpointParam = schema.find { it.name == "endpoint" }
        assertNotNull("Endpoint parameter should exist", endpointParam)
        assertTrue("Endpoint should be required", endpointParam?.isRequired == true)
    }

    @Test
    fun `should not support non-streaming run method`() {
        val request = InferenceRequest(
            sessionId = "test-123",
            inputs = mapOf(InferenceRequest.INPUT_TEXT to "Hello world")
        )

        try {
            runner.run(request, false)
            fail("Should throw UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertTrue("Error message should mention streaming", 
                e.message?.contains("streaming", ignoreCase = true) == true)
        }
    }
}

class LlamaStackConfigTest {

    @Test
    fun `should create valid production config`() {
        val config = LlamaStackConfig.production("test-api-key")
        
        assertEquals("https://api.llamastack.ai", config.endpoint)
        assertEquals("test-api-key", config.apiKey)
        assertEquals("llama-3.2-90b-vision-instruct", config.modelId)
        assertTrue("Should enable vision", config.enableVision)
        assertTrue("Should be vision capable", config.isVisionCapable())
        
        val validation = config.validateConfiguration()
        assertTrue("Production config should be valid", validation.isValid)
    }

    @Test
    fun `should create valid development config`() {
        val config = LlamaStackConfig.development()
        
        assertEquals("http://localhost:8080", config.endpoint)
        assertNull("Dev config should not have API key", config.apiKey)
        assertEquals("llama-3.2-11b-vision-instruct", config.modelId)
        assertTrue("Should enable vision", config.enableVision)
        
        val validation = config.validateConfiguration()
        assertTrue("Development config should be valid", validation.isValid)
    }

    @Test
    fun `should parse parameters correctly`() {
        val params = mapOf(
            "endpoint" to "https://custom.api.com",
            "api_key" to "custom-key",
            "model_id" to "custom-model",
            "temperature" to 0.9f,
            "max_tokens" to 2048,
            "enable_vision" to true,
            "enable_rag" to true,
            "enable_agents" to false,
            "timeout" to 45000L
        )

        val config = LlamaStackConfig.fromParams(params, "default-model")
        
        assertEquals("https://custom.api.com", config.endpoint)
        assertEquals("custom-key", config.apiKey)
        assertEquals("custom-model", config.modelId)
        assertEquals(0.9f, config.temperature, 0.001f)
        assertEquals(2048, config.maxTokens)
        assertTrue(config.enableVision)
        assertTrue(config.enableRAG)
        assertFalse(config.enableAgents)
        assertEquals(45000L, config.timeout)
    }

    @Test
    fun `should use defaults for missing parameters`() {
        val config = LlamaStackConfig.fromParams(emptyMap(), "test-model")
        
        assertEquals("https://api.llamastack.ai", config.endpoint)
        assertNull(config.apiKey)
        assertEquals("test-model", config.modelId)
        assertEquals(0.7f, config.temperature, 0.001f)
        assertEquals(4096, config.maxTokens)
        assertTrue(config.enableVision)
        assertFalse(config.enableRAG)
        assertFalse(config.enableAgents)
        assertEquals(30000L, config.timeout)
    }

    @Test
    fun `should validate invalid configurations`() {
        val invalidConfigs = listOf(
            LlamaStackConfig(endpoint = ""), // Empty endpoint
            LlamaStackConfig(endpoint = "invalid-url"), // Invalid URL
            LlamaStackConfig(temperature = -0.1f), // Negative temperature
            LlamaStackConfig(temperature = 2.5f), // Too high temperature
            LlamaStackConfig(maxTokens = 0), // Invalid max tokens
            LlamaStackConfig(maxTokens = 100000), // Too high max tokens
            LlamaStackConfig(timeout = 500L), // Too short timeout
            LlamaStackConfig(timeout = 400000L) // Too long timeout
        )

        invalidConfigs.forEach { config ->
            val result = config.validateConfiguration()
            assertFalse("Config should be invalid: $config", result.isValid)
            assertNotNull("Should have error message", result.errorMessage)
        }
    }

    @Test
    fun `should detect vision capability correctly`() {
        assertTrue("Vision model should be vision capable", 
            LlamaStackConfig(modelId = "llama-3.2-90b-vision-instruct").isVisionCapable())
        assertTrue("Vision model should be vision capable (case insensitive)", 
            LlamaStackConfig(modelId = "LLAMA-3.2-90B-VISION-INSTRUCT").isVisionCapable())
        
        assertFalse("Non-vision model should not be vision capable", 
            LlamaStackConfig(modelId = "llama-3.1-70b-instruct", enableVision = false).isVisionCapable())
        assertFalse("Disabled vision should not be vision capable", 
            LlamaStackConfig(modelId = "llama-3.2-90b-vision-instruct", enableVision = false).isVisionCapable())
    }
}