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
 * Messenger LLM Compliance Test
 *
 * 目的：驗證真實的 LLM Runner (搭配 System Prompt) 是否能產生符合 Messenger App 要求的 JSON 格式。
 *
 * 注意：
 * 1. 这是一个 Instrumented Test，需要在 Android 設備或模擬器上運行。
 * 2. 默認會跳過需要 API Key 的測試 (如 OpenRouter)，除非透過 instrumentation arguments 提供。
 */
@RunWith(AndroidJUnit4::class)
class MessengerLLMComplianceTest {

        private lateinit var context: Context
        private lateinit var openRouterApiKey: String
        private lateinit var openRouterModel: String

        @Before
        fun setup() {
                context = ApplicationProvider.getApplicationContext()

                // Fetch arguments passed via -Pandroid.testInstrumentationRunnerArguments.key=value
                val args = androidx.test.platform.app.InstrumentationRegistry.getArguments()
                openRouterApiKey = args.getString("OPENROUTER_API_KEY") ?: ""
                openRouterModel = args.getString("OPENROUTER_MODEL") ?: ""
        }

        /**
         * 驗證 OpenRouter Runner 是否能遵循 JSON 格式指令
         *
         * 前提：需要：
         * 1. OpenRouter API Key
         * 2. 指定的 Model Name
         * 3. 網際網路連線
         */
        @Test
        fun chatResponse_matchesExpectedSchema() {
                // 1. Check Preconditions (Skip if key or model missing)
                Assume.assumeTrue(
                        "Skipping: API Key required. Pass -Pandroid.testInstrumentationRunnerArguments.OPENROUTER_API_KEY=...",
                        openRouterApiKey.isNotBlank() &&
                                openRouterApiKey != "YOUR_OPENROUTER_KEY_HERE"
                )

                Assume.assumeTrue(
                        "Skipping: Model Name required. Pass -Pandroid.testInstrumentationRunnerArguments.OPENROUTER_MODEL=...",
                        openRouterModel.isNotBlank()
                )

                // Note: We removed the network availability check because it requires
                // ACCESS_NETWORK_STATE
                // permission.
                // If there's no network, the API call will fail with a clear error.

                // 2. Setup Runner
                val runner = OpenRouterLLMRunner(context)

                // Configure settings using the generic map approach since EngineSettings is dynamic
                val openRouterParams =
                        mapOf("api_key" to openRouterApiKey, "model" to openRouterModel)

                val settings =
                        EngineSettings.default()
                                .withRunnerParameters("OpenRouterLLMRunner", openRouterParams)

                // Load Model
                val loaded = runner.load("openrouter-test", settings, emptyMap())
                assertTrue("Failed to load OpenRouter runner", loaded)

                // 3. Construct System Prompt + User Prompt
                // 使用統一的 System Prompt 來源
                val systemPrompt =
                        com.mtkresearch.breezeapp.engine.prompts.BreezeSystemPrompt.FULL_PROMPT

                val userPrompt = "@ai translate: 你好"

                // Combine them (OpenRouter runner usually handles chat history, but here we
                // simulate raw
                // input or formatted prompt)
                val fullInfo = "$systemPrompt\n\nUser: $userPrompt"

                val request =
                        InferenceRequest(
                                sessionId = UUID.randomUUID().toString(),
                                inputs = mapOf(InferenceRequest.INPUT_TEXT to fullInfo),
                                params = mapOf("model" to openRouterModel)
                        )

                // 4. Run Inference (Blocking)
                val result = runner.run(request, stream = false)

                // 5. Validation
                assertNull("Runner returned an error: ${result.error}", result.error)
                val rawOutput = result.outputs[InferenceResult.OUTPUT_TEXT] as? String
                assertNotNull("Runner output was null", rawOutput)

                // Log full output to terminal for debugging
                System.out.println("========================================")
                System.out.println("Test 1.1: JSON Response Format Validation")
                System.out.println("========================================")
                System.out.println("Model: $openRouterModel")
                System.out.println("Input: @ai translate: 你好")
                System.out.println("Raw Output:")
                System.out.println(rawOutput)
                System.out.println("========================================")

                // Parse JSON directly - NO workarounds for markdown wrapping.
                // If the model wraps output in ```json blocks, the test SHOULD fail.
                // This is a compliance test: we're testing if the model follows our System Prompt
                // instructions.
                try {
                        val json = JSONObject(rawOutput!!)
                        val validator = MessengerPayloadValidator.fromJSON(json)

                        // Assertions (as per TDD Plan - only schema validation)
                        assertEquals("Expected type 'response'", "response", validator.type)
                        assertNotNull("Expected 'text' field", validator.text)

                        System.out.println(
                                "✅ Model output is compliant with MessengerPayloadValidator schema"
                        )
                } catch (e: Exception) {
                        System.out.println("❌ Model output is NOT compliant!")
                        fail(
                                "Model failed to produce valid JSON.\n" +
                                        "This means the model did not follow BreezeSystemPrompt instructions.\n" +
                                        "Raw output:\n$rawOutput\n\n" +
                                        "Error: ${e.message}"
                        )
                }
        }

