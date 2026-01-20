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
 * Category 2: SDK LLM Behavior Integration Tests (EdgeAI SDK Version)
 *
 * Purpose: Validate real LLM behavior through EdgeAI SDK.
 * This mirrors MessengerLLMBehaviorTest.kt but uses EdgeAI SDK API.
 *
 * Tests correspond to TDD Plan Category 2:
 * - Test 2.1: Response Type Classification (8 scenarios)
 * - Test 2.2: Translation Accuracy (4 translations)
 * - Test 2.3: Draft Message Quality
 *
 * Note: These tests require Engine to be running with configured API keys.
 */
@RunWith(AndroidJUnit4::class)
class MessengerSDKBehaviorTest : SDKTestBase() {

    /**
     * Test 2.1: Response Type Classification
     *
     * Validates that the LLM correctly identifies when to use "draft" vs "response"
     * based on trigger phrases in user commands.
     * 
     * Success Criteria: ≥95% accuracy (8/8 or 7/8 correct)
     */
    @Test
    fun llm_correctlyClassifiesResponseTypes() = runBlocking {
        val systemPrompt = TestSystemPrompt.FULL_PROMPT

        // Test cases: command -> expected type
        val testCases = listOf(
            "@ai translate: 你好" to "response",
            "@ai what time is it?" to "response",
            "@ai summarize this" to "response",
            "@ai help" to "response",
            "@ai tell Alice meeting is at 3pm" to "draft",
            "@ai send Bob a hello message" to "draft",
            "@ai let her know I'm running late" to "draft",
            "@ai translate and send: 謝謝" to "draft"
        )

        logReport("========================================")
        logReport("Test 2.1: Response Type Classification")
        logReport("========================================")
        logReport("Testing ${testCases.size} scenarios...")

        val failures = mutableListOf<String>()
        var passedCount = 0

        testCases.forEachIndexed { index, (userPrompt, expectedType) ->
            logReport("\n========================================")
            logReport("Scenario ${index + 1}/${testCases.size}")
            logReport("========================================")
            logReport("Input Prompt: $userPrompt")
            logReport("Expected Type: $expectedType")
            logReport("----------------------------------------")

            try {
                val request = chatRequest(
                    prompt = userPrompt,
                    systemPrompt = systemPrompt
                )

                val responses = EdgeAI.chat(request).toList()
                assertTrue("Scenario ${index + 1} should get response", responses.isNotEmpty())

                val rawOutput = responses.last().choices.first().message!!.content
                assertNotNull("Scenario ${index + 1} output should not be null", rawOutput)
                
                logReport("Model Raw Output:")
                logReport(rawOutput)
                logReport("----------------------------------------")

                val json = JSONObject(rawOutput)
                val validator = MessengerPayloadValidator.fromJSON(json)

                // Include model output in assertion message for HTML report
                assertEquals(
                    "Scenario ${index + 1}: '$userPrompt'\n" +
                    "Expected: $expectedType\n" +
                    "Model Output: $rawOutput\n" +
                    "Result: PASS",
                    expectedType,
                    validator.type
                )
                passedCount++
                logReport("✅ Scenario ${index + 1} passed")
            } catch (e: Exception) {
                val errorMsg = "Scenario ${index + 1} ($userPrompt) failed: ${e.message}"
                failures.add(errorMsg)
                logReport("❌ $errorMsg")
            }
        }

        val accuracy = (passedCount.toDouble() / testCases.size) * 100
        logReport("\n========================================")
        logReport("Classification Accuracy: $passedCount/${testCases.size} (${accuracy}%)")
        logReport("Success Criteria: ≥95%")

        if (failures.isNotEmpty()) {
            fail("${failures.size}/${testCases.size} scenarios failed:\n" + 
                 failures.joinToString("\n"))
        }

        assertTrue(
            "Classification accuracy must be ≥95% (got ${accuracy}%)",
            accuracy >= 95.0
        )
    }

