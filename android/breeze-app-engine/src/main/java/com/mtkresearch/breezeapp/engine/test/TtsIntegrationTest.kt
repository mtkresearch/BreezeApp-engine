package com.mtkresearch.breezeapp.engine.test

import android.content.Context
import android.util.Log
// Removed BreezeAppEngineApplication import - using service-based initialization
import com.mtkresearch.breezeapp.engine.domain.model.InferenceRequest
import com.mtkresearch.breezeapp.engine.domain.model.InferenceResult
import com.mtkresearch.breezeapp.engine.domain.model.ModelConfig
import com.mtkresearch.breezeapp.engine.runner.sherpa.SherpaTTSRunner
import com.mtkresearch.breezeapp.engine.system.SherpaLibraryManager
import com.mtkresearch.breezeapp.engine.util.TtsTestUtil
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

/**
 * Integration test for TTS functionality
 * Verifies that the complete setup works from Application initialization to TTS generation
 */
object TtsIntegrationTest {
    private const val TAG = "TtsIntegrationTest"
    
    /**
     * Complete integration test
     * Tests the full flow: Application → Library → Model → TTS Generation
     */
    fun runCompleteIntegrationTest(context: Context): TestResult {
        Log.i(TAG, "🚀 Starting complete TTS integration test...")
        
        val testResults = mutableListOf<String>()
        var overallSuccess = true
        
        try {
            // Step 1: Verify Service-based initialization
            testResults.add("Step 1: Service-based Initialization")
            // Note: Sherpa library should be initialized by BreezeAppEngineService
            testResults.add("Service-based initialization (no Application class needed)")
            
            // Step 2: Verify Sherpa library status
            testResults.add("Step 2: Sherpa Library Status")
            val isLibraryReady = SherpaLibraryManager.isLibraryReady()
            val libraryDiagnostics = SherpaLibraryManager.getDiagnosticInfo()
            Log.d(TAG, "Library ready: $isLibraryReady, diagnostics: $libraryDiagnostics")
            
            if (isLibraryReady) {
                testResults.add("✅ Sherpa library loaded and ready")
            } else {
                testResults.add("❌ Sherpa library not ready")
                overallSuccess = false
            }
            
            // Step 3: Test TTS Runner initialization
            testResults.add("Step 3: TTS Runner Initialization")
            val ttsRunner = SherpaTTSRunner(context)
            val modelLoaded = ttsRunner.load(ModelConfig(modelName = "vits-mr-20250709"))
            
            if (modelLoaded && ttsRunner.isLoaded()) {
                testResults.add("✅ TTS Runner loaded successfully")
                val runnerInfo = ttsRunner.getRunnerInfo()
                Log.d(TAG, "Runner info: $runnerInfo")
            } else {
                testResults.add("❌ TTS Runner failed to load")
                overallSuccess = false
                return TestResult(false, testResults)
            }
            
            // Step 4: Test basic TTS generation
            testResults.add("Step 4: Basic TTS Generation")
            val basicResult = testBasicTtsGeneration(ttsRunner)
            testResults.add(if (basicResult) "✅ Basic TTS generation successful" else "❌ Basic TTS generation failed")
            overallSuccess = overallSuccess && basicResult
            
            // Step 5: Test streaming TTS
            testResults.add("Step 5: Streaming TTS")
            val streamingResult = testStreamingTts(ttsRunner)
            testResults.add(if (streamingResult) "✅ Streaming TTS successful" else "❌ Streaming TTS failed")
            overallSuccess = overallSuccess && streamingResult
            
            // Step 6: Test parameter validation
            testResults.add("Step 6: Parameter Validation")
            val validationResult = testParameterValidation(ttsRunner)
            testResults.add(if (validationResult) "✅ Parameter validation working" else "❌ Parameter validation failed")
            overallSuccess = overallSuccess && validationResult
            
            // Cleanup
            ttsRunner.unload()
            testResults.add("✅ Cleanup completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Integration test failed with exception", e)
            testResults.add("❌ Exception: ${e.message}")
            overallSuccess = false
        }
        
        val result = TestResult(overallSuccess, testResults)
        Log.i(TAG, "🏁 Integration test completed: ${if (overallSuccess) "PASSED" else "FAILED"}")
        return result
    }
    
