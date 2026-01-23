package com.mtkresearch.breezeapp.engine.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtkresearch.breezeapp.engine.model.EngineSettings
import com.mtkresearch.breezeapp.engine.model.InferenceRequest
import com.mtkresearch.breezeapp.engine.model.InferenceResult
import com.mtkresearch.breezeapp.engine.runner.core.BaseRunner
import com.mtkresearch.breezeapp.engine.runner.huggingface.HuggingFaceASRRunner
import com.mtkresearch.breezeapp.engine.runner.selfhosted.SelfHostedASRRunner
import java.util.UUID
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * General ASR Behavior Test
 *
 * Purpose: Validate real ASR behavior with actual API calls.
 * Tests accuracy and quality of speech recognition.
 * Supports multiple ASR runner implementations.
 *
 * Supported Runners:
 * - SelfHostedASRRunner (default)
 * - HuggingFaceASRRunner
 *
 * Configuration via instrumentation arguments:
 * - ASR_RUNNER_TYPE: "selfhosted" (default) or "huggingface"
 * - HF_API_KEY: Required for HuggingFace runner
 * - SELFHOSTED_SERVER_URL: Server URL for self-hosted runner
 * - ASR_MODEL: Model name (optional)
 * - TEST_LANGUAGE: Language for tests (default: "en")
 *
 * Example:
 * # Test with self-hosted runner (default)
 * ./gradlew connectedAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.ASR_RUNNER_TYPE=selfhosted \
 *   -Pandroid.testInstrumentationRunnerArguments.SELFHOSTED_SERVER_URL=https://your-id.ngrok.io
 *
 * # Test with HuggingFace runner
 * ./gradlew connectedAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.ASR_RUNNER_TYPE=huggingface \
 *   -Pandroid.testInstrumentationRunnerArguments.HF_API_KEY=hf_xxx
 */
