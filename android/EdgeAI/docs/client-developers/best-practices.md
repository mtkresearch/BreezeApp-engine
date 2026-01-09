# Best Practices

[← Back to README](../README.md)

> **Production-ready implementation tips**: Lifecycle management, state handling, UI/UX recommendations, and performance considerations.

---

## Lifecycle Management

### ViewModel Integration

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
                // Handle connection failure
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        EdgeAI.shutdown()
    }
}
```

### Activity/Fragment Integration

```kotlin
class ChatActivity : AppCompatActivity() {
    private val viewModel: ChatViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Observe connection state
        lifecycleScope.launch {
            viewModel.isConnected.collect { isConnected ->
                updateUI(isConnected)
            }
        }
    }
}
```

---

## State Management

### Request State Handling

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
                _chatState.value = ChatState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
```

### Conversation History

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

## UI/UX Recommendations

### Loading States

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
                // Retry action
            }
        }
        else -> {
            // Idle state
        }
    }
}
```

### Streaming UI Updates

```kotlin
@Composable
fun StreamingChat(viewModel: ChatViewModel) {
    var currentResponse by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        viewModel.sendStreamingMessage("Tell me a story").collect { response ->
            when (response) {
                is ChatResponse.Stream -> {
                    currentResponse += response.delta?.message?.content ?: ""
                }
                is ChatResponse.Final -> {
                    // Final response received
                }
            }
        }
    }
    
    Text(text = currentResponse)
}
```

### Error Handling UI

```kotlin
@Composable
fun ErrorDialog(
    error: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Error") },
        text = { Text(error) },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}
```

---

## Performance Considerations

### Request Optimization

```kotlin
// Use appropriate timeouts
val request = chatRequest(
    prompt = "Short response",
    maxTokens = 100,  // Limit response length
    temperature = 0.7f
)

// Cancel long-running requests
val job = viewModelScope.launch {
    EdgeAI.chat(request).collect { response ->
        // Handle response
    }
}

// Cancel if user navigates away
job.cancel()
```

### Memory Management

```kotlin
class ChatViewModel : ViewModel() {
    private val _responses = MutableStateFlow<List<String>>(emptyList())
    
    fun addResponse(response: String) {
        // Limit history to prevent memory issues
        val current = _responses.value
        if (current.size >= 50) {
            _responses.value = current.drop(1) + response
        } else {
            _responses.value = current + response
        }
    }
}
```

### Background Processing

```kotlin
// Use appropriate dispatchers
viewModelScope.launch(Dispatchers.IO) {
    // Heavy processing
    val result = processLargeRequest()
    
    withContext(Dispatchers.Main) {
        // Update UI
        updateUI(result)
    }
}
```

---

## Security Best Practices

### Input Validation

```kotlin
fun validateChatInput(input: String): Boolean {
    return input.isNotBlank() && 
           input.length <= 4096 && 
           !input.contains("<script>")
}

fun sendMessage(input: String) {
    if (!validateChatInput(input)) {
        showError("Invalid input")
        return
    }
    
    // Send validated input
    viewModel.sendMessage(input)
}
```

### Error Information

```kotlin
// Don't expose sensitive information in error messages
catch (e: EdgeAIException) {
    when (e) {
        is AuthenticationException -> {
            showError("Authentication failed")
        }
        is ServiceConnectionException -> {
            showError("Service unavailable")
        }
        else -> {
            showError("An error occurred")
        }
    }
}
```

---

## Testing

### Unit Testing

```kotlin
@Test
fun `test chat request success`() = runTest {
    // Mock EdgeAI responses
    val mockResponse = ChatResponse.Final(listOf(
        ChatChoice(ChatMessage("Test response"), null)
    ))
    
    // Test your ViewModel logic
    viewModel.sendMessage("Test")
    
    assertEquals(ChatState.Success("Test response"), viewModel.chatState.value)
}
```

### Integration Testing

```kotlin
@Test
fun `test EdgeAI integration`() = runTest {
    // Test with real EdgeAI SDK
    EdgeAI.initializeAndWait(context)
    
    val request = chatRequest(prompt = "Hello")
    val response = EdgeAI.chat(request).first()
    
    assertNotNull(response)
    EdgeAI.shutdown()
}
```

---

## Monitoring & Analytics

### Performance Tracking

```kotlin
fun trackRequestPerformance(request: ChatRequest) {
    val startTime = System.currentTimeMillis()
    
    EdgeAI.chat(request).collect { response ->
        val duration = System.currentTimeMillis() - startTime
        analytics.track("chat_request_duration", duration)
    }
}
```

### Error Tracking

```kotlin
EdgeAI.chat(request).catch { error ->
    analytics.track("chat_request_error", error.javaClass.simpleName)
    throw error
}.collect { response ->
    // Handle success
}
``` 