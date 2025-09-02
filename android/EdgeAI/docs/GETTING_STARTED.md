# Getting Started

[← Back to README](../README.md) | [Usage Guide →](./USAGE_GUIDE.md)

> **Quick start guide for new users**: Install, initialize, make your first API call, and clean up resources.

---

## Installation

### JitPack Dependency

Add EdgeAI SDK to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.mtkresearch:BreezeApp-engine:{{EDGEAI_VERSION}}")
}
```

### Local Dependency (Development)

If you're developing or modifying EdgeAI SDK:

```kotlin
dependencies {
    implementation(project(":EdgeAI"))
}
```

---

## Prerequisites

Before using EdgeAI SDK, ensure:

1. **BreezeApp Engine is installed** on the target device
2. **BreezeApp Engine Service is running** (usually auto-starts with the app)
3. **Your app has the required permissions** (see [Usage Guide](./USAGE_GUIDE.md#permissions))

---

## Quick Start

### 1. Initialize SDK

```kotlin
import com.mtkresearch.breezeapp.edgeai.*
import kotlinx.coroutines.launch

// In a CoroutineScope (e.g., lifecycleScope or viewModelScope)
launch {
    try {
        // Initialize and wait for connection to BreezeApp Engine Service
        EdgeAI.initializeAndWait(context, timeoutMs = 10000)
        Log.i("EdgeAI", "SDK connected successfully")
    } catch (e: ServiceConnectionException) {
        Log.e("EdgeAI", "Connection failed. Is BreezeApp Engine installed?", e)
        return@launch
    }
}
```

### 2. Make Your First API Call

```kotlin
// Send a simple chat request
val request = chatRequest(prompt = "Explain quantum computing in simple terms")

EdgeAI.chat(request).collect { response ->
    val content = response.choices.firstOrNull()?.message?.content
    Log.d("EdgeAI", "AI Response: $content")
}
```

### 3. Clean Up Resources

```kotlin
// In your Application.onTerminate() or when app exits
EdgeAI.shutdown()
```

---

## Next Steps

- **[Usage Guide](./USAGE_GUIDE.md)**: Advanced configuration, permissions, error handling, and FAQ
- **[API Reference](./API_REFERENCE.md)**: Complete API documentation with parameters and examples
- **[Best Practices](./BEST_PRACTICES.md)**: Lifecycle management and UI integration tips 