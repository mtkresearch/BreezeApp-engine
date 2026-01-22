package com.mtkresearch.breezeapp.edgeai.integration.helpers

import com.mtkresearch.breezeapp.edgeai.ChatMessage

object LongContextData {
    private const val FILLER_TEXT = "The quick brown fox jumps over the lazy dog. " +
            "Engineers build bridges to connect people. " +
            "AI assistants help with productivity. " +
            "Android development requires knowing the lifecycle. " +
            "Kotlin is a concise and safe language. "   // ~35 words

    fun getHistory(systemPrompt: String): List<ChatMessage> {
        val history = mutableListOf<ChatMessage>()
        history.add(ChatMessage(role = "system", content = systemPrompt))
        
        // --- Turn 1 ---
        // The "Needle" we want to retrieve later
        history.add(ChatMessage(role = "user", content = "Here is a secret code: BLUE-SKY-99. Please remember it."))
        history.add(ChatMessage(role = "assistant", content = "{\"type\": \"response\", \"text\": \"Okay, I will remember the code BLUE-SKY-99.\"}"))
        
        // --- Filler Turns (Need > 8k tokens) ---
        // 1 token approx 4 chars or 0.75 words. 
        // 8000 tokens ~ 32,000 chars.
        // Our filler block is ~200 chars. So we need ~160 turns if each turn has one block.
        // To be safe and really push 8k, let's do 200 turns of substantial length.
        
        repeat(200) { i ->
            val userMsg = "Turn $i: Tell me a fact about number $i. $FILLER_TEXT"
            val aiMsg = "{\"type\": \"response\", \"text\": \"Fact $i: This is a filler response to occupy context window space. $FILLER_TEXT\"}"
            
            history.add(ChatMessage(role = "user", content = userMsg))
            history.add(ChatMessage(role = "assistant", content = aiMsg))
        }
        
        return history
    }
}
