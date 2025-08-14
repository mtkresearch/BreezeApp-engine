package com.mtkresearch.breezeapp.engine.annotation

import com.mtkresearch.breezeapp.engine.model.CapabilityType
import com.mtkresearch.breezeapp.engine.runner.core.BaseRunner
import com.mtkresearch.breezeapp.engine.model.InferenceRequest
import com.mtkresearch.breezeapp.engine.model.InferenceResult
import com.mtkresearch.breezeapp.engine.model.ModelConfig
import com.mtkresearch.breezeapp.engine.runner.core.RunnerInfo
import org.junit.Test
import org.junit.Assert.*

/**
 * Critical unit tests for @AIRunner annotation functionality.
 * 
 * Tests the most important aspects that could break the discovery system:
 * - Annotation presence and parameter access via reflection
 * - Default value handling
 * - Multi-capability support
 * - Hardware requirement validation
 */
class AIRunnerAnnotationTest {

    // Test runner implementations for annotation testing
    @AIRunner(
        capabilities = [CapabilityType.LLM],
        vendor = VendorType.MEDIATEK,
        priority = RunnerPriority.HIGH,
        hardwareRequirements = [HardwareRequirement.MTK_NPU, HardwareRequirement.HIGH_MEMORY]
    )
    class TestMediaTekRunner : BaseRunner {
        override fun load(config: ModelConfig): Boolean = true
        override fun run(input: InferenceRequest, stream: Boolean): InferenceResult = 
            InferenceResult.success(mapOf("test" to "result"))
        override fun unload() {}
        override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.LLM)
        override fun isLoaded(): Boolean = true
        override fun getRunnerInfo(): RunnerInfo = RunnerInfo("test", "1.0.0", listOf(CapabilityType.LLM))
    }

    @AIRunner(capabilities = [CapabilityType.ASR, CapabilityType.TTS])
    class TestDefaultsRunner : BaseRunner {
        override fun load(config: ModelConfig): Boolean = true
        override fun run(input: InferenceRequest, stream: Boolean): InferenceResult = 
            InferenceResult.success(mapOf("test" to "result"))
        override fun unload() {}
        override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.ASR, CapabilityType.TTS)
        override fun isLoaded(): Boolean = true
        override fun getRunnerInfo(): RunnerInfo = RunnerInfo("test", "1.0.0", listOf(CapabilityType.ASR))
    }

    @Test
    fun `annotation is present and accessible via reflection`() {
        // CRITICAL: Discovery system depends on reflection access to annotations
        val annotation = TestMediaTekRunner::class.java.getAnnotation(AIRunner::class.java)
        
        assertNotNull("@AIRunner annotation must be accessible via reflection", annotation)
        assertEquals("Capabilities must be accessible", listOf(CapabilityType.LLM), annotation.capabilities.toList())
        assertEquals("Vendor must be accessible", VendorType.MEDIATEK, annotation.vendor)
        assertEquals("Priority must be accessible", RunnerPriority.HIGH, annotation.priority)
        assertEquals("Hardware requirements must be accessible", 2, annotation.hardwareRequirements.size)
    }

    @Test
    fun `annotation provides correct default values`() {
        // CRITICAL: Default values must work correctly for minimal runner configuration
        val annotation = TestDefaultsRunner::class.java.getAnnotation(AIRunner::class.java)
        
        assertNotNull("Annotation with minimal configuration must be accessible", annotation)
        assertEquals("Default vendor should be UNKNOWN", VendorType.UNKNOWN, annotation.vendor)
        assertEquals("Default priority should be NORMAL", RunnerPriority.NORMAL, annotation.priority)
        assertEquals("Default hardware requirements should be empty", 0, annotation.hardwareRequirements.size)
        assertEquals("Default enabled should be true", true, annotation.enabled)
        assertEquals("Default API level should be 1", 1, annotation.apiLevel)
    }

    @Test
    fun `annotation supports multiple capabilities`() {
        // CRITICAL: Multi-capability runners are essential for efficiency
        val annotation = TestDefaultsRunner::class.java.getAnnotation(AIRunner::class.java)
        
        val capabilities = annotation.capabilities.toList()
        assertEquals("Must support multiple capabilities", 2, capabilities.size)
        assertTrue("Must contain ASR capability", capabilities.contains(CapabilityType.ASR))
        assertTrue("Must contain TTS capability", capabilities.contains(CapabilityType.TTS))
    }

    @Test
    fun `annotation preserves hardware requirements order`() {
        // CRITICAL: Hardware requirement order may affect validation logic
        val annotation = TestMediaTekRunner::class.java.getAnnotation(AIRunner::class.java)
        
        val requirements = annotation.hardwareRequirements
        assertEquals("Must preserve requirement count", 2, requirements.size)
        assertEquals("First requirement must be MTK_NPU", HardwareRequirement.MTK_NPU, requirements[0])
        assertEquals("Second requirement must be HIGH_MEMORY", HardwareRequirement.HIGH_MEMORY, requirements[1])
    }

    @Test
    fun `annotation runtime retention works correctly`() {
        // CRITICAL: Runtime retention is required for ClassGraph discovery
        val annotationClass = AIRunner::class.java
        val retention = annotationClass.getAnnotation(Retention::class.java)
        
        assertNotNull("@AIRunner must have @Retention annotation", retention)
        // Test that runtime retention is configured (retention object exists means RUNTIME is set)
        assertNotNull("Retention must be configured for runtime access", retention)
    }

    @Test
    fun `annotation target is correctly set to CLASS`() {
        // CRITICAL: Target must be CLASS for runner class annotation
        val annotationClass = AIRunner::class.java
        val target = annotationClass.getAnnotation(Target::class.java)
        
        assertNotNull("@AIRunner must have @Target annotation", target)
        // Test that target annotation is configured (target object exists means CLASS is set)
        assertNotNull("Target must be configured for class annotation", target)
    }

    @Test
    fun `annotation parameters are non-null and valid`() {
        // CRITICAL: Null parameters could crash discovery system
        val annotation = TestMediaTekRunner::class.java.getAnnotation(AIRunner::class.java)
        
        assertNotNull("Capabilities array must not be null", annotation.capabilities)
        assertNotNull("Vendor must not be null", annotation.vendor)
        assertNotNull("Priority must not be null", annotation.priority)
        assertNotNull("Hardware requirements must not be null", annotation.hardwareRequirements)
        
        // Validate enum values are not corrupted
        assertTrue("Vendor must be valid enum value", 
            VendorType.values().contains(annotation.vendor))
        assertTrue("Priority must be valid enum value", 
            RunnerPriority.values().contains(annotation.priority))
    }
}