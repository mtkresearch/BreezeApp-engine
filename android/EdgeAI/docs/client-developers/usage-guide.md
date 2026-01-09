# Usage Guide

Advanced usage patterns for EdgeAI SDK.

---

## Streaming

```kotlin
EdgeAI.chat(request).collect { response ->
    val choice = response.choices.first()
    if (choice.finishReason == null) {
        // Streaming chunk
        print(choice.delta?.content)
    } else {
        // Complete
        println("\nDone: ${choice.finishReason}")
    }
}
```

---

## Error Handling

```kotlin
EdgeAI.chat(request)
    .catch { error -> handleError(error) }
    .collect { response -> handleSuccess(response) }
```

**See**: [Error Handling](./error-handling.md) for exception types

---

## Cancellation

```kotlin
val job = launch { EdgeAI.chat(request).collect { } }
job.cancel() // Cancel when needed
```

---

## Configuration

**Simple**:
```kotlin
chatRequest(prompt = "Hello", temperature = 0.7f, stream = true)
```

**Advanced**:
```kotlin
ChatRequest(
    messages = listOf(ChatMessage(role = "user", content = "Hello")),
    temperature = 0.7f,
    maxCompletionTokens = 500
)
```

---

## Examples

**All patterns**: See [unit tests](../../src/test/java/com/mtkresearch/breezeapp/edgeai/)

**Topics covered**:
- Multi-turn conversations
- TTS (text-to-speech)
- ASR (speech-to-text)
- Error recovery
- Retry logic