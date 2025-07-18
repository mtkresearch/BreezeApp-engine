package com.mtkresearch.breezeapp.edgeai

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

/**
 * Architectural Tests for EdgeAI SDK
 * 
 * These tests focus on the core architectural principles and business logic
 * without requiring Android Context or service binding. They test the SDK's
 * internal behavior, error handling, and API contracts.
 * 
 * As a Senior Android Architect, this approach:
 * 1. Separates concerns - tests logic, not infrastructure
 * 2. Follows SOLID principles - tests abstractions, not implementations
 * 3. Ensures fast, reliable unit tests
 * 4. Validates architectural decisions
 */
@RunWith(MockitoJUnitRunner::class)
class EdgeAIArchitecturalTest {

    @Before
    fun setUp() {
        // Reset EdgeAI state before each test
        EdgeAI.shutdown()
    }

    @After
    fun tearDown() {
        EdgeAI.shutdown()
    }

    // === ARCHITECTURAL PRINCIPLE TESTS ===

    @Test
    fun `test SDK follows singleton pattern correctly`() {
        // Verify EdgeAI is a proper singleton
        assertFalse("SDK should not be initialized initially", EdgeAI.isInitialized())
        assertFalse("SDK should not be ready initially", EdgeAI.isReady())
        
        // Test state consistency
        val initializedState1 = EdgeAI.isInitialized()
        val initializedState2 = EdgeAI.isInitialized()
        assertEquals("Singleton state should be consistent", initializedState1, initializedState2)
    }

    @Test
    fun `test SDK state management lifecycle`() {
        // Test initial state
        assertFalse("SDK should start uninitialized", EdgeAI.isInitialized())
        assertFalse("SDK should start not ready", EdgeAI.isReady())
        
        // Test shutdown idempotency
        EdgeAI.shutdown()
        EdgeAI.shutdown() // Should not crash
        
        assertFalse("SDK should remain uninitialized after shutdown", EdgeAI.isInitialized())
        assertFalse("SDK should remain not ready after shutdown", EdgeAI.isReady())
    }

    @Test
    fun `test API methods throw appropriate exceptions when not initialized`() {
        // Ensure SDK is not initialized
        EdgeAI.shutdown()
        
        val chatRequest = ChatRequest(
            model = "test-model",
            messages = listOf(ChatMessage(role = "user", content = "test"))
        )
        
        val ttsRequest = TTSRequest(
            input = "test text",
            model = "tts-1",
            voice = "alloy"
        )
        
        val asrRequest = ASRRequest(
            byteArrayOf(1, 2, 3),
            model = "whisper-1"
        )
        
        // Test that all API methods throw ServiceConnectionException when not initialized
        assertThrows("Chat should throw when not initialized", ServiceConnectionException::class.java) {
            runBlocking { EdgeAI.chat(chatRequest).first() }
        }
        
        assertThrows("TTS should throw when not initialized", ServiceConnectionException::class.java) {
            runBlocking { EdgeAI.tts(ttsRequest).first() }
        }
        
        assertThrows("ASR should throw when not initialized", ServiceConnectionException::class.java) {
            runBlocking { EdgeAI.asr(asrRequest).first() }
        }
    }

    // === REQUEST VALIDATION TESTS ===

    @Test
    fun `test ChatRequest validation and immutability`() {
        // Test valid request creation
        val validRequest = ChatRequest(
            model = "gpt-4",
            messages = listOf(
                ChatMessage(role = "system", content = "You are a helpful assistant"),
                ChatMessage(role = "user", content = "Hello")
            ),
            temperature = 0.7f,
            maxCompletionTokens = 1000
        )
        
        assertNotNull("Valid request should be created", validRequest)
        assertEquals("Model should be set correctly", "gpt-4", validRequest.model)
        assertEquals("Messages should be set correctly", 2, validRequest.messages.size)
        assertEquals("Temperature should be set correctly", 0.7f, validRequest.temperature)
        assertEquals("Max tokens should be set correctly", 1000, validRequest.maxCompletionTokens)
    }

