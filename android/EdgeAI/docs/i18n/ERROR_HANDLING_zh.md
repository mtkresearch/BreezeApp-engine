# 錯誤處理

[← 回到 README](./README_zh.md) | [使用指南 ←](./USAGE_GUIDE_zh.md)

> **詳細的錯誤處理策略**：例外類型、常見原因和建議的處理方式。

---

## 例外階層

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

## 例外類型與處理

### ServiceConnectionException

**原因：**
- BreezeApp Engine 未安裝
- BreezeApp Engine 服務未運行
- AIDL 綁定失敗
- 權限被拒絕

**建議處理：**
```kotlin
try {
    EdgeAI.initializeAndWait(context, timeoutMs = 10000)
} catch (e: ServiceConnectionException) {
    // 顯示用戶友好的訊息
    showDialog("請從應用程式商店安裝 BreezeApp Engine")
    // 或重定向到下載頁面
    openAppStore("com.mtkresearch.breezeapp.engine")
}
```

### InvalidRequestException

**原因：**
- 缺少必需參數
- 無效的參數值
- 不支援的模型/語音
- 格式錯誤的請求資料

**建議處理：**
```kotlin
EdgeAI.chat(request).catch { error ->
    when (error) {
        is InvalidRequestException -> {
            Log.e("EdgeAI", "無效請求：${error.message}")
            // 驗證並重試請求參數
            validateAndRetry(request)
        }
    }
}.collect { response ->
    // 處理成功
}
```

### TimeoutException

**原因：**
- 網路超時
- 服務回應超時
- 長時間運行的 AI 操作

**建議處理：**
```kotlin
EdgeAI.chat(request).catch { error ->
    when (error) {
        is TimeoutException -> {
            Log.w("EdgeAI", "請求超時：${error.message}")
            // 顯示載入指示器並重試
            showRetryDialog {
                retryRequest(request)
            }
        }
    }
}.collect { response ->
    // 處理成功
}
```

### NetworkException

**原因：**
- 網路連接問題
- 服務不可用
- 連接重置

**建議處理：**
```kotlin
EdgeAI.chat(request).catch { error ->
    when (error) {
        is NetworkException -> {
            Log.e("EdgeAI", "網路錯誤：${error.message}")
            // 檢查連接性並重試
            if (isNetworkAvailable()) {
                retryRequest(request)
            } else {
                showOfflineMessage()
            }
        }
    }
}.collect { response ->
    // 處理成功
}
```

### AuthenticationException

**原因：**
- 無效的 API 金鑰
- 過期的憑證
- 未授權的存取

**建議處理：**
```kotlin
EdgeAI.chat(request).catch { error ->
    when (error) {
        is AuthenticationException -> {
            Log.e("EdgeAI", "認證失敗：${error.message}")
            // 重新整理憑證或顯示登入
            refreshCredentials()
        }
    }
}.collect { response ->
    // 處理成功
}
```

---

## 錯誤恢復策略

### 指數退避重試

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
    
    throw lastException ?: UnknownException("超過最大重試次數")
}
```

### 優雅降級

```kotlin
fun handleChatRequest(request: ChatRequest) {
    viewModelScope.launch {
        try {
            EdgeAI.chat(request).collect { response ->
                updateUI(response)
            }
        } catch (e: ServiceConnectionException) {
            // 降級到離線模式或快取回應
            showOfflineMode()
        } catch (e: TimeoutException) {
            // 顯示部分結果或快取內容
            showPartialResults()
        } catch (e: EdgeAIException) {
            // 通用錯誤處理
            showErrorMessage(e.message)
        }
    }
}
```

---

## 除錯技巧

### 啟用詳細日誌

```kotlin
// 僅限開發
EdgeAI.setLogLevel(LogLevel.DEBUG)

// 檢查日誌
adb logcat | grep "EdgeAI"
```

### 常見除錯情境

1. **初始化失敗**
   - 檢查 BreezeApp Engine 是否已安裝
   - 驗證服務是否運行：`adb shell ps | grep breezeapp`
   - 檢查 AndroidManifest.xml 中的權限

2. **請求超時**
   - 增加超時值
   - 檢查設備效能
   - 驗證模型可用性

3. **無效回應**
   - 驗證請求參數
   - 檢查模型相容性
   - 驗證回應格式

---

## 最佳實踐

1. **始終處理例外** - 不要讓它們崩潰您的應用程式
2. **提供用戶友好的訊息** - 不要顯示技術錯誤詳情
3. **實作重試邏輯** - 針對暫時性失敗
4. **使用優雅降級** - 提供備用選項
5. **適當記錄錯誤** - 用於除錯但不要面向用戶
6. **測試錯誤情境** - 在測試中包含錯誤處理 