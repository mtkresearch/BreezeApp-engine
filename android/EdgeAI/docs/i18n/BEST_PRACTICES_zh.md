# 最佳實踐

[← 回到 README](./README_zh.md)

> **生產環境實作技巧**：生命週期管理、狀態處理、UI/UX 建議和效能考量。

---

## 生命週期管理

### ViewModel 整合

```kotlin
class ChatViewModel : ViewModel() {
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    init {
        initializeEdgeAI()
    }
    
    private fun initializeEdgeAI() {
        viewModelScope.launch {
            try {
                EdgeAI.initializeAndWait(context, timeoutMs = 10000)
                _isConnected.value = true
            } catch (e: ServiceConnectionException) {
                _isConnected.value = false
                // 處理連接失敗
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        EdgeAI.shutdown()
    }
}
```

### Activity/Fragment 整合

```kotlin
class ChatActivity : AppCompatActivity() {
    private val viewModel: ChatViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 觀察連接狀態
        lifecycleScope.launch {
            viewModel.isConnected.collect { isConnected ->
                updateUI(isConnected)
            }
        }
    }
}
```

---

## 狀態管理

### 請求狀態處理

```kotlin
sealed class ChatState {
    object Idle : ChatState()
    object Loading : ChatState()
    data class Success(val response: String) : ChatState()
    data class Error(val message: String) : ChatState()
}

class ChatViewModel : ViewModel() {
    private val _chatState = MutableStateFlow<ChatState>(ChatState.Idle)
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()
    
    fun sendMessage(prompt: String) {
        viewModelScope.launch {
            _chatState.value = ChatState.Loading
            
            try {
                val request = chatRequest(prompt = prompt)
                val response = EdgeAI.chat(request).first()
                _chatState.value = ChatState.Success(response.choices.firstOrNull()?.message?.content ?: "")
            } catch (e: EdgeAIException) {
                _chatState.value = ChatState.Error(e.message ?: "未知錯誤")
            }
        }
    }
}
```

### 對話歷史

```kotlin
data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class ChatViewModel : ViewModel() {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    fun addMessage(content: String, isUser: Boolean) {
        val message = ChatMessage(content, isUser)
        _messages.value = _messages.value + message
    }
}
```

---

## UI/UX 建議

### 載入狀態

```kotlin
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val chatState by viewModel.chatState.collectAsState()
    
    when (chatState) {
        is ChatState.Loading -> {
            LoadingIndicator()
        }
        is ChatState.Success -> {
            ChatContent(response = chatState.response)
        }
        is ChatState.Error -> {
            ErrorMessage(message = chatState.message) {
                // 重試動作
            }
        }
        else -> {
            // 閒置狀態
        }
    }
}
```

### 串流 UI 更新

```kotlin
@Composable
fun StreamingChat(viewModel: ChatViewModel) {
    var currentResponse by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        viewModel.sendStreamingMessage("告訴我一個故事").collect { response ->
            when (response) {
                is ChatResponse.Stream -> {
                    currentResponse += response.delta?.message?.content ?: ""
                }
                is ChatResponse.Final -> {
                    // 收到最終回應
                }
            }
        }
    }
    
    Text(text = currentResponse)
}
```

### 錯誤處理 UI

```kotlin
@Composable
fun ErrorDialog(
    error: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("錯誤") },
        text = { Text(error) },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text("重試")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}
```

---

## 效能考量

### 請求優化

```kotlin
// 使用適當的超時時間
val request = chatRequest(
    prompt = "簡短回應",
    maxTokens = 100,  // 限制回應長度
    temperature = 0.7f
)

// 取消長時間運行的請求
val job = viewModelScope.launch {
    EdgeAI.chat(request).collect { response ->
        // 處理回應
    }
}

// 如果用戶離開則取消
job.cancel()
```

### 記憶體管理

```kotlin
class ChatViewModel : ViewModel() {
    private val _responses = MutableStateFlow<List<String>>(emptyList())
    
    fun addResponse(response: String) {
        // 限制歷史記錄以防止記憶體問題
        val current = _responses.value
        if (current.size >= 50) {
            _responses.value = current.drop(1) + response
        } else {
            _responses.value = current + response
        }
    }
}
```

### 背景處理

```kotlin
// 使用適當的調度器
viewModelScope.launch(Dispatchers.IO) {
    // 重處理
    val result = processLargeRequest()
    
    withContext(Dispatchers.Main) {
        // 更新 UI
        updateUI(result)
    }
}
```

---

## 安全性最佳實踐

### 輸入驗證

```kotlin
fun validateChatInput(input: String): Boolean {
    return input.isNotBlank() && 
           input.length <= 4096 && 
           !input.contains("<script>")
}

fun sendMessage(input: String) {
    if (!validateChatInput(input)) {
        showError("無效輸入")
        return
    }
    
    // 發送驗證過的輸入
    viewModel.sendMessage(input)
}
```

### 錯誤資訊

```kotlin
// 不要在錯誤訊息中暴露敏感資訊
catch (e: EdgeAIException) {
    when (e) {
        is AuthenticationException -> {
            showError("認證失敗")
        }
        is ServiceConnectionException -> {
            showError("服務不可用")
        }
        else -> {
            showError("發生錯誤")
        }
    }
}
```

---

## 測試

### 單元測試

```kotlin
@Test
fun `test chat request success`() = runTest {
    // 模擬 EdgeAI 回應
    val mockResponse = ChatResponse.Final(listOf(
        ChatChoice(ChatMessage("測試回應"), null)
    ))
    
    // 測試您的 ViewModel 邏輯
    viewModel.sendMessage("測試")
    
    assertEquals(ChatState.Success("測試回應"), viewModel.chatState.value)
}
```

### 整合測試

```kotlin
@Test
fun `test EdgeAI integration`() = runTest {
    // 使用真實的 EdgeAI SDK 測試
    EdgeAI.initializeAndWait(context)
    
    val request = chatRequest(prompt = "你好")
    val response = EdgeAI.chat(request).first()
    
    assertNotNull(response)
    EdgeAI.shutdown()
}
```

---

## 監控與分析

### 效能追蹤

```kotlin
fun trackRequestPerformance(request: ChatRequest) {
    val startTime = System.currentTimeMillis()
    
    EdgeAI.chat(request).collect { response ->
        val duration = System.currentTimeMillis() - startTime
        analytics.track("chat_request_duration", duration)
    }
}
```

### 錯誤追蹤

```kotlin
EdgeAI.chat(request).catch { error ->
    analytics.track("chat_request_error", error.javaClass.simpleName)
    throw error
}.collect { response ->
    // 處理成功
}
``` 