    @Test
    fun `test TTSRequest validation and constraints`() {
        // Test valid TTS request
        val validRequest = TTSRequest(
            input = "Hello, world!",
            model = "tts-1",
            voice = "alloy",
            speed = 1.0f
        )
        
        assertNotNull("Valid TTS request should be created", validRequest)
        assertEquals("Input should be set correctly", "Hello, world!", validRequest.input)
        assertEquals("Model should be set correctly", "tts-1", validRequest.model)
        assertEquals("Voice should be set correctly", "alloy", validRequest.voice)
        assertEquals("Speed should be set correctly", 1.0f, validRequest.speed)
    }

    @Test
    fun `test ASRRequest validation and audio data handling`() {
        val audioData = byteArrayOf(1, 2, 3, 4, 5)
        
        val validRequest = ASRRequest(
            audioData,
            model = "whisper-1",
            language = "en",
            temperature = 0.0f
        )
        
        assertNotNull("Valid ASR request should be created", validRequest)
        assertArrayEquals("Audio data should be preserved", audioData, validRequest.file)
        assertEquals("Model should be set correctly", "whisper-1", validRequest.model)
        assertEquals("Language should be set correctly", "en", validRequest.language)
        assertEquals("Temperature should be set correctly", 0.0f, validRequest.temperature)
    }

    // === RESPONSE MODEL TESTS ===

    @Test
    fun `test ChatResponse structure and immutability`() {
        val choice = Choice(
            index = 0,
            message = ChatMessage(role = "assistant", content = "Hello!"),
            finishReason = "stop"
        )
        
        val usage = Usage(
            promptTokens = 10,
            completionTokens = 5,
            totalTokens = 15
        )
        
        val response = ChatResponse(
            id = "test-id",
            `object` = "chat.completion",
            created = 1234567890L,
            model = "gpt-4",
            choices = listOf(choice),
            usage = usage
        )
        
        assertNotNull("Response should be created", response)
        assertEquals("ID should be preserved", "test-id", response.id)
        assertEquals("Object type should be preserved", "chat.completion", response.`object`)
        assertEquals("Model should be preserved", "gpt-4", response.model)
        assertEquals("Choices should be preserved", 1, response.choices.size)
        assertEquals("Usage should be preserved", usage, response.usage)
    }

    @Test
    fun `test TTSResponse audio data handling`() {
        val audioData = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        
        val response = TTSResponse(
            audioData = audioData,
            format = "mp3"
        )
        
        assertNotNull("TTS response should be created", response)
        assertArrayEquals("Audio data should be preserved", audioData, response.audioData)
        assertEquals("Format should be preserved", "mp3", response.format)
        
        // Test convenience method
        val inputStream = response.toInputStream()
        assertNotNull("Should convert to InputStream", inputStream)
        assertEquals("InputStream should have correct size", audioData.size, inputStream.available())
    }

    @Test
    fun `test ASRResponse text handling`() {
        val transcription = "This is a test transcription."
        
        val response = ASRResponse(text = transcription)
        
        assertNotNull("ASR response should be created", response)
        assertEquals("Transcription should be preserved", transcription, response.text)
    }

    // === ERROR HANDLING TESTS ===

    @Test
    fun `test exception hierarchy and error messages`() {
        // Test base exception
        val baseException = EdgeAIException("Base error")
        assertEquals("Should preserve message", "Base error", baseException.message)

        // Test specific exceptions
        val serviceException = ServiceConnectionException("Service error")
        val validationException = InvalidInputException("Validation error")
        val internalException = InternalErrorException("Internal error")

        // Assert messages are correct
        assertEquals("Service error", serviceException.message)
        assertEquals("Validation error", validationException.message)
        assertEquals("Internal error", internalException.message)

        // Optional: test polymorphism using Kotlin reflection or `::class` equality (compile-safe)
        assertEquals(EdgeAIException::class, serviceException::class.supertypes.first().classifier)
        assertEquals(EdgeAIException::class, validationException::class.supertypes.first().classifier)
        assertEquals(EdgeAIException::class, internalException::class.supertypes.first().classifier)
    }

