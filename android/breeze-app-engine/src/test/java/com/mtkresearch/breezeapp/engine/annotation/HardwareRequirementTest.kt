package com.mtkresearch.breezeapp.engine.annotation

import android.content.Context
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock

/**
 * Critical unit tests for HardwareRequirement enum functionality.
 * 
 * Tests the most important aspects for runner validation:
 * - Requirement categorization
 * - Threshold value consistency
 * - Validation utility methods
 * - Error handling for validation failures
 */
class HardwareRequirementTest {

    @Mock
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `all requirements have valid categories`() {
        // CRITICAL: Categories are used for UI grouping and validation logic
        HardwareRequirement.values().forEach { requirement ->
            assertNotNull("Requirement $requirement must have category", requirement.category)
            assertNotNull("Category must have display name", requirement.category.displayName)
            assertTrue("Category display name must not be empty", 
                requirement.category.displayName.isNotBlank())
        }
    }

    @Test
    fun `memory requirements have correct threshold values`() {
        // CRITICAL: Memory thresholds are used for device compatibility checking
        val highMemory = HardwareRequirement.HIGH_MEMORY.getThresholdValue()
        val mediumMemory = HardwareRequirement.MEDIUM_MEMORY.getThresholdValue()
        val lowMemory = HardwareRequirement.LOW_MEMORY.getThresholdValue()
        
        assertNotNull("HIGH_MEMORY must have threshold value", highMemory)
        assertNotNull("MEDIUM_MEMORY must have threshold value", mediumMemory)
        assertNotNull("LOW_MEMORY must have threshold value", lowMemory)
        
        assertTrue("HIGH_MEMORY must be larger than MEDIUM_MEMORY", highMemory!! > mediumMemory!!)
        assertTrue("MEDIUM_MEMORY must be larger than LOW_MEMORY", mediumMemory > lowMemory!!)
        
        // Verify specific values (8GB, 4GB, 2GB)
        assertEquals("HIGH_MEMORY threshold must be 8GB", 8L * 1024 * 1024 * 1024, highMemory)
        assertEquals("MEDIUM_MEMORY threshold must be 4GB", 4L * 1024 * 1024 * 1024, mediumMemory)
        assertEquals("LOW_MEMORY threshold must be 2GB", 2L * 1024 * 1024 * 1024, lowMemory)
    }

    @Test
    fun `storage requirements have correct threshold values`() {
        // CRITICAL: Storage thresholds are used for model download validation
        val largeStorage = HardwareRequirement.LARGE_STORAGE.getThresholdValue()
        val mediumStorage = HardwareRequirement.MEDIUM_STORAGE.getThresholdValue()
        
        assertNotNull("LARGE_STORAGE must have threshold value", largeStorage)
        assertNotNull("MEDIUM_STORAGE must have threshold value", mediumStorage)
        
        assertTrue("LARGE_STORAGE must be larger than MEDIUM_STORAGE", largeStorage!! > mediumStorage!!)
        
        // Verify specific values (1GB, 512MB)
        assertEquals("LARGE_STORAGE threshold must be 1GB", 1024L * 1024 * 1024, largeStorage)
        assertEquals("MEDIUM_STORAGE threshold must be 512MB", 512L * 1024 * 1024, mediumStorage)
    }

    @Test
    fun `non-threshold requirements return null threshold`() {
        // CRITICAL: Non-quantitative requirements should not have threshold values
        assertNull("INTERNET should not have threshold value", 
            HardwareRequirement.INTERNET.getThresholdValue())
        assertNull("MTK_NPU should not have threshold value", 
            HardwareRequirement.MTK_NPU.getThresholdValue())
        assertNull("CPU should not have threshold value", 
            HardwareRequirement.CPU.getThresholdValue())
        assertNull("MICROPHONE should not have threshold value", 
            HardwareRequirement.MICROPHONE.getThresholdValue())
        assertNull("CAMERA should not have threshold value", 
            HardwareRequirement.CAMERA.getThresholdValue())
    }

    @Test
    fun `cpu requirement is always satisfied`() {
        // CRITICAL: CPU is always available and must always validate to true
        assertTrue("CPU requirement must always be satisfied", 
            HardwareRequirement.CPU.isSatisfied(mockContext))
    }

