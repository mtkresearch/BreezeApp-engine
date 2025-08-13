package com.mtkresearch.breezeapp.engine.config

import android.content.Context
import android.content.res.AssetManager
import com.mtkresearch.breezeapp.engine.core.Logger
import com.mtkresearch.breezeapp.engine.model.CapabilityType
import com.mtkresearch.breezeapp.engine.runner.core.RunnerRegistry
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Comprehensive tests for EnhancedConfigurationManager
 * 
 * Tests the enhanced configuration functionality including:
 * - Dynamic configuration loading
 * - Fallback mechanisms
 * - Configuration validation
 * - Runtime updates
 * - Multiple configuration sources
 */
@ExperimentalCoroutinesApi
class EnhancedConfigurationManagerTest {

    private lateinit var context: Context
    private lateinit var assetManager: AssetManager
    private lateinit var logger: Logger
    private lateinit var runnerRegistry: RunnerRegistry
    private lateinit var configManager: EnhancedConfigurationManager

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        // Mock dependencies
        context = mockk(relaxed = true)
        assetManager = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        runnerRegistry = mockk(relaxed = true)

        every { context.assets } returns assetManager
        every { context.getSharedPreferences(any(), any()) } returns mockk(relaxed = true)

        Dispatchers.setMain(testDispatcher)

