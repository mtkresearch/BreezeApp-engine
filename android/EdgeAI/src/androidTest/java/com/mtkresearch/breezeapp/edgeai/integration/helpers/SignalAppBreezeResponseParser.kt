package com.mtkresearch.breezeapp.edgeai.integration.helpers

import org.json.JSONObject

/**
 * Mocks the response parsing logic from the Signal App.
 * Used in Segment 1 API Contract tests to verify compatibility.
 */
object SignalAppBreezeResponseParser {
    
    data class ParsedResponse(
        val type: String,
        val text: String,
        val draftMessage: String? = null,
        val recipient: String? = null
    )

    fun parse(jsonString: String): ParsedResponse? {
        return try {
            val json = JSONObject(jsonString)
            val type = json.optString("type")
            val text = json.optString("text")
            
            // Extract draft fields if present
            val draftMessage = if (json.has("draft_message")) json.getString("draft_message") else null
            val recipient = if (json.has("recipient")) json.getString("recipient") else null
            
            ParsedResponse(
                type = type,
                text = text,
                draftMessage = draftMessage,
                recipient = recipient
            )
        } catch (e: Exception) {
            null
        }
    }
}
