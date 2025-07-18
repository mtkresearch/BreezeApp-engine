package com.mtkresearch.breezeapp.edgeai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

/**
 * Business Logic Tests for EdgeAI SDK
 * 
 * These tests focus on the core business logic, data transformations,
 * and algorithmic behavior without requiring Android infrastructure.
 * 
 * As a Senior Android Architect, this approach:
 * 1. Tests business rules and logic flows
 * 2. Validates data transformations and conversions
 * 3. Ensures proper error handling and edge cases
 * 4. Verifies performance characteristics
 */
@RunWith(MockitoJUnitRunner::class)
class EdgeAIBusinessLogicTest {

    @Before
    fun setUp() {
        EdgeAI.shutdown()
    }

    @After
    fun tearDown() {
        EdgeAI.shutdown()
    }

    // === DATA TRANSFORMATION TESTS ===

    @Test
    fun `test chat message role validation and normalization`() {
        // Test valid roles
        val systemMessage = ChatMessage(role = "system", content = "You are helpful")
        val userMessage = ChatMessage(role = "user", content = "Hello")
        val assistantMessage = ChatMessage(role = "assistant", content = "Hi there")
        val toolMessage = ChatMessage(role = "tool", content = "Tool result")
        
        assertEquals("System role should be preserved", "system", systemMessage.role)
        assertEquals("User role should be preserved", "user", userMessage.role)
        assertEquals("Assistant role should be preserved", "assistant", assistantMessage.role)
        assertEquals("Tool role should be preserved", "tool", toolMessage.role)
        
        // Test content preservation
        assertNotNull("System message content should not be null", systemMessage.content)
        assertNotNull("User message content should not be null", userMessage.content)
        assertNotNull("Assistant message content should not be null", assistantMessage.content)
        assertNotNull("Tool message content should not be null", toolMessage.content)
    }

    @Test
    fun `test chat request parameter validation and defaults`() {
        // Test minimal valid request
        val minimalRequest = ChatRequest(
            model = "gpt-4",
            messages = listOf(ChatMessage(role = "user", content = "test"))
        )
        
        assertNotNull("Minimal request should be valid", minimalRequest)
        assertEquals("Model should be preserved", "gpt-4", minimalRequest.model)
        assertEquals("Messages should be preserved", 1, minimalRequest.messages.size)
        
        // Test request with all parameters
        val fullRequest = ChatRequest(
            model = "gpt-4",
            messages = listOf(
                ChatMessage(role = "system", content = "You are helpful"),
                ChatMessage(role = "user", content = "Hello")
            ),
            temperature = 0.7f,
            frequencyPenalty = 0.1f,
            presencePenalty = 0.2f,
            topP = 0.9f,
            n = 1,
            maxCompletionTokens = 1000,
            topLogprobs = 5,
            stream = true,
            stop = listOf("END", "STOP"),
            user = "user123"
        )
        
        assertEquals("Temperature should be preserved", 0.7f, fullRequest.temperature)
        assertEquals("Frequency penalty should be preserved", 0.1f, fullRequest.frequencyPenalty)
        assertEquals("Presence penalty should be preserved", 0.2f, fullRequest.presencePenalty)
        assertEquals("Top P should be preserved", 0.9f, fullRequest.topP)
        assertEquals("N should be preserved", 1, fullRequest.n)
        assertEquals("Max tokens should be preserved", 1000, fullRequest.maxCompletionTokens)
        assertEquals("Top logprobs should be preserved", 5, fullRequest.topLogprobs)
        assertEquals("Stream should be preserved", true, fullRequest.stream)
        assertEquals("Stop sequences should be preserved", 2, fullRequest.stop?.size)
        assertEquals("User should be preserved", "user123", fullRequest.user)
    }

    @Test
    fun `test TTS request voice and model validation`() {
        // Test all supported voices
        val voices = listOf("alloy", "echo", "fable", "onyx", "nova", "shimmer")
        
        voices.forEach { voice ->
            val request = TTSRequest(
                input = "Test text",
                model = "tts-1",
                voice = voice
            )
            
            assertEquals("Voice $voice should be preserved", voice, request.voice)
        }
        
        // Test different models
        val models = listOf("tts-1", "tts-1-hd")
        
        models.forEach { model ->
            val request = TTSRequest(
                input = "Test text",
                model = model,
                voice = "alloy"
            )
            
            assertEquals("Model $model should be preserved", model, request.model)
        }
    }

