# BreezeApp-engine Integration Patterns

**Purpose**: Common integration patterns, best practices, and anti-patterns for client applications
**Audience**: Client app developers, integration engineers
**Last Updated**: 2025-11-03

---

## Table of Contents

1. [Integration Pattern Overview](#integration-pattern-overview)
2. [Basic Service Binding Pattern](#basic-service-binding-pattern)
3. [Lifecycle-Aware Binding Pattern](#lifecycle-aware-binding-pattern)
4. [Repository Pattern with Engine](#repository-pattern-with-engine)
5. [Streaming Inference Pattern](#streaming-inference-pattern)
6. [Error Handling Patterns](#error-handling-patterns)
7. [Version Compatibility Pattern](#version-compatibility-pattern)
8. [Resource Management Patterns](#resource-management-patterns)
9. [Testing Patterns](#testing-patterns)
10. [Anti-Patterns (What to Avoid)](#anti-patterns-what-to-avoid)

---

## Integration Pattern Overview (T075)

### Pattern Catalog

| Pattern | Use Case | Complexity | Recommended For |
|---------|----------|------------|-----------------|
| **Basic Binding** | Simple one-off inference | Low | Prototypes, demos |
| **Lifecycle-Aware** | Production Android apps | Medium | Most apps |
| **Repository** | Clean Architecture apps | Medium-High | Enterprise apps |
| **Streaming** | Real-time text generation | Medium | Chat UIs |
| **Error Handling** | Robust production apps | Medium | All production apps |
| **Version Compatibility** | Long-term maintenance | High | All apps (MVP+) |

### Architecture Layers

```
┌─────────────────────────────────────────────────────────────┐
│  Client Application Architecture                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Presentation Layer (UI)                            │   │
│  │  • Activity / Fragment                              │   │
│  │  • ViewModel (LiveData / StateFlow)                 │   │
│  │  • Compose UI (optional)                            │   │
│  └────────────────────┬────────────────────────────────┘   │
│                       │ Observe data                        │
│  ┌────────────────────▼────────────────────────────────┐   │
│  │  Domain Layer (Business Logic)                      │   │
│  │  • Use Cases (e.g., GenerateTextUseCase)           │   │
│  │  • Repository Interfaces                            │   │
│  └────────────────────┬────────────────────────────────┘   │
│                       │ Call methods                        │
│  ┌────────────────────▼────────────────────────────────┐   │
│  │  Data Layer (Repository Implementation)             │   │
│  │  • AIEngineRepository                               │   │
│  │  • EngineClient (Service binding)                   │   │
│  └────────────────────┬────────────────────────────────┘   │
│                       │ AIDL calls                          │
└───────────────────────┼─────────────────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────────────────┐
│  BreezeApp AI Engine (Service)                             │
│  • AIEngineService                                         │
│  • LLMManager, VLMManager, etc.                            │
└─────────────────────────────────────────────────────────────┘
```

---

## Basic Service Binding Pattern (T076)

### Simple Synchronous Binding

**Use Case**: Quick prototyping, simple scripts, testing

```kotlin
class SimpleEngineClient(private val context: Context) {
    companion object {
        private const val ENGINE_PACKAGE = "com.mtkresearch.breezeapp.engine"
        private const val ENGINE_ACTION = "com.mtkresearch.breezeapp.engine.AI_SERVICE"
    }

    private var engineService: IAIEngineService? = null
    private val bindingLatch = CountDownLatch(1)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            engineService = IAIEngineService.Stub.asInterface(service)
            bindingLatch.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            engineService = null
        }
    }

    fun bind(): Boolean {
        val intent = Intent(ENGINE_ACTION).apply {
            setPackage(ENGINE_PACKAGE)
        }

        val bound = context.bindService(
            intent,
            connection,
            Context.BIND_AUTO_CREATE
        )

        if (!bound) return false

        // Wait for connection (blocking, up to 10 seconds)
        return bindingLatch.await(10, TimeUnit.SECONDS)
    }

    fun unbind() {
        context.unbindService(connection)
        engineService = null
    }

    fun inferText(prompt: String): String? {
        return engineService?.inferText(prompt, Bundle())
    }
}

// Usage
val client = SimpleEngineClient(context)
if (client.bind()) {
    val result = client.inferText("Hello, AI")
    println("Response: $result")
    client.unbind()
}
```

### Pros & Cons

**Pros**:
- ✅ Simple, easy to understand
- ✅ Few lines of code
- ✅ Good for prototyping

**Cons**:
- ❌ Blocks on binding (CountDownLatch)
- ❌ No lifecycle awareness
- ❌ Manual unbind required (memory leak if forgotten)
- ❌ Not suitable for production

---

## Lifecycle-Aware Binding Pattern (T077)

### Android Lifecycle Integration

**Use Case**: Production Android apps with Activity/Fragment lifecycle

```kotlin
class LifecycleAwareEngineClient(
    private val context: Context,
    private val lifecycle: Lifecycle
) : DefaultLifecycleObserver {

    private var engineService: IAIEngineService? = null
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        data class Connected(val service: IAIEngineService) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val svc = IAIEngineService.Stub.asInterface(service)
            engineService = svc
            _connectionState.value = ConnectionState.Connected(svc)
            Log.i(TAG, "Engine service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            engineService = null
            _connectionState.value = ConnectionState.Disconnected
            Log.w(TAG, "Engine service disconnected")
        }

        override fun onBindingDied(name: ComponentName?) {
            engineService = null
            _connectionState.value = ConnectionState.Error("Service binding died")
            Log.e(TAG, "Engine service binding died")
        }

        override fun onNullBinding(name: ComponentName?) {
            engineService = null
            _connectionState.value = ConnectionState.Error("Null binding (unauthorized?)")
            Log.e(TAG, "Engine service returned null binding")
        }
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        bind()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        unbind()
    }

    private fun bind() {
        if (_connectionState.value is ConnectionState.Connected) {
            Log.w(TAG, "Already connected, skipping bind")
            return
        }

        _connectionState.value = ConnectionState.Connecting

        val intent = Intent(ENGINE_ACTION).apply {
            setPackage(ENGINE_PACKAGE)
        }

        val bound = context.bindService(
            intent,
            connection,
            Context.BIND_AUTO_CREATE
        )

        if (!bound) {
            _connectionState.value = ConnectionState.Error("bindService() returned false")
        }
    }

    private fun unbind() {
        try {
            context.unbindService(connection)
        } catch (e: IllegalArgumentException) {
            // Not bound, ignore
        }
        engineService = null
        _connectionState.value = ConnectionState.Disconnected
    }

    suspend fun inferText(prompt: String, params: Bundle = Bundle()): Result<String> {
        return when (val state = _connectionState.value) {
            is ConnectionState.Connected -> {
                try {
                    val result = withContext(Dispatchers.IO) {
                        state.service.inferText(prompt, params)
                    }
                    Result.success(result)
                } catch (e: RemoteException) {
                    Result.failure(e)
                }
            }
            is ConnectionState.Error -> {
                Result.failure(IllegalStateException(state.message))
            }
            else -> {
                Result.failure(IllegalStateException("Service not connected"))
            }
        }
    }

    companion object {
        private const val TAG = "LifecycleAwareEngineClient"
        private const val ENGINE_PACKAGE = "com.mtkresearch.breezeapp.engine"
        private const val ENGINE_ACTION = "com.mtkresearch.breezeapp.engine.AI_SERVICE"
    }
}

// Usage in Activity/Fragment
class MainActivity : AppCompatActivity() {
    private lateinit var engineClient: LifecycleAwareEngineClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        engineClient = LifecycleAwareEngineClient(this, lifecycle)

        // Observe connection state
        lifecycleScope.launch {
            engineClient.connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Connected -> {
                        // Enable UI
                        enableInferenceButton()
                    }
                    is ConnectionState.Error -> {
                        showError(state.message)
                    }
                    else -> {
                        // Disable UI
                        disableInferenceButton()
                    }
                }
            }
        }
    }

    private fun generateText(prompt: String) {
        lifecycleScope.launch {
            val result = engineClient.inferText(prompt)
            result.onSuccess { text ->
                displayResult(text)
            }.onFailure { error ->
                showError(error.message ?: "Unknown error")
            }
        }
    }
}
```

### Advantages

- ✅ Automatic bind/unbind with lifecycle
- ✅ No memory leaks
- ✅ StateFlow for reactive UI updates
- ✅ Coroutine-friendly
- ✅ Handles all connection edge cases (null binding, died, etc.)

---

## Repository Pattern with Engine (T078)

### Clean Architecture Integration

**Use Case**: Apps following Clean Architecture / MVVM

```kotlin
// Domain Layer - Repository Interface
interface AIRepository {
    suspend fun generateText(prompt: String, params: InferenceParams): Result<String>
    suspend fun generateTextStreaming(
        prompt: String,
        params: InferenceParams
    ): Flow<GenerationState>
    fun getConnectionState(): StateFlow<ConnectionState>
}

data class InferenceParams(
    val temperature: Float = 0.7f,
    val maxTokens: Int = 512,
    val topK: Int = 40,
    val topP: Float = 0.9f
) {
    fun toBundle(): Bundle = Bundle().apply {
        putFloat("temperature", temperature)
        putInt("max_tokens", maxTokens)
        putInt("top_k", topK)
        putFloat("top_p", topP)
    }
}

sealed class GenerationState {
    object Idle : GenerationState()
    object Generating : GenerationState()
    data class Token(val text: String, val fullText: String) : GenerationState()
    data class Complete(val fullText: String) : GenerationState()
    data class Error(val message: String) : GenerationState()
}

// Data Layer - Repository Implementation
class AIEngineRepository(
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AIRepository {

    private val engineClient = LifecycleAwareEngineClient(context, lifecycle)

    override fun getConnectionState(): StateFlow<ConnectionState> {
        return engineClient.connectionState
    }

    override suspend fun generateText(
        prompt: String,
        params: InferenceParams
    ): Result<String> = withContext(dispatcher) {
        engineClient.inferText(prompt, params.toBundle())
    }

    override suspend fun generateTextStreaming(
        prompt: String,
        params: InferenceParams
    ): Flow<GenerationState> = callbackFlow {
        val callback = object : IStreamCallback.Stub() {
            override fun onStart() {
                trySend(GenerationState.Generating)
            }

            override fun onToken(token: String) {
                // fullText maintained on client side
                val fullText = (channel.tryReceive().getOrNull() as? GenerationState.Token)
                    ?.fullText.orEmpty() + token
                trySend(GenerationState.Token(token, fullText))
            }

            override fun onComplete(fullText: String) {
                trySend(GenerationState.Complete(fullText))
                close()
            }

            override fun onError(errorCode: Int, message: String) {
                trySend(GenerationState.Error(message))
                close()
            }
        }

        when (val state = engineClient.connectionState.value) {
            is ConnectionState.Connected -> {
                try {
                    state.service.inferTextStreaming(prompt, params.toBundle(), callback)
                } catch (e: RemoteException) {
                    send(GenerationState.Error(e.message ?: "Remote exception"))
                    close()
                }
            }
            else -> {
                send(GenerationState.Error("Service not connected"))
                close()
            }
        }

        awaitClose {
            // Cleanup if needed
        }
    }
}

// Domain Layer - Use Case
class GenerateTextUseCase(
    private val repository: AIRepository
) {
    suspend operator fun invoke(
        prompt: String,
        params: InferenceParams = InferenceParams()
    ): Result<String> {
        // Business logic can go here (e.g., prompt preprocessing)
        return repository.generateText(prompt, params)
    }
}

// Presentation Layer - ViewModel
class ChatViewModel(
    private val generateTextUseCase: GenerateTextUseCase,
    private val repository: AIRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Idle)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val connectionState = repository.getConnectionState()

    fun generateText(userMessage: String) {
        viewModelScope.launch {
            _uiState.value = ChatUiState.Loading

            generateTextUseCase(userMessage)
                .onSuccess { response ->
                    _uiState.value = ChatUiState.Success(response)
                }
                .onFailure { error ->
                    _uiState.value = ChatUiState.Error(error.message ?: "Unknown error")
                }
        }
    }

    sealed class ChatUiState {
        object Idle : ChatUiState()
        object Loading : ChatUiState()
        data class Success(val message: String) : ChatUiState()
        data class Error(val message: String) : ChatUiState()
    }
}

// Dependency Injection (Hilt example)
@Module
@InstallIn(SingletonComponent::class)
object AIModule {
    @Provides
    @Singleton
    fun provideAIRepository(
        @ApplicationContext context: Context,
        lifecycle: Lifecycle
    ): AIRepository {
        return AIEngineRepository(context, lifecycle)
    }

    @Provides
    fun provideGenerateTextUseCase(repository: AIRepository): GenerateTextUseCase {
        return GenerateTextUseCase(repository)
    }
}
```

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│  Clean Architecture Layers                                 │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Presentation:                                             │
│  ChatViewModel ──uses──> GenerateTextUseCase               │
│       │                           │                         │
│       └── observes ──> AIRepository Interface              │
│                                   │                         │
│  Domain:                          │                         │
│  GenerateTextUseCase              │                         │
│  AIRepository (interface)         │                         │
│                                   │                         │
│  Data:                            │                         │
│  AIEngineRepository ──implements──┘                         │
│       └── uses ──> LifecycleAwareEngineClient              │
│                           │                                 │
│                           └──AIDL──> AIEngineService       │
└─────────────────────────────────────────────────────────────┘
```

---

## Streaming Inference Pattern (T079)

### Real-Time Token Streaming

**Use Case**: Chat UIs, typewriter effects, real-time generation

```kotlin
class StreamingInferenceManager(
    private val engineService: IAIEngineService
) {
    fun generateStreaming(
        prompt: String,
        params: Bundle,
        onToken: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val buffer = StringBuilder()

        val callback = object : IStreamCallback.Stub() {
            override fun onStart() {
                // Optional: Show loading indicator
            }

            override fun onToken(token: String) {
                buffer.append(token)
                onToken(token)  // Incremental update
            }

            override fun onComplete(fullText: String) {
                onComplete(fullText)
            }

            override fun onError(errorCode: Int, message: String) {
                onError(message)
            }
        }

        engineService.inferTextStreaming(prompt, params, callback)
    }
}

// UI Integration (Jetpack Compose example)
@Composable
fun StreamingChatMessage(
    viewModel: ChatViewModel,
    userMessage: String
) {
    val aiResponse by viewModel.streamingResponse.collectAsState()

    Column {
        Text("You: $userMessage")

        when (val state = aiResponse) {
            is StreamingState.Generating -> {
                Text(
                    text = state.partialText,
                    modifier = Modifier.animateContentSize()  // Smooth height animation
                )
                // Cursor animation
                BlinkingCursor()
            }
            is StreamingState.Complete -> {
                Text(state.fullText)
            }
            is StreamingState.Error -> {
                Text("Error: ${state.message}", color = Color.Red)
            }
            else -> { /* Idle */ }
        }
    }

    LaunchedEffect(userMessage) {
        viewModel.generateStreaming(userMessage)
    }
}

// ViewModel
class ChatViewModel(
    private val repository: AIRepository
) : ViewModel() {

    private val _streamingResponse = MutableStateFlow<StreamingState>(StreamingState.Idle)
    val streamingResponse: StateFlow<StreamingState> = _streamingResponse.asStateFlow()

    fun generateStreaming(prompt: String) {
        viewModelScope.launch {
            repository.generateTextStreaming(prompt, InferenceParams())
                .collect { state ->
                    _streamingResponse.value = when (state) {
                        is GenerationState.Generating -> StreamingState.Generating("")
                        is GenerationState.Token -> StreamingState.Generating(state.fullText)
                        is GenerationState.Complete -> StreamingState.Complete(state.fullText)
                        is GenerationState.Error -> StreamingState.Error(state.message)
                        else -> StreamingState.Idle
                    }
                }
        }
    }

    sealed class StreamingState {
        object Idle : StreamingState()
        data class Generating(val partialText: String) : StreamingState()
        data class Complete(val fullText: String) : StreamingState()
        data class Error(val message: String) : StreamingState()
    }
}
```

### Performance Optimization

```kotlin
// Debounce rapid token updates for smoother UI
fun Flow<GenerationState.Token>.debounceTokens(
    windowMs: Long = 50
): Flow<GenerationState.Token> {
    return this.conflate()  // Drop intermediate values if collector is slow
        .debounce(windowMs)  // Wait for silence before emitting
}

// Usage
repository.generateTextStreaming(prompt, params)
    .filterIsInstance<GenerationState.Token>()
    .debounceTokens(50)
    .collect { state ->
        updateUI(state.fullText)
    }
```

---

## Error Handling Patterns

### Comprehensive Error Handling

```kotlin
class RobustEngineClient(
    private val context: Context,
    private val lifecycle: Lifecycle
) {
    suspend fun inferTextWithRetry(
        prompt: String,
        maxRetries: Int = 3
    ): Result<String> {
        repeat(maxRetries) { attempt ->
            when (val state = connectionState.value) {
                is ConnectionState.Connected -> {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            state.service.inferText(prompt, Bundle())
                        }
                        return Result.success(result)
                    } catch (e: RemoteException) {
                        // Service died, try rebinding
                        if (attempt < maxRetries - 1) {
                            delay(1000 * (attempt + 1))  // Exponential backoff
                            rebind()
                        } else {
                            return Result.failure(e)
                        }
                    } catch (e: DeadObjectException) {
                        // Service process crashed
                        Log.e(TAG, "Service process died", e)
                        if (attempt < maxRetries - 1) {
                            delay(2000)
                            rebind()
                        } else {
                            return Result.failure(e)
                        }
                    }
                }
                is ConnectionState.Error -> {
                    return Result.failure(IllegalStateException(state.message))
                }
                else -> {
                    // Not connected yet, wait
                    delay(500)
                }
            }
        }

        return Result.failure(IllegalStateException("Max retries exceeded"))
    }

    private suspend fun rebind() {
        unbind()
        delay(100)
        bind()
        // Wait for connection
        connectionState.first { it is ConnectionState.Connected }
    }
}

// Error code interpretation
fun interpretErrorCode(errorCode: Int): String {
    return when (errorCode) {
        IAIEngineService.ERROR_NONE -> "Success"
        IAIEngineService.ERROR_MODEL_NOT_LOADED -> "Model not loaded. Please load a model first."
        IAIEngineService.ERROR_INVALID_INPUT -> "Invalid input. Check prompt format."
        IAIEngineService.ERROR_OUT_OF_MEMORY -> "Out of memory. Try a smaller model or shorter prompt."
        IAIEngineService.ERROR_TIMEOUT -> "Request timed out. Try again."
        IAIEngineService.ERROR_UNSUPPORTED_OPERATION -> "This operation is not supported."
        IAIEngineService.ERROR_INTERNAL -> "Internal engine error. Please report this."
        else -> "Unknown error code: $errorCode"
    }
}
```

---

## Version Compatibility Pattern

### Runtime Version Checking

```kotlin
class VersionCompatibilityChecker(
    private val engineService: IAIEngineService
) {
    companion object {
        const val MIN_REQUIRED_API_VERSION = 1
        const val RECOMMENDED_API_VERSION = 1
    }

    fun checkCompatibility(): CompatibilityResult {
        val engineVersion = try {
            engineService.version
        } catch (e: RemoteException) {
            return CompatibilityResult.Error("Cannot check version: ${e.message}")
        }

        return when {
            engineVersion < MIN_REQUIRED_API_VERSION -> {
                CompatibilityResult.Incompatible(
                    "Engine version $engineVersion is too old. " +
                    "Minimum required: $MIN_REQUIRED_API_VERSION. " +
                    "Please update BreezeApp Engine."
                )
            }
            engineVersion < RECOMMENDED_API_VERSION -> {
                CompatibilityResult.Compatible(
                    warning = "Engine version $engineVersion is outdated. " +
                    "Recommended: $RECOMMENDED_API_VERSION. " +
                    "Some features may be unavailable."
                )
            }
            else -> {
                CompatibilityResult.Compatible()
            }
        }
    }

    sealed class CompatibilityResult {
        data class Compatible(val warning: String? = null) : CompatibilityResult()
        data class Incompatible(val reason: String) : CompatibilityResult()
        data class Error(val message: String) : CompatibilityResult()
    }
}

// Check on connection
lifecycle AwareEngineClient(context, lifecycle).apply {
    connectionState.collect { state ->
        if (state is ConnectionState.Connected) {
            val checker = VersionCompatibilityChecker(state.service)
            when (val result = checker.checkCompatibility()) {
                is CompatibilityResult.Incompatible -> {
                    showUpdateDialog(result.reason)
                    unbind()
                }
                is CompatibilityResult.Compatible -> {
                    result.warning?.let { showWarningSnackbar(it) }
                }
                is CompatibilityResult.Error -> {
                    showError(result.message)
                }
            }
        }
    }
}
```

---

## Resource Management Patterns

### Connection Pooling (Advanced)

```kotlin
object EngineConnectionPool {
    private val connections = mutableMapOf<Context, LifecycleAwareEngineClient>()
    private val lock = ReentrantLock()

    fun getOrCreate(context: Context, lifecycle: Lifecycle): LifecycleAwareEngineClient {
        lock.withLock {
            return connections.getOrPut(context) {
                LifecycleAwareEngineClient(context, lifecycle).also { client ->
                    lifecycle.addObserver(object : DefaultLifecycleObserver {
                        override fun onDestroy(owner: LifecycleOwner) {
                            connections.remove(context)
                        }
                    })
                }
            }
        }
    }
}
```

---

## Testing Patterns

### Mocking Engine Service

```kotlin
class FakeAIEngineService : IAIEngineService.Stub() {
    override fun getVersion(): Int = 1
    override fun getVersionInfo(): Bundle = Bundle()
    override fun getCapabilities(): Bundle = Bundle()

    override fun inferText(input: String?, params: Bundle?): String {
        return "Fake response to: $input"
    }

    // ... other methods with fake implementations
}

// Unit test
class ChatViewModelTest {
    private val fakeService = FakeAIEngineService()
    private val fakeRepository = FakeAIRepository(fakeService)
    private val viewModel = ChatViewModel(GenerateTextUseCase(fakeRepository), fakeRepository)

    @Test
    fun `test generate text success`() = runTest {
        viewModel.generateText("Hello")

        val state = viewModel.uiState.value
        assertTrue(state is ChatViewModel.ChatUiState.Success)
        assertEquals("Fake response to: Hello", (state as ChatViewModel.ChatUiState.Success).message)
    }
}
```

---

## Anti-Patterns (What to Avoid)

### ❌ Anti-Pattern 1: Binding in onCreate() without Lifecycle Observer

```kotlin
// BAD: Manual binding without lifecycle awareness
class BadActivity : AppCompatActivity() {
    private var engineService: IAIEngineService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindService(intent, connection, BIND_AUTO_CREATE)
    }

    // PROBLEM: What if activity is destroyed? Memory leak!
    // PROBLEM: No unbind() call
}
```

**Fix**: Use LifecycleObserver or lifecycle-aware client.

### ❌ Anti-Pattern 2: Synchronous Blocking on Main Thread

```kotlin
// BAD: Blocking main thread
button.setOnClickListener {
    val result = engineService.inferText(prompt, params)  // Blocks UI!
    textView.text = result
}
```

**Fix**: Use coroutines or async callbacks.

### ❌ Anti-Pattern 3: Ignoring Version Compatibility

```kotlin
// BAD: Assuming API is always compatible
engineService.someNewMethod()  // Crashes if engine is old version!
```

**Fix**: Check version first, handle gracefully.

### ❌ Anti-Pattern 4: Not Handling Service Death

```kotlin
// BAD: No error handling
try {
    engineService.inferText(prompt, params)
} catch (e: RemoteException) {
    // Ignored!
}
```

**Fix**: Retry with exponential backoff, rebind if needed.

### ❌ Anti-Pattern 5: Leaking AIDL Callbacks

```kotlin
// BAD: Callback holds reference to Activity
val callback = object : IStreamCallback.Stub() {
    override fun onToken(token: String) {
        this@Activity.updateUI(token)  // Activity leaked if service holds callback!
    }
}
```

**Fix**: Use WeakReference or unregister callback in onDestroy().

---

**Document Version**: 1.0
**Maintained By**: BreezeApp Team
**Last Updated**: 2025-11-03
