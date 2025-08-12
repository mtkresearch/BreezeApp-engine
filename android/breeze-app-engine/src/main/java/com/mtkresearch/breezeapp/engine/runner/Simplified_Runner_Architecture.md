# BreezeApp-engine Third-Party Runner Architecture

## Overview

BreezeApp-engine is designed as an **extensible AI platform** that enables seamless third-party integration through a standardized annotation-based runner system. This document defines the architecture principles and developer guidelines for creating and integrating AI runners.

## Core Architecture Principles

### 1. Annotation-Based Discovery
- **Zero Configuration**: Runners are automatically discovered via `@AIRunner` annotations
- **Package Agnostic**: All runners use unified package structure `com.mtkresearch.breezeapp.engine.runner`
- **Classpath Scanning**: No manual registration or configuration files required

### 2. Standardized Interface
All runners implement the `BaseRunner` interface providing consistent:
- Model loading/unloading lifecycle
- Synchronous and streaming inference execution  
- Hardware capability checking
- Error handling and logging

### 3. Vendor Prioritization System
- **Simple Priority Levels**: HIGH/NORMAL/LOW enum (no complex strings)
- **Vendor-Based Ordering**: Centralized priority by capability type
- **Hardware Requirements**: Closed enum preventing typos and inconsistencies

## Runner Annotation System

### @AIRunner Annotation
```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AIRunner(
    val capabilities: Array<CapabilityType>,                    // LLM, ASR, TTS, VLM
    val vendor: VendorType = VendorType.UNKNOWN,               // Vendor identification
    val priority: RunnerPriority = RunnerPriority.NORMAL,     // Simple priority level
    val hardwareRequirements: Array<HardwareRequirement> = [], // Hardware constraints
    val enabled: Boolean = true,                               // Runtime enable/disable
    val apiLevel: Int = 1                                      // Runner API version
)
```

### Vendor Types
```kotlin
enum class VendorType {
    MEDIATEK,     // MediaTek NPU/AI technology
    SHERPA,       // Sherpa ONNX framework
    OPENROUTER,   // OpenRouter API service
    META,         // Meta ExecuTorch framework
    UNKNOWN       // Unknown/fallback provider
}
```

### Priority Levels
```kotlin
enum class RunnerPriority {
    HIGH,      // Premium/flagship runners (hardware-accelerated, latest models)
    NORMAL,    // Standard runners (default, balanced performance)
    LOW        // Lite/fallback runners (CPU-only, lightweight models)
}
```

### Hardware Requirements
```kotlin
enum class HardwareRequirement {
    // Connectivity
    INTERNET,           // Network connection required
    
    // Processing units  
    MTK_NPU,            // MediaTek NPU required
    CPU,                // Basic CPU processing (always available)
    
    // Memory requirements
    HIGH_MEMORY,        // >8GB RAM
    MEDIUM_MEMORY,      // >4GB RAM  
    LOW_MEMORY,         // >2GB RAM
    
    // Storage requirements
    LARGE_STORAGE,      // >1GB for models
    MEDIUM_STORAGE,     // >500MB for models
    
    // Sensors
    MICROPHONE,         // Microphone access required
    CAMERA              // Camera access required
}
```

## Runner Implementation Guidelines

### Basic Runner Structure
```kotlin
// Package: com.mtkresearch.breezeapp.engine.runner
@AIRunner(
    capabilities = [CapabilityType.LLM],
    vendor = VendorType.OPENROUTER,
    priority = RunnerPriority.HIGH,
    hardwareRequirements = [HardwareRequirement.CPU, HardwareRequirement.HIGH_MEMORY]
)
class OpenRouterLLMRunner : BaseRunner {
    
    private var isModelLoaded = false
    
    override fun load(config: ModelConfig): Boolean {
        // Initialize your AI library/API
        return true
    }
    
    override fun run(input: InferenceRequest): InferenceResult {
        // Execute inference and return standardized result
        return InferenceResult.success(mapOf("text" to "response"))
    }
    
    override fun unload() {
        // Cleanup resources
        isModelLoaded = false
    }
    
    override fun getCapabilities() = listOf(CapabilityType.LLM)
    override fun isLoaded() = isModelLoaded
    override fun getRunnerInfo() = RunnerInfo(
        name = "OpenRouter",
        version = "1.0.0",
        capabilities = getCapabilities(),
        description = "OpenRouter LLM inference"
    )
    
    companion object {
        @JvmStatic
        fun isSupported(): Boolean {
            // Hardware detection logic
            return true
        }
    }
}
```

