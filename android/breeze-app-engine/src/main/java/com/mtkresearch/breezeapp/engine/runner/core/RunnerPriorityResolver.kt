package com.mtkresearch.breezeapp.engine.runner.core

import com.mtkresearch.breezeapp.engine.annotation.AIRunner
import com.mtkresearch.breezeapp.engine.annotation.VendorType
import com.mtkresearch.breezeapp.engine.annotation.RunnerPriority

/**
 * RunnerPriorityResolver - Runner Priority Calculation and Selection
 * 
 * Implements the priority formula for runner selection: (vendor.ordinal × 10) + priority.ordinal
 * Lower numbers indicate higher priority. This provides a clear, predictable selection
 * algorithm that prioritizes certain vendors while allowing fine-grained priority control.
 * 
 * ## Priority Calculation
 * The formula ensures vendor-level grouping with priority-level ordering:
 * 
 * ### Vendor Priority Order (vendor.ordinal × 10):
 * - **MEDIATEK**: 0-9 (highest priority vendor)
 * - **SHERPA**: 10-19  
 * - **OPENROUTER**: 20-29
 * - **META**: 30-39
 * - **UNKNOWN**: 40-49 (lowest priority vendor)
 * 
 * ### Within-Vendor Priority (+ priority.ordinal):
 * - **HIGH**: +0 (highest within vendor)
 * - **NORMAL**: +1
 * - **LOW**: +2 (lowest within vendor)
 * 
 * ### Example Calculations:
 * - MediaTek HIGH: (0 × 10) + 0 = **0** (highest overall)
 * - MediaTek NORMAL: (0 × 10) + 1 = **1**
 * - Sherpa HIGH: (1 × 10) + 0 = **10**
 * - Unknown HIGH: (4 × 10) + 0 = **40**
 * 
 * ## Selection Logic
 * When multiple runners support the same capability:
 * 1. Calculate priority for each runner using the formula
 * 2. Select the runner with the lowest priority number
 * 3. Break ties by preferring the first registered runner
 * 
 * @since Engine API v2.0
 */
object RunnerPriorityResolver {
    
    /**
     * Calculate the numerical priority for a runner based on vendor and priority.
     * 
     * Formula: (vendor.ordinal × 10) + priority.ordinal
     * Lower numbers = higher priority
     * 
     * @param vendor The AI provider/technology vendor
     * @param priority The priority level within the vendor category
     * @return Numerical priority (0 = highest, higher numbers = lower priority)
     */
    fun calculatePriority(vendor: VendorType, priority: RunnerPriority): Int {
        return (vendor.ordinal * 10) + priority.ordinal
    }
    
    /**
     * Calculate the priority for a runner instance by extracting annotation data.
     * 
     * @param runner The runner instance to calculate priority for
     * @return Numerical priority, or Int.MAX_VALUE if annotation is missing
     */
    fun calculatePriority(runner: BaseRunner): Int {
        val annotation = runner.javaClass.getAnnotation(AIRunner::class.java)
        return if (annotation != null) {
            calculatePriority(annotation.vendor, annotation.priority)
        } else {
            Int.MAX_VALUE // Lowest priority for runners without annotations
        }
    }
    
    /**
     * Select the best (highest priority) runner from a list of candidates.
     * 
     * @param candidates List of runner candidates that support the required capability
     * @return The runner with the highest priority (lowest number), or null if list is empty
     */
    fun selectBestRunner(candidates: List<BaseRunner>): BaseRunner? {
        if (candidates.isEmpty()) return null
        
        return candidates.minByOrNull { runner ->
            calculatePriority(runner)
        }
    }
    
    /**
     * Sort a list of runners by priority (highest priority first).
     * 
     * @param runners List of runners to sort
     * @return New list sorted by priority, with highest priority (lowest number) first
     */
    fun sortByPriority(runners: List<BaseRunner>): List<BaseRunner> {
        return runners.sortedBy { runner ->
            calculatePriority(runner)
        }
    }
    
