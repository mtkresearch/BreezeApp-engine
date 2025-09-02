# Usage Guide

[← Back to README](../README.md) |  [API Reference →](./API_REFERENCE.md)

> **Advanced usage guide**: Configuration, permissions, advanced API usage, and common questions.

---

## Configuration

### Custom Parameters

```kotlin
// Using builder function (simple)
val request = chatRequest(
    prompt = "Write a short story",
    maxTokens = 500,
    temperature = 0.7f,
    stream = true
)

// Using full ChatRequest constructor (advanced)
val messages = listOf(
    ChatMessage(role = "system", content = "You are a helpful AI assistant."),
    ChatMessage(role = "user", content = "Write a short story")
)

val metadata = mutableMapOf<String, String>()
metadata["top_k"] = "40"
metadata["repetition_penalty"] = "1.1"

val request = ChatRequest(
    model = "", // Empty string means let engine decide
    messages = messages,
    temperature = 0.7f,
    topP = 0.9f,
    maxCompletionTokens = 500,
    stream = true,
    metadata = metadata
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
    // This pattern matches ChatViewModel.kt production usage
    val choice = response.choices.firstOrNull()
    
    // Handle streaming chunks (no finishReason = still streaming)
    if (choice?.finishReason == null) {
        choice?.delta?.content?.let { chunk ->
            if (chunk.isNotBlank()) {
                appendToMessage(chunk) // Append streaming chunk
            }
        }
    }
    
    // Handle final response
    choice?.message?.content?.let { finalContent ->
        setFinalMessage(finalContent) // Set complete message
    }
    
    // Check if streaming completed
    choice?.finishReason?.let { reason ->
        onStreamingComplete(reason) // "stop", "length", etc.
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

### Error Handling with Flow

```kotlin
EdgeAI.chat(request)
    .catch { error ->
        when (error) {
            is InvalidInputException -> {
                // Handle invalid input (e.g., empty prompt, invalid parameters)
                Log.e("Chat", "Invalid input: ${error.message}")
            }
            is ModelNotFoundException -> {
                // Handle model not found
                Log.e("Chat", "Model not found: ${error.message}")
            }
            is ServiceConnectionException -> {
                // Handle service not available
                Log.e("Chat", "Service connection error: ${error.message}")
            }
            is EdgeAIException -> {
                // Handle other EdgeAI errors (includes Guardian violations)
                Log.e("Chat", "EdgeAI error: ${error.message}")
            }
            else -> {
                // Handle unexpected errors
                Log.e("Chat", "Unexpected error: ${error.message}")
            }
        }
    }
    .collect { response ->
        // Handle successful response
        val content = response.choices.firstOrNull()?.let { choice ->
            choice.delta?.content ?: choice.message?.content
        } ?: ""
        updateUI(content)
    }
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