### Streaming Runners
For streaming capabilities (ASR, real-time inference), implement `FlowStreamingRunner`:

```kotlin
@AIRunner(
    capabilities = [CapabilityType.ASR],
    vendor = VendorType.SHERPA,
    hardwareRequirements = [HardwareRequirement.MICROPHONE]
)
class SherpaASRRunner : BaseRunner, FlowStreamingRunner {
    
    override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = flow {
        // Emit partial results as they become available
        emit(InferenceResult.success(outputs = mapOf("text" to partialText), partial = true))
        // Emit final result
        emit(InferenceResult.success(outputs = mapOf("text" to finalText), partial = false))
    }
}
```

## Vendor Priority System

The system uses a centralized ordering approach that prioritizes vendors by capability:

### LLM Priority Order
1. **MEDIATEK** (0-2): MediaTek NPU acceleration 
2. **META** (10-12): ExecuTorch local inference
3. **OPENROUTER** (20-22): Cloud API fallback

### Complete Vendor Priority Orders

#### LLM Capability Priority
```kotlin
val LLM_VENDOR_ORDER = listOf(
    VendorType.MEDIATEK,     // MediaTek NPU acceleration (highest performance)
    VendorType.META,         // Meta ExecuTorch local inference
    VendorType.OPENROUTER,   // OpenRouter cloud API (reliable fallback)
    VendorType.UNKNOWN       // Unknown/fallback
)
```

#### ASR Capability Priority
```kotlin
val ASR_VENDOR_ORDER = listOf(
    VendorType.SHERPA,       // Sherpa ONNX local processing (privacy-first)
    VendorType.OPENAI,       // OpenAI Whisper API
    VendorType.UNKNOWN
)
```

#### TTS Capability Priority
```kotlin
val TTS_VENDOR_ORDER = listOf(
    VendorType.SHERPA,       // Sherpa local TTS (privacy-first)
    VendorType.OPENAI,       // OpenAI TTS API
    VendorType.UNKNOWN
)
```

### Priority Calculation
```kotlin
// Formula: (vendorIndex × 10) + priorityIndex
// Priority within vendor: HIGH=0, NORMAL=1, LOW=2

// Example LLM ordering:
// MEDIATEK-HIGH:    (0 × 10) + 0 = 0    <- Highest priority (NPU acceleration)
// MEDIATEK-NORMAL:  (0 × 10) + 1 = 1
// MEDIATEK-LOW:     (0 × 10) + 2 = 2
// META-HIGH:        (1 × 10) + 0 = 10   // ExecuTorch local inference
// META-NORMAL:      (1 × 10) + 1 = 11
// META-LOW:         (1 × 10) + 2 = 12
// OPENROUTER-HIGH:  (2 × 10) + 0 = 20   // Cloud API
// OPENROUTER-NORMAL:(2 × 10) + 1 = 21
// OPENROUTER-LOW:   (2 × 10) + 2 = 22   <- Lowest priority
```

### Real-World Selection Examples

#### Scenario 1: Device with MediaTek NPU
Available runners: MEDIATEK-HIGH, OPENROUTER-NORMAL, OPENROUTER-HIGH

**Selection Order:**
1. ✅ **MEDIATEK-HIGH** (score: 0) - Best internal hardware
2. OPENROUTER-NORMAL (score: 11) - Local CPU fallback
3. OPENROUTER-HIGH (score: 10) - Cloud fallback

#### Scenario 2: Device without NPU
Available runners: META-HIGH, OPENROUTER-HIGH, OPENROUTER-NORMAL

**Selection Order:**
1. ✅ **META-HIGH** (score: 10) - Best local option (ExecuTorch)
2. OPENROUTER-HIGH (score: 20) - Cloud option (requires internet)
3. OPENROUTER-NORMAL (score: 21) - Standard cloud fallback