        /** 驗證 OpenRouter Runner 是否能正確處理 Draft 類型的請求 測試 "tell/send [person]..." 指令 */
        @Test
        fun draftResponse_matchesExpectedSchema() {
                // 1. Check Preconditions
                Assume.assumeTrue(
                        "Skipping: API Key required",
                        openRouterApiKey.isNotBlank() &&
                                openRouterApiKey != "YOUR_OPENROUTER_KEY_HERE"
                )
                Assume.assumeTrue("Skipping: Model Name required", openRouterModel.isNotBlank())

                // 2. Setup Runner
                val runner = OpenRouterLLMRunner(context)
                val openRouterParams =
                        mapOf("api_key" to openRouterApiKey, "model" to openRouterModel)
                val settings =
                        EngineSettings.default()
                                .withRunnerParameters("OpenRouterLLMRunner", openRouterParams)
                val loaded = runner.load("openrouter-test", settings, emptyMap())
                assertTrue("Failed to load OpenRouter runner", loaded)

                // 3. Construct Draft Request
                val systemPrompt =
                        com.mtkresearch.breezeapp.engine.prompts.BreezeSystemPrompt.FULL_PROMPT
                val userPrompt = "@ai tell Alice meeting is at 3pm"
                val fullInfo = "$systemPrompt\n\nUser: $userPrompt"

                val request =
                        InferenceRequest(
                                sessionId = UUID.randomUUID().toString(),
                                inputs = mapOf(InferenceRequest.INPUT_TEXT to fullInfo),
                                params = mapOf("model" to openRouterModel)
                        )

                // 4. Run Inference
                val result = runner.run(request, stream = false)

                // 5. Validation
                assertNull("Runner returned an error: ${result.error}", result.error)
                val rawOutput = result.outputs[InferenceResult.OUTPUT_TEXT] as? String
                assertNotNull("Runner output was null", rawOutput)

                // Log output
                System.out.println("========================================")
                System.out.println("Test 1.2: Draft Response Format Validation")
                System.out.println("========================================")
                System.out.println("Model: $openRouterModel")
                System.out.println("Input: @ai tell Alice meeting is at 3pm")
                System.out.println("Raw Output:")
                System.out.println(rawOutput)
                System.out.println("========================================")

                // Parse and validate draft schema
                try {
                        val json = JSONObject(rawOutput!!)
                        val validator = MessengerPayloadValidator.fromJSON(json)

                        // Draft-specific assertions
                        assertEquals("Expected type 'draft'", "draft", validator.type)
                        assertNotNull("Expected 'draft_message' field", validator.draftMessage)
                        assertNotNull("Expected 'recipient' field", validator.recipient)
                        assertNotNull(
                                "Expected 'confirmation_prompt' field",
                                validator.confirmationPrompt
                        )

                        // Validate recipient is Alice
                        assertEquals(
                                "Recipient should be 'Alice'",
                                "Alice",
                                validator.recipient
                        )
                        
                        // Validate draft_message is not empty
                        assertFalse(
                                "draft_message should not be empty",
                                validator.draftMessage.isNullOrEmpty()
                        )

                        System.out.println("✅ Draft response is compliant")
                } catch (e: Exception) {
                        System.out.println("❌ Draft response is NOT compliant!")
                        fail(
                                "Model failed to produce valid draft JSON.\n" +
                                        "Raw output:\n$rawOutput\n\n" +
                                        "Error: ${e.message}"
                        )
                }
        }

