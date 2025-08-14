package com.mtkresearch.breezeapp.engine.annotation

import com.mtkresearch.breezeapp.engine.model.CapabilityType

/**
 * Annotation to mark classes as AI runners for automatic discovery and registration.
 * 
 * This annotation enables the annotation-based auto-discovery system that replaces
 * complex configuration files with simple, type-safe annotations.
 * 
 * **Usage Example:**
 * ```kotlin
 * @AIRunner(
 *     capabilities = [CapabilityType.LLM],
 *     vendor = VendorType.MEDIATEK,
 *     priority = RunnerPriority.HIGH,
 *     hardwareRequirements = [HardwareRequirement.MTK_NPU, HardwareRequirement.HIGH_MEMORY]
 * )
 * class MediaTekLLMRunner : BaseRunner {
 *     // Runner implementation
 * }
 * ```
 * 
 * **Architecture Notes:**
 * - Uses runtime retention for reflection-based discovery via ClassGraph
 * - Enables compile-time validation of runner configurations
 * - Supports multiple capabilities per runner (e.g., LLM + TTS)
 * - Hardware requirements are validated at runtime during registration
 * 
 * @since Engine API v2.0
 * @see com.mtkresearch.breezeapp.engine.runner.core.RunnerRegistry
 * @see com.mtkresearch.breezeapp.engine.runner.core.BaseRunner
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class AIRunner(
    /**
     * List of AI capabilities this runner supports.
     * 
     * A runner can support multiple capabilities (e.g., both LLM and TTS).
     * The system will register the runner for each specified capability.
     * 
     * @see CapabilityType for available capability types
     */
    val capabilities: Array<CapabilityType>,
    
    /**
     * The AI provider/technology vendor for this runner.
     * 
     * This represents the underlying AI technology (e.g., MediaTek NPU, Sherpa ONNX),
     * not who implemented the runner. Multiple developers can implement runners
     * for the same vendor using their public SDKs.
     * 
     * Default: VendorType.UNKNOWN
     * @see VendorType for available vendor types
     */
    val vendor: VendorType = VendorType.UNKNOWN,
    
    /**
     * Priority level for this runner within its vendor category.
     * 
     * Used for selection when multiple runners from the same vendor
     * support the same capability. Higher priority runners are preferred.
     * 
     * - HIGH: Premium/flagship runners (hardware-accelerated, latest models)
     * - NORMAL: Standard runners (default, balanced performance)  
     * - LOW: Lite/fallback runners (CPU-only, lightweight models)
     * 
     * Default: RunnerPriority.NORMAL
     * @see RunnerPriority for priority levels
     */
    val priority: RunnerPriority = RunnerPriority.NORMAL,
    
    /**
     * Hardware requirements this runner needs to function properly.
     * 
     * The system validates these requirements against device capabilities
     * during registration. Runners with unmet requirements are excluded
     * from selection.
     * 
     * Empty array means no special hardware requirements (CPU-only).
     * 
     * @see HardwareRequirement for available requirement types
     */
    val hardwareRequirements: Array<HardwareRequirement> = [],
    
    /**
     * Whether this runner is enabled for registration.
     * 
     * Disabled runners are discovered but not registered for use.
     * Useful for debugging or gradual rollouts.
     * 
     * Default: true
     */
    val enabled: Boolean = true,
    
    /**
     * API level this runner was designed for.
     * 
     * Used for compatibility checking and future migrations.
     * Current API level is 1.
     * 
     * Default: 1
     */
    val apiLevel: Int = 1
)