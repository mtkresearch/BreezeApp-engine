# Best Practices

Production tips for EdgeAI SDK.

---

## Lifecycle

**Initialize in ViewModel**:
```kotlin
class ChatViewModel : ViewModel() {
    init {
        viewModelScope.launch { EdgeAI.initializeAndWait(context) }
    }
    
    override fun onCleared() {
        EdgeAI.shutdown()
    }
}
```

---

## State Management

```kotlin
sealed class ChatState {
    object Idle : ChatState()
    object Loading : ChatState()
    data class Success(val response: String) : ChatState()
    data class Error(val message: String) : ChatState()
}
```

**See**: [Unit tests](../../src/test/java/com/mtkresearch/breezeapp/edgeai/) for implementation

---

## Performance

**Limit response length**:
```kotlin
chatRequest(prompt = "Short answer", maxTokens = 100)
```

**Cancel when done**:
```kotlin
val job = launch { EdgeAI.chat(request).collect { } }
job.cancel() // When user navigates away
```

**Limit history**:
```kotlin
val messages = conversationHistory.takeLast(10) // Keep last 10 only
```

---

## Security

**Validate input**:
```kotlin
if (input.isBlank() || input.length > 4096) {
    return // Invalid
}
```

**Safe error messages**:
```kotlin
catch (e: EdgeAIException) {
    showError("An error occurred") // Don't expose details
}
```

---

## Testing

**Unit tests**: See [`EdgeAIContractTest.kt`](../../src/test/java/com/mtkresearch/breezeapp/edgeai/EdgeAIContractTest.kt)

**All tests**: See [test directory](../../src/test/java/com/mtkresearch/breezeapp/edgeai/)

---

## Key Principles

1. Initialize in ViewModel, cleanup in onCleared()
2. Use StateFlow for reactive UI
3. Handle all exceptions
4. Validate user input
5. Limit conversation history
6. Cancel requests appropriately
7. Test thoroughly

**Complete examples**: See unit tests in source code.