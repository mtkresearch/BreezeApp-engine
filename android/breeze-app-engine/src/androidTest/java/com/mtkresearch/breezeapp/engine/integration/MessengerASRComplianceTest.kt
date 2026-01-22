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
 * General ASR Compliance Test
 *
 * Purpose: Validate that ASR runners correctly transcribe audio to text.
 * Supports multiple ASR runner implementations.
 *
 * Supported Runners:
 * - SelfHostedASRRunner (default)
 * - HuggingFaceASRRunner
 *
 * Configuration via instrumentation arguments:
 * - ASR_RUNNER_TYPE: "selfhosted" (default) or "huggingface"
 * - HF_API_KEY: Required for HuggingFace runner
 * - SELFHOSTED_SERVER_URL: Server URL for self-hosted runner (default: http://10.0.2.2:5000)
 * - ASR_MODEL: Model name (optional, uses runner default)
 *
 * Example:
 * ./gradlew connectedAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.ASR_RUNNER_TYPE=selfhosted \
 *   -Pandroid.testInstrumentationRunnerArguments.SELFHOSTED_SERVER_URL=https://your-id.ngrok.io
 */
@RunWith(AndroidJUnit4::class)
class GeneralASRComplianceTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().context // Use test context for assets
    private lateinit var runnerType: String
    private lateinit var runner: BaseRunner
    private lateinit var runnerName: String
    private lateinit var modelName: String

    @Before
    fun setup() {
        val args = androidx.test.platform.app.InstrumentationRegistry.getArguments()

        // Get runner type (default: selfhosted)
        runnerType = args.getString("ASR_RUNNER_TYPE")?.lowercase() ?: "selfhosted"

        System.out.println("========================================")
        System.out.println("ASR Runner Configuration")
        System.out.println("========================================")
        System.out.println("Runner Type: $runnerType")

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
            "assume_connectivity" to true  // Skip connectivity checks for testing
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
     * Test 1.1: Audio Input Processing
     *
     * Validates that the ASR runner can:
     * 1. Accept audio data in ByteArray format
     * 2. Successfully process the audio
     * 3. Return a non-null result
     */
    @Test
    fun asrRunner_acceptsAudioInput() {
        System.out.println("\n========================================")
        System.out.println("Test 1.1: Audio Input Processing")
        System.out.println("========================================")

        // Load test audio from assets
        val audioData = loadAudioFromAssets("test_audio_hello.wav")
        assertNotNull("Test audio file not found", audioData)
        assertTrue("Audio data should not be empty", audioData!!.isNotEmpty())

        System.out.println("Audio loaded: ${audioData.size} bytes")
        System.out.println("----------------------------------------")

        // Create inference request
        val request = InferenceRequest(
            sessionId = UUID.randomUUID().toString(),
            inputs = mapOf(InferenceRequest.INPUT_AUDIO to audioData)
        )

        // Run inference
        val startTime = System.currentTimeMillis()
        val result = runner.run(request, stream = false)
        val elapsed = System.currentTimeMillis() - startTime

        System.out.println("Processing time: ${elapsed}ms")
        System.out.println("----------------------------------------")

        // Validate result
        assertNull("ASR runner returned error: ${result.error}", result.error)
        assertNotNull("Result should not be null", result)

        val transcription = result.outputs[InferenceResult.OUTPUT_TEXT] as? String
        assertNotNull("Transcription should not be null", transcription)

        System.out.println("Transcription: '$transcription'")
        System.out.println("✅ Audio input processing successful")
        System.out.println("========================================")
    }

    /**
     * Test 1.2: Text Output Format
     *
     * Validates that the ASR runner returns text in the correct format
     */
    @Test
    fun asrRunner_returnsValidTextFormat() {
        System.out.println("\n========================================")
        System.out.println("Test 1.2: Text Output Format Validation")
        System.out.println("========================================")

        // Load test audio
        val audioData = loadAudioFromAssets("test_audio_hello.wav")
        assertNotNull("Test audio not found", audioData)

        System.out.println("Testing output format...")
        System.out.println("----------------------------------------")

        // Create and run request
        val request = InferenceRequest(
            sessionId = UUID.randomUUID().toString(),
            inputs = mapOf(InferenceRequest.INPUT_AUDIO to audioData!!)
        )

        val result = runner.run(request, stream = false)

        // Validate output format
        assertNull("Should not have error", result.error)

        // Check OUTPUT_TEXT key exists
        assertTrue(
            "Result should contain OUTPUT_TEXT key",
            result.outputs.containsKey(InferenceResult.OUTPUT_TEXT)
        )

        val transcription = result.outputs[InferenceResult.OUTPUT_TEXT]

        // Validate type
        assertTrue(
            "OUTPUT_TEXT should be a String, got: ${transcription?.javaClass?.simpleName}",
            transcription is String
        )

        val text = transcription as String

        // Validate content
        assertTrue(
            "Transcription should not be empty",
            text.isNotEmpty()
        )

        assertTrue(
            "Transcription should be reasonable length (>0, <1000 chars)",
            text.length in 1..1000
        )

        // Validate it contains actual words (not just symbols/numbers)
        assertTrue(
            "Transcription should contain letters: '$text'",
            text.any { it.isLetter() }
        )

        System.out.println("Transcription: '$text'")
        System.out.println("Length: ${text.length} characters")
        System.out.println("✅ Output format is valid")
        System.out.println("========================================")
    }

    /**
     * Test 1.3: Error Handling
     *
     * Validates proper error handling for invalid inputs
     */
    @Test
    fun asrRunner_handlesInvalidInputGracefully() {
        System.out.println("\n========================================")
        System.out.println("Test 1.3: Error Handling Validation")
        System.out.println("========================================")

        // Test Case 1: Empty audio
        System.out.println("\n--- Test Case 1: Empty Audio ---")
        val emptyRequest = InferenceRequest(
            sessionId = UUID.randomUUID().toString(),
            inputs = mapOf(InferenceRequest.INPUT_AUDIO to ByteArray(0))
        )

        val emptyResult = runner.run(emptyRequest, stream = false)

        assertNotNull(
            "Should have error for empty audio",
            emptyResult.error
        )
        System.out.println("Error message: ${emptyResult.error?.message}")
        System.out.println("✅ Empty audio handled correctly")

        // Test Case 2: Missing audio input
        System.out.println("\n--- Test Case 2: Missing Audio Input ---")
        val missingRequest = InferenceRequest(
            sessionId = UUID.randomUUID().toString(),
            inputs = emptyMap()
        )

        val missingResult = runner.run(missingRequest, stream = false)

        assertNotNull(
            "Should have error for missing audio",
            missingResult.error
        )
        System.out.println("Error message: ${missingResult.error?.message}")
        System.out.println("✅ Missing audio handled correctly")

        // Test Case 3: Invalid audio data (random bytes)
        System.out.println("\n--- Test Case 3: Invalid Audio Data ---")
        val invalidAudio = ByteArray(100) { it.toByte() }
        val invalidRequest = InferenceRequest(
            sessionId = UUID.randomUUID().toString(),
            inputs = mapOf(InferenceRequest.INPUT_AUDIO to invalidAudio)
        )

        val invalidResult = runner.run(invalidRequest, stream = false)

        // Should either error OR return empty/nonsense transcription
        if (invalidResult.error != null) {
            System.out.println("Error message: ${invalidResult.error?.message}")
            System.out.println("✅ Invalid audio rejected")
        } else {
            System.out.println("Transcription: ${invalidResult.outputs[InferenceResult.OUTPUT_TEXT]}")
            System.out.println("✅ Invalid audio processed (model attempted transcription)")
        }

        System.out.println("\n✅ All error handling tests passed")
        System.out.println("========================================")
    }

    /**
     * Test 1.4: Multiple Audio Files
     *
     * Validates ASR consistency across different audio samples
     */
    @Test
    fun asrRunner_processesMultipleAudioFiles() {
        System.out.println("\n========================================")
        System.out.println("Test 1.4: Multiple Audio Files Processing")
        System.out.println("========================================")

        // Test multiple audio files
        val testFiles = listOf(
            "test_audio_hello.wav",
            "test_audio_thanks.wav",
            "test_audio_goodbye.wav"
        )

        var successCount = 0
        val results = mutableListOf<Pair<String, String>>()

        testFiles.forEachIndexed { index, filename ->
            System.out.println("\n--- Processing ${index + 1}/${testFiles.size}: $filename ---")

            val audioData = loadAudioFromAssets(filename)

            if (audioData == null) {
                System.out.println("⚠️  Audio file not found, skipping: $filename")
                return@forEachIndexed
            }

            System.out.println("Audio size: ${audioData.size} bytes")

            val request = InferenceRequest(
                sessionId = UUID.randomUUID().toString(),
                inputs = mapOf(InferenceRequest.INPUT_AUDIO to audioData)
            )

            val startTime = System.currentTimeMillis()
            val result = runner.run(request, stream = false)
            val elapsed = System.currentTimeMillis() - startTime

            if (result.error == null) {
                val transcription = result.outputs[InferenceResult.OUTPUT_TEXT] as? String
                System.out.println("Transcription: '$transcription'")
                System.out.println("Time: ${elapsed}ms")
                System.out.println("✅ Success")

                if (transcription != null) {
                    results.add(filename to transcription)
                    successCount++
                }
            } else {
                System.out.println("❌ Error: ${result.error?.message}")
            }
        }

        System.out.println("\n========================================")
        System.out.println("Results: $successCount/${testFiles.size} files processed successfully")
        System.out.println("========================================")

        results.forEach { (file, text) ->
            System.out.println("$file → '$text'")
        }

        assertTrue(
            "At least one audio file should be processed successfully",
            successCount > 0
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
            System.err.println("Failed to load audio from assets: $filename - ${e.message}")
            null
        }
    }
}