### Benefits
- **Predictable**: Hardware acceleration preferred over cloud
- **Transparent**: Clear formula for ranking runners
- **Flexible**: Easy to reorder vendors or add new priorities
- **Fair**: Third-party vendors get appropriate priority placement

## Quick Start Tutorial: Add Your Own Runner

### 5-Minute Tutorial: Create a Custom Runner

Want to add your own AI runner? Follow this simple tutorial:

#### Step 1: Create Your Runner Class (2 minutes)
```kotlin
// File: BreezeApp-engine/android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/runner/openrouter/MyCustomRunner.kt
package com.mtkresearch.breezeapp.engine.runner.openrouter

@AIRunner(
    capabilities = [CapabilityType.LLM],              // What can your runner do?
    vendor = VendorType.OPENROUTER,                   // Which AI provider/technology?
    priority = RunnerPriority.NORMAL,                // How important is it?
    hardwareRequirements = [HardwareRequirement.CPU] // What does it need?
)
class MyCustomRunner : BaseRunner {
    
    private var isLoaded = false
    
    override fun load(config: ModelConfig): Boolean {
        // TODO: Initialize your AI model/API here
        println("Loading my custom model from: ${config.modelPath}")
        isLoaded = true
        return true
    }
    
    override fun run(input: InferenceRequest): InferenceResult {
        if (!isLoaded) return InferenceResult.error(RunnerError.modelNotLoaded())
        
        // TODO: Replace this with your actual AI inference
        val userPrompt = input.inputs[InferenceRequest.INPUT_TEXT] as String
        val myResponse = "My custom AI response to: $userPrompt"
        
        return InferenceResult.success(
            outputs = mapOf(InferenceResult.OUTPUT_TEXT to myResponse),
            metadata = mapOf("backend" to "MyCustomAI", "version" to "1.0")
        )
    }
    
    override fun unload() {
        // TODO: Clean up your resources
        println("Unloading my custom model")
        isLoaded = false
    }
    
    override fun getCapabilities() = listOf(CapabilityType.LLM)
    override fun isLoaded() = isLoaded
    override fun getRunnerInfo() = RunnerInfo(
        name = "MyCustom-LLM",
        version = "1.0.0",
        capabilities = getCapabilities(),
        description = "My custom AI runner"
    )
}
```

#### Step 2: Test Your Runner (1 minute)
```kotlin
// Testing happens within the BreezeApp-engine service
// Runners are discovered automatically by the service's RunnerRegistry

// Client apps test via EdgeAI SDK:
EdgeAI.initializeAndWait(context)
val response = EdgeAI.chat(ChatRequest(
    messages = listOf(ChatMessage("user", "Hello AI!"))
)).first()
println("Result: ${response.choices.first().message?.content}")
```

#### Step 3: That's It! (1 minute)
Your runner is automatically discovered by the BreezeApp-engine service! All client apps instantly get access to your runner without any changes.

### Advanced: Connect Real AI Library

Replace the TODO sections with your actual AI library:

```kotlin
class MyOpenAIRunner : BaseRunner {
    private val client = OpenAI(apiKey = "your-key")
    
    override fun run(input: InferenceRequest): InferenceResult {
        val completion = client.chat.completions.create {
            model = "gpt-3.5-turbo"
            messages = listOf(
                ChatMessage(role = "user", content = input.inputs["text"] as String)
            )
        }
        return InferenceResult.success(
            outputs = mapOf("text" to completion.choices.first().message.content)
        )
    }
}
```

### Streaming Example (for ASR/Real-time)

```kotlin
@AIRunner(capabilities = [CapabilityType.ASR], vendor = VendorType.OPENROUTER)
class MyStreamingRunner : BaseRunner, FlowStreamingRunner {
    
    override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = flow {
        val audioData = input.inputs[InferenceRequest.INPUT_AUDIO] as ByteArray
        
        // Process audio in chunks and emit partial results
        audioData.asSequence().chunked(1000).forEachIndexed { index, chunk ->
            val partialText = "Partial result ${index + 1}"
            emit(InferenceResult.success(
                outputs = mapOf("text" to partialText),
                partial = true // This tells the system it's not the final result
            ))
            delay(100) // Simulate processing time
        }
        
        // Emit final result
        emit(InferenceResult.success(
            outputs = mapOf("text" to "Final transcription result"),
            partial = false
        ))
    }
}
```