    /**
     * Quick integration test for basic functionality
     */
    fun runQuickIntegrationTest(context: Context): Boolean {
        return try {
            Log.i(TAG, "Running quick integration test...")
            
            // Check if Sherpa library is ready (initialized by service)
            if (!SherpaLibraryManager.isLibraryReady()) {
                Log.e(TAG, "Sherpa library not ready - ensure BreezeAppEngineService is started")
                return false
            }
            
            // Run quick TTS test
            val success = TtsTestUtil.runQuickTest(context, "Quick integration test.")
            Log.i(TAG, "Quick integration test: ${if (success) "PASSED" else "FAILED"}")
            success
            
        } catch (e: Exception) {
            Log.e(TAG, "Quick integration test failed", e)
            false
        }
    }
    
    private fun testBasicTtsGeneration(ttsRunner: SherpaTTSRunner): Boolean {
        return try {
            val request = InferenceRequest(
                inputs = mapOf(
                    InferenceRequest.INPUT_TEXT to "Hello, this is a basic TTS test.",
                    "speaker_id" to 0,
                    "speed" to 1.0f
                ),
                sessionId = "basic_test"
            )
            
            val result = ttsRunner.run(request)
            if (result.error == null) {
                val audioSamples = result.outputs[InferenceResult.OUTPUT_AUDIO] as? FloatArray
                val audioFile = result.outputs["audio_file_path"] as? String
                Log.d(TAG, "Basic TTS: ${audioSamples?.size} samples, file: $audioFile")
                true
            } else {
                Log.e(TAG, "Basic TTS failed: ${result.error?.message}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Basic TTS test exception", e)
            false
        }
    }
    
    private fun testStreamingTts(ttsRunner: SherpaTTSRunner): Boolean {
        return try {
            var finalResult: InferenceResult? = null
            
            val request = InferenceRequest(
                inputs = mapOf(
                    InferenceRequest.INPUT_TEXT to "This is a streaming TTS test.",
                    "speaker_id" to 0,
                    "speed" to 1.0f
                ),
                sessionId = "streaming_test"
            )
            
            runBlocking {
                ttsRunner.runAsFlow(request).collect { result ->
                    if (result.partial != true) {
                        finalResult = result
                    }
                }
            }
            
            val success = finalResult?.error == null
            if (success) {
                val audioFile = finalResult?.outputs?.get("audio_file_path") as? String
                Log.d(TAG, "Streaming TTS completed, file: $audioFile")
            } else {
                Log.e(TAG, "Streaming TTS failed: ${finalResult?.error?.message}")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Streaming TTS test exception", e)
            false
        }
    }
    
    private fun testParameterValidation(ttsRunner: SherpaTTSRunner): Boolean {
        return try {
            // Test invalid speaker ID
            val invalidSpeakerRequest = InferenceRequest(
                inputs = mapOf(
                    InferenceRequest.INPUT_TEXT to "Test",
                    "speaker_id" to -1,
                    "speed" to 1.0f
                ),
                sessionId = "invalid_speaker"
            )
            
            val result1 = ttsRunner.run(invalidSpeakerRequest)
            val validationWorking = result1.error != null
            
            if (validationWorking) {
                Log.d(TAG, "Parameter validation working correctly")
            } else {
                Log.e(TAG, "Parameter validation not working - invalid speaker ID was accepted")
            }
            
            validationWorking
        } catch (e: Exception) {
            Log.e(TAG, "Parameter validation test exception", e)
            false
        }
    }
    
    /**
     * Test result data class
     */
    data class TestResult(
        val success: Boolean,
        val details: List<String>
    ) {
        fun printResults() {
            Log.i(TAG, "=== TTS Integration Test Results ===")
            details.forEach { detail ->
                Log.i(TAG, detail)
            }
            Log.i(TAG, "Overall Result: ${if (success) "✅ PASSED" else "❌ FAILED"}")
            Log.i(TAG, "=====================================")
        }
    }
}