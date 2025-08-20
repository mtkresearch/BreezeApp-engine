package com.mtkresearch.breezeapp.engine.runner.guardian

import com.mtkresearch.breezeapp.engine.model.*
import com.mtkresearch.breezeapp.engine.runner.core.BaseRunner

/**
 * Represents the result of a guardian analysis.
 *
 * @property status The overall safety status.
 * @property riskScore A normalized score from 0.0 (safe) to 1.0 (high risk).
 * @property categories A list of detected risk categories.
 * @property action The recommended action to take.
 * @property filteredText The original text, possibly with sensitive content redacted.
 * @property details A map for any additional, runner-specific metadata.
 */
data class GuardianAnalysisResult(
    val status: GuardianStatus,
    val riskScore: Double,
    val categories: List<GuardianCategory>,
    val action: GuardianAction,
    val filteredText: String?,
    val details: Map<String, Any> = emptyMap()
)

/**
 * Overall safety status of the content.
 */
enum class GuardianStatus {
    SAFE,
    WARNING,
    BLOCKED
}

/**
 * Categories of potential risks in the content.
 */
enum class GuardianCategory {
    HATE_SPEECH,
    SEXUAL_CONTENT,
    VIOLENCE,
    SELF_HARM,
    SPAM,
    PII, // Personally Identifiable Information
    TOXICITY,
    UNSAFE_CONTENT,
    UNKNOWN
}

/**
 * Recommended action based on the analysis.
 */
enum class GuardianAction {
    NONE,
    REVIEW,
    BLOCK
}

/**
 * Configuration for a guardian analysis request.
 *
 * @property strictness The desired level of strictness for the analysis (e.g., "low", "medium", "high").
 */
data class GuardianConfig(
    val strictness: String
)

/**
 * Abstract base class for all Guardian runners.
 *
 * This class provides a common structure for implementing content safety analysis runners.
 * It handles the generic `run` logic, input/output parsing, and error handling,
 * allowing subclasses to focus solely on implementing their specific `analyze` logic.
 */
abstract class BaseGuardianRunner : BaseRunner {

    /**
     * Performs the core content safety analysis. Subclasses must implement this method.
     *
     * @param text The input text to analyze.
     * @param config The configuration for the analysis, such as strictness level.
     * @return A [GuardianAnalysisResult] containing the outcome of the analysis.
     */
    abstract fun analyze(text: String, config: GuardianConfig): GuardianAnalysisResult

    /**
     * Executes the runner. This method is implemented by the base class and should not be overridden.
     * It parses the input, calls the abstract `analyze` method, and formats the output.
     */
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        if (!isLoaded()) {
            return InferenceResult.error(RunnerError.modelNotLoaded())
        }

        val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String
        if (text.isNullOrBlank()) {
            return InferenceResult.error(RunnerError.invalidInput("Input text is missing for guardian analysis."))
        }

        // Extract configuration from the request parameters
        val config = GuardianConfig(
            strictness = (input.params["strictness"] as? String) ?: "medium"
        )

        return try {
            val startTime = System.currentTimeMillis()
            val analysisResult = analyze(text, config)
            val processingTime = System.currentTimeMillis() - startTime

            // Convert the specific analysis result to the generic InferenceResult
            InferenceResult.success(
                outputs = mapOf(
                    "safety_status" to analysisResult.status.name,
                    "risk_score" to analysisResult.riskScore,
                    "risk_categories" to analysisResult.categories.map { it.name },
                    "action_required" to analysisResult.action.name,
                    "filtered_text" to (analysisResult.filteredText ?: text)
                ),
                metadata = mapOf(
                    InferenceResult.META_PROCESSING_TIME_MS to processingTime,
                    InferenceResult.META_MODEL_NAME to (getRunnerInfo().name),
                    "strictness_level" to config.strictness
                ) + analysisResult.details
            )
        } catch (e: Exception) {
            InferenceResult.error(RunnerError.runtimeError("Guardian analysis failed: ${e.message}", e))
        }
    }

    /**
     * Returns the capabilities of this runner. All guardian runners have the GUARDIAN capability.
     */
    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.GUARDIAN)
}