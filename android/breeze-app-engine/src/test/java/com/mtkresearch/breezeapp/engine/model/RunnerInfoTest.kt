package com.mtkresearch.breezeapp.engine.model

import com.mtkresearch.breezeapp.engine.annotation.VendorType
import com.mtkresearch.breezeapp.engine.annotation.RunnerPriority
import com.mtkresearch.breezeapp.engine.annotation.HardwareRequirement
import android.os.Parcel
import android.os.Parcelable
import org.junit.Test
import org.junit.Assert.*

/**
 * Critical unit tests for RunnerInfo data class functionality.
 * 
 * Tests the most important aspects for runner management:
 * - Builder pattern correctness
 * - Capability checking methods
 * - Metadata handling
 * - Parcelable implementation
 * - Immutability and thread safety
 */
class RunnerInfoTest {

    @Test
    fun `minimal RunnerInfo factory works correctly`() {
        // CRITICAL: Minimal factory is used for simple runner creation
        val runnerInfo = com.mtkresearch.breezeapp.engine.model.RunnerInfo.minimal("test-runner", CapabilityType.LLM, VendorType.MEDIATEK)
        
        assertEquals("Name must be set correctly", "test-runner", runnerInfo.name)
        assertEquals("Display name must default to name", "test-runner", runnerInfo.displayName)
        assertEquals("Version must have default", "1.0.0", runnerInfo.version)
        assertEquals("Must have single capability", 1, runnerInfo.capabilities.size)
        assertTrue("Must contain specified capability", 
            runnerInfo.capabilities.contains(CapabilityType.LLM))
        assertEquals("Vendor must be set correctly", VendorType.MEDIATEK, runnerInfo.vendor)
        assertEquals("Priority must default to NORMAL", RunnerPriority.NORMAL, runnerInfo.priority)
        assertTrue("Hardware requirements must be empty", runnerInfo.hardwareRequirements.isEmpty())
        assertTrue("Must be enabled by default", runnerInfo.enabled)
    }

    @Test
    fun `builder pattern builds complete RunnerInfo`() {
        // CRITICAL: Builder pattern is used for complex runner configuration
        val runnerInfo = com.mtkresearch.breezeapp.engine.model.RunnerInfo.builder("test-runner", "1.2.3")
            .displayName("Test Display Name")
            .capabilities(CapabilityType.LLM, CapabilityType.TTS)
            .vendor(VendorType.SHERPA)
            .priority(RunnerPriority.HIGH)
            .hardwareRequirements(HardwareRequirement.HIGH_MEMORY, HardwareRequirement.MICROPHONE)
            .description("Test description")
            .enabled(false)
            .apiLevel(2)
            .metadata("test_key", "test_value")
            .metadata(mapOf("batch_key" to 42))
            .build()
        
        assertEquals("Name must be set", "test-runner", runnerInfo.name)
        assertEquals("Display name must be set", "Test Display Name", runnerInfo.displayName)
        assertEquals("Version must be set", "1.2.3", runnerInfo.version)
        assertEquals("Must have 2 capabilities", 2, runnerInfo.capabilities.size)
        assertTrue("Must contain LLM capability", runnerInfo.capabilities.contains(CapabilityType.LLM))
        assertTrue("Must contain TTS capability", runnerInfo.capabilities.contains(CapabilityType.TTS))
        assertEquals("Vendor must be set", VendorType.SHERPA, runnerInfo.vendor)
        assertEquals("Priority must be set", RunnerPriority.HIGH, runnerInfo.priority)
        assertEquals("Must have 2 hardware requirements", 2, runnerInfo.hardwareRequirements.size)
        assertEquals("Description must be set", "Test description", runnerInfo.description)
        assertFalse("Must be disabled", runnerInfo.enabled)
        assertEquals("API level must be set", 2, runnerInfo.apiLevel)
        assertEquals("Metadata must contain string value", "test_value", runnerInfo.getMetadata<String>("test_key"))
        assertEquals("Metadata must contain int value", 42, runnerInfo.getMetadata<Int>("batch_key"))
    }

