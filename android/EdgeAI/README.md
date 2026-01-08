# EdgeAI SDK

Android SDK for on-device AI inference via BreezeApp Engine.

[![JitPack](https://jitpack.io/v/mtkresearch/BreezeApp-engine.svg)](https://jitpack.io/#mtkresearch/BreezeApp-engine)

## Quick Links

**For Client App Developers**:
- [Installation](#installation) - Add to your project
- [Quick Start](#quick-start) - 3 steps to get started
- [API Reference](#api-reference) - Available APIs
- [Documentation](#documentation) - Detailed guides

**For SDK Maintainers**:
- [SDK Architecture](#sdk-architecture) - Internal design
- [Contributing](#contributing) - Development guide
- [Testing](#testing) - Unit tests & examples

---

## Overview

EdgeAI SDK provides a type-safe Kotlin API for integrating on-device AI into Android apps.

**Architecture**: `Your App → EdgeAI SDK → AIDL IPC → BreezeApp Engine → AI Models`

**Capabilities**:
- **LLM** - Text generation (chat completions)
- **ASR** - Speech-to-text transcription
- **TTS** - Text-to-speech synthesis
- **Guardrail** - Content safety checking

**Key Features**:
- Simple API (3 lines to get started)
- Streaming support (real-time responses)
- Type-safe Kotlin (coroutines + Flow)
- Single dependency (JitPack)

---

## Installation

### Prerequisites

- BreezeApp Engine installed on device
- Android API 26+ (Android 8.0 Oreo)

### Add Dependency

**build.gradle.kts** (app level):
```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.mtkresearch:BreezeApp-engine:EdgeAI-v0.1.7")
}
```

**For local development**:
```kotlin
dependencies {
    implementation(project(":EdgeAI"))
}
```

---

## Quick Start

### 1. Initialize

```kotlin
import com.mtkresearch.breezeapp.edgeai.*

lifecycleScope.launch {
    EdgeAI.initializeAndWait(context, timeoutMs = 10000)
}
```

### 2. Use API

```kotlin
val request = chatRequest(prompt = "Explain quantum computing")

EdgeAI.chat(request).collect { response ->
    println(response.choices.firstOrNull()?.message?.content)
}
```

### 3. Clean Up

```kotlin
override fun onDestroy() {
    super.onDestroy()
    EdgeAI.shutdown()
}
```

**See**: [`EdgeAIUsageExample.kt`](./src/main/java/com/mtkresearch/breezeapp/edgeai/EdgeAIUsageExample.kt) for complete examples

---

## API Reference

### Available APIs

| API | Method | Description | Status |
|-----|--------|-------------|--------|
| **Chat** | `chat(ChatRequest)` | LLM text generation | ✅ Stable |
| **ASR** | `asr(ASRRequest)` | Speech-to-text | ✅ Stable |
| **TTS** | `tts(TTSRequest)` | Text-to-speech | ✅ Stable |
| **Guardrail** | `guardrail(GuardrailRequest)` | Content safety | ✅ Stable |

### Usage Examples

**See unit tests for complete examples**:
- [`EdgeAITest.kt`](./src/test/java/com/mtkresearch/breezeapp/edgeai/EdgeAITest.kt) - Unit tests
- [`EdgeAIUsageExample.kt`](./src/main/java/com/mtkresearch/breezeapp/edgeai/EdgeAIUsageExample.kt) - Usage patterns

### API Documentation

**Detailed API docs**: [docs/API_REFERENCE.md](./docs/API_REFERENCE.md)
- Request/response models
- All parameters
- Return types

---

## Documentation

### For Client Developers

- **[Getting Started](./docs/GETTING_STARTED.md)** - Installation & first API call
- **[API Reference](./docs/API_REFERENCE.md)** - Complete API documentation
- **[Usage Guide](./docs/USAGE_GUIDE.md)** - Advanced usage & configuration
- **[Error Handling](./docs/ERROR_HANDLING.md)** - Exception types & handling
- **[Best Practices](./docs/BEST_PRACTICES.md)** - Lifecycle & UI integration

### For SDK Maintainers

- **[Architecture](./docs/ARCHITECTURE.md)** - SDK design & AIDL communication
- **[JitPack Release SOP](./docs/JitPack_Release_SOP.md)** - Release process

### Related Documentation

- **[BreezeApp Engine](../breeze-app-engine/README.md)** - Engine architecture
- **[Model Management](../../docs/guides/model-management.md)** - Model lifecycle

---

## SDK Architecture

> **For SDK Maintainers**

### Simplified v2.0 Design

**Architecture**: Standard API → AIDL → Service (2-layer)

**Benefits**:
- 30% faster (eliminates serialization)
- 50% less memory
- 66% less code

**See**: [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md) for details

### Package Structure

```
com.mtkresearch.breezeapp.edgeai/
├── EdgeAI.kt              # Main SDK entry point
├── EdgeAIBuilders.kt      # Request builders (chatRequest, etc.)
├── EdgeAIExceptions.kt    # Exception types
├── ChatModels.kt          # Chat API data models
├── ASRModels.kt           # ASR API data models
├── TTSModels.kt           # TTS API data models
├── GuardrailModels.kt     # Guardrail API data models
└── EdgeAIUsageExample.kt  # Usage examples
```

**See**: [Source code](./src/main/java/com/mtkresearch/breezeapp/edgeai/) for implementation details

---

## Contributing

### Development Setup

1. Clone repository
2. Open in Android Studio
3. Build: `./gradlew :EdgeAI:build`

**See**: [docs/JitPack_Release_SOP.md](./docs/JitPack_Release_SOP.md) for contribution guidelines

---

## Testing

### Run Tests

```bash
# Unit tests
./gradlew :EdgeAI:test

# Integration tests (requires BreezeApp Engine on device)
./gradlew :EdgeAI:connectedAndroidTest
```

### Example Code

**Unit tests serve as usage examples**:
- [`EdgeAITest.kt`](./src/test/java/com/mtkresearch/breezeapp/edgeai/EdgeAITest.kt) - All API tests
- [`EdgeAIUsageExample.kt`](./src/main/java/com/mtkresearch/breezeapp/edgeai/EdgeAIUsageExample.kt) - Usage patterns