# 🚀 How to Add a New Runner - Complete SOP

## 📋 Overview

This guide provides a step-by-step Standard Operating Procedure (SOP) for adding new AI runners to the BreezeApp Engine. The enhanced runner management system makes it easy to add new vendors (OpenAI, Google, HuggingFace, etc.) with minimal code changes.

## 🎯 Prerequisites

- Understanding of Kotlin/Android development
- Familiarity with the BreezeApp Engine architecture
- Access to the vendor's AI API/SDK (if applicable)

---

## 📝 Step-by-Step SOP

### **Step 1: Choose Your Vendor Folder** ⏱️ 2 minutes

Create a new vendor-specific folder under `data/runner/`:

```bash
# For OpenAI runners
mkdir -p android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/data/runner/openai

# For Google runners  
mkdir -p android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/data/runner/google

# For HuggingFace runners
mkdir -p android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/data/runner/huggingface

# For custom vendor
mkdir -p android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/data/runner/[vendor_name]
```

### **Step 2: Create Your Runner Class** ⏱️ 15-30 minutes

Create a new runner class implementing the required interfaces:

```kotlin
// Example: OpenAILLMRunner.kt
package com.mtkresearch.breezeapp.engine.data.runner.openai

import android.util.Log
import com.mtkresearch.breezeapp.engine.data.runner.core.BaseRunner
import com.mtkresearch.breezeapp.engine.data.runner.core.FlowStreamingRunner
import com.mtkresearch.breezeapp.engine.data.runner.core.RunnerInfo
import com.mtkresearch.breezeapp.engine.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.toList
import java.util.concurrent.atomic.AtomicBoolean

class OpenAILLMRunner(
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1"
) : BaseRunner, FlowStreamingRunner {
    
    companion object {
        private const val TAG = "OpenAILLMRunner"
    }
    
    private val isLoaded = AtomicBoolean(false)
    
    override fun load(modelId: String, settings: EngineSettings): Boolean {
        return try {
            Log.d(TAG, "Loading OpenAI LLM Runner with model: $modelId")
            // Initialize OpenAI client here
            // Get runner-specific parameters from settings
            val runnerParams = settings.getRunnerParameters("OpenAILLMRunner")
            val apiKey = runnerParams["api_key"] as? String ?: ""
            // Use modelId to determine which model to load
            isLoaded.set(true)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load OpenAI LLM Runner", e)
            false
        }
    }
    
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        // Always use streaming mode internally, regardless of the stream parameter
        Log.d(TAG, "OpenAI runner called with stream=$stream, but forcing streaming mode internally")
        
        return try {
            // Collect all results from the flow and combine them
            val results = runAsFlow(input).toList()
            
            // Find the final result (non-partial) or combine all partial results
            val finalResult = results.find { !it.partial } ?: combinePartialResults(results)
            finalResult
        } catch (e: Exception) {
            Log.e(TAG, "Error processing non-streaming request", e)
            InferenceResult.error(RunnerError.runtimeError("Inference failed: ${e.message}", e))
        }
    }
    
    override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = callbackFlow {
        if (!isLoaded.get()) {
            trySend(InferenceResult.error(RunnerError.modelNotLoaded()))
            close()
            return@callbackFlow
        }

        val prompt = input.inputs[InferenceRequest.INPUT_TEXT] as? String
            ?: run {
                trySend(InferenceResult.error(RunnerError.invalidInput("Missing text input")))
                close()
                return@callbackFlow
            }

        try {
            // Call OpenAI streaming API here
            val response = callOpenAIStreamingAPI(prompt) { token ->
                // Send partial result for each token
                trySend(
                    InferenceResult.textOutput(
                        text = token,
                        metadata = mapOf("partial" to true),
                        partial = true
                    )
                )
            }
            
            // Send final result
            trySend(
                InferenceResult.textOutput(
                    text = response,
                    metadata = mapOf("partial" to false),
                    partial = false
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in OpenAI streaming API call", e)
            trySend(InferenceResult.error(RunnerError.runtimeError("API Error: ${e.message}", e)))
        } finally {
            close()
        }

        awaitClose {
            // Cleanup resources when flow is closed
            Log.d(TAG, "Streaming flow closed, cleaning up resources")
        }
    }
    
    override fun unload() {
        Log.d(TAG, "Unloading OpenAI LLM Runner")
        isLoaded.set(false)
    }
    
    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.LLM)
    
    override fun isLoaded(): Boolean = isLoaded.get()
    
    override fun getRunnerInfo(): RunnerInfo = RunnerInfo(
        name = "OpenAILLMRunner",
        version = "1.0.0",
        capabilities = getCapabilities(),
        description = "OpenAI GPT-based LLM runner",
        isMock = false
    )
    
    // Enhanced Runtime Settings Support
    override fun getParameterSchema(): List<ParameterSchema> {
        return listOf(
            ParameterSchema(
                name = "api_key",
                displayName = "API Key",
                description = "Your OpenAI API key for authentication",
                type = ParameterType.StringType(
                    minLength = 10,
                    isSensitive = true
                ),
                defaultValue = "",
                isRequired = true,
                category = "Authentication"
            ),
            ParameterSchema(
                name = "model",
                displayName = "Model",
                description = "OpenAI model to use for text generation",
                type = ParameterType.SelectionType(
                    options = listOf(
                        SelectionOption("gpt-3.5-turbo", "GPT-3.5 Turbo", "Fast and efficient for most tasks"),
                        SelectionOption("gpt-4", "GPT-4", "Most capable model, higher cost"),
                        SelectionOption("gpt-4-turbo", "GPT-4 Turbo", "Latest GPT-4 with improved performance")
                    )
                ),
                defaultValue = "gpt-3.5-turbo",
                isRequired = false,
                category = "Model Configuration"
            ),
            ParameterSchema(
                name = "temperature",
                displayName = "Temperature",
                description = "Controls randomness in text generation. Lower values (0.1) make output more focused, higher values (1.0) make it more creative.",
                type = ParameterType.FloatType(
                    minValue = 0.0,
                    maxValue = 2.0,
                    step = 0.1,
                    precision = 1
                ),
                defaultValue = 0.7f,
                isRequired = false,
                category = "Generation Parameters"
            )
        )
    }
    
    override fun validateParameters(parameters: Map<String, Any>): ValidationResult {
        // Validate API key format
        val apiKey = parameters["api_key"] as? String
        if (apiKey.isNullOrBlank()) {
            return ValidationResult.invalid("API key is required")
        }
        if (!apiKey.startsWith("sk-")) {
            return ValidationResult.invalid("Invalid API key format")
        }

        // Validate temperature
        val temperature = parameters["temperature"] as? Number
        temperature?.let { temp ->
            val tempValue = temp.toFloat()
            if (tempValue < 0.0f || tempValue > 2.0f) {
                return ValidationResult.invalid("Temperature must be between 0.0 and 2.0")
            }
        }

        return ValidationResult.valid()
    }
    
    private fun callOpenAIStreamingAPI(prompt: String, onToken: (String) -> Unit): String {
        // Implement actual OpenAI streaming API call here
        // This is a placeholder that simulates streaming
        val response = "OpenAI response for: $prompt"
        response.forEach { char ->
            onToken(char.toString())
            Thread.sleep(10) // Simulate delay
        }
        return response
    }
    
    /**
     * Combines partial results into a single result
     */
    private fun combinePartialResults(partialResults: List<InferenceResult>): InferenceResult {
        if (partialResults.isEmpty()) {
            return InferenceResult.error(RunnerError.runtimeError("No results received from model"))
        }
        
        // Combine all text outputs
        val combinedText = partialResults
            .filter { it.error == null }
            .joinToString("") { it.outputs[InferenceResult.OUTPUT_TEXT] as? String ?: "" }
        
        // Get metadata from the last result if available
        val lastResult = partialResults.lastOrNull { it.error == null }
        val metadata = lastResult?.metadata?.toMutableMap() ?: mutableMapOf()
        metadata["partial"] = false
        
        return InferenceResult.textOutput(
            text = combinedText,
            metadata = metadata,
            partial = false
        )
    }
}
```

