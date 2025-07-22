# API 參考

[← 回到 README](./README_zh.md) | [最佳實踐 →](./BEST_PRACTICES_zh.md)

> **完整的 API 文件**：所有 EdgeAI API 的參數、回傳類型和程式碼範例。

---

## 聊天 API

### `EdgeAI.chat(request: ChatRequest): Flow<ChatResponse>`

**參數：**
```kotlin
data class ChatRequest(
    val prompt: String,                    // 必需：輸入文字
    val maxTokens: Int = 2048,            // 可選：最大生成 token 數
    val temperature: Float = 0.7f,        // 可選：創造性（0.0-1.0）
    val topP: Float = 0.9f,              // 可選：核心採樣
    val stream: Boolean = false,          // 可選：啟用串流
    val model: String = "default"         // 可選：模型識別碼
)
```

**回應類型：**
```kotlin
sealed class ChatResponse {
    data class Stream(val delta: ChatChoice?) : ChatResponse()
    data class Final(val choices: List<ChatChoice>) : ChatResponse()
}

data class ChatChoice(
    val message: ChatMessage,
    val finishReason: String?
)

data class ChatMessage(
    val content: String,
    val role: String = "assistant"
)
```

**範例：**
```kotlin
val request = chatRequest(
    prompt = "解釋量子計算",
    maxTokens = 500,
    temperature = 0.7f,
    stream = true
)

EdgeAI.chat(request).collect { response ->
    when (response) {
        is ChatResponse.Stream -> {
            val content = response.delta?.message?.content ?: ""
            updateUI(content)
        }
        is ChatResponse.Final -> {
            val fullContent = response.choices.firstOrNull()?.message?.content
            updateUI(fullContent)
        }
    }
}
```

---

## 文字轉語音 API

### `EdgeAI.tts(request: TTSRequest): Flow<TTSResponse>`

**參數：**
```kotlin
data class TTSRequest(
    val text: String,                     // 必需：要合成的文字
    val voice: String = "default",        // 可選：語音識別碼
    val speed: Float = 1.0f,             // 可選：語音速率
    val pitch: Float = 1.0f,             // 可選：語音音調
    val volume: Float = 1.0f             // 可選：音訊音量
)
```

**回應類型：**
```kotlin
sealed class TTSResponse {
    data class Audio(val audioData: ByteArray) : TTSResponse()
    data class Error(val message: String) : TTSResponse()
}
```

**範例：**
```kotlin
val request = ttsRequest(
    text = "你好，這是一個測試訊息",
    voice = "en-US-Standard-A",
    speed = 1.0f
)

EdgeAI.tts(request).collect { response ->
    when (response) {
        is TTSResponse.Audio -> {
            playAudio(response.audioData)
        }
        is TTSResponse.Error -> {
            Log.e("TTS", "錯誤：${response.message}")
        }
    }
}
```

---

## 語音轉文字 API

### `EdgeAI.asr(request: ASRRequest): Flow<ASRResponse>`

**參數：**
```kotlin
data class ASRRequest(
    val audioData: ByteArray,             // 必需：音訊位元組
    val language: String = "en-US",       // 可選：語言代碼
    val sampleRate: Int = 16000,          // 可選：音訊取樣率
    val channels: Int = 1                 // 可選：音訊聲道
)
```

**回應類型：**
```kotlin
sealed class ASRResponse {
    data class Text(val transcription: String) : ASRResponse()
    data class Error(val message: String) : ASRResponse()
}
```

**範例：**
```kotlin
val request = asrRequest(
    audioData = audioBytes,
    language = "en-US",
    sampleRate = 16000
)

EdgeAI.asr(request).collect { response ->
    when (response) {
        is ASRResponse.Text -> {
            val transcription = response.transcription
            updateUI(transcription)
        }
        is ASRResponse.Error -> {
            Log.e("ASR", "錯誤：${response.message}")
        }
    }
}
```

---

## 初始化 API

### `EdgeAI.initializeAndWait(context: Context, timeoutMs: Long = 10000): Unit`

**參數：**
- `context`：Android Context
- `timeoutMs`：連接超時時間（毫秒）

**拋出：**
- `ServiceConnectionException`：當 BreezeApp Engine 不可用時

**範例：**
```kotlin
try {
    EdgeAI.initializeAndWait(context, timeoutMs = 10000)
    Log.i("EdgeAI", "初始化成功")
} catch (e: ServiceConnectionException) {
    Log.e("EdgeAI", "初始化失敗", e)
}
```

### `EdgeAI.shutdown(): Unit`

**範例：**
```kotlin
// 在應用程式退出時呼叫
EdgeAI.shutdown()
```

---

## 配置 API

### `EdgeAI.setLogLevel(level: LogLevel): Unit`

**LogLevel 選項：**
- `LogLevel.DEBUG`：詳細日誌
- `LogLevel.INFO`：資訊日誌
- `LogLevel.WARN`：警告日誌
- `LogLevel.ERROR`：僅錯誤日誌

**範例：**
```kotlin
// 啟用除錯日誌（開發用）
EdgeAI.setLogLevel(LogLevel.DEBUG)

// 停用日誌（生產環境）
EdgeAI.setLogLevel(LogLevel.ERROR)
```

---

## 例外類型

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