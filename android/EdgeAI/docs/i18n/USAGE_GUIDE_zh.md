# 使用指南

[← 回到 README](./README_zh.md) | [API 參考 →](./API_REFERENCE_zh.md)

> **進階使用指南**：配置、權限、進階 API 使用和常見問題。

---

## 配置

### 自訂參數

```kotlin
// 具有自訂參數的進階聊天請求
val request = chatRequest(
    prompt = "寫一個短篇故事",
    maxTokens = 500,
    temperature = 0.7f,
    topP = 0.9f,
    stream = true  // 啟用串流以獲得即時回應
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
    when (response) {
        is ChatResponse.Stream -> {
            // 即時串流回應
            val content = response.delta?.content ?: ""
            updateUI(content)
        }
        is ChatResponse.Final -> {
            // 最終完整回應
            val fullContent = response.choices.firstOrNull()?.message?.content
            updateUI(fullContent)
        }
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

### 使用監聽器（Flow 的替代方案）

```kotlin
EdgeAI.chat(request, object : ChatListener {
    override fun onStream(delta: ChatChoice) {
        // 處理串流回應
    }
    
    override fun onComplete(response: ChatResponse) {
        // 處理最終回應
    }
    
    override fun onError(error: EdgeAIException) {
        // 處理錯誤
    }
})
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