    /**
     * Group runners by vendor and sort by priority within each group.
     * 
     * @param runners List of runners to group and sort
     * @return Map of vendor to sorted list of runners
     */
    fun groupByVendor(runners: List<BaseRunner>): Map<VendorType, List<BaseRunner>> {
        return runners
            .groupBy { runner ->
                val annotation = runner.javaClass.getAnnotation(AIRunner::class.java)
                annotation?.vendor ?: VendorType.UNKNOWN
            }
            .mapValues { (_, runnersInVendor) ->
                sortByPriority(runnersInVendor)
            }
    }
    
    /**
     * Get a human-readable description of the priority calculation.
     * 
     * @param vendor The vendor type
     * @param priority The priority level
     * @return Descriptive string explaining the priority calculation
     */
    fun describePriority(vendor: VendorType, priority: RunnerPriority): String {
        val numericPriority = calculatePriority(vendor, priority)
        return "Priority $numericPriority: ${vendor.displayName} ${priority.description} " +
                "(vendor=${vendor.ordinal}×10 + priority=${priority.ordinal})"
    }
    
    /**
     * Get priority information for a runner instance.
     * 
     * @param runner The runner to analyze
     * @return PriorityInfo containing detailed priority calculation
     */
    fun getPriorityInfo(runner: BaseRunner): PriorityInfo {
        val annotation = runner.javaClass.getAnnotation(AIRunner::class.java)
        return if (annotation != null) {
            val numericPriority = calculatePriority(annotation.vendor, annotation.priority)
            PriorityInfo(
                vendor = annotation.vendor,
                priority = annotation.priority,
                numericPriority = numericPriority,
                description = describePriority(annotation.vendor, annotation.priority),
                hasAnnotation = true
            )
        } else {
            PriorityInfo(
                vendor = VendorType.UNKNOWN,
                priority = RunnerPriority.LOW,
                numericPriority = Int.MAX_VALUE,
                description = "No @AIRunner annotation found",
                hasAnnotation = false
            )
        }
    }
    
    /**
     * Compare two runners and determine which has higher priority.
     * 
     * @param runner1 First runner to compare
     * @param runner2 Second runner to compare
     * @return Negative if runner1 has higher priority, positive if runner2 has higher priority, 0 if equal
     */
    fun comparePriority(runner1: BaseRunner, runner2: BaseRunner): Int {
        val priority1 = calculatePriority(runner1)
        val priority2 = calculatePriority(runner2)
        return priority1.compareTo(priority2)
    }
    
    /**
     * Check if a runner has higher priority than another.
     * 
     * @param runner1 The runner to check
     * @param runner2 The runner to compare against
     * @return true if runner1 has higher priority (lower number) than runner2
     */
    fun hasHigherPriority(runner1: BaseRunner, runner2: BaseRunner): Boolean {
        return comparePriority(runner1, runner2) < 0
    }
    
    /**
     * Get the theoretical priority range for all possible vendor/priority combinations.
     * 
     * @return PriorityRange containing min and max possible priority values
     */
    fun getPriorityRange(): PriorityRange {
        val allVendors = VendorType.values()
        val allPriorities = RunnerPriority.values()
        
        val minPriority = calculatePriority(allVendors.first(), allPriorities.first())
        val maxPriority = calculatePriority(allVendors.last(), allPriorities.last())
        
        return PriorityRange(
            minPriority = minPriority,
            maxPriority = maxPriority,
            totalCombinations = allVendors.size * allPriorities.size
        )
    }
}

/**
 * Data class containing detailed priority information for a runner.
 */
data class PriorityInfo(
    val vendor: VendorType,
    val priority: RunnerPriority,
    val numericPriority: Int,
    val description: String,
    val hasAnnotation: Boolean
)

/**
 * Data class containing the theoretical priority range.
 */
data class PriorityRange(
    val minPriority: Int,
    val maxPriority: Int,
    val totalCombinations: Int
)