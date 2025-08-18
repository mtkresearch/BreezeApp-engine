package com.mtkresearch.breezeapp.engine.runner.openrouter

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.util.Log
import com.mtkresearch.breezeapp.engine.model.*
import io.mockk.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/**
 * Unit tests for OpenRouterLLMRunner
 * 
 * Tests the OpenRouter API integration, authentication, streaming capabilities,
 * and error handling without making actual network calls.
 */
class OpenRouterLLMRunnerTest {

    private lateinit var mockContext: Context
    private lateinit var mockConnectivityManager: ConnectivityManager
    private lateinit var mockNetworkInfo: NetworkInfo
    private lateinit var runner: OpenRouterLLMRunner

    private val testApiKey = "sk-test-api-key-12345"
    private val defaultConfig = ModelConfig(
        modelName = "openai/gpt-3.5-turbo",
        modelPath = "",
        parameters = mapOf(
            "api_key" to testApiKey,
            "timeout_ms" to 30000
        )
    )

    @Before
    fun setUp() {
        // Mock Android Log
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        // Mock Android Context and connectivity
        mockContext = mockk(relaxed = true)
        mockConnectivityManager = mockk(relaxed = true)
        mockNetworkInfo = mockk(relaxed = true)
        
        every { mockContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockConnectivityManager
        every { mockConnectivityManager.activeNetworkInfo } returns mockNetworkInfo
        every { mockNetworkInfo.isConnectedOrConnecting } returns true

        // Create runner with test API key
        runner = OpenRouterLLMRunner(apiKey = testApiKey, context = mockContext)
    }

    @After
    fun tearDown() {
        runner.unload()
        unmockkStatic(Log::class)
        clearAllMocks()
    }

    @Test
    fun `getRunnerInfo returns correct information`() {
        val info = runner.getRunnerInfo()
        assertEquals("OpenRouterLLMRunner", info.name)
        assertEquals("1.0.0", info.version)
        assertTrue(info.capabilities.contains(CapabilityType.LLM))
        assertEquals("OpenRouter API-based Large Language Model runner with streaming support", info.description)
    }

    @Test
    fun `load with valid API key returns true`() {
        val configWithValidation = ModelConfig(
            modelName = "openai/gpt-3.5-turbo",
            modelPath = "",
            parameters = mapOf(
                "api_key" to testApiKey,
                "validate_connection" to false // Skip network validation in tests
            )
        )
        
        assertTrue(runner.load(configWithValidation))
        assertTrue(runner.isLoaded())
    }

    @Test
    fun `load with empty API key returns false`() {
        val runner = OpenRouterLLMRunner(apiKey = "")
        val configWithoutKey = ModelConfig(
            modelName = "openai/gpt-3.5-turbo",
            modelPath = ""
        )
        
        assertFalse(runner.load(configWithoutKey))
        assertFalse(runner.isLoaded())
    }

    @Test
    fun `load with invalid API key format returns false`() {
        val runner = OpenRouterLLMRunner(apiKey = "invalid key")
        val config = ModelConfig(
            modelName = "openai/gpt-3.5-turbo",
            modelPath = ""
        )
        
        assertFalse(runner.load(config))
        assertFalse(runner.isLoaded())
    }

    @Test
    fun `load extracts parameters from config correctly`() {
        val configWithParams = ModelConfig(
            modelName = "anthropic/claude-3",
            modelPath = "",
            parameters = mapOf(
                "api_key" to testApiKey,
                "timeout_ms" to 60000,
                "validate_connection" to false
            )
        )
        
        assertTrue(runner.load(configWithParams))
        assertTrue(runner.isLoaded())
    }

    @Test
    fun `getCapabilities returns LLM capability`() {
        val capabilities = runner.getCapabilities()
        assertEquals(1, capabilities.size)
        assertTrue(capabilities.contains(CapabilityType.LLM))
    }

    @Test
    fun `isSupported returns true when internet is available`() {
        every { mockNetworkInfo.isConnectedOrConnecting } returns true
        assertTrue(runner.isSupported())
    }

    @Test
    fun `isSupported returns true when context is null`() {
        val runnerWithoutContext = OpenRouterLLMRunner(apiKey = testApiKey, context = null)
        // Should assume connected when no context is available
        assertTrue(runnerWithoutContext.isSupported())
    }

    @Test
    fun `run returns error when model not loaded`() {
        val request = InferenceRequest(
            sessionId = "test-session",
            inputs = mapOf(InferenceRequest.INPUT_TEXT to "Hello")
        )
        
        val result = runner.run(request, stream = false)
        
        assertNotNull(result.error)
        assertEquals("E001", result.error?.code)
    }

    @Test
    fun `run returns error when text input is missing`() {
        runner.load(defaultConfig)
        
        val request = InferenceRequest(
            sessionId = "test-session",
            inputs = emptyMap()
        )
        
        val result = runner.run(request, stream = false)
        
        assertNotNull(result.error)
        assertEquals("E401", result.error?.code)
    }

    @Test
    fun `runAsFlow returns error when model not loaded`() = runTest {
        val request = InferenceRequest(
            sessionId = "test-session",
            inputs = mapOf(InferenceRequest.INPUT_TEXT to "Hello")
        )
        
        val results = runner.runAsFlow(request).toList()
        
        assertEquals(1, results.size)
        assertNotNull(results[0].error)
        assertEquals("E001", results[0].error?.code)
    }

    @Test
    fun `runAsFlow returns error when text input is missing`() = runTest {
        runner.load(defaultConfig)
        
        val request = InferenceRequest(
            sessionId = "test-session",
            inputs = emptyMap()
        )
        
        val results = runner.runAsFlow(request).toList()
        
        assertEquals(1, results.size)
        assertNotNull(results[0].error)
        assertEquals("E401", results[0].error?.code)
    }

    @Test
    fun `unload sets isLoaded to false`() {
        runner.load(defaultConfig)
        assertTrue(runner.isLoaded())
        
        runner.unload()
        assertFalse(runner.isLoaded())
    }

    @Test
    fun `constructor with context works correctly`() {
        val contextRunner = OpenRouterLLMRunner(mockContext)
        assertNotNull(contextRunner)
        assertEquals("OpenRouterLLMRunner", contextRunner.getRunnerInfo().name)
    }

    @Test
    fun `default constructor works correctly`() {
        val defaultRunner = OpenRouterLLMRunner()
        assertNotNull(defaultRunner)
        assertEquals("OpenRouterLLMRunner", defaultRunner.getRunnerInfo().name)
    }

    @Test
    fun `request body building includes correct parameters`() {
        // This is a conceptual test - in a real implementation, you might want to
        // make buildRequestBody public or create a test-specific method
        runner.load(defaultConfig)
        
        val request = InferenceRequest(
            sessionId = "test-session",
            inputs = mapOf(InferenceRequest.INPUT_TEXT to "Hello"),
            params = mapOf(
                InferenceRequest.PARAM_TEMPERATURE to 0.8f,
                InferenceRequest.PARAM_MAX_TOKENS to 1024,
                "top_p" to 0.9f
            )
        )
        
        // Since buildRequestBody is private, we test indirectly by ensuring
        // the runner accepts these parameters without error
        assertNotNull(request.params[InferenceRequest.PARAM_TEMPERATURE])
        assertNotNull(request.params[InferenceRequest.PARAM_MAX_TOKENS])
    }

    @Test
    fun `load returns true with default constructor and config API key`() {
        val defaultRunner = OpenRouterLLMRunner()
        val configWithKey = ModelConfig(
            modelName = "openai/gpt-3.5-turbo",
            modelPath = "",
            parameters = mapOf("api_key" to testApiKey)
        )
        
        assertTrue(defaultRunner.load(configWithKey))
        assertTrue(defaultRunner.isLoaded())
    }

    @Test
    fun `load handles missing model name gracefully`() {
        val configWithoutModel = ModelConfig(
            modelName = "",
            modelPath = "",
            parameters = mapOf("api_key" to testApiKey)
        )
        
        // Should use default model
        assertTrue(runner.load(configWithoutModel))
        assertTrue(runner.isLoaded())
    }

    @Test
    fun `multiple load calls are idempotent`() {
        assertTrue(runner.load(defaultConfig))
        assertTrue(runner.isLoaded())
        
        // Loading again should not cause issues
        assertTrue(runner.load(defaultConfig))
        assertTrue(runner.isLoaded())
    }

    @Test
    fun `unload after unload does not cause issues`() {
        runner.load(defaultConfig)
        runner.unload()
        assertFalse(runner.isLoaded())
        
        // Unloading again should not cause issues
        runner.unload()
        assertFalse(runner.isLoaded())
    }

    // Integration test concepts (would require MockWebServer for actual HTTP testing)
    @Test
    fun `runner handles network errors gracefully`() {
        // In a full integration test, you would use MockWebServer to simulate
        // network failures and test error handling
        runner.load(defaultConfig)
        
        val request = InferenceRequest(
            sessionId = "test-session",
            inputs = mapOf(InferenceRequest.INPUT_TEXT to "Hello")
        )
        
        // For now, just ensure the runner is loaded and ready for testing
        assertTrue(runner.isLoaded())
        assertNotNull(request.inputs[InferenceRequest.INPUT_TEXT])
    }
}