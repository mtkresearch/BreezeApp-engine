package com.mtkresearch.breezeapp.edgeai.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtkresearch.breezeapp.edgeai.EdgeAI
import com.mtkresearch.breezeapp.edgeai.chatRequest
import com.mtkresearch.breezeapp.edgeai.integration.helpers.SDKTestBase
import com.mtkresearch.breezeapp.edgeai.integration.helpers.MessengerPayloadValidator
import com.mtkresearch.breezeapp.edgeai.integration.helpers.TestSystemPrompt
import com.mtkresearch.breezeapp.edgeai.integration.helpers.TestDataLoader
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
    
    // Load test data lazily
    private val testData by lazy { TestDataLoader.loadCategory1Data() }
    
    /**
     * Test 1.1: JSON Response Format Validation
     * 
     * Validates that SDK receives properly formatted JSON responses
     * that match the Messenger schema.
     */
    @Test
    fun chatResponse_matchesExpectedSchema() = runBlocking {
        logReport("========================================")
        logReport("Test 1.1: JSON Response Format Validation")
        logReport("Data Source: category1_compliance.json")
        logReport("========================================")
        
        val systemPrompt = TestSystemPrompt.FULL_PROMPT
        
        testData.schemaValidationTests.forEachIndexed { index, testCase ->
            logReport("\n--- Scenario ${index+1}: '${testCase.input}' ---")
            val request = chatRequest(
                prompt = testCase.input,
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
            logReport("Output: $rawOutput")
             
            // Parse JSON directly - NO workarounds for markdown wrapping
            try {
                val json = JSONObject(rawOutput)
                val validator = MessengerPayloadValidator.fromJSON(json)
                
                // Required checks per MessengerPayloadValidator
                assertNotNull("Should have text", validator.text)
                
                // Optional specific field/value checks from JSON
                testCase.expectedFields?.forEach { field ->
                    assertTrue("Response should contain field '$field'", json.has(field))
                }
                testCase.expectedValues?.forEach { (key, expectedVal) ->
                    assertEquals("Field '$key' should match", expectedVal, json.optString(key))
                }
                
                logReport("✅ Schema validation PASSED")
            } catch (e: Exception) {
                logReport("❌ Schema validation FAILED: ${e.message}")
                fail(e.message)
            }
        }
    }
    
    /**
     * Test 1.2: Draft Response Format Validation
     * 
     * Validates that SDK can handle draft-type requests correctly.
     */
    @Test
    fun draftResponse_matchesExpectedSchema() = runBlocking {
        logReport("========================================")
        logReport("Test 1.2: Draft Response Format Validation")
        logReport("========================================")
        
        val systemPrompt = TestSystemPrompt.FULL_PROMPT
        
        testData.draftSchemaTests.forEachIndexed { index, testCase ->
            logReport("\n--- Scenario ${index+1}: '${testCase.input}' ---")
            val request = chatRequest(
            prompt = testCase.input,
            systemPrompt = systemPrompt
            )
            
            val responses = EdgeAI.chat(request).toList()
            assertTrue("Should receive response", responses.isNotEmpty())
            val rawOutput = responses.last().choices.first().message!!.content
            assertNotNull("Response content should not be null", rawOutput)
            logReport("Output: $rawOutput")

            try {
                val json = JSONObject(rawOutput)
                val validator = MessengerPayloadValidator.fromJSON(json)
                
                assertEquals("Expected type 'draft'", "draft", validator.type)
                assertTrue("Must have 'draft_message' field", json.has("draft_message"))
                assertTrue("Must have 'recipient' field", json.has("recipient"))
                assertTrue("Must have 'confirmation_prompt' field", json.has("confirmation_prompt"))
                
                assertEquals("Recipient should match", testCase.expectedRecipient, json.optString("recipient"))
                
                if (testCase.checkNotEmpty) {
                    val msg = json.optString("draft_message")
                    assertFalse("draft_message should not be empty", msg.isEmpty())
                }
                
                logReport("✅ Draft schema PASSED")
            } catch(e: Exception) {
                logReport("❌ Draft schema FAILED: ${e.message}")
                fail(e.message)
            }
        }
    }
    
    /**
     * Test 1.3: Response Completeness
     * 
     * Validates that all response types are complete and properly formatted.
     */
    @Test
    fun allResponseTypes_areComplete() = runBlocking {
        logReport("========================================")
        logReport("Test 1.3: Response Completeness")
        logReport("========================================")
        
        val systemPrompt = TestSystemPrompt.FULL_PROMPT
        
        testData.completenessTests.forEachIndexed { index, testCase ->
             logReport("\n--- Scenario ${index+1}: '${testCase.input}' ---")
             val request = chatRequest(
                 prompt = testCase.input,
                 systemPrompt = systemPrompt
             )
             
             val responses = EdgeAI.chat(request).toList()
             assertTrue("Response should be complete", responses.isNotEmpty())

             val rawOutput = responses.last().choices.first().message!!.content
            assertNotNull("Response content should not be null", rawOutput)
             logReport("Output: $rawOutput")
             
             try {
                 val json = JSONObject(rawOutput)
                 val actualType = json.optString("type")
                 assertEquals("Type should match", testCase.expectedType, actualType)
                 
                 if (testCase.checkCompleteness) {
                     // Basic check: is it valid JSON and has text?
                     val validator = MessengerPayloadValidator.fromJSON(json)
                     assertNotNull(validator) // throws if invalid
                     logReport("✅ Completeness Check passed")
                 }
                 
             } catch(e: Exception) {
                 logReport("❌ Completeness Check FAILED: ${e.message}")
                 fail(e.message)
             }
        }
    }
}
