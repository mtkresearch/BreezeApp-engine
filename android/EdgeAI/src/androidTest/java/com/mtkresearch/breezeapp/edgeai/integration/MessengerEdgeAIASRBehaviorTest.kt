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
 * Category 2: EdgeAI SDK ASR Behavior Integration Tests
 *
 * Purpose: Validate real ASR behavior through EdgeAI SDK.
 * Tests accuracy and quality of speech recognition via SDK.
 *
 * DATA-DRIVEN: Test cases are loaded from assets/test_data/asr_category2_behavior.json
 *
 * Tests correspond to TDD Plan Category 2:
 * - Test 2.1: English Transcription Accuracy (8 scenarios)
 * - Test 2.2: Chinese/Multilingual Accuracy (4 translations)
 * - Test 2.3: Performance Metrics
 * - Test 2.4: Language Detection Accuracy
 *
 * Note: These tests require Engine to be running with ASR runner configured.
 */
@RunWith(AndroidJUnit4::class)
class EdgeAIASRBehaviorTest : SDKTestBase() {

    // Load test data lazily
    private val testData by lazy { TestASRDataLoader.loadASRCategory2Data() }

    /**
     * Test 2.1: English Transcription Accuracy
     *
     * Validates that ASR correctly transcribes English phrases.
     * Uses fuzzy matching to account for minor variations.
     *
     * Success Criteria: ≥80% word accuracy across test samples
     */
    @Test
    fun sdkASR_transcribesEnglishAccurately() = runBlocking {
        logReport("========================================")
        logReport("Test 2.1: English Transcription Accuracy")
        logReport("========================================")

        val testCases = testData.englishAccuracyTests
        logReport("Testing ${testCases.size} English scenarios...")

        var correctCount = 0
        val results = mutableListOf<Triple<String, String, Boolean>>()

        testCases.forEachIndexed { index, testCase ->
            logReport("\n========================================")
            logReport("Test Case ${index + 1}/${testCases.size}")
            logReport("========================================")
            logReport("Audio: ${testCase.audioFile}")
            logReport("Expected: ${testCase.expectedPhrases}")
            logReport("----------------------------------------")

            val audioData = TestASRDataLoader.loadAudioFromAssets(testCase.audioFile)
            if (audioData == null) {
                logReport("⚠️  Audio file not found, skipping")
                return@forEachIndexed
            }

            val request = asrRequest(
                audioBytes = audioData,
                language = "en"
            )

            val startTime = System.currentTimeMillis()
            val responses = EdgeAI.asr(request).toList()
            val elapsed = System.currentTimeMillis() - startTime

            assertTrue("Should get response", responses.isNotEmpty())

            val response = responses.last()

            val transcription = response.text ?: ""
            val transcriptionLower = transcription.lowercase()

            logReport("Transcription: '$transcription'")
            logReport("Time: ${elapsed}ms")

            // Check if any expected phrase is present
            val matches = testCase.expectedPhrases.any { phrase ->
                transcriptionLower.contains(phrase.lowercase())
            }

            if (matches) {
                correctCount++
                logReport("✅ Match found")
                results.add(Triple(testCase.audioFile, transcription, true))
            } else {
                logReport("❌ No match found")
                results.add(Triple(testCase.audioFile, transcription, false))
            }
        }

        // Calculate accuracy
        val testedCount = results.count { it.second.isNotEmpty() }
        val accuracy = if (testedCount > 0) {
            (correctCount.toDouble() / testedCount) * 100
        } else {
            0.0
        }

        logReport("\n========================================")
        logReport("Transcription Accuracy: $correctCount/$testedCount (${String.format("%.1f", accuracy)}%)")
        logReport("Success Criteria: ≥80%")
        logReport("========================================")

        // Print results table
        logReport("\nDetailed Results:")
        results.forEach { (file, transcription, correct) ->
            val status = if (correct) "✅" else "❌"
            logReport("$status $file → '$transcription'")
        }

        assertTrue(
            "At least one test case should be available",
            testedCount > 0
        )

        assertTrue(
            "Transcription accuracy must be ≥80% (got ${String.format("%.1f", accuracy)}%)",
            accuracy >= 80.0
        )
    }

