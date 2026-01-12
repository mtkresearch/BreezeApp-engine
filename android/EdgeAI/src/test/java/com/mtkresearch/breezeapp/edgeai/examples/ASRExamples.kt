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
 * ASR (Speech-to-Text) API examples for EdgeAI SDK.
 *
 * Complete examples for the **ASR** API including basic usage, different languages,
 * response formats, streaming (if supported), and error handling.
 *
 * ## Topics Covered
 * - Basic speech recognition
 * - Different languages (auto-detect vs explicit)
 * - Response formats (json vs verbose_json)
 * - Word-level timestamps
 * - Error handling
 *
 * ## For Client Developers
 * Read these examples to learn how to use the ASR API.
 * Each example shows a specific ASR use case.
 *
 * ## For Maintainers
 * When modifying the ASR API, update these examples.
 * All examples must pass CI.
 *
 * @see EdgeAI.asr
 * @see ASRRequest
 * @see ASRResponse
 * @see ChatExamples for chat API
 * @see TTSExamples for text-to-speech
 */
@RunWith(RobolectricTestRunner::class)
class ASRExamples : EdgeAITestBase() {
    // setUp/tearDown inherited from EdgeAITestBase

    /**
     * Example 01: Basic speech recognition
     *
     * Shows the simplest way to transcribe audio to text.
     *
     * ## When to use
     * - Voice input
     * - Transcription services
     * - Voice commands
     * - Dictation
     *
     * ## Input format
     * - `file`: Audio data as ByteArray
     * - `language`: Language code (optional, auto-detected if not specified)
     * - `responseFormat`: "json" (simple) or "verbose_json" (detailed)
     *
     * ## Output format
     * - `ASRResponse` with `text` field (transcribed text)
     * - Optional: `language`, `duration`, `words` (if verbose_json)
     *
     * @see asrRequest builder
     */
    @Test
    fun `01 - basic speech recognition`() = runTest {
        // Mock audio data (in real app, this would be from microphone)
        val audioBytes = ByteArray(1024) { it.toByte() }

        // Create ASR request
        val request = asrRequest(
            audioData = audioBytes,
            language = "en",  // English
            responseFormat = "json"
        )

        // Get transcription
        val response = EdgeAI.asr(request).first()

        // Verify transcription
        assertNotNull("Should have transcription", response.text)
        println("Transcription: ${response.text}")
    }

    /**
     * Example 02: Auto language detection
     *
     * Shows how to let the API automatically detect the language.
     *
     * ## When to use
     * - Multi-language apps
     * - Unknown input language
     * - International users
     *
     * ## How it works
     * - Don't specify `language` parameter
     * - API detects language automatically
     * - Use `verbose_json` to see detected language
     *
     * ## Supported languages
     * - English, Spanish, French, German, Chinese, Japanese, etc.
     * - See OpenAI Whisper documentation for full list
     *
     */
    @Test
    fun `02 - auto language detection`() = runTest {
        val audioBytes = ByteArray(1024) { it.toByte() }

        // Don't specify language - let API detect
        val request = ASRRequest(
            _file = audioBytes,
            model = "whisper-1",
            responseFormat = "verbose_json"  // Get language info
        )

        val response = EdgeAI.asr(request).first()

        assertNotNull("Should have transcription", response.text)
        assertNotNull("Should detect language", response.language)
        println("Detected language: ${response.language}")
        println("Transcription: ${response.text}")
    }

    /**
     * Example 03: Different languages
     *
     * Shows how to transcribe audio in different languages.
     *
     * ## When to specify language
     * - Better accuracy for known language
     * - Faster processing
     * - Avoid misdetection
     *
     * ## Common language codes
     * - `en`: English
     * - `es`: Spanish
     * - `fr`: French
     * - `de`: German
     * - `zh`: Chinese
     * - `ja`: Japanese
     *
     */
    @Test
    fun `03 - different languages`() = runTest {
        val audioBytes = ByteArray(1024) { it.toByte() }
        val languages = listOf("en", "es", "fr", "de", "zh", "ja")

        languages.forEach { lang ->
            val request = asrRequest(
                audioData = audioBytes,
                language = lang,
                responseFormat = "json"
            )

            val response = EdgeAI.asr(request).first()

            assertNotNull("$lang should have transcription", response.text)
            println("$lang: ${response.text}")
        }
    }

