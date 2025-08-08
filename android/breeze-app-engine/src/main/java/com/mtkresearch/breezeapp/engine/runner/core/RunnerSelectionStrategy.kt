package com.mtkresearch.breezeapp.engine.runner.core

import com.mtkresearch.breezeapp.engine.config.RunnerDefinition
import com.mtkresearch.breezeapp.engine.domain.model.CapabilityType

/**
 * Strategy interface for selecting the best runner for a given capability.
 * 
 * This allows different selection strategies:
 * - MockFirst: Always prefer mock runners (reliable for development/testing)
 * - HardwareFirst: Prefer hardware-accelerated runners when available
 * - PriorityBased: Select based on configured priority values
 */
sealed class RunnerSelectionStrategy {
    
    /**
     * Select the best runner for the given capability.
     * 
     * @param capability The AI capability needed (LLM, ASR, TTS, etc.)
     * @param availableRunners List of available runner definitions
     * @param hardwareCapabilities Set of available hardware capabilities
     * @return The best runner definition, or null if none suitable
     */
    abstract fun selectRunner(
        capability: CapabilityType,
        availableRunners: List<RunnerDefinition>,
        hardwareCapabilities: Set<String>
    ): RunnerDefinition?
    
    /**
     * MockFirst Strategy - Always prefer mock runners for reliable operation.
     * This is the recommended strategy for development and testing.
     */
    object MockFirst : RunnerSelectionStrategy() {
        override fun selectRunner(
            capability: CapabilityType,
            availableRunners: List<RunnerDefinition>,
            hardwareCapabilities: Set<String>
        ): RunnerDefinition? {
            // Filter runners that support the capability
            val capableRunners = availableRunners.filter { runner ->
                runner.capabilities.contains(capability.name)
            }
            
            // First, try to find a mock runner
            val mockRunner = capableRunners
                .filter { !it.isReal }
                .minByOrNull { it.priority }
            
            if (mockRunner != null) {
                return mockRunner
            }
            
            // Fallback to real runners if no mock available
            return capableRunners
                .filter { it.isReal }
                .filter { runner ->
                    // Check hardware requirements for real runners
                    runner.getHardwareRequirements().all { req -> 
                        hardwareCapabilities.contains(req) 
                    }
                }
                .minByOrNull { it.priority }
        }
    }
    
    /**
     * HardwareFirst Strategy - Prefer hardware-accelerated runners when available.
     * Falls back to mock runners if hardware requirements not met.
     */
    object HardwareFirst : RunnerSelectionStrategy() {
        override fun selectRunner(
            capability: CapabilityType,
            availableRunners: List<RunnerDefinition>,
            hardwareCapabilities: Set<String>
        ): RunnerDefinition? {
            // Filter runners that support the capability
            val capableRunners = availableRunners.filter { runner ->
                runner.capabilities.contains(capability.name)
            }
            
            // First, try to find a real runner with met hardware requirements
            val hardwareRunner = capableRunners
                .filter { it.isReal }
                .filter { runner ->
                    runner.getHardwareRequirements().all { req -> 
                        hardwareCapabilities.contains(req) 
                    }
                }
                .minByOrNull { it.priority }
            
            if (hardwareRunner != null) {
                return hardwareRunner
            }
            
            // Fallback to mock runners
            return capableRunners
                .filter { !it.isReal }
                .minByOrNull { it.priority }
        }
    }
    
    /**
     * PriorityBased Strategy - Select purely based on configured priority values.
     * Lower priority number = higher preference.
     */
    object PriorityBased : RunnerSelectionStrategy() {
        override fun selectRunner(
            capability: CapabilityType,
            availableRunners: List<RunnerDefinition>,
            hardwareCapabilities: Set<String>
        ): RunnerDefinition? {
            return availableRunners
                .filter { runner ->
                    runner.capabilities.contains(capability.name)
                }
                .filter { runner ->
                    if (runner.isReal) {
                        // Check hardware requirements for real runners
                        runner.getHardwareRequirements().all { req -> 
                            hardwareCapabilities.contains(req) 
                        }
                    } else {
                        true // Mock runners always available
                    }
                }
                .minByOrNull { it.priority }
        }
    }
    
    companion object {
        /**
         * Create strategy from string name.
         */
        fun fromString(strategyName: String): RunnerSelectionStrategy {
            return when (strategyName.uppercase()) {
                "MOCK_FIRST" -> MockFirst
                "HARDWARE_FIRST" -> HardwareFirst
                "PRIORITY_BASED" -> PriorityBased
                else -> MockFirst // Default to MockFirst for safety
            }
        }
    }
}

/**
 * Extension to get hardware requirements for legacy runner definitions
 */
fun RunnerDefinition.getHardwareRequirements(): List<String> {
    // Use the requirements field if available (v2.0), otherwise infer from class name (v1.0)
    return requirements ?: when {
        className.contains(".mtk.") -> listOf("MTK_NPU")
        className.contains(".openai.") -> listOf("INTERNET_CONNECTION")
        className.contains(".huggingface.") -> listOf("INTERNET_CONNECTION")
        else -> emptyList() // Mock runners have no requirements
    }
}