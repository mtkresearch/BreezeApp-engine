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
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicBoolean

class OpenAILLMRunner(
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1"
) : BaseRunner, FlowStreamingRunner {
    
    companion object {
        private const val TAG = "OpenAILLMRunner"
    }
    
    private val isLoaded = AtomicBoolean(false)
    
    override fun load(config: ModelConfig): Boolean {
        return try {
            Log.d(TAG, "Loading OpenAI LLM Runner with model: ${config.modelName}")
            // Initialize OpenAI client here
            isLoaded.set(true)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load OpenAI LLM Runner", e)
            false
        }
    }
    
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        if (!isLoaded.get()) {
            return InferenceResult.error(RunnerError.modelNotLoaded())
        }
        
        val prompt = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
        
        return try {
            // Call OpenAI API here
            val response = callOpenAIAPI(prompt)
            
            InferenceResult.success(
                outputs = mapOf(InferenceResult.OUTPUT_TEXT to response),
                metadata = mapOf(
                    InferenceResult.META_MODEL_NAME to "gpt-3.5-turbo",
                    InferenceResult.META_SESSION_ID to input.sessionId
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in OpenAI API call", e)
            InferenceResult.error(RunnerError.runtimeError(e.message ?: "API Error", e))
        }
    }
    
    override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = flow {
        // Implement streaming response here
        // This is where you'd handle OpenAI's streaming API
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
    
    private fun callOpenAIAPI(prompt: String): String {
        // Implement actual OpenAI API call here
        // This is a placeholder
        return "OpenAI response for: $prompt"
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
import com.mtkresearch.breezeapp.engine.data.runner.core.RunnerInfo
import com.mtkresearch.breezeapp.engine.domain.model.*

class [Vendor][Capability]Runner : BaseRunner {
    
    override fun load(config: ModelConfig): Boolean = true
    
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        // Implement your AI logic here
        return InferenceResult.success(mapOf())
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

### **Streaming Support**

Implement `FlowStreamingRunner` for real-time responses:

```kotlin
override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = flow {
    // Emit partial results as they come
    emit(InferenceResult.success(mapOf("partial" to "result"), partial = true))
    emit(InferenceResult.success(mapOf("final" to "result"), partial = false))
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

**Total Time Estimate**: 30-60 minutes for a basic runner, 2-4 hours for a full-featured implementation.