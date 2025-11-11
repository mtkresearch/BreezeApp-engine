package com.mtkresearch.breezeapp.engine.runner.core

/**
 * Parameter Schema for Self-Describing AI Runners
 * 
 * This file defines the schema system that allows runners to declare their own 
 * configuration parameters dynamically. This enables automatic UI generation
 * and parameter validation without requiring hardcoded parameter definitions
 * in the application layer.
 * 
 * ## Architecture Benefits
 * - **Zero Developer Overhead**: Runner developers just add parameter schemas to their runner class
 * - **Automatic UI Generation**: UI adapts to runner parameters without code changes
 * - **Type Safety**: Parameter validation enforced by runner schema
 * - **Clean Architecture**: UI remains decoupled from runner implementation details
 * 
 * ## Usage Example
 * ```kotlin
 * class MyRunner : BaseRunner {
 *     override fun getParameterSchema(): List<ParameterSchema> {
 *         return listOf(
 *             ParameterSchema(
 *                 name = "api_key",
 *                 displayName = "API Key",
 *                 description = "Your API key for authentication",
 *                 type = ParameterType.StringType(minLength = 10),
 *                 defaultValue = "",
 *                 isRequired = true,
 *                 isSensitive = true
 *             )
 *         )
 *     }
 * }
 * ```
 * 
 * @since Engine API v2.1
 * @author BreezeApp Engine Team
 */

/**
 * Parameter schema definition for a single runner parameter.
 * 
 * This defines everything the UI needs to know about a parameter:
 * - How to display it (name, description)
 * - How to validate it (type, constraints, required)
 * - How to handle it (sensitive, category, default)
 * 
 * @param name Unique parameter name used for storage and retrieval
 * @param displayName Human-readable name for UI display
 * @param description Help text explaining what this parameter does
 * @param type Parameter type with validation rules and constraints
 * @param defaultValue Default value for this parameter
 * @param isRequired Whether this parameter must be provided by the user
 * @param isSensitive Whether this parameter should be masked in UI (e.g., passwords, API keys)
 * @param category Optional category for grouping related parameters
 * @param metadata Additional metadata for advanced use cases
 */
data class ParameterSchema(
    val name: String,
    val displayName: String,
    val description: String,
    val type: ParameterType,
    val defaultValue: Any?,
    val isRequired: Boolean = false,
    val isSensitive: Boolean = false,
    val category: String? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * Validate a value against this parameter's schema
     */
    fun validateValue(value: Any?): ValidationResult {
        // Check required constraint
        if (isRequired && (value == null || (value is String && value.isBlank()))) {
            return ValidationResult.invalid("${displayName} is required")
        }
        
        // Skip type validation if value is null and parameter is optional
        if (value == null && !isRequired) {
            return ValidationResult.valid()
        }
        
        // Delegate to type-specific validation
        return type.validate(value)
    }
}

/**
 * Parameter type definitions with validation rules.
 * 
 * Each type defines how to validate values and provides metadata
 * for UI generation (e.g., input field type, constraints).
 */
sealed class ParameterType {
    
    /**
     * Validate a value against this parameter type
     */
    abstract fun validate(value: Any?): ValidationResult
    
    /**
     * String parameter type with optional constraints.
     * 
     * @param minLength Minimum string length (inclusive)
     * @param maxLength Maximum string length (inclusive)  
     * @param pattern Regex pattern the string must match
     * @param allowEmpty Whether empty strings are allowed
     */
    data class StringType(
        val minLength: Int? = null,
        val maxLength: Int? = null,
        val pattern: Regex? = null,
        val allowEmpty: Boolean = true
    ) : ParameterType() {
        
        override fun validate(value: Any?): ValidationResult {
            if (value !is String) {
                return ValidationResult.invalid("Expected string value")
            }
            
            if (!allowEmpty && value.isEmpty()) {
                return ValidationResult.invalid("Empty values are not allowed")
            }
            
            minLength?.let { min ->
                if (value.length < min) {
                    return ValidationResult.invalid("Minimum length is $min characters")
                }
            }
            
            maxLength?.let { max ->
                if (value.length > max) {
                    return ValidationResult.invalid("Maximum length is $max characters")
                }
            }
            
            pattern?.let { regex ->
                if (!regex.matches(value)) {
                    return ValidationResult.invalid("Invalid format")
                }
            }
            
            return ValidationResult.valid()
        }
    }
    
    /**
     * Integer parameter type with range constraints.
     * 
     * @param minValue Minimum allowed value (inclusive)
     * @param maxValue Maximum allowed value (inclusive)
     * @param step Step size for UI controls (e.g., slider increments)
     */
    data class IntType(
        val minValue: Int? = null,
        val maxValue: Int? = null,
        val step: Int? = null
    ) : ParameterType() {
        
        override fun validate(value: Any?): ValidationResult {
            val intValue = when (value) {
                is Int -> value
                is Number -> value.toInt()
                is String -> value.toIntOrNull() ?: return ValidationResult.invalid("Invalid integer format")
                else -> return ValidationResult.invalid("Expected integer value")
            }
            
            minValue?.let { min ->
                if (intValue < min) {
                    return ValidationResult.invalid("Minimum value is $min")
                }
            }
            
            maxValue?.let { max ->
                if (intValue > max) {
                    return ValidationResult.invalid("Maximum value is $max")
                }
            }
            
            return ValidationResult.valid()
        }
    }
    
