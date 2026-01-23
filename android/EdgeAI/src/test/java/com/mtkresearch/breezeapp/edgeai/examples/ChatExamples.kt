package com.mtkresearch.breezeapp.edgeai.examples

import com.mtkresearch.breezeapp.edgeai.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Chat API examples for EdgeAI SDK.
 *
 * Complete examples for the **chat** API including basic usage, streaming,
 * multi-turn conversations, error handling, and advanced patterns.
 *
 * ## Topics Covered
 * - Basic chat completion (non-streaming)
 * - Streaming chat responses
 * - Multi-turn conversations with history
 * - Custom parameters (temperature, top_p, etc.)
 * - Error handling and recovery
 * - Request cancellation
 * - Production patterns
 *
 * ## For Client Developers
 * Read these examples to learn how to use the chat API.
 * Each example is a complete, working pattern you can copy.
 *
 * ## For Maintainers
 * When modifying the chat API, update these examples.
 * All examples must pass CI.
 *
 * @see EdgeAI.chat
 * @see ChatRequest
 * @see ChatResponse
 * @see TTSExamples for text-to-speech
 * @see ASRExamples for speech-to-text
 */
@RunWith(RobolectricTestRunner::class)
class ChatExamples : EdgeAITestBase() {
    // setUp/tearDown inherited from EdgeAITestBase

    /**
     * Example 01: Basic chat completion (non-streaming)
     *
     * Shows the simplest way to get a chat response.
     *
     * ## When to use
     * - Simple Q&A
     * - Short responses (< 100 tokens)
     * - When you need the complete response at once
     *
     * ## Input format
     * - Single prompt string OR list of messages
     * - Optional parameters (temperature, max_tokens, etc.)
     *
     * ## Output format
     * - Single `ChatResponse` with complete message
     * - Access via `response.choices.first().message.content`
     *
     * @see chatRequest builder for simple prompts
     */
    @Test
    fun `01 - basic chat completion`() = runTest {
        // Create request with simple prompt
        val request = chatRequest(
            prompt = "Say hello in exactly 5 words",
            temperature = 0.7f,
            maxTokens = 20
        )

        // Get complete response
        val response = EdgeAI.chat(request).first()

        // Extract content
        val content = response.choices.first().message?.content

        // Verify
        assertNotNull("Should have content", content)
        assertTrue("Content should not be empty", content!!.isNotBlank())
        println("Response: $content")
    }

    /**
     * Example 02: Streaming chat completion
     *
     * Shows how to get responses chunk-by-chunk for real-time display.
     *
     * ## When to use
     * - Long responses (> 100 tokens)
     * - Real-time chat interfaces
     * - When you want to show progress to users
     *
     * ## Input format
     * - Same as basic chat, but set `stream = true`
     *
     * ## Output format
     * - Multiple `ChatResponse` chunks via Flow
     * - Each chunk has `delta.content` (incremental text)
     * - Last chunk has `finishReason` (completion indicator)
     *
     * @see StreamingExamples for more streaming patterns
     */
    @Test
    fun `02 - streaming chat completion`() = runTest {
        // Create streaming request
        val request = chatRequest(
            prompt = "Write a short story about a robot",
            stream = true,  // Enable streaming
            temperature = 0.7f
        )

        // Accumulate response
        val fullResponse = StringBuilder()
        var chunkCount = 0

        // Collect streaming chunks
        EdgeAI.chat(request).collect { response ->
            val choice = response.choices.firstOrNull()

            if (choice?.finishReason == null) {
                // Still streaming - append delta
                choice?.delta?.content?.let { chunk ->
                    fullResponse.append(chunk)
                    chunkCount++
                    print(chunk)  // Real-time display
                }
            } else {
                // Stream complete
                println("\n✓ Stream finished: ${choice.finishReason}")
                println("Total chunks: $chunkCount")
            }
        }

        // Verify
        assertTrue("Should receive chunks", chunkCount > 0)
        assertTrue("Should have response", fullResponse.isNotEmpty())
    }

