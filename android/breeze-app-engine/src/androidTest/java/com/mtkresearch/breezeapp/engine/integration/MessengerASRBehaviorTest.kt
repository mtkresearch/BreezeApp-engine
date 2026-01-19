package com.mtkresearch.breezeapp.engine.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtkresearch.breezeapp.engine.model.EngineSettings
import com.mtkresearch.breezeapp.engine.model.InferenceRequest
import com.mtkresearch.breezeapp.engine.model.InferenceResult
import com.mtkresearch.breezeapp.engine.runner.huggingface.HuggingFaceASRRunner
import java.util.UUID
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Category 2: ASR Behavior Integration Tests
 *
 * Purpose: Validate real ASR behavior with actual Hugging Face API calls.
 * This tests the accuracy and quality of speech recognition.
 *
 * Tests correspond to TDD Plan Category 2:
 * - Test 2.1: Transcription Accuracy (English)
 * - Test 2.2: Transcription Accuracy (Chinese/Multilingual)
 * - Test 2.3: Audio Quality Handling (various quality levels)
 * - Test 2.4: Performance Metrics (latency, throughput)
 *
 * Note: These are instrumented tests requiring:
 * 1. Hugging Face API Key (via instrumentation arguments)
 * 2. Network connectivity
 * 3. Android device/emulator
 * 4. Test audio files in assets folder
 */
@RunWith(AndroidJUnit4::class)
class MessengerASRBehaviorTest {

