# EdgeAI SDK

Android SDK for on-device AI inference via BreezeApp Engine.

[![JitPack](https://jitpack.io/v/mtkresearch/BreezeApp-engine.svg)](https://jitpack.io/#mtkresearch/BreezeApp-engine)

---

## 👥 Choose Your Path

### 📱 I'm a Client App Developer

**You want to**: Integrate EdgeAI SDK into your Android app

**Start here**:
1. [Quick Start](#quick-start) - 3 steps to get started
2. [Installation](#installation) - Add to your project
3. [API Reference](#api-reference) - Available APIs
4. **[Complete Documentation](./docs/client-developers/)** - All guides

> All documentation available in [docs/client-developers/](./docs/client-developers/)

---

### 🔧 I'm an SDK Maintainer

**You want to**: Contribute to EdgeAI SDK or understand its internals

**Start here**:
1. [SDK Architecture](#sdk-architecture) - Internal design
2. [Contributing](#contributing) - Development guide
3. **[Complete Documentation](./docs/maintainers/)** - All guides

> All documentation available in [docs/maintainers/](./docs/maintainers/)

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
    implementation("com.github.mtkresearch:BreezeApp-engine:EdgeAI-vX.X.X")
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

**See**: [`ChatExamples.kt`](./src/test/java/com/mtkresearch/breezeapp/edgeai/examples/ChatExamples.kt) for complete examples

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
- [`ChatExamples.kt`](./src/test/java/com/mtkresearch/breezeapp/edgeai/examples/ChatExamples.kt) - Usage patterns
- [`SDKLifecycleExamples.kt`](./src/test/java/com/mtkresearch/breezeapp/edgeai/examples/SDKLifecycleExamples.kt) - Lifecycle Tests

### API Documentation

**Detailed API docs**: [docs/client-developers/usage-guide.md](./docs/client-developers/usage-guide.md)
- Request/response models
- All parameters
- Return types


---

## SDK Architecture

> **For SDK Maintainers**

**Architecture**: Standard API → AIDL → Service (2-layer)

**See**: [docs/maintainers/architecture.md](./docs/maintainers/architecture.md) for details

**Package structure**: See [source code](./src/main/java/com/mtkresearch/breezeapp/edgeai/) for implementation details

---

## Contributing

### Development Setup

1. Clone repository
2. Open in Android Studio
3. Build: `./gradlew :EdgeAI:build`

**See**: [docs/maintainers/contributing.md](./docs/maintainers/contributing.md) for complete contribution guidelines

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
- [`ASRExamples.kt`](./src/test/java/com/mtkresearch/breezeapp/edgeai/examples/ASRExamples.kt) - ASR API tests
- [`TTSExamples.kt`](./src/test/java/com/mtkresearch/breezeapp/edgeai/examples/TTSExamples.kt) - TTS API tests

**See**: [docs/maintainers/testing.md](./docs/maintainers/testing.md) for complete testing guide