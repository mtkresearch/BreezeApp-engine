package com.mtkresearch.breezeapp.edgeai.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtkresearch.breezeapp.edgeai.ChatMessage
import com.mtkresearch.breezeapp.edgeai.EdgeAI
import com.mtkresearch.breezeapp.edgeai.chatRequestWithHistory
import com.mtkresearch.breezeapp.edgeai.integration.helpers.LongContextData
import com.mtkresearch.breezeapp.edgeai.integration.helpers.SDKTestBase
import com.mtkresearch.breezeapp.edgeai.integration.helpers.TestSystemPrompt
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Category 4: Multi-turn Context Tests (EdgeAI SDK Version)
 *
 * Purpose: Verify proper conversational context management in the SDK.
 *
 * Tests:
 * - Test 4.1: Context Retention Test
 * - Test 4.2: Context Window Test
 */
@RunWith(AndroidJUnit4::class)
class MessengerSDKContextTest : SDKTestBase() {

    /**
     * Test 4.1: Context Retention Test
     *
     * Purpose: Verify LLM remembers previous conversation.
     *
     * Scenario:
     * 1. Turn 1: "Translate: 我餓了"
     * 2. Turn 2: "How do you say that more politely?" (Refers to Turn 1)
     * 3. Turn 3: "What did I ask you to translate earlier?" (Refers to Turn 1)
     */
    @Test
    fun llm_retainsContextAcrossTurns() = runBlocking {
        // Use the proper TestSystemPrompt that matches Engine logic
        val systemPrompt = TestSystemPrompt.FULL_PROMPT
        
        logReport("========================================")
        logReport("Test 4.1: Context Retention Test")
        logReport("========================================")

        val history = mutableListOf<ChatMessage>()
        history.add(ChatMessage(role = "system", content = systemPrompt))
        
        // --- Turn 1 ---
        val userPrompt1 = "@ai translate: 我餓了"
        logReport("\n--- Turn 1 ---")
        logReport("User: $userPrompt1")
        
        history.add(ChatMessage(role = "user", content = userPrompt1))
        
        logReport("DEBUG: Sending Request 1 with history size: ${history.size}")
        // Note: model parameter is null to let Engine decide (OpenRouter)
        val request1 = chatRequestWithHistory(
            messages = history
        )
        
        val responses1 = EdgeAI.chat(request1).toList()
        assertTrue("Turn 1 should response", responses1.isNotEmpty())
        val output1 = responses1.last().choices.first().message!!.content
        logReport("AI: $output1")
        
        // Try parsing, if fails print raw output
        val json1 = try {
            JSONObject(output1)
        } catch (e: Exception) {
            logReport("Failed to parse JSON for Turn 1: $output1")
            throw e
        }
        val text1 = json1.optString("text", "")
        assertTrue("Turn 1 should contain 'hungry'",
            text1.contains("hungry", ignoreCase = true))
            
        history.add(ChatMessage(role = "assistant", content = output1))
        
        // --- Turn 2 ---
        val userPrompt2 = "@ai how do you say that more politely?"
        logReport("\n--- Turn 2 ---")
        logReport("User: $userPrompt2")
        
        history.add(ChatMessage(role = "user", content = userPrompt2))
        
        logReport("DEBUG: Sending Request 2 with history size: ${history.size}")
        val request2 = chatRequestWithHistory(
            messages = history
        )
        
        val responses2 = EdgeAI.chat(request2).toList()
        assertTrue("Turn 2 should response", responses2.isNotEmpty())
        val output2 = responses2.last().choices.first().message!!.content
        logReport("AI: $output2")
        
        val json2 = JSONObject(output2)
        val text2 = json2.optString("text", "")
        
        // Should reference the previous Chinese phrase
        val passed2 = text2.contains("有點餓") || text2.contains("polite") || text2.contains("hungry")
        assertTrue("Turn 2 should understand context. Got: $text2", passed2)
        
        history.add(ChatMessage(role = "assistant", content = output2))
        
        // --- Turn 3 ---
        // Specific recall prompt
        val userPrompt3 = "@ai what did I ask you to translate earlier?"
        logReport("\n--- Turn 3 ---")
        logReport("User: $userPrompt3")
        
        history.add(ChatMessage(role = "user", content = userPrompt3))
        
        logReport("DEBUG: Sending Request 3 with history size: ${history.size}")
        
        val request3 = chatRequestWithHistory(
            messages = history
        )
        
        val responses3 = EdgeAI.chat(request3).toList()
        assertTrue("Turn 3 should response", responses3.isNotEmpty())
        val output3 = responses3.last().choices.first().message!!.content
        logReport("AI: $output3")
        
        val json3 = JSONObject(output3)
        val text3 = json3.optString("text", "")
        
        val passed3 = text3.contains("我餓了") || text3.contains("hungry")
        assertTrue("Turn 3 should recall turn 1. Got: $text3", passed3)
        
        logReport("\n✅ Context retention passed across 3 turns")
    }

    /**
     * Test 4.2: Context Window Test
     *
     * Purpose: Verify 8K+ token context window.
     * We simulate a long conversation and verify the model can still recall early details.
     */
    @Test
    fun llm_maintains8KTokenContext() = runBlocking {
        val systemPrompt = TestSystemPrompt.FULL_PROMPT
        
        logReport("========================================")
        logReport("Test 4.2: Context Window Test (>8k tokens)")
        logReport("========================================")
        
        // Use helper to generate massive 8k+ history
        val history = LongContextData.getHistory(systemPrompt).toMutableList()
        logReport("Generated history with ${history.size} turns.")
        
        // Ask for the needle buried at turn 1
        val finalQuestion = "@ai what is the secret code I mentioned at the beginning?"
        logReport("Sending final request...")
        history.add(ChatMessage(role = "user", content = finalQuestion))
        
        val request = chatRequestWithHistory(
            messages = history
        )
        
        val responses = EdgeAI.chat(request).toList()
        assertTrue("Should get response for large context", responses.isNotEmpty())
        
        val output = responses.last().choices.first().message!!.content
        logReport("Final AI Response: $output")
        
        val json = JSONObject(output)
        val text = json.optString("text", "")
        
        // Check for "BLUE-SKY-99"
        val passed = text.contains("BLUE-SKY-99")
        
        if (passed) {
             logReport("✅ Context window test passed (Retrieved BLUE-SKY-99)")
        } else {
             logReport("⚠️ Context window test failed to retrieve exact code. Output: $text")
        }
        
        // Assertion: Valid JSON and content is reasonable
        assertEquals("Response type should be 'response'", "response", json.optString("type"))
        // We assert true only if it passes, otherwise fail to flag it
        assertTrue("Should retrieve the secret code BLUE-SKY-99", passed)
    }
}
