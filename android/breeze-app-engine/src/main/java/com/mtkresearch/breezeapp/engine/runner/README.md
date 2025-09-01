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
            InferenceResult.error(RunnerError("E101", "Generation failed: ${e.message}"))
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
                return InferenceResult.error(RunnerError("E400", "Audio input is required"))
            }
            val transcript = asrClient.transcribe(audio)
            InferenceResult.success(mapOf("text" to transcript))
        } catch (e: Exception) {
            InferenceResult.error(RunnerError("E102", "Transcription failed: ${e.message}"))
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
                return InferenceResult.error(RunnerError("E400", "Text input cannot be empty"))
            }
            val audioData = ttsClient.synthesize(text)
            InferenceResult.success(mapOf("audio_data" to audioData, "sample_rate" to 22050))
        } catch (e: Exception) {
            InferenceResult.error(RunnerError("E103", "Speech synthesis failed: ${e.message}"))
        }
    }
}
```

## Error Handling

Always return structured errors using `RunnerError`:

```kotlin
// Input validation errors (4xx)
return InferenceResult.error(RunnerError("E400", "Invalid input: text cannot be empty"))

// Processing errors (5xx) 
return InferenceResult.error(RunnerError("E101", "API call failed: ${e.message}"))

// Resource errors
return InferenceResult.error(RunnerError("E501", "Model not loaded"))
```

**Error Code Guidelines:**
- **E4xx**: Client errors (invalid input, missing params)
- **E5xx**: Server errors (model loading, API failures) 
- **E1xx**: Processing errors (inference failures)

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