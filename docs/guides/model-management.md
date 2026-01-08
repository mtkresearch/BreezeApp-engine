# Model Management Guide

This guide explains how models are declared, discovered, downloaded, and managed in BreezeApp-engine.

---

## Overview

**Key Principle**: Model management is **engine-side only**. Client apps using EdgeAI SDK don't manage models - the engine handles everything automatically.

**Model Lifecycle**:
1. **Declaration**: Models defined in `fullModelList.json`
2. **Discovery**: Engine reads model registry on startup
3. **Auto-Download**: Default models downloaded automatically
4. **Storage**: Models stored in engine's private directory
5. **Loading**: Runners load models into memory when needed

---

## Model Declaration

### Model Registry

Models are declared in [`fullModelList.json`](../../android/breeze-app-engine/src/main/assets/fullModelList.json):

```json
{
  "models": [
    {
      "id": "Llama3_2-3b-4096-spin-250605-cpu",
      "runner": "executorch",
      "backend": "cpu",
      "ramGB": 3,
      "capabilities": ["LLM"],
      "files": [
        {
          "fileName": "llama3_2-4096-spin.pte",
          "type": "model",
          "urls": [
            "https://huggingface.co/MediaTek-Research/Llama-3.2-3B-Instruct-SpinQuant_INT4_EO8-executorch/resolve/main/llama3_2-4096-spin.pte?download=true"
          ]
        },
        {
          "fileName": "tokenizer.bin",
          "type": "tokenizer",
          "urls": [
            "https://huggingface.co/MediaTek-Research/Llama-3.2-3B-Instruct-SpinQuant_INT4_EO8-executorch/resolve/main/tokenizer.bin?download=true"
          ]
        }
      ],
      "entry_point": {
        "type": "file",
        "value": "llama3_2-4096-spin.pte"
      }
    }
  ]
}
```

### Model Schema

| Field | Type | Description |
|-------|------|-------------|
| `id` | String | Unique model identifier (used in code) |
| `runner` | String | Runner type: `executorch`, `mediatek`, `sherpa_offline_asr_runner_v1`, etc. |
| `backend` | String | Backend type: `cpu`, `npu`, `api` |
| `ramGB` | Integer | Minimum RAM required (GB) |
| `capabilities` | Array | Capabilities: `LLM`, `ASR`, `TTS`, `VLM`, `GUARDIAN` |
| `files` | Array | List of files to download |
| `entry_point` | Object | Entry point configuration |

### File Schema

| Field | Type | Description |
|-------|------|-------------|
| `fileName` | String | Exact filename (for single files) |
| `group` | String | File group name (for pattern-based files) |
| `pattern` | String | Filename pattern with wildcards |
| `type` | String | File type: `model`, `tokenizer`, `config`, `dla`, `weights`, `embedding` |
| `urls` | Array | Download URLs (supports multiple mirrors) |

### Adding a New Model

1. **Add to `fullModelList.json`**:
```json
{
  "id": "your-model-id",
  "runner": "executorch",
  "backend": "cpu",
  "ramGB": 2,
  "capabilities": ["LLM"],
  "files": [
    {
      "fileName": "model.pte",
      "type": "model",
      "urls": ["https://your-url/model.pte"]
    }
  ],
  "entry_point": {
    "type": "file",
    "value": "model.pte"
  }
}
```

2. **Rebuild the engine** - Model registry is read from assets

3. **Model will appear** in Engine Settings UI automatically

---

## Model Discovery

### How Users See Available Models

Users can view available models in the **Engine Settings** UI:

1. Open BreezeApp Engine app
2. Navigate to "Model Management"
3. See models organized by category:
   - **LLM Models**: Text generation models
   - **ASR Models**: Speech recognition models
   - **TTS Models**: Text-to-speech models

### Model Information Displayed

For each model, users see:
- **Name**: Human-readable name
- **Size**: Total download size
- **Status**: Available, Downloading, Downloaded, Ready
- **Backend**: CPU, NPU, or API-based
- **RAM Required**: Minimum RAM needed

### Code Reference

