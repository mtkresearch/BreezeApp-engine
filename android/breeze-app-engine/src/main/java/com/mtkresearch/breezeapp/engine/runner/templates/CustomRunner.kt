package com.mtkresearch.breezeapp.engine.runner.templates

import android.util.Log
import com.mtkresearch.breezeapp.engine.annotation.AIRunner
import com.mtkresearch.breezeapp.engine.annotation.RunnerPriority
import com.mtkresearch.breezeapp.engine.annotation.VendorType
import com.mtkresearch.breezeapp.engine.runner.core.BaseRunner
import com.mtkresearch.breezeapp.engine.runner.core.FlowStreamingRunner
import com.mtkresearch.breezeapp.engine.runner.core.RunnerInfo
import com.mtkresearch.breezeapp.engine.runner.core.ParameterSchema
import com.mtkresearch.breezeapp.engine.runner.core.ParameterType
import com.mtkresearch.breezeapp.engine.runner.core.ValidationResult
import com.mtkresearch.breezeapp.engine.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CLEAN RUNNER TEMPLATE
 * 
 * Copy this template and replace placeholder implementations with your AI logic.
 * 
 * QUICK START:
 * 1. Copy to: runner/yourvendor/YourRunner.kt
 * 2. Update @AIRunner annotation (capabilities, vendor, model)
 * 3. Ensure SUPPORTED_CAPABILITIES matches annotation
 * 4. Replace placeholder comments with your AI client initialization
 * 5. Implement your AI processing logic
 * 
 * For detailed streaming patterns, see: STREAMING_GUIDE.md
 */

@AIRunner(
    vendor = VendorType.UNKNOWN,           // Choose your vendor
    priority = RunnerPriority.NORMAL,      // HIGH/NORMAL/LOW
    capabilities = [CapabilityType.LLM],   // SYNC with SUPPORTED_CAPABILITIES below
    defaultModel = "your-model-name"
)
class CustomRunner : BaseRunner, FlowStreamingRunner {
    
    companion object {
        private const val TAG = "CustomRunner"
        // CRITICAL: Must match @AIRunner annotation capabilities exactly
        private val SUPPORTED_CAPABILITIES = listOf(CapabilityType.LLM)
    }
    
    private val isLoaded = AtomicBoolean(false)
    private var loadedModelId: String = ""  // Track loaded model for change detection

    // TODO: Replace with your AI client instances
    // private var llmClient: YourLLMClient? = null
    // private var asrClient: YourASRClient? = null
    // private var ttsClient: YourTTSClient? = null
    
    override fun load(modelId: String, settings: EngineSettings, initialParams: Map<String, Any>): Boolean {
        Log.d(TAG, "Loading model: $modelId")
        
        return try {
            val runnerParams = settings.getRunnerParameters(this::class.java.simpleName)
            
            // TODO: Extract parameters you need
            val apiKey = runnerParams["api_key"] as? String ?: ""
            
            // TODO: Initialize your AI client(s) based on SUPPORTED_CAPABILITIES
            // if (CapabilityType.LLM in SUPPORTED_CAPABILITIES) {
            //     llmClient = YourLLMClient(apiKey, modelId)
            //     val success = llmClient?.initialize() ?: false
            //     if (!success) return false
            // }

            isLoaded.set(true)
            loadedModelId = modelId
            Log.d(TAG, "Successfully loaded model: $modelId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: $modelId", e)
            isLoaded.set(false)
            loadedModelId = ""
            false
        }
    }
    
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        if (!isLoaded.get()) {
            return InferenceResult.error(RunnerError.resourceUnavailable("Model not loaded"))
        }
        