    /**
     * Example 04: Response formats
     *
     * Shows the difference between json and verbose_json formats.
     *
     * ## json format
     * - Simple text only
     * - Faster processing
     * - Smaller response
     *
     * ## verbose_json format
     * - Text + metadata
     * - Language detection
     * - Duration
     * - Word-level timestamps
     * - Confidence scores
     *
     * ## When to use verbose_json
     * - Need timestamps for subtitles
     * - Want language detection
     * - Need confidence scores
     *
     */
    @Test
    fun `04 - response formats`() = runTest {
        val audioBytes = ByteArray(1024) { it.toByte() }

        // Simple format
        val jsonRequest = asrRequest(
            audioData = audioBytes,
            responseFormat = "json"
        )
        val jsonResponse = EdgeAI.asr(jsonRequest).first()
        println("JSON format:")
        println("  Text: ${jsonResponse.text}")

        // Verbose format
        val verboseRequest = asrRequest(
            audioData = audioBytes,
            responseFormat = "verbose_json"
        )
        val verboseResponse = EdgeAI.asr(verboseRequest).first()
        println("Verbose JSON format:")
        println("  Text: ${verboseResponse.text}")
        println("  Language: ${verboseResponse.language}")
        println("  Segments: ${verboseResponse.segments?.size ?: 0}")
    }

    /**
     * Example 05: Word-level timestamps
     *
     * Shows how to get timestamps for each word (for subtitles, etc.).
     *
     * ## When to use
     * - Generating subtitles
     * - Syncing text with audio
     * - Word-by-word highlighting
     * - Karaoke-style display
     *
     * ## How to get timestamps
     * - Use `responseFormat = "verbose_json"`
     * - Access `response.segments` array
     * - Each word has `word`, `start`, `end` fields
     *
     */
    @Test
    fun `05 - word level timestamps`() = runTest {
        val audioBytes = ByteArray(1024) { it.toByte() }

        val request = asrRequest(
            audioData = audioBytes,
            responseFormat = "verbose_json"  // Required for timestamps
        )

        val response = EdgeAI.asr(request).first()

        // Process segment timestamps
        response.segments?.forEachIndexed { index, segment ->
            println("Segment ${index + 1}: '${segment.text}'")
        }

        assertNotNull("Should have segments", response.segments)
    }

    /**
     * Example 06: Error handling
     *
     * Shows how to handle ASR API errors gracefully.
     *
     * ## Common errors
     * - `ServiceConnectionException`: BreezeApp Engine unavailable
     * - `InvalidInputException`: Invalid audio format, empty audio
     * - `TimeoutException`: Transcription took too long
     *
     * ## Recovery strategies
     * - Service error: Show installation dialog
     * - Invalid audio: Validate format before sending
     * - Timeout: Retry with shorter audio
     *
     */
    @Test
    fun `06 - error handling`() = runTest {
        val audioBytes = ByteArray(1024) { it.toByte() }

        val request = asrRequest(audioData = audioBytes)

        EdgeAI.asr(request)
            .catch { error ->
                when (error) {
                    is ServiceConnectionException -> {
                        println("⚠ Service unavailable")
                        // Show installation dialog
                    }
                    is InvalidInputException -> {
                        println("⚠ Invalid audio: ${error.message}")
                        // Validate audio format
                    }
                    is TimeoutException -> {
                        println("⚠ Request timeout")
                        // Retry with shorter audio
                    }
                    else -> {
                        println("✗ Unexpected error: ${error.message}")
                    }
                }
            }
            .collect { response ->
                println("✓ Success: ${response.text}")
            }
    }

    // === HELPER FUNCTIONS ===

    private fun mockContext(): android.content.Context {
        return org.mockito.Mockito.mock(android.content.Context::class.java)
    }
}