Model discovery is handled by [`ModelManager.loadAvailableModels()`](../../android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/core/ModelManager.kt#L359-L404):

```kotlin
// Engine reads fullModelList.json on startup
private fun loadAvailableModels() {
    val json = context.assets.open("fullModelList.json").bufferedReader().use { it.readText() }
    val root = JSONObject(json)
    val models = root.optJSONArray("models") ?: JSONArray()
    
    // Parse each model and add to availableModels list
    for (i in 0 until models.length()) {
        val m = models.getJSONObject(i)
        availableModels.add(ModelInfo(...))
    }
}
```

---

## Automatic Model Downloads

### When Downloads Happen

**1. Engine First Startup**

When the engine starts for the first time, it automatically downloads default models:

```kotlin
// BreezeAppEngineService.onCreate()
private fun ensureDefaultModelReadyWithLogging() {
    val modelManager = ModelManager.getInstance(this)
    
    // Download essential categories: LLM, ASR, TTS
    val essentialCategories = listOf(
        ModelManager.ModelCategory.LLM,
        ModelManager.ModelCategory.ASR,
        ModelManager.ModelCategory.TTS
    )
    
    modelManager.downloadDefaultModels(essentialCategories, listener)
}
```

**Default Models** (as of current implementation):
- **LLM**: `Llama3_2-3b-4096-spin-250605-cpu` (2.1 GB)
- **ASR**: `Breeze-ASR-25-onnx` (varies)
- **TTS**: VITS models (varies)

**2. User-Initiated Downloads**

Users can manually download additional models via Engine Settings UI:
1. Open Engine Settings
2. Navigate to Model Management
3. Select a model
4. Tap "Download"

### Download Process

```
User/Engine → ModelManager.downloadModel(modelId)
  │
  ├─> Check if already downloaded
  │   └─> If yes: Skip
  │
  ├─> For each file in model:
  │   └─> ModelDownloadService.startDownload(url, fileName)
  │       │
  │       ├─> HTTP download with progress tracking
  │       ├─> Resume support (if interrupted)
  │       ├─> Checksum validation
  │       └─> Foreground notification with progress
  │
  └─> Mark model as DOWNLOADED when complete
```

### Download Notifications

Users see persistent notifications during downloads:
- **Title**: "Downloading [Model Name]"
- **Progress**: "45% - 1.2 GB / 2.6 GB"
- **Speed**: "5.2 MB/s"
- **ETA**: "2 minutes remaining"
- **Actions**: Pause, Resume, Cancel

### Code References

- [`ModelManager.downloadModel()`](../../android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/core/ModelManager.kt#L164-L216) - Initiates download
- [`ModelDownloadService`](../../android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/core/download/ModelDownloadService.kt) - Background download service
- [`ModelDownloadService.performDownload()`](../../android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/core/download/ModelDownloadService.kt#L271-L368) - HTTP download logic

---

## Model Storage

### Storage Structure

Models are stored in the engine's private directory:

```
/data/data/com.mtkresearch.breezeapp.engine/files/models/
├── llm/
│   ├── Llama3_2-3b-4096-spin-250605-cpu/
│   │   ├── llama3_2-4096-spin.pte (2.1 GB)
│   │   └── tokenizer.bin (500 KB)
│   └── Breeze2-3B-8W16A-250630-npu/
│       ├── BreezeTinyInstruct_v0.1_sym8W_sym16A_Overall_14layer_*.dla
│       ├── embedding_int16.bin
│       ├── shared_weights_*.bin
│       └── tokenizer.tiktoken
├── asr/
│   ├── Breeze-ASR-25-onnx/
│   │   ├── breeze-asr-25-half-encoder.int8.onnx
│   │   ├── breeze-asr-25-half-decoder.int8.onnx
│   │   └── breeze-asr-25-half-tokens.txt
│   └── sherpa-onnx-whisper-base/
│       ├── base-encoder.int8.onnx
│       ├── base-decoder.int8.onnx
│       └── base-tokens.txt
└── tts/
    └── vits-models/
        └── (TTS model files)
```

### Storage Management

Users can manage storage via Engine Settings:

**View Storage Usage**:
```kotlin
val totalUsed = ModelManager.getInstance(context).calculateTotalStorageUsed()
val byCategory = ModelManager.getInstance(context).getStorageUsageByCategory()
// Returns: Map<ModelCategory, Long> (bytes per category)
```

**Delete Models**:
```kotlin
ModelManager.getInstance(context).deleteModel(modelId)
// Removes all files for the model
```

**Cleanup Orphaned Files**:
```kotlin
ModelManager.getInstance(context).cleanupStorage()
// Removes files not referenced in fullModelList.json
```

---

## Model Loading (Runtime)

### When Models Load into Memory

Models are loaded into memory when a runner receives its first inference request:

```
Client Request → AIEngineManager → RunnerManager → ExecutorchLLMRunner
                                                          │
                                                          ├─> Check if loaded
                                                          │   └─> If no: load()
                                                          │
                                                          └─> runAsFlow()
```

### Loading Process

```kotlin
// ExecutorchLLMRunner.load()
override fun load(modelId: String, settings: EngineSettings, initialParams: Map<String, Any>): Boolean {
    // 1. Resolve model paths
    val paths = ExecutorchUtils.resolveModelPaths(context, modelId)
    modelPath = paths.modelPath
    tokenizerPath = paths.tokenizerPath
    
    // 2. Create native module
    llmModule = LlmModule(modelPath, tokenizerPath, temperature)
    
    // 3. Load into memory (JNI call)
    val loadResult = llmModule.load()
    
    // 4. Mark as loaded
    isLoaded = (loadResult == 0)
    return isLoaded
}
```

**Loading Time**: 1-5 seconds depending on model size

### Memory Management

- **Lazy Loading**: Models load only when needed
- **Unloading**: Models can be unloaded to free memory
- **Reloading**: Models reload if parameters change (e.g., temperature)

---

## User-Facing Model Management

### Engine Settings UI

Users manage models through the Engine Settings app:

**Features**:
1. **Browse Models**: View all available models by category
2. **Download Models**: Download additional models
3. **View Status**: See download progress and model status
4. **Delete Models**: Remove models to free space
5. **View Storage**: See storage usage by category
6. **Set Defaults**: Choose default model per category

### Model States

| State | Description | User Action |
|-------|-------------|-------------|
| `AVAILABLE` | Model in registry, not downloaded | Can download |
| `DOWNLOADING` | Download in progress | Can pause/cancel |
| `PAUSED` | Download paused | Can resume |
| `DOWNLOADED` | Files downloaded, not loaded | Ready to use |
| `READY` | Loaded in memory | In use |
| `ERROR` | Download/load failed | Can retry |

---

## Default Model Selection

### How Defaults Are Determined

Default models are specified in runner annotations:

```kotlin
@AIRunner(
    vendor = VendorType.EXECUTORCH,
    priority = RunnerPriority.HIGH,
    capabilities = [CapabilityType.LLM],
    defaultModel = "Llama3_2-3b-4096-spin-250605-cpu"  // ← Default model
)
class ExecutorchLLMRunner : BaseRunner, FlowStreamingRunner {
    // ...
}
```

### Changing Defaults

Users can change default models via Engine Settings:
1. Navigate to Model Management
2. Select category (LLM, ASR, TTS)
3. Choose model
4. Tap "Set as Default"

**Code**:
```kotlin
// Engine stores user preference
settings.setDefaultModel(category, modelId)

// RunnerManager uses this when selecting runner
val defaultModel = settings.getDefaultModel(ModelCategory.LLM)
```

---

## API Models (No Download)

Some models don't require downloads (API-based):

```json
{
  "id": "openai/gpt-oss-20b:free",
  "runner": "OpenRouterLLMRunner",
  "backend": "api",
  "ramGB": 1,
  "capabilities": ["LLM"],
  "files": [],  // ← No files to download
  "entry_point": {
    "type": "api",
    "value": "openai/gpt-oss-20b:free"
  }
}
```

**Characteristics**:
- No download required
- Requires internet connection
- Uses API key (configured separately)
- Lower RAM usage
- Higher latency

---

## Code References

### Core Components

- [`ModelManager.kt`](../../android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/core/ModelManager.kt) - Central model management
- [`ModelDownloadService.kt`](../../android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/core/download/ModelDownloadService.kt) - Download service
- [`ModelFile.kt`](../../android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/model/ModelFile.kt) - Model data structures
- [`fullModelList.json`](../../android/breeze-app-engine/src/main/assets/fullModelList.json) - Model registry

### Key Methods

- `ModelManager.getAvailableModels(category)` - List models
- `ModelManager.downloadModel(modelId)` - Download model
- `ModelManager.deleteModel(modelId)` - Delete model
- `ModelManager.getDefaultModel(category)` - Get default
- `ModelManager.calculateTotalStorageUsed()` - Storage usage

---

## Best Practices

### For Engine Developers

1. **Always specify default models** in runner annotations
2. **Test downloads** on slow connections
3. **Validate checksums** for downloaded files
4. **Handle download failures** gracefully
5. **Provide clear error messages** to users

### For Model Providers

1. **Use stable URLs** (avoid temporary links)
2. **Provide multiple mirrors** for reliability
3. **Include checksums** in model metadata
4. **Document RAM requirements** accurately
5. **Test on target devices** before release

### For Users

1. **Download on WiFi** to save mobile data
2. **Check storage space** before downloading large models
3. **Delete unused models** to free space
4. **Use default models** for best compatibility
5. **Check model status** in Engine Settings

---

## Troubleshooting

### Download Fails

**Symptoms**: Download stuck or fails with error

**Solutions**:
1. Check internet connection
2. Check storage space
3. Retry download
4. Try different mirror URL
5. Check logs: `adb logcat | grep ModelDownload`

### Model Not Loading

**Symptoms**: Inference fails with "model not loaded"

**Solutions**:
1. Check model is downloaded (Engine Settings)
2. Check RAM availability
3. Check model files exist in storage
4. Restart engine
5. Re-download model

### Storage Issues

**Symptoms**: "Not enough space" error

**Solutions**:
1. Delete unused models
2. Run cleanup: `ModelManager.cleanupStorage()`
3. Check storage: `ModelManager.calculateTotalStorageUsed()`
4. Free device storage

---

## See Also

- [Data Flow](../architecture/data-flow.md) - Request processing flows
- [Runner Development](./runner-development.md) - Creating custom runners
- [System Design](../architecture/system-design.md) - Overall architecture
