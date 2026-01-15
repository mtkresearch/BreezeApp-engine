package com.mtkresearch.breezeapp.engine.helpers

import org.json.JSONObject

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
