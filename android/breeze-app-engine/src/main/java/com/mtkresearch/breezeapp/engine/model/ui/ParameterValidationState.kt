package com.mtkresearch.breezeapp.engine.model.ui

import com.mtkresearch.breezeapp.engine.model.CapabilityType
import com.mtkresearch.breezeapp.engine.runner.core.ParameterSchema
import com.mtkresearch.breezeapp.engine.runner.core.ValidationResult

/**
 * Manages validation state for all visible parameters.
 *
 * Stores error messages and controls Save button enablement based on validation results.
 *
 * Thread Safety: Main thread only (UI-scoped)
 * Lifecycle: Activity-scoped, cleared on runner switch or discard
 */
data class ParameterValidationState(
    private val validationErrors: MutableMap<CapabilityType, MutableMap<String, MutableMap<String, String?>>> = mutableMapOf()
    // Structure: CapabilityType -> RunnerName -> ParameterName -> ErrorMessage?
) {
    /**
     * Validate a single parameter and store result
     *
     * @param capability The capability type
     * @param runnerName The runner identifier
     * @param parameterName The parameter to validate
     * @param value The current value to validate
     * @param schema The parameter's schema defining validation rules
     * @return ValidationResult indicating success/failure and error message
     */
    fun validateParameter(
        capability: CapabilityType,
        runnerName: String,
        parameterName: String,
        value: Any?,
        schema: ParameterSchema
    ): ValidationResult {
        val result = schema.validateValue(value)
        setError(capability, runnerName, parameterName, result.errorMessage)
        return result
    }

    /**
     * Validate all parameters for a runner
     *
     * @param capability The capability type
     * @param runnerName The runner identifier
     * @param parameters Map of parameter name -> current value
     * @param schemas List of parameter schemas to validate against
     * @return true if all parameters valid, false if any invalid
     */
    fun validateRunner(
        capability: CapabilityType,
        runnerName: String,
        parameters: Map<String, Any?>,
        schemas: List<ParameterSchema>
    ): Boolean {
        var allValid = true

        schemas.forEach { schema ->
            val value = parameters[schema.name]
            val result = validateParameter(capability, runnerName, schema.name, value, schema)
            if (!result.isValid) {
                allValid = false
            }
        }

        return allValid
    }

    /**
     * Get error message for a specific parameter
     *
     * @param capability The capability type
     * @param runnerName The runner identifier
     * @param parameterName The parameter to query
     * @return Error message string if invalid, null if valid
     */
    fun getError(
        capability: CapabilityType,
        runnerName: String,
        parameterName: String
    ): String? {
        return validationErrors[capability]?.get(runnerName)?.get(parameterName)
    }

    /**
     * Check if a specific parameter is valid
     *
     * @param capability The capability type
     * @param runnerName The runner identifier
     * @param parameterName The parameter to check
     * @return true if valid (no error stored), false if invalid
     */
    fun isValid(
        capability: CapabilityType,
        runnerName: String,
        parameterName: String
    ): Boolean {
        return getError(capability, runnerName, parameterName) == null
    }

    /**
     * Check if all parameters for a runner are valid
     *
     * @param capability The capability type
     * @param runnerName The runner identifier
     * @return true if no errors for this runner, false if any errors exist
     */
    fun isRunnerValid(capability: CapabilityType, runnerName: String): Boolean {
        val runnerErrors = validationErrors[capability]?.get(runnerName) ?: return true
        return runnerErrors.values.all { it == null }
    }

    /**
     * Check if all tracked runners are valid
     *
     * @return true if no errors across all runners, false if any errors exist
     */
    fun isAllValid(): Boolean {
        return validationErrors.values.all { capabilityErrors ->
            capabilityErrors.values.all { runnerErrors ->
                runnerErrors.values.all { it == null }
            }
        }
    }

    /**
     * Clear validation error for a parameter
     *
     * @param capability The capability type
     * @param runnerName The runner identifier
     * @param parameterName The parameter to clear
     */
    fun clearError(
        capability: CapabilityType,
        runnerName: String,
        parameterName: String
    ) {
        validationErrors[capability]?.get(runnerName)?.set(parameterName, null)
    }

    /**
     * Clear all validation errors for a runner
     *
     * @param capability The capability type
     * @param runnerName The runner identifier
     */
    fun clearRunner(capability: CapabilityType, runnerName: String) {
        validationErrors[capability]?.remove(runnerName)
    }

    /**
     * Get count of invalid parameters for a runner
     *
     * @param capability The capability type
     * @param runnerName The runner identifier
     * @return Number of parameters with validation errors
     */
    fun getErrorCount(capability: CapabilityType, runnerName: String): Int {
        val runnerErrors = validationErrors[capability]?.get(runnerName) ?: return 0
        return runnerErrors.values.count { it != null }
    }

    /**
     * Internal helper to set error message
     */
    private fun setError(
        capability: CapabilityType,
        runnerName: String,
        parameterName: String,
        errorMessage: String?
    ) {
        val capabilityErrors = validationErrors.getOrPut(capability) { mutableMapOf() }
        val runnerErrors = capabilityErrors.getOrPut(runnerName) { mutableMapOf() }
        runnerErrors[parameterName] = errorMessage
    }
}