### **Step 3: Update RunnerFactory** ⏱️ 5 minutes

Add your vendor to the `RunnerFactory.kt`:

```kotlin
// In RunnerFactory.kt, add to the when statement in createRunner()
when {
    definition.className.contains(".mock.") -> {
        createMockRunner(definition)
    }
    definition.className.contains(".mtk.") -> {
        createMTKRunner(definition)
    }
    definition.className.contains(".openai.") -> {
        createOpenAIRunner(definition)  // Add this line
    }
    definition.className.contains(".google.") -> {
        createGoogleRunner(definition)  // Add this line
    }
    // Add more vendors as needed
    else -> {
        createGenericRunner(definition)
    }
}
```

Then implement the vendor-specific creation method:

```kotlin
// Add this method to RunnerFactory.kt
private fun createOpenAIRunner(definition: RunnerDefinition): BaseRunner? {
    logger.d(TAG, "Creating OpenAI runner: ${definition.name}")
    
    return try {
        when (definition.className) {
            "com.mtkresearch.breezeapp.engine.data.runner.openai.OpenAILLMRunner" -> {
                val apiKey = getOpenAIApiKey() // Implement this method
                OpenAILLMRunner(apiKey)
            }
            // Add more OpenAI runners here
            else -> {
                logger.w(TAG, "Unknown OpenAI runner type: ${definition.className}")
                null
            }
        }
    } catch (e: Exception) {
        logger.e(TAG, "Failed to create OpenAI runner: ${definition.name}", e)
        null
    }
}

private fun getOpenAIApiKey(): String {
    // Implement API key retrieval (from SharedPreferences, BuildConfig, etc.)
    return BuildConfig.OPENAI_API_KEY // Example
}
```

