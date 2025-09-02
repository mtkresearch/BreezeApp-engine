# API Reference

[← Back to README](../README.md) | [Best Practices →](./BEST_PRACTICES.md)

> **Complete API documentation**: Parameters, return types, and code examples for all EdgeAI APIs.

---

## Chat API

### `EdgeAI.chat(request: ChatRequest): Flow<ChatResponse>`

**Parameters:**
```kotlin
data class ChatRequest(
    val messages: List<ChatMessage>,       // Required: Array of chat messages
    val model: String? = null,            // Optional: Model ID (engine decides if null)
    val temperature: Float? = 1f,         // Optional: Creativity (0.0-2.0)
    val maxCompletionTokens: Int? = null, // Optional: Max tokens to generate
    val stream: Boolean? = false,         // Optional: Enable streaming
    val topP: Float? = 1f,               // Optional: Nucleus sampling
    val frequencyPenalty: Float? = 0f,    // Optional: Frequency penalty (-2.0~2.0)
    val presencePenalty: Float? = 0f,     // Optional: Presence penalty (-2.0~2.0)
    val stop: List<String>? = null,       // Optional: Stop sequences (up to 4)
    val n: Int? = 1,                      // Optional: Number of responses
    val seed: Int? = null,                // Optional: Seed for reproducibility
    val user: String? = null,             // Optional: User identifier
    val tools: List<Tool>? = null,        // Optional: Available tools (up to 128)
    val toolChoice: ToolChoice? = null,   // Optional: Tool selection control
    val reasoningEffort: String? = "medium", // Optional: "low", "medium", "high"
    val webSearchOptions: WebSearchOptions? = null // Optional: Web search settings
    // ... and many more standard parameters
)
```

**Response Types:**
```kotlin
data class ChatResponse(
    val id: String,                       // Unique identifier
    val object: String,                   // "chat.completion" or "chat.completion.chunk"
    val created: Long,                    // Unix timestamp
    val model: String,                    // Model used
    val choices: List<Choice>,            // Completion choices
    val usage: Usage? = null,             // Token usage (non-streaming)
    val systemFingerprint: String? = null,
    val error: ChatError? = null          // Error information if failed
)

data class Choice(
    val index: Int,                       // Choice index
    val message: ChatMessage? = null,     // For non-streaming
    val delta: ChatMessage? = null,       // For streaming
    val finishReason: String? = null      // Why generation stopped
)

data class ChatMessage(
    val role: String,                     // "system", "user", "assistant", "tool"
    val content: String,                  // Message content
    val name: String? = null,             // Author name
    val toolCallId: String? = null,       // Tool call ID (for tool role)
    val toolCalls: List<ToolCall>? = null // Tool calls (for assistant role)
)
```

**Example (Simple using Builder):**
```kotlin
// Using builder function
val request = chatRequest(
    prompt = "Explain quantum computing",
    maxTokens = 500,
    temperature = 0.7f,
    stream = true
)

EdgeAI.chat(request).collect { response ->
    // This matches the actual production usage in ChatViewModel
    val choice = response.choices.firstOrNull()
    
    // For streaming: check if still ongoing (no finishReason)
    if (choice?.finishReason == null) {
        choice?.delta?.content?.let { chunk ->
            if (chunk.isNotBlank()) {
                appendToUI(chunk) // Streaming chunk
            }
        }
    }
    
    // For final response or non-streaming
    choice?.message?.content?.let { finalContent ->
        updateUI(finalContent) // Final content
    }
    
    // Check completion
    if (choice?.finishReason != null) {
        onStreamComplete(choice.finishReason)
    }
}
```

**Example (Full API):**
```kotlin
// Using full ChatRequest model
val messages = listOf(
    ChatMessage(role = "system", content = "You are a helpful assistant."),
    ChatMessage(role = "user", content = "Explain quantum computing")
)

val request = ChatRequest(
    messages = messages,
    model = "gpt-3.5-turbo",
    temperature = 0.7f,
    maxCompletionTokens = 500,
    stream = true
)

EdgeAI.chat(request).collect { response ->
    // Handle response
}
```

---

## Text-to-Speech API

### `EdgeAI.tts(request: TTSRequest): Flow<TTSResponse>`

**Parameters:**
```kotlin
data class TTSRequest(
    val input: String,                    // Required: Text to synthesize (max 4096 chars)
    val model: String,                    // Required: TTS model name
    val voice: String,                    // Required: Voice (alloy, ash, ballad, coral, echo, fable, onyx, nova, sage, shimmer, verse)
    val instructions: String? = null,     // Optional: Voice style instructions (gpt-4o-mini-tts only)
    val responseFormat: String? = "mp3", // Optional: mp3, opus, aac, flac, wav, pcm, pcm16
    val speed: Float? = 1.0f             // Optional: Speed (0.25-4.0)
)
```

**Response Types:**
```kotlin
data class TTSResponse(
    val audioData: ByteArray,             // Generated audio data
    val format: String = "mp3",          // Audio format
    val durationMs: Long? = null,        // Duration in milliseconds
    val sampleRate: Int? = null,         // Sample rate
    val chunkIndex: Int = 0,             // For streaming
    val isLastChunk: Boolean = true,     // For streaming
    val channels: Int = 1,               // Audio channels
    val bitDepth: Int = 16               // Bit depth
)
```

**Example:**
```kotlin
val request = ttsRequest(
    input = "Hello, this is a test message",
    voice = "alloy",
    speed = 1.0f
)

EdgeAI.tts(request).collect { response ->
    playAudio(response.audioData)
}
```

---

## Speech-to-Text API

### `EdgeAI.asr(request: ASRRequest): Flow<ASRResponse>`

