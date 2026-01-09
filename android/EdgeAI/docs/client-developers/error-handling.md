# Error Handling

Exception handling for EdgeAI SDK.

---

## Exception Types

```kotlin
EdgeAIException
├── ServiceConnectionException  // BreezeApp Engine unavailable
├── InvalidRequestException     // Bad parameters
├── TimeoutException           // Request timeout
├── NetworkException           // Network issues
├── AuthenticationException    // Auth failed
└── UnknownException          // Unexpected error
```

---

## Handling Strategy

```kotlin
EdgeAI.chat(request)
    .catch { error ->
        when (error) {
            is ServiceConnectionException -> showInstallDialog()
            is InvalidRequestException -> validateInput()
            is TimeoutException -> retry()
            else -> showError(error.message)
        }
    }
    .collect { response -> /* success */ }
```

---

## Retry Pattern

```kotlin
suspend fun retryWithBackoff(request: ChatRequest, maxRetries: Int = 3) {
    repeat(maxRetries) { attempt ->
        try {
            return EdgeAI.chat(request).first()
        } catch (e: EdgeAIException) {
            if (attempt < maxRetries - 1) delay(2.0.pow(attempt) * 1000)
            else throw e
        }
    }
}
```

**See**: [Unit tests](../../src/test/java/com/mtkresearch/breezeapp/edgeai/) for complete examples

---

## Debugging

```kotlin
// Enable debug logs
EdgeAI.setLogLevel(LogLevel.DEBUG)

// Check logs
adb logcat | grep "EdgeAI"
```

**Common issues**:
- Initialization fails → Check BreezeApp Engine installed
- Request timeout → Increase timeout or check device performance
- Invalid response → Validate request parameters