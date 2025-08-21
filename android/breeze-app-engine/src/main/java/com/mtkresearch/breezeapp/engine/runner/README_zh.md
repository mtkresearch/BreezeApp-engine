# BreezeApp Runner 開發指南

一份在 BreezeApp-engine 中建立自訂 AI runner 的簡單指南。

## 快速入門：5 步驟建立您的 Runner

### 步驟 1：選擇您的套件位置
```
BreezeApp-engine/android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/runner/
├── mtk/           # MediaTek NPU runners
├── sherpa/        # Sherpa ONNX runners  
├── mock/          # Mock/測試 runners
└── [your-vendor]/ # 您的 runner 目錄
```

### 步驟 2：實作 BaseRunner 介面
```kotlin
package com.mtkresearch.breezeapp.engine.runner.yourvendor

import com.mtkresearch.breezeapp.engine.annotation.*
import com.mtkresearch.breezeapp.engine.runner.core.*
import com.mtkresearch.breezeapp.engine.model.*

@AIRunner(
    vendor = VendorType.UNKNOWN,                    // 若可用，請變更為您的供應商
    priority = RunnerPriority.NORMAL,              // HIGH/NORMAL/LOW
    capabilities = [CapabilityType.LLM]            // LLM/ASR/TTS/VLM/GUARDIAN
)
class YourCustomRunner : BaseRunner {
    
    private val isLoaded = AtomicBoolean(false)
    
    // 實作必要方法
    override fun load(): Boolean {
        // 載入預設模型組態
        val defaultConfig = ModelConfig(
            modelName = "YourModel",
            modelPath = ""
        )
        return load(defaultConfig)
    }
    
    override fun load(config: ModelConfig): Boolean {
        // 在此處初始化您的 AI 模型/API
        // 範例：yourAIClient.initialize(config.modelPath)
        isLoaded.set(true)
        return true
    }
    
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        if (!isLoaded.get()) {
            return InferenceResult.error(RunnerError.modelNotLoaded())
        }
        
        // 處理輸入
        val inputText = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
        
        // 您自己的 AI 推理邏輯
        val response = processWithYourAI(inputText)
        
        return InferenceResult.textOutput(
            text = response,
            metadata = mapOf(
                InferenceResult.META_MODEL_NAME to "YourModel",
                InferenceResult.META_PROCESSING_TIME_MS to System.currentTimeMillis()
            )
        )
    }
    
    override fun unload() {
        // 清理資源
        isLoaded.set(false)
    }
    
    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.LLM)
    override fun isLoaded(): Boolean = isLoaded.get()
    override fun getRunnerInfo(): RunnerInfo = RunnerInfo(
        name = "YourCustomRunner",
        version = "1.0.0",
        capabilities = getCapabilities(),
        description = "您的自訂 AI runner"
    )
    
    private fun processWithYourAI(input: String): String {
        // 請替換為您實際的 AI 處理邏輯
        return "已處理: $input"
    }
    
    // 硬體支援檢查
    companion object : BaseRunnerCompanion {
        @JvmStatic
        override fun isSupported(): Boolean {
            // 檢查您的 AI 函式庫/硬體是否可用
            return true
        }
    }
}
```

### 步驟 3：新增串流支援 (可選)
如果您的 runner 支援即時串流 (ASR、即時推理):

```kotlin
class YourStreamingRunner : BaseRunner, FlowStreamingRunner {
    
    override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = callbackFlow {
        // 當部分結果可用時發送
        for (i in 1..5) {
            val partialResult = "部分結果 $i"
            trySend(InferenceResult.textOutput(
                text = partialResult,
                partial = true  // 標記為部分結果
            ))
            delay(100)
        }
        
        // 發送最終結果
        trySend(InferenceResult.textOutput(
            text = "最終結果",
            partial = false
        ))
        close()
    }
}
```

### 步驟 4：正確處理錯誤
```kotlin
override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
    return try {
        if (!isLoaded()) {
            return InferenceResult.error(RunnerError.modelNotLoaded())
        }
        
        val inputText = input.inputs[InferenceRequest.INPUT_TEXT] as? String
            ?: return InferenceResult.error(RunnerError.invalidInput("缺少文字輸入"))
        
        // 您的處理邏輯...
        val result = processWithYourAI(inputText)
        InferenceResult.textOutput(text = result)
        
    } catch (e: Exception) {
        InferenceResult.error(RunnerError.runtimeError("處理失敗: ${e.message}", e))
    }
}
```

### 步驟 5：測試您的 Runner
您的 runner 會被引擎自動發現。透過 BreezeApp 進行測試：

```kotlin
// 引擎會根據以下條件自動尋找並使用您的 runner：
// 1. 能力類型 (LLM/ASR/TTS/VLM)
// 2. 供應商優先級 
// 3. 硬體可用性
```

## 程式碼庫中的真實範例

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

## 關鍵架構要點

### 1. 雙重載入方法
每個 runner 都必須實作：
- `load(): Boolean` - 載入預設模型
- `load(config: ModelConfig): Boolean` - 載入特定組態

### 2. 自動註冊
- 無需手動註冊
- 引擎會自動發現帶有 `@AIRunner` 標註的類別
- 成功載入的 runner 會被追蹤於 `activeRunners` 集合中

### 3. 優先級系統
- `HIGH`：硬體加速、優質 runner (MTK NPU)
- `NORMAL`：標準 runner (基於 CPU)
- `LOW`：備用/mock runner

### 4. 記憶體管理
- 當需要記憶體時，引擎會自動卸載其他 runner
- Runner 在卸載後會從活動集合中正確移除
- 務必在 `unload()` 中實作適當的清理

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

## 目前能力

| CapabilityType | 描述 | 輸入 | 輸出 |
|----------------|-------------|-------|--------|
| `LLM` | 大型語言模型 | 文字 | 文字 |
| `ASR` | 語音辨識 | 音訊 | 文字 |
| `TTS` | 文字轉語音 | 文字 | 音訊 |
| `VLM` | 視覺語言模型 | 圖片 + 文字 | 文字 |
| `GUARDIAN` | 內容安全 | 文字 | 安全性分析 |

## 就是這樣！

您的 runner 將被自動發現並整合到 BreezeApp 引擎中。系統會處理：
- ✅ 透過標註自動發現  
- ✅ 基於優先級的選擇
- ✅ 記憶體管理與清理
- ✅ 錯誤處理與備援
- ✅ 硬體偵測與驗證

專注於實作您的 AI 邏輯 - 引擎會處理其他所有事情。