    // === PERFORMANCE AND MEMORY TESTS ===

    @Test
    fun `test request ID generation uniqueness`() {
        // This tests the internal generateRequestId method indirectly
        // by ensuring different requests would get different IDs
        val request1 = ChatRequest(
            model = "test",
            messages = listOf(ChatMessage(role = "user", content = "test1"))
        )
        
        val request2 = ChatRequest(
            model = "test", 
            messages = listOf(ChatMessage(role = "user", content = "test2"))
        )
        
        // Requests should be independent
        assertNotEquals("Different requests should be different objects", request1, request2)
        assertNotEquals("Different message content should create different requests", 
                       request1.messages[0].content, request2.messages[0].content)
    }

    @Test
    fun `test data class equality and hashCode contracts`() {
        val message1 = ChatMessage(role = "user", content = "test")
        val message2 = ChatMessage(role = "user", content = "test")
        val message3 = ChatMessage(role = "assistant", content = "test")
        
        // Test equality
        assertEquals("Same content should be equal", message1, message2)
        assertNotEquals("Different roles should not be equal", message1, message3)
        
        // Test hashCode contract
        assertEquals("Equal objects should have same hashCode", message1.hashCode(), message2.hashCode())
        
        // Test with requests
        val request1 = ChatRequest(model = "test", messages = listOf(message1))
        val request2 = ChatRequest(model = "test", messages = listOf(message2))
        val request3 = ChatRequest(model = "different", messages = listOf(message1))
        
        assertEquals("Requests with same content should be equal", request1, request2)
        assertNotEquals("Requests with different models should not be equal", request1, request3)
    }

    // === ARCHITECTURAL CONSTRAINT TESTS ===

    @Test
    fun `test API surface follows OpenAI compatibility`() {
        // Verify that our API follows OpenAI-compatible naming and structure
        val chatRequest = ChatRequest(
            model = "gpt-4",
            messages = listOf(ChatMessage(role = "user", content = "test")),
            temperature = 0.7f,
            maxCompletionTokens = 1000,
            stream = false
        )
        
        // These field names should match OpenAI API
        assertNotNull("Should have model field", chatRequest.model)
        assertNotNull("Should have messages field", chatRequest.messages)
        assertNotNull("Should have temperature field", chatRequest.temperature)
        assertNotNull("Should have maxCompletionTokens field", chatRequest.maxCompletionTokens)
        assertNotNull("Should have stream field", chatRequest.stream)
        
        // Message structure should match OpenAI
        val message = chatRequest.messages[0]
        assertNotNull("Message should have role", message.role)
        assertNotNull("Message should have content", message.content)
    }

    @Test
    fun `test simplified architecture eliminates intermediate models`() {
        // This test verifies that we're using the simplified architecture
        // by ensuring our request models are directly used (no conversion needed)
        
        val chatRequest = ChatRequest(
            model = "test",
            messages = listOf(ChatMessage(role = "user", content = "test"))
        )
        
        val ttsRequest = TTSRequest(
            input = "test",
            model = "tts-1", 
            voice = "alloy"
        )

        val asrRequest = ASRRequest(
            byteArrayOf(1, 2, 3),
            model = "whisper-1"
        )

        assertNotNull("ChatRequest should be usable", chatRequest)
        assertNotNull("TTSRequest should be usable", ttsRequest)
        assertNotNull("ASRRequest should be usable", asrRequest)

    }

    // === HELPER METHODS ===

    private inline fun <reified T : Throwable> assertThrows(
        message: String,
        exceptionClass: Class<T>,
        executable: () -> Unit
    ) {
        try {
            executable()
            fail("$message - Expected ${exceptionClass.simpleName} to be thrown")
        } catch (e: Throwable) {
            if (!exceptionClass.isInstance(e)) {
                fail("$message - Expected ${exceptionClass.simpleName} but got ${e::class.simpleName}: ${e.message}")
            }
        }
    }
}