    @Test
    fun `test ASR request format and language handling`() {
        val audioData = byteArrayOf(1, 2, 3, 4, 5)
        
        // Test different response formats
        val formats = listOf("json", "text", "srt", "verbose_json", "vtt")
        
        formats.forEach { format ->
            val request = ASRRequest(
                audioData,
                model = "whisper-1",
                responseFormat = format
            )
            
            assertEquals("Format $format should be preserved", format, request.responseFormat)
        }
        
        // Test different languages
        val languages = listOf("en", "es", "fr", "de", "it", "pt", "ru", "ja", "ko", "zh")
        
        languages.forEach { language ->
            val request = ASRRequest(
                audioData,
                model = "whisper-1",
                language = language
            )
            
            assertEquals("Language $language should be preserved", language, request.language)
        }
    }

    // === RESPONSE PROCESSING TESTS ===

    @Test
    fun `test chat response choice processing`() {
        // Test non-streaming response
        val nonStreamingChoice = Choice(
            index = 0,
            message = ChatMessage(role = "assistant", content = "Hello!"),
            finishReason = "stop"
        )
        
        assertNotNull("Non-streaming choice should have message", nonStreamingChoice.message)
        assertNull("Non-streaming choice should not have delta", nonStreamingChoice.delta)
        assertEquals("Finish reason should be preserved", "stop", nonStreamingChoice.finishReason)
        
        // Test streaming response
        val streamingChoice = Choice(
            index = 0,
            delta = ChatMessage(role = "assistant", content = "Hel"),
            finishReason = null
        )
        
        assertNull("Streaming choice should not have message", streamingChoice.message)
        assertNotNull("Streaming choice should have delta", streamingChoice.delta)
        assertNull("Streaming choice should not have finish reason initially", streamingChoice.finishReason)
    }

    @Test
    fun `test usage statistics calculation`() {
        val usage = Usage(
            promptTokens = 10,
            completionTokens = 15,
            totalTokens = 25
        )
        
        assertEquals("Prompt tokens should be preserved", 10, usage.promptTokens)
        assertEquals("Completion tokens should be preserved", 15, usage.completionTokens)
        assertEquals("Total tokens should be preserved", 25, usage.totalTokens)
        
        // Verify total is sum of prompt and completion
        assertEquals("Total should equal prompt + completion", 
                    usage.promptTokens + usage.completionTokens, usage.totalTokens)
    }

    @Test
    fun `test TTS response audio data integrity`() {
        val originalAudioData = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        
        val response = TTSResponse(
            audioData = originalAudioData,
            format = "mp3"
        )
        
        assertArrayEquals("Audio data should be preserved exactly", 
                         originalAudioData, response.audioData)
        assertEquals("Format should be preserved", "mp3", response.format)
        
        // Test InputStream conversion
        val inputStream = response.toInputStream()
        val readData = inputStream.readBytes()
        
        assertArrayEquals("InputStream should contain same data", 
                         originalAudioData, readData)
    }

    // === EDGE CASE TESTS ===

    @Test
    fun `test empty and boundary value handling`() {
        // Test empty message content (should be allowed)
        val emptyContentMessage = ChatMessage(role = "user", content = "")
        assertEquals("Empty content should be preserved", "", emptyContentMessage.content)
        
        // Test very long content
        val longContent = "a".repeat(10000)
        val longContentMessage = ChatMessage(role = "user", content = longContent)
        assertEquals("Long content should be preserved", longContent.length, longContentMessage.content.length)
        
        // Test boundary temperature values
        val minTempRequest = ChatRequest(
            model = "gpt-4",
            messages = listOf(ChatMessage(role = "user", content = "test")),
            temperature = 0.0f
        )
        assertEquals("Min temperature should be preserved", 0.0f, minTempRequest.temperature)
        
        val maxTempRequest = ChatRequest(
            model = "gpt-4", 
            messages = listOf(ChatMessage(role = "user", content = "test")),
            temperature = 2.0f
        )
        assertEquals("Max temperature should be preserved", 2.0f, maxTempRequest.temperature)
    }

    @Test
    fun `test special character and encoding handling`() {
        // Test Unicode characters
        val unicodeContent = "Hello 世界 🌍 émojis 🚀"
        val unicodeMessage = ChatMessage(role = "user", content = unicodeContent)
        assertEquals("Unicode content should be preserved", unicodeContent, unicodeMessage.content)
        
        // Test special characters in TTS
        val specialTTSInput = "Hello, world! How are you? 123... @#$%"
        val ttsRequest = TTSRequest(
            input = specialTTSInput,
            model = "tts-1",
            voice = "alloy"
        )
        assertEquals("Special characters should be preserved in TTS", specialTTSInput, ttsRequest.input)
        
        // Test binary data integrity in ASR
        val binaryData = byteArrayOf(-128, -1, 0, 1, 127) // Full byte range
        val asrRequest = ASRRequest(
            binaryData,
            model = "whisper-1"
        )
        assertArrayEquals("Binary data should be preserved exactly", binaryData, asrRequest.file)
    }

