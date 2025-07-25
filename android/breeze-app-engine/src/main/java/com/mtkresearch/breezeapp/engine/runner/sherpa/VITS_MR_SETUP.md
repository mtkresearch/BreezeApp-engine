# VITS MR 20250709 Model Setup Guide

## Your Custom TTS Model Configuration

### Model Information
- **Model Name**: `vits-mr-20250709`
- **Model Type**: `VITS_MR_20250709`
- **Description**: Custom VITS TTS model (2025-07-09)
- **Files Required**: 3 files total

### Required Assets Structure

Place your model files in the following structure:

```
android/breeze-app-engine/src/main/assets/
└── vits-mr-20250709/
    ├── vits-mr-20250709.onnx   # Main TTS model file
    ├── tokens.txt              # Token vocabulary file
    └── lexicon.txt             # Pronunciation lexicon file
```

### Automatic Model Detection

Your model will be automatically detected when using any of these model names:
- `"vits-mr-20250709"` (exact match)
- `"mr-20250709"`
- `"vits-mr"`
- Any string containing "vits-mr" or "mr-20250709"

If no specific model is detected, the system defaults to your model.

## Quick Setup Instructions

### 1. Copy Your Model Files
```bash
# Copy your three files to the assets directory:
cp vits-mr-20250709.onnx android/breeze-app-engine/src/main/assets/vits-mr-20250709/
cp tokens.txt android/breeze-app-engine/src/main/assets/vits-mr-20250709/
cp lexicon.txt android/breeze-app-engine/src/main/assets/vits-mr-20250709/
```

### 2. Initialize in Application
```kotlin
class BreezeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SherpaLibraryManager.initializeGlobally()
        GlobalLibraryTracker.initialize(this)
    }
}
```

### 3. Test Your Model
```kotlin
// Quick test
val success = TtsTestUtil.runQuickTest(context, "Hello, this is a test.")
Log.d("TTS", "Model test: ${if (success) "PASSED" else "FAILED"}")

// Comprehensive test
val testResult = TtsTestUtil.runComprehensiveTest(context)
Log.d("TTS", "Full test: ${testResult.passedTests}/${testResult.totalTests} passed")
```

## Usage Examples

### Basic TTS Generation
```kotlin
// Initialize runner
val ttsRunner = SherpaTTSRunner(context)
ttsRunner.load(ModelConfig(modelName = "vits-mr-20250709"))

// Create TTS request
val request = InferenceRequest(
    inputs = mapOf(
        InferenceRequest.INPUT_TEXT to "Hello, this is a test with your custom model.",
        "speaker_id" to 0,
        "speed" to 1.0f
    ),
    sessionId = "vits_mr_test"
)

// Generate audio
val result = ttsRunner.run(request)
if (result.isSuccess) {
    val audioSamples = result.outputs[InferenceResult.OUTPUT_AUDIO] as FloatArray
    val sampleRate = result.outputs["sample_rate"] as Int
    val audioFilePath = result.outputs["audio_file_path"] as String
    
    // Play the generated audio
    ttsRunner.playAudioFile(audioFilePath)
    
    Log.i("TTS", "Generated ${audioSamples.size} audio samples at ${sampleRate}Hz")
    Log.i("TTS", "Audio saved to: $audioFilePath")
}
```

### Streaming TTS with Real-time Audio
```kotlin
// Streaming generation with real-time playback
ttsRunner.runAsFlow(request).collect { result ->
    if (result.isSuccess) {
        if (result.partial) {
            // Audio is playing in real-time through AudioTrack
            Log.d("TTS", "Streaming audio...")
        } else {
            // Final result with complete audio file
            val audioFilePath = result.outputs["audio_file_path"] as String
            val duration = result.metadata["audio_duration_ms"] as Long
            Log.i("TTS", "Streaming completed. Duration: ${duration}ms")
            Log.i("TTS", "Final audio file: $audioFilePath")
        }
    }
}
```

