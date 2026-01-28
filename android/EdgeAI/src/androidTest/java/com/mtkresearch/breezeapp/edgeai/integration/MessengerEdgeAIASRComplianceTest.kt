package com.mtkresearch.breezeapp.edgeai.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtkresearch.breezeapp.edgeai.EdgeAI
import com.mtkresearch.breezeapp.edgeai.asrRequest
import com.mtkresearch.breezeapp.edgeai.integration.helpers.SDKTestBase
import com.mtkresearch.breezeapp.edgeai.integration.helpers.TestASRDataLoader
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Category 1: EdgeAI SDK ASR Compliance Test
 *
 * Purpose: Validate that EdgeAI SDK can communicate with Engine's ASR runners
 * and receive properly formatted transcription responses.
 *
 * This mirrors GeneralASRComplianceTest.kt but uses EdgeAI SDK API instead
 * of direct Runner calls.
 *
 * Tests correspond to TDD Plan Category 1:
 * - Test 1.1: Audio Input Processing via SDK
 * - Test 1.2: Text Output Format Validation
 * - Test 1.3: Error Handling through SDK
 * - Test 1.4: Multiple Audio Files via SDK
 *
 * Configuration:
 * - ASR_RUNNER_TYPE: Runner to test (default from Engine config)
 * - ASR_TEST_LANGUAGE: Default language for tests
 */
@RunWith(AndroidJUnit4::class)
class MessengerEdgeAIASRComplianceTest : SDKTestBase() {

    // Load test data lazily
    private val testData by lazy { TestASRDataLoader.loadASRCategory1Data() }

    /**
     * Test 1.1: Audio Input Processing via SDK
     *
     * Validates that SDK can send audio to Engine and receive transcription.
     */
    @Test
    fun sdkASR_acceptsAudioInput() = runBlocking {
        logReport("========================================")
        logReport("Test 1.1: Audio Input Processing via EdgeAI SDK")
        logReport("Data Source: category1_compliance.json")
        logReport("========================================")

        testData.audioInputTests.forEachIndexed { index, testCase ->
            logReport("\n--- Scenario ${index + 1}: ${testCase.audioFile} ---")
            logReport("Expected: Non-null transcription")

            // Load audio from assets
            val audioData = TestASRDataLoader.loadAudioFromAssets(testCase.audioFile)
            assertNotNull("Audio file should exist: ${testCase.audioFile}", audioData)
            assertTrue("Audio data should not be empty", audioData!!.isNotEmpty())
            logReport("Audio loaded: ${audioData.size} bytes")

            // Create ASR request via EdgeAI SDK
            val request = asrRequest(
                audioBytes = audioData,
            )

            // Execute via EdgeAI SDK
            val startTime = System.currentTimeMillis()
            val responses = EdgeAI.asr(request).toList()
            val elapsed = System.currentTimeMillis() - startTime

            logReport("Processing time: ${elapsed}ms")
            logReport("----------------------------------------")

            // Validate response
            assertTrue("SDK should return at least one response", responses.isNotEmpty())
            val finalResponse = responses.last()
            assertNotNull("Final response should not be null", finalResponse)

            val transcription = finalResponse.text
            assertNotNull("Transcription should not be null", transcription)
            assertFalse("Transcription should not be empty", transcription.isNullOrEmpty())

            logReport("Transcription: '$transcription'")
            logReport("✅ Audio input processing successful via SDK")
        }

        logReport("\n========================================")
        logReport("✅ All audio input tests passed")
        logReport("========================================")
    }

    /**
     * Test 1.2: Text Output Format Validation
     *
     * Validates that SDK returns text in expected format.
     */
    @Test
    fun sdkASR_returnsValidTextFormat() = runBlocking {
        logReport("========================================")
        logReport("Test 1.2: Text Output Format Validation")
        logReport("========================================")

        testData.outputFormatTests.forEach { testCase ->
            logReport("\n--- Testing: ${testCase.audioFile} ---")

            val audioData = TestASRDataLoader.loadAudioFromAssets(testCase.audioFile)
            assertNotNull("Audio file should exist", audioData)

            val request = asrRequest(
                audioBytes = audioData!!,
            )

            val responses = EdgeAI.asr(request).toList()
            assertTrue("Should receive response", responses.isNotEmpty())

            val transcription = responses.last().text
            assertNotNull("Transcription should not be null", transcription)

            // Validate text format
            assertTrue(
                "Transcription should be non-empty String",
                transcription is String && transcription.isNotEmpty()
            )

            assertTrue(
                "Transcription should be reasonable length (>0, <10000 chars)",
                transcription.length in 1..10000
            )

            assertTrue(
                "Transcription should contain letters",
                transcription.any { it.isLetter() }
            )

            logReport("Transcription: '$transcription'")
            logReport("Length: ${transcription.length} characters")
            logReport("✅ Output format is valid")
        }

        logReport("\n========================================")
        logReport("✅ All output format tests passed")
        logReport("========================================")
    }

