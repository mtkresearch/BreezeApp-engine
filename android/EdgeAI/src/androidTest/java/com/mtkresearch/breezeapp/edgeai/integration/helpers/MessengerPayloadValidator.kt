package com.mtkresearch.breezeapp.edgeai.integration.helpers

import org.json.JSONObject

/**
 * MessengerPayloadValidator (EdgeAI SDK Test Version)
 *
 * Validates that the AI response matches the Messenger App's JSON schema requirements.
 * This is a copy of the Engine test helper for use in EdgeAI SDK tests.
 */
data class MessengerPayloadValidator(
    val type: String,
    val text: String? = null,
    val draftMessage: String? = null,
    val recipient: String? = null,
    val confirmationPrompt: String? = null
) {
    companion object {
        fun fromJSON(json: JSONObject): MessengerPayloadValidator {
            return MessengerPayloadValidator(
                type = json.optString("type"),
                text = json.optString("text"),
                draftMessage = json.optString("draft_message"),
                recipient = json.optString("recipient"),
                confirmationPrompt = json.optString("confirmation_prompt")
            )
        }
    }
}
