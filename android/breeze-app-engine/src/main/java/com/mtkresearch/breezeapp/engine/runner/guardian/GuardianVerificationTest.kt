package com.mtkresearch.breezeapp.engine.runner.guardian

import com.mtkresearch.breezeapp.engine.model.CapabilityType
import com.mtkresearch.breezeapp.engine.model.InferenceRequest
import com.mtkresearch.breezeapp.engine.model.EngineSettings
import com.mtkresearch.breezeapp.engine.runner.core.RunnerInfo

/**
 * Guardian Integration Verification Test
 * 
 * Simple verification that all Guardian components are properly integrated
 * and can work together without compilation errors.
 */
object GuardianVerificationTest {
    
    /**
     * Verify Guardian Configuration Creation
     */
    fun verifyConfigurationCreation(): Boolean {
        return try {
            // Test creating different configurations
            GuardianPipelineConfig.DISABLED
            GuardianPipelineConfig.DEFAULT_SAFE  
            GuardianPipelineConfig.MAXIMUM_PROTECTION
            
            // Test custom configuration
            val custom = GuardianPipelineConfig(
                enabled = true,
                checkpoints = setOf(GuardianCheckpoint.BOTH),
                strictnessLevel = "high",
                guardianRunnerName = "mock_guardian",
                failureStrategy = GuardianFailureStrategy.FILTER
            )
            
            // Test configuration methods
            custom.shouldCheckInput() && custom.shouldCheckOutput()
            
        } catch (e: Exception) {
            println("Configuration creation failed: ${e.message}")
            false
        }
    }
    
    /**
     * Verify EngineSettings Integration  
     */
    fun verifyEngineSettingsIntegration(): Boolean {
        return try {
            val guardianConfig = GuardianPipelineConfig.DEFAULT_SAFE
            
            // Test EngineSettings with guardian config
            val settings = EngineSettings.default()
                .withGuardianConfig(guardianConfig)
            
            // Verify configuration was set
            settings.guardianConfig.enabled && 
            settings.guardianConfig.checkpoints.contains(GuardianCheckpoint.INPUT_VALIDATION)
            
        } catch (e: Exception) {
            println("EngineSettings integration failed: ${e.message}")
            false
        }
    }
    
    /**
     * Verify Guardian Check Result Logic
     */
    fun verifyCheckResultLogic(): Boolean {
        return try {
            // Test different result types
            val skipped = GuardianCheckResult.skip("Test skip")
            val passed = GuardianCheckResult.Passed(
                GuardianAnalysisResult(
                    status = GuardianStatus.SAFE,
                    riskScore = 0.1,
                    categories = emptyList(),
                    action = GuardianAction.NONE,
                    filteredText = null
                )
            )
            
            val failed = GuardianCheckResult.Failed(
                GuardianAnalysisResult(
                    status = GuardianStatus.BLOCKED,
                    riskScore = 0.9,
                    categories = listOf(GuardianCategory.VIOLENCE),
                    action = GuardianAction.BLOCK,
                    filteredText = null
                ),
                GuardianFailureStrategy.BLOCK
            )
            
            // Test logic methods
            !skipped.shouldBlock() && 
            !passed.shouldBlock() && 
            failed.shouldBlock()
            
        } catch (e: Exception) {
            println("Check result logic failed: ${e.message}")
            false
        }
    }
    
    /**
     * Verify MockGuardianRunner Structure
     */
    fun verifyMockGuardianRunner(): Boolean {
        return try {
            val mockRunner = MockGuardianRunner()
            
            // Test basic runner interface
            mockRunner.getCapabilities().contains(CapabilityType.GUARDIAN) &&
            mockRunner.getRunnerInfo().name == "mock_guardian" &&
            mockRunner.isSupported()
            
        } catch (e: Exception) {
            println("MockGuardianRunner verification failed: ${e.message}")
            false
        }
    }
    
    /**
     * Verify Guardian Request Creation
     */
    fun verifyRequestCreation(): Boolean {
        return try {
            // Test creating requests with guardian parameters
            val basicRequest = InferenceRequest(
                sessionId = "test-1",
                inputs = mapOf("text" to "Test content"),
                params = mapOf("temperature" to 0.7)
            )
            
            val guardianRequest = InferenceRequest(
                sessionId = "test-2", 
                inputs = mapOf("text" to "Test content"),
                params = mapOf(
                    "temperature" to 0.7,
                    GuardianPipeline.PARAM_GUARDIAN_ENABLED to true,
                    GuardianPipeline.PARAM_GUARDIAN_STRICTNESS to "high",
                    GuardianPipeline.PARAM_GUARDIAN_CHECKPOINT to "both"
                )
            )
            
            // Verify requests were created properly
            basicRequest.inputs.containsKey("text") &&
            guardianRequest.params.containsKey(GuardianPipeline.PARAM_GUARDIAN_ENABLED)
            
        } catch (e: Exception) {
            println("Request creation failed: ${e.message}")
            false
        }
    }
    
    /**
     * Run All Verification Tests
     */
    fun runAllTests(): Boolean {
        val tests = listOf(
            "Configuration Creation" to ::verifyConfigurationCreation,
            "EngineSettings Integration" to ::verifyEngineSettingsIntegration,
            "Check Result Logic" to ::verifyCheckResultLogic,
            "MockGuardianRunner Structure" to ::verifyMockGuardianRunner,
            "Request Creation" to ::verifyRequestCreation
        )
        
        var allPassed = true
        
        println("=== Guardian Integration Verification ===")
        
        tests.forEach { (testName, testFunction) ->
            try {
                val passed = testFunction()
                println("✓ $testName: ${if (passed) "PASS" else "FAIL"}")
                if (!passed) allPassed = false
            } catch (e: Exception) {
                println("✗ $testName: ERROR - ${e.message}")
                allPassed = false
            }
        }
        
        println("=== Summary: ${if (allPassed) "ALL TESTS PASSED" else "SOME TESTS FAILED"} ===")
        
        return allPassed
    }
}

/**
 * Mock implementation of GuardianRunner for testing purposes.
 * This provides a minimal implementation that satisfies the BaseGuardianRunner interface.
 */
class MockGuardianRunner : BaseGuardianRunner() {
    private var loaded = false

    override fun analyze(text: String, config: GuardianConfig): GuardianAnalysisResult {
        return GuardianAnalysisResult(
            status = GuardianStatus.SAFE,
            riskScore = 0.1,
            categories = emptyList(),
            action = GuardianAction.NONE,
            filteredText = null
        )
    }

    override fun load(modelId: String, settings: EngineSettings, initialParams: Map<String, Any>): Boolean {
        loaded = true
        return true
    }

    override fun unload() {
        loaded = false
    }

    override fun isLoaded(): Boolean = loaded

    override fun getRunnerInfo(): RunnerInfo {
        return RunnerInfo(
            name = "mock_guardian",
            version = "1.0.0",
            capabilities = getCapabilities(),
            description = "Mock Guardian Runner for testing"
        )
    }

    override fun isSupported(): Boolean = true
}

/**
 * Simple verification that can be run to test Guardian integration.
 */
fun verifyGuardianIntegration(): Boolean {
    return GuardianVerificationTest.runAllTests()
}