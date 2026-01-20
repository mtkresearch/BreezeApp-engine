package com.mtkresearch.breezeapp.edgeai.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtkresearch.breezeapp.edgeai.EdgeAI
import com.mtkresearch.breezeapp.edgeai.chatRequest
import com.mtkresearch.breezeapp.edgeai.integration.helpers.SDKTestBase
import com.mtkresearch.breezeapp.edgeai.integration.helpers.MessengerPayloadValidator
import com.mtkresearch.breezeapp.edgeai.integration.helpers.TestSystemPrompt
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Messenger SDK Compliance Test (EdgeAI SDK Version)
 *
 * Purpose: Validate that EdgeAI SDK can communicate with Engine and receive
 * properly formatted responses that match Messenger App requirements.
 *
 * This mirrors MessengerLLMComplianceTest.kt but uses EdgeAI SDK API instead
 * of direct Runner calls.
 *
 * Tests correspond to TDD Plan Category 1:
 * - Test 1.1: JSON Response Format Validation
 * - Test 1.2: Draft Response Format Validation
 * - Test 1.3: Response Completeness
 */
@RunWith(AndroidJUnit4::class)
class MessengerSDKComplianceTest : SDKTestBase() {
    
    /**
     * Test 1.1: JSON Response Format Validation
     * 
     * Validates that SDK receives properly formatted JSON responses
     * that match the Messenger schema.
     */
    @Test
    fun chatResponse_matchesExpectedSchema() = runBlocking {
        // System Prompt
        val systemPrompt = TestSystemPrompt.FULL_PROMPT
        val userPrompt = "@ai translate: 你好"
        
        // Create request via SDK API
        val request = chatRequest(
            prompt = userPrompt,
            systemPrompt = systemPrompt
        )
        
        // Execute via EdgeAI SDK
        val responses = EdgeAI.chat(request).toList()
        
        // Get final response
        assertTrue("SDK should return at least one response", responses.isNotEmpty())
        val finalResponse = responses.last()
        assertNotNull("Final response should not be null", finalResponse)
        
        val rawOutput = finalResponse.choices.first().message!!.content
        assertNotNull("Response content should not be null", rawOutput)
        
        // Log output
        logReport("========================================")
        logReport("Test 1.1: JSON Response Format Validation")
        logReport("========================================")
        logReport("Input: @ai translate: 你好")
        logReport("Raw Output:")
        logReport(rawOutput)
        logReport("========================================")
        
        // Parse JSON directly - NO workarounds for markdown wrapping
        try {
            val json = JSONObject(rawOutput)
            val validator = MessengerPayloadValidator.fromJSON(json)
            
            // Assertions (as per TDD Plan - only schema validation)
            assertEquals("Expected type 'response'", "response", validator.type)
            assertNotNull("Expected 'text' field", validator.text)
            
            logReport("✅ Model output is compliant with MessengerPayloadValidator schema")
        } catch (e: Exception) {
            logReport("❌ Model output is NOT compliant!")
            fail(
                "Model failed to produce valid JSON.\n" +
                "This means the model did not follow BreezeSystemPrompt instructions.\n" +
                "Raw output:\n$rawOutput\n\n" +
                "Error: ${e.message}"
            )
        }
    }
    
    /**
     * Test 1.2: Draft Response Format Validation
     * 
     * Validates that SDK can handle draft-type requests correctly.
     */
    @Test
    fun draftResponse_matchesExpectedSchema() = runBlocking {
        // System Prompt
        val systemPrompt = TestSystemPrompt.FULL_PROMPT
        val userPrompt = "@ai tell Alice meeting is at 3pm"
        
        // Create request via SDK API
        val request = chatRequest(
            prompt = userPrompt,
            systemPrompt = systemPrompt
        )
        
        // Execute via EdgeAI SDK
        val responses = EdgeAI.chat(request).toList()
        
        // Get final response
        assertTrue("SDK should return responses", responses.isNotEmpty())
        val rawOutput = responses.last().choices.first().message!!.content
        assertNotNull("Response content should not be null", rawOutput)
        
        // Log output
        logReport("========================================")
        logReport("Test 1.2: Draft Response Format Validation")
        logReport("========================================")
        logReport("Input: @ai tell Alice meeting is at 3pm")
        logReport("Raw Output:")
        logReport(rawOutput)
        logReport("========================================")
        
        // Validate draft schema
        try {
            val json = JSONObject(rawOutput)
            val validator = MessengerPayloadValidator.fromJSON(json)
            
            // Validate draft schema
            assertEquals("Expected type 'draft'", "draft", validator.type)
            assertTrue("Must have 'draft_message' field", 
                json.has("draft_message"))
            assertTrue("Must have 'recipient' field", 
                json.has("recipient"))
            assertTrue("Must have 'confirmation_prompt' field", 
                json.has("confirmation_prompt"))
            
            // Validate values
            assertEquals("Recipient should be Alice", "Alice", 
                json.optString("recipient"))
            assertFalse("draft_message should not be empty",
                json.optString("draft_message").isEmpty())
            
            logReport("✅ Draft response matches expected schema")
        } catch (e: Exception) {
            logReport("❌ Draft response validation failed!")
            fail(
                "Draft response does not match schema.\n" +
                "Raw output:\n$rawOutput\n\n" +
                "Error: ${e.message}"
            )
        }
    }
    
    /**
     * Test 1.3: Response Completeness
     * 
     * Validates that all response types are complete and properly formatted.
     */
    @Test
    fun allResponseTypes_areComplete() = runBlocking {
        val systemPrompt = TestSystemPrompt.FULL_PROMPT
        
        val testCases = listOf(
            "@ai translate: 你好" to "response",
            "@ai help" to "response",
            "@ai tell Alice hi" to "draft",
            "@ai summarize last 5 messages" to "response"
        )
        
        logReport("========================================")
        logReport("Test 1.3: Response Completeness")
        logReport("========================================")
        logReport("Testing ${testCases.size} different commands...")
        
        testCases.forEachIndexed { index, (command, expectedType) ->
            logReport("\n========================================")
            logReport("Test Case ${index + 1}/${testCases.size}")
            logReport("========================================")
            logReport("Command: $command")
            logReport("Expected Type: $expectedType")
            
            val request = chatRequest(
                prompt = command,
                systemPrompt = systemPrompt
            )
            
            val responses = EdgeAI.chat(request).toList()
            assertTrue("Response should be complete", responses.isNotEmpty())
            
            val rawOutput = responses.last().choices.first().message!!.content
            assertNotNull("Response content should not be null", rawOutput)

            logReport("Raw Output:")
            logReport(rawOutput)
            logReport("----------------------------------------")
            
            // Validate type
            val json = JSONObject(rawOutput)
            val actualType = json.optString("type")
            assertEquals(
                "Response type should be $expectedType",
                expectedType,
                actualType
            )
            
            logReport("✅ Test case ${index + 1} passed")
        }
        
        logReport("\n========================================")
        logReport("✅ All ${testCases.size} test cases passed")
        logReport("========================================")
    }
}
