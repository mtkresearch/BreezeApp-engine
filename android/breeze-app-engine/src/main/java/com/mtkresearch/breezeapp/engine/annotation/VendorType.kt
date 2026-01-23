package com.mtkresearch.breezeapp.engine.annotation

/**
 * Represents the AI provider/technology vendor for runners.
 * 
 * This enum identifies the underlying AI technology or service provider,
 * not who implemented the runner. Multiple developers can create runners
 * for the same vendor using their published SDKs and APIs.
 * 
 * **Design Principles:**
 * - Represents AI technology providers, not implementers
 * - Enables vendor-specific optimizations and features
 * - Supports priority ordering based on capability-vendor combinations
 * - Extensible for future AI providers
 * 
 * **Priority Ordering:**
 * Vendors are prioritized differently per capability type:
 * - **LLM**: MEDIATEK → EXECUTORCH → OPENROUTER → UNKNOWN
 * - **ASR**: SHERPA → OPENAI → UNKNOWN  
 * - **TTS**: SHERPA → OPENAI → UNKNOWN
 * 
 * @since Engine API v2.0
 * @see AIRunner.vendor
 */
enum class VendorType(
    /**
     * Human-readable name for this vendor.
     */
    val displayName: String,
    
    /**
     * Brief description of the vendor's technology.
     */
    val description: String,
    
    /**
     * Whether this vendor typically requires special hardware.
     */
    val requiresSpecialHardware: Boolean = false,
    
    /**
     * Whether this vendor requires internet connectivity.
     */
    val requiresInternet: Boolean = false
) {
    CUSTOM(
        displayName = "Custom",
        description = "Self hosted server",
        requiresSpecialHardware = false,
        requiresInternet = true
    ),

    /**
     * MediaTek NPU and AI acceleration technology.
     * 
     * Provides hardware-accelerated inference using MediaTek's Neural Processing Unit.
     * Typically offers the best performance on supported devices.
     * 
     * **Characteristics:**
     * - Local processing (privacy-first)
     * - Hardware acceleration via NPU
     * - High performance, low latency
     * - Device-specific optimization
     */
    MEDIATEK(
        displayName = "MediaTek",
        description = "MediaTek NPU and AI acceleration technology",
        requiresSpecialHardware = true,
        requiresInternet = false
    ),
    
    /**
     * Sherpa ONNX framework for local AI processing.
     * 
     * Open-source framework providing local inference without cloud dependencies.
     * Excellent for privacy-sensitive applications and offline usage.
     * 
     * **Characteristics:**
     * - Local processing (privacy-first)
     * - CPU-based inference
     * - Offline capability
     * - Cross-platform compatibility
     */
    SHERPA(
        displayName = "Sherpa ONNX",
        description = "Local ONNX-based AI processing framework",
        requiresSpecialHardware = false,
        requiresInternet = false
    ),
    
    /**
     * OpenRouter API service for cloud-based AI.
     * 
     * Provides access to multiple AI models through a unified API.
     * Offers good reliability and model variety as a cloud fallback.
     * 
     * **Characteristics:**
     * - Cloud-based processing
     * - Multiple model access
     * - API-based integration
     * - Reliable fallback option
     */
    OPENROUTER(
        displayName = "OpenRouter",
        description = "Cloud-based AI API service",
        requiresSpecialHardware = false,
        requiresInternet = true
    ),
    
    /**
     * Meta ExecuTorch framework for mobile AI.
     * 
     * Meta's framework for efficient on-device AI inference.
     * Optimized for mobile deployment and resource efficiency.
     * 
     * **Characteristics:**
     * - Local processing
     * - Mobile-optimized
     * - Efficient resource usage
     * - Cross-platform support
     */
    EXECUTORCH(
        displayName = "Meta ExecuTorch",
        description = "Meta's mobile AI inference framework",
        requiresSpecialHardware = false,
        requiresInternet = false
    ),


    HF(
        displayName = "HuggingFace",
        description = "HuggingFace AI Inference Framework",
        requiresSpecialHardware = false,
        requiresInternet = true
    ),



    ELEVENLABS(
        displayName = "ElevenLabs",
        description = "ElevenLabs Inference Framework",
        requiresSpecialHardware = false,
        requiresInternet = true
    ),
    
    /**
     * Unknown or unspecified vendor.
     * 
     * Used for fallback cases or when vendor identification fails.
     * Always has the lowest priority in selection algorithms.
     * 
     * **Characteristics:**
     * - Fallback/unknown provider
     * - Lowest selection priority
     * - Basic compatibility assumptions
     */
    UNKNOWN(
        displayName = "Unknown",
        description = "Unknown or unspecified AI provider",
        requiresSpecialHardware = false,
        requiresInternet = false
    );
    
    /**
     * Returns true if this vendor typically provides local (on-device) processing.
     * 
     * Local vendors don't require internet connectivity for inference and
     * provide better privacy guarantees.
     */
    val isLocal: Boolean
        get() = !requiresInternet
    
    /**
     * Returns true if this vendor provides cloud-based processing.
     * 
     * Cloud vendors require internet connectivity but may offer
     * access to larger models or specialized capabilities.
     */
    val isCloud: Boolean
        get() = requiresInternet
    
    companion object {
        /**
         * Returns all vendor types that support local processing.
         */
        fun getLocalVendors(): List<VendorType> = values().filter { it.isLocal }
        
        /**
         * Returns all vendor types that support cloud processing.
         */
        fun getCloudVendors(): List<VendorType> = values().filter { it.isCloud }
        
        /**
         * Returns all vendor types that require special hardware.
         */
        fun getHardwareVendors(): List<VendorType> = values().filter { it.requiresSpecialHardware }
    }
}