# Sherpa TTS Model Setup Guide

## Overview
This guide explains how to set up TTS models for the SherpaTTSRunner, with focus on the vits-melo-tts-zh_en model.

## Supported Model Types

### 1. VITS MR Custom Model (Your Model) ⭐
**Model Type**: `VITS_MR_20250709`
**Description**: Custom VITS TTS model (2025-07-09)

**Required Assets Structure**:
```
android/breeze-app-engine/src/main/assets/
└── vits-mr-20250709/
    ├── vits-mr-20250709.onnx   # Main TTS model
    ├── tokens.txt              # Token vocabulary
    └── lexicon.txt             # Pronunciation lexicon
```

**Usage Example**:
```kotlin
val request = InferenceRequest(
    inputs = mapOf(
        InferenceRequest.INPUT_TEXT to "Hello, this is a test with your custom model.",
        "speaker_id" to 0,
        "speed" to 1.0f
    ),
    sessionId = "tts_session_1"
)
```

### 2. VITS Piper English (Fallback)
**Model Type**: `VITS_PIPER_EN_US_AMY`
**Description**: English TTS with Amy voice

**Required Assets Structure**:
```
android/breeze-app-engine/src/main/assets/
└── vits-piper-en_US-amy-low/
    ├── en_US-amy-low.onnx      # Main TTS model
    └── espeak-ng-data/         # eSpeak NG data
        └── [espeak data files]
```

### 3. Other Supported Models
- **VITS Icefall Chinese**: `VITS_ICEFALL_ZH`
- **Matcha Icefall Chinese**: `MATCHA_ICEFALL_ZH`
- **Kokoro English**: `KOKORO_EN`

## Model Configuration

### Automatic Model Detection
The runner automatically detects your model type based on the `modelName` parameter:

```kotlin
// These will automatically use VITS_MELO_ZH_EN:
modelName = "vits-melo-tts-zh_en"
modelName = "melo_zh_en_model"
modelName = "chinese_english_tts"
```

### Manual Model Configuration
You can also create custom configurations:

```kotlin
val customConfig = SherpaTtsConfigUtil.createCustomConfig(
    context = context,
    modelDir = "your-custom-model",
    modelName = "model.onnx",
    lexicon = "lexicon.txt",
    dictDir = "your-custom-model/dict"
)
```

## Setup Instructions

### Step 1: Download Your Model
Download your vits-melo-tts-zh_en model files and place them in the assets folder:

```bash
# Your model structure should look like:
android/breeze-app-engine/src/main/assets/vits-melo-tts-zh_en/
├── model.onnx
├── lexicon.txt
└── dict/
    ├── phone.fst
    ├── date.fst
    └── number.fst
```

### Step 2: Update Runner Configuration
The runner is already configured in `runner_config.json`:

```json
{
  "TTS": {
    "defaultRunner": "sherpa_tts_runner_v1",
    "runners": {
      "sherpa_tts_runner_v1": {
        "class": "com.mtkresearch.breezeapp.engine.runner.sherpa.SherpaTTSRunner",
        "priority": 10,
        "type": "REAL",
        "enabled": true,
        "alwaysAvailable": false
      }
    }
  }
}
```

### Step 3: Initialize in Application
Add to your Application class:

```kotlin
class BreezeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Sherpa library globally
        SherpaLibraryManager.initializeGlobally()
        
        // Initialize other systems
        GlobalLibraryTracker.initialize(this)
    }
}
```

### Step 4: Usage Examples

#### Basic TTS Generation
```kotlin
val ttsRunner = SherpaTTSRunner(context)
ttsRunner.load(ModelConfig(modelName = "vits-melo-tts-zh_en"))

val request = InferenceRequest(
    inputs = mapOf(
        InferenceRequest.INPUT_TEXT to "你好，这是一个测试。Hello, this is a test.",
        "speaker_id" to 0,
        "speed" to 1.0f
    ),
    sessionId = "tts_test"
)

val result = ttsRunner.run(request)
if (result.isSuccess) {
    val audioSamples = result.outputs[InferenceResult.OUTPUT_AUDIO] as FloatArray
    val sampleRate = result.outputs["sample_rate"] as Int
    val audioFilePath = result.outputs["audio_file_path"] as String
    
    // Play the generated audio file
    ttsRunner.playAudioFile(audioFilePath)
}
```

#### Streaming TTS with Real-time Audio
```kotlin
ttsRunner.runAsFlow(request).collect { result ->
    if (result.isSuccess && !result.partial) {
        // Audio is automatically played in real-time through AudioTrack
        // Final result contains the complete audio file
        val audioFilePath = result.outputs["audio_file_path"] as String
        Log.i("TTS", "Audio saved to: $audioFilePath")
    }
}
```

#### Stop Audio Playback
```kotlin
ttsRunner.stopAudio()
```

## Model Parameters

### Speaker ID
- **Range**: 0 to N (depends on your model)
- **Default**: 0
- **Description**: Selects different voices if your model supports multiple speakers

### Speed
- **Range**: 0.1 to 3.0 (recommended)
- **Default**: 1.0
- **Description**: Controls speech speed (1.0 = normal, 0.5 = half speed, 2.0 = double speed)

### Text Input
- **Supports**: Chinese and English text (for vits-melo-tts-zh_en)
- **Mixed Language**: Can handle mixed Chinese-English sentences
- **Example**: "今天天气很好 The weather is nice today"

## Troubleshooting

### Common Issues

1. **Model Not Found**
   ```
   Error: Model assets validation failed for vits-melo-tts-zh_en
   ```
   **Solution**: Ensure all required files are in the correct assets directory structure.

2. **Library Loading Failed**
   ```
   Error: Failed to initialize Sherpa ONNX library
   ```
   **Solution**: Make sure `SherpaLibraryManager.initializeGlobally()` is called in Application.onCreate().

3. **Audio Generation Failed**
   ```
   Error: Failed to generate audio
   ```
   **Solution**: Check if the input text is valid and speaker_id/speed parameters are within valid ranges.

### Validation
Use the built-in validation to check your model setup:

```kotlin
val modelConfig = SherpaTtsConfigUtil.getTtsModelConfig(SherpaTtsConfigUtil.TtsModelType.VITS_MELO_ZH_EN)
val isValid = SherpaTtsConfigUtil.validateModelAssets(context, modelConfig)
Log.d("TTS", "Model validation: $isValid")
```

## Performance Tips

1. **Use External Storage**: Set `useExternalStorage = true` for better performance
2. **Preload Models**: Initialize the runner once and reuse it
3. **Batch Processing**: For multiple TTS requests, reuse the same runner instance
4. **Memory Management**: Call `unload()` when done to free resources

## Advanced Configuration

### Custom Model Integration
If you have a different model structure, create a custom configuration:

```kotlin
val customModelConfig = SherpaTtsConfigUtil.TtsModelConfig(
    modelDir = "your-model-directory",
    modelName = "your-model.onnx",
    lexicon = "your-lexicon.txt",
    dictDir = "your-model-directory/dict",
    description = "Your custom TTS model"
)

val config = SherpaTtsConfigUtil.createOfflineTtsConfig(context, customModelConfig)
```

This setup guide should help you get your vits-melo-tts-zh_en model working with the SherpaTTSRunner!