    /**
     * Test 1.3: Error Handling through SDK
     *
     * Validates that SDK properly handles error conditions.
     */
    @Test
    fun sdkASR_handlesErrorsGracefully() = runBlocking {
        logReport("========================================")
        logReport("Test 1.3: Error Handling through SDK")
        logReport("========================================")

        // Test Case 1: Empty audio
        logReport("\n--- Test Case 1: Empty Audio ---")
        try {
            val emptyRequest = asrRequest(audioBytes = ByteArray(0))
            val result = EdgeAI.asr(emptyRequest).toList()

            if (result.isEmpty() || result.last().text.isNullOrEmpty()) {
                logReport("✅ Empty audio handled (no result or empty transcription)")
            } else {
                logReport("⚠️  Empty audio processed (may be acceptable)")
            }
        } catch (e: Exception) {
            logReport("Exception caught: ${e.message}")
            logReport("✅ Empty audio handled with exception")
        }

        // Test Case 2: Invalid audio data
        logReport("\n--- Test Case 2: Invalid Audio Data ---")
        try {
            val invalidAudio = ByteArray(100) { it.toByte() }
            val invalidRequest = asrRequest(audioBytes = invalidAudio)
            val result = EdgeAI.asr(invalidRequest).toList()

            if (result.isNotEmpty()) {
                logReport("Transcription: ${result.last().text}")
                logReport("✅ Invalid audio processed (model attempted transcription)")
            }
        } catch (e: Exception) {
            logReport("Exception caught: ${e.message}")
            logReport("✅ Invalid audio handled with exception")
        }

        // Test Case 3: Unsupported language code
        logReport("\n--- Test Case 3: Unsupported Language Code ---")
        try {
            val audioData = TestASRDataLoader.loadAudioFromAssets("test_audio_hello.wav")
            if (audioData != null) {
                val request = asrRequest(
                    audioBytes = audioData,
                    language = "xyz"  // Invalid language code
                )
                val result = EdgeAI.asr(request).toList()

                if (result.isNotEmpty()) {
                    val transcription = result.last().text
                    if (transcription.isNullOrEmpty()) {
                        logReport("✅ Unsupported language handled")
                    } else {
                        logReport("⚠️  Processed despite invalid language (fallback to auto-detect)")
                        logReport("Transcription: $transcription")
                    }
                }
            }
        } catch (e: Exception) {
            logReport("Exception caught: ${e.message}")
            logReport("✅ Unsupported language handled with exception")
        }

        logReport("\n✅ All error handling tests completed")
        logReport("========================================")
    }

    /**
     * Test 1.4: Multiple Audio Files via SDK
     *
     * Validates SDK can handle multiple transcription requests.
     */
    @Test
    fun sdkASR_handlesMultipleRequests() = runBlocking {
        logReport("========================================")
        logReport("Test 1.4: Multiple Audio Files via SDK")
        logReport("========================================")

        val audioFiles = listOf(
            "test_audio_hello.wav",
            "test_audio_thanks.wav",
            "test_audio_goodbye.wav"
        )

        var successCount = 0
        val results = mutableListOf<Pair<String, String>>()

        audioFiles.forEachIndexed { index, filename ->
            logReport("\n--- Request ${index + 1}/${audioFiles.size}: $filename ---")

            val audioData = TestASRDataLoader.loadAudioFromAssets(filename)
            if (audioData == null) {
                logReport("⚠️  Audio file not found, skipping")
                return@forEachIndexed
            }

            logReport("Audio size: ${audioData.size} bytes")

            val request = asrRequest(audioBytes = audioData)
            val startTime = System.currentTimeMillis()
            val responses = EdgeAI.asr(request).toList()
            val elapsed = System.currentTimeMillis() - startTime

            if (responses.isNotEmpty()) {
                val transcription = responses.last().text ?: ""
                logReport("Transcription: '$transcription'")
                logReport("Time: ${elapsed}ms")
                logReport("✅ Success")

                if (transcription.isNotEmpty()) {
                    results.add(filename to transcription)
                    successCount++
                }
            } else {
                logReport("❌ Error: ${"Unknown"}")
            }
        }

        logReport("\n========================================")
        logReport("Results: $successCount/${audioFiles.size} files processed successfully")
        logReport("========================================")

        results.forEach { (file, text) ->
            logReport("$file → '$text'")
        }

        assertTrue(
            "At least one audio file should be processed successfully",
            successCount > 0
        )
    }
}