        /** 驗證 OpenRouter Runner 在多種場景下的一致性 對應 BreezeAPIContractTest.allResponseTypes_areComplete */
        @Test
        fun allResponseTypes_areComplete() {
                // 1. Check Preconditions
                Assume.assumeTrue(
                        "Skipping: API Key required",
                        openRouterApiKey.isNotBlank() &&
                                openRouterApiKey != "YOUR_OPENROUTER_KEY_HERE"
                )
                Assume.assumeTrue("Skipping: Model Name required", openRouterModel.isNotBlank())

                // 2. Setup Runner (reuse for all scenarios)
                val runner = OpenRouterLLMRunner(context)
                val openRouterParams =
                        mapOf("api_key" to openRouterApiKey, "model" to openRouterModel)
                val settings =
                        EngineSettings.default()
                                .withRunnerParameters("OpenRouterLLMRunner", openRouterParams)
                val loaded = runner.load("openrouter-test", settings, emptyMap())
                assertTrue("Failed to load OpenRouter runner", loaded)

                val systemPrompt =
                        com.mtkresearch.breezeapp.engine.prompts.BreezeSystemPrompt.FULL_PROMPT

                // 3. Test multiple scenarios
                val testCases =
                        listOf(
                                "@ai translate: 你好" to "response",
                                "@ai help" to "response",
                                "@ai tell Alice hi" to "draft",
                                "@ai summarize last 5 messages" to "response"
                        )

                System.out.println("========================================")
                System.out.println("Test 1.3: Response Completeness")
                System.out.println("========================================")
                System.out.println("Model: $openRouterModel")
                System.out.println("Testing ${testCases.size} scenarios...")

                testCases.forEachIndexed { index, (userPrompt, expectedType) ->
                        System.out.println("\n--- Scenario ${index + 1}: $userPrompt ---")

                        val fullInfo = "$systemPrompt\n\nUser: $userPrompt"
                        val request =
                                InferenceRequest(
                                        sessionId = UUID.randomUUID().toString(),
                                        inputs = mapOf(InferenceRequest.INPUT_TEXT to fullInfo),
                                        params = mapOf("model" to openRouterModel)
                                )

                        val result = runner.run(request, stream = false)
                        assertNull(
                                "Scenario ${index + 1} returned error: ${result.error}",
                                result.error
                        )

                        val rawOutput = result.outputs[InferenceResult.OUTPUT_TEXT] as? String
                        assertNotNull("Scenario ${index + 1} output was null", rawOutput)

                        System.out.println("Output: $rawOutput")

                        try {
                                val json = JSONObject(rawOutput!!)
                                val validator = MessengerPayloadValidator.fromJSON(json)

                                assertEquals(
                                        "Scenario ${index + 1}: Expected type '$expectedType'",
                                        expectedType,
                                        validator.type
                                )

                                System.out.println("✅ Scenario ${index + 1} passed")
                        } catch (e: Exception) {
                                System.out.println("❌ Scenario ${index + 1} FAILED!")
                                fail(
                                        "Scenario ${index + 1} failed.\n" +
                                                "Prompt: $userPrompt\n" +
                                                "Expected type: $expectedType\n" +
                                                "Raw output: $rawOutput\n" +
                                                "Error: ${e.message}"
                                )
                        }
                }

                System.out.println("\n✅ All ${testCases.size} scenarios passed!")
                System.out.println("=============================================")
        }

        // Note: extractJsonFromMarkdown was intentionally removed.
        // If a model wraps JSON in markdown code blocks, it's NOT following our System Prompt.
        // The test should fail to catch this non-compliance.
}
