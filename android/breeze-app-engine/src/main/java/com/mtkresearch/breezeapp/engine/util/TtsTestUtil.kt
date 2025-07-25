package com.mtkresearch.breezeapp.engine.util

import android.content.Context
import android.util.Log
import com.mtkresearch.breezeapp.engine.domain.model.InferenceRequest
import com.mtkresearch.breezeapp.engine.domain.model.InferenceResult
import com.mtkresearch.breezeapp.engine.domain.model.ModelConfig
import com.mtkresearch.breezeapp.engine.runner.sherpa.SherpaTTSRunner
import com.mtkresearch.breezeapp.engine.system.SherpaLibraryManager
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

/**
 * Utility for testing TTS functionality
 * Helps verify your vits-melo-tts-zh_en model setup
 */
object TtsTestUtil {
    private const val TAG = "TtsTestUtil"
    
    /**
     * Test data for different languages and scenarios
     */
    object TestData {
        val CHINESE_TEXT = "你好，这是一个中文测试。"
        val ENGLISH_TEXT = "Hello, this is an English test."
        val MIXED_TEXT = "你好世界 Hello World，今天天气很好 The weather is nice today。"
        val NUMBERS_TEXT = "今天是2024年1月15日，温度是25度。Today is January 15th, 2024, temperature is 25 degrees."
        val LONG_TEXT = "这是一个较长的测试文本，用来验证TTS模型的性能和稳定性。" +
                "This is a longer test text to verify the performance and stability of the TTS model. " +
                "我们希望模型能够正确处理中英文混合的长句子。" +
                "We hope the model can correctly handle long sentences with mixed Chinese and English."
    }
    
    /**
     * Comprehensive TTS test suite
     */
    fun runComprehensiveTest(context: Context): TestResult {
        val results = mutableListOf<SingleTestResult>()
        
        Log.i(TAG, "Starting comprehensive TTS test suite...")
        
        // Test 1: Library initialization
        results.add(testLibraryInitialization())
        
        // Test 2: Model validation
        results.add(testModelValidation(context))
        
        // Test 3: Runner initialization
        val runner = testRunnerInitialization(context)
        results.add(runner.second)
        
        if (runner.first != null) {
            // Test 4: Basic TTS generation
            results.add(testBasicTts(runner.first!!, TestData.CHINESE_TEXT, "Chinese"))
            results.add(testBasicTts(runner.first!!, TestData.ENGLISH_TEXT, "English"))
            results.add(testBasicTts(runner.first!!, TestData.MIXED_TEXT, "Mixed"))
            
            // Test 5: Parameter validation
            results.add(testParameterValidation(runner.first!!))
            
            // Test 6: Streaming TTS
            results.add(testStreamingTts(runner.first!!, TestData.NUMBERS_TEXT))
            
            // Test 7: Performance test
            results.add(testPerformance(runner.first!!, TestData.LONG_TEXT))
            
            // Cleanup
            runner.first!!.unload()
        }
        
        val testResult = TestResult(
            totalTests = results.size,
            passedTests = results.count { it.passed },
            failedTests = results.count { !it.passed },
            results = results
        )
        
        Log.i(TAG, "Test suite completed: ${testResult.passedTests}/${testResult.totalTests} passed")
        return testResult
    }
    
    /**
     * Quick test for basic functionality
     */
    fun runQuickTest(context: Context, testText: String = TestData.MIXED_TEXT): Boolean {
        return try {
            Log.i(TAG, "Running quick TTS test...")
            
            if (!SherpaLibraryManager.initializeGlobally()) {
                Log.e(TAG, "Quick test failed: Library initialization failed")
                return false
            }
            
            val runner = SherpaTTSRunner(context)
            val loaded = runner.load(ModelConfig(modelName = "vits-melo-tts-zh_en"))
            
            if (!loaded) {
                Log.e(TAG, "Quick test failed: Runner loading failed")
                return false
            }
            
            val request = InferenceRequest(
                inputs = mapOf(
                    InferenceRequest.INPUT_TEXT to testText,
                    "speaker_id" to 0,
                    "speed" to 1.0f
                ),
                sessionId = "quick_test"
            )
            
            val result = runner.run(request)
            runner.unload()
            
            val success = result.error == null
            Log.i(TAG, "Quick test ${if (success) "PASSED" else "FAILED"}")
            return success
            
        } catch (e: Exception) {
            Log.e(TAG, "Quick test failed with exception", e)
            false
        }
    }
    
