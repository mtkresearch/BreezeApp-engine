# Usage Guide

[← Back to README](../README.md) |  [API Reference →](./API_REFERENCE.md)

> **Advanced usage guide**: Configuration, permissions, advanced API usage, and common questions.

---

## Configuration

### Custom Parameters

```kotlin
// Advanced chat request with custom parameters
val request = chatRequest(
    prompt = "Write a short story",
    maxTokens = 500,
    temperature = 0.7f,
    topP = 0.9f,
    stream = true  // Enable streaming for real-time responses
)
```

### Logging Configuration

```kotlin
// Enable detailed logging (development only)
EdgeAI.setLogLevel(LogLevel.DEBUG)

// Disable logging for production
EdgeAI.setLogLevel(LogLevel.ERROR)
```

---

## Permissions

Add these permissions to your `AndroidManifest.xml`:

```xml
<!-- Required for AIDL communication -->
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" />

<!-- Optional: For audio features -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

---

## Advanced API Usage

### Streaming Responses

```kotlin
EdgeAI.chat(request).collect { response ->
    when (response) {
        is ChatResponse.Stream -> {
            // Real-time streaming response
            val content = response.delta?.content ?: ""
            updateUI(content)
        }
        is ChatResponse.Final -> {
            // Final complete response
            val fullContent = response.choices.firstOrNull()?.message?.content
            updateUI(fullContent)
        }
    }
}
```

### Cancelling Requests

```kotlin
val job = launch {
    EdgeAI.chat(request).collect { response ->
        // Handle response
    }
}

// Cancel the request
job.cancel()
```

### Using Listeners (Alternative to Flow)

```kotlin
EdgeAI.chat(request, object : ChatListener {
    override fun onStream(delta: ChatChoice) {
        // Handle streaming response
    }
    
    override fun onComplete(response: ChatResponse) {
        // Handle final response
    }
    
    override fun onError(error: EdgeAIException) {
        // Handle error
    }
})
```

---

## Common Questions (FAQ)

### Q: Why does initialization fail?
**A**: Check that BreezeApp Engine is installed and running. See [Error Handling](./ERROR_HANDLING.md) for details.

### Q: How to handle network errors?
**A**: EdgeAI works offline, but check for service connection issues. See [Error Handling](./ERROR_HANDLING.md).

### Q: Can I use multiple requests simultaneously?
**A**: Yes, but manage resources carefully. See [Best Practices](./BEST_PRACTICES.md).

### Q: How to optimize performance?
**A**: Use appropriate timeouts, manage lifecycle, and avoid blocking operations. See [Best Practices](./BEST_PRACTICES.md).

---

## Error Handling

For detailed error handling strategies, see **[Error Handling](./ERROR_HANDLING.md)**.

Common patterns:

```kotlin
EdgeAI.chat(request)
    .catch { error ->
        when (error) {
            is ServiceConnectionException -> {
                // Handle service not available
            }
            is InvalidRequestException -> {
                // Handle invalid parameters
            }
            is TimeoutException -> {
                // Handle timeout
            }
            else -> {
                // Handle other errors
            }
        }
    }
    .collect { response ->
        // Handle success
    }
```

---

## Next Steps

- **[API Reference](./API_REFERENCE.md)**: Complete API documentation
- **[Error Handling](./ERROR_HANDLING.md)**: Detailed error handling strategies
- **[Best Practices](./BEST_PRACTICES.md)**: Production-ready implementation tips 