# 使用指南

[← 回到 README](./README_zh.md) | [API 參考 →](./API_REFERENCE_zh.md)

> **進階使用指南**：配置、權限、進階 API 使用和常見問題。

---

## 配置

### 自訂參數

```kotlin
// 使用建構器函數（簡單）
val request = chatRequest(
    prompt = "寫一個短篇故事",
    maxTokens = 500,
    temperature = 0.7f,
    stream = true
)

// 使用完整的 ChatRequest 建構器（進階）
val messages = listOf(
    ChatMessage(role = "system", content = "你是一個有用的AI助手。"),
    ChatMessage(role = "user", content = "寫一個短篇故事")
)

val metadata = mutableMapOf<String, String>()
metadata["top_k"] = "40"
metadata["repetition_penalty"] = "1.1"

val request = ChatRequest(
    model = "", // 空字串表示讓引擎決定
    messages = messages,
    temperature = 0.7f,
    topP = 0.9f,
    maxCompletionTokens = 500,
    stream = true,
    metadata = metadata
)
```

### 日誌配置

```kotlin
// 啟用詳細日誌（僅限開發）
EdgeAI.setLogLevel(LogLevel.DEBUG)

// 停用日誌（生產環境）
EdgeAI.setLogLevel(LogLevel.ERROR)
```

---

## 權限

在您的 `AndroidManifest.xml` 中添加這些權限：

```xml
<!-- AIDL 通訊所需 -->
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" />

<!-- 可選：音訊功能 -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

---

## 進階 API 使用

### 串流回應

```kotlin
EdgeAI.chat(request).collect { response ->
    // 此模式符合 ChatViewModel.kt 的生產環境用法
    val choice = response.choices.firstOrNull()
    
    // 處理串流片段（沒有 finishReason = 仍在串流中）
    if (choice?.finishReason == null) {
        choice?.delta?.content?.let { chunk ->
            if (chunk.isNotBlank()) {
                appendToMessage(chunk) // 附加串流片段
            }
        }
    }
    
    // 處理最終回應
    choice?.message?.content?.let { finalContent ->
        setFinalMessage(finalContent) // 設定完整訊息
    }
    
    // 檢查串流是否完成
    choice?.finishReason?.let { reason ->
        onStreamingComplete(reason) // "stop", "length" 等
    }
}
```

### 取消請求

```kotlin
val job = launch {
    EdgeAI.chat(request).collect { response ->
        // 處理回應
    }
}

// 取消請求
job.cancel()
```

### 使用 Flow 進行錯誤處理

```kotlin
EdgeAI.chat(request)
    .catch { error ->
        when (error) {
            is InvalidInputException -> {
                // 處理無效輸入（例如：空提示、無效參數）
                Log.e("Chat", "無效輸入: ${error.message}")
            }
            is ModelNotFoundException -> {
                // 處理模型未找到
                Log.e("Chat", "模型未找到: ${error.message}")
            }
            is ServiceConnectionException -> {
                // 處理服務不可用
                Log.e("Chat", "服務連接錯誤: ${error.message}")
            }
            is EdgeAIException -> {
                // 處理其他 EdgeAI 錯誤（包括 Guardian 違規）
                Log.e("Chat", "EdgeAI 錯誤: ${error.message}")
            }
            else -> {
                // 處理意外錯誤
                Log.e("Chat", "意外錯誤: ${error.message}")
            }
        }
    }
    .collect { response ->
        // 處理成功回應
        val content = response.choices.firstOrNull()?.let { choice ->
            choice.delta?.content ?: choice.message?.content
        } ?: ""
        updateUI(content)
    }
```

---

## 常見問題（FAQ）

### Q: 為什麼初始化失敗？
**A**: 檢查 BreezeApp Engine 是否已安裝並運行。詳情請參閱 [錯誤處理](./ERROR_HANDLING_zh.md)。

### Q: 如何處理網路錯誤？
**A**: EdgeAI 可離線工作，但請檢查服務連接問題。詳情請參閱 [錯誤處理](./ERROR_HANDLING_zh.md)。

### Q: 可以同時使用多個請求嗎？
**A**: 可以，但要小心管理資源。詳情請參閱 [最佳實踐](./BEST_PRACTICES_zh.md)。

### Q: 如何優化效能？
**A**: 使用適當的超時時間、管理生命週期，並避免阻塞操作。詳情請參閱 [最佳實踐](./BEST_PRACTICES_zh.md)。

---

## 錯誤處理

詳細的錯誤處理策略，請參閱 **[錯誤處理](./ERROR_HANDLING_zh.md)**。

常見模式：

```kotlin
EdgeAI.chat(request)
    .catch { error ->
        when (error) {
            is ServiceConnectionException -> {
                // 處理服務不可用
            }
            is InvalidRequestException -> {
                // 處理無效參數
            }
            is TimeoutException -> {
                // 處理超時
            }
            else -> {
                // 處理其他錯誤
            }
        }
    }
    .collect { response ->
        // 處理成功
    }
```

---

## 下一步

- **[API 參考](./API_REFERENCE_zh.md)**：完整的 API 文件
- **[錯誤處理](./ERROR_HANDLING_zh.md)**：詳細的錯誤處理策略
- **[最佳實踐](./BEST_PRACTICES_zh.md)**：生產環境實作技巧 