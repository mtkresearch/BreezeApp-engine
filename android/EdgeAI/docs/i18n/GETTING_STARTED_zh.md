# 快速開始

[← 回到 README](./README_zh.md) | [使用指南 →](./USAGE_GUIDE_zh.md)

> **新用戶快速入門指南**：安裝、初始化、進行第一個 API 呼叫，以及清理資源。

---

## 安裝

### JitPack 依賴

將 EdgeAI SDK 加入到您的 `build.gradle.kts`：

```kotlin
dependencies {
    implementation("com.github.mtkresearch:BreezeApp-engine:EdgeAI-v0.1.1")
}
```

### 本地依賴（開發用）

如果您正在開發或修改 EdgeAI SDK：

```kotlin
dependencies {
    implementation(project(":EdgeAI"))
}
```

---

## 前置需求

在使用 EdgeAI SDK 之前，請確保：

1. **BreezeApp Engine 已安裝**在目標設備上
2. **BreezeApp Engine 服務正在運行**（通常會隨應用程式自動啟動）
3. **您的應用程式具有所需權限**（請參閱 [使用指南](./USAGE_GUIDE_zh.md#permissions)）

---

## 快速開始

### 1. 初始化 SDK

```kotlin
import com.mtkresearch.breezeapp.edgeai.*
import kotlinx.coroutines.launch

// 在 CoroutineScope 中（例如 lifecycleScope 或 viewModelScope）
launch {
    try {
        // 初始化並等待連接到 BreezeApp Engine 服務
        EdgeAI.initializeAndWait(context, timeoutMs = 10000)
        Log.i("EdgeAI", "SDK 連接成功")
    } catch (e: ServiceConnectionException) {
        Log.e("EdgeAI", "連接失敗。BreezeApp Engine 是否已安裝？", e)
        return@launch
    }
}
```

### 2. 進行第一個 API 呼叫

```kotlin
// 發送簡單的聊天請求
val request = chatRequest(prompt = "用簡單的術語解釋量子計算")

EdgeAI.chat(request).collect { response ->
    val content = response.choices.firstOrNull()?.message?.content
    Log.d("EdgeAI", "AI 回應：$content")
}
```

### 3. 清理資源

```kotlin
// 在您的 Application.onTerminate() 或應用程式退出時
EdgeAI.shutdown()
```

---

## 下一步

- **[使用指南](./USAGE_GUIDE_zh.md)**：進階配置、權限、錯誤處理和常見問題
- **[API 參考](./API_REFERENCE_zh.md)**：完整的 API 文件，包含參數和範例
- **[最佳實踐](./BEST_PRACTICES_zh.md)**：生命週期管理和 UI 整合技巧 