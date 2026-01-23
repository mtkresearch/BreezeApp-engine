package com.mtkresearch.breezeapp.edgeai.examples

import com.mtkresearch.breezeapp.edgeai.*
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
 * TTS (Text-to-Speech) API examples for EdgeAI SDK.
 *
 * Complete examples for the **TTS** API including basic usage, different voices,
 * audio formats, streaming (if supported), and error handling.
 *
 * ## Topics Covered
 * - Basic text-to-speech
 * - Different voices (alloy, echo, fable, onyx, nova, shimmer)
 * - Different audio formats (mp3, opus, aac, flac, wav, pcm)
 * - Speech speed control
 * - Error handling
 *
 * ## For Client Developers
 * Read these examples to learn how to use the TTS API.
 * Each example shows a specific TTS use case.
 *
 * ## For Maintainers
 * When modifying the TTS API, update these examples.
 * All examples must pass CI.
 *
 * @see EdgeAI.tts
 * @see TTSRequest
 * @see TTSResponse
 * @see ChatExamples for chat API
 * @see ASRExamples for speech-to-text
 */
@RunWith(RobolectricTestRunner::class)
class TTSExamples : EdgeAITestBase() {
    // setUp/tearDown inherited from EdgeAITestBase

    /**
     * Example 01: Basic text-to-speech
     *
     * Shows the simplest way to convert text to speech.
     *
     * ## When to use
     * - Voice assistants
     * - Accessibility features
     * - Audio content generation
     * - Reading text aloud
     *
     * ## Input format
     * - `input`: Text to convert (string)
     * - `voice`: Voice name (default: "alloy")
     * - `speed`: Speech speed (0.25-4.0, default: 1.0)
     * - `format`: Audio format (default: "mp3")
     *
     * ## Output format
     * - `TTSResponse` with `audio` field (ByteArray)
     * - Audio data in specified format
     * - Ready to play or save
     *
     * @see ttsRequest builder
     */
    @Test
    fun `01 - basic text to speech`() = runTest {
        // Create TTS request
        val request = ttsRequest(
            input = "Hello, this is a test of text to speech.",
            voice = "alloy",
            speed = 1.0f,
            format = "mp3"
        )

        // Get audio response
        val response = EdgeAI.tts(request).first()

        // EdgeAI uses engine-side playback, client doesn't receive audio data
        assertNotNull("Should have response", response)
        assertEquals("Should use engine playback", "engine_playback", response.format)
        println("TTS completed with engine playback mode")
    }

    /**
     * Example 02: Different voices
     *
     * Shows how to use different voice options.
     *
     * ## Available voices
     * - `alloy`: Neutral, balanced
     * - `echo`: Clear, expressive
     * - `fable`: Warm, storytelling
     * - `onyx`: Deep, authoritative
     * - `nova`: Bright, energetic
     * - `shimmer`: Soft, gentle
     *
     * ## When to use different voices
     * - Assistants: alloy, echo
     * - Storytelling: fable
     * - Announcements: onyx
     * - Friendly apps: nova, shimmer
     *
     */
    @Test
    fun `02 - different voices`() = runTest {
        val text = "This is a voice test."
        val voices = listOf("alloy", "echo", "fable", "onyx", "nova", "shimmer")

        voices.forEach { voice ->
            val request = ttsRequest(
                input = text,
                voice = voice,
                format = "mp3"
            )

            val response = EdgeAI.tts(request).first()

            assertNotNull("$voice should generate response", response)
            assertEquals("Should use engine playback", "engine_playback", response.format)
            println("$voice: engine playback mode")
        }
    }

