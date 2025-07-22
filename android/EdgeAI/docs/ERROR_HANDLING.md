# Error Handling

[← Back to README](../README.md) | [Usage Guide ←](./USAGE_GUIDE.md)

> **Detailed error handling strategies**: Exception types, common causes, and recommended handling approaches.

---

## Exception Hierarchy

```kotlin
sealed class EdgeAIException : Exception() {
    class ServiceConnectionException(message: String) : EdgeAIException()
    class InvalidRequestException(message: String) : EdgeAIException()
    class TimeoutException(message: String) : EdgeAIException()
    class NetworkException(message: String) : EdgeAIException()
    class AuthenticationException(message: String) : EdgeAIException()
    class UnknownException(message: String) : EdgeAIException()
}
```

---

## Exception Types & Handling

### ServiceConnectionException

**Causes:**
- BreezeApp Engine not installed
- BreezeApp Engine service not running
- AIDL binding failed
- Permission denied

**Recommended Handling:**
```kotlin
try {
    EdgeAI.initializeAndWait(context, timeoutMs = 10000)
} catch (e: ServiceConnectionException) {
    // Show user-friendly message
    showDialog("Please install BreezeApp Engine from the app store")
    // Or redirect to download page
    openAppStore("com.mtkresearch.breezeapp.engine")
}
```

### InvalidRequestException

**Causes:**
- Missing required parameters
- Invalid parameter values
- Unsupported model/voice
- Malformed request data

**Recommended Handling:**
```kotlin
EdgeAI.chat(request).catch { error ->
    when (error) {
        is InvalidRequestException -> {
            Log.e("EdgeAI", "Invalid request: ${error.message}")
            // Validate and fix request parameters
            validateAndRetry(request)
        }
    }
}.collect { response ->
    // Handle success
}
```

### TimeoutException

**Causes:**
- Network timeout
- Service response timeout
- Long-running AI operations

**Recommended Handling:**
```kotlin
EdgeAI.chat(request).catch { error ->
    when (error) {
        is TimeoutException -> {
            Log.w("EdgeAI", "Request timed out: ${error.message}")
            // Show loading indicator and retry
            showRetryDialog {
                retryRequest(request)
            }
        }
    }
}.collect { response ->
    // Handle success
}
```

### NetworkException

**Causes:**
- Network connectivity issues
- Service unavailable
- Connection reset

**Recommended Handling:**
```kotlin
EdgeAI.chat(request).catch { error ->
    when (error) {
        is NetworkException -> {
            Log.e("EdgeAI", "Network error: ${error.message}")
            // Check connectivity and retry
            if (isNetworkAvailable()) {
                retryRequest(request)
            } else {
                showOfflineMessage()
            }
        }
    }
}.collect { response ->
    // Handle success
}
```

### AuthenticationException

**Causes:**
- Invalid API key
- Expired credentials
- Unauthorized access

**Recommended Handling:**
```kotlin
EdgeAI.chat(request).catch { error ->
    when (error) {
        is AuthenticationException -> {
            Log.e("EdgeAI", "Authentication failed: ${error.message}")
            // Refresh credentials or show login
            refreshCredentials()
        }
    }
}.collect { response ->
    // Handle success
}
```

---

## Error Recovery Strategies

### Retry with Exponential Backoff

```kotlin
suspend fun retryWithBackoff(
    request: ChatRequest,
    maxRetries: Int = 3
): ChatResponse {
    var lastException: EdgeAIException? = null
    
    repeat(maxRetries) { attempt ->
        try {
            return EdgeAI.chat(request).first()
        } catch (e: EdgeAIException) {
            lastException = e
            if (attempt < maxRetries - 1) {
                delay(2.0.pow(attempt.toDouble()).toLong() * 1000)
            }
        }
    }
    
    throw lastException ?: UnknownException("Max retries exceeded")
}
```

### Graceful Degradation

```kotlin
fun handleChatRequest(request: ChatRequest) {
    viewModelScope.launch {
        try {
            EdgeAI.chat(request).collect { response ->
                updateUI(response)
            }
        } catch (e: ServiceConnectionException) {
            // Fallback to offline mode or cached responses
            showOfflineMode()
        } catch (e: TimeoutException) {
            // Show partial results or cached content
            showPartialResults()
        } catch (e: EdgeAIException) {
            // Generic error handling
            showErrorMessage(e.message)
        }
    }
}
```

---

## Debugging Tips

### Enable Detailed Logging

```kotlin
// Development only
EdgeAI.setLogLevel(LogLevel.DEBUG)

// Check logs
adb logcat | grep "EdgeAI"
```

### Common Debug Scenarios

1. **Initialization Fails**
   - Check if BreezeApp Engine is installed
   - Verify service is running: `adb shell ps | grep breezeapp`
   - Check permissions in AndroidManifest.xml

2. **Requests Timeout**
   - Increase timeout value
   - Check device performance
   - Verify model availability

3. **Invalid Responses**
   - Validate request parameters
   - Check model compatibility
   - Verify response format

---

## Best Practices

1. **Always handle exceptions** - Don't let them crash your app
2. **Provide user-friendly messages** - Don't show technical error details
3. **Implement retry logic** - For transient failures
4. **Use graceful degradation** - Provide fallback options
5. **Log errors appropriately** - For debugging but not user-facing
6. **Test error scenarios** - Include error handling in your tests 