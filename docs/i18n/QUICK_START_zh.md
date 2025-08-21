# 快速開始指南

## 🚀 5 分鐘內開始使用

### 給 AI 工程師

#### 1. 建立您的 Runner
建立一個實作 `BaseRunner` 的類別，並用 `@AIRunner` 進行標註。剩下的工作交給引擎處理。

```kotlin
import com.mtkresearch.breezeapp.engine.annotation.*
import com.mtkresearch.breezeapp.engine.runner.core.*
import com.mtkresearch.breezeapp.engine.model.*

@AIRunner(
    vendor = VendorType.UNKNOWN,
    priority = RunnerPriority.NORMAL,
    capabilities = [CapabilityType.LLM]
)
class MyCustomRunner : BaseRunner {
    
    override fun load(config: ModelConfig): Boolean {
        // 載入您的 AI 模型
        return true
    }
    
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        // 處理請求
        val inputText = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
        return InferenceResult.textOutput(text = "Hello: $inputText")
    }
    
    // ... 實作其他必要方法
}
```

#### 2. 自動註冊
無需在 JSON 檔案中手動註冊 runner。BreezeApp 引擎使用標註 (`@AIRunner`) 在執行時自動發現和管理可用的 runner。

## 📚 下一步

- **AI 工程師**：閱讀 [RUNNER_DEVELOPMENT_zh.md](./RUNNER_DEVELOPMENT_zh.md)
- **貢獻者**：閱讀 [CONTRIBUTING_zh.md](./CONTRIBUTING_zh.md)
- **架構**：閱讀 [ARCHITECTURE_zh.md](./ARCHITECTURE_zh.md)