    /**
     * Example 03: Multi-turn conversation
     *
     * Shows how to maintain conversation history for context-aware responses.
     *
     * ## When to use
     * - Chatbot applications
     * - Assistant features
     * - When context from previous messages matters
     *
     * ## Input format
     * - List of `ChatMessage` objects
     * - Each message has `role` (system/user/assistant) and `content`
     * - Use `conversation {}` DSL for clean code
     *
     * ## Output format
     * - Same as basic chat
     * - Add assistant response to history for next turn
     *
     * ## Best practices
     * - Limit history to last 10-20 messages
     * - Include system message for behavior instructions
     * - Trim old messages to avoid token limits
     *
     * @see conversation DSL builder
     * @see chatRequestWithHistory
     */
    @Test
    fun `03 - multi-turn conversation`() = runTest {
        // Build conversation with history
        val request = chatRequestWithHistory(
            messages = conversation {
                system("You are a helpful math tutor. Be concise.")
                user("What is 2+2?")
                assistant("2+2 equals 4.")
                user("What about 3+3?")
            },
            temperature = 0.7f
        )

        // Get contextual response
        val response = EdgeAI.chat(request).first()
        val content = response.choices.first().message?.content

        // Verify contextual understanding
        assertNotNull("Should have response", content)
        assertTrue("Should mention 6", content!!.contains("6"))
        println("Contextual response: $content")
    }

    /**
     * Example 04: Custom parameters
     *
     * Shows how to use advanced parameters for fine-tuned control.
     *
     * ## Parameters explained
     * - `temperature` (0.0-2.0): Randomness (0.0 = deterministic, 2.0 = creative)
     * - `topP` (0.0-1.0): Nucleus sampling (0.1 = focused, 1.0 = diverse)
     * - `frequencyPenalty` (-2.0 to 2.0): Reduce repetition
     * - `presencePenalty` (-2.0 to 2.0): Encourage new topics
     * - `maxCompletionTokens`: Limit response length
     *
     * ## When to customize
     * - Creative writing: High temperature (0.8-1.2)
     * - Factual responses: Low temperature (0.2-0.5)
     * - Avoid repetition: Increase frequency penalty
     * - Explore topics: Increase presence penalty
     *
     * @see ChatRequest for all parameters
     */
    @Test
    fun `04 - custom parameters`() = runTest {
        // Create request with custom parameters
        val request = ChatRequest(
            messages = listOf(
                ChatMessage(role = "user", content = "Write a creative poem")
            ),
            temperature = 1.2f,        // High creativity
            topP = 0.9f,               // Diverse vocabulary
            frequencyPenalty = 0.5f,   // Reduce repetition
            presencePenalty = 0.6f,    // Encourage new topics
            maxCompletionTokens = 200, // Limit length
            stream = false
        )

        val response = EdgeAI.chat(request).first()
        val content = response.choices.first().message?.content

        assertNotNull("Should have creative response", content)
        println("Creative output: $content")
    }

    /**
     * Example 05: Error handling
     *
     * Shows how to handle chat API errors gracefully.
     *
     * ## Common errors
     * - `ServiceConnectionException`: BreezeApp Engine unavailable
     * - `InvalidInputException`: Bad parameters (empty prompt, invalid temperature)
     * - `TimeoutException`: Request took too long
     *
     * ## Recovery strategies
     * - Service error: Show installation dialog
     * - Invalid input: Validate before sending
     * - Timeout: Retry with longer timeout
     *
     * @see ErrorHandlingExamples for comprehensive error patterns
     */
    @Test
    fun `05 - error handling`() = runTest {
        val request = chatRequest(prompt = "Hello")

        EdgeAI.chat(request)
            .catch { error ->
                when (error) {
                    is ServiceConnectionException -> {
                        println("⚠ Service unavailable")
                        // Show installation dialog
                    }
                    is InvalidInputException -> {
                        println("⚠ Invalid input: ${error.message}")
                        // Validate and retry
                    }
                    is TimeoutException -> {
                        println("⚠ Request timeout")
                        // Retry with longer timeout
                    }
                    else -> {
                        println("✗ Unexpected error: ${error.message}")
                        // Log and show generic error
                    }
                }
            }
            .collect { response ->
                println("✓ Success: ${response.choices.first().message?.content}")
            }
    }

    /**
     * Example 06: Cancel streaming request
     *
     * Shows how to cancel a streaming chat request mid-stream.
     *
     * ## When to cancel
     * - User navigates away
     * - User stops the request
     * - Timeout reached
     *
     * ## How to cancel
     * - Cancel the coroutine job
     * - Flow collection stops automatically
     * - Resources are cleaned up
     *
     * @see kotlinx.coroutines.Job.cancel
     */
    @Test
    fun `06 - cancel streaming request`() = runTest {
        val request = chatRequest(
            prompt = "Write a very long story",
            stream = true
        )

        var chunkCount = 0
        val job = launch {
            EdgeAI.chat(request).collect { response ->
                chunkCount++
                println("Chunk $chunkCount")

                // Cancel after 3 chunks
                if (chunkCount >= 3) {
                    println("Cancelling...")
                    this.cancel()
                }
            }
        }

        job.join()
        assertEquals("Should receive exactly 3 chunks", 3, chunkCount)
    }

}