### Common Patterns

#### Hardware Detection
```kotlin
companion object {
    @JvmStatic
    fun isSupported(): Boolean {
        return try {
            // Real-world hardware detection examples:
            
            // 1. Check MediaTek NPU availability
            val hasNPU = System.getProperty("ro.vendor.mtk_nn_support") == "1"
            
            // 2. Check minimum memory requirement (4GB for this example)
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory() / (1024 * 1024) // Convert to MB
            val hasEnoughMemory = maxMemory >= 4096
            
            // 3. Check if required native libraries are available
            val hasNativeLib = try {
                System.loadLibrary("your-ai-library")
                true
            } catch (e: UnsatisfiedLinkError) {
                false
            }
            
            // 4. Check API availability
            val hasAPIAccess = try {
                YourAILibrary.isAvailable() // Your library's availability check
            } catch (e: Exception) {
                false
            }
            
            // Combine all checks based on your runner's AI provider/technology
            when (thisRunner.vendor) {
                VendorType.MEDIATEK -> hasNPU && hasEnoughMemory        // MediaTek NPU
                VendorType.META -> hasNativeLib && hasEnoughMemory      // ExecuTorch runtime
                VendorType.OPENROUTER -> hasAPIAccess                   // Cloud API only needs network
                VendorType.SHERPA -> hasNativeLib                      // ONNX runtime
                else -> hasEnoughMemory // Fallback requirements
            }
            
        } catch (e: Exception) {
            Log.w("RunnerSupport", "Hardware detection failed", e)
            false
        }
    }
}
```

#### Error Handling
```kotlin
// Common error codes (from EngineConstants.kt)
object ErrorCodes {
    const val RUNTIME_ERROR = "E101"
    const val MODEL_NOT_LOADED = "E201" 
    const val INVALID_INPUT = "E301"
    const val PERMISSION_DENIED = "E401"
    const val RUNNER_NOT_FOUND = "E404"
    const val CAPABILITY_NOT_SUPPORTED = "E405"
    const val STREAMING_NOT_SUPPORTED = "E406"
    const val MODEL_LOAD_FAILED = "E501"
    const val OOM_RISK = "E502"
}

override fun run(input: InferenceRequest): InferenceResult {
    return try {
        // Validate input
        if (!isLoaded()) {
            return InferenceResult.error(RunnerError(ErrorCodes.MODEL_NOT_LOADED, "Model not loaded"))
        }
        
        val inputText = input.inputs[InferenceRequest.INPUT_TEXT] as? String
            ?: return InferenceResult.error(RunnerError(ErrorCodes.INVALID_INPUT, "Missing text input"))
        
        // Your inference code
        val result = doInference(inputText)
        InferenceResult.success(mapOf("text" to result))
        
    } catch (e: OutOfMemoryError) {
        InferenceResult.error(RunnerError(ErrorCodes.OOM_RISK, "Out of memory during inference", true, e))
    } catch (e: SecurityException) {
        InferenceResult.error(RunnerError(ErrorCodes.PERMISSION_DENIED, "Permission denied: ${e.message}", false, e))
    } catch (e: Exception) {
        InferenceResult.error(RunnerError(ErrorCodes.RUNTIME_ERROR, "Inference failed: ${e.message}", true, e))
    }
}
```