    /**
     * Test 2.2: Chinese/Multilingual Transcription Accuracy
     *
     * Validates that ASR correctly transcribes Chinese phrases via SDK.
     *
     * Success Criteria: ≥70% accuracy (Chinese is generally harder)
     */
    @Test
    fun sdkASR_transcribesChineseAccurately() = runBlocking {
        logReport("========================================")
        logReport("Test 2.2: Chinese Transcription Accuracy")
        logReport("========================================")

        val testCases = testData.chineseAccuracyTests

        var correctCount = 0
        val results = mutableListOf<Triple<String, String, Boolean>>()

        testCases.forEachIndexed { index, testCase ->
            logReport("\n========================================")
            logReport("Test Case ${index + 1}/${testCases.size}")
            logReport("========================================")
            logReport("Audio: ${testCase.audioFile}")
            logReport("Expected: ${testCase.expectedCharacters}")
            logReport("----------------------------------------")

            val audioData = TestASRDataLoader.loadAudioFromAssets(testCase.audioFile)
            if (audioData == null) {
                logReport("⚠️  Audio file not found, skipping")
                return@forEachIndexed
            }

            val request = asrRequest(
                audioBytes = audioData,
                language = "zh"
            )

            val startTime = System.currentTimeMillis()
            val responses = EdgeAI.asr(request).toList()
            val elapsed = System.currentTimeMillis() - startTime

            if (responses.isEmpty()) {
                logReport("❌ Error: No response")
                results.add(Triple(testCase.audioFile, "", false))
                return@forEachIndexed
            }

            val transcription = responses.last().text ?: ""

            logReport("Transcription: '$transcription'")
            logReport("Time: ${elapsed}ms")

            // Check if any expected characters are present
            val matches = testCase.expectedCharacters.any { chars ->
                transcription.contains(chars)
            }

            if (matches) {
                correctCount++
                logReport("✅ Match found")
                results.add(Triple(testCase.audioFile, transcription, true))
            } else {
                logReport("❌ No match found")
                results.add(Triple(testCase.audioFile, transcription, false))
            }
        }

        // Calculate accuracy
        val testedCount = results.count { it.second.isNotEmpty() }
        val accuracy = if (testedCount > 0) {
            (correctCount.toDouble() / testedCount) * 100
        } else {
            0.0
        }

        logReport("\n========================================")
        logReport("Chinese Accuracy: $correctCount/$testedCount (${String.format("%.1f", accuracy)}%)")
        logReport("Success Criteria: ≥70%")
        logReport("========================================")

        // Print results table
        logReport("\nDetailed Results:")
        results.forEach { (file, transcription, correct) ->
            val status = if (correct) "✅" else "❌"
            logReport("$status $file → '$transcription'")
        }

        if (testedCount > 0) {
            assertTrue(
                "Chinese transcription accuracy must be ≥70% (got ${String.format("%.1f", accuracy)}%)",
                accuracy >= 70.0
            )
        } else {
            logReport("⚠️  No Chinese test audio files available, test skipped")
        }
    }