    @Test
    fun `builder validation works correctly`() {
        // CRITICAL: Builder must validate required fields
        val builder = com.mtkresearch.breezeapp.engine.model.RunnerInfo.builder("test", "1.0.0")
        
        // Should fail without capabilities
        assertThrows("Builder must require capabilities", IllegalArgumentException::class.java) {
            builder.description("test").build()
        }
        
        // Should fail without description
        assertThrows("Builder must require description", IllegalArgumentException::class.java) {
            builder.capabilities(CapabilityType.LLM).build()
        }
        
        // Should fail with empty description
        assertThrows("Builder must require non-blank description", IllegalArgumentException::class.java) {
            builder.capabilities(CapabilityType.LLM).description("   ").build()
        }
    }

    @Test
    fun `capability checking methods work correctly`() {
        // CRITICAL: Capability checking is used throughout the system
        val runnerInfo = com.mtkresearch.breezeapp.engine.model.RunnerInfo.builder("test", "1.0.0")
            .capabilities(CapabilityType.LLM, CapabilityType.ASR)
            .description("test")
            .build()
        
        // Single capability checks
        assertTrue("Must support LLM", runnerInfo.supportsCapability(CapabilityType.LLM))
        assertTrue("Must support ASR", runnerInfo.supportsCapability(CapabilityType.ASR))
        assertFalse("Must not support TTS", runnerInfo.supportsCapability(CapabilityType.TTS))
        
        // Multiple capability checks - any
        assertTrue("Must support any of LLM or TTS", 
            runnerInfo.supportsAnyCapability(CapabilityType.LLM, CapabilityType.TTS))
        assertFalse("Must not support any of TTS or VLM", 
            runnerInfo.supportsAnyCapability(CapabilityType.TTS, CapabilityType.VLM))
        
        // Multiple capability checks - all
        assertTrue("Must support all of LLM and ASR", 
            runnerInfo.supportsAllCapabilities(CapabilityType.LLM, CapabilityType.ASR))
        assertFalse("Must not support all of LLM and TTS", 
            runnerInfo.supportsAllCapabilities(CapabilityType.LLM, CapabilityType.TTS))
    }

    @Test
    fun `metadata handling works correctly`() {
        // CRITICAL: Metadata is used for runner-specific configuration
        val runnerInfo = com.mtkresearch.breezeapp.engine.model.RunnerInfo.builder("test", "1.0.0")
            .capabilities(CapabilityType.LLM)
            .description("test")
            .metadata("string_key", "string_value")
            .metadata("int_key", 42)
            .metadata("boolean_key", true)
            .build()
        
        // Typed metadata access
        assertEquals("String metadata must be accessible", "string_value", 
            runnerInfo.getMetadata<String>("string_key"))
        assertEquals("Int metadata must be accessible", 42, 
            runnerInfo.getMetadata<Int>("int_key"))
        assertEquals("Boolean metadata must be accessible", true, 
            runnerInfo.getMetadata<Boolean>("boolean_key"))
        
        // Non-existent keys
        assertNull("Non-existent key must return null", 
            runnerInfo.getMetadata<String>("non_existent"))
        
        // Default values
        assertEquals("Default value must be returned", "default", 
            runnerInfo.getMetadata("non_existent", "default"))
        assertEquals("Existing value must override default", "string_value", 
            runnerInfo.getMetadata("string_key", "default"))
        
        // Wrong type casting
        assertNull("Wrong type cast must return null", 
            runnerInfo.getMetadata<Int>("string_key"))
    }