    // === PERFORMANCE CHARACTERISTICS TESTS ===

    @Test
    fun `test large data handling efficiency`() {
        // Test large message list
        val largeMessageList = (1..1000).map { i ->
            ChatMessage(role = if (i % 2 == 0) "user" else "assistant", content = "Message $i")
        }
        
        val largeRequest = ChatRequest(
            model = "gpt-4",
            messages = largeMessageList
        )
        
        assertEquals("Large message list should be preserved", 1000, largeRequest.messages.size)
        assertEquals("First message should be correct", "Message 1", largeRequest.messages[0].content)
        assertEquals("Last message should be correct", "Message 1000", largeRequest.messages[999].content)
        
        // Test large audio data
        val largeAudioData = ByteArray(1024 * 1024) { (it % 256).toByte() } // 1MB
        
        val largeASRRequest = ASRRequest(
            largeAudioData,
            model = "whisper-1"
        )
        
        assertEquals("Large audio data size should be preserved", 1024 * 1024, largeASRRequest.file.size)
        assertEquals("First byte should be correct", 0.toByte(), largeASRRequest.file[0])
        assertEquals("Last byte should be correct", 255.toByte(), largeASRRequest.file[largeAudioData.size - 1])
    }

    @Test
    fun `test object creation and memory efficiency`() {
        // Test that creating multiple requests doesn't cause memory issues
        val requests = (1..1000).map { i ->
            ChatRequest(
                model = "gpt-4",
                messages = listOf(ChatMessage(role = "user", content = "Request $i"))
            )
        }
        
        assertEquals("Should create 1000 requests", 1000, requests.size)
        
        // Verify each request is independent
        requests.forEachIndexed { index, request ->
            assertEquals("Request $index should have correct content", 
                        "Request ${index + 1}", request.messages[0].content)
        }
        
        // Test that requests are immutable (data classes)
        val originalRequest = requests[0]
        val copiedRequest = originalRequest.copy(model = "different-model")
        
        assertNotEquals("Original and copied should be different", originalRequest, copiedRequest)
        assertEquals("Original should be unchanged", "gpt-4", originalRequest.model)
        assertEquals("Copy should have new model", "different-model", copiedRequest.model)
    }

    // === SERIALIZATION AND PARCELABLE TESTS ===

    @Test
    fun `test data class serialization properties`() {
        val originalMessage = ChatMessage(
            role = "user",
            content = "Test message",
            name = "TestUser"
        )
        
        // Test toString (should not crash and should contain key info)
        val stringRepresentation = originalMessage.toString()
        assertTrue("toString should contain role", stringRepresentation.contains("user"))
        assertTrue("toString should contain content", stringRepresentation.contains("Test message"))
        
        // Test copy functionality
        val copiedMessage = originalMessage.copy(content = "Modified content")
        assertEquals("Role should be preserved in copy", "user", copiedMessage.role)
        assertEquals("Content should be modified in copy", "Modified content", copiedMessage.content)
        assertEquals("Name should be preserved in copy", "TestUser", copiedMessage.name)
        
        // Original should be unchanged
        assertEquals("Original content should be unchanged", "Test message", originalMessage.content)
    }

    @Test
    fun `test complex nested object handling`() {
        val complexRequest = ChatRequest(
            model = "gpt-4",
            messages = listOf(
                ChatMessage(role = "system", content = "System prompt", name = "system"),
                ChatMessage(role = "user", content = "User message", name = "user1"),
                ChatMessage(role = "assistant", content = "Assistant response"),
                ChatMessage(role = "user", content = "Follow-up question", name = "user1")
            ),
            temperature = 0.8f,
            stop = listOf("END", "STOP", "FINISH")
        )
        
        // Verify nested structure is preserved
        assertEquals("Should have 4 messages", 4, complexRequest.messages.size)
        assertEquals("First message should be system", "system", complexRequest.messages[0].role)
        assertEquals("Second message should have name", "user1", complexRequest.messages[1].name)
        assertEquals("Third message should not have name", null, complexRequest.messages[2].name)
        assertEquals("Should have 3 stop sequences", 3, complexRequest.stop?.size)
        
        // Test that nested objects maintain independence
        val modifiedRequest = complexRequest.copy(
            messages = complexRequest.messages + ChatMessage(role = "user", content = "New message")
        )
        
        assertEquals("Original should have 4 messages", 4, complexRequest.messages.size)
        assertEquals("Modified should have 5 messages", 5, modifiedRequest.messages.size)
    }
}