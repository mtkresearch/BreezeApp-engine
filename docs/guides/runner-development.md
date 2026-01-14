# Runner Development Guide

Create custom AI runners for BreezeApp Engine in 3 simple steps.

## Quick Start

### 1. Copy the Template
Under the `android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/runner` directory,
```bash
cp templates/CustomRunner.kt yourvendor/YourRunner.kt
```

### 2. Update the Annotation
```kotlin
@AIRunner(
    vendor = VendorType.UNKNOWN,           // Choose your vendor
    priority = RunnerPriority.NORMAL,      // HIGH/NORMAL/LOW  
    capabilities = [CapabilityType.LLM],   // LLM/ASR/TTS/VLM/GUARDIAN
    defaultModel = "your-model-name" // Align with fullModelList.json 'id' for local models, or API name for cloud models.
)
class YourRunner : BaseRunner, FlowStreamingRunner {
    // Implementation...
}
```

### 3. Implement Your AI Logic
```kotlin
// The `run` method from `BaseRunner` and `runAsFlow` from `FlowStreamingRunner` are the primary entry points
// for your runner's inference logic. This is where your runner receives the `InferenceRequest` and returns an `InferenceResult`.

// Example `run` method implementation:
override fun run(request: InferenceRequest): InferenceResult {
    // Input data (text, audio, image) is in `request.inputs`.
    val inputText = request.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""

    // Your core AI logic (e.g., calling an API or local model)
    val aiResponse = apiClient.generateText(inputText)

    // Output should be returned via `InferenceResult.textOutput()`, `InferenceResult.audioOutput()`, etc.
    return InferenceResult.textOutput(text = aiResponse)
}

// For streaming runners, implement `runAsFlow` (see template for examples).
// Example `runAsFlow` method implementation:
override fun runAsFlow(request: InferenceRequest): Flow<InferenceResult> = flow {
    val inputText = request.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
    var accumulatedText = ""

    // Simulate streaming output
    apiClient.streamGenerate(inputText) { chunk ->
        accumulatedText += chunk
        emit(InferenceResult.textOutput(text = accumulatedText, partial = true))
    }
    // Emit final result
    emit(InferenceResult.textOutput(text = accumulatedText, partial = false))
}
```
For more detailed streaming patterns and best practices, refer to: [Streaming Implementation Guide](../android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/runner/STREAMING_GUIDE.md)

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

## Step 4: Create Tests for Your Runner

Every new Runner should have corresponding contract tests to ensure compliance with the interface.

### Create Contract Test Class

```kotlin
// For LLM Runners - inherit from LLMRunnerContractTest
class MyLLMRunnerContractTest : LLMRunnerContractTest<MyLLMRunner>() {
    override fun createRunner() = MyLLMRunner()
    override val defaultModelId = "my-model-id"
}

// For ASR Runners - inherit from ASRRunnerContractTest
class MyASRRunnerContractTest : ASRRunnerContractTest<MyASRRunner>() {
    override fun createRunner() = MyASRRunner()
    override val defaultModelId = "my-asr-model"
}

// For TTS Runners - inherit from TTSRunnerContractTest
class MyTTSRunnerContractTest : TTSRunnerContractTest<MyTTSRunner>() {
    override fun createRunner() = MyTTSRunner()
    override val defaultModelId = "my-tts-model"
}
```

### Run Your Tests

```bash
# Via CLI tool
cd android/scripts
./runner-test.sh --runner=MyLLMRunner verify llm

# Via Gradle
./gradlew :breeze-app-engine:testDebugUnitTest --tests "*MyLLMRunnerContractTest*"
```

### Test Coverage

Your Runner will automatically be tested for:

| Category | Tests |
|----------|-------|
| **Lifecycle** | load, unload, isLoaded, idempotency |
| **Run** | valid input, empty input, not-loaded error |
| **Info** | getRunnerInfo, getCapabilities, isSupported |
| **Parameters** | getParameterSchema, validateParameters |
| **Errors** | error codes, error messages |

For LLM Runners with streaming:
- `runAsFlow` emission, partial results, cancellation handling

For detailed testing documentation, see [Testing Guide](testing-guide.md).

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

### Enabling CLI Verification (Zero-Code Testing)

To test your new Runner immediately using the `quick-test` CLI (without writing any extra test code), ensure your `load()` method reads parameters from `EngineSettings`.

**Implementation Pattern:**

```kotlin
override fun load(modelId: String, settings: EngineSettings, context: Context?): Boolean {
    // 1. Get parameters injected from CLI (--param:key=value) or App Settings
    // The 'name' property is automatically derived from @AIRunner or class name
    val runnerParams = settings.getRunnerParameters(this.getRunnerInfo().name)
    
    // 2. Read specific keys
    val apiKey = runnerParams["api_key"] as? String
    
    // 3. Initialize your client
    if (apiKey.isNullOrBlank()) {
         return false // Load failure
    }
    
    this.apiClient = MyApiClient(apiKey)
    return true
}
```

**Verify Immediately:**

```bash
./runner-test.sh --runner=MyNewRunner \
  --param:api_key=your_key \
  --input="Test" \
  quick-test
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

Detailed error codes are defined in `engine/model/RunnerError.kt`.

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