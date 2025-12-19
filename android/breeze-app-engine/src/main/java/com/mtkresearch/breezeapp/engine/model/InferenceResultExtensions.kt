package com.mtkresearch.breezeapp.engine.model

/**
 * Extension functions for safely extracting optional data from InferenceResult.
 * Returns null if the key doesn't exist or the type doesn't match.
 */

/**
 * Safely extract an Int value from outputs or metadata.
 */
fun InferenceResult.getIntOrNull(key: String): Int? {
    return (outputs[key] as? Number)?.toInt() 
        ?: (metadata[key] as? Number)?.toInt()
}

/**
 * Safely extract a Long value from outputs or metadata.
 */
fun InferenceResult.getLongOrNull(key: String): Long? {
    return (outputs[key] as? Number)?.toLong()
        ?: (metadata[key] as? Number)?.toLong()
}

/**
 * Safely extract a Float value from outputs or metadata.
 */
fun InferenceResult.getFloatOrNull(key: String): Float? {
    return (outputs[key] as? Number)?.toFloat()
        ?: (metadata[key] as? Number)?.toFloat()
}

/**
 * Safely extract a String value from outputs or metadata.
 */
fun InferenceResult.getStringOrNull(key: String): String? {
    return outputs[key] as? String 
        ?: metadata[key] as? String
}

/**
 * Safely extract a Boolean value from outputs or metadata.
 */
fun InferenceResult.getBooleanOrNull(key: String): Boolean? {
    return outputs[key] as? Boolean
        ?: metadata[key] as? Boolean
}

/**
 * Safely extract a List of Strings from outputs or metadata.
 */
@Suppress("UNCHECKED_CAST")
fun InferenceResult.getStringListOrNull(key: String): List<String>? {
    return outputs[key] as? List<String>
        ?: metadata[key] as? List<String>
}