### **Step 4: Update Configuration** ⏱️ 3 minutes

Add your runner to `runner_config.json`:

```json
{
  "version": "2.0",
  "defaultStrategy": "MOCK_FIRST",
  "globalSettings": {
    "enableHardwareDetection": true,
    "fallbackToMock": true,
    "maxInitRetries": 3,
    "defaultTimeoutMs": 30000
  },
  "capabilities": {
    "LLM": {
      "defaultRunner": "breeze_llm_mock_v1",
      "runners": {
        "openai_gpt35_v1": {
          "class": "com.mtkresearch.breezeapp.engine.data.runner.openai.OpenAILLMRunner",
          "priority": 50,
          "type": "CLOUD",
          "enabled": true,
          "requirements": ["INTERNET_CONNECTION"],
          "modelId": "gpt-3.5-turbo"
        },
        "breeze_llm_mock_v1": {
          "class": "com.mtkresearch.breezeapp.engine.data.runner.mock.MockLLMRunner",
          "priority": 100,
          "type": "MOCK",
          "enabled": true,
          "alwaysAvailable": true
        }
      }
    }
  }
}
```

### **Step 5: Update Hardware Requirements (Optional)** ⏱️ 2 minutes

If your runner has specific requirements, update the `RunnerSelectionStrategy.kt`:

```kotlin
// In RunnerSelectionStrategy.kt, update getHardwareRequirements()
fun RunnerDefinition.getHardwareRequirements(): List<String> {
    return requirements ?: when {
        className.contains(".mtk.") -> listOf("MTK_NPU")
        className.contains(".openai.") -> listOf("INTERNET_CONNECTION")
        className.contains(".google.") -> listOf("INTERNET_CONNECTION", "GOOGLE_PLAY_SERVICES")
        className.contains(".huggingface.") -> listOf("INTERNET_CONNECTION")
        else -> emptyList()
    }
}
```

### **Step 6: Test Your Runner** ⏱️ 10 minutes

Create a test file for your runner:

```kotlin
// Example: OpenAILLMRunnerTest.kt
package com.mtkresearch.breezeapp.engine.runner.openai

import com.mtkresearch.breezeapp.engine.data.runner.openai.OpenAILLMRunner
import com.mtkresearch.breezeapp.engine.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OpenAILLMRunnerTest {
    
    private lateinit var runner: OpenAILLMRunner
    
    @Before
    fun setup() {
        runner = OpenAILLMRunner("test-api-key")
    }
    
    @Test
    fun testRunnerInfo() {
        val info = runner.getRunnerInfo()
        assertEquals("OpenAILLMRunner", info.name)
        assertEquals(listOf(CapabilityType.LLM), info.capabilities)
        assertFalse(info.isMock)
    }
    
    @Test
    fun testCapabilities() {
        val capabilities = runner.getCapabilities()
        assertTrue(capabilities.contains(CapabilityType.LLM))
    }
    
    // Add more tests as needed
}
```

