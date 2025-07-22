# 🧩 Runner 開發指南

本指南適用於想要透過建立新的 AI 功能或新增模型支援來擴展 BreezeApp Engine 的開發者。

**Runner** 是一個實作特定 AI 任務的自包含元件。要貢獻一個 runner，您需要了解其生命週期以及它如何融入整體架構。

> **前置條件**：在閱讀本指南之前，請確保您已閱讀並理解我們的 **[README.md](../README.md)**，因為本指南建立在那裡解釋的概念之上。

## 🎯 核心哲學：從 Payload 到 Runner

我們的架構設計為高度可擴展。請求的旅程總是：

`RequestPayload` → `UseCase` → `Runner`

-   **`RequestPayload`**：定義客戶端想要什麼的特定、型別安全的資料類別。
-   **`UseCase`**：包含單一功能的業務邏輯（例如：`TextGenerationUseCase`）。它知道*做什麼*，但不知道*如何做*。
-   **`Runner`**：知道*如何做*的實作（例如：`LlamaRunner`、`MockLLMRunner`）。

要新增功能，您需要觸及所有三個層級。

## 🚀 教學：建立新的 "Echo" 功能

讓我們從頭開始建立一個全新的功能。我們的目標是建立一個簡單回顯接收文字的 `EchoRunner`。

這個範例將教會您建立真實 runner 所需的一切（例如：為 Hugging Face 的新模型）。

### 步驟 1：定義 `RequestPayload`

首先，我們在 `EdgeAI` 模組中為新功能定義合約。

```kotlin
// 在：EdgeAI/src/.../model/RequestPayload.kt

@Parcelize
sealed interface RequestPayload : Parcelable {
    // ... 其他 payloads (TextChat, ImageAnalysis, 等)

    @Parcelize
    data class Echo(
        val textToEcho: String
    ) : RequestPayload
}
```

### 步驟 2：實作 `BaseRunner`

接下來，我們在 `breeze-app-engine` 模組中建立實際的 runner。它必須實作 `BaseRunner` 介面。

最重要的方法是 `run()`，它接受特定的 `payload` 並回傳 `Flow<AIResponse>`。

```kotlin
// 在：breeze-app-engine/src/.../runner/EchoRunner.kt

import com.mtkresearch.breezeapp.shared.contracts.model.AIResponse
import com.mtkresearch.breezeapp.shared.contracts.model.RequestPayload
import com.mtkresearch.breezeapp.shared.contracts.model.ResponseMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

class EchoRunner : BaseRunner {
    override val id: String = "echo-runner-${UUID.randomUUID()}"
    private var isLoaded = false

    override suspend fun load(): Boolean {
        // 對於真實的 runners，您會在這裡載入模型檔案。
        // 對於我們簡單的 echo runner，我們只是設定一個標誌。
        isLoaded = true
        return true
    }

    override fun run(payload: RequestPayload, requestId: String): Flow<AIResponse> = flow {
        if (!isLoaded) {
            emit(AIResponse.error(requestId, "EchoRunner model not loaded"))
            return@flow
        }

        // 型別檢查 payload 以確保我們得到正確的類型。
        if (payload !is RequestPayload.Echo) {
            emit(AIResponse.error(requestId, "Invalid payload type for EchoRunner"))
            return@flow
        }

        val echoedText = "Echo: ${payload.textToEcho}"

        // 發送單一、完整的回應。
        emit(AIResponse(
            requestId = requestId,
            text = echoedText,
            isComplete = true,
            state = AIResponse.ResponseState.COMPLETED,
            metadata = ResponseMetadata.Standard(
                modelName = "Echo-v1",
                processingTimeMs = 2, // 很快！
                backend = "CPU"
            )
        ))
    }

    override suspend fun unload() {
        isLoaded = false
    }

    override fun isLoaded(): Boolean = isLoaded
}
```

### 步驟 3：建立 `UseCase`

Use Case 充當路由邏輯和 runner 之間的橋樑。

```kotlin
// 在：breeze-app-engine/src/.../usecase/EchoUseCase.kt

import com.mtkresearch.breezeapp.engine.engine.RunnerRegistry
import com.mtkresearch.breezeapp.shared.contracts.model.AIResponse
import com.mtkresearch.breezeapp.shared.contracts.model.RequestPayload
import kotlinx.coroutines.flow.Flow

class EchoUseCase(private val runnerRegistry: RunnerRegistry) {
    
    suspend fun execute(requestId: String, payload: RequestPayload.Echo): Flow<AIResponse> {
        // "ECHO" 是我們將在配置中定義的功能名稱。
        val runner = runnerRegistry.getRunnerForCapability("ECHO") 
            ?: throw IllegalStateException("No runner found for ECHO capability")
        
        return runner.run(payload, requestId)
    }
}
```

### 步驟 4：連接所有元件

1.  **實例化 UseCase**：在 `AIEngineManager.kt`（或您的 DI 框架）中，建立新 `EchoUseCase` 的實例。
    ```kotlin
    val echoUseCase = EchoUseCase(runnerRegistry)
    ```

2.  **更新 Dispatcher**：在 `AIRequestDispatcher.kt` 中，在 `when` 區塊中新增一個分支來處理 `Echo` payload。
    ```kotlin
    // 在 AIRequestDispatcher.kt
    suspend fun dispatch(request: AIRequest): Flow<AIResponse> {
        return when (val payload = request.payload) {
            is RequestPayload.TextChat -> // ...
            is RequestPayload.ImageAnalysis -> // ...
            
            // 我們的新功能
            is RequestPayload.Echo -> echoUseCase.execute(request.id, payload)
        }
    }
    ```

### 步驟 5：在 `runner_config.json` 中註冊

最後，透過將其新增到配置檔案中來告訴引擎您的新 runner。

```json
// 在：breeze-app-engine/src/main/assets/runner_config.json
{
  "runners": [
    // ... 其他 runners
    {
      "name": "echo_runner_v1",
      "class": "com.mtkresearch.breezeapp.engine.runner.EchoRunner",
      "capabilities": ["ECHO"],
      "priority": 10,
      "is_real": true
    }
  ]
}
```
-   **`"class"`**：您 runner 的完整類別名稱。
-   **`"capabilities"`**：此 runner 可以處理的功能列表。這**必須匹配**您在 `UseCase` 中使用的字串。

就這樣！您已成功為引擎新增了一個端到端的 AI 功能。

## 實作串流

對於增量生成回應的 runners（如 LLM），您可以從 `run()` 方法的 `Flow` 中發送多個 `AIResponse` 物件。

```kotlin
// 在串流 runner 的 run() 方法內部...
override fun run(payload: RequestPayload, requestId: String): Flow<AIResponse> = flow {
    val fullText = "This is a streaming response."
    val words = fullText.split(" ")
    var currentText = ""

    words.forEachIndexed { index, word ->
        currentText += "$word "
        val isLast = (index == words.size - 1)
        
        // 發送部分回應
        emit(AIResponse(
            requestId = requestId,
            text = currentText.trim(),
            isComplete = isLast,
            state = if (isLast) AIResponse.ResponseState.COMPLETED else AIResponse.ResponseState.STREAMING
        ))
        
        delay(100) // 模擬處理時間
    }
}
```
客戶端將收到每個發送作為單獨的 `onResponse` 呼叫，允許即時 UI 更新。`isComplete` 標誌表示串流何時結束。 