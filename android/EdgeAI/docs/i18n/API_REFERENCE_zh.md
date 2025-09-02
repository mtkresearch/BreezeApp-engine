# API 參考

[← 回到 README](./README_zh.md) | [最佳實踐 →](./BEST_PRACTICES_zh.md)

> **完整的 API 文件**：所有 EdgeAI API 的參數、回傳類型和程式碼範例。

---

## 聊天 API

### `EdgeAI.chat(request: ChatRequest): Flow<ChatResponse>`

**參數：**
```kotlin
data class ChatRequest(
    /**
     * 聊天訊息陣列。每個訊息必須包含角色和內容。
     */
    val messages: List<ChatMessage>,
    
    /**
     * 用於生成回應的模型 ID。若為 null，引擎將使用設定的預設模型。
     */
    val model: String? = null,
    
    /**
     * 音訊輸出參數。請求音訊輸出時必需。
     */
    val audio: AudioParams? = null,
    
    /**
     * 範圍：-2.0 ~ 2.0。正值會懲罰頻繁出現的 token。
     */
    val frequencyPenalty: Float? = 0f,
    
    /**
     * 調整特定 token 的機率。格式：token ID 映射到偏差值 (-100~100)。
     */
    val logitBias: Map<String, Float>? = null,
    
    /**
     * 是否回傳輸出 token 的對數機率。
     */
    val logprobs: Boolean? = false,
    
    /**
     * 限制模型生成的最大 token 數。
     */
    val maxResults: Int? = 10
) : Parcelable

@Parcelize
data class LogProbs(
    val tokens: List<String>,
    val tokenLogprobs: List<Float>,
    val topLogprobs: List<Map<String, Float>>? = null
) : Parcelable

@Parcelize
data class CompletionTokensDetails(
    val reasoningTokens: Int? = null
) : Parcelable
    
    /**
     * 指定所需的輸出模式。預設是 ["text"]。
     */
    val modalities: List<String>? = listOf("text"),
    
    /**
     * 為每個輸入訊息生成的回應選項數量。
     */
    val n: Int? = 1,
    
    /**
     * 是否啟用平行工具（函數）呼叫。
     */
    val parallelToolCalls: Boolean? = true,
    
    /**
     * 預測輸出設定，用於更快的回應。
     */
    val prediction: PredictionParams? = null,
    
    /**
     * 範圍：-2.0 ~ 2.0。正值鼓勵新主題。
     */
    val presencePenalty: Float? = 0f,
    
    /**
     * 僅適用於 o 系列。限制推理努力。選項：low、medium、high。
     */
    val reasoningEffort: String? = "medium",
    
    /**
     * 指定回應格式。
     */
    val responseFormat: ResponseFormat? = null,
    
    /**
     * 實驗性。系統會嘗試為相同種子產生相同結果。
     */
    val seed: Int? = null,
    
    /**
     * 服務層級。選項：auto、default、flex。
     */
    val serviceTier: String? = "auto",
    
    /**
     * 最多 4 個停止序列。遇到任何序列時生成停止。
     */
    val stop: List<String>? = null,
    
    /**
     * 是否儲存此完成以供模型蒸餾或評估使用。
     */
    val store: Boolean? = false,
    
    /**
     * 是否以串流形式回傳資料（伺服器發送事件）。
     */
    val stream: Boolean? = false,
    
    /**
     * 串流回應選項，僅在 stream: true 時可用。
     */
    val streamOptions: StreamOptions? = null,
    
    /**
     * 範圍：0~2。控制隨機性；較高的值更隨機。
     */
    val temperature: Float? = 1f,
    
    /**
     * 控制模型是否呼叫工具。
     */
    val toolChoice: ToolChoice? = null,
    
    /**
     * 工具清單，目前僅支援函數。最多 128 個函數。
     */
    val tools: List<Tool>? = null,
    
    /**
     * 0~20。對每個 token，回傳最可能的前 N 個 token 及其對數機率。
     */
    val topLogprobs: Int? = null,
    
    /**
     * 範圍：0~1。核心採樣；僅考慮累積機率質量到 top_p 的 token。
     */
    val topP: Float? = 1f,
    
    /**
     * 唯一使用者識別碼，對濫用檢測和追蹤很有用。
     */
    val user: String? = null,
    
    /**
     * 網路搜尋工具設定，允許模型查詢線上資訊。
     */
    val webSearchOptions: WebSearchOptions? = null
)
```

**回應類型：**
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

**範例：**
```kotlin
val request = chatRequest(
    prompt = "解釋量子計算",
    maxTokens = 500,
    temperature = 0.7f,
    stream = true
)

EdgeAI.chat(request).collect { response ->
    // 這與 ChatViewModel 中的實際生產用法相符
    val choice = response.choices.firstOrNull()
    
    // 對於串流：檢查是否仍在進行中（沒有 finishReason）
    if (choice?.finishReason == null) {
        choice?.delta?.content?.let { chunk ->
            if (chunk.isNotBlank()) {
                appendToUI(chunk) // 串流區塊
            }
        }
    }
    
    // 對於最終回應或非串流
    choice?.message?.content?.let { finalContent ->
        updateUI(finalContent) // 最終內容
    }
    
    // 檢查完成
    if (choice?.finishReason != null) {
        onStreamComplete(choice.finishReason)
    }
}

**範例（完整 API）：**
```kotlin
// 使用完整的 ChatRequest 模型
val messages = listOf(
    ChatMessage(role = "system", content = "您是一個有用的助手。"),
    ChatMessage(role = "user", content = "解釋量子計算")
)

val request = ChatRequest(
    messages = messages,
    model = "gpt-3.5-turbo",
    temperature = 0.7f,
    maxCompletionTokens = 500,
    stream = true
)

