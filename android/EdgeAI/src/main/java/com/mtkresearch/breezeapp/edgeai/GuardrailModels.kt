package com.mtkresearch.breezeapp.edgeai

import android.os.Parcel
import android.os.Parcelable

/**
 * Guardrail Models for EdgeAI SDK
 * 
 * Data models for content safety and guardrail analysis requests and responses.
 * These models provide a standardized interface for content moderation capabilities.
 */

/**
 * Request model for content guardrail/safety analysis
 * 
 * @param text The text content to analyze for safety
 * @param model The guardrail model to use (optional, defaults to system preference)
 * @param strictnessLevel The strictness level for analysis ("low", "medium", "high")
 * @param categories Specific categories to check (e.g., ["toxicity", "spam", "violence"])
 */
data class GuardrailRequest(
    val text: String,
    val model: String? = null,
    val strictnessLevel: String = "medium",
    val categories: List<String>? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        text = parcel.readString() ?: "",
        model = parcel.readString(),
        strictnessLevel = parcel.readString() ?: "medium",
        categories = parcel.createStringArrayList()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(text)
        parcel.writeString(model)
        parcel.writeString(strictnessLevel)
        parcel.writeStringList(categories)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<GuardrailRequest> {
        override fun createFromParcel(parcel: Parcel): GuardrailRequest {
            return GuardrailRequest(parcel)
        }

        override fun newArray(size: Int): Array<GuardrailRequest?> {
            return arrayOfNulls(size)
        }
    }
}

/**
 * Response model for content guardrail/safety analysis
 * 
 * @param safetyStatus Overall safety status ("safe", "warning", "blocked")
 * @param riskScore Risk score from 0.0 (safe) to 1.0 (high risk)
 * @param riskCategories List of detected risk categories
 * @param actionRequired Required action ("none", "review", "block")
 * @param filteredText Text with sensitive content filtered (if applicable)
 * @param detectedIssues List of specific issues detected
 * @param confidence Confidence score of the analysis (0.0 to 1.0)
 * @param processingTimeMs Time taken for analysis in milliseconds
 */
data class GuardrailResponse(
    val safetyStatus: String,
    val riskScore: Double,
    val riskCategories: List<String>,
    val actionRequired: String,
    val filteredText: String? = null,
    val detectedIssues: List<String> = emptyList(),
    val confidence: Double = 0.0,
    val processingTimeMs: Long = 0
) : Parcelable {
    constructor(parcel: Parcel) : this(
        safetyStatus = parcel.readString() ?: "unknown",
        riskScore = parcel.readDouble(),
        riskCategories = parcel.createStringArrayList() ?: emptyList(),
        actionRequired = parcel.readString() ?: "none",
        filteredText = parcel.readString(),
        detectedIssues = parcel.createStringArrayList() ?: emptyList(),
        confidence = parcel.readDouble(),
        processingTimeMs = parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(safetyStatus)
        parcel.writeDouble(riskScore)
        parcel.writeStringList(riskCategories)
        parcel.writeString(actionRequired)
        parcel.writeString(filteredText)
        parcel.writeStringList(detectedIssues)
        parcel.writeDouble(confidence)
        parcel.writeLong(processingTimeMs)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<GuardrailResponse> {
        override fun createFromParcel(parcel: Parcel): GuardrailResponse {
            return GuardrailResponse(parcel)
        }

        override fun newArray(size: Int): Array<GuardrailResponse?> {
            return arrayOfNulls(size)
        }
    }
}

/**
 * Builder function for creating GuardrailRequest instances
 */
fun guardrailRequest(
    text: String,
    model: String? = null,
    strictnessLevel: String = "medium",
    categories: List<String>? = null
): GuardrailRequest {
    return GuardrailRequest(
        text = text,
        model = model,
        strictnessLevel = strictnessLevel,
        categories = categories
    )
}