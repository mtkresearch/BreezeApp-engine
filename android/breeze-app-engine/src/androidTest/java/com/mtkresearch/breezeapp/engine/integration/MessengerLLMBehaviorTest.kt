package com.mtkresearch.breezeapp.engine.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtkresearch.breezeapp.engine.integration.helpers.MessengerPayloadValidator
import com.mtkresearch.breezeapp.engine.model.EngineSettings
import com.mtkresearch.breezeapp.engine.model.InferenceRequest
import com.mtkresearch.breezeapp.engine.model.InferenceResult
import com.mtkresearch.breezeapp.engine.runner.openrouter.OpenRouterLLMRunner
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Category 2: LLM Behavior Integration Tests
 *
 * Purpose: Validate real LLM behavior with actual OpenRouter API calls.
 * This tests the effectiveness of BreezeSystemPrompt in guiding LLM responses.
 *
 * Tests correspond to TDD Plan Category 2:
 * - Test 2.1: Response Type Classification (7 scenarios)
 * - Test 2.2: Translation Accuracy (4 translations)
 * - Test 2.3: Draft Message Quality
 *
 * Note: These are instrumented tests requiring:
 * 1. OpenRouter API Key (via instrumentation arguments)
 * 2. Network connectivity
 * 3. Android device/emulator
 */
@RunWith(AndroidJUnit4::class)
class MessengerLLMBehaviorTest {

