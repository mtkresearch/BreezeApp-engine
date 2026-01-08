# BreezeApp-engine Data Flow

## Overview

This document describes how requests flow through the engine from EdgeAI SDK to AI runners. The EdgeAI SDK handles all AIDL service binding automatically - clients only need to call [`EdgeAI.initialize()`](../../android/EdgeAI/src/main/java/com/mtkresearch/breezeapp/edgeai/EdgeAI.kt#L145-L201) and use the [API](../../android/EdgeAI/docs/API_REFERENCE.md).

---

## Table of Contents

1. [LLM (Text Generation)](#llm-text-generation)
   - [Non-Streaming](#non-streaming)
   - [Streaming](#streaming)
2. [ASR (Speech Recognition)](#asr-speech-recognition)
   - [Non-Streaming (File-based)](#non-streaming-file-based)
   - [Streaming (Real-time)](#streaming-real-time)
3. [TTS (Speech Synthesis)](#tts-speech-synthesis)
   - [Non-Streaming](#non-streaming-1)
   - [Streaming](#streaming-1)
4. [Model Management](#model-management)
5. [Error Handling](#error-handling)
6. [Data Structures](#data-structures)
7. [Code References](#code-references)

---

## LLM (Text Generation)

### Non-Streaming

```
EdgeAI SDK → AIEngineManager → RunnerManager → ExecutorchLLMRunner → Model
     │                                                                  │
     │                                                                  │
     └────────────────────── Complete Response ◄────────────────────────┘
```

**Flow**:
1. Client calls `EdgeAI.chat(ChatRequest)`
2. EdgeAI SDK sends request via AIDL
3. AIEngineManager validates input
4. RunnerManager selects appropriate LLM runner
5. Runner loads model (if not already loaded)
6. Runner tokenizes input
7. Model runs inference (100ms-10s)
8. Runner detokenizes output
9. Response returned to client

**Example**:
```kotlin
val response = EdgeAI.chat(ChatRequest(
    sessionId = "session-123",
    message = "Hello, AI!"
))
// Returns complete response when ready
```

### Streaming

```
EdgeAI SDK → AIEngineManager → RunnerManager → ExecutorchLLMRunner → Model
     │                                               │                  │
     │ ◄─── Token 1 ─────────────────────────────────┘                  │
     │ ◄─── Token 2 ─────────────────────────────────┐                  │
     │ ◄─── Token 3 ─────────────────────────────────┘                  │
     │ ◄─── Complete ────────────────────────────────┐                  │
```

**Flow**:
1. Client calls `EdgeAI.chat(ChatRequest)` (returns `Flow<ChatResponse>`)
2. EdgeAI SDK sends streaming request via AIDL
3. Runner generates tokens incrementally
4. Each token emitted as `ChatResponse` with `partial = true`
5. Final response emitted with `partial = false`

**Example**:
```kotlin
EdgeAI.chat(ChatRequest(
    sessionId = "session-123",
    message = "Write a story"
)).collect { response ->
    if (response.partial) {
        // Update UI incrementally
        textView.append(response.message)
    } else {
        // Final response
        progressBar.visibility = View.GONE
    }
}
```

---

## ASR (Speech Recognition)

### Non-Streaming (File-based)

```
EdgeAI SDK → AIEngineManager → RunnerManager → SherpaOfflineASRRunner → Model
     │                                                                    │
     │ (audio file)                                                       │
     │                                                                    │
     └────────────────────── Transcription ◄──────────────────────────────┘
```

**Flow**:
1. Client calls `EdgeAI.asr(ASRRequest)` with audio data
2. EdgeAI SDK sends request via AIDL
3. Runner decodes audio (WAV/MP3 → PCM)
4. Runner resamples to 16kHz if needed
5. Runner extracts features (Mel-spectrogram)
6. Model runs ASR inference (~1x audio duration)
7. Runner decodes tokens to text
8. Transcription returned to client

**Example**:
```kotlin
val response = EdgeAI.asr(ASRRequest(
    sessionId = "session-123",
    audioData = audioBytes,
    language = "en"
))
// Returns: ASRResponse(text = "Hello world", confidence = 0.95)
```

### Streaming (Real-time)

```
EdgeAI SDK → AIEngineManager → RunnerManager → SherpaASRRunner → Model
     │                                               │                │
     │ ──── Audio chunk 1 ───────────────────────────►                │
     │ ◄─── Partial: "Hello" ────────────────────────┘                │
     │ ──── Audio chunk 2 ───────────────────────────►                │
     │ ◄─── Partial: "Hello world" ───────────────────┘               │
     │ ──── EOF ─────────────────────────────────────►                │
     │ ◄─── Final: "Hello world" (confidence) ────────┘               │
```

**Flow**:
1. Client calls `EdgeAI.asr(ASRRequest)` (returns `Flow<ASRResponse>`)
2. EdgeAI SDK sends streaming request
3. Client provides audio chunks incrementally
4. Runner processes each chunk
5. Partial results emitted as available
6. Final result emitted when stream ends

**Example**:
```kotlin
EdgeAI.asr(ASRRequest(
    sessionId = "session-123",
    language = "en"
)).collect { response ->
    if (response.partial) {
        // Show interim result
        subtitleView.text = response.text
    } else {
        // Final transcription
        saveTranscription(response.text)
    }
}
```

---

## TTS (Speech Synthesis)

### Non-Streaming

```
EdgeAI SDK → AIEngineManager → RunnerManager → SherpaTTSRunner → Model
     │                                                              │
     │ (text)                                                       │
     │                                                              │
     └────────────────────── Audio Data ◄───────────────────────────┘
```

**Flow**:
1. Client calls `EdgeAI.tts(TTSRequest)`
2. EdgeAI SDK sends request via AIDL
3. Runner normalizes text (expand numbers, handle acronyms)
4. Runner converts to phonemes
5. Model runs TTS inference (~1-3x text duration)
6. Runner encodes audio (PCM → WAV)
7. Audio data returned to client

**Example**:
```kotlin
EdgeAI.tts(TTSRequest(
    sessionId = "session-123",
    text = "Hello, world!",
    voice = "female_en_us"
)).collect { response ->
    // Play audio
    mediaPlayer.setDataSource(response.audioData)
    mediaPlayer.start()
}
```

### Streaming

```
EdgeAI SDK → AIEngineManager → RunnerManager → SherpaTTSRunner → Model
     │                                               │                │
     │ ◄─── Audio chunk 1 ───────────────────────────┘                │
     │ ◄─── Audio chunk 2 ───────────────────────────┐                │
     │ ◄─── Audio chunk 3 ───────────────────────────┘                │
     │ ◄─── Complete ────────────────────────────────┐                │
```

**Flow**:
1. Client calls `EdgeAI.tts(TTSRequest)` (returns `Flow<TTSResponse>`)
2. EdgeAI SDK sends streaming request
3. Runner generates audio incrementally
4. Audio chunks emitted as available
5. Client can start playback immediately (lower latency)

---

## Model Management

**Key Point**: Model management is **engine-side only**. Clients using EdgeAI SDK don't manage models - the engine handles everything automatically.

### How It Works

```
Client Request → Engine checks model → Auto-download if needed → Load into memory → Run inference
```

**From client perspective**:
```kotlin
// Client just calls the API
EdgeAI.chat(request)

// Engine automatically:
// 1. Downloads model if needed (first time)
// 2. Loads model into memory if needed  
// 3. Runs inference
```

**Model lifecycle**:
- **First engine startup**: Auto-downloads default models (LLM, ASR, TTS)
- **First inference request**: Runner loads model into memory (1-5s)
- **Subsequent requests**: Model already in memory (fast)

> **For detailed model management**: See [Model Management Guide](../guides/model-management.md) (engine internals, download process, storage structure)

---

## Error Handling

### Error Flow

```
EdgeAI SDK → AIEngineManager → Runner
     │                              │
     │                              │ Error occurs (e.g., OOM)
     │                              │
     │ ◄──── Exception ─────────────┘
     │
     │ Client catches exception
```

### Standard Error Codes

```kotlin
// Model errors
ERROR_MODEL_NOT_LOADED = 1
ERROR_MODEL_LOAD_FAILED = 2

// Input errors
ERROR_INVALID_INPUT = 10
ERROR_INPUT_TOO_LONG = 11

// Runtime errors
ERROR_OUT_OF_MEMORY = 20
ERROR_TIMEOUT = 21
ERROR_INFERENCE_FAILED = 22

// System errors
ERROR_SERVICE_UNAVAILABLE = 30
ERROR_INTERNAL = 99
```

### Example Error Handling

```kotlin
try {
    val response = EdgeAI.chat(request)
} catch (e: ModelNotLoadedException) {
    // Model not available
    showError("Please download the model first")
} catch (e: OutOfMemoryException) {
    // Insufficient memory
    showError("Not enough memory. Close other apps")
} catch (e: ServiceConnectionException) {
    // Engine not available
    showError("AI Engine not installed")
}
```

---

## Data Structures

### InferenceRequest

```kotlin
data class InferenceRequest(
    val sessionId: String,
    val inputs: Map<String, Any>,
    val params: Map<String, Any> = emptyMap()
)

// Standard input keys
INPUT_TEXT = "text"
INPUT_AUDIO = "audio"
INPUT_IMAGE = "image"

// Standard parameters
PARAM_TEMPERATURE = "temperature"  // 0.0-2.0
PARAM_MAX_TOKENS = "max_tokens"    // Int
PARAM_LANGUAGE = "language"        // "en", "zh", etc.
```

### InferenceResult

```kotlin
data class InferenceResult(
    val outputs: Map<String, Any>,
    val metadata: Map<String, Any> = emptyMap(),
    val error: RunnerError? = null,
    val partial: Boolean = false
)

// Standard output keys
OUTPUT_TEXT = "text"
OUTPUT_AUDIO = "audio"
OUTPUT_CONFIDENCE = "confidence"

// Standard metadata keys
META_PROCESSING_TIME_MS = "processing_time_ms"
META_MODEL_NAME = "model"
META_TOKEN_COUNT = "tokens"
```

---

## References

- [Architecture Overview](./README.md) - High-level system design
- [System Design](./system-design.md) - Component architecture
