package com.mtkresearch.breezeapp.engine.annotation

import org.junit.Test
import org.junit.Assert.*

/**
 * Critical unit tests for VendorType enum functionality.
 * 
 * Tests the most important aspects for runner selection:
 * - Vendor characteristics (local vs cloud, hardware requirements)
 * - Utility methods for filtering vendors
 * - Consistency of vendor properties
 */
class VendorTypeTest {

    @Test
    fun `local vendors do not require internet`() {
        // CRITICAL: Local vendors must work offline for privacy and reliability
        val localVendors = VendorType.getLocalVendors()
        
        assertTrue("Must have at least one local vendor", localVendors.isNotEmpty())
        localVendors.forEach { vendor ->
            assertFalse("Local vendor $vendor must not require internet", vendor.requiresInternet)
            assertTrue("Local vendor $vendor must be identified as local", vendor.isLocal)
            assertFalse("Local vendor $vendor must not be identified as cloud", vendor.isCloud)
        }
    }

    @Test
    fun `cloud vendors require internet`() {
        // CRITICAL: Cloud vendors must be correctly identified for connectivity checks
        val cloudVendors = VendorType.getCloudVendors()
        
        cloudVendors.forEach { vendor ->
            assertTrue("Cloud vendor $vendor must require internet", vendor.requiresInternet)
            assertTrue("Cloud vendor $vendor must be identified as cloud", vendor.isCloud)
            assertFalse("Cloud vendor $vendor must not be identified as local", vendor.isLocal)
        }
    }

    @Test
    fun `mediatek vendor has correct characteristics`() {
        // CRITICAL: MediaTek is our primary vendor and must be configured correctly
        val mediatek = VendorType.MEDIATEK
        
        assertEquals("MediaTek display name must be correct", "MediaTek", mediatek.displayName)
        assertTrue("MediaTek must require special hardware (NPU)", mediatek.requiresSpecialHardware)
        assertFalse("MediaTek must not require internet", mediatek.requiresInternet)
        assertTrue("MediaTek must be local", mediatek.isLocal)
        assertFalse("MediaTek must not be cloud", mediatek.isCloud)
    }

    @Test
    fun `openrouter vendor has correct characteristics`() {
        // CRITICAL: OpenRouter is our primary cloud fallback
        val openrouter = VendorType.OPENROUTER
        
        assertEquals("OpenRouter display name must be correct", "OpenRouter", openrouter.displayName)
        assertFalse("OpenRouter must not require special hardware", openrouter.requiresSpecialHardware)
        assertTrue("OpenRouter must require internet", openrouter.requiresInternet)
        assertFalse("OpenRouter must not be local", openrouter.isLocal)
        assertTrue("OpenRouter must be cloud", openrouter.isCloud)
    }

    @Test
    fun `sherpa vendor has correct characteristics`() {
        // CRITICAL: Sherpa is our privacy-first local processing vendor
        val sherpa = VendorType.SHERPA
        
        assertEquals("Sherpa display name must be correct", "Sherpa ONNX", sherpa.displayName)
        assertFalse("Sherpa must not require special hardware", sherpa.requiresSpecialHardware)
        assertFalse("Sherpa must not require internet", sherpa.requiresInternet)
        assertTrue("Sherpa must be local", sherpa.isLocal)
        assertFalse("Sherpa must not be cloud", sherpa.isCloud)
    }

    @Test
    fun `unknown vendor has safe defaults`() {
        // CRITICAL: Unknown vendor must have conservative, safe characteristics
        val unknown = VendorType.UNKNOWN
        
        assertEquals("Unknown display name must be correct", "Unknown", unknown.displayName)
        assertFalse("Unknown must not require special hardware", unknown.requiresSpecialHardware)
        assertFalse("Unknown must not require internet", unknown.requiresInternet)
        assertTrue("Unknown must be local (safe default)", unknown.isLocal)
        assertFalse("Unknown must not be cloud", unknown.isCloud)
    }

    @Test
    fun `hardware vendors are correctly identified`() {
        // CRITICAL: Hardware detection affects runner selection
        val hardwareVendors = VendorType.getHardwareVendors()
        
        assertTrue("MediaTek must be identified as hardware vendor", 
            hardwareVendors.contains(VendorType.MEDIATEK))
        assertFalse("Sherpa must not be identified as hardware vendor", 
            hardwareVendors.contains(VendorType.SHERPA))
        assertFalse("OpenRouter must not be identified as hardware vendor", 
            hardwareVendors.contains(VendorType.OPENROUTER))
    }

    @Test
    fun `all vendors have non-empty descriptions`() {
        // CRITICAL: Descriptions are used for UI and debugging
        VendorType.values().forEach { vendor ->
            assertNotNull("Vendor $vendor must have description", vendor.description)
            assertTrue("Vendor $vendor description must not be empty", 
                vendor.description.isNotBlank())
            assertNotNull("Vendor $vendor must have display name", vendor.displayName)
            assertTrue("Vendor $vendor display name must not be empty", 
                vendor.displayName.isNotBlank())
        }
    }

    @Test
    fun `vendor categorization is mutually exclusive`() {
        // CRITICAL: No vendor should be both local and cloud
        VendorType.values().forEach { vendor ->
            val isLocal = vendor.isLocal
            val isCloud = vendor.isCloud
            
            assertFalse("Vendor $vendor cannot be both local and cloud", 
                isLocal && isCloud)
            assertTrue("Vendor $vendor must be either local or cloud", 
                isLocal || isCloud)
        }
    }

    @Test
    fun `vendor utility methods return consistent results`() {
        // CRITICAL: Utility methods must be consistent with individual vendor properties
        val allVendors = VendorType.values().toSet()
        val localVendors = VendorType.getLocalVendors().toSet()
        val cloudVendors = VendorType.getCloudVendors().toSet()
        val hardwareVendors = VendorType.getHardwareVendors().toSet()
        
        // Local + Cloud should equal all vendors
        assertEquals("Local + Cloud vendors must equal all vendors", 
            allVendors, localVendors + cloudVendors)
        
        // Hardware vendors should be subset of all vendors
        assertTrue("Hardware vendors must be subset of all vendors", 
            allVendors.containsAll(hardwareVendors))
        
        // Verify consistency with individual properties
        localVendors.forEach { vendor ->
            assertTrue("Local vendor $vendor must have isLocal=true", vendor.isLocal)
        }
        cloudVendors.forEach { vendor ->
            assertTrue("Cloud vendor $vendor must have isCloud=true", vendor.isCloud)
        }
        hardwareVendors.forEach { vendor ->
            assertTrue("Hardware vendor $vendor must have requiresSpecialHardware=true", 
                vendor.requiresSpecialHardware)
        }
    }
}