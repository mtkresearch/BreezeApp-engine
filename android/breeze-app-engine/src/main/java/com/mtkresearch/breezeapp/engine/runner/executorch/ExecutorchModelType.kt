package com.mtkresearch.breezeapp.engine.runner.executorch

/**
 * Represents the supported Executorch model variants.
 */
enum class ExecutorchModelType {
    BREEZE_2,
    LLAMA_3_2;

    companion object {
        /**
         * Converts a model ID string to an [ExecutorchModelType].
         *
         * @param modelId The model identifier, e.g., "Breeze2" or "Llama3_2".
         * @return The corresponding [ExecutorchModelType]. Defaults to [LLAMA_3_2] if unknown.
         */
        fun fromString(modelId: String?): ExecutorchModelType {
            return when {
                modelId.isNullOrEmpty() -> LLAMA_3_2
                modelId.contains("Breeze2", ignoreCase = true) -> BREEZE_2
                modelId.contains("Llama3_2", ignoreCase = true) -> LLAMA_3_2
                else -> {
                    // Default to Llama3_2 for any unknown modelId
                    LLAMA_3_2
                }
            }
        }
    }
}