### **Step 7: Build and Verify** ⏱️ 5 minutes

```bash
# Compile to check for errors
cd android && ./gradlew :breeze-app-engine:compileDebugKotlin

# Run tests
cd android && ./gradlew :breeze-app-engine:test

# Build the full project
cd android && ./gradlew :breeze-app-engine:build
```

---

## 🎯 Quick Reference Templates

### **Minimal Runner Template**

```kotlin
package com.mtkresearch.breezeapp.engine.data.runner.[vendor]

import com.mtkresearch.breezeapp.engine.data.runner.core.BaseRunner
import com.mtkresearch.breezeapp.engine.data.runner.core.FlowStreamingRunner
import com.mtkresearch.breezeapp.engine.data.runner.core.RunnerInfo
import com.mtkresearch.breezeapp.engine.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.toList

class [Vendor][Capability]Runner : BaseRunner, FlowStreamingRunner {
    
    override fun load(modelId: String, settings: EngineSettings): Boolean = true
    
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        // For runners implementing FlowStreamingRunner, 
        // always use streaming mode internally regardless of stream parameter
        return try {
            val results = runAsFlow(input).toList()
            val finalResult = results.find { !it.partial } ?: combinePartialResults(results)
            finalResult
        } catch (e: Exception) {
            InferenceResult.error(RunnerError.runtimeError("Inference failed: ${e.message}", e))
        }
    }
    
    override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = callbackFlow {
        // Implement your AI logic here with proper streaming support
        // Remember to use trySend() for results and close() when done
        // Use awaitClose for resource cleanup
        awaitClose {
            // Cleanup resources
        }
    }
    
    override fun unload() {}
    
    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.[CAPABILITY])
    
    override fun isLoaded(): Boolean = true
    
    override fun getRunnerInfo(): RunnerInfo = RunnerInfo(
        name = "[Vendor][Capability]Runner",
        version = "1.0.0",
        capabilities = getCapabilities(),
        description = "[Description]",
        isMock = false
    )
    
    // Enhanced Runtime Settings Support
    override fun getParameterSchema(): List<ParameterSchema> {
        return listOf(
            // Define your runner's parameters here
            // See the OpenAI example for reference
        )
    }
    
    override fun validateParameters(parameters: Map<String, Any>): ValidationResult {
        // Implement parameter validation logic here
        return ValidationResult.valid()
    }
    
    private fun combinePartialResults(partialResults: List<InferenceResult>): InferenceResult {
        // Combine partial results into a single final result
        val combinedText = partialResults
            .filter { it.error == null }
            .joinToString("") { it.outputs[InferenceResult.OUTPUT_TEXT] as? String ?: "" }
        
        val lastResult = partialResults.lastOrNull { it.error == null }
        val metadata = lastResult?.metadata?.toMutableMap() ?: mutableMapOf()
        metadata["partial"] = false
        
        return InferenceResult.textOutput(
            text = combinedText,
            metadata = metadata,
            partial = false
        )
    }
}
```

### **Configuration Template**

```json
"[runner_name]": {
  "class": "com.mtkresearch.breezeapp.engine.data.runner.[vendor].[RunnerClass]",
  "priority": [priority_number],
  "type": "[MOCK|HARDWARE|CLOUD|LOCAL]",
  "enabled": true,
  "requirements": ["[REQUIREMENT1]", "[REQUIREMENT2]"],
  "modelId": "[model_identifier]"
}
```

---

## ✅ Checklist

Before submitting your new runner:

- [ ] **Folder Structure**: Created vendor-specific folder
- [ ] **Runner Class**: Implements BaseRunner (and FlowStreamingRunner if needed)
- [ ] **Factory Update**: Added vendor case to RunnerFactory
- [ ] **Configuration**: Updated runner_config.json
- [ ] **Requirements**: Added hardware requirements if needed
- [ ] **Tests**: Created unit tests for the runner
- [ ] **Build**: Compilation successful
- [ ] **Documentation**: Updated any relevant docs

