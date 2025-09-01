# Runner Development Guide

Create custom AI runners for BreezeApp Engine in 3 simple steps.

## Quick Start

### 1. Copy the Template
```bash
cp templates/CustomRunner.kt yourvendor/YourRunner.kt
```

### 2. Update the Annotation
```kotlin
@AIRunner(
    vendor = VendorType.UNKNOWN,           // Choose your vendor
    priority = RunnerPriority.NORMAL,      // HIGH/NORMAL/LOW  
    capabilities = [CapabilityType.LLM],   // LLM/ASR/TTS/VLM/GUARDIAN
    defaultModel = "your-model-name"
)
class YourRunner : BaseRunner, FlowStreamingRunner {
    // Implementation...
}
```

### 3. Implement Your AI Logic
```kotlin
// For LLM:
private fun processTextInput(text: String): String {
    return apiClient.generateText(text)
}

// For ASR:
private fun processAudioInput(audio: ByteArray): String {
    return apiClient.transcribeAudio(audio)
}

// For TTS, VLM, GUARDIAN - see template for examples
```

**That's it!** The engine automatically discovers and integrates your runner.

## Available Types

**Capabilities:**
- `LLM` - Text generation (text → text)
- `ASR` - Speech recognition (audio → text)
- `TTS` - Text-to-speech (text → audio)
- `VLM` - Vision + language (text + image → text)
- `GUARDIAN` - Content safety (text → safety analysis)

**Vendors:**
- `OPENROUTER` - Cloud API services
- `EXECUTORCH` - Local mobile inference
- `SHERPA` - ONNX-based processing
- `MEDIATEK` - NPU acceleration
- `UNKNOWN` - Custom/unspecified

## Examples

### LLM Runner Example
```kotlin
@AIRunner(vendor = VendorType.OPENROUTER, capabilities = [CapabilityType.LLM])
class MyLLMRunner : BaseRunner {
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        return try {
            val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
            val response = apiClient.generateText(text)
            InferenceResult.success(mapOf("text" to response))
        } catch (e: Exception) {
            InferenceResult.error(RunnerError.processingError("Generation failed: ${e.message}", e))
        }
    }
}
```

### ASR Runner Example
```kotlin
@AIRunner(vendor = VendorType.SHERPA, capabilities = [CapabilityType.ASR])
class MyASRRunner : BaseRunner {
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        return try {
            val audio = input.inputs[InferenceRequest.INPUT_AUDIO] as? ByteArray ?: byteArrayOf()
            if (audio.isEmpty()) {
                return InferenceResult.error(RunnerError.invalidInput("Audio input is required"))
            }
            val transcript = asrClient.transcribe(audio)
            InferenceResult.success(mapOf("text" to transcript))
        } catch (e: Exception) {
            InferenceResult.error(RunnerError(RunnerError.Code.ASR_FAILURE, "Transcription failed: ${e.message}", e))
        }
    }
}
```

### TTS Runner Example
```kotlin
@AIRunner(vendor = VendorType.SHERPA, capabilities = [CapabilityType.TTS])
class MyTTSRunner : BaseRunner {
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        return try {
            val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
            if (text.isBlank()) {
                return InferenceResult.error(RunnerError.invalidInput("Text input cannot be empty"))
            }
            val audioData = ttsClient.synthesize(text)
            InferenceResult.success(mapOf("audio_data" to audioData, "sample_rate" to 22050))
        } catch (e: Exception) {
            InferenceResult.error(RunnerError(RunnerError.Code.TTS_FAILURE, "Speech synthesis failed: ${e.message}", e))
        }
    }
}
```

## Error Handling

Always return structured errors using the `RunnerError` factory methods for consistency.

```kotlin
// Input validation errors
return InferenceResult.error(RunnerError.invalidInput("Text input cannot be empty"))

// Processing errors
return InferenceResult.error(RunnerError.processingError("API call failed: ${e.message}", e))

// Resource errors
return InferenceResult.error(RunnerError.resourceUnavailable("Model not loaded"))
```

**Error Code Guidelines:**

Error codes are centralized in `RunnerError.Code` to ensure consistency. Use the factory methods (`RunnerError.invalidInput(...)`, `RunnerError.processingError(...)`, etc.) whenever possible.

- **E1xx**: Processing Errors (e.g., inference failure)
- **E4xx**: Client/Input Errors (e.g., invalid parameters, permissions)
- **E5xx**: Server/Resource Errors (e.g., model loading, resource unavailable)

Here is the complete list of defined codes:

| Code   | Constant                 | Description                                       |
|--------|--------------------------|---------------------------------------------------|
| `E101` | `PROCESSING_ERROR`       | General processing or inference failure.          |
| `E102` | `ASR_FAILURE`            | ASR-specific transcription failure.               |
| `E103` | `TTS_FAILURE`            | TTS-specific speech synthesis failure.            |
| `E400` | `INVALID_INPUT`          | Invalid, missing, or unsupported input.           |
| `E401` | `PERMISSION_DENIED`      | Permission denied.                                |
| `E404` | `RUNNER_NOT_FOUND`       | No suitable runner found for the request.         |
| `E405` | `CAPABILITY_NOT_SUPPORTED` | Runner does not support the requested capability. |
| `E406` | `STREAMING_NOT_SUPPORTED`| Streaming is not supported by the runner.         |
| `E500` | `MODEL_DOWNLOAD_FAILED`  | Failed to download a required model.              |
| `E501` | `RESOURCE_UNAVAILABLE`   | A required resource is not available.             |
| `E502` | `MODEL_LOAD_FAILED`      | A model failed to initialize during loading.      |
| `E503` | `HARDWARE_NOT_SUPPORTED` | Runner hardware requirements not met.             |
| `E504` | `INSUFFICIENT_RESOURCES` | Not enough resources (e.g., RAM) to run.          |

## Testing

Your runner works immediately:
```kotlin
// Engine automatically finds and uses your runner
val request = InferenceRequest(inputs = mapOf("text" to "Hello"))
val result = engineManager.runInference(request, CapabilityType.LLM)
```

## Key Points

✅ **Auto-discovery** - No registration needed  
✅ **Parameter UI** - Define schemas for automatic settings UI  
✅ **Streaming** - Implement `FlowStreamingRunner` for real-time responses  
✅ **Error handling** - Use `RunnerError` for structured errors  
✅ **Memory management** - Engine handles loading/unloading  

## Need Help?

- 📋 **Start with template** - `templates/CustomRunner.kt` 
- 🚀 **Streaming patterns** - `runner/STREAMING_GUIDE.md` for detailed streaming implementation
- 📁 **Real examples** - `executorch/`, `openrouter/`, `sherpa/`, `mock/` directories
- 🧪 **Test patterns** - `src/test/` for usage examples

**Focus on your AI logic - the engine handles the rest!**