        return try {
            when {
                // LLM: Text input
                input.inputs.containsKey(InferenceRequest.INPUT_TEXT) && 
                CapabilityType.LLM in SUPPORTED_CAPABILITIES -> {
                    val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
                    
                    // TODO: Replace with your LLM client call
                    // val response = llmClient?.generateText(text)
                    val response = "LLM response to: $text"
                    
                    InferenceResult.success(mapOf(InferenceResult.OUTPUT_TEXT to response))
                }
                
                // ASR: Audio input
                input.inputs.containsKey(InferenceRequest.INPUT_AUDIO) && 
                CapabilityType.ASR in SUPPORTED_CAPABILITIES -> {
                    val audio = input.inputs[InferenceRequest.INPUT_AUDIO] as? ByteArray ?: byteArrayOf()
                    
                    // TODO: Replace with your ASR client call
                    // val transcript = asrClient?.transcribe(audio)
                    val transcript = "Transcribed: ${audio.size} bytes"
                    
                    InferenceResult.success(mapOf(InferenceResult.OUTPUT_TEXT to transcript))
                }
                
                // TODO: Add other capabilities (TTS, VLM, GUARDIAN) as needed
                
                else -> {
                    InferenceResult.error(RunnerError.invalidInput( 
                        "Unsupported input for capabilities: ${SUPPORTED_CAPABILITIES}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Processing failed", e)
            InferenceResult.error(RunnerError.processingError("Processing failed: ${e.message}"))
        }
    }
    
    override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = flow {
        if (!isLoaded.get()) {
            emit(InferenceResult.error(RunnerError.resourceUnavailable("Model not loaded")))
            return@flow
        }
        
        try {
            when {
                // LLM Streaming
                CapabilityType.LLM in SUPPORTED_CAPABILITIES && 
                input.inputs.containsKey(InferenceRequest.INPUT_TEXT) -> {
                    
                    val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
                    
                    // TODO: Replace with your streaming implementation
                    // Your streaming API pattern:
                    // llmClient?.streamGenerate(text) { chunk ->
                    //     emit(InferenceResult.success(
                    //         outputs = mapOf(InferenceResult.OUTPUT_TEXT to chunk.text),
                    //         partial = !chunk.isComplete
                    //     ))
                    // }
                    
                    // Simple fallback to non-streaming
                    emit(run(input, false))
                }
                
                // TODO: Add streaming for other capabilities as needed
                // See STREAMING_GUIDE.md for detailed patterns
                
                else -> {
                    // Fallback to non-streaming
                    emit(run(input, false))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Streaming failed", e)
            emit(InferenceResult.error(RunnerError.processingError("Streaming failed: ${e.message}")))
        }
    }
    
    override fun unload() {
        Log.d(TAG, "Unloading runner")

        // TODO: Cleanup your AI clients
        // llmClient?.cleanup()
        // llmClient = null

        isLoaded.set(false)
        loadedModelId = ""
    }

    override fun getCapabilities(): List<CapabilityType> = SUPPORTED_CAPABILITIES

    override fun isLoaded(): Boolean = isLoaded.get()

    override fun getLoadedModelId(): String = loadedModelId
    
    override fun getRunnerInfo(): RunnerInfo = RunnerInfo(
        name = this::class.java.simpleName,
        version = "1.0.0",
        capabilities = getCapabilities(),
        description = "Custom runner for ${getCapabilities().joinToString()}"
    )
    
    override fun isSupported(): Boolean {
        return try {
            // TODO: Check if your AI library/service is available
            // return YourAILibrary.isAvailable()
            true
        } catch (e: Exception) {
            Log.d(TAG, "Support check failed", e)
            false
        }
    }
    
    override fun getParameterSchema(): List<ParameterSchema> {
        return listOf(
            ParameterSchema(
                name = "api_key",
                displayName = "API Key",
                description = "Your AI service API key",
                type = ParameterType.StringType(minLength = 8),
                defaultValue = "",
                isRequired = true,
                isSensitive = true,
                category = "Authentication"
            )
            // TODO: Add parameters specific to your AI service
        )
    }
    
    override fun validateParameters(parameters: Map<String, Any>): ValidationResult {
        val apiKey = parameters["api_key"] as? String
        if (apiKey.isNullOrBlank()) {
            return ValidationResult.invalid("API key is required")
        }
        
        // TODO: Add your parameter validation
        
        return ValidationResult.valid()
    }
}