    /**
     * Test 2.2: Translation Accuracy
     *
     * Validates that translations contain expected English equivalents.
     * Tests basic Chinese-to-English translation quality.
     * 
     * Success Criteria: ≥90% accuracy (4/4 or 3/4 correct)
     */
    @Test
    fun llm_translatesAccurately() = runBlocking {
        val systemPrompt = TestSystemPrompt.FULL_PROMPT

        // Translation test cases: Chinese -> Expected English (fuzzy match)
        val translationTests = mapOf(
            "你好" to listOf("hello"),
            "謝謝你" to listOf("thank you"),
            "明天見" to listOf("see you tomorrow"),
            "我餓了" to listOf("i'm hungry")
        )

        logReport("========================================")
        logReport("Test 2.2: Translation Accuracy")
        logReport("========================================")
        logReport("Testing ${translationTests.size} translations...")

        val failures = mutableListOf<String>()
        var correctCount = 0

        translationTests.toList().forEachIndexed { index, (chinese, expectedPhrases) ->
            logReport("\n========================================")
            logReport("Translation ${index + 1}/${translationTests.size}")
            logReport("========================================")
            logReport("Chinese Input: $chinese")
            logReport("Expected Phrases: $expectedPhrases")
            logReport("----------------------------------------")

            try {
                val userPrompt = "@ai translate: $chinese"
                val request = chatRequest(
                    prompt = userPrompt,
                    systemPrompt = systemPrompt
                )

                val responses = EdgeAI.chat(request).toList()
                assertTrue("Translation should get response", responses.isNotEmpty())

                val rawOutput = responses.last().choices.first().message!!.content
                assertNotNull("Translation output should not be null", rawOutput)
                
                logReport("Model Raw Output:")
                logReport(rawOutput)
                logReport("----------------------------------------")

                val json = JSONObject(rawOutput)
                val validator = MessengerPayloadValidator.fromJSON(json)
                val responseText = validator.text?.lowercase() ?: ""

                // Check if any expected phrase is present
                val containsExpected = expectedPhrases.any { phrase ->
                    responseText.contains(phrase.lowercase())
                }

                // Include model output in assertion for HTML report
                assertTrue(
                    "Translation: '$chinese'\n" +
                    "Expected phrases: $expectedPhrases\n" +
                    "Model Output: $rawOutput\n" +
                    "Result: " + if (containsExpected) "PASS" else "FAIL",
                    containsExpected
                )

                if (containsExpected) {
                    correctCount++
                    logReport("✅ Translation correct")
                } else {
                    val errorMsg = "Translation of '$chinese' missing expected phrases. Got: $responseText"
                    failures.add(errorMsg)
                    logReport("❌ $errorMsg")
                }
            } catch (e: Exception) {
                val errorMsg = "Translation of '$chinese' failed: ${e.message}"
                failures.add(errorMsg)
                logReport("❌ $errorMsg")
            }
        }

        val accuracy = (correctCount.toDouble() / translationTests.size) * 100
        logReport("\n========================================")
        logReport("Translation Accuracy: $correctCount/${translationTests.size} (${accuracy}%)")
        logReport("Success Criteria: ≥90%")

        if (failures.isNotEmpty()) {
            fail("${failures.size}/${translationTests.size} translations failed:\n" + 
                 failures.joinToString("\n"))
        }

        assertTrue(
            "Translation accuracy must be ≥90% (got ${accuracy}%)",
            accuracy >= 90.0
        )
    }

    /**
     * Test 2.3: Draft Message Quality
     *
     * Validates that draft messages:
     * 1. Contain key information from the command
     * 2. Are conversational (reasonable length 20-200 chars)
     * 3. Don't contain the @ai trigger
     */
    @Test
    fun llm_generatesSensibleDrafts() = runBlocking {
        val systemPrompt = TestSystemPrompt.FULL_PROMPT

        logReport("========================================")
        logReport("Test 2.3: Draft Message Quality")
        logReport("========================================")
        logReport("")

        val userPrompt = "@ai tell Alice the meeting is at 3pm tomorrow"
        logReport("Input Prompt: $userPrompt")
        logReport("----------------------------------------")
        
        val request = chatRequest(
            prompt = userPrompt,
            systemPrompt = systemPrompt
        )

        val responses = EdgeAI.chat(request).toList()
        assertTrue("Draft request should get response", responses.isNotEmpty())

        val rawOutput = responses.last().choices.first().message!!.content
        assertNotNull("Draft output should not be null", rawOutput)
        
        logReport("Model Raw Output:")
        logReport(rawOutput)
        logReport("----------------------------------------")

        try {
            val json = JSONObject(rawOutput)
            val validator = MessengerPayloadValidator.fromJSON(json)

            assertEquals("Should be draft type", "draft", validator.type)
            assertNotNull("Draft message should exist", validator.draftMessage)

            val draftMessage = validator.draftMessage!!
            logReport("Draft message: $draftMessage")

            // Check draft contains key information
            assertTrue(
                "Draft should mention Alice",
                draftMessage.contains("Alice", ignoreCase = true)
            )
            assertTrue(
                "Draft should mention time",
                draftMessage.contains("3pm", ignoreCase = true) ||
                        draftMessage.contains("3 pm", ignoreCase = true)
            )
            assertTrue(
                "Draft should mention tomorrow",
                draftMessage.contains("tomorrow", ignoreCase = true)
            )

            // Check draft is conversational
            assertTrue(
                "Draft should be conversational",
                draftMessage.length in 20..200
            )

            // Check draft doesn't contain trigger
            assertFalse(
                "Draft should not contain @ai",
                draftMessage.contains("@ai")
            )

            logReport("✅ Draft message quality passed all checks")
        } catch (e: Exception) {
            logReport("❌ Draft quality test FAILED!")
            fail(
                "Draft quality test failed.\n" +
                        "Raw output: $rawOutput\n" +
                        "Error: ${e.message}"
            )
        }
    }
}