#### Configuration
```kotlin
// ModelConfig structure (from actual codebase)
data class ModelConfig(
    val modelName: String,                          // Model identifier
    val modelPath: String? = null,                  // Primary model file path
    val files: Map<String, String> = emptyMap(),    // Related files (tokenizer, vocab, etc.)
    val parameters: Map<String, Any> = emptyMap(),  // Model parameters
    val metadata: Map<String, Any> = emptyMap()     // Additional metadata
) {
    companion object {
        // File keys
        const val FILE_MODEL = "model"
        const val FILE_TOKENIZER = "tokenizer"
        const val FILE_CONFIG = "config"
        const val FILE_VOCAB = "vocab"
        
        // Parameter keys  
        const val PARAM_MAX_CONTEXT_LENGTH = "max_context_length"
        const val PARAM_VOCAB_SIZE = "vocab_size"
        const val PARAM_EMBEDDING_DIM = "embedding_dim"
        const val PARAM_NUM_LAYERS = "num_layers"
    }
}

override fun load(config: ModelConfig): Boolean {
    return try {
        // Extract API key from parameters
        val apiKey = config.parameters["api_key"] as? String ?: "default-key"
        
        // Get model file path
        val modelPath = config.files[ModelConfig.FILE_MODEL] ?: config.modelPath
        
        // Get tokenizer if available
        val tokenizerPath = config.files[ModelConfig.FILE_TOKENIZER]
        
        // Extract model parameters
        val maxLength = config.parameters[ModelConfig.PARAM_MAX_CONTEXT_LENGTH] as? Int ?: 2048
        val vocabSize = config.parameters[ModelConfig.PARAM_VOCAB_SIZE] as? Int ?: 32000
        
        // Initialize with config
        yourAIClient.initialize(
            apiKey = apiKey,
            modelPath = modelPath,
            tokenizerPath = tokenizerPath,
            maxContextLength = maxLength,
            vocabSize = vocabSize
        )
        true
    } catch (e: Exception) {
        Log.e("MyRunner", "Failed to load model: ${config.modelName}", e)
        false
    }
}
```

That's it! Your custom runner will automatically appear in BreezeApp's AI engine and be used according to its priority level.

## Third-Party Integration Process

**IMPORTANT**: BreezeApp uses a service-based architecture. Client apps communicate with the BreezeApp-engine service via AIDL protocol. There are two distinct integration paths:

### For Client App Developers (Using AI Capabilities)

#### Step 1: Add EdgeAI Dependency
```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.mtkresearch:EdgeAI:EdgeAI-v0.1.4")
    // No additional dependencies needed - all AI processing happens in the service
}
```

#### Step 2: Initialize and Use EdgeAI SDK
```kotlin
// Initialize connection to BreezeApp-engine service
EdgeAI.initializeAndWait(context)

// Use AI capabilities via AIDL protocol
val chatFlow = EdgeAI.chat(ChatRequest(
    messages = listOf(ChatMessage("user", "Hello AI!"))
))

chatFlow.collect { response ->
    println("AI Response: ${response.choices.first().message?.content}")
}
```

### For AI Runner Contributors (Adding New AI Capabilities)

Runner integration happens **within the BreezeApp-engine service**, not in client apps. Anyone can contribute runners for any AI provider using their published SDKs.

#### Step 1: Contribute to BreezeApp-engine Repository
```
BreezeApp-engine/android/breeze-app-engine/src/main/java/
├── com/mtkresearch/breezeapp/engine/runner/
│   ├── openrouter/                      # OpenRouter API runners
│   ├── meta/                            # Meta ExecuTorch runners
│   ├── mediatek/                        # MediaTek NPU runners
│   ├── sherpa/                          # Sherpa ONNX runners
│   └── mock/                            # Mock/testing runners
```

#### Step 2: Implement Runner Using Provider's SDK
```kotlin
// Example: Anyone can implement OpenRouter using their public API
// File: com/mtkresearch/breezeapp/engine/runner/openrouter/OpenRouterLLMRunner.kt
package com.mtkresearch.breezeapp.engine.runner.openrouter

@AIRunner(
    capabilities = [CapabilityType.LLM],
    vendor = VendorType.OPENROUTER,        // The AI provider, not who implemented it
    priority = RunnerPriority.NORMAL,
    hardwareRequirements = [HardwareRequirement.CPU, HardwareRequirement.INTERNET]
)
class OpenRouterLLMRunner : BaseRunner {
    // Implementation using OpenRouter SDK - can be done by anyone
    private val client = OpenRouterClient(apiKey = "...")
}
```

#### Step 3: Service Integration
- The BreezeApp-engine service automatically discovers your runner via annotations
- No client-side changes needed - all apps instantly get access to new runners
- Service handles prioritization, fallbacks, and resource management

#### Step 4: Distribution Model
- **Contribution-based**: Submit PRs to BreezeApp-engine repository
- **Service deployment**: MediaTek distributes updated service to devices
- **Automatic availability**: All client apps automatically get new runners

