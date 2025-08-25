package com.mtkresearch.breezeapp.engine.runner.guardian

/**
 * Guardian pipeline checkpoint definitions.
 */
enum class GuardianCheckpoint {
    INPUT_VALIDATION,     // Check input before AI processing
    OUTPUT_FILTERING,     // Check output after AI processing
    BOTH                  // Apply both input and output checks
}

/**
 * Guardian failure handling strategies.
 */
enum class GuardianFailureStrategy {
    BLOCK,      // Stop processing and return error
    WARN,       // Log warning but continue processing
    FILTER      // Attempt to filter/redact content and continue
}

/**
 * Guardian pipeline configuration.
 * 
 * Centralizes all guardian-related settings for easy management and extension.
 */
data class GuardianPipelineConfig(
    val enabled: Boolean = false,
    val checkpoints: Set<GuardianCheckpoint> = setOf(GuardianCheckpoint.INPUT_VALIDATION),
    val strictnessLevel: String = "medium", // low, medium, high
    val guardianRunnerName: String? = null, // Specific guardian runner, null for auto-select
    val failureStrategy: GuardianFailureStrategy = GuardianFailureStrategy.BLOCK
) {
    
    /**
     * Check if input validation is enabled.
     */
    fun shouldCheckInput(): Boolean = enabled && 
        (checkpoints.contains(GuardianCheckpoint.INPUT_VALIDATION) || 
         checkpoints.contains(GuardianCheckpoint.BOTH))
    
    /**
     * Check if output filtering is enabled.
     */
    fun shouldCheckOutput(): Boolean = enabled && 
        (checkpoints.contains(GuardianCheckpoint.OUTPUT_FILTERING) || 
         checkpoints.contains(GuardianCheckpoint.BOTH))
    
    companion object {
        /**
         * Default disabled configuration.
         */
        val DISABLED = GuardianPipelineConfig(enabled = false)
        
        /**
         * Safe default configuration with input validation only.
         */
        val DEFAULT_SAFE = GuardianPipelineConfig(
            enabled = true,
            checkpoints = setOf(GuardianCheckpoint.INPUT_VALIDATION),
            strictnessLevel = "medium",
            failureStrategy = GuardianFailureStrategy.BLOCK
        )
        
        /**
         * Maximum protection configuration.
         */
        val MAXIMUM_PROTECTION = GuardianPipelineConfig(
            enabled = true,
            checkpoints = setOf(GuardianCheckpoint.BOTH),
            strictnessLevel = "high",
            failureStrategy = GuardianFailureStrategy.FILTER
        )
    }
}