    /**
     * Float/Double parameter type with range constraints.
     * 
     * @param minValue Minimum allowed value (inclusive)
     * @param maxValue Maximum allowed value (inclusive)
     * @param step Step size for UI controls (e.g., slider increments)
     * @param precision Number of decimal places for display
     */
    data class FloatType(
        val minValue: Double? = null,
        val maxValue: Double? = null,
        val step: Double? = null,
        val precision: Int = 2
    ) : ParameterType() {
        
        override fun validate(value: Any?): ValidationResult {
            val doubleValue = when (value) {
                is Double -> value
                is Float -> value.toDouble()
                is Number -> value.toDouble()
                is String -> {
                    // Stricter validation for string inputs to reject incomplete numbers during typing
                    val trimmed = value.trim()

                    // Reject incomplete number formats that Android's toDoubleOrNull accepts but shouldn't:
                    // - Lone minus sign: "-"
                    // - Minus with trailing dot: "-.", "0.", etc
                    // - Just a dot: "."
                    if (trimmed.isEmpty() ||
                        trimmed == "-" ||
                        trimmed == "." ||
                        trimmed.endsWith(".") && !trimmed.contains(Regex("\\d+\\.\\d+")) ||
                        trimmed.startsWith(".") ||
                        trimmed == "-." ||
                        trimmed.matches(Regex("-?0*\\.?$"))) {  // "-0", "-0.", "0.", etc without digits after decimal
                        return ValidationResult.invalid("Incomplete number")
                    }

                    value.toDoubleOrNull() ?: return ValidationResult.invalid("Invalid number format")
                }
                else -> return ValidationResult.invalid("Expected numeric value")
            }

            minValue?.let { min ->
                if (doubleValue < min) {
                    return ValidationResult.invalid("Minimum value is $min")
                }
            }

            maxValue?.let { max ->
                if (doubleValue > max) {
                    return ValidationResult.invalid("Maximum value is $max")
                }
            }

            return ValidationResult.valid()
        }
    }
    
    /**
     * Boolean parameter type.
     * 
     * Accepts boolean values, or string representations like "true"/"false".
     */
    object BooleanType : ParameterType() {
        override fun validate(value: Any?): ValidationResult {
            return when (value) {
                is Boolean -> ValidationResult.valid()
                is String -> {
                    if (value.lowercase() in listOf("true", "false", "1", "0", "yes", "no")) {
                        ValidationResult.valid()
                    } else {
                        ValidationResult.invalid("Expected boolean value (true/false)")
                    }
                }
                else -> ValidationResult.invalid("Expected boolean value")
            }
        }
    }
    
    /**
     * Selection parameter type for choosing from predefined options.
     * 
     * @param options List of available selection options
     * @param allowMultiple Whether multiple selections are allowed
     */
    data class SelectionType(
        val options: List<SelectionOption>,
        val allowMultiple: Boolean = false
    ) : ParameterType() {
        
        override fun validate(value: Any?): ValidationResult {
            if (allowMultiple) {
                // Handle multiple selections (List or comma-separated string)
                val selections = when (value) {
                    is List<*> -> value.map { it.toString() }
                    is String -> value.split(",").map { it.trim() }
                    else -> return ValidationResult.invalid("Expected list of selections")
                }
                
                val validKeys = options.map { it.key }.toSet()
                val invalidSelections = selections.filter { it !in validKeys }
                
                return if (invalidSelections.isEmpty()) {
                    ValidationResult.valid()
                } else {
                    ValidationResult.invalid("Invalid selections: ${invalidSelections.joinToString()}")
                }
            } else {
                // Handle single selection
                val selectedKey = value?.toString() ?: return ValidationResult.invalid("Selection required")
                val validKeys = options.map { it.key }
                
                return if (selectedKey in validKeys) {
                    ValidationResult.valid()
                } else {
                    ValidationResult.invalid("Invalid selection: $selectedKey")
                }
            }
        }
    }
    
    /**
     * File path parameter type with optional file type constraints.
     * 
     * @param allowedExtensions List of allowed file extensions (without dots)
     * @param mustExist Whether the file must exist on the filesystem
     * @param isDirectory Whether this should be a directory path instead of file
     */
    data class FilePathType(
        val allowedExtensions: List<String> = emptyList(),
        val mustExist: Boolean = false,
        val isDirectory: Boolean = false
    ) : ParameterType() {
        
        override fun validate(value: Any?): ValidationResult {
            if (value !is String) {
                return ValidationResult.invalid("Expected file path string")
            }
            
            if (value.isEmpty()) {
                return ValidationResult.invalid("File path cannot be empty")
            }
            
            // Check file extension if specified
            if (allowedExtensions.isNotEmpty() && !isDirectory) {
                val extension = value.substringAfterLast(".", "").lowercase()
                if (extension !in allowedExtensions.map { it.lowercase() }) {
                    return ValidationResult.invalid("Allowed extensions: ${allowedExtensions.joinToString(", ")}")
                }
            }
            
            // File existence check would require filesystem access
            // This could be implemented in specific runners if needed
            
            return ValidationResult.valid()
        }
    }
}

/**
 * Selection option for SelectionType parameters.
 * 
 * @param key Internal key used for storage
 * @param displayName Human-readable name shown in UI
 * @param description Optional description for this option
 */
data class SelectionOption(
    val key: String,
    val displayName: String,
    val description: String? = null
)

/**
 * Result of parameter validation.
 * 
 * @param isValid Whether the validation passed
 * @param errorMessage Error message if validation failed
 */
data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
) {
    companion object {
        fun valid() = ValidationResult(true)
        fun invalid(message: String) = ValidationResult(false, message)
    }
    
    /**
     * Check if validation failed
     */
    fun isError(): Boolean = !isValid
}