---

## 🚀 Advanced Features

### **Enhanced Runtime Settings Support**

With the enhanced runtime settings system, runners can now provide self-describing parameter schemas that automatically generate UI controls in the Engine Settings Activity. This eliminates the need for clients to duplicate parameter configuration logic.

Key features of the enhanced system:
1. **Self-Describing Runners**: Each runner implements `getParameterSchema()` to define its parameters
2. **Dynamic UI Generation**: The Engine automatically creates appropriate UI controls based on parameter types
3. **Parameter Validation**: Built-in validation with real-time feedback
4. **Type Safety**: Automatic parameter validation and constraint enforcement

#### Parameter Schema Implementation

Implement the `getParameterSchema()` method to define your runner's parameters:

```kotlin
override fun getParameterSchema(): List<ParameterSchema> {
    return listOf(
        // String parameter with sensitive data masking
        ParameterSchema(
            name = "api_key",
            displayName = "API Key",
            description = "Your API key for authentication",
            type = ParameterType.StringType(
                minLength = 10,
                isSensitive = true  // Masks input in UI
            ),
            defaultValue = "",
            isRequired = true,
            category = "Authentication"
        ),
        
        // Selection parameter with predefined options
        ParameterSchema(
            name = "model",
            displayName = "Model",
            description = "Model to use for inference",
            type = ParameterType.SelectionType(
                options = listOf(
                    SelectionOption("model-a", "Model A", "Description for Model A"),
                    SelectionOption("model-b", "Model B", "Description for Model B")
                )
            ),
            defaultValue = "model-a",
            isRequired = false,
            category = "Model Configuration"
        ), 
        // Float parameter with precision control
        ParameterSchema(
            name = "temperature",
            displayName = "Temperature",
            description = "Controls randomness in generation",
            type = ParameterType.FloatType(
                minValue = 0.0,
                maxValue = 2.0,
                step = 0.1,
                precision = 1
            ),
            defaultValue = 0.7f,
            isRequired = false,
            category = "Generation Parameters"
        )
    )
}
```

#### Parameter Validation

Implement the `validateParameters()` method to provide real-time validation:

```kotlin
override fun validateParameters(parameters: Map<String, Any>): ValidationResult {
    // Validate API key format
    val apiKey = parameters["api_key"] as? String
    if (apiKey.isNullOrBlank()) {
        return ValidationResult.invalid("API key is required")
    }
    if (!apiKey.startsWith("sk-")) {
        return ValidationResult.invalid("Invalid API key format")
    }

    // Cross-parameter validation
    val temperature = parameters["temperature"] as? Number
    
    temperature?.let { temp ->
        val tempValue = temp.toFloat()
        if (tempValue < 0.0f || tempValue > 2.0f) {
            return ValidationResult.invalid("Temperature must be between 0.0 and 2.0")
        }
        
        // High temperature with high max_tokens warning
        if (tempValue > 1.5f) {
            maxTokens?.let { tokens ->
                if (tokens.toInt() > 2048) {
                    return ValidationResult.warning("High temperature with high token count may produce incoherent results")
                }
            }
        }
    }

    return ValidationResult.valid()
}
```

### **Streaming Support**

Implement `FlowStreamingRunner` for real-time responses. When implementing streaming support, consider these key principles:

1. **Always implement both methods**: If your runner implements `FlowStreamingRunner`, you should implement both `run()` and `runAsFlow()` methods properly.

2. **Handle the stream parameter correctly**: The `run()` method receives a `stream` parameter that indicates whether the client wants streaming responses. Your implementation should respect this parameter.

3. **Use proper result marking**: Always mark partial results with `partial = true` and final results with `partial = false`.

4. **Implement proper resource cleanup**: Use `awaitClose` in `callbackFlow` to ensure resources are cleaned up.

Example of a robust streaming implementation:

