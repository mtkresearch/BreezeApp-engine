package com.mtkresearch.breezeapp.engine.annotation

import org.junit.Test
import org.junit.Assert.*

/**
 * Critical unit tests for RunnerPriority enum functionality.
 * 
 * Tests the most important aspects for runner selection:
 * - Priority value assignment and ordering
 * - Comparison methods for selection logic
 * - Utility methods for priority handling
 */
class RunnerPriorityTest {

    @Test
    fun `priority values are correctly assigned for selection algorithm`() {
        // CRITICAL: Priority values directly affect selection algorithm
        assertEquals("HIGH priority must have value 0 (highest)", 0, RunnerPriority.HIGH.value)
        assertEquals("NORMAL priority must have value 1", 1, RunnerPriority.NORMAL.value)
        assertEquals("LOW priority must have value 2 (lowest)", 2, RunnerPriority.LOW.value)
    }

    @Test
    fun `priority ordering is correct for selection`() {
        // CRITICAL: Selection algorithm depends on correct priority ordering
        assertTrue("HIGH must be higher than NORMAL", 
            RunnerPriority.HIGH.isHigherThan(RunnerPriority.NORMAL))
        assertTrue("HIGH must be higher than LOW", 
            RunnerPriority.HIGH.isHigherThan(RunnerPriority.LOW))
        assertTrue("NORMAL must be higher than LOW", 
            RunnerPriority.NORMAL.isHigherThan(RunnerPriority.LOW))
        
        assertFalse("NORMAL must not be higher than HIGH", 
            RunnerPriority.NORMAL.isHigherThan(RunnerPriority.HIGH))
        assertFalse("LOW must not be higher than NORMAL", 
            RunnerPriority.LOW.isHigherThan(RunnerPriority.NORMAL))
        assertFalse("LOW must not be higher than HIGH", 
            RunnerPriority.LOW.isHigherThan(RunnerPriority.HIGH))
    }

    @Test
    fun `priority comparison methods work correctly`() {
        // CRITICAL: Comparison methods are used in selection algorithms
        
        // Test isHigherThan
        assertTrue("HIGH > NORMAL", RunnerPriority.HIGH.isHigherThan(RunnerPriority.NORMAL))
        assertFalse("NORMAL not > HIGH", RunnerPriority.NORMAL.isHigherThan(RunnerPriority.HIGH))
        assertFalse("HIGH not > HIGH", RunnerPriority.HIGH.isHigherThan(RunnerPriority.HIGH))
        
        // Test isLowerThan
        assertTrue("NORMAL < HIGH", RunnerPriority.NORMAL.isLowerThan(RunnerPriority.HIGH))
        assertFalse("HIGH not < NORMAL", RunnerPriority.HIGH.isLowerThan(RunnerPriority.NORMAL))
        assertFalse("NORMAL not < NORMAL", RunnerPriority.NORMAL.isLowerThan(RunnerPriority.NORMAL))
    }

    @Test
    fun `priority calculation formula works correctly`() {
        // CRITICAL: Priority formula is documented and must be consistent
        // Formula: (vendorIndex × 10) + priorityIndex
        
        val vendorIndex = 2 // Example vendor index
        
        val highScore = (vendorIndex * 10) + RunnerPriority.HIGH.value
        val normalScore = (vendorIndex * 10) + RunnerPriority.NORMAL.value
        val lowScore = (vendorIndex * 10) + RunnerPriority.LOW.value
        
        assertEquals("HIGH priority calculation", 20, highScore)
        assertEquals("NORMAL priority calculation", 21, normalScore)
        assertEquals("LOW priority calculation", 22, lowScore)
        
        // Verify ordering
        assertTrue("HIGH score must be less than NORMAL", highScore < normalScore)
        assertTrue("NORMAL score must be less than LOW", normalScore < lowScore)
    }

    @Test
    fun `ordered by priority returns correct sequence`() {
        // CRITICAL: Ordered list is used for selection algorithms
        val ordered = RunnerPriority.orderedByPriority()
        
        assertEquals("Must have all 3 priorities", 3, ordered.size)
        assertEquals("First must be HIGH", RunnerPriority.HIGH, ordered[0])
        assertEquals("Second must be NORMAL", RunnerPriority.NORMAL, ordered[1])
        assertEquals("Third must be LOW", RunnerPriority.LOW, ordered[2])
    }

    @Test
    fun `companion object methods return correct values`() {
        // CRITICAL: Utility methods must return consistent values
        assertEquals("Highest priority must be HIGH", RunnerPriority.HIGH, RunnerPriority.highest())
        assertEquals("Lowest priority must be LOW", RunnerPriority.LOW, RunnerPriority.lowest())
        assertEquals("Default priority must be NORMAL", RunnerPriority.NORMAL, RunnerPriority.default())
    }

    @Test
    fun `fromValue method handles all valid values`() {
        // CRITICAL: fromValue is used for configuration parsing
        assertEquals("Value 0 must return HIGH", RunnerPriority.HIGH, RunnerPriority.fromValue(0))
        assertEquals("Value 1 must return NORMAL", RunnerPriority.NORMAL, RunnerPriority.fromValue(1))
        assertEquals("Value 2 must return LOW", RunnerPriority.LOW, RunnerPriority.fromValue(2))
    }

    @Test
    fun `fromValue method handles invalid values gracefully`() {
        // CRITICAL: Invalid values must not crash the system
        assertEquals("Invalid negative value must return default", 
            RunnerPriority.NORMAL, RunnerPriority.fromValue(-1))
        assertEquals("Invalid high value must return default", 
            RunnerPriority.NORMAL, RunnerPriority.fromValue(999))
        assertEquals("Invalid value must return default", 
            RunnerPriority.NORMAL, RunnerPriority.fromValue(5))
    }

    @Test
    fun `priority descriptions are non-empty`() {
        // CRITICAL: Descriptions are used for UI and documentation
        RunnerPriority.values().forEach { priority ->
            assertNotNull("Priority $priority must have description", priority.description)
            assertTrue("Priority $priority description must not be empty", 
                priority.description.isNotBlank())
            assertNotNull("Priority $priority must have use cases", priority.useCases)
            assertTrue("Priority $priority use cases must not be empty", 
                priority.useCases.isNotBlank())
        }
    }

    @Test
    fun `priority values are unique and sequential`() {
        // CRITICAL: Priority values must be unique for correct ordering
        val values = RunnerPriority.values().map { it.value }
        val uniqueValues = values.toSet()
        
        assertEquals("All priority values must be unique", values.size, uniqueValues.size)
        assertEquals("Priority values must be sequential starting from 0", 
            listOf(0, 1, 2), values.sorted())
    }
}