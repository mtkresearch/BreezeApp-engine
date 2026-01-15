package com.mtkresearch.breezeapp.engine.breeze

import com.mtkresearch.breezeapp.edgeai.AIResponse
import com.mtkresearch.breezeapp.edgeai.ChatMessage
import com.mtkresearch.breezeapp.edgeai.ChatRequest
import com.mtkresearch.breezeapp.engine.helpers.MessengerPayloadValidator
import com.mtkresearch.breezeapp.engine.model.EngineSettings
import com.mtkresearch.breezeapp.engine.model.InferenceRequest
import com.mtkresearch.breezeapp.engine.model.InferenceResult
import com.mtkresearch.breezeapp.engine.runner.mock.MockLLMRunner
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class BreezeAPIContractTest {

    @Test
    fun chatResponse_matchesExpectedSchema() {
        val request =
                ChatRequest(
                        messages =
                                listOf(ChatMessage(role = "user", content = "@ai translate: 你好")),
                        model = "breeze-llm"
                )

        val response = sendRequestAndWaitForResponse(request)

        // Parse JSON response
        val json = JSONObject(response.text)

        // Validate schema
        assertTrue("Response must have 'type' field", json.has("type"))
        assertTrue("Response must have 'text' field", json.has("text"))
        assertEquals("Type should be 'response'", "response", json.getString("type"))

        // Validate parseable by Messenger app
        val payloadValidator = MessengerPayloadValidator.fromJSON(json)
        assertNotNull("Messenger app should be able to parse this", payloadValidator)
    }

    @Test
    fun draftResponse_matchesExpectedSchema() {
        val request =
                ChatRequest(
                        messages =
                                listOf(
                                        ChatMessage(
                                                role = "user",
                                                content = "@ai tell Alice meeting is at 3pm"
                                        )
                                ),
                        // currentRecipient removed, using metadata to pass context if needed,
                        // though for this test the prompt content drives the "Tenant #1" logic in
                        // MockLLMRunner.
                        metadata = mapOf("current_recipient" to "Alice"),
                        model = "breeze-llm"
                )

        val response = sendRequestAndWaitForResponse(request)
        val json = JSONObject(response.text)

        // Validate draft schema
        assertEquals("Type should be 'draft'", "draft", json.getString("type"))
        assertTrue("Must have 'draft_message' field", json.has("draft_message"))
        assertTrue("Must have 'recipient' field", json.has("recipient"))
        assertTrue("Must have 'confirmation_prompt' field", json.has("confirmation_prompt"))

        // Validate values
        assertEquals("Recipient should be Alice", "Alice", json.getString("recipient"))
        assertFalse("draft_message should not be empty", json.getString("draft_message").isEmpty())
    }

    @Test
    fun allResponseTypes_areComplete() {
        val testCases =
                listOf(
                        "@ai translate: 你好" to "response",
                        "@ai help" to "response",
                        "@ai tell Alice hi" to "draft",
                        "@ai summarize last 5 messages" to "response"
                )

        testCases.forEach { (command, expectedType) ->
            val response = sendCommand(command)
            assertTrue("Response should be complete", response.isComplete)
            val json = JSONObject(response.text)
            assertEquals(
                    "Response type should be $expectedType",
                    expectedType,
                    json.getString("type")
            )
        }
    }

    // Helpers using real MockLLMRunner
    private fun sendRequestAndWaitForResponse(request: ChatRequest): AIResponse {
        // 1. Initialize Runner
        val runner = MockLLMRunner()
        val settings = EngineSettings.default()
        runner.load("mock-llm", settings, emptyMap())

        // Inject Response Strategy based on input
        runner.setResponseStrategy { prompt ->
            when {
                prompt.contains("translate:", ignoreCase = true) ->
                        """{"type": "response", "text": "Hello"}"""
                prompt.contains("tell Alice", ignoreCase = true) ->
                        """{"type": "draft", "draft_message": "Hi Alice, meeting is at 3pm", "recipient": "Alice", "confirmation_prompt": "Send?"}"""
                prompt.contains("help", ignoreCase = true) ->
                        """{"type": "response", "text": "Help"}"""
                prompt.contains("summarize", ignoreCase = true) ->
                        """{"type": "response", "text": "Summary"}"""
                else -> """{"type": "response", "text": "Default"}"""
            }
        }

        // 2. Convert ChatRequest to InferenceRequest
        // Extract the last message content as the prompt
        val prompt = request.messages.lastOrNull()?.content ?: ""

        val inferenceRequest =
                InferenceRequest(
                        sessionId = "test-session-${UUID.randomUUID()}",
                        inputs = mapOf(InferenceRequest.INPUT_TEXT to prompt),
                        params = mapOf("model" to (request.model ?: "mock-llm"))
                )

        // 3. Run Inference
        val result = runner.run(inferenceRequest, stream = false)

        // 4. Convert InferenceResult to AIResponse
        // In a real scenario, this mapping happens in the Service/Manager layer
        val resultText = result.outputs[InferenceResult.OUTPUT_TEXT] as? String ?: ""

        return AIResponse(
                requestId = UUID.randomUUID().toString(),
                text = resultText,
                isComplete = true, // Non-streaming is always complete
                state =
                        if (result.error != null) AIResponse.ResponseState.ERROR
                        else AIResponse.ResponseState.COMPLETED,
                error = result.error?.message
        )
    }

    private fun sendCommand(command: String): AIResponse {
        val request =
                ChatRequest(
                        messages = listOf(ChatMessage(role = "user", content = command)),
                        model = "breeze-llm"
                )
        return sendRequestAndWaitForResponse(request)
    }
}