    /**
     * Example 03: Different audio formats
     *
     * Shows how to generate audio in different formats.
     *
     * ## Supported formats
     * - `mp3`: Compressed, good quality, widely supported
     * - `opus`: Compressed, best for streaming
     * - `aac`: Compressed, good for mobile
     * - `flac`: Lossless, large file size
     * - `wav`: Uncompressed, large file size
     * - `pcm`: Raw audio data
     *
     * ## When to use each format
     * - Web/mobile: mp3, opus, aac
     * - High quality: flac, wav
     * - Processing: pcm
     *
     */
    @Test
    fun `03 - different audio formats`() = runTest {
        val text = "Testing audio formats."
        val formats = listOf("mp3", "opus", "aac", "flac", "wav", "pcm")

        formats.forEach { format ->
            val request = ttsRequest(
                input = text,
                voice = "alloy",
                format = format
            )

            val response = EdgeAI.tts(request).first()

            assertNotNull("$format should generate response", response)
            assertEquals("Should use engine playback", "engine_playback", response.format)
            println("$format: engine playback mode")
        }
    }

    /**
     * Example 04: Speech speed control
     *
     * Shows how to control the speed of speech.
     *
     * ## Speed range
     * - Minimum: 0.25 (very slow)
     * - Default: 1.0 (normal)
     * - Maximum: 4.0 (very fast)
     *
     * ## When to adjust speed
     * - Accessibility: 0.5-0.75 (slower for clarity)
     * - Quick playback: 1.5-2.0 (faster for efficiency)
     * - Dramatic effect: 0.25 or 4.0 (extreme speeds)
     *
     */
    @Test
    fun `04 - speech speed control`() = runTest {
        val text = "This is a speed test."
        val speeds = listOf(0.5f, 1.0f, 1.5f, 2.0f)

        speeds.forEach { speed ->
            val request = ttsRequest(
                input = text,
                voice = "alloy",
                speed = speed,
                format = "mp3"
            )

            val response = EdgeAI.tts(request).first()

            assertNotNull("Speed $speed should generate response", response)
            println("Speed $speed: engine playback mode")
        }
    }

    /**
     * Example 05: Error handling
     *
     * Shows how to handle TTS API errors gracefully.
     *
     * ## Common errors
     * - `ServiceConnectionException`: BreezeApp Engine unavailable
     * - `InvalidInputException`: Invalid parameters (empty input, invalid voice)
     * - `TimeoutException`: Audio generation took too long
     *
     * ## Recovery strategies
     * - Service error: Show installation dialog
     * - Invalid input: Validate before sending
     * - Timeout: Retry or use shorter text
     *
     */
    @Test
    fun `05 - error handling`() = runTest {
        val request = ttsRequest(
            input = "Hello, world!",
            voice = "alloy"
        )

        EdgeAI.tts(request)
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
                        // Retry or use shorter text
                    }
                    else -> {
                        println("✗ Unexpected error: ${error.message}")
                    }
                }
            }
            .collect { response ->
                println("✓ Success: ${response.audioData.size} bytes")
            }
    }

    /**
     * Example 06: Long text handling
     *
     * Shows how to handle long text input.
     *
     * ## Best practices for long text
     * - Split into chunks (< 4096 characters each)
     * - Generate audio for each chunk
     * - Concatenate audio files
     * - Show progress to user
     *
     * ## Chunking strategies
     * - By sentences
     * - By paragraphs
     * - By character limit
     *
     */
    @Test
    fun `06 - long text handling`() = runTest {
        val longText = """
            This is a long text that might need to be split into chunks.
            Each chunk should be processed separately.
            Then the audio files can be concatenated.
        """.trimIndent()

        // Split into sentences
        val chunks = longText.split(". ").filter { it.isNotBlank() }

        var successfulChunks = 0

        chunks.forEachIndexed { index, chunk ->
            val request = ttsRequest(
                input = chunk,
                voice = "alloy",
                format = "mp3"
            )

            val response = EdgeAI.tts(request).first()
            // EdgeAI uses engine playback, verify response received
            assertNotNull("Should have response for chunk ${index + 1}", response)
            successfulChunks++

            println("Chunk ${index + 1}/${chunks.size}: engine playback mode")
        }

        // Verify all chunks processed
        assertEquals("Should process ${chunks.size} chunks", chunks.size, successfulChunks)
    }

    // === HELPER FUNCTIONS ===

    private fun mockContext(): android.content.Context {
        return org.mockito.Mockito.mock(android.content.Context::class.java)
    }
}