## Auto-Discovery System

### Discovery Process
1. **Classpath Scanning**: Use ClassGraph to find all `@AIRunner` annotated classes
2. **Hardware Validation**: Check hardware requirements against device capabilities
3. **Registration**: Create runner definitions and cache instances
4. **Prioritization**: Sort runners by vendor priority and capability

### Discovery Implementation
```kotlin
class RunnerRegistry {
    fun initialize() {
        val runners = ClassGraph()
            .enableAnnotationInfo()
            .scan()
            .use { scanResult ->
                scanResult.getClassesWithAnnotation(AIRunner::class.java.name)
                    .mapNotNull { classInfo -> loadAndValidateRunner(classInfo) }
            }
        
        logger.i("Discovered ${runners.size} runners")
    }
    
    fun getBestRunnerFor(capability: CapabilityType): BaseRunner? {
        return discoveredRunners
            .filter { it.capabilities.contains(capability) }
            .sortedBy { getPriorityScore(capability, it.vendor, it.priority) }
            .firstOrNull()
            ?.let { createRunnerInstance(it) }
    }
}
```

## Integration Examples

### OpenAI API Integration
```kotlin
@AIRunner(
    capabilities = [CapabilityType.LLM],
    vendor = VendorType.OPENROUTER,
    hardwareRequirements = [HardwareRequirement.INTERNET]
)
class OpenAILLMRunner : BaseRunner {
    private val client = OpenAI()
    
    override fun run(input: InferenceRequest): InferenceResult {
        val completion = client.chat.completions.create {
            model = "gpt-4"
            messages = listOf(ChatMessage("user", input.inputs["text"] as String))
        }
        return InferenceResult.success(mapOf("text" to completion.choices.first().message.content))
    }
}
```

### Hardware Acceleration Integration  
```kotlin
@AIRunner(
    capabilities = [CapabilityType.LLM],
    vendor = VendorType.OPENROUTER,
    priority = RunnerPriority.HIGH,
    hardwareRequirements = [HardwareRequirement.HIGH_MEMORY]
)
class QualcommLLMRunner : BaseRunner {
    private val qnnRuntime = QNNRuntime()
    
    override fun load(config: ModelConfig): Boolean {
        return qnnRuntime.initialize() && qnnRuntime.loadModel(config.modelPath)
    }
    
    override fun run(input: InferenceRequest): InferenceResult {
        val tokens = qnnRuntime.tokenize(input.inputs["text"] as String)
        val output = qnnRuntime.execute(tokens)
        val response = qnnRuntime.detokenize(output)
        
        return InferenceResult.success(mapOf("text" to response))
    }
    
    companion object {
        @JvmStatic
        fun isSupported(): Boolean = QNNRuntime.isNPUAvailable()
    }
}
```

## Benefits

### For Client App Developers
- **Simple Integration**: One EdgeAI dependency, full AI capabilities
- **Zero Configuration**: No runner setup or management needed
- **Automatic Updates**: New runners available instantly via service updates
- **Consistent API**: Unified interface for all AI capabilities
- **Performance**: Service handles optimization and resource management

### For Runner Contributors
- **Service-Level Integration**: Runners benefit all apps simultaneously
- **Clear Standards**: Annotation-based system with transparent priorities
- **Automatic Discovery**: Service finds and integrates runners automatically
- **Hardware Optimization**: Service handles device-specific acceleration

### For End Users  
- **Optimal Performance**: Service automatically selects best available runners
- **Graceful Fallbacks**: Seamless degradation from hardware → local → cloud
- **Battery Efficiency**: Service-level resource management and sharing
- **Consistent Experience**: All apps get same high-quality AI capabilities

### For Platform
- **Centralized Quality Control**: Service validates all runners before use
- **Resource Sharing**: Multiple apps share same AI service efficiently
- **Easy Distribution**: Service updates deploy new runners system-wide
- **Security**: Isolated service prevents malicious runner access to app data

## Best Practices

### Runner Implementation
- Implement hardware detection in companion `isSupported()` method
- Use appropriate priority level (HIGH for premium/hardware, NORMAL for standard, LOW for fallback)
- Specify minimal required hardware constraints
- Handle errors gracefully with meaningful error messages
- Clean up resources properly in `unload()` method