        configManager = EnhancedConfigurationManager(context, logger)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        configManager.shutdown()
    }

    @Test
    fun `test configuration state flow initialization`() = runTest {
        // Initial state should be Loading
        val initialState = configManager.configurationState.value
        assertTrue("Initial state should be Loading", 
            initialState is EnhancedConfigurationManager.ConfigurationState.Loading)
    }

    @Test
    fun `test successful configuration loading with v1 format`() = runTest {
        // Mock v1.0 configuration
        val v1Config = """
        {
            "version": "1.0",
            "runners": [
                {
                    "name": "test-runner",
                    "class": "com.test.TestRunner",
                    "capabilities": ["LLM"],
                    "priority": 1,
                    "is_real": false,
                    "enabled": true
                }
            ]
        }
        """.trimIndent()

        every { assetManager.open("runner_config.json") } returns ByteArrayInputStream(v1Config.toByteArray())

        val result = configManager.loadAndRegisterRunnersAsync(runnerRegistry)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Should load successfully", result.isSuccess)
        
        val finalState = configManager.configurationState.value
        assertTrue("Final state should be Ready", 
            finalState is EnhancedConfigurationManager.ConfigurationState.Ready)
    }

    @Test
    fun `test successful configuration loading with v2 format`() = runTest {
        // Mock v2.0 configuration
        val v2Config = """
        {
            "version": "2.0",
            "defaultStrategy": "HARDWARE_FIRST",
            "globalSettings": {
                "enableHardwareDetection": true,
                "fallbackToMock": true,
                "maxInitRetries": 3
            },
            "capabilities": {
                "LLM": {
                    "defaultRunner": "test-llm",
                    "runners": {
                        "test-llm": {
                            "class": "com.test.TestLLMRunner",
                            "priority": 1,
                            "type": "MOCK",
                            "enabled": true
                        }
                    }
                }
            }
        }
        """.trimIndent()

        every { assetManager.open("runner_config.json") } returns ByteArrayInputStream(v2Config.toByteArray())

        val result = configManager.loadAndRegisterRunnersAsync(runnerRegistry)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Should load v2.0 configuration successfully", result.isSuccess)
        
        // Verify smart selection was initialized
        verify { runnerRegistry.setSelectionStrategy(any()) }
        verify { runnerRegistry.updateHardwareCapabilities(any()) }
    }

    @Test
    fun `test configuration fallback mechanism`() = runTest {
        // First source fails
        every { assetManager.open("runner_config.json") } throws Exception("File not found")
        
        // Fallback source succeeds
        val fallbackConfig = """
        {
            "version": "1.0",
            "runners": [
                {
                    "name": "fallback-runner",
                    "class": "com.test.FallbackRunner",
                    "capabilities": ["ASR"],
                    "priority": 1,
                    "is_real": false,
                    "enabled": true
                }
            ]
        }
        """.trimIndent()
        
        every { assetManager.open("runner_config_fallback.json") } returns ByteArrayInputStream(fallbackConfig.toByteArray())

        val result = configManager.loadAndRegisterRunnersAsync(runnerRegistry)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Should fallback to secondary configuration", result.isSuccess)
        verify { logger.w(TAG, any<String>()) } // Should log warning about primary failure
    }

    @Test
    fun `test configuration validation failure`() = runTest {
        // Invalid configuration (no runners in v1.0)
        val invalidConfig = """
        {
            "version": "1.0",
            "runners": []
        }
        """.trimIndent()

        every { assetManager.open("runner_config.json") } returns ByteArrayInputStream(invalidConfig.toByteArray())

        val result = configManager.loadAndRegisterRunnersAsync(runnerRegistry)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("Should fail validation", result.isSuccess)
        
        val finalState = configManager.configurationState.value
        assertTrue("Final state should be Error", 
            finalState is EnhancedConfigurationManager.ConfigurationState.Error)
    }

    @Test
    fun `test runtime configuration update`() = runTest {
        // Initial valid configuration
        val initialConfig = """
        {
            "version": "1.0",
            "runners": [
                {
                    "name": "initial-runner",
                    "class": "com.test.InitialRunner",
                    "capabilities": ["LLM"],
                    "priority": 1,
                    "is_real": false,
                    "enabled": true
                }
            ]
        }
        """.trimIndent()

        every { assetManager.open("runner_config.json") } returns ByteArrayInputStream(initialConfig.toByteArray())

        // Load initial configuration
        configManager.loadAndRegisterRunnersAsync(runnerRegistry)
        testDispatcher.scheduler.advanceUntilIdle()

        // Create new configuration
        val newConfig = RunnerConfigFile(
            version = "1.0",
            runners = listOf(
                RunnerDefinition(
                    name = "updated-runner",
                    className = "com.test.UpdatedRunner",
                    capabilities = listOf("ASR"),
                    priority = 1,
                    isReal = false,
                    enabled = true
                )
            )
        )

        // Update configuration
        val updateResult = configManager.updateConfiguration(newConfig)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Configuration update should succeed", updateResult.isSuccess)
        assertEquals("Active configuration should be updated", 
            newConfig, configManager.activeConfiguration.value)
    }

    @Test
    fun `test runtime runner enable disable`() = runTest {
        // Load initial configuration with tracking
        val config = """
        {
            "version": "1.0",
            "runners": [
                {
                    "name": "test-runner",
                    "class": "com.test.TestRunner",
                    "capabilities": ["LLM"],
                    "priority": 1,
                    "is_real": false,
                    "enabled": true
                }
            ]
        }
        """.trimIndent()

        every { assetManager.open("runner_config.json") } returns ByteArrayInputStream(config.toByteArray())

        configManager.loadAndRegisterRunnersAsync(runnerRegistry)
        testDispatcher.scheduler.advanceUntilIdle()

        // Test enable/disable
        assertTrue("Should enable runner", configManager.setRunnerEnabled("test-runner", false))
        assertFalse("Should not find non-existent runner", configManager.setRunnerEnabled("non-existent", false))

        val registeredRunners = configManager.getRegisteredRunners()
        val testRunner = registeredRunners["test-runner"]
        assertNotNull("Test runner should be registered", testRunner)
        assertFalse("Test runner should be disabled", testRunner!!.enabled)
    }

    @Test
    fun `test configuration summary`() = runTest {
        val config = """
        {
            "version": "2.0",
            "capabilities": {
                "LLM": {
                    "defaultRunner": "llm-runner",
                    "runners": {
                        "llm-runner": {
                            "class": "com.test.LLMRunner",
                            "priority": 1,
                            "type": "MOCK",
                            "enabled": true
                        }
                    }
                },
                "ASR": {
                    "defaultRunner": "asr-runner",
                    "runners": {
                        "asr-runner": {
                            "class": "com.test.ASRRunner",
                            "priority": 1,
                            "type": "MOCK",
                            "enabled": true
                        }
                    }
                }
            }
        }
        """.trimIndent()

        every { assetManager.open("runner_config.json") } returns ByteArrayInputStream(config.toByteArray())

        configManager.loadAndRegisterRunnersAsync(runnerRegistry)
        testDispatcher.scheduler.advanceUntilIdle()

        val summary = configManager.getConfigurationSummary()
        
        assertEquals("Version should be 2.0", "2.0", summary.version)
        assertEquals("Should have 2 total runners", 2, summary.totalRunners)
        assertEquals("Should have 2 enabled runners", 2, summary.enabledRunners)
        assertTrue("Should have LLM capability", summary.capabilities.contains(CapabilityType.LLM))
        assertTrue("Should have ASR capability", summary.capabilities.contains(CapabilityType.ASR))
    }

    @Test
    fun `test runners for capability query`() = runTest {
        val config = """
        {
            "version": "1.0",
            "runners": [
                {
                    "name": "llm-runner-1",
                    "class": "com.test.LLMRunner1",
                    "capabilities": ["LLM"],
                    "priority": 1,
                    "is_real": false,
                    "enabled": true
                },
                {
                    "name": "llm-runner-2",
                    "class": "com.test.LLMRunner2",
                    "capabilities": ["LLM"],
                    "priority": 2,
                    "is_real": false,
                    "enabled": true
                },
                {
                    "name": "asr-runner",
                    "class": "com.test.ASRRunner",
                    "capabilities": ["ASR"],
                    "priority": 1,
                    "is_real": false,
                    "enabled": false
                }
            ]
        }
        """.trimIndent()

        every { assetManager.open("runner_config.json") } returns ByteArrayInputStream(config.toByteArray())

        configManager.loadAndRegisterRunnersAsync(runnerRegistry)
        testDispatcher.scheduler.advanceUntilIdle()

        val llmRunners = configManager.getRunnersForCapability(CapabilityType.LLM)
        val asrRunners = configManager.getRunnersForCapability(CapabilityType.ASR)

        assertEquals("Should have 2 LLM runners", 2, llmRunners.size)
        assertEquals("Should have 0 ASR runners (disabled)", 0, asrRunners.size)
        
        // Verify priority ordering
        assertTrue("First LLM runner should have lower priority", 
            llmRunners[0].priority < llmRunners[1].priority)
    }

    @Test
    fun `test custom configuration source`() = runTest {
        // Create a custom configuration source
        val customSource = object : EnhancedConfigurationManager.ConfigurationSource {
            override val name = "CustomTest"
            override suspend fun loadConfiguration(): String = """
            {
                "version": "1.0",
                "runners": [
                    {
                        "name": "custom-runner",
                        "class": "com.test.CustomRunner",
                        "capabilities": ["LLM"],
                        "priority": 1,
                        "is_real": false,
                        "enabled": true
                    }
                ]
            }
            """.trimIndent()
        }

        // Add custom source (will be tried first)
        configManager.addConfigurationSource(customSource)

        val result = configManager.loadAndRegisterRunnersAsync(runnerRegistry)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Should load from custom source", result.isSuccess)
        
        val registeredRunners = configManager.getRegisteredRunners()
        assertTrue("Should have custom runner", registeredRunners.containsKey("custom-runner"))
    }

    @Test
    fun `test malformed JSON handling`() = runTest {
        // Provide malformed JSON
        val malformedJson = "{ invalid json }"
        every { assetManager.open("runner_config.json") } returns ByteArrayInputStream(malformedJson.toByteArray())
        every { assetManager.open("runner_config_fallback.json") } throws Exception("Not found")

        val result = configManager.loadAndRegisterRunnersAsync(runnerRegistry)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse("Should fail with malformed JSON", result.isSuccess)
        
        val finalState = configManager.configurationState.value
        assertTrue("Final state should be Error", 
            finalState is EnhancedConfigurationManager.ConfigurationState.Error)
    }

    @Test
    fun `test hardware capabilities detection`() = runTest {
        // This tests the hardware capability detection logic
        val v2Config = """
        {
            "version": "2.0",
            "defaultStrategy": "HARDWARE_FIRST",
            "capabilities": {
                "LLM": {
                    "defaultRunner": "test-runner",
                    "runners": {
                        "test-runner": {
                            "class": "com.test.TestRunner",
                            "priority": 1,
                            "type": "HARDWARE",
                            "enabled": true
                        }
                    }
                }
            }
        }
        """.trimIndent()

        every { assetManager.open("runner_config.json") } returns ByteArrayInputStream(v2Config.toByteArray())

        configManager.loadAndRegisterRunnersAsync(runnerRegistry)
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify hardware capabilities were detected and passed to registry
        verify { runnerRegistry.updateHardwareCapabilities(match { capabilities ->
            capabilities.contains("CPU") && // Always present
            capabilities.contains("INTERNET_CONNECTION") && // Assumed present
            (capabilities.contains("HIGH_MEMORY") || 
             capabilities.contains("MEDIUM_MEMORY") || 
             capabilities.contains("LOW_MEMORY")) // One of these should be present
        }) }
    }

    companion object {
        private const val TAG = "EnhancedConfigManagerTest"
    }
}