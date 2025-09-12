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
 * Guardian operational modes for transition management.
 */
enum class GuardianMode {
    FULL,        // Full Guardian (input + output validation) - Current behavior
    INPUT_ONLY,  // Input-only Guardian (simplified architecture) - New default
    DISABLED     // Guardian completely disabled
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
    val mode: GuardianMode = GuardianMode.INPUT_ONLY, // NEW: Transition-friendly mode
    val checkpoints: Set<GuardianCheckpoint> = setOf(GuardianCheckpoint.INPUT_VALIDATION),
    val strictnessLevel: String = "medium", // low, medium, high
    val guardianRunnerName: String? = null, // Specific guardian runner, null for auto-select
    val failureStrategy: GuardianFailureStrategy = GuardianFailureStrategy.BLOCK,
    val streamingWordAccumulationCount: Int = 20,
    // Deprecated progressive streaming parameters removed
    val microBatchSize: Int = 3, // Kept for compatibility but unused
    val windowOverlap: Int = 1   // Kept for compatibility but unused
) {

    /**
     * Check if input validation is enabled.
     * 
     * For backward compatibility, respects both mode and checkpoint settings.
     */
    fun shouldCheckInput(): Boolean = when {
        !enabled -> false
        mode == GuardianMode.DISABLED -> false
        mode == GuardianMode.INPUT_ONLY -> true
        mode == GuardianMode.FULL -> (checkpoints.contains(GuardianCheckpoint.INPUT_VALIDATION) ||
                                      checkpoints.contains(GuardianCheckpoint.BOTH))
        else -> false
    }

    
    /**
     * Check if this configuration uses the simplified input-only architecture.
     */
    fun isInputOnlyMode(): Boolean = mode == GuardianMode.INPUT_ONLY

    companion object {
        /**
         * Default disabled configuration.
         */
        val DISABLED = GuardianPipelineConfig(enabled = false)

        /**
         * Safe default configuration with INPUT_ONLY mode (NEW DEFAULT).
         * This provides the simplified architecture with optimal performance.
         */
        val DEFAULT_SAFE = GuardianPipelineConfig(
            enabled = true,
            mode = GuardianMode.INPUT_ONLY, // NEW: Simplified default
            checkpoints = setOf(GuardianCheckpoint.INPUT_VALIDATION),
            strictnessLevel = "medium",
            failureStrategy = GuardianFailureStrategy.BLOCK,
            streamingWordAccumulationCount = 20,
            microBatchSize = 3, // Deprecated but maintained for compatibility
            windowOverlap = 1   // Deprecated but maintained for compatibility
        )
        

        /**
         * Maximum protection configuration with INPUT_ONLY mode.
         * Provides strongest input validation with optimal performance.
         */
        val MAXIMUM_PROTECTION = GuardianPipelineConfig(
            enabled = true,
            mode = GuardianMode.INPUT_ONLY, // NEW: Use simplified architecture
            checkpoints = setOf(GuardianCheckpoint.INPUT_VALIDATION),
            strictnessLevel = "high",
            failureStrategy = GuardianFailureStrategy.BLOCK, // Block for maximum protection
            streamingWordAccumulationCount = 10,
            microBatchSize = 2, // Deprecated but maintained for compatibility
            windowOverlap = 1   // Deprecated but maintained for compatibility
        )
        
        /**
         * Input-only Guardian configuration with relaxed settings.
         * Optimal for performance-critical applications.
         */
        val INPUT_ONLY_RELAXED = GuardianPipelineConfig(
            enabled = true,
            mode = GuardianMode.INPUT_ONLY,
            checkpoints = setOf(GuardianCheckpoint.INPUT_VALIDATION),
            strictnessLevel = "low",
            failureStrategy = GuardianFailureStrategy.WARN,
            streamingWordAccumulationCount = 30
        )
    }
}