### Runner Contribution Guidelines
- Use clear runner naming convention: `{Vendor}{Capability}Runner`
- Include comprehensive hardware detection logic
- Document required permissions and native dependencies
- Test on multiple device configurations
- Follow MediaTek's contribution and review process

### Security Considerations
- Validate all input parameters
- Use secure storage for API keys and sensitive data
- Implement proper error handling to prevent information leakage
- Follow Android security best practices
- Consider resource limitations and prevent abuse

## Performance Characteristics

### Discovery & Runtime Performance
- **Runner Discovery Time**: <100ms for complete classpath scanning
- **Runner Switching Time**: <50ms between active runners
- **Memory Overhead**: ~2MB per active runner instance
- **Service Cold Start**: <200ms from bind to ready
- **AIDL Communication**: <10ms latency for typical requests

### Resource Management
- **Concurrent Runners**: Up to 3 active runners (LLM, ASR, TTS)
- **Memory Monitoring**: Automatic unloading when memory pressure detected
- **Model Loading**: Lazy loading with automatic resource cleanup
- **Service Lifecycle**: Persistent service with client connection tracking

## Debugging & Troubleshooting

### Common Issues & Solutions

#### 1. Runner Not Discovered
```bash
# Check discovery logs
adb logcat | grep "RunnerRegistry"

# Verify annotation is correct
@AIRunner(capabilities = [CapabilityType.LLM], vendor = VendorType.OPENROUTER)
```

#### 2. Service Connection Failed
```kotlin
// Check service readiness
if (!EdgeAI.isReady()) {
    EdgeAI.initializeAndWait(context)
}

// Verify service is installed
adb shell pm list packages | grep com.mtkresearch.breezeapp.engine
```

#### 3. Hardware Requirements Not Met
```kotlin
// Validate hardware at runtime
companion object {
    fun validateRequirements(): List<String> {
        val missing = mutableListOf<String>()
        if (!hasNPU()) missing.add("MTK_NPU")
        if (!hasEnoughMemory()) missing.add("HIGH_MEMORY")
        return missing
    }
}
```

### Debug Commands
```bash
# Monitor service logs
adb logcat -s BreezeAppEngine:* EdgeAI:* RunnerRegistry:*

# Check hardware capabilities
adb shell getprop ro.vendor.mtk_nn_support

# Monitor memory usage
adb shell dumpsys meminfo com.mtkresearch.breezeapp.engine
```

## Version Compatibility Matrix

| EdgeAI SDK Version | Min Engine Version | API Level | Key Features |
|-------------------|-------------------|-----------|-------------|
| v0.1.4            | engine-v1.2.0     | 1         | Basic LLM, ASR, TTS |
| v0.2.x (planned)  | engine-v1.3.0     | 2         | VLM support, Streaming |
| v0.3.x (planned)  | engine-v2.0.0     | 3         | Multi-modal, RAG |

## Service Architecture Overview

```
┌─────────────────┐    AIDL     ┌──────────────────────┐
│   Client App    │◄──────────► │ BreezeApp-engine     │
│                 │             │ Service              │
│ EdgeAI SDK      │             │                      │
│ - ChatRequest   │             │ ┌─────────────────┐  │
│ - ASRRequest    │             │ │ RunnerRegistry  │  │
│ - TTSRequest    │             │ │ ┌─────────────┐ │  │
└─────────────────┘             │ │ │MediaTek NPU │ │  │
                                │ │ │Sherpa ONNX  │ │  │ 
┌─────────────────┐             │ │ │OpenRouter   │ │  │
│   Client App    │◄─────────┐  │ │ │Meta ExecuTorch│ │  │
│                 │          │  │ │ │Mock Runners │ │  │
│ EdgeAI SDK      │          │  │ │ └─────────────┘ │  │
                             │  │ │ └─────────────┘ │  │
        Multiple clients ────┘  │ └─────────────────┘  │
        share same service      │ Resource Management  │
                                │ Memory Monitoring    │
                                │ Hardware Detection   │
                                └──────────────────────┘
```

This architecture provides a robust, extensible foundation for third-party AI integration while maintaining simplicity and developer experience.