    private fun testLibraryInitialization(): SingleTestResult {
        return try {
            val success = SherpaLibraryManager.initializeGlobally()
            val diagnostics = SherpaLibraryManager.getDiagnosticInfo()
            
            SingleTestResult(
                testName = "Library Initialization",
                passed = success,
                message = if (success) "Library loaded successfully" else "Library loading failed",
                details = diagnostics.toString()
            )
        } catch (e: Exception) {
            SingleTestResult(
                testName = "Library Initialization",
                passed = false,
                message = "Exception during library loading: ${e.message}",
                details = e.stackTraceToString()
            )
        }
    }
    
    private fun testModelValidation(context: Context): SingleTestResult {
        return try {
            val modelConfig = SherpaTtsConfigUtil.getTtsModelConfig(SherpaTtsConfigUtil.TtsModelType.VITS_MR_20250709)
            val isValid = SherpaTtsConfigUtil.validateModelAssets(context, modelConfig)
            
            SingleTestResult(
                testName = "Model Validation",
                passed = isValid,
                message = if (isValid) "Model assets found and valid" else "Model assets missing or invalid",
                details = "Model directory: ${modelConfig.modelDir}, Description: ${modelConfig.description}"
            )
        } catch (e: Exception) {
            SingleTestResult(
                testName = "Model Validation",
                passed = false,
                message = "Exception during model validation: ${e.message}",
                details = e.stackTraceToString()
            )
        }
    }
    
    private fun testRunnerInitialization(context: Context): Pair<SherpaTTSRunner?, SingleTestResult> {
        return try {
            val runner = SherpaTTSRunner(context)
            val loaded = runner.load(ModelConfig(modelName = "vits-mr-20250709"))
            
            val result = SingleTestResult(
                testName = "Runner Initialization",
                passed = loaded && runner.isLoaded(),
                message = if (loaded) "Runner loaded successfully" else "Runner loading failed",
                details = "Runner info: ${runner.getRunnerInfo()}"
            )
            
            if (loaded) Pair(runner, result) else Pair(null, result)
        } catch (e: Exception) {
            Pair(null, SingleTestResult(
                testName = "Runner Initialization",
                passed = false,
                message = "Exception during runner initialization: ${e.message}",
                details = e.stackTraceToString()
            ))
        }
    }
    
    private fun testBasicTts(runner: SherpaTTSRunner, text: String, language: String): SingleTestResult {
        return try {
            val startTime = System.currentTimeMillis()
            
            val request = InferenceRequest(
                inputs = mapOf(
                    InferenceRequest.INPUT_TEXT to text,
                    "speaker_id" to 0,
                    "speed" to 1.0f
                ),
                sessionId = "test_${language.lowercase()}"
            )
            
            val result = runner.run(request)
            val elapsed = System.currentTimeMillis() - startTime
            
            if (result.error == null) {
                val audioSamples = result.outputs[InferenceResult.OUTPUT_AUDIO] as? FloatArray
                val sampleRate = result.outputs["sample_rate"] as? Int
                
                SingleTestResult(
                    testName = "Basic TTS ($language)",
                    passed = true,
                    message = "TTS generation successful",
                    details = "Audio samples: ${audioSamples?.size}, Sample rate: $sampleRate, Time: ${elapsed}ms"
                )
            } else {
                SingleTestResult(
                    testName = "Basic TTS ($language)",
                    passed = false,
                    message = "TTS generation failed: ${result.error?.message}",
                    details = "Error: ${result.error?.message}, Time: ${elapsed}ms"
                )
            }
        } catch (e: Exception) {
            SingleTestResult(
                testName = "Basic TTS ($language)",
                passed = false,
                message = "Exception during TTS generation: ${e.message}",
                details = e.stackTraceToString()
            )
        }
    }
    
