# BreezeApp Engine - Android Implementation

BreezeApp Engine is a modular AI inference service for Android, providing LLM, ASR, TTS, and VLM capabilities through a secure AIDL interface.

## Architecture

For system architecture and design patterns, see:
- [Architecture Overview](../../docs/architecture/README.md)
- [System Design](../../docs/architecture/system-design.md) - Component architecture
- [Data Flow](../../docs/architecture/data-flow.md) - Request processing flows
- [Deployment Model](../../docs/architecture/deployment-model.md) - Physical deployment


## Prebuilt Native Libraries

Located in `libs/` (source):

| Library | Purpose | Platform |
|---------|---------|----------|
| `executorch` | On-device LLM inference | arm64-v8a |
| `sherpa-onnx` | ASR/TTS processing | arm64-v8a |

See [libs/README.md](./libs/README.md) for build instructions and version information.


## Development Quick Start

### Adding a New AI Runner

See the [Runner Development Guide](../../docs/guides/runner-development.md) for step-by-step instructions on implementing a new runner.

### Adding Models

Each runner has specific model format requirements. See the README in the runner's package for details:

- **ExecuTorch LLM**: `runner/executorch/README.md` - `.pte` format models
- **Sherpa ASR/TTS**: `runner/sherpa/README.md` - `.onnx` format models
- **OpenRouter**: `runner/openrouter/README.md` - API configuration

**Recommended**: Use `ModelDownloadService` for runtime downloads (see below for usage).


## ModelDownloadService Usage

The engine provides `ModelDownloadService` for runtime model downloads.

**Benefits**:
- Progress tracking with notifications
- Automatic SHA-256 verification
- Storage management
- UI integration via `DownloadProgressActivity`

**Usage**:
```kotlin
// Trigger model download
val intent = Intent(context, ModelDownloadService::class.java).apply {
    putExtra("model_url", "https://example.com/model.pte")
    putExtra("model_name", "llama-3.2-1b")
}
context.startService(intent)
```

**Storage Path**: Downloaded models are stored in:
- **Location**: `{app_data_dir}/files/models/`
- **Example**: `/data/data/com.mtkresearch.breezeapp.engine/files/models/llama-3.2-1b.pte`

**Alternative**: For bundled models, place them in `assets/models/` during build.


## Supported AI Runners

| Runner | Capability | Backend | Streaming | Status |
|--------|-----------|---------|-----------|--------|
| ExecutorchLLMRunner | LLM | ExecuTorch (CPU) | ✅ | Stable |
| MTKLLMRunner | LLM | MTK NPU | ✅ | Stable |
| SherpaASRRunner | ASR | Sherpa-ONNX | ✅ | Stable |
| SherpaTTSRunner | TTS | Sherpa-ONNX | ✅ | Stable |
| OpenRouterLLMRunner | LLM | Remote API | ✅ | Stable |
| LlamaStackRunner | LLM/VLM | Remote API | ✅ | Experimental |

**Legend**: ✅ Supported | ❌ Not Supported



## Further Reading

- [Security Model](../../docs/security/security-model.md) - Understanding the security architecture
- [Play Store Deployment](../../docs/guides/play-store-deployment.md) - Deploying the engine to Play Store