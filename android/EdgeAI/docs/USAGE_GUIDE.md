# 📱 EdgeAI Library 使用指南

> **版本**：edgeai-v0.1.2  
> **維護者**：mtkresearch 團隊

---

## 🚀 快速開始

### 1. 添加 JitPack 倉庫

在您的 `settings.gradle.kts` 或 `build.gradle` 中添加：

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }  // 添加這行
    }
}
```

或者如果您使用舊版 Gradle：

```groovy
// build.gradle
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }  // 添加這行
    }
}
```

### 2. 添加依賴

在您的 app 模組的 `build.gradle.kts` 中：

```kotlin
dependencies {
    // 使用最新版本（推薦）
    implementation("com.github.mtkresearch:BreezeApp-engine:edgeai-v0.1.2")
    
    // 或者使用最新版本（不指定版本號）
    // implementation("com.github.mtkresearch:BreezeApp-engine")
}
```

---

## 📋 基本使用

### 初始化 EdgeAI

```kotlin
import com.mtkresearch.breezeapp.edgeai.EdgeAI

class MainActivity : AppCompatActivity() {
    private lateinit var edgeAI: EdgeAI
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // 初始化 EdgeAI
        edgeAI = EdgeAI.Builder(this)
            .setApiKey("your-api-key")  // 可選
            .setTimeout(30000)          // 30秒超時
            .build()
    }
}
```

### 發送聊天請求

```kotlin
// 創建聊天請求
val chatRequest = ChatRequest.Builder()
    .setMessage("Hello, how are you?")
    .setModel("gpt-3.5-turbo")
    .build()

// 發送請求
edgeAI.sendChatRequest("request-123", chatRequest) { response ->
    when (response) {
        is AIResponse.Success -> {
            val result = response.data
            Log.d("EdgeAI", "Response: ${result.message}")
        }
        is AIResponse.Error -> {
            Log.e("EdgeAI", "Error: ${response.error}")
        }
    }
}
```

### 語音轉文字 (ASR)

```kotlin
val asrRequest = ASRRequest.Builder()
    .setAudioData(audioBytes)
    .setLanguage("en-US")
    .build()

edgeAI.sendASRRequest("asr-request-123", asrRequest) { response ->
    when (response) {
        is AIResponse.Success -> {
            val transcription = response.data.text
            Log.d("EdgeAI", "Transcription: $transcription")
        }
        is AIResponse.Error -> {
            Log.e("EdgeAI", "ASR Error: ${response.error}")
        }
    }
}
```

### 文字轉語音 (TTS)

```kotlin
val ttsRequest = TTSRequest.Builder()
    .setText("Hello, this is a test message")
    .setVoice("en-US-Standard-A")
    .build()

edgeAI.sendTTSRequest("tts-request-123", ttsRequest) { response ->
    when (response) {
        is AIResponse.Success -> {
            val audioData = response.data.audioData
            // 播放音頻
            playAudio(audioData)
        }
        is AIResponse.Error -> {
            Log.e("EdgeAI", "TTS Error: ${response.error}")
        }
    }
}
```

---

## 🔧 進階配置

### 自定義配置

```kotlin
val edgeAI = EdgeAI.Builder(this)
    .setApiKey("your-api-key")
    .setTimeout(60000)  // 60秒超時
    .setRetryCount(3)   // 重試3次
    .setLogLevel(LogLevel.DEBUG)
    .build()
```

### 監聽器模式

```kotlin
// 註冊監聽器
edgeAI.registerListener(object : IBreezeAppEngineListener {
    override fun onResponse(response: AIResponse) {
        when (response) {
            is AIResponse.Success -> {
                // 處理成功響應
                updateUI(response.data)
            }
            is AIResponse.Error -> {
                // 處理錯誤
                showError(response.error)
            }
        }
    }
})

// 取消註冊
edgeAI.unregisterListener(listener)
```

### 取消請求

```kotlin
// 發送請求
edgeAI.sendChatRequest("request-123", chatRequest) { response ->
    // 處理響應
}

// 取消請求
val cancelled = edgeAI.cancelRequest("request-123")
if (cancelled) {
    Log.d("EdgeAI", "Request cancelled successfully")
}
```

---

## 🛠 錯誤處理

### 常見錯誤類型

```kotlin
edgeAI.sendChatRequest("request-123", chatRequest) { response ->
    when (response) {
        is AIResponse.Success -> {
            // 成功處理
        }
        is AIResponse.Error -> {
            when (response.error) {
                is NetworkError -> {
                    // 網絡錯誤
                    showNetworkError()
                }
                is TimeoutError -> {
                    // 超時錯誤
                    showTimeoutError()
                }
                is AuthenticationError -> {
                    // 認證錯誤
                    showAuthError()
                }
                else -> {
                    // 其他錯誤
                    showGenericError(response.error.message)
                }
            }
        }
    }
}
```

---

## 📱 權限要求

在您的 `AndroidManifest.xml` 中添加必要的權限：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />  <!-- 如果需要語音功能 -->
```

---

## 🔍 調試與日誌

### 啟用調試模式

```kotlin
val edgeAI = EdgeAI.Builder(this)
    .setLogLevel(LogLevel.DEBUG)
    .build()
```

### 查看日誌

```bash
adb logcat | grep "EdgeAI"
```

---

## 📚 API 參考

### 主要類別

- `EdgeAI` - 主要客戶端類別
- `ChatRequest` - 聊天請求
- `ASRRequest` - 語音識別請求
- `TTSRequest` - 文字轉語音請求
- `AIResponse` - 響應封裝類別

### 配置選項

- `setApiKey()` - 設置 API 金鑰
- `setTimeout()` - 設置請求超時時間
- `setRetryCount()` - 設置重試次數
- `setLogLevel()` - 設置日誌級別

---

## 🤝 支援

如果您遇到問題或有建議，請：

1. 檢查 [GitHub Issues](https://github.com/mtkresearch/BreezeApp-engine/issues)
2. 查看 [JitPack 狀態](https://jitpack.io/#mtkresearch/BreezeApp-engine)
3. 聯繫 mtkresearch 團隊

---

© 2025 mtkresearch - EdgeAI Library v0.1.2 