    private fun testParameterValidation(runner: SherpaTTSRunner): SingleTestResult {
        return try {
            val tests = listOf(
                // Test invalid speaker ID
                mapOf(InferenceRequest.INPUT_TEXT to "test", "speaker_id" to -1, "speed" to 1.0f),
                // Test invalid speed
                mapOf(InferenceRequest.INPUT_TEXT to "test", "speaker_id" to 0, "speed" to 0.0f),
                // Test empty text
                mapOf(InferenceRequest.INPUT_TEXT to "", "speaker_id" to 0, "speed" to 1.0f)
            )
            
            var validationsPassed = 0
            for ((index, testInputs) in tests.withIndex()) {
                val request = InferenceRequest(inputs = testInputs, sessionId = "validation_$index")
                val result = runner.run(request)
                
                if (result.error != null) {
                    validationsPassed++
                }
            }
            
            val allPassed = validationsPassed == tests.size
            SingleTestResult(
                testName = "Parameter Validation",
                passed = allPassed,
                message = if (allPassed) "All invalid parameters correctly rejected" else "Some invalid parameters were accepted",
                details = "$validationsPassed/${tests.size} validations passed"
            )
        } catch (e: Exception) {
            SingleTestResult(
                testName = "Parameter Validation",
                passed = false,
                message = "Exception during parameter validation: ${e.message}",
                details = e.stackTraceToString()
            )
        }
    }
    
    private fun testStreamingTts(runner: SherpaTTSRunner, text: String): SingleTestResult {
        return try {
            val startTime = System.currentTimeMillis()
            var resultCount = 0
            var finalResult: InferenceResult? = null
            
            val request = InferenceRequest(
                inputs = mapOf(
                    InferenceRequest.INPUT_TEXT to text,
                    "speaker_id" to 0,
                    "speed" to 1.0f
                ),
                sessionId = "streaming_test"
            )
            
            runBlocking {
                runner.runAsFlow(request).collect { result ->
                    resultCount++
                    if (result.partial != true) {
                        finalResult = result
                    }
                }
            }
            
            val elapsed = System.currentTimeMillis() - startTime
            val success = finalResult?.error == null
            
            SingleTestResult(
                testName = "Streaming TTS",
                passed = success,
                message = if (success) "Streaming TTS completed successfully" else "Streaming TTS failed",
                details = "Results received: $resultCount, Time: ${elapsed}ms"
            )
        } catch (e: Exception) {
            SingleTestResult(
                testName = "Streaming TTS",
                passed = false,
                message = "Exception during streaming TTS: ${e.message}",
                details = e.stackTraceToString()
            )
        }
    }
    
    private fun testPerformance(runner: SherpaTTSRunner, text: String): SingleTestResult {
        return try {
            val iterations = 3
            val times = mutableListOf<Long>()
            
            repeat(iterations) { i ->
                val startTime = System.currentTimeMillis()
                
                val request = InferenceRequest(
                    inputs = mapOf(
                        InferenceRequest.INPUT_TEXT to text,
                        "speaker_id" to 0,
                        "speed" to 1.0f
                    ),
                    sessionId = "perf_test_$i"
                )
                
                val result = runner.run(request)
                val elapsed = System.currentTimeMillis() - startTime
                
                if (result.error == null) {
                    times.add(elapsed)
                }
            }
            
            val avgTime = if (times.isNotEmpty()) times.average() else 0.0
            val success = times.size == iterations
            
            SingleTestResult(
                testName = "Performance Test",
                passed = success,
                message = if (success) "Performance test completed" else "Some performance tests failed",
                details = "Successful runs: ${times.size}/$iterations, Average time: ${avgTime.toInt()}ms"
            )
        } catch (e: Exception) {
            SingleTestResult(
                testName = "Performance Test",
                passed = false,
                message = "Exception during performance test: ${e.message}",
                details = e.stackTraceToString()
            )
        }
    }
    
    /**
     * Data classes for test results
     */
    data class TestResult(
        val totalTests: Int,
        val passedTests: Int,
        val failedTests: Int,
        val results: List<SingleTestResult>
    ) {
        val successRate: Double = if (totalTests > 0) passedTests.toDouble() / totalTests else 0.0
        val isAllPassed: Boolean = failedTests == 0
    }
    
    data class SingleTestResult(
        val testName: String,
        val passed: Boolean,
        val message: String,
        val details: String = ""
    )
}