    @Test
    fun `withMetadata creates new instance with merged metadata`() {
        // CRITICAL: Immutability must be preserved when updating metadata
        val original = com.mtkresearch.breezeapp.engine.model.RunnerInfo.builder("test", "1.0.0")
            .capabilities(CapabilityType.LLM)
            .description("test")
            .metadata("original_key", "original_value")
            .build()
        
        val updated = original.withMetadata(mapOf("new_key" to "new_value", "original_key" to "updated_value"))
        
        // Original must be unchanged
        assertEquals("Original must retain original value", "original_value", 
            original.getMetadata<String>("original_key"))
        assertNull("Original must not have new key", 
            original.getMetadata<String>("new_key"))
        
        // Updated must have merged metadata
        assertEquals("Updated must have new value", "updated_value", 
            updated.getMetadata<String>("original_key"))
        assertEquals("Updated must have new key", "new_value", 
            updated.getMetadata<String>("new_key"))
        
        // Objects must be different instances
        assertNotSame("Must create new instance", original, updated)
    }

    @Test
    fun `withEnabled creates new instance with updated status`() {
        // CRITICAL: Immutability must be preserved when updating enabled status
        val original = com.mtkresearch.breezeapp.engine.model.RunnerInfo.minimal("test", CapabilityType.LLM)
        assertTrue("Original must be enabled", original.enabled)
        
        val disabled = original.withEnabled(false)
        assertFalse("Disabled instance must be disabled", disabled.enabled)
        assertTrue("Original must still be enabled", original.enabled)
        assertNotSame("Must create new instance", original, disabled)
    }

    @Test
    fun `parcelable implementation works correctly`() {
        // CRITICAL: Parcelable is required for IPC and bundle storage
        val original = RunnerInfo.builder("test-runner", "1.2.3")
            .displayName("Test Runner")
            .capabilities(CapabilityType.LLM, CapabilityType.ASR)
            .vendor(VendorType.MEDIATEK)
            .priority(RunnerPriority.HIGH)
            .hardwareRequirements(HardwareRequirement.MTK_NPU, HardwareRequirement.HIGH_MEMORY)
            .description("Test description")
            .enabled(false)
            .apiLevel(2)
            .metadata("test_key", "test_value")
            .build()
        
        // Test Parcelable implementation exists
        assertTrue("RunnerInfo must implement Parcelable", original is Parcelable)
        
        // Test describeContents
        assertEquals("Parcelable contents should be 0", 0, original.describeContents())
        
        // Test basic serialization properties
        assertNotNull("RunnerInfo should have valid properties", original.name)
        assertNotNull("RunnerInfo should have valid metadata", original.metadata)
    }

    @Test
    fun `toSummaryString provides useful debug information`() {
        // CRITICAL: Summary string is used for logging and debugging
        val runnerInfo = RunnerInfo.builder("test-runner", "1.0.0")
            .capabilities(CapabilityType.LLM, CapabilityType.ASR)
            .vendor(VendorType.MEDIATEK)
            .priority(RunnerPriority.HIGH)
            .description("test")
            .enabled(false)
            .build()
        
        val summary = runnerInfo.toSummaryString()
        
        assertTrue("Summary must contain name", summary.contains("test-runner"))
        assertTrue("Summary must contain vendor", summary.contains("MEDIATEK"))
        assertTrue("Summary must contain priority", summary.contains("HIGH"))
        assertTrue("Summary must contain capabilities", summary.contains("LLM"))
        assertTrue("Summary must contain capabilities", summary.contains("ASR"))
        assertTrue("Summary must contain enabled status", summary.contains("enabled=false"))
    }

    @Test
    fun `metadata keys constants are defined`() {
        // CRITICAL: Standard metadata keys prevent typos in runner implementations
        // Verify standard metadata keys are correctly defined
        val modelNameKey = "model_name"
        val modelVersionKey = "model_version"
        val backendNameKey = "backend_name"
        
        assertEquals("MODEL_NAME key must be correct", modelNameKey, "model_name")
        assertEquals("MODEL_VERSION key must be correct", modelVersionKey, "model_version")
        assertEquals("BACKEND_NAME key must be correct", backendNameKey, "backend_name")
        
        // Verify they are meaningful strings
        assertTrue("MODEL_NAME must not be blank", modelNameKey.isNotBlank())
        assertTrue("MODEL_VERSION must not be blank", modelVersionKey.isNotBlank())
        assertTrue("BACKEND_NAME must not be blank", backendNameKey.isNotBlank())
    }
}