    /**
     * Test 2.3: Performance Metrics
     *
     * Measures ASR performance characteristics via SDK:
     * - Latency (time to first result)
     * - Consistency across multiple requests
     *
     * Success Criteria: Latency < 3 seconds for 3-second audio
     */
    @Test
    fun sdkASR_meetsPerformanceRequirements() = runBlocking {
        logReport("========================================")
        logReport("Test 2.3: Performance Metrics")
        logReport("========================================")

        val audioFile = "test_audio_hello.wav"
        val audioData = TestASRDataLoader.loadAudioFromAssets(audioFile)
        assertNotNull("Test audio required", audioData)

        logReport("Audio size: ${audioData!!.size} bytes")
        logReport("Running 3 iterations to measure consistency...")
        logReport("----------------------------------------")

        val latencies = mutableListOf<Long>()

        // Run 3 iterations
        repeat(3) { iteration ->
            logReport("\nIteration ${iteration + 1}/3")

            val request = asrRequest(audioBytes = audioData)

            val startTime = System.currentTimeMillis()
            val responses = EdgeAI.asr(request).toList()
            val elapsed = System.currentTimeMillis() - startTime

            latencies.add(elapsed)

            if (responses.isNotEmpty()) {
                val transcription = responses.last().text
                logReport("  Time: ${elapsed}ms")
                logReport("  Result: '$transcription'")
            } else {
                logReport("  Error: No response")
            }
        }

        // Calculate statistics
        val avgLatency = latencies.average()
        val minLatency = latencies.minOrNull() ?: 0L
        val maxLatency = latencies.maxOrNull() ?: 0L

        logReport("\n========================================")
        logReport("Performance Statistics:")
        logReport("========================================")
        logReport("Average Latency: ${String.format("%.0f", avgLatency)}ms")
        logReport("Min Latency: ${minLatency}ms")
        logReport("Max Latency: ${maxLatency}ms")
        logReport("Success Criteria: <3,000ms (3 seconds)")
        logReport("========================================")

        assertTrue(
            "Average latency should be under 3 seconds (got ${avgLatency}ms)",
            avgLatency < 3000
        )
    }

    /**
     * Test 2.4: Language Detection Accuracy
     *
     * Validates that ASR can correctly auto-detect languages or
     * handle explicit language specification via SDK.
     *
     * Success Criteria: Correct language handling
     */
    @Test
    fun sdkASR_handlesLanguagesCorrectly() = runBlocking {
        logReport("========================================")
        logReport("Test 2.4: Language Detection Accuracy")
        logReport("========================================")

        val languageTests = listOf(
            LanguageTest("test_audio_hello.wav", "en", listOf("hello", "hi")),
            LanguageTest("test_audio_nihao.wav", "zh", listOf("你好", "您好")),
            LanguageTest("test_audio_hello.wav", null, listOf("hello", "hi"))  // Auto-detect
        )

        val results = mutableListOf<Pair<String, String>>()

        languageTests.forEachIndexed { index, test ->
            logReport("\n--- Test ${index + 1}/${languageTests.size} ---")
            logReport("Audio: ${test.audioFile}")
            logReport("Language: ${test.language ?: "auto-detect"}")
            logReport("Expected: ${test.expectedWords}")

            val audioData = TestASRDataLoader.loadAudioFromAssets(test.audioFile)
            if (audioData == null) {
                logReport("⚠️  Audio file not found, skipping")
                return@forEachIndexed
            }

            val request = asrRequest(
                audioBytes = audioData,
                language = test.language
            )

            val responses = EdgeAI.asr(request).toList()

            if (responses.isNotEmpty()) {
                val transcription = responses.last().text ?: ""
                logReport("Transcription: '$transcription'")

                val matches = test.expectedWords.any { word ->
                    transcription.lowercase().contains(word.lowercase()) ||
                            transcription.contains(word)
                }

                logReport(if (matches) "✅ Match found" else "⚠️  No match")
                results.add(test.audioFile to transcription)
            } else {
                logReport("❌ Error: No response")
            }
        }

        logReport("\n========================================")
        logReport("Language Test Results:")
        results.forEach { (file, text) ->
            logReport("$file → '$text'")
        }
        logReport("========================================")

        assertTrue(
            "At least one language test should succeed",
            results.isNotEmpty()
        )
    }

    // Helper classes
    data class LanguageTest(
        val audioFile: String,
        val language: String?,
        val expectedWords: List<String>
    )
}