```kotlin
override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
    return try {
        if (stream) {
            // For streaming requests, we could collect the flow or return an error
            // indicating that streaming should be handled at a higher level
            InferenceResult.error(RunnerError.runtimeError("Use runAsFlow for streaming requests"))
        } else {
            // For non-streaming, collect all results and combine them
            val results = runAsFlow(input).toList()
            val finalResult = results.find { !it.partial } ?: combinePartialResults(results)
            finalResult
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error processing request", e)
        InferenceResult.error(RunnerError.runtimeError("Inference failed: ${e.message}", e))
    }
}

override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = callbackFlow {
    // Validate input and setup
    if (!isLoaded.get()) {
        trySend(InferenceResult.error(RunnerError.modelNotLoaded()))
        close()
        return@callbackFlow
    }

    val prompt = input.inputs[InferenceRequest.INPUT_TEXT] as? String
        ?: run {
            trySend(InferenceResult.error(RunnerError.invalidInput("Missing text input")))
            close()
            return@callbackFlow
        }

    // Process the request with streaming callback
    val responseBuffer = StringBuilder()
    
    // Example callback for an external API
    val callback = object : ExternalAPICallback {
        override fun onToken(token: String) {
            responseBuffer.append(token)
            // Send partial result
            trySend(
                InferenceResult.textOutput(
                    text = token,  // Send just the new token
                    metadata = mapOf("partial" to true),
                    partial = true
                )
            )
        }
        
        override fun onComplete() {
            // Send final result
            trySend(
                InferenceResult.textOutput(
                    text = responseBuffer.toString(),  // Send complete response
                    metadata = mapOf("partial" to false),
                    partial = false
                )
            )
            close()
        }
        
        override fun onError(error: Exception) {
            trySend(InferenceResult.error(RunnerError.runtimeError("API Error: ${error.message}", error)))
            close()
        }
    }
    
    // Start the external API call with callback
    externalAPI.generate(prompt, callback)
    
    awaitClose {
        // Cleanup resources when flow is closed
        externalAPI.cancelCurrentRequest()
    }
}
```

### **Error Handling in Streaming**

Proper error handling in streaming runners is crucial for a good user experience:

1. **Immediate Errors**: Errors that occur before streaming starts should be returned directly or emitted as the first item in the flow.

2. **Streaming Errors**: Errors that occur during streaming should be emitted through the flow and then the flow should be closed.

3. **Resource Cleanup**: Always ensure resources are cleaned up using `awaitClose`.

Example:

```kotlin
override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = callbackFlow {
    try {
        // Validate and setup
        if (!isLoaded.get()) {
            throw RunnerError.modelNotLoaded()
        }
        
        // Process request...
        
    } catch (e: RunnerError) {
        // Send runner-specific errors
        trySend(InferenceResult.error(e))
        close()
        return@callbackFlow
    } catch (e: Exception) {
        // Send generic errors
        trySend(InferenceResult.error(RunnerError.runtimeError("Unexpected error: ${e.message}", e)))
        close()
        return@callbackFlow
    }
    
    awaitClose {
        // Cleanup resources
        cleanupResources()
    }
}
```

### **Resource Management**

Proper resource management is essential for long-running services:

1. **Native Resources**: Always clean up native resources in the `unload()` method.

2. **Streaming Resources**: Use `awaitClose` to clean up resources when streaming completes or is cancelled.

3. **Thread Safety**: Use atomic variables or locks when managing shared state.

Example:

```kotlin
class MyRunner : BaseRunner, FlowStreamingRunner {
    private val isLoaded = AtomicBoolean(false)
    private var nativeHandle: Long = 0L
    private val lock = ReentrantReadWriteLock()
    
    override fun load(config: ModelConfig): Boolean {
        return lock.write {
            try {
                // Load model and get native handle
                nativeHandle = loadNativeModel(config)
                isLoaded.set(true)
                true
            } catch (e: Exception) {
                isLoaded.set(false)
                false
            }
        }
    }
    
    override fun unload() {
        lock.write {
            if (isLoaded.get()) {
                unloadNativeModel(nativeHandle)
                nativeHandle = 0L
                isLoaded.set(false)
            }
        }
    }
    
    override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = callbackFlow {
        val handle = lock.read {
            if (!isLoaded.get()) {
                throw RunnerError.modelNotLoaded()
            }
            nativeHandle
        }
        
        // Use handle for inference...
        
        awaitClose {
            // Clean up any request-specific resources
            cleanupRequestResources()
        }
    }
}
```