**Parameters:**
```kotlin
data class ASRRequest(
    val _file: ByteArray,                 // Required: Audio file data
    val model: String,                    // Required: ASR model (e.g., "whisper-1")
    val language: String? = null,        // Optional: Language code (e.g., "en")
    val prompt: String? = null,          // Optional: Prompt to guide transcription
    val responseFormat: String? = "json", // Optional: json, text, srt, verbose_json, vtt
    val include: List<String>? = null,   // Optional: Additional data to include
    val stream: Boolean? = false,        // Optional: Enable streaming
    val temperature: Float? = 0f,        // Optional: Temperature (0.0-1.0)
    val timestampGranularities: List<String>? = listOf("segment") // Optional: "word", "segment"
)
```

**Response Types:**
```kotlin
data class ASRResponse(
    val text: String,                    // Transcribed text
    val segments: List<TranscriptionSegment>? = null, // Detailed segments (verbose_json)
    val language: String? = null,       // Detected language
    val rawResponse: String? = null,    // Raw response in requested format
    val isChunk: Boolean = false        // Whether this is streaming chunk
)

data class TranscriptionSegment(
    val id: Int,                        // Segment ID
    val seek: Int,                      // Seek position
    val start: Float,                   // Start time in seconds
    val end: Float,                     // End time in seconds
    val text: String,                   // Segment text
    val tokens: List<Int>? = null,      // Token IDs
    val temperature: Float? = null,     // Temperature used
    val avgLogprob: Float? = null,      // Average log probability
    val compressionRatio: Float? = null, // Compression ratio
    val noSpeechProb: Float? = null,    // No speech probability
    val words: List<WordTimestamp>? = null // Word-level timestamps
)

data class WordTimestamp(
    val word: String,                   // The word
    val start: Float,                   // Start time in seconds
    val end: Float                      // End time in seconds
)
```

**Example:**
```kotlin
val request = asrRequest(
    audioBytes = audioBytes,
    model = "whisper-1",
    language = "en"
)

EdgeAI.asr(request).collect { response ->
    updateUI(response.text)
}

// With detailed timestamps
val detailedRequest = asrRequestDetailed(
    audioBytes = audioBytes,
    model = "whisper-1",
    includeWordTimestamps = true
)

EdgeAI.asr(detailedRequest).collect { response ->
    response.segments?.forEach { segment ->
        println("${segment.start}-${segment.end}: ${segment.text}")
    }
}
```

---

## Initialization API

### `EdgeAI.initializeAndWait(context: Context, timeoutMs: Long = 10000): Unit`

**Parameters:**
- `context`: Android Context
- `timeoutMs`: Connection timeout in milliseconds

**Throws:**
- `ServiceConnectionException`: When BreezeApp Engine is not available

**Example:**
```kotlin
try {
    EdgeAI.initializeAndWait(context, timeoutMs = 10000)
    Log.i("EdgeAI", "Initialized successfully")
} catch (e: ServiceConnectionException) {
    Log.e("EdgeAI", "Initialization failed", e)
}
```

### `EdgeAI.shutdown(): Unit`

**Example:**
```kotlin
// Call when app exits
EdgeAI.shutdown()
```

---

## Configuration API

### `EdgeAI.setLogLevel(level: LogLevel): Unit`

**LogLevel Options:**
- `LogLevel.DEBUG`: Detailed logging
- `LogLevel.INFO`: Information logging
- `LogLevel.WARN`: Warning logging
- `LogLevel.ERROR`: Error logging only

**Example:**
```kotlin
// Enable debug logging (development)
EdgeAI.setLogLevel(LogLevel.DEBUG)

// Disable logging (production)
EdgeAI.setLogLevel(LogLevel.ERROR)
```

---

## Builder Functions

EdgeAI provides convenient builder functions to simplify common use cases:

```kotlin
// Simple chat
fun chatRequest(
    model: String? = null,
    prompt: String,
    systemPrompt: String? = null,
    temperature: Float? = null,
    maxTokens: Int? = null,
    stream: Boolean = false
): ChatRequest

// Chat with history
fun chatRequestWithHistory(
    model: String? = null,
    messages: List<ChatMessage>,
    temperature: Float? = null,
    maxTokens: Int? = null,
    stream: Boolean = false
): ChatRequest

// Simple TTS
fun ttsRequest(
    input: String,
    model: String = "tts-1",
    voice: String = "alloy",
    speed: Float? = null,
    format: String = "pcm"
): TTSRequest

// Simple ASR
fun asrRequest(
    audioBytes: ByteArray,
    model: String = "whisper-1",
    language: String? = null,
    format: String = "json",
    temperature: Float? = null,
    stream: Boolean = false
): ASRRequest

// Detailed ASR with timestamps
fun asrRequestDetailed(
    audioBytes: ByteArray,
    model: String = "whisper-1",
    language: String? = null,
    prompt: String? = null,
    format: String = "verbose_json",
    includeWordTimestamps: Boolean = false,
    temperature: Float? = null,
    stream: Boolean = false
): ASRRequest
```

---

## Exception Types

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

## Error Handling

Chat responses include structured error information:

```kotlin
data class ChatError(
    val code: String,                    // Error code (e.g., "G100" for Guardian violations)
    val message: String,                 // Human-readable message
    val type: String,                    // Error type category
    val metadata: Map<String, Any>? = null, // Additional context
    val guardianInfo: GuardianErrorInfo? = null // Guardian-specific details
)

data class GuardianErrorInfo(
    val stage: String,                   // "input" or "output"
    val safetyStatus: String,           // Safety status
    val riskScore: Double,              // Risk score (0.0-1.0)
    val riskCategories: List<String>,   // Detected risk categories
    val suggestion: String? = null,     // User-friendly suggestion
    val confidence: Double = 0.0        // Confidence score
)
```