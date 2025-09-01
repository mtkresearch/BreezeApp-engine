# BreezeApp Runner 開發指南

一份在 BreezeApp-engine 中建立自訂 AI runner 的簡單指南。

## 快速入門

### 1. 複製範本
```bash
cp templates/CustomRunner.kt yourvendor/YourRunner.kt
```

### 2. 更新註解
```kotlin
@AIRunner(
    vendor = VendorType.UNKNOWN,           // 選擇您的供應商
    priority = RunnerPriority.NORMAL,      // HIGH/NORMAL/LOW  
    capabilities = [CapabilityType.LLM],   // LLM/ASR/TTS/VLM/GUARDIAN
    defaultModel = "your-model-name"
)
class YourRunner : BaseRunner, FlowStreamingRunner {
    // 實作...
}
```

### 3. 實作您的 AI 邏輯
```kotlin
// 對於 LLM:
private fun processTextInput(text: String): String {
    return apiClient.generateText(text)
}

// 對於 ASR:
private fun processAudioInput(audio: ByteArray): String {
    return apiClient.transcribeAudio(audio)
}

// 對於 TTS, VLM, GUARDIAN - 請參閱範本以取得範例
```

## 範例

### 範例 1：MTK NPU Runner
```kotlin
@AIRunner(
    vendor = VendorType.MEDIATEK,
    priority = RunnerPriority.HIGH,
    capabilities = [CapabilityType.LLM]
)
class MTKLLMRunner(private val context: Context? = null) : BaseRunner, FlowStreamingRunner {
    
    override fun load(): Boolean {
        val defaultConfig = ModelConfig(
            modelName = MODEL_NAME,
            modelPath = "" // 空白 - 使用 MTKUtils.resolveModelPath()
        )
        return load(defaultConfig)
    }
    
    // 智慧模型路徑解析
    override fun load(config: ModelConfig): Boolean {
        val modelPath = if (context != null) {
            MTKUtils.resolveModelPath(context, config.modelPath)
        } else {
            config.modelPath ?: return false
        }
        // ... MTK 初始化
    }
}
```

### 範例 2：Mock Runner (絕佳範本)
```kotlin
@AIRunner(
    vendor = VendorType.UNKNOWN,
    priority = RunnerPriority.LOW,
    capabilities = [CapabilityType.LLM]
)
class MockLLMRunner : BaseRunner, FlowStreamingRunner {
    
    override fun load(): Boolean {
        val defaultConfig = ModelConfig(
            modelName = "MockLLMModel",
            modelPath = ""
        )
        return load(defaultConfig)
    }
    
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        val inputText = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
        val response = "Mock AI 回應: $inputText"
        
        return InferenceResult.textOutput(
            text = response,
            metadata = mapOf(
                InferenceResult.META_MODEL_NAME to "mock-llm-v1",
                InferenceResult.META_PROCESSING_TIME_MS to 100L
            )
        )
    }
}
```

## 測試

您的 runner 會立即生效：
```kotlin
// 引擎會自動尋找並使用您的 runner
val request = InferenceRequest(inputs = mapOf("text" to "Hello"))
val result = engineManager.runInference(request, CapabilityType.LLM)
```

## 關鍵要點

✅ **自動發現** - 無需註冊  
✅ **參數 UI** - 定義 schema 以自動生成設定 UI  
✅ **串流** - 實作 `FlowStreamingRunner` 以實現即時回應  
✅ **錯誤處理** - 使用 `RunnerError` 進行結構化錯誤處理  
✅ **記憶體管理** - 引擎處理載入/卸載  

## 常見模式

### 硬體偵測
```kotlin
companion object : BaseRunnerCompanion {
    @JvmStatic
    override fun isSupported(): Boolean {
        return try {
            // 檢查原生函式庫
            System.loadLibrary("your-ai-lib")
            
            // 檢查裝置能力  
            val hasEnoughMemory = Runtime.getRuntime().maxMemory() > 4_000_000_000L
            
            // 檢查 API 可用性
            YourAILibrary.isAvailable()
            
            hasEnoughMemory
        } catch (e: Exception) {
            false
        }
    }
}
```

### 輸入/輸出處理
```kotlin
// 常見輸入鍵
val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String
val audio = input.inputs[InferenceRequest.INPUT_AUDIO] as? ByteArray
val image = input.inputs[InferenceRequest.INPUT_IMAGE] as? ByteArray

// 常見參數  
val temperature = (input.params[InferenceRequest.PARAM_TEMPERATURE] as? Number)?.toFloat() ?: 0.7f
val maxTokens = (input.params[InferenceRequest.PARAM_MAX_TOKENS] as? Number)?.toInt() ?: 2048

// 回傳結果
return InferenceResult.textOutput(
    text = response,
    metadata = mapOf(
        InferenceResult.META_MODEL_NAME to "your-model",
        InferenceResult.META_PROCESSING_TIME_MS to processingTime
    )
)
```

## 可用類型

**能力 (Capabilities):**
- `LLM` - 文字生成 (文字 → 文字)
- `ASR` - 語音辨識 (音訊 → 文字)
- `TTS` - 文字轉語音 (文字 → 音訊)
- `VLM` - 視覺 + 語言 (文字 + 圖片 → 文字)
- `GUARDIAN` - 內容安全 (文字 → 安全性分析)

**供應商 (Vendors):**
- `OPENROUTER` - 雲端 API 服務
- `EXECUTORCH` - 本地行動裝置推理
- `SHERPA` - 基於 ONNX 的處理
- `MEDIATEK` - NPU 加速
- `UNKNOWN` - 自訂/未指定

## 需要協助？

- 📋 **從範本開始** - `templates/CustomRunner.kt` 
- 🚀 **串流模式** - `runner/STREAMING_GUIDE.md` 以取得詳細的串流實作
- 📁 **真實範例** - `executorch/`, `openrouter/`, `sherpa/`, `mock/` 目錄
- 🧪 **測試模式** - `src/test/` 以取得使用範例

**專注於您的 AI 邏輯 - 引擎會處理其餘部分！**