EdgeAI.chat(request).collect {
    // 處理回應
}

```

---

## 文字轉語音 API

### `EdgeAI.tts(request: TTSRequest): Flow<TTSResponse>`

**參數：**
```kotlin
data class TTSRequest(
    /**
     * 要轉換為語音的文字。最大長度：4096 個字元。
     */
    val input: String,
    
    /**
     * 用於生成的 TTS 模型名稱
     */
    val model: String,
    
    /**
     * 語音風格。支援的值：alloy、ash、ballad、coral、echo、fable、onyx、nova、sage、shimmer、verse。
     */
    val voice: String,
    
    /**
     * 控制語音風格的額外指示。
     * 僅 gpt-4o-mini-tts 支援；tts-1/tts-1-hd 不支援。
     */
    val instructions: String? = null,
    
    /**
     * 輸出音訊格式。支援的值：mp3、opus、aac、flac、wav、pcm、pcm16。
     * 預設：mp3
     */
    val responseFormat: String? = "mp3",
    
    /**
     * 播放速度，範圍：0.25~4.0，預設為 1.0
     */
    val speed: Float? = 1.0f
)
```

**回應類型：**
```kotlin
data class TTSResponse(
    /**
     * 生成的音訊資料作為位元組陣列
     */
    val audioData: ByteArray,
    
    /**
     * 音訊格式（mp3、wav 等）
     */
    val format: String = "mp3",
    
    /**
     * 持續時間（毫秒）（如果可用）
     */
    val durationMs: Long? = null,
    
    /**
     * 取樣率（如果可用）
     */
    val sampleRate: Int? = null,
    
    /**
     * 串流音訊的區塊索引
     */
    val chunkIndex: Int = 0,
    
    /**
     * 在串流模式中是否為最後一個區塊
     */
    val isLastChunk: Boolean = true,
    
    /**
     * 音訊聲道數
     */
    val channels: Int = 1,
    
    /**
     * 音訊位元深度
     */
    val bitDepth: Int = 16
)
```

**範例：**
```kotlin
val request = ttsRequest(
    input = "你好，這是一個測試訊息",
    voice = "alloy",
    speed = 1.0f
)

EdgeAI.tts(request).collect { response ->
    playAudio(response.audioData)
}
```

---

## 語音轉文字 API

### `EdgeAI.asr(request: ASRRequest): Flow<ASRResponse>`

**參數：**
```kotlin
data class ASRRequest(
    /**
     * 音訊檔案資料作為位元組陣列
     */
    val file: ByteArray,
    
    /**
     * 要使用的 ASR 模型名稱（例如，"whisper-1"）
     */
    val model: String,
    
    /**
     * 音訊的語言代碼（例如，"en"、"zh"）。若為 null，將自動檢測語言。
     */
    val language: String? = null,
    
    /**
     * 指導轉錄風格的可選提示
     */
    val prompt: String? = null,
    
    /**
     * 回應格式。支援的值：json、text、srt、verbose_json、vtt
     */
    val responseFormat: String? = "json",
    
    /**
     * 要包含在回應中的額外資料
     */
    val include: List<String>? = null,
    
    /**
     * 是否啟用串流轉錄
     */
    val stream: Boolean? = false,
    
    /**
     * 取樣溫度（0.0-1.0）。較高的值增加隨機性。
     */
    val temperature: Float? = 0f,
    
    /**
     * 要包含的時間戳粒度。選項："word"、"segment"
     * 注意："word" 需要 responseFormat="verbose_json"
     */
    val timestampGranularities: List<String>? = listOf("segment")
)
```

**回應類型：**
```kotlin
data class ASRResponse(
    /**
     * 轉錄的文字（無論格式如何都始終存在）
     */
    val text: String,
    
    /**
     * 詳細段落（僅在 verbose_json 格式中存在）
     */
    val segments: List<TranscriptionSegment>? = null,
    
    /**
     * 檢測到的語言（僅在 verbose_json 格式中存在）
     */
    val language: String? = null,
    
    /**
     * 請求格式的原始回應（text、srt、vtt 等）
     */
    val rawResponse: String? = null,
    
    /**
     * 這是否為串流區塊（true）或最終回應（false）
     */
    val isChunk: Boolean = false
)

data class TranscriptionSegment(
    /**
     * 段落識別碼
     */
    val id: Int,
    
    /**
     * 尋找位置
     */
    val seek: Int,
    
    /**
     * 開始時間（秒）
     */
    val start: Float,
    
    /**
     * 結束時間（秒）
     */
    val end: Float,
    
    /**
     * 此段落的轉錄文字
     */
    val text: String,
    
    /**
     * Token ID
     */
    val tokens: List<Int>? = null,
    
    /**
     * 此段落使用的溫度
     */
    val temperature: Float? = null,
    
    /**
     * 平均對數機率
     */
    val avgLogprob: Float? = null,
    
    /**
     * 壓縮比
     */
    val compressionRatio: Float? = null,
    
    /**
     * 無語音機率
     */
    val noSpeechProb: Float? = null,
    
    /**
     * 字級時間戳（僅當 timestampGranularities 包含 "word" 時）
     */
    val words: List<WordTimestamp>? = null
)

data class WordTimestamp(
    /**
     * 字詞
     */
    val word: String,
    
    /**
     * 字詞開始時間（秒）
     */
    val start: Float,
    
    /**
     * 字詞結束時間（秒）
     */
    val end: Float
)
```

**範例：**
```kotlin
val request = asrRequest(
    audioBytes = audioBytes,
    model = "whisper-1",
    language = "en"
)

EdgeAI.asr(request).collect { response ->
    updateUI(response.text)
}

// 帶有詳細時間戳
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