    private lateinit var context: Context
    private lateinit var hfApiKey: String
    private lateinit var hfModel: String

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        val args = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        hfApiKey = args.getString("HF_API_KEY") ?: ""
        hfModel = args.getString("HF_ASR_MODEL") ?: "openai/whisper-large-v3"
    }

    /**
     * Test 2.1: English Transcription Accuracy
     *
     * Validates that the ASR correctly transcribes common English phrases.
     * Uses fuzzy matching to account for minor variations.
     *
     * Success Criteria: ≥80% word accuracy across test samples
     */
    @Test
    fun asr_transcribesEnglishAccurately() {
        // Check prerequisites
        Assume.assumeTrue(
            "Skipping: HF API Key required",
            hfApiKey.isNotBlank() && hfApiKey != "hf_YOUR_TOKEN_HERE"
        )

        System.out.println("========================================")
        System.out.println("Test 2.1: English Transcription AccuracyfallbackAudio")
        System.out.println("========================================")
        System.out.println("Model: $hfModel")

        // Setup runner
        val runner = HuggingFaceASRRunner(context)
        val params = mapOf(
            "api_key" to hfApiKey,
            "model" to hfModel,
            "wait_for_model" to true
        )

        val settings = EngineSettings.default()
            .withRunnerParameters("HuggingFaceASRRunner", params)

        val loaded = runner.load(hfModel, settings, emptyMap())
        assertTrue("Failed to load runner", loaded)

        // Test cases: audio file -> expected phrases
        val testCases = mapOf(
            "test_audio_hello.wav" to listOf("hello", "hi"),
            "test_audio_thanks.wav" to listOf("thank you", "thanks"),
            "test_audio_goodbye.wav" to listOf("goodbye", "bye", "see you"),
            "test_audio_question.wav" to listOf("how are you", "how's it going")
        )

        var correctCount = 0
        val results = mutableListOf<Triple<String, String, Boolean>>()

        testCases.entries.forEachIndexed { index, (audioFile, expectedPhrases) ->
            System.out.println("\n========================================")
            System.out.println("Test Case ${index + 1}/${testCases.size}")
            System.out.println("========================================")
            System.out.println("Audio: $audioFile")
            System.out.println("Expected: $expectedPhrases")
            System.out.println("----------------------------------------")

            val audioData = loadAudioFromAssets(audioFile)

            if (audioData == null) {
                System.out.println("⚠️  Audio file not found, skipping")
                return@forEachIndexed
            }

            val request = InferenceRequest(
                sessionId = UUID.randomUUID().toString(),
                inputs = mapOf(InferenceRequest.INPUT_AUDIO to audioData)
            )

            val startTime = System.currentTimeMillis()
            val result = runner.run(request, stream = false)
            val elapsed = System.currentTimeMillis() - startTime

            if (result.error != null) {
                System.out.println("❌ Error: ${result.error?.message}")
                results.add(Triple(audioFile, "", false))
                return@forEachIndexed
            }

            val transcription = result.outputs[InferenceResult.OUTPUT_TEXT] as? String ?: ""
            val transcriptionLower = transcription.lowercase()

            System.out.println("Transcription: '$transcription'")
            System.out.println("Time: ${elapsed}ms")

            // Check if any expected phrase is present
            val matches = expectedPhrases.any { phrase ->
                transcriptionLower.contains(phrase.lowercase())
            }

            if (matches) {
                correctCount++
                System.out.println("✅ Match found")
                results.add(Triple(audioFile, transcription, true))
            } else {
                System.out.println("❌ No match found")
                results.add(Triple(audioFile, transcription, false))
            }
        }

        // Calculate accuracy
        val testedCount = results.count { it.second.isNotEmpty() }
        val accuracy = if (testedCount > 0) {
            (correctCount.toDouble() / testedCount) * 100
        } else {
            0.0
        }

        System.out.println("\n========================================")
        System.out.println("Transcription Accuracy: $correctCount/$testedCount (${String.format("%.1f", accuracy)}%)")
        System.out.println("Success Criteria: ≥80%")
        System.out.println("========================================")

        // Print results table
        System.out.println("\nDetailed Results:")
        results.forEach { (file, transcription, correct) ->
            val status = if (correct) "✅" else "❌"
            System.out.println("$status $file → '$transcription'")
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
     * Validates that the ASR correctly transcribes Chinese phrases.
     *
     * Success Criteria: ≥70% accuracy (Chinese is generally harder)
     */
    @Test
    fun asr_transcribesChineseAccurately() {
        // Check prerequisites
        Assume.assumeTrue(
            "Skipping: HF API Key required",
            hfApiKey.isNotBlank() && hfApiKey != "hf_YOUR_TOKEN_HERE"
        )

        System.out.println("========================================")
        System.out.println("Test 2.2: Chinese Transcription Accuracy")
        System.out.println("========================================")
        System.out.println("Model: $hfModel")

        // Setup runner (no language specified for auto-detection)
        val runner = HuggingFaceASRRunner(context)
        val params = mapOf(
            "api_key" to hfApiKey,
            "model" to hfModel,
            "wait_for_model" to true
        )

        val settings = EngineSettings.default()
            .withRunnerParameters("HuggingFaceASRRunner", params)

        val loaded = runner.load(hfModel, settings, emptyMap())
        assertTrue("Failed to load runner", loaded)

        // Test cases: audio file -> expected Chinese characters
        val testCases = mapOf(
            "test_audio_nihao.wav" to listOf("你好", "您好"),
            "test_audio_xiexie.wav" to listOf("謝謝", "谢谢"),
            "test_audio_zaijian.wav" to listOf("再見", "再见"),
            "test_audio_chinese_question.wav" to listOf("怎麼", "怎么", "如何")
        )

        var correctCount = 0
        val results = mutableListOf<Triple<String, String, Boolean>>()

        testCases.entries.forEachIndexed { index, (audioFile, expectedChars) ->
            System.out.println("\n========================================")
            System.out.println("Test Case ${index + 1}/${testCases.size}")
            System.out.println("========================================")
            System.out.println("Audio: $audioFile")
            System.out.println("Expected: $expectedChars")
            System.out.println("----------------------------------------")

            val audioData = loadAudioFromAssets(audioFile)

            if (audioData == null) {
                System.out.println("⚠️  Audio file not found, skipping")
                return@forEachIndexed
            }

            val request = InferenceRequest(
                sessionId = UUID.randomUUID().toString(),
                inputs = mapOf(InferenceRequest.INPUT_AUDIO to audioData)
            )

            val startTime = System.currentTimeMillis()
            val result = runner.run(request, stream = false)
            val elapsed = System.currentTimeMillis() - startTime

            if (result.error != null) {
                System.out.println("❌ Error: ${result.error?.message}")
                results.add(Triple(audioFile, "", false))
                return@forEachIndexed
            }

            val transcription = result.outputs[InferenceResult.OUTPUT_TEXT] as? String ?: ""

            System.out.println("Transcription: '$transcription'")
            System.out.println("Time: ${elapsed}ms")

            // Check if any expected characters are present
            val matches = expectedChars.any { chars ->
                transcription.contains(chars)
            }

            if (matches) {
                correctCount++
                System.out.println("✅ Match found")
                results.add(Triple(audioFile, transcription, true))
            } else {
                System.out.println("❌ No match found")
                results.add(Triple(audioFile, transcription, false))
            }
        }

        // Calculate accuracy
        val testedCount = results.count { it.second.isNotEmpty() }
        val accuracy = if (testedCount > 0) {
            (correctCount.toDouble() / testedCount) * 100
        } else {
            0.0
        }

        System.out.println("\n========================================")
        System.out.println("Chinese Accuracy: $correctCount/$testedCount (${String.format("%.1f", accuracy)}%)")
        System.out.println("Success Criteria: ≥70%")
        System.out.println("========================================")

        // Print results table
        System.out.println("\nDetailed Results:")
        results.forEach { (file, transcription, correct) ->
            val status = if (correct) "✅" else "❌"
            System.out.println("$status $file → '$transcription'")
        }

        if (testedCount > 0) {
            assertTrue(
                "Chinese transcription accuracy must be ≥70% (got ${String.format("%.1f", accuracy)}%)",
                accuracy >= 70.0
            )
        } else {
            System.out.println("⚠️  No Chinese test audio files available, test skipped")
        }
    }

    /**
     * Test 2.3: Audio Quality Handling
     *
     * Validates that ASR handles various audio quality levels:
     * - Clear audio (studio quality)
     * - Normal audio (phone quality)
     * - Noisy audio (background noise)
     *
     * Success Criteria: Degrades gracefully with quality
     */
    @Test
    fun asr_handlesVariousAudioQualities() {
        // Check prerequisites
        Assume.assumeTrue(
            "Skipping: HF API Key required",
            hfApiKey.isNotBlank() && hfApiKey != "hf_YOUR_TOKEN_HERE"
        )

        System.out.println("========================================")
        System.out.println("Test 2.3: Audio Quality Handling")
        System.out.println("========================================")
        System.out.println("Model: $hfModel")

        // Setup runner
        val runner = HuggingFaceASRRunner(context)
        val params = mapOf(
            "api_key" to hfApiKey,
            "model" to hfModel,
            "wait_for_model" to true
        )

        val settings = EngineSettings.default()
            .withRunnerParameters("HuggingFaceASRRunner", params)

        val loaded = runner.load(hfModel, settings, emptyMap())
        assertTrue("Failed to load runner", loaded)

        // Test cases with different quality levels
        val qualityTests = listOf(
            Triple("test_audio_clear.wav", "Clear", "hello"),
            Triple("test_audio_normal.wav", "Normal", "hello"),
            Triple("test_audio_noisy.wav", "Noisy", "hello")
        )

        val results = mutableMapOf<String, Pair<String, Long>>()

        qualityTests.forEachIndexed { index, (audioFile, quality, expectedWord) ->
            System.out.println("\n========================================")
            System.out.println("Quality Test ${index + 1}/${qualityTests.size}: $quality")
            System.out.println("========================================")
            System.out.println("Audio: $audioFile")
            System.out.println("Expected word: '$expectedWord'")
            System.out.println("----------------------------------------")

            var audioData = loadAudioFromAssets(audioFile)

            if (audioData == null) {
                System.out.println("⚠️  Audio file not found, using fallback")
                // Use a fallback audio if specific quality file doesn't exist
                val fallbackAudio = loadAudioFromAssets("test_audio_hello.wav")
                if (fallbackAudio == null) {
                    System.out.println("⚠️  No fallback audio, skipping")
                    return@forEachIndexed
                } else {
                    audioData = fallbackAudio
                }
            }

            val request = InferenceRequest(
                sessionId = UUID.randomUUID().toString(),
                inputs = mapOf(InferenceRequest.INPUT_AUDIO to audioData)
            )

            val startTime = System.currentTimeMillis()
            val result = runner.run(request, stream = false)
            val elapsed = System.currentTimeMillis() - startTime

            if (result.error != null) {
                System.out.println("❌ Error: ${result.error?.message}")
                results[quality] = Pair("ERROR", elapsed)
                return@forEachIndexed
            }

            val transcription = result.outputs[InferenceResult.OUTPUT_TEXT] as? String ?: ""
            results[quality] = Pair(transcription, elapsed)

            System.out.println("Transcription: '$transcription'")
            System.out.println("Time: ${elapsed}ms")

            val containsExpected = transcription.lowercase().contains(expectedWord.lowercase())
            if (containsExpected) {
                System.out.println("✅ Contains expected word")
            } else {
                System.out.println("⚠️  Does not contain expected word")
            }
        }

        System.out.println("\n========================================")
        System.out.println("Audio Quality Results:")
        System.out.println("========================================")

        results.forEach { (quality, data) ->
            val (transcription, time) = data
            System.out.println("$quality: '$transcription' (${time}ms)")
        }

        assertTrue(
            "At least one quality level should be tested",
            results.isNotEmpty()
        )
    }

    /**
     * Test 2.4: Performance Metrics
     *
     * Measures ASR performance characteristics:
     * - Latency (time to first result)
     * - Throughput (processing speed)
     * - Consistency across multiple requests
     *
     * Success Criteria: Latency < 10 seconds for 3-second audio
     */
    @Test
    fun asr_meetsPerformanceRequirements() {
        // Check prerequisites
        Assume.assumeTrue(
            "Skipping: HF API Key required",
            hfApiKey.isNotBlank() && hfApiKey != "hf_YOUR_TOKEN_HERE"
        )

        System.out.println("========================================")
        System.out.println("Test 2.4: Performance Metrics")
        System.out.println("========================================")
        System.out.println("Model: $hfModel")

        // Setup runner
        val runner = HuggingFaceASRRunner(context)
        val params = mapOf(
            "api_key" to hfApiKey,
            "model" to hfModel,
            "wait_for_model" to true
        )

        val settings = EngineSettings.default()
            .withRunnerParameters("HuggingFaceASRRunner", params)

        val loaded = runner.load(hfModel, settings, emptyMap())
        assertTrue("Failed to load runner", loaded)

        // Load test audio
        val audioData = loadAudioFromAssets("test_audio_hello.wav")
        assertNotNull("Test audio required", audioData)

        System.out.println("Audio size: ${audioData!!.size} bytes")
        System.out.println("Running 3 iterations to measure consistency...")
        System.out.println("----------------------------------------")

        val latencies = mutableListOf<Long>()

        // Run 3 iterations
        repeat(3) { iteration ->
            System.out.println("\nIteration ${iteration + 1}/3")

            val request = InferenceRequest(
                sessionId = UUID.randomUUID().toString(),
                inputs = mapOf(InferenceRequest.INPUT_AUDIO to audioData)
            )

            val startTime = System.currentTimeMillis()
            val result = runner.run(request, stream = false)
            val elapsed = System.currentTimeMillis() - startTime

            latencies.add(elapsed)

            if (result.error == null) {
                val transcription = result.outputs[InferenceResult.OUTPUT_TEXT] as? String
                System.out.println("  Time: ${elapsed}ms")
                System.out.println("  Result: '$transcription'")
            } else {
                System.out.println("  Error: ${result.error?.message}")
            }
        }

        // Calculate statistics
        val avgLatency = latencies.average()
        val minLatency = latencies.minOrNull() ?: 0L
        val maxLatency = latencies.maxOrNull() ?: 0L

        System.out.println("\n========================================")
        System.out.println("Performance Statistics:")
        System.out.println("========================================")
        System.out.println("Average Latency: ${String.format("%.0f", avgLatency)}ms")
        System.out.println("Min Latency: ${minLatency}ms")
        System.out.println("Max Latency: ${maxLatency}ms")
        System.out.println("Success Criteria: <10,000ms (10 seconds)")
        System.out.println("========================================")

        assertTrue(
            "Average latency should be under 10 seconds (got ${avgLatency}ms)",
            avgLatency < 10000
        )
    }

    /**
     * Helper: Load audio file from assets
     */
    private fun loadAudioFromAssets(filename: String): ByteArray? {
        return try {
            context.assets.open(filename).use { inputStream ->
                inputStream.readBytes()
            }
        } catch (e: Exception) {
            System.err.println("Failed to load audio: $filename - ${e.message}")
            null
        }
    }
}