    @Test
    fun `requirements are correctly categorized`() {
        // CRITICAL: Category filtering is used for UI and validation grouping
        val connectivityRequirements = HardwareRequirement.getByCategory(RequirementCategory.CONNECTIVITY)
        val processingRequirements = HardwareRequirement.getByCategory(RequirementCategory.PROCESSING)
        val memoryRequirements = HardwareRequirement.getByCategory(RequirementCategory.MEMORY)
        val storageRequirements = HardwareRequirement.getByCategory(RequirementCategory.STORAGE)
        val sensorRequirements = HardwareRequirement.getByCategory(RequirementCategory.SENSORS)
        
        // Verify CONNECTIVITY category
        assertTrue("INTERNET must be in CONNECTIVITY category", 
            connectivityRequirements.contains(HardwareRequirement.INTERNET))
        
        // Verify PROCESSING category
        assertTrue("MTK_NPU must be in PROCESSING category", 
            processingRequirements.contains(HardwareRequirement.MTK_NPU))
        assertTrue("CPU must be in PROCESSING category", 
            processingRequirements.contains(HardwareRequirement.CPU))
        
        // Verify MEMORY category
        assertTrue("HIGH_MEMORY must be in MEMORY category", 
            memoryRequirements.contains(HardwareRequirement.HIGH_MEMORY))
        assertTrue("MEDIUM_MEMORY must be in MEMORY category", 
            memoryRequirements.contains(HardwareRequirement.MEDIUM_MEMORY))
        assertTrue("LOW_MEMORY must be in MEMORY category", 
            memoryRequirements.contains(HardwareRequirement.LOW_MEMORY))
        
        // Verify STORAGE category
        assertTrue("LARGE_STORAGE must be in STORAGE category", 
            storageRequirements.contains(HardwareRequirement.LARGE_STORAGE))
        assertTrue("MEDIUM_STORAGE must be in STORAGE category", 
            storageRequirements.contains(HardwareRequirement.MEDIUM_STORAGE))
        
        // Verify SENSORS category
        assertTrue("MICROPHONE must be in SENSORS category", 
            sensorRequirements.contains(HardwareRequirement.MICROPHONE))
        assertTrue("CAMERA must be in SENSORS category", 
            sensorRequirements.contains(HardwareRequirement.CAMERA))
    }

    @Test
    fun `validateAll returns correct structure`() {
        // CRITICAL: validateAll is used for batch validation in runner registration
        val requirements = listOf(
            HardwareRequirement.CPU,
            HardwareRequirement.MTK_NPU,
            HardwareRequirement.HIGH_MEMORY
        )
        
        val results = HardwareRequirement.validateAll(requirements, mockContext)
        
        assertEquals("Must validate all provided requirements", requirements.size, results.size)
        requirements.forEach { requirement ->
            assertTrue("Must contain result for $requirement", results.containsKey(requirement))
            assertNotNull("Result for $requirement must not be null", results[requirement])
        }
    }

    @Test
    fun `getUnsatisfied filters correctly`() {
        // CRITICAL: getUnsatisfied is used to determine why runner registration failed
        val requirements = listOf(
            HardwareRequirement.CPU,  // Always satisfied
            HardwareRequirement.MTK_NPU  // Usually not satisfied in tests
        )
        
        val unsatisfied = HardwareRequirement.getUnsatisfied(requirements, mockContext)
        
        // CPU should always be satisfied (not in unsatisfied list)
        assertFalse("CPU should not be in unsatisfied list", 
            unsatisfied.contains(HardwareRequirement.CPU))
        
        // Result should be a subset of input requirements
        assertTrue("Unsatisfied must be subset of input requirements", 
            requirements.containsAll(unsatisfied))
    }

    @Test
    fun `all requirements have non-empty descriptions`() {
        // CRITICAL: Descriptions are used for error messages and UI
        HardwareRequirement.values().forEach { requirement ->
            assertNotNull("Requirement $requirement must have display name", requirement.displayName)
            assertTrue("Requirement $requirement display name must not be empty", 
                requirement.displayName.isNotBlank())
            assertNotNull("Requirement $requirement must have description", requirement.description)
            assertTrue("Requirement $requirement description must not be empty", 
                requirement.description.isNotBlank())
        }
    }

    @Test
    fun `requirement categories are complete`() {
        // CRITICAL: All requirements must be categorized
        val allRequirements = HardwareRequirement.values().toSet()
        val categorizedRequirements = RequirementCategory.values()
            .flatMap { category -> HardwareRequirement.getByCategory(category) }
            .toSet()
        
        assertEquals("All requirements must be categorized", 
            allRequirements, categorizedRequirements)
    }

    @Test
    fun `requirement validation handles exceptions gracefully`() {
        // CRITICAL: Validation failures must not crash the system
        val nullContext: Context? = null
        
        // Validation with null context should not throw exceptions
        assertDoesNotThrow("CPU validation with null context must not throw") {
            HardwareRequirement.CPU.isSatisfied(nullContext)
        }
        
        // CPU should always return true even with null context
        assertTrue("CPU should be satisfied with null context", 
            HardwareRequirement.CPU.isSatisfied(nullContext))
        
        // All requirements should handle null context gracefully (no exceptions)
        HardwareRequirement.values().forEach { requirement ->
            assertDoesNotThrow("Validation of $requirement with null context must not throw") {
                requirement.isSatisfied(nullContext)
            }
        }
        
        // Context-dependent requirements should return false with null context
        assertFalse("INTERNET should not be satisfied with null context",
            HardwareRequirement.INTERNET.isSatisfied(nullContext))
        assertFalse("MICROPHONE should not be satisfied with null context",
            HardwareRequirement.MICROPHONE.isSatisfied(nullContext))
        assertFalse("CAMERA should not be satisfied with null context",
            HardwareRequirement.CAMERA.isSatisfied(nullContext))
    }

    private fun assertDoesNotThrow(message: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            fail("$message but threw: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}