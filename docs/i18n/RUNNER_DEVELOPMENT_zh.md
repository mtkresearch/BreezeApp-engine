# BreezeApp Runner 開發指南

一份在 BreezeApp-engine 中建立自訂 AI runner 的簡單指南。

## 快速入門

### 1. 複製範本

在 `android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/runner` 目錄下，

```bash
cp templates/CustomRunner.kt yourvendor/YourRunner.kt
```

### 2. 更新裝飾詞

```kotlin
@AIRunner(
    vendor = VendorType.UNKNOWN,           // 選擇您的供應商
    priority = RunnerPriority.NORMAL,      // HIGH/NORMAL/LOW  
    capabilities = [CapabilityType.LLM],   // LLM/ASR/TTS/VLM/GUARDIAN
    defaultModel = "your-model-name" // 對於本地模型，請與 fullModelList.json 中的 'id' 對齊；對於雲端模型，請與 API 名稱對齊。
)
class YourRunner : BaseRunner, FlowStreamingRunner {
    // 實作...
}
```

### 3. 實作您的 AI 邏輯

```kotlin
// `BaseRunner` 中的 `run` 方法和 `FlowStreamingRunner` 中的 `runAsFlow` 是您的 runner 推論邏輯的主要入口點。
// 這是您的 runner 接收 `InferenceRequest` 並返回 `InferenceResult` 的地方。

// `run` 方法實作範例
override fun run(request: InferenceRequest): InferenceResult {
    // 輸入資料（文字、音訊、圖片）位於 `request.inputs` 中。
    val inputText = request.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""

    // 您的核心 AI 邏輯（例如，呼叫 API 或本地模型）
    val aiResponse = apiClient.generateText(inputText)

    // 輸出應透過 `InferenceResult.textOutput()`、`InferenceResult.audioOutput()` 等方法返回。
    return InferenceResult.textOutput(text = aiResponse)
}

// 對於串流 runner，請實作 `runAsFlow`（請參閱範本以取得範例）。
// `runAsFlow` 方法實作範例：
override fun runAsFlow(request: InferenceRequest): Flow<InferenceResult> = flow {
    val inputText = request.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
    var accumulatedText = ""

    // 模擬串流輸出
    apiClient.streamGenerate(inputText) { chunk ->
        accumulatedText += chunk
        emit(InferenceResult.textOutput(text = accumulatedText, partial = true))
    }
    // 發出最終結果
    emit(InferenceResult.textOutput(text = accumulatedText, partial = false))
}
```

有關更詳細的串流模式和最佳實踐，請參閱：[串流實作指南](../android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/runner/STREAMING_GUIDE.md)

**就是這樣！** 引擎會自動發現並整合您的 runner。

## 範例

### LLM Runner 範例

```kotlin
@AIRunner(vendor = VendorType.OPENROUTER, capabilities = [CapabilityType.LLM])
class MyLLMRunner : BaseRunner {
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        return try {
            val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
            val response = apiClient.generateText(text)
            InferenceResult.success(mapOf("text" to response))
        } catch (e: Exception) {
            InferenceResult.error(RunnerError.processingError("Generation failed: ${e.message}", e))
        }
    }
}
```

### ASR Runner 範例

```kotlin
@AIRunner(vendor = VendorType.SHERPA, capabilities = [CapabilityType.ASR])
class MyASRRunner : BaseRunner {
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        return try {
            val audio = input.inputs[InferenceRequest.INPUT_AUDIO] as? ByteArray ?: byteArrayOf()
            if (audio.isEmpty()) {
                return InferenceResult.error(RunnerError.invalidInput("Audio input is required"))
            }
            val transcript = asrClient.transcribe(audio)
            InferenceResult.success(mapOf("text" to transcript))
        } catch (e: Exception) {
            InferenceResult.error(RunnerError(RunnerError.Code.ASR_FAILURE, "Transcription failed: ${e.message}", e))
        }
    }
}
```

### TTS Runner 範例

```kotlin
@AIRunner(vendor = VendorType.SHERPA, capabilities = [CapabilityType.TTS])
class MyTTSRunner : BaseRunner {
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        return try {
            val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
            if (text.isBlank()) {
                return InferenceResult.error(RunnerError.invalidInput("Text input cannot be empty"))
            }
            val audioData = ttsClient.synthesize(text)
            InferenceResult.success(mapOf("audio_data" to audioData, "sample_rate" to 22050))
        } catch (e: Exception) {
            InferenceResult.error(RunnerError(RunnerError.Code.TTS_FAILURE, "Speech synthesis failed: ${e.message}", e))
        }
    }
}
```

## 錯誤處理

始終使用 `RunnerError` 工廠方法返回結構化錯誤以保持一致性。

```kotlin
// 輸入驗證錯誤
return InferenceResult.error(RunnerError.invalidInput("Text input cannot be empty"))

// 處理錯誤
return InferenceResult.error(RunnerError.processingError("API call failed: ${e.message}", e))

// 資源錯誤
return InferenceResult.error(RunnerError.resourceUnavailable("Model not loaded"))
```

**錯誤碼指南：**

錯誤碼集中在 `RunnerError.Code` 中以確保一致性。盡可能使用工廠方法（`RunnerError.invalidInput(...)`、`RunnerError.processingError(...)` 等）。

- **E1xx**：處理錯誤（例如，推論失敗）
- **E4xx**：客戶端/輸入錯誤（例如，無效參數、權限）
- **E5xx**：服務器/資源錯誤（例如，模型載入、資源不可用）

詳細錯誤碼定義在 `engine/model/RunnerError.kt` 中。

## 關鍵要點

✅ **自動發現** - 自動發現
✅ **參數 UI** - 自動設定 UI
✅ **串流** - 串流
✅ **錯誤處理** - 錯誤處理
✅ **記憶體管理** - 記憶體管理

## 需要幫助嗎？

- 📋 **從範本開始** - `templates/CustomRunner.kt`
- 🚀 **串流模式** - `runner/STREAMING_GUIDE.md`
- 📁 **實際範例** - `executorch/`, `openrouter/`, `sherpa/`, `mock/`
- 🧪 **測試模式** - `src/test/`

**專注於您的 AI 邏輯 - 引擎處理剩下的部分！**
