# BreezeApp-engine Data Flow Documentation

**Purpose**: Data flow patterns and sequences for all AI capabilities
**Audience**: Developers, integration engineers, QA testers
**Last Updated**: 2025-11-03

---

## Table of Contents

1. [Service Binding Flow](#service-binding-flow)
2. [LLM Text Inference Flow](#llm-text-inference-flow)
3. [Streaming Inference Flow](#streaming-inference-flow)
4. [VLM Vision Inference Flow](#vlm-vision-inference-flow)
5. [ASR Speech Recognition Flow](#asr-speech-recognition-flow)
6. [TTS Speech Synthesis Flow](#tts-speech-synthesis-flow)
7. [Model Management Flow](#model-management-flow)
8. [Error Handling Flow](#error-handling-flow)

---

## Service Binding Flow (T065)

### Sequence Diagram

```
┌──────────┐        ┌──────────┐        ┌──────────────┐        ┌─────────────────┐
│  Client  │        │ Android  │        │ AIEngine     │        │ Signature       │
│   App    │        │  System  │        │  Service     │        │ Validator       │
└────┬─────┘        └────┬─────┘        └──────┬───────┘        └────┬────────────┘
     │                   │                     │                      │
     │ 1. bindService()  │                     │                      │
     ├──────────────────>│                     │                      │
     │                   │                     │                      │
     │                   │ 2. Check permission │                      │
     │                   │    BIND_ENGINE_SERVICE  │                      │
     │                   │    (signature level)│                      │
     │                   │                     │                      │
     │                   │ 3. onBind(intent)   │                      │
     │                   ├────────────────────>│                      │
     │                   │                     │                      │
     │                   │                     │ 4. Get calling UID   │
     │                   │                     │                      │
     │                   │                     │ 5. verifyCallerSignature(uid)
     │                   │                     ├─────────────────────>│
     │                   │                     │                      │
     │                   │                     │                      │ 6. Check cache
     │                   │                     │                      │    (LRU, 5-min TTL)
     │                   │                     │                      │
     │                   │                     │                      │ 7. Cache miss?
     │                   │                     │                      │    Get package info
     │                   │                     │                      │    Extract signature
     │                   │                     │                      │    SHA-256 hash
     │                   │                     │                      │
     │                   │                     │                      │ 8. Compare with
     │                   │                     │                      │    authorized list
     │                   │                     │                      │
     │                   │                     │ 9. Result (true/false)
     │                   │                     │<─────────────────────│
     │                   │                     │                      │
     │                   │ 10a. If authorized: │                      │
     │                   │     return binder   │                      │
     │                   │<────────────────────│                      │
     │                   │                     │                      │
     │ 11. onServiceConnected(binder)          │                      │
     │<──────────────────│                     │                      │
     │                   │                     │                      │
     │ 12. Call AIDL methods (e.g., getVersion())                     │
     ├─────────────────────────────────────────>                      │
     │                   │                     │                      │
     │                   │ 10b. If unauthorized:                      │
     │                   │      return null    │                      │
     │                   │<────────────────────│                      │
     │                   │                     │                      │
     │                   │                     │ Log audit event      │
     │                   │                     ├─────────────────────>│
     │                   │                     │                      │
     │ Binding failed    │                     │                      │
     │ (onNullBinding)   │                     │                      │
     │<──────────────────│                     │                      │
     │                   │                     │                      │
```

### Data Flow Steps

1. **Client initiates binding**:
   ```kotlin
   val intent = Intent("com.mtkresearch.breezeapp.engine.AI_SERVICE")
       .setPackage("com.mtkresearch.breezeapp.engine")
   context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
   ```

2. **Android system checks permission**:
   - Verifies client app declared `BIND_ENGINE_SERVICE` in manifest
   - Checks if client signature matches service signature (signature-level)
   - If permission denied → binding fails immediately

3. **AIEngineService.onBind() called**:
   ```kotlin
   override fun onBind(intent: Intent?): IBinder? {
       val callingUid = Binder.getCallingUid()
       // ...
   }
   ```

4. **Signature verification**:
   - Extract caller UID
   - Call `SignatureValidator.verifyCallerSignature(context, uid)`
   - Check cache first (LRU, 50 entries, 5-minute TTL)
   - On cache miss:
     - Get package name from UID
     - Extract signing certificate
     - Compute SHA-256 hash
     - Compare against `AUTHORIZED_SIGNATURES` set

5. **Return binder or null**:
   - If authorized → `return AIEngineServiceBinder()` instance
   - If unauthorized → `return null` and log audit event

6. **Client receives callback**:
   - Success: `onServiceConnected(name, binder)` → store `IAIEngineService` stub
   - Failure: `onNullBinding()` or `onServiceDisconnected()`

### Performance Characteristics

| Step | Duration | Notes |
|------|----------|-------|
| Permission check | <1ms | Android system |
| Signature verification (cached) | ~0.5ms | LRU cache hit |
| Signature verification (uncached) | ~3-5ms | Cache miss, hash computation |
| Total binding time | ~50ms | Includes IPC overhead |

---

## LLM Text Inference Flow (T066)

### Synchronous Inference

```
┌──────────┐        ┌──────────────┐        ┌─────────────┐        ┌──────────────┐
│  Client  │        │  AIEngine    │        │ LLMManager  │        │ ExecuTorch   │
│   App    │        │  Service     │        │             │        │  Runtime     │
└────┬─────┘        └──────┬───────┘        └──────┬──────┘        └──────┬───────┘
     │                     │                       │                      │
     │ 1. inferText(       │                       │                      │
     │    "Hello, AI",     │                       │                      │
     │    params)          │                       │                      │
     ├────────────────────>│                       │                      │
     │                     │                       │                      │
     │                     │ 2. Validate input     │                      │
     │                     │    (length, encoding) │                      │
     │                     │                       │                      │
     │                     │ 3. Get LLMManager     │                      │
     │                     ├──────────────────────>│                      │
     │                     │                       │                      │
     │                     │                       │ 4. Check model loaded│
     │                     │                       │    (lazy loading)    │
     │                     │                       │                      │
     │                     │                       │ 5. If not loaded:    │
     │                     │                       │    loadModel()       │
     │                     │                       ├─────────────────────>│
     │                     │                       │                      │
     │                     │                       │ Model loaded         │
     │                     │                       │<─────────────────────│
     │                     │                       │                      │
     │                     │                       │ 6. Tokenize input    │
     │                     │                       ├─────────────────────>│
     │                     │                       │                      │
     │                     │                       │ Tokens [101, 245, ...]
     │                     │                       │<─────────────────────│
     │                     │                       │                      │
     │                     │                       │ 7. Run inference     │
     │                     │                       │    (forward pass)    │
     │                     │                       ├─────────────────────>│
     │                     │                       │                      │
     │                     │                       │    [Inference: 100ms-10s]
     │                     │                       │                      │
     │                     │                       │ Output tokens        │
     │                     │                       │ [123, 456, ...]      │
     │                     │                       │<─────────────────────│
     │                     │                       │                      │
     │                     │                       │ 8. Detokenize output │
     │                     │                       ├─────────────────────>│
     │                     │                       │                      │
     │                     │                       │ "Hello! How can I..."│
     │                     │                       │<─────────────────────│
     │                     │                       │                      │
     │                     │ 9. Return result      │                      │
     │                     │<──────────────────────│                      │
     │                     │                       │                      │
     │ 10. Result string   │                       │                      │
     │<────────────────────│                       │                      │
     │                     │                       │                      │
```

### Asynchronous Inference

```
┌──────────┐        ┌──────────────┐        ┌─────────────┐        ┌──────────────┐
│  Client  │        │  AIEngine    │        │ LLMManager  │        │ ExecuTorch   │
│   App    │        │  Service     │        │             │        │  Runtime     │
└────┬─────┘        └──────┬───────┘        └──────┬──────┘        └──────┬───────┘
     │                     │                       │                      │
     │ 1. inferTextAsync(  │                       │                      │
     │    "Hello",         │                       │                      │
     │    params,          │                       │                      │
     │    callback)        │                       │                      │
     ├────────────────────>│                       │                      │
     │                     │                       │                      │
     │                     │ 2. Spawn background   │                      │
     │                     │    thread (coroutine) │                      │
     │                     │                       │                      │
     │                     │ 3. Start inference    │                      │
     │                     ├──────────────────────>│                      │
     │                     │                       ├─────────────────────>│
     │                     │                       │                      │
     │ 4. Return immediately (non-blocking)        │                      │
     │<────────────────────│                       │                      │
     │                     │                       │                      │
     │ [Client continues   │    [Background inference in progress...]      │
     │  other work]        │                       │                      │
     │                     │                       │                      │
     │                     │                       │ Inference complete   │
     │                     │                       │<─────────────────────│
     │                     │                       │                      │
     │                     │ 5. callback.onSuccess(result)                │
     │<─────────────────────────────────────────────                      │
     │                     │                       │                      │
     │ [OR if error:]      │                       │                      │
     │                     │ callback.onError(code, message)              │
     │<─────────────────────────────────────────────                      │
     │                     │                       │                      │
```

### Data Structures

**Input Parameters** (`Bundle`):
```kotlin
val params = Bundle().apply {
    putFloat("temperature", 0.7f)       // Sampling temperature (0.0-2.0)
    putInt("max_tokens", 512)           // Maximum output tokens
    putInt("top_k", 40)                 // Top-K sampling
    putFloat("top_p", 0.9f)             // Top-P (nucleus) sampling
    putFloat("repeat_penalty", 1.1f)    // Repetition penalty
    putStringArray("stop_tokens", arrayOf("</s>", "\n\n"))
}
```

**Output** (synchronous):
```kotlin
val result: String = engineService.inferText("Hello, AI", params)
// "Hello! How can I assist you today?"
```

**Output** (asynchronous callback):
```kotlin
interface IInferenceCallback {
    fun onSuccess(result: String)
    fun onError(errorCode: Int, message: String)
    fun onProgress(progress: Int) // Optional, for long-running tasks
}
```

---

## Streaming Inference Flow (T067)

### Streaming Text Generation

```
┌──────────┐        ┌──────────────┐        ┌─────────────┐        ┌──────────────┐
│  Client  │        │  AIEngine    │        │ LLMManager  │        │ ExecuTorch   │
│   App    │        │  Service     │        │             │        │  Runtime     │
└────┬─────┘        └──────┬───────┘        └──────┬──────┘        └──────┬───────┘
     │                     │                       │                      │
     │ 1. inferTextStreaming(                      │                      │
     │    "Write a story", │                       │                      │
     │    params,          │                       │                      │
     │    streamCallback)  │                       │                      │
     ├────────────────────>│                       │                      │
     │                     │                       │                      │
     │                     │ 2. Start streaming    │                      │
     │                     ├──────────────────────>│                      │
     │                     │                       │                      │
     │                     │                       │ 3. Tokenize & start  │
     │                     │                       │    inference         │
     │                     │                       ├─────────────────────>│
     │                     │                       │                      │
     │ 4. streamCallback.onStart()                 │                      │
     │<─────────────────────────────────────────────                      │
     │                     │                       │                      │
     │                     │                       │ First token generated│
     │                     │                       │<─────────────────────│
     │                     │                       │ "Once"               │
     │                     │                       │                      │
     │ 5. streamCallback.onToken("Once")           │                      │
     │<─────────────────────────────────────────────                      │
     │                     │                       │                      │
     │                     │                       │ Second token         │
     │                     │                       │<─────────────────────│
     │                     │                       │ " upon"              │
     │                     │                       │                      │
     │ 6. streamCallback.onToken(" upon")          │                      │
     │<─────────────────────────────────────────────                      │
     │                     │                       │                      │
     │ [Pattern repeats for each token...]         │                      │
     │                     │                       │                      │
     │                     │                       │ Last token (stop)    │
     │                     │                       │<─────────────────────│
     │                     │                       │ "</s>"               │
     │                     │                       │                      │
     │ 7. streamCallback.onComplete(fullText)      │                      │
     │<─────────────────────────────────────────────                      │
     │    "Once upon a time, in a faraway land..." │                      │
     │                     │                       │                      │
```

### Streaming Callback Interface

```kotlin
interface IStreamCallback {
    /**
     * Called when streaming starts
     */
    fun onStart()

    /**
     * Called for each generated token
     * @param token The new token (may be partial word)
     */
    fun onToken(token: String)

    /**
     * Called when streaming completes successfully
     * @param fullText The complete generated text
     */
    fun onComplete(fullText: String)

    /**
     * Called if an error occurs during streaming
     */
    fun onError(errorCode: Int, message: String)
}
```

### Example Client Implementation

```kotlin
val streamCallback = object : IStreamCallback.Stub() {
    val buffer = StringBuilder()

    override fun onStart() {
        runOnUiThread {
            textView.text = ""  // Clear previous content
        }
    }

    override fun onToken(token: String) {
        buffer.append(token)
        runOnUiThread {
            textView.text = buffer.toString()  // Update UI incrementally
        }
    }

    override fun onComplete(fullText: String) {
        runOnUiThread {
            progressBar.visibility = View.GONE
            // Final text should match buffer
        }
    }

    override fun onError(errorCode: Int, message: String) {
        runOnUiThread {
            Toast.makeText(context, "Error: $message", Toast.LENGTH_LONG).show()
        }
    }
}

engineService.inferTextStreaming("Write a story", params, streamCallback)
```

---

## VLM Vision Inference Flow (T068)

### Image Understanding

```
┌──────────┐        ┌──────────────┐        ┌─────────────┐        ┌──────────────┐
│  Client  │        │  AIEngine    │        │ VLMManager  │        │ ExecuTorch   │
│   App    │        │  Service     │        │             │        │  Runtime     │
└────┬─────┘        └──────┬───────┘        └──────┬──────┘        └──────┬───────┘
     │                     │                       │                      │
     │ 1. Prepare image file                       │                      │
     │    /storage/image.jpg                       │                      │
     │                     │                       │                      │
     │ 2. Open file descriptor                     │                      │
     │    ParcelFileDescriptor                     │                      │
     │                     │                       │                      │
     │ 3. inferVision(     │                       │                      │
     │    imageFd,         │                       │                      │
     │    "What's in this │                       │                      │
     │     image?",        │                       │                      │
     │    params)          │                       │                      │
     ├────────────────────>│                       │                      │
     │                     │                       │                      │
     │                     │ 4. Read image from FD │                      │
     │                     ├──────────────────────>│                      │
     │                     │                       │                      │
     │                     │                       │ 5. Decode image      │
     │                     │                       │    (JPEG/PNG → RGB)  │
     │                     │                       │                      │
     │                     │                       │ 6. Preprocess image  │
     │                     │                       │    • Resize to 224x224│
     │                     │                       │    • Normalize pixels│
     │                     │                       │    • Convert to tensor│
     │                     │                       │                      │
     │                     │                       │ 7. Encode image      │
     │                     │                       ├─────────────────────>│
     │                     │                       │                      │
     │                     │                       │ Image embeddings     │
     │                     │                       │<─────────────────────│
     │                     │                       │                      │
     │                     │                       │ 8. Tokenize prompt   │
     │                     │                       │    "What's in this   │
     │                     │                       │     image?"          │
     │                     │                       │                      │
     │                     │                       │ 9. Concatenate       │
     │                     │                       │    image embeddings +│
     │                     │                       │    text embeddings   │
     │                     │                       │                      │
     │                     │                       │ 10. Run VLM inference│
     │                     │                       ├─────────────────────>│
     │                     │                       │                      │
     │                     │                       │  [Inference: 200ms-5s]
     │                     │                       │                      │
     │                     │                       │ Output: "This image  │
     │                     │                       │ shows a cat sitting  │
     │                     │                       │ on a sofa..."        │
     │                     │                       │<─────────────────────│
     │                     │                       │                      │
     │                     │ 11. Return result     │                      │
     │                     │<──────────────────────│                      │
     │                     │                       │                      │
     │ 12. Result string   │                       │                      │
     │<────────────────────│                       │                      │
     │                     │                       │                      │
     │ 13. Close file descriptor                   │                      │
     │                     │                       │                      │
```

### Using ParcelFileDescriptor

**Why file descriptors instead of byte arrays?**
- AIDL Binder has 1MB transaction limit
- Images can be 5-20MB (high resolution)
- File descriptors pass only the file handle (4 bytes), not content

**Client-side code**:
```kotlin
val imageFile = File("/storage/emulated/0/DCIM/photo.jpg")
val imageFd = ParcelFileDescriptor.open(
    imageFile,
    ParcelFileDescriptor.MODE_READ_ONLY
)

val result = engineService.inferVision(
    imageFd,
    "What objects are in this image?",
    params
)

imageFd.close()  // Always close when done
```

**Service-side code** (simplified):
```kotlin
override fun inferVision(
    imageFd: ParcelFileDescriptor?,
    prompt: String?,
    params: Bundle?
): String {
    // Read image from file descriptor
    val fileInputStream = FileInputStream(imageFd?.fileDescriptor)
    val bitmap = BitmapFactory.decodeStream(fileInputStream)

    // Process image + prompt with VLM
    val result = vlmManager.infer(bitmap, prompt, params)

    return result
}
```

---

## ASR Speech Recognition Flow (T069)

### Audio File Recognition

```
┌──────────┐        ┌──────────────┐        ┌─────────────┐        ┌──────────────┐
│  Client  │        │  AIEngine    │        │ ASRManager  │        │ Sherpa ONNX  │
│   App    │        │  Service     │        │             │        │  Runtime     │
└────┬─────┘        └──────┬───────┘        └──────┬──────┘        └──────┬───────┘
     │                     │                       │                      │
     │ 1. Record audio     │                       │                      │
     │    /storage/audio.wav                       │                      │
     │                     │                       │                      │
     │ 2. Open file descriptor                     │                      │
     │    ParcelFileDescriptor                     │                      │
     │                     │                       │                      │
     │ 3. recognizeSpeech( │                       │                      │
     │    audioFd,         │                       │                      │
     │    params)          │                       │                      │
     ├────────────────────>│                       │                      │
     │                     │                       │                      │
     │                     │ 4. Read audio from FD │                      │
     │                     ├──────────────────────>│                      │
     │                     │                       │                      │
     │                     │                       │ 5. Decode audio      │
     │                     │                       │    (WAV/MP3 → PCM)   │
     │                     │                       │                      │
     │                     │                       │ 6. Resample to 16kHz │
     │                     │                       │    (if needed)       │
     │                     │                       │                      │
     │                     │                       │ 7. Extract features  │
     │                     │                       │    (Mel-spectrogram) │
     │                     │                       │                      │
     │                     │                       │ 8. Run ASR inference │
     │                     │                       ├─────────────────────>│
     │                     │                       │                      │
     │                     │                       │   [Inference: ~1x    │
     │                     │                       │    audio duration]   │
     │                     │                       │                      │
     │                     │                       │ CTC output / tokens  │
     │                     │                       │<─────────────────────│
     │                     │                       │                      │
     │                     │                       │ 9. Decode tokens     │
     │                     │                       │    "Hello world"     │
     │                     │                       │                      │
     │                     │ 10. Return Bundle     │                      │
     │                     │<──────────────────────│                      │
     │                     │                       │                      │
     │ 11. Result Bundle   │                       │                      │
     │<────────────────────│                       │                      │
     │     text: "Hello world"                     │                      │
     │     confidence: 0.95                        │                      │
     │     duration: 1.2s  │                       │                      │
     │                     │                       │                      │
```

### Streaming ASR

```
┌──────────┐        ┌──────────────┐        ┌─────────────┐        ┌──────────────┐
│  Client  │        │  AIEngine    │        │ ASRManager  │        │ Sherpa ONNX  │
│   App    │        │  Service     │        │             │        │  Runtime     │
└────┬─────┘        └──────┬───────┘        └──────┬──────┘        └──────┬───────┘
     │                     │                       │                      │
     │ 1. recognizeSpeechStreaming(                │                      │
     │    params,          │                       │                      │
     │    streamCallback)  │                       │                      │
     ├────────────────────>│                       │                      │
     │                     │                       │                      │
     │                     │ 2. Create audio pipe  │                      │
     │                     │    (ParcelFileDescriptor.createPipe())       │
     │                     │                       │                      │
     │ 3. Return write FD  │                       │                      │
     │<────────────────────│                       │                      │
     │                     │                       │                      │
     │ 4. Client writes audio chunks (e.g., 100ms) │                      │
     │ ──── audioData ────>│                       │                      │
     │                     ├──────────────────────>│                      │
     │                     │                       ├─────────────────────>│
     │                     │                       │                      │
     │                     │                       │ Partial result       │
     │                     │                       │<─────────────────────│
     │                     │                       │ "Hello"              │
     │                     │                       │                      │
     │ 5. streamCallback.onPartialResult("Hello")  │                      │
     │<─────────────────────────────────────────────                      │
     │                     │                       │                      │
     │ ──── more data ────>│                       │                      │
     │                     ├──────────────────────>│                      │
     │                     │                       ├─────────────────────>│
     │                     │                       │                      │
     │                     │                       │ Updated result       │
     │                     │                       │<─────────────────────│
     │                     │                       │ "Hello world"        │
     │                     │                       │                      │
     │ 6. streamCallback.onPartialResult("Hello world")                   │
     │<─────────────────────────────────────────────                      │
     │                     │                       │                      │
     │ 7. Client closes FD (end of stream)         │                      │
     │ ────── EOF ────────>│                       │                      │
     │                     │                       │                      │
     │                     │                       │ Final result         │
     │                     │                       │<─────────────────────│
     │                     │                       │ "Hello world"        │
     │                     │                       │                      │
     │ 8. streamCallback.onFinalResult("Hello world", confidence)         │
     │<─────────────────────────────────────────────                      │
     │                     │                       │                      │
```

---

## TTS Speech Synthesis Flow

### Text-to-Speech Conversion

```
┌──────────┐        ┌──────────────┐        ┌─────────────┐        ┌──────────────┐
│  Client  │        │  AIEngine    │        │ TTSManager  │        │ Sherpa ONNX  │
│   App    │        │  Service     │        │             │        │  Runtime     │
└────┬─────┘        └──────┬───────┘        └──────┬──────┘        └──────┬───────┘
     │                     │                       │                      │
     │ 1. synthesizeSpeech(│                       │                      │
     │    "Hello, world!", │                       │                      │
     │    params)          │                       │                      │
     ├────────────────────>│                       │                      │
     │                     │                       │                      │
     │                     │ 2. Validate text      │                      │
     │                     │    (length, encoding) │                      │
     │                     │                       │                      │
     │                     │ 3. Get TTSManager     │                      │
     │                     ├──────────────────────>│                      │
     │                     │                       │                      │
     │                     │                       │ 4. Text normalization│
     │                     │                       │    • Expand numbers  │
     │                     │                       │      "123" → "one    │
     │                     │                       │       twenty-three"  │
     │                     │                       │    • Handle acronyms │
     │                     │                       │    • Phonetic rules  │
     │                     │                       │                      │
     │                     │                       │ 5. Phoneme conversion│
     │                     │                       │    "Hello, world!"   │
     │                     │                       │    → [HH, EH, L, ...] │
     │                     │                       │                      │
     │                     │                       │ 6. TTS inference     │
     │                     │                       ├─────────────────────>│
     │                     │                       │                      │
     │                     │                       │ [Inference: ~1-3x    │
     │                     │                       │  text duration]      │
     │                     │                       │                      │
     │                     │                       │ Audio waveform       │
     │                     │                       │ (PCM, 16kHz)         │
     │                     │                       │<─────────────────────│
     │                     │                       │                      │
     │                     │                       │ 7. Encode to WAV     │
     │                     │                       │                      │
     │                     │                       │ 8. Write to temp file│
     │                     │                       │    /cache/tts_xxx.wav│
     │                     │                       │                      │
     │                     │ 9. Return FD          │                      │
     │                     │<──────────────────────│                      │
     │                     │                       │                      │
     │ 10. ParcelFileDescriptor (read audio)       │                      │
     │<────────────────────│                       │                      │
     │                     │                       │                      │
     │ 11. Read audio from FD                      │                      │
     │     Play with MediaPlayer                   │                      │
     │                     │                       │                      │
     │ 12. Close FD & delete temp file             │                      │
     │                     │                       │                      │
```

### TTS Parameters

```kotlin
val params = Bundle().apply {
    putString("voice", "female_en_us")   // Voice selection
    putFloat("speed", 1.0f)               // Speech rate (0.5-2.0)
    putFloat("pitch", 1.0f)               // Pitch adjustment (0.5-2.0)
    putInt("sampleRate", 16000)           // Output sample rate
    putString("format", "wav")            // Output format (wav/mp3)
}
```

---

## Model Management Flow

### Model Loading

```
┌──────────┐        ┌──────────────┐        ┌─────────────┐
│  Client  │        │  AIEngine    │        │ Model       │
│   App    │        │  Service     │        │ Manager     │
└────┬─────┘        └──────┬───────┘        └──────┬──────┘
     │                     │                       │
     │ 1. loadModel(       │                       │
     │    "llama-3b-q4")   │                       │
     ├────────────────────>│                       │
     │                     │                       │
     │                     │ 2. Check if already   │
     │                     │    loaded             │
     │                     ├──────────────────────>│
     │                     │                       │
     │                     │                       │ 3. If not loaded:
     │                     │                       │    • Find model file
     │                     │                       │    • Validate checksum
     │                     │                       │    • Load to memory
     │                     │                       │    • Initialize runtime
     │                     │                       │
     │                     │                       │ [Loading: 1-5s]
     │                     │                       │
     │                     │ 4. Model loaded       │
     │                     │<──────────────────────│
     │                     │                       │
     │ 5. SUCCESS          │                       │
     │<────────────────────│                       │
     │                     │                       │
```

### Model Discovery

```
Client: listModels("llm")
  │
  ├─> Engine scans directories:
  │     /data/data/com.mtkresearch.breezeapp.engine/files/models/llm/
  │
  ├─> Returns Bundle array:
  │     [
  │       {
  │         "modelId": "llama-3b-q4",
  │         "name": "Llama 3B Quantized (Q4)",
  │         "size": 2147483648,  // bytes
  │         "format": "executorch",
  │         "loaded": true
  │       },
  │       {
  │         "modelId": "llama-7b-q8",
  │         "name": "Llama 7B Quantized (Q8)",
  │         "size": 7516192768,
  │         "format": "executorch",
  │         "loaded": false
  │       }
  │     ]
  │
  └─> Client displays in UI
```

---

## Error Handling Flow

### Error Propagation

```
┌──────────┐        ┌──────────────┐        ┌─────────────┐
│  Client  │        │  AIEngine    │        │ Manager     │
│   App    │        │  Service     │        │             │
└────┬─────┘        └──────┬───────┘        └──────┬──────┘
     │                     │                       │
     │ Request             │                       │
     ├────────────────────>│                       │
     │                     ├──────────────────────>│
     │                     │                       │
     │                     │                       │ Error occurs
     │                     │                       │ (e.g., OOM)
     │                     │                       │
     │                     │ Exception/Error code  │
     │                     │<──────────────────────│
     │                     │                       │
     │                     │ Map to standard       │
     │                     │ error code            │
     │                     │                       │
     │ Callback/Exception  │                       │
     │<────────────────────│                       │
     │                     │                       │
```

### Standard Error Codes

```kotlin
// Defined in IAIEngineService.aidl
const val ERROR_NONE = 0
const val ERROR_MODEL_NOT_LOADED = 1
const val ERROR_INVALID_INPUT = 2
const val ERROR_OUT_OF_MEMORY = 3
const val ERROR_TIMEOUT = 4
const val ERROR_UNSUPPORTED_OPERATION = 5
const val ERROR_INTERNAL = 99

// Usage in callbacks
callback.onError(ERROR_MODEL_NOT_LOADED, "LLM model not loaded. Call loadModel() first.")
```

---

**Document Version**: 1.0
**Maintained By**: BreezeApp Team
**Last Updated**: 2025-11-03
