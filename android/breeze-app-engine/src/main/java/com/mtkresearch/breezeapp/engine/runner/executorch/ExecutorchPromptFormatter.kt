package com.mtkresearch.breezeapp.engine.runner.executorch

/**
 * A utility object for formatting prompts for different Executorch models.
 * This is adapted from the official Executorch demo application.
 */
object ExecutorchPromptFormatter {

    private const val SYSTEM_PLACEHOLDER = "{{ system_prompt }}"
    private const val USER_PLACEHOLDER = "{{ user_prompt }}"
    private const val DEFAULT_SYSTEM_PROMPT = "You are a helpful AI assistant."

    private fun getSystemPromptTemplate(modelType: ExecutorchModelType): String {
        return when (modelType) {
            ExecutorchModelType.BREEZE_2, 
            ExecutorchModelType.LLAMA_3_2 -> 
                "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n" +
                "$SYSTEM_PLACEHOLDER<|eot_id|>"
        }
    }

    private fun getUserPromptTemplate(modelType: ExecutorchModelType): String {
        return when (modelType) {
            ExecutorchModelType.LLAMA_3_2 -> 
                "<|start_header_id|>user<|end_header_id|>\n" +
                "$USER_PLACEHOLDER<|eot_id|>" +
                "<|start_header_id|>assistant<|end_header_id|>"
            ExecutorchModelType.BREEZE_2 -> 
                "<|start_header_id|>user<|end_header_id|>\n" +
                "$USER_PLACEHOLDER<|eot_id|>" +
                "<|start_header_id|>assistant<|end_header_id|>\n\n"
        }
    }

    /**
     * Gets the stop token for the specified model type.
     */
    fun getStopToken(modelType: ExecutorchModelType): String {
        return when (modelType) {
            ExecutorchModelType.BREEZE_2, 
            ExecutorchModelType.LLAMA_3_2 -> "<|eot_id|>"
        }
    }

    /**
     * Formats the final prompt to be sent to the model.
     */
    fun formatPrompt(
        modelType: ExecutorchModelType,
        userPrompt: String,
        systemPrompt: String? = DEFAULT_SYSTEM_PROMPT
    ): String {
        val finalSystemPrompt = systemPrompt ?: DEFAULT_SYSTEM_PROMPT
        val systemPart = getSystemPromptTemplate(modelType)
            .replace(SYSTEM_PLACEHOLDER, finalSystemPrompt)
        val userPart = getUserPromptTemplate(modelType)
            .replace(USER_PLACEHOLDER, userPrompt)
        return systemPart + userPart
    }
}
