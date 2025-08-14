package com.mtkresearch.breezeapp.engine.annotation

/**
 * Priority levels for AI runners within their vendor category.
 * 
 * This enum provides a simple, maintainable priority system that replaces
 * complex string-based configurations. Priorities are used for selection
 * when multiple runners from the same vendor support the same capability.
 * 
 * **Priority Formula:**
 * Final priority score = (vendorIndex × 10) + priorityIndex
 * Where priorityIndex: HIGH=0, NORMAL=1, LOW=2
 * 
 * **Selection Logic:**
 * 1. Vendor priority (capability-specific ordering)
 * 2. Runner priority within vendor (HIGH > NORMAL > LOW)
 * 3. Hardware availability and compatibility
 * 
 * **Example Priority Calculation:**
 * ```
 * MEDIATEK-HIGH:    (0 × 10) + 0 = 0   ← Highest priority
 * MEDIATEK-NORMAL:  (0 × 10) + 1 = 1
 * META-HIGH:        (1 × 10) + 0 = 10
 * OPENROUTER-LOW:   (2 × 10) + 2 = 22  ← Lower priority
 * ```
 * 
 * @since Engine API v2.0
 * @see AIRunner.priority
 * @see VendorType
 */
enum class RunnerPriority(
    /**
     * Numeric value used in priority calculations.
     * Lower values = higher priority.
     */
    val value: Int,
    
    /**
     * Human-readable description of this priority level.
     */
    val description: String,
    
    /**
     * Typical use cases for this priority level.
     */
    val useCases: String
) {
    /**
     * Highest priority within vendor category.
     * 
     * Reserved for premium, flagship, or hardware-accelerated runners
     * that provide the best performance and latest features.
     * 
     * **Typical Characteristics:**
     * - Hardware acceleration (NPU, GPU)
     * - Latest model versions
     * - Optimal performance/quality
     * - Premium features enabled
     * - Higher resource requirements
     * 
     * **Selection Weight:** 0 (highest)
     */
    HIGH(
        value = 0,
        description = "Premium/flagship runners with optimal performance",
        useCases = "Hardware-accelerated inference, latest models, premium features"
    ),
    
    /**
     * Standard priority within vendor category.
     * 
     * Default priority for most runners. Provides balanced performance
     * and compatibility for general use cases.
     * 
     * **Typical Characteristics:**
     * - Balanced performance/resource usage
     * - Standard model versions
     * - Good compatibility
     * - Moderate resource requirements
     * - Default selection for most users
     * 
     * **Selection Weight:** 1 (normal)
     */
    NORMAL(
        value = 1,
        description = "Standard runners with balanced performance",
        useCases = "General use cases, balanced performance/resource usage"
    ),
    
    /**
     * Lowest priority within vendor category.
     * 
     * Used for lightweight, fallback, or experimental runners
     * that prioritize compatibility over performance.
     * 
     * **Typical Characteristics:**
     * - CPU-only processing
     * - Lightweight models
     * - Minimal resource requirements
     * - Maximum compatibility
     * - Fallback/emergency usage
     * 
     * **Selection Weight:** 2 (lowest)
     */
    LOW(
        value = 2,
        description = "Lite/fallback runners with minimal requirements",
        useCases = "CPU-only inference, lightweight models, maximum compatibility"
    );
    
    /**
     * Returns true if this priority is higher than the specified priority.
     * 
     * @param other The priority to compare against
     * @return true if this priority has a lower numeric value (higher priority)
     */
    fun isHigherThan(other: RunnerPriority): Boolean = this.value < other.value
    
    /**
     * Returns true if this priority is lower than the specified priority.
     * 
     * @param other The priority to compare against  
     * @return true if this priority has a higher numeric value (lower priority)
     */
    fun isLowerThan(other: RunnerPriority): Boolean = this.value > other.value
    
    companion object {
        /**
         * Returns all priorities ordered from highest to lowest.
         */
        fun orderedByPriority(): List<RunnerPriority> = values().sortedBy { it.value }
        
        /**
         * Returns the priority with the highest precedence.
         */
        fun highest(): RunnerPriority = HIGH
        
        /**
         * Returns the priority with the lowest precedence.
         */
        fun lowest(): RunnerPriority = LOW
        
        /**
         * Returns the default priority for new runners.
         */
        fun default(): RunnerPriority = NORMAL
        
        /**
         * Converts a numeric priority value back to enum.
         * 
         * @param value The numeric priority value (0, 1, or 2)
         * @return The corresponding RunnerPriority, or NORMAL if invalid
         */
        fun fromValue(value: Int): RunnerPriority = values().find { it.value == value } ?: NORMAL
    }
}