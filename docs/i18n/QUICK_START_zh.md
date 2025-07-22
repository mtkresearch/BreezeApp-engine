# 快速開始指南

## 🚀 5 分鐘內開始使用

### 給 App 開發者

#### 1. 安裝 BreezeApp Engine
```bash
# 安裝引擎 APK
adb install breeze-app-engine.apk
```

#### 2. 加入您的專案
```kotlin
// build.gradle (app level)
dependencies {
    implementation project(':breeze-app-engine')
}
```

#### 3. 基本聊天實作
```kotlin
class MainActivity : AppCompatActivity() {
    private var aiService: IBreezeAppEngineService? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // 綁定 AI 服務
        bindAIService()
        
        // 發送聊天訊息
        sendButton.setOnClickListener {
            sendChatMessage(inputText.text.toString())
        }
    }
    
    private fun sendChatMessage(message: String) {
        val request = ChatRequest().apply {
            messages = listOf(ChatMessage().apply {
                role = "user"
                content = message
            })
            stream = true
        }
        
        aiService?.sendChatRequest(UUID.randomUUID().toString(), request)
    }
    
    // 完整實作請參考 API_REFERENCE.md
}
```

### 給 AI 工程師

#### 1. 建立您的 Runner
```kotlin
class MyCustomRunner : BaseRunner {
    override fun load(config: ModelConfig): Boolean {
        // 載入您的 AI 模型
        return true
    }
    
    override fun run(input: InferenceRequest): InferenceResult {
        // 處理請求
        return InferenceResult.success(mapOf("text" to "Hello!"))
    }
    
    override fun getCapabilities() = listOf(CapabilityType.LLM)
    // ... 實作其他方法
}
```

#### 2. 註冊您的 Runner
```json
// assets/runner_config.json
{
  "runners": [
    {
      "name": "MyCustomRunner",
      "class": "com.yourpackage.MyCustomRunner",
      "capabilities": ["LLM"]
    }
  ]
}
```

## 📚 下一步

- **AI 工程師**：閱讀 [RUNNER_DEVELOPMENT_zh.md](./RUNNER_DEVELOPMENT_zh.md)
- **貢獻者**：閱讀 [CONTRIBUTING_zh.md](./CONTRIBUTING_zh.md)
- **架構**：閱讀 [ARCHITECTURE_zh.md](./ARCHITECTURE_zh.md)