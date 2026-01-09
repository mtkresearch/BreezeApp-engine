package com.mtkresearch.breezeapp.edgeai

import android.content.Context
import org.mockito.kotlin.mock

/**
 * Test helper functions for EdgeAI examples.
 * 
 * These builders simplify test code and provide sensible defaults.
 * All example tests use these helpers to create requests and mock objects.
 * 
 * @see ChatExamples
 * @see TTSExamples
 * @see ASRExamples
 * @see SDKLifecycleExamples
 */

// === Context Mocking ===

/**
 * Create a mock Android context for testing.
 * 
 * This is a simple mock that can be used for EdgeAI initialization in tests.
 * For more complex scenarios, use Robolectric's application context.
 */
fun mockContext(): Context = mock()

// === Request Builders ===

/**
 * Build a ChatRequest with sensible defaults.
 * 
 * ## Example
 * ```kotlin
 * val request = chatRequest("Hello, how are you?")
 * val response = EdgeAI.chat(request).first()
 * ```
 * 
 * @param prompt The user's message
 * @param temperature Controls randomness (0.0-2.0, default 0.7)
 * @param maxTokens Maximum tokens to generate (null = no limit)
 * @param stream Whether to stream the response
 */
fun chatRequest(
    prompt: String,
    temperature: Float = 0.7f,
    maxTokens: Int? = null,
    stream: Boolean = false
): ChatRequest {
    return ChatRequest(
        messages = listOf(ChatMessage(role = "user", content = prompt)),
        temperature = temperature,
        maxCompletionTokens = maxTokens,
        stream = stream
    )
}

/**
 * Build a ChatRequest with message history.
 * 
 * ## Example
 * ```kotlin
 * val messages = listOf(
 *     ChatMessage(role = "system", content = "You are a helpful assistant"),
 *     ChatMessage(role = "user", content = "Hello"),
 *     ChatMessage(role = "assistant", content = "Hi! How can I help?"),
 *     ChatMessage(role = "user", content = "Tell me a joke")
 * )
 * val request = chatRequestWithHistory(messages)
 * ```
 */
fun chatRequestWithHistory(
    messages: List<ChatMessage>,
    temperature: Float = 0.7f,
    stream: Boolean = false
): ChatRequest {
    return ChatRequest(
        messages = messages,
        temperature = temperature,
        stream = stream
    )
}

/**
 * DSL for building conversation history.
 * 
 * ## Example
 * ```kotlin
 * val messages = conversation {
 *     system("You are a helpful assistant")
 *     user("Hello")
 *     assistant("Hi! How can I help?")
 *     user("Tell me a joke")
 * }
 * val request = chatRequestWithHistory(messages)
 * ```
 */
fun conversation(block: ConversationBuilder.() -> Unit): List<ChatMessage> {
    return ConversationBuilder().apply(block).messages
}

/**
 * Builder for conversation history using DSL.
 */
class ConversationBuilder {
    val messages = mutableListOf<ChatMessage>()
    
    fun system(content: String) {
        messages.add(ChatMessage(role = "system", content = content))
    }
    
    fun user(content: String) {
        messages.add(ChatMessage(role = "user", content = content))
    }
    
    fun assistant(content: String) {
        messages.add(ChatMessage(role = "assistant", content = content))
    }
}

/**
 * Build a TTSRequest with sensible defaults.
 * 
 * ## Example
 * ```kotlin
 * val request = ttsRequest("Hello, world!")
 * val audioBytes = EdgeAI.tts(request).first()
 * ```
 * 
 * @param input The text to convert to speech
 * @param voice Voice to use (alloy, echo, fable, onyx, nova, shimmer)
 * @param speed Speech speed (0.25-4.0, default 1.0)
 * @param format Audio format (mp3, opus, aac, flac, wav, pcm)
 */
fun ttsRequest(
    input: String,
    voice: String = "alloy",
    speed: Float = 1.0f,
    format: String = "mp3"
): TTSRequest {
    return TTSRequest(
        input = input,
        voice = voice,
        speed = speed,
        responseFormat = format
    )
}

/**
 * Build an ASRRequest with sensible defaults.
 * 
 * ## Example
 * ```kotlin
 * val audioData = ByteArray(1024) { it.toByte() } // Mock audio
 * val request = asrRequest(audioData, language = "en")
 * val transcription = EdgeAI.asr(request).first()
 * ```
 * 
 * @param audioData The audio file as bytes
 * @param language Language code (null = auto-detect)
 * @param responseFormat "json" (simple) or "verbose_json" (detailed)
 */
fun asrRequest(
    audioData: ByteArray,
    language: String? = null,
    responseFormat: String = "json"
): ASRRequest {
    return ASRRequest(
        file = audioData,
        model = "whisper-1",
        language = language,
        responseFormat = responseFormat
    )
}