    private lateinit var context: Context
    private lateinit var openRouterApiKey: String
    private lateinit var openRouterModel: String

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        val args = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        openRouterApiKey = args.getString("OPENROUTER_API_KEY") ?: ""
        openRouterModel = args.getString("OPENROUTER_MODEL") ?: ""
    }

    /**
     * Test 2.1: Response Type Classification
     *
     * Validates that the LLM correctly identifies when to use "draft" vs "response"
     * based on trigger phrases in user commands.
     * 
     * Success Criteria: ≥95% accuracy (7/7 or 6/7 correct)
     */
    @Test
    fun llm_correctlyClassifiesResponseTypes() {
        // Check prerequisites
        Assume.assumeTrue(
            "Skipping: API Key required",
            openRouterApiKey.isNotBlank() && openRouterApiKey != "YOUR_OPENROUTER_KEY_HERE"
        )
        Assume.assumeTrue("Skipping: Model Name required", openRouterModel.isNotBlank())

        // Setup runner
        val runner = OpenRouterLLMRunner(context)
        val openRouterParams = mapOf("api_key" to openRouterApiKey, "model" to openRouterModel)
        val settings = EngineSettings.default()
            .withRunnerParameters("OpenRouterLLMRunner", openRouterParams)
        val loaded = runner.load("openrouter-test", settings, emptyMap())
        assertTrue("Failed to load OpenRouter runner", loaded)

        val systemPrompt = com.mtkresearch.breezeapp.engine.prompts.BreezeSystemPrompt.FULL_PROMPT

        // Test cases: command -> expected type
        val testCases = listOf(
            "@ai translate: 你好" to "response",
            "@ai what time is it?" to "response",
            "@ai summarize this conversation" to "response",
            "@ai help" to "response",
            "@ai tell Alice meeting is at 3pm" to "draft",
            "@ai send Bob a hello message" to "draft",
            "@ai let her know I'm running late" to "draft",
            "@ai translate and send: 謝謝" to "draft" 
        )

        System.out.println("========================================")
        System.out.println("Test 2.1: Response Type Classification")
        System.out.println("========================================")
        System.out.println("Model: $openRouterModel")
        System.out.println("Testing ${testCases.size} scenarios...")

        val failures = mutableListOf<String>()
        var passedCount = 0

        testCases.forEachIndexed { index, (userPrompt, expectedType) ->
            System.out.println("\n========================================")
            System.out.println("Scenario ${index + 1}/${testCases.size}")
            System.out.println("========================================")
            System.out.println("Input Prompt: $userPrompt")
            System.out.println("Expected Type: $expectedType")
            System.out.println("----------------------------------------")

            try {
                val fullInfo = "$systemPrompt\n\nUser: $userPrompt"
                val request = InferenceRequest(
                    sessionId = UUID.randomUUID().toString(),
                    inputs = mapOf(InferenceRequest.INPUT_TEXT to fullInfo),
                    params = mapOf("model" to openRouterModel)
                )

                val result = runner.run(request, stream = false)
                assertNull("Scenario ${index + 1} returned error: ${result.error}", result.error)

                val rawOutput = result.outputs[InferenceResult.OUTPUT_TEXT] as? String
                assertNotNull("Scenario ${index + 1} output was null", rawOutput)

                System.out.println("Model Raw Output:")
                System.out.println(rawOutput)
                System.out.println("----------------------------------------")

                val json = JSONObject(rawOutput!!)
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
                System.out.println("✅ Scenario ${index + 1} passed")
            } catch (e: Exception) {
                val errorMsg = "Scenario ${index + 1} ($userPrompt) failed: ${e.message}"
                failures.add(errorMsg)
                System.out.println("❌ $errorMsg")
            }
        }

        val accuracy = (passedCount.toDouble() / testCases.size) * 100
        System.out.println("\n========================================")
        System.out.println("Classification Accuracy: $passedCount/${testCases.size} (${accuracy}%)")
        System.out.println("Success Criteria: ≥95%")

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
    fun llm_translatesCorrectly() {
        // Check prerequisites
        Assume.assumeTrue(
            "Skipping: API Key required",
            openRouterApiKey.isNotBlank() && openRouterApiKey != "YOUR_OPENROUTER_KEY_HERE"
        )
        Assume.assumeTrue("Skipping: Model Name required", openRouterModel.isNotBlank())

        // Setup runner
        val runner = OpenRouterLLMRunner(context)
        val openRouterParams = mapOf("api_key" to openRouterApiKey, "model" to openRouterModel)
        val settings = EngineSettings.default()
            .withRunnerParameters("OpenRouterLLMRunner", openRouterParams)
        val loaded = runner.load("openrouter-test", settings, emptyMap())
        assertTrue("Failed to load OpenRouter runner", loaded)

        val systemPrompt = com.mtkresearch.breezeapp.engine.prompts.BreezeSystemPrompt.FULL_PROMPT

        // Translation test cases: Chinese -> Expected English (fuzzy match)
        val translationTests = mapOf(
            "你好" to listOf("hello", "hi"),
            "謝謝你" to listOf("thank you", "thanks"),
            "明天見" to listOf("see you tomorrow", "tomorrow"),
            "我餓了" to listOf("hungry", "i'm hungry")
        )

        System.out.println("========================================")
        System.out.println("Test 2.2: Translation Accuracy")
        System.out.println("========================================")
        System.out.println("Model: $openRouterModel")
        System.out.println("Testing ${translationTests.size} translations...")

        val failures = mutableListOf<String>()
        var correctCount = 0

        translationTests.toList().forEachIndexed { index, (chinese, expectedPhrases) ->
            System.out.println("\n========================================")
            System.out.println("Translation ${index + 1}/${translationTests.size}")
            System.out.println("========================================")
            System.out.println("Chinese Input: $chinese")
            System.out.println("Expected Phrases: $expectedPhrases")
            System.out.println("----------------------------------------")

            try {
                val userPrompt = "@ai translate: $chinese"
                val fullInfo = "$systemPrompt\n\nUser: $userPrompt"
                val request = InferenceRequest(
                    sessionId = UUID.randomUUID().toString(),
                    inputs = mapOf(InferenceRequest.INPUT_TEXT to fullInfo),
                    params = mapOf("model" to openRouterModel)
                )

                val result = runner.run(request, stream = false)
                assertNull("Translation request returned error: ${result.error}", result.error)

                val rawOutput = result.outputs[InferenceResult.OUTPUT_TEXT] as? String
                assertNotNull("Translation output was null", rawOutput)

                System.out.println("Model Raw Output:")
                System.out.println(rawOutput)
                System.out.println("----------------------------------------")

                val json = JSONObject(rawOutput!!)
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
                    System.out.println("✅ Translation correct")
                } else {
                    val errorMsg = "Translation of '$chinese' missing expected phrases. Got: $responseText"
                    failures.add(errorMsg)
                    System.out.println("❌ $errorMsg")
                }
            } catch (e: Exception) {
                val errorMsg = "Translation of '$chinese' failed: ${e.message}"
                failures.add(errorMsg)
                System.out.println("❌ $errorMsg")
            }
        }

        val accuracy = (correctCount.toDouble() / translationTests.size) * 100
        System.out.println("\n========================================")
        System.out.println("Translation Accuracy: $correctCount/${translationTests.size} (${accuracy}%)")
        System.out.println("Success Criteria: ≥90%")

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
    fun llm_generatesSensibleDrafts() {
        // Check prerequisites
        Assume.assumeTrue(
            "Skipping: API Key required",
            openRouterApiKey.isNotBlank() && openRouterApiKey != "YOUR_OPENROUTER_KEY_HERE"
        )
        Assume.assumeTrue("Skipping: Model Name required", openRouterModel.isNotBlank())

        // Setup runner
        val runner = OpenRouterLLMRunner(context)
        val openRouterParams = mapOf("api_key" to openRouterApiKey, "model" to openRouterModel)
        val settings = EngineSettings.default()
            .withRunnerParameters("OpenRouterLLMRunner", openRouterParams)
        val loaded = runner.load("openrouter-test", settings, emptyMap())
        assertTrue("Failed to load OpenRouter runner", loaded)

        val systemPrompt = com.mtkresearch.breezeapp.engine.prompts.BreezeSystemPrompt.FULL_PROMPT

        System.out.println("========================================")
        System.out.println("Test 2.3: Draft Message Quality")
        System.out.println("========================================")
        System.out.println("Model: $openRouterModel")
        System.out.println("")

        val userPrompt = "@ai tell Alice the meeting is at 3pm tomorrow"
        System.out.println("Input Prompt: $userPrompt")
        System.out.println("----------------------------------------")
        
        val fullInfo = "$systemPrompt\n\nUser: $userPrompt"
        val request = InferenceRequest(
            sessionId = UUID.randomUUID().toString(),
            inputs = mapOf(InferenceRequest.INPUT_TEXT to fullInfo),
            params = mapOf("model" to openRouterModel)
        )

        val result = runner.run(request, stream = false)
        assertNull("Draft request returned error: ${result.error}", result.error)

        val rawOutput = result.outputs[InferenceResult.OUTPUT_TEXT] as? String
        assertNotNull("Draft output was null", rawOutput)

        System.out.println("Model Raw Output:")
        System.out.println(rawOutput)
        System.out.println("----------------------------------------")

        try {
            val json = JSONObject(rawOutput!!)
            val validator = MessengerPayloadValidator.fromJSON(json)

            assertEquals("Should be draft type", "draft", validator.type)
            assertNotNull("Draft message should exist", validator.draftMessage)

            val draftMessage = validator.draftMessage!!
            System.out.println("Draft message: $draftMessage")

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

            System.out.println("✅ Draft message quality passed all checks")
        } catch (e: Exception) {
            System.out.println("❌ Draft quality test FAILED!")
            fail(
                "Draft quality test failed.\n" +
                        "Raw output: $rawOutput\n" +
                        "Error: ${e.message}"
            )
        }
    }
}