### **Testing Streaming Runners**

When testing streaming runners, ensure you test both streaming and non-streaming modes:

```kotlin
@Test
fun testStreamingInference() = runTest {
    val runner = MyStreamingRunner()
    runner.load(defaultConfig)
    
    val request = InferenceRequest(
        sessionId = "test-stream",
        inputs = mapOf(InferenceRequest.INPUT_TEXT to "Hello"),
        params = emptyMap()
    )
    
    // Test streaming mode
    val results = mutableListOf<InferenceResult>()
    runner.runAsFlow(request).toList(results)
    
    // Verify we got both partial and final results
    assertTrue(results.any { it.partial })
    assertTrue(results.any { !it.partial })
    assertTrue(results.all { it.error == null })
}

@Test
fun testNonStreamingInference() {
    val runner = MyStreamingRunner()
    runner.load(defaultConfig)
    
    val request = InferenceRequest(
        sessionId = "test-non-stream",
        inputs = mapOf(InferenceRequest.INPUT_TEXT to "Hello"),
        params = mapOf("stream" to false)
    )
    
    // Test non-streaming mode
    val result = runner.run(request, stream = false)
    
    // Verify we got a final result directly
    assertFalse(result.partial)
    assertNull(result.error)
    assertNotNull(result.outputs[InferenceResult.OUTPUT_TEXT])
}
```

### **Performance and Best Practices**

1. **Avoid Blocking Operations**: Use coroutines and non-blocking I/O for better performance.

2. **Proper Logging**: Log important events but avoid excessive logging in hot paths.

3. **Memory Management**: Be mindful of memory usage, especially with large models or long-running streams.

4. **Connection Reuse**: Reuse connections when possible for remote APIs.

5. **Timeout Handling**: Implement appropriate timeouts for external calls.

Example with proper coroutine usage:

```kotlin
override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = callbackFlow {
    val deferredResult = CoroutineScope(Dispatchers.IO).async {
        // Perform non-blocking I/O operation
        externalAPI.generateStream(input)
    }
    
    try {
        val stream = deferredResult.await()
        stream.collect { token ->
            trySend(
                InferenceResult.textOutput(
                    text = token,
                    metadata = mapOf("partial" to true),
                    partial = true
                )
            )
        }
        
        // Send final result
        trySend(
            InferenceResult.textOutput(
                text = "Complete response",
                metadata = mapOf("partial" to false),
                partial = false
            )
        )
    } catch (e: Exception) {
        trySend(InferenceResult.error(RunnerError.runtimeError(e.message ?: "Unknown error", e)))
    } finally {
        close()
    }
    
    awaitClose {
        deferredResult.cancel()
    }
}
```

### **Custom Configuration**

Add vendor-specific configuration classes:

```kotlin
data class OpenAIConfig(
    val apiKey: String,
    val model: String = "gpt-3.5-turbo",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048
)
```

### **Error Handling**

Use the built-in error types:

```kotlin
return when (errorType) {
    "auth" -> InferenceResult.error(RunnerError.authenticationError("Invalid API key"))
    "network" -> InferenceResult.error(RunnerError.networkError("Connection failed"))
    "quota" -> InferenceResult.error(RunnerError.quotaExceeded("API quota exceeded"))
    else -> InferenceResult.error(RunnerError.runtimeError("Unknown error"))
}
```

---

## 📞 Support

For questions or issues:
1. Check existing mock runners for reference
2. Review the architecture documentation
3. Test with MockFirst strategy to ensure fallback works
4. Ensure your runner follows the established patterns

### **Enhanced Runtime Settings Integration**

When implementing new runners, remember to:
1. Implement `getParameterSchema()` to enable dynamic UI generation
2. Implement `validateParameters()` for real-time validation
3. Use runner-specific parameters from `EngineSettings` in your `load()` method
4. Test parameter validation in the Engine Settings Activity

**Total Time Estimate**: 30-60 minutes for a basic runner, 2-4 hours for a full-featured implementation.