@RunWith(AndroidJUnit4::class)
class GeneralASRBehaviorTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().context // Use test context for assets
    private lateinit var runnerType: String
    private lateinit var runner: BaseRunner
    private lateinit var runnerName: String
    private lateinit var modelName: String
    private var testLanguage: String = "en"

    @Before
    fun setup() {
        val args = androidx.test.platform.app.InstrumentationRegistry.getArguments()

        // Get runner type (default: selfhosted)
        runnerType = args.getString("ASR_RUNNER_TYPE")?.lowercase() ?: "selfhosted"
        testLanguage = args.getString("TEST_LANGUAGE") ?: "auto"

        System.out.println("========================================")
        System.out.println("ASR Behavior Test Configuration")
        System.out.println("========================================")
        System.out.println("Runner Type: $runnerType")
        System.out.println("Test Language: $testLanguage")

        // Initialize appropriate runner
        when (runnerType) {
            "huggingface", "hf" -> {
                setupHuggingFaceRunner(args)
            }
            "selfhosted", "self" -> {
                setupSelfHostedRunner(args)
            }
            else -> {
                System.out.println("Unknown runner type '$runnerType', defaulting to selfhosted")
                setupSelfHostedRunner(args)
            }
        }

        System.out.println("Runner: $runnerName")
        System.out.println("Model: $modelName")
        System.out.println("========================================")
    }

    private fun setupHuggingFaceRunner(args: android.os.Bundle) {
        val apiKey = args.getString("HF_API_KEY") ?: ""
        modelName = args.getString("ASR_MODEL") ?: "openai/whisper-large-v3"
        runnerName = "HuggingFaceASRRunner"

        runner = HuggingFaceASRRunner(context)

        val params = mapOf(
            "api_key" to apiKey,
            "model" to modelName,
            "wait_for_model" to true,
            "max_retries" to 3
        )

        val settings = EngineSettings.default()
            .withRunnerParameters(runnerName, params)

        val loaded = runner.load(modelName, settings, emptyMap())

        Assume.assumeTrue(
            "Failed to load HuggingFace runner. Check HF_API_KEY parameter.",
            loaded
        )

        System.out.println("HuggingFace API Key: ${if (apiKey.isNotBlank()) "✓ Provided" else "✗ Missing"}")
    }

    private fun setupSelfHostedRunner(args: android.os.Bundle) {
        // Default to Android emulator host (10.0.2.2 maps to host machine's localhost)
        val serverUrl = args.getString("SELFHOSTED_SERVER_URL") ?: "https://neely-henlike-shin.ngrok-free.dev"
        modelName = args.getString("ASR_MODEL") ?: "Taigi"
        runnerName = "SelfHostedASRRunner"

        runner = SelfHostedASRRunner(context)

        val params = mapOf(
            "server_url" to serverUrl,
            "model_name" to modelName,
            "endpoint" to "/transcribe",
            "timeout_ms" to 120000,
            "assume_connectivity" to true
        )

        val settings = EngineSettings.default()
            .withRunnerParameters(runnerName, params)

        val loaded = runner.load(modelName, settings, emptyMap())

        Assume.assumeTrue(
            "Failed to load SelfHosted runner. Check if server is running at $serverUrl",
            loaded
        )

        System.out.println("Server URL: $serverUrl")
    }

    /**
     * Test 2.1: English Transcription Accuracy
     *
     * Validates accurate transcription of English phrases
     * Success Criteria: ≥80% word accuracy
     */
    @Test
    fun asr_transcribesEnglishAccurately() {
        System.out.println("\n========================================")
        System.out.println("Test 2.1: English Transcription Accuracy")
        System.out.println("========================================")

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
     * Validates accurate transcription of Chinese phrases
     * Success Criteria: ≥70% accuracy
     */
    @Test
    fun asr_transcribesChineseAccurately() {
        System.out.println("\n========================================")
        System.out.println("Test 2.2: Chinese Transcription Accuracy")
        System.out.println("========================================")

        // Test cases: audio file -> expected Chinese characters
        val testCases = mapOf(
            "test_audio_nihao.wav" to listOf("你好", "您好"),
            "test_audio_xiexie.wav" to listOf("謝謝", "谢谢"),
            "test_audio_zaijian.wav" to listOf("再見", "再见"),
            "test_audio_mix.wav" to listOf("我想要", "order", "一個pizza", "一个pizza")
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
     * Test 2.4: Performance Metrics
     *
     * Measures ASR performance:
     * - Latency (time to first result)
     * - Consistency across multiple requests
     *
     * Success Criteria: Latency < 10 seconds for typical audio
     */
    @Test
    fun asr_meetsPerformanceRequirements() {
        System.out.println("\n========================================")
        System.out.println("Test 2.4: Performance Metrics")
        System.out.println("========================================")

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
     * Test 2.5: Language Detection
     *
     * Validates that ASR can handle multiple languages
     * Tests auto-detection or explicit language setting
     */
    @Test
    fun asr_handlesMultipleLanguages() {
        System.out.println("\n========================================")
        System.out.println("Test 2.5: Language Handling")
        System.out.println("========================================")

        // Test cases with different languages
        val languageTests = listOf(
            Triple("test_audio_hello.wav", "en", listOf("hello", "hi")),
            Triple("test_audio_nihao.wav", "zh", listOf("你好", "您好")),
            Triple("test_audio_nihao.wav", null, listOf("你好", "您好"))  // Auto-detect
        )

        val results = mutableListOf<Pair<String, String>>()

        languageTests.forEachIndexed { index, (audioFile, language, expectedWords) ->
            System.out.println("\n--- Test ${index + 1}/${languageTests.size} ---")
            System.out.println("Audio: $audioFile")
            System.out.println("Language: ${language ?: "auto-detect"}")
            System.out.println("Expected: $expectedWords")

            val audioData = loadAudioFromAssets(audioFile)
            if (audioData == null) {
                System.out.println("⚠️  Audio file not found, skipping")
                return@forEachIndexed
            }

            val request = InferenceRequest(
                sessionId = UUID.randomUUID().toString(),
                inputs = mapOf(InferenceRequest.INPUT_AUDIO to audioData),
                params = if (language != null) {
                    mapOf(InferenceRequest.PARAM_LANGUAGE to language)
                } else {
                    emptyMap()
                }
            )

            val result = runner.run(request, stream = false)

            if (result.error == null) {
                val transcription = result.outputs[InferenceResult.OUTPUT_TEXT] as? String ?: ""
                System.out.println("Transcription: '$transcription'")

                val matches = expectedWords.any { word ->
                    transcription.lowercase().contains(word.lowercase()) ||
                            transcription.contains(word)
                }

                System.out.println(if (matches) "✅ Match found" else "⚠️  No match")
                results.add(audioFile to transcription)
            } else {
                System.out.println("❌ Error: ${result.error?.message}")
            }
        }

        System.out.println("\n========================================")
        System.out.println("Language Test Results:")
        results.forEach { (file, text) ->
            System.out.println("$file → '$text'")
        }
        System.out.println("========================================")

        assertTrue(
            "At least one language test should succeed",
            results.isNotEmpty()
        )
    }

    /**
     * Helper: Load audio file from assets
     */
    private fun loadAudioFromAssets(filename: String): ByteArray? {
        return try {
            context.assets.open("test_data/$filename").use { inputStream ->
                inputStream.readBytes()
            }
        } catch (e: Exception) {
            System.err.println("Failed to load audio: $filename - ${e.message}")
            null
        }
    }
}