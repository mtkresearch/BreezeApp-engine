package com.mtkresearch.breezeapp.engine.runner.guardian

/**
 * Minimal Guardian data models for backward compatibility.
 * These are maintained for legacy streaming functionality but are not used in INPUT_ONLY mode.
 */

/**
 * ContentBatch - Minimal definition for compatibility
 */
data class ContentBatch(
    val startIndex: Int,
    val endIndex: Int,
    val content: String
)

/**
 * GuardianMaskingResult - Minimal definition for compatibility
 */
data class GuardianMaskingResult(
    val startIndex: Int,
    val endIndex: Int,
    val originalText: String,
    val maskedText: String,
    val violationCategories: List<GuardianCategory>,
    val riskScore: Double
)