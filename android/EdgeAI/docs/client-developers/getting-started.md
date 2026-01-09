# Getting Started

Quick start guide for EdgeAI SDK.

---

## Installation

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.mtkresearch.BreezeApp-engine:EdgeAI:EdgeAI-v0.2.0")
}
```

**Prerequisites**: BreezeApp Engine installed, Android SDK 34+

---

## Quick Start

```kotlin
// 1. Initialize
EdgeAI.initializeAndWait(context)

// 2. Use API
val request = chatRequest(prompt = "Hello")
EdgeAI.chat(request).collect { response ->
    println(response.choices.first().message?.content)
}

// 3. Cleanup
EdgeAI.shutdown()
```

---

## Next Steps

- **[API Reference](../../../build/dokka/)** - Complete API docs (run `./gradlew :EdgeAI:dokkaHtml`)
- **[Examples](../../src/main/java/com/mtkresearch/breezeapp/edgeai/EdgeAIUsageExample.kt)** - Working code examples
- **[Usage Guide](./usage-guide.md)** - Advanced patterns