### Parameter Customization
```kotlin
// Different speaker IDs (if your model supports multiple speakers)
val multiSpeakerRequest = InferenceRequest(
    inputs = mapOf(
        InferenceRequest.INPUT_TEXT to "Testing different voices.",
        "speaker_id" to 1,  // Try different values: 0, 1, 2, etc.
        "speed" to 1.2f     // Slightly faster speech
    ),
    sessionId = "multi_speaker_test"
)

// Speed variations
val speedTests = listOf(0.8f, 1.0f, 1.5f, 2.0f)
speedTests.forEach { speed ->
    val speedRequest = InferenceRequest(
        inputs = mapOf(
            InferenceRequest.INPUT_TEXT to "Testing speed variation.",
            "speaker_id" to 0,
            "speed" to speed
        ),
        sessionId = "speed_test_$speed"
    )
    
    val result = ttsRunner.run(speedRequest)
    if (result.isSuccess) {
        val audioFile = result.outputs["audio_file_path"] as String
        Log.i("TTS", "Speed $speed: $audioFile")
    }
}
```

### Audio Control
```kotlin
// Stop current audio playback
ttsRunner.stopAudio()

// Play a specific audio file
val success = ttsRunner.playAudioFile("/path/to/your/audio.wav")
Log.d("TTS", "Audio playback: ${if (success) "started" else "failed"}")

// Cleanup when done
ttsRunner.unload()
```

## Model Validation

### Check Model Assets
```kotlin
val modelConfig = SherpaTtsConfigUtil.getTtsModelConfig(SherpaTtsConfigUtil.TtsModelType.VITS_MR_20250709)
val isValid = SherpaTtsConfigUtil.validateModelAssets(context, modelConfig)

if (isValid) {
    Log.i("TTS", "✅ All model files found and valid")
} else {
    Log.e("TTS", "❌ Model validation failed - check file structure")
}
```

### Diagnostic Information
```kotlin
val diagnostics = SherpaLibraryManager.getDiagnosticInfo()
Log.d("TTS", "Library diagnostics: $diagnostics")

val runnerInfo = ttsRunner.getRunnerInfo()
Log.d("TTS", "Runner info: $runnerInfo")
```

## Troubleshooting

### Common Issues

1. **Model files not found**
   ```
   Error: Model assets validation failed for vits-mr-20250709
   ```
   **Solution**: Verify all three files are in the correct directory:
   - `vits-mr-20250709.onnx`
   - `tokens.txt`
   - `lexicon.txt`

2. **Library loading failed**
   ```
   Error: Failed to initialize Sherpa ONNX library
   ```
   **Solution**: Ensure `SherpaLibraryManager.initializeGlobally()` is called in `Application.onCreate()`

3. **Audio generation failed**
   ```
   Error: Failed to generate audio
   ```
   **Solution**: Check input text and parameters (speaker_id ≥ 0, speed > 0)

### Validation Checklist

- [ ] Model files copied to `assets/vits-mr-20250709/`
- [ ] All three files present: `.onnx`, `tokens.txt`, `lexicon.txt`
- [ ] `SherpaLibraryManager.initializeGlobally()` called in Application
- [ ] Model validation test passes
- [ ] Quick test generates audio successfully

## Performance Tips

1. **Reuse Runner Instance**: Initialize once, use multiple times
2. **Batch Processing**: Process multiple texts with the same runner
3. **Memory Management**: Call `unload()` when completely done
4. **File Storage**: Generated audio files are saved to internal storage

## Integration Notes

- **Default Model**: Your `vits-mr-20250709` is set as the default TTS model
- **Fallback**: System falls back to VITS Piper if your model isn't available
- **Configuration**: Registered in `runner_config.json` as primary TTS runner
- **Global Library**: Uses shared Sherpa library management for efficiency

Your custom VITS MR model is now fully integrated and ready to use! 🚀