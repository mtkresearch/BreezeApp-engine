package com.mtkresearch.breezeapp.edgeai.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtkresearch.breezeapp.edgeai.ChatMessage
import com.mtkresearch.breezeapp.edgeai.EdgeAI
import com.mtkresearch.breezeapp.edgeai.chatRequestWithHistory
import com.mtkresearch.breezeapp.edgeai.integration.helpers.LongContextData
import com.mtkresearch.breezeapp.edgeai.integration.helpers.SDKTestBase
import com.mtkresearch.breezeapp.edgeai.integration.helpers.TestSystemPrompt
import com.mtkresearch.breezeapp.edgeai.integration.helpers.TestDataLoader
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

    // Load test data lazily
    private val testData by lazy { TestDataLoader.loadCategory4Data() }

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
        logReport("========================================")
        logReport("Test 4.1: Context Retention Test")
        logReport("========================================")
        
        val systemPrompt = TestSystemPrompt.FULL_PROMPT
        val scenarios = testData.retentionTests
        
        scenarios.forEach { scenario ->
            logReport("\n--- Scenario ID: ${scenario.id} ---")
            
            val history = mutableListOf<ChatMessage>()
            history.add(ChatMessage(role = "system", content = systemPrompt))
            
            scenario.turns.forEachIndexed { index, turn ->
                logReport("\nTurn ${index + 1}: User Input: '${turn.input}'")
                
                history.add(ChatMessage(role = "user", content = turn.input))
                
                val request = chatRequestWithHistory(messages = history)
                val responses = EdgeAI.chat(request).toList()
                assertTrue("Should get response", responses.isNotEmpty())
                
                val output = responses.last().choices.first().message!!.content
                logReport("AI Response: $output")
                
                // Add assistant response to history for next turn
                history.add(ChatMessage(role = "assistant", content = output))
                
                // Validate expectation
                val json = try { JSONObject(output) } catch(e: Exception) { null }
                val text = json?.optString("text", "") ?: ""
                val draftMsg = json?.optString("draft_message", "") ?: ""
                val contentToCheck = if (text.isNotEmpty()) text else draftMsg
                
                if (turn.expectedContainsAny.isNotEmpty()) {
                    val matched = turn.expectedContainsAny.any { phrase ->
                        contentToCheck.contains(phrase, ignoreCase = true)
                    }
                    if (matched) {
                        logReport("✅ PASS: Found expected phrase from ${turn.expectedContainsAny}")
                    } else {
                        val msg = "❌ FAIL: Output did not contain any of ${turn.expectedContainsAny}. Got: $contentToCheck"
                        logReport(msg)
                        fail(msg)
                    }
                }
            }
        }
    }

    /**
     * Test 4.2: Context Window Test
     */
    @Test
    fun llm_maintains8KTokenContext() = runBlocking {
        val testConfig = testData.longContextTest
        val systemPrompt = TestSystemPrompt.FULL_PROMPT
        
        logReport("========================================")
        logReport("Test 4.2: Context Window Test")
        logReport("Target Token Count: ${testConfig.targetTokenCount}")
        logReport("========================================")
        
        // Use helper to generate massive history based on target token count
        // Note: LongContextData.getHistory is existing code, assuming it generates roughly 8k tokens.
        // If we want precise control we would pass targetTokenCount to it, but for now we follow existing logic 
        // while asserting the INTENT from JSON.
        val history = LongContextData.getHistory(systemPrompt).toMutableList()
        logReport("Generated history with ${history.size} turns.")
        
        // Ask final question from JSON
        val finalQuestion = testConfig.finalQuestion
        logReport("Sending final request: $finalQuestion")
        history.add(ChatMessage(role = "user", content = finalQuestion))
        
        val request = chatRequestWithHistory(messages = history)
        val responses = EdgeAI.chat(request).toList()
        assertTrue("Should get response for large context", responses.isNotEmpty())
        
        val output = responses.last().choices.first().message!!.content
        logReport("Final AI Response: $output")
        
        val json = JSONObject(output)
        val text = json.optString("text", "")
        
        val expected = testConfig.expectedAnswerContains
        val passed = text.contains(expected, ignoreCase = true)
        
        if (passed) {
             logReport("✅ PASS: Retrieved secret '$expected'")
        } else {
             logReport("⚠️ FAIL: Did not retrieve secret '$expected'. Output: $text")
        }
        
        // Assertion: Valid JSON and content is reasonable
        assertEquals("Response type should be 'response'", "response", json.optString("type"))
        // We assert true only if it passes, otherwise fail to flag it
        assertTrue("Should retrieve the secret code BLUE-SKY-99", passed)
    }
}
