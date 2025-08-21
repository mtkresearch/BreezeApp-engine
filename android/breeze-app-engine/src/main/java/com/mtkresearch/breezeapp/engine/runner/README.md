# BreezeApp Runner Development Guide

A simple guide for creating custom AI runners in BreezeApp-engine.

## Quick Start: Create Your Runner in 5 Steps

### Step 1: Choose Your Package Location
```
BreezeApp-engine/android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/runner/
├── mtk/           # MediaTek NPU runners
├── sherpa/        # Sherpa ONNX runners  
├── mock/          # Mock/testing runners
└── [your-vendor]/ # Your runner directory
```

### Step 2: Implement BaseRunner Interface
```kotlin
package com.mtkresearch.breezeapp.engine.runner.yourvendor

import com.mtkresearch.breezeapp.engine.annotation.*
import com.mtkresearch.breezeapp.engine.runner.core.*
import com.mtkresearch.breezeapp.engine.model.*

@AIRunner(
    vendor = VendorType.UNKNOWN,                    // Change to your vendor if available
    priority = RunnerPriority.NORMAL,              // HIGH/NORMAL/LOW
    capabilities = [CapabilityType.LLM]            // LLM/ASR/TTS/VLM/GUARDIAN
)
class YourCustomRunner : BaseRunner {
    
    private val isLoaded = AtomicBoolean(false)
    
    // Implement required methods
    override fun load(): Boolean {
        // Load default model configuration
        val defaultConfig = ModelConfig(
            modelName = "YourModel",
            modelPath = ""
        )
        return load(defaultConfig)
    }
    
    override fun load(config: ModelConfig): Boolean {
        // Initialize your AI model/API here
        // Example: yourAIClient.initialize(config.modelPath)
        isLoaded.set(true)
        return true
    }
    
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        if (!isLoaded.get()) {
            return InferenceResult.error(RunnerError.modelNotLoaded())
        }
        
        // Process the input
        val inputText = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
        
        // Your AI inference logic here
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
        // Cleanup resources
        isLoaded.set(false)
    }
    
    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.LLM)
    override fun isLoaded(): Boolean = isLoaded.get()
    override fun getRunnerInfo(): RunnerInfo = RunnerInfo(
        name = "YourCustomRunner",
        version = "1.0.0",
        capabilities = getCapabilities(),
        description = "Your custom AI runner"
    )
    
    private fun processWithYourAI(input: String): String {
        // Replace with your actual AI processing
        return "Processed: $input"
    }
    
    // Hardware support check
    companion object : BaseRunnerCompanion {
        @JvmStatic
        override fun isSupported(): Boolean {
            // Check if your AI library/hardware is available
            return true
        }
    }
}
```

### Step 3: Add Streaming Support (Optional)
If your runner supports real-time streaming (ASR, live inference):

```kotlin
class YourStreamingRunner : BaseRunner, FlowStreamingRunner {
    
    override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = callbackFlow {
        // Emit partial results as they become available
        for (i in 1..5) {
            val partialResult = "Partial result $i"
            trySend(InferenceResult.textOutput(
                text = partialResult,
                partial = true  // Mark as partial
            ))
            delay(100)
        }
        
        // Emit final result
        trySend(InferenceResult.textOutput(
            text = "Final result",
            partial = false
        ))
        close()
    }
}
```

### Step 4: Handle Errors Properly
```kotlin
override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
    return try {
        if (!isLoaded()) {
            return InferenceResult.error(RunnerError.modelNotLoaded())
        }
        
        val inputText = input.inputs[InferenceRequest.INPUT_TEXT] as? String
            ?: return InferenceResult.error(RunnerError.invalidInput("Missing text input"))
        
        // Your processing...
        val result = processWithYourAI(inputText)
        InferenceResult.textOutput(text = result)
        
    } catch (e: Exception) {
        InferenceResult.error(RunnerError.runtimeError("Processing failed: ${e.message}", e))
    }
}
```

### Step 5: Test Your Runner
Your runner is automatically discovered by the engine. Test it through the BreezeApp:

```kotlin
// The engine will automatically find and use your runner based on:
// 1. Capability type (LLM/ASR/TTS/VLM)
// 2. Vendor priority 
// 3. Hardware availability
```

## Real Examples from Codebase

### Example 1: MTK NPU Runner
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
            modelPath = "" // Empty - uses MTKUtils.resolveModelPath()
        )
        return load(defaultConfig)
    }
    
    // Smart model path resolution
    override fun load(config: ModelConfig): Boolean {
        val modelPath = if (context != null) {
            MTKUtils.resolveModelPath(context, config.modelPath)
        } else {
            config.modelPath ?: return false
        }
        // ... MTK initialization
    }
}
```

### Example 2: Mock Runner (Perfect Template)
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
        val response = "Mock AI response to: $inputText"
        
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

## Key Architecture Points

### 1. Dual Load Methods
Every runner must implement both:
- `load(): Boolean` - Loads default model
- `load(config: ModelConfig): Boolean` - Loads specific configuration

### 2. Automatic Registration
- No manual registration needed
- Engine automatically discovers `@AIRunner` annotated classes
- Successfully loaded runners are tracked in `activeRunners` collection

### 3. Priority System
- `HIGH`: Hardware-accelerated, premium runners (MTK NPU)
- `NORMAL`: Standard runners (CPU-based)
- `LOW`: Fallback/mock runners

### 4. Memory Management
- Engine automatically unloads other runners when memory is needed
- Runners are properly removed from active collection after unloading
- Always implement proper cleanup in `unload()`

## Common Patterns

### Hardware Detection
```kotlin
companion object : BaseRunnerCompanion {
    @JvmStatic
    override fun isSupported(): Boolean {
        return try {
            // Check for native libraries
            System.loadLibrary("your-ai-lib")
            
            // Check device capabilities  
            val hasEnoughMemory = Runtime.getRuntime().maxMemory() > 4_000_000_000L
            
            // Check API availability
            YourAILibrary.isAvailable()
            
            hasEnoughMemory
        } catch (e: Exception) {
            false
        }
    }
}
```

### Input/Output Handling
```kotlin
// Common input keys
val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String
val audio = input.inputs[InferenceRequest.INPUT_AUDIO] as? ByteArray
val image = input.inputs[InferenceRequest.INPUT_IMAGE] as? ByteArray

// Common parameters  
val temperature = (input.params[InferenceRequest.PARAM_TEMPERATURE] as? Number)?.toFloat() ?: 0.7f
val maxTokens = (input.params[InferenceRequest.PARAM_MAX_TOKENS] as? Number)?.toInt() ?: 2048

// Return results
return InferenceResult.textOutput(
    text = response,
    metadata = mapOf(
        InferenceResult.META_MODEL_NAME to "your-model",
        InferenceResult.META_PROCESSING_TIME_MS to processingTime
    )
)
```

## Current Capabilities

| CapabilityType | Description | Input | Output |
|----------------|-------------|-------|--------|
| `LLM` | Large Language Model | Text | Text |
| `ASR` | Speech Recognition | Audio | Text |
| `TTS` | Text-to-Speech | Text | Audio |
| `VLM` | Vision Language Model | Image + Text | Text |
| `GUARDIAN` | Content Safety | Text | Safety Analysis |

## That's It!

Your runner will be automatically discovered and integrated into the BreezeApp engine. The system handles:
- ✅ Automatic discovery via annotations  
- ✅ Priority-based selection
- ✅ Memory management and cleanup
- ✅ Error handling and fallbacks
- ✅ Hardware detection and validation

Focus on implementing your AI logic - the engine handles everything else.