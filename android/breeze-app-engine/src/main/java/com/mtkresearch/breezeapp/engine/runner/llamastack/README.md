# LlamaStack Integration Guide

**🚀 Remote-First Strategy: Simple, Robust, Production-Ready Integration**

---

## 📋 Table of Contents

1. [Strategic Overview](#strategic-overview)
2. [Architecture Decision](#architecture-decision)
3. [Implementation Roadmap](#implementation-roadmap)
4. [Phase 1: Remote-Only MVP](#phase-1-remote-only-mvp)
5. [Phase 2: Advanced Features](#phase-2-advanced-features)
6. [Configuration Guide](#configuration-guide)
7. [Testing Strategy](#testing-strategy)
8. [Production Deployment](#production-deployment)
9. [Future Enhancements](#future-enhancements)

---

## Strategic Overview

### 🎯 **LlamaStack Integration Strategy**

Following **Senior Product Manager approval**, this integration adopts a **Remote-First Strategy** that aligns with BreezeApp-engine's core principles:

- ✅ **"Don't Over-Engineer"** - Simple, maintainable architecture
- ✅ **"Minimal Dependencies"** - Standard HTTP clients vs complex version management
- ✅ **"Clean Architecture"** - Clear separation without complex adapters
- ✅ **"Pragmatic Implementation"** - Solve real problems efficiently

### 🏗️ **What is LlamaStack?**

LlamaStack is Meta's comprehensive AI framework providing remote inference capabilities for Llama models. This integration focuses on **what LlamaStack does uniquely well**:

### 🚀 **Core Value Proposition**

**BreezeApp-engine gains these unique capabilities through LlamaStack:**

1. 🖼️ **Vision Language Models (VLM)** - Image reasoning and multimodal interactions
2. 🔍 **RAG Integration** - Retrieval-Augmented Generation for enhanced knowledge
3. 🤖 **Agent Support** - Advanced AI agent capabilities with tool calling
4. 📊 **Large Model Access** - Access to 70B+ parameter models via remote processing

### 🔄 **Complementary Architecture**

```
┌─────────────────────────────────────────────────────────┐
│                BreezeApp Engine                         │
├─────────────────────────────────────────────────────────┤
│  ExecutorchLLMRunner (HIGH Priority)                   │
│  ├─ Local inference only                               │
│  ├─ ExecuTorch 0.7.0 (stable, fast)                   │
│  ├─ Capabilities: [LLM]                                │
│  └─ Use case: Quick local responses                    │
├─────────────────────────────────────────────────────────┤
│  LlamaStackRunner (NORMAL Priority)                   │
│  ├─ Remote inference only                              │
│  ├─ Zero version conflicts                             │
│  ├─ Capabilities: [LLM, VLM]                           │
│  └─ Use case: Advanced AI features                     │
└─────────────────────────────────────────────────────────┘
```

### ✅ **Strategic Benefits**

- **Zero Version Conflicts** - No ExecuTorch compatibility issues
- **Rapid Development** - 5 days implementation vs 15+ days for complex approaches
- **High Reliability** - Remote HTTP clients are battle-tested
- **Easy Maintenance** - Simple codebase, predictable behavior
- **Future-Proof** - Easy to enhance when needed

---

## Architecture Decision

### 🚨 **Why Remote-First?**

**Problem**: BreezeApp-engine uses ExecuTorch 0.7.0, while LlamaStack uses older ExecuTorch commit 0a12e33. Version conflicts would create:
- ❌ Runtime crashes and ClassLoader conflicts
- ❌ Complex debugging and maintenance burden
- ❌ Brittle reflection-based workarounds

**Solution**: Focus on LlamaStack's **unique strengths** via remote inference:
- ✅ **Vision capabilities** ExecutorchLLMRunner lacks
- ✅ **Large model access** (70B+) impossible locally
- ✅ **RAG and agents** advanced features
- ✅ **Zero conflicts** with existing runners

### 📊 **Decision Matrix**

| Approach | Development Time | Maintenance Burden | Risk Level | Value Delivered |
|----------|------------------|-------------------|------------|-----------------|
| **Remote-First** ⭐ | 5 days | Low | Low | 80% |
| Complex Hybrid | 15+ days | High | Medium-High | 100% |
| Version Bridge | 20+ days | Very High | High | 90% |

**Winner**: Remote-First delivers **80% of value with 30% of complexity**

---

## Implementation Roadmap

### 🗓️ **Timeline: 2 Weeks Total**

```
Week 1: Phase 1 - Remote-Only MVP
├── Day 1-2: Core LlamaStackRunner implementation
├── Day 3: VLM capabilities
├── Day 4: Configuration system
└── Day 5: Testing & integration

Week 2: Phase 2 - Advanced Features  
├── Day 1-2: RAG integration
├── Day 3: Agent capabilities
├── Day 4: Performance optimization
└── Day 5: Production deployment
```

### 📋 **Deliverables**

**Phase 1 MVP**:
- ✅ `LlamaStackRunner` with remote-only inference
- ✅ VLM support (image + text processing)
- ✅ Simple configuration system
- ✅ Unit tests and integration tests

**Phase 2 Advanced**:
- ✅ RAG (Retrieval-Augmented Generation)
- ✅ Agent capabilities with tool calling
- ✅ Streaming support
- ✅ Production monitoring

---

## Phase 1: Remote-Only MVP

### 🛠️ **Core Implementation**

#### **1. Dependencies** (`build.gradle.kts`)

```kotlin
dependencies {
    // LlamaStack Kotlin SDK (remote only)
    implementation("com.llama.llamastack:llama-stack-client-kotlin:0.2.14") {
        // Exclude local ExecuTorch to prevent conflicts
        exclude(group = "org.pytorch", module = "executorch-android")
    }
    
    // Keep existing ExecuTorch for ExecutorchLLMRunner
    implementation("org.pytorch:executorch-android:0.7.0")
    
    // Standard dependencies (already present)
    // implementation("androidx.core:core-ktx:1.12.0")
    // implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

#### **2. Core Runner Implementation**

```kotlin
// File: LlamaStackRunner.kt
package com.mtkresearch.breezeapp.engine.runner.llamastack

import android.content.Context
import android.util.Base64
import com.mtkresearch.breezeapp.engine.annotation.*
import com.mtkresearch.breezeapp.engine.runner.core.*
import com.mtkresearch.breezeapp.engine.model.*
import com.mtkresearch.breezeapp.engine.core.Logger
import com.llama.llamastack.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean

@AIRunner(
    vendor = VendorType.META,
    priority = RunnerPriority.NORMAL, // Below ExecutorchLLMRunner (HIGH)
    capabilities = [CapabilityType.LLM, CapabilityType.VLM],
    defaultModel = "llama-3-70b-instruct"
)
class LlamaStackRunner(
    private val context: Context? = null
) : BaseRunner, FlowStreamingRunner {
    
    companion object {
        private const val TAG = "LlamaStackRunner"
    }
    
    // Simple, single client approach
    private var remoteClient: LlamaStackClientOkHttpClient? = null
    private var config: LlamaStackConfig? = null
    private val isLoaded = AtomicBoolean(false)
    
    override fun load(modelId: String, settings: EngineSettings): Boolean {
        return try {
            val runnerParams = settings.getRunnerParameters("llama_stack")
            config = LlamaStackConfig.fromParams(runnerParams, modelId)
            
            remoteClient = LlamaStackClientOkHttpClient
                .builder()
                .baseUrl(config!!.endpoint)
                .apply {
                    config!!.apiKey?.let { key ->
                        headers(mapOf("Authorization" to listOf("Bearer $key")))
                    }
                }
                .headers(mapOf("x-llamastack-client-version" to listOf("0.2.14")))
                .build()
            
            // Test connection
            testConnection()
            isLoaded.set(true)
            Logger.i(TAG, "LlamaStack remote client loaded successfully")
            true
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to load LlamaStack runner", e)
            isLoaded.set(false)
            false
        }
    }
    
    override fun run(input: InferenceRequest): InferenceResult {
        if (!isLoaded.get() || remoteClient == null) {
            return InferenceResult.error(RunnerError.modelNotLoaded())
        }
        
        return when {
            hasVisionInput(input) -> processVisionRequest(input)
            hasRAGRequest(input) -> processRAGRequest(input)
            else -> processStandardLLMRequest(input)
        }
    }
    
    override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = callbackFlow {
        if (!isLoaded.get() || remoteClient == null) {
            trySend(InferenceResult.error(RunnerError.modelNotLoaded()))
            close()
            return@callbackFlow
        }
        
        try {
            // Process request and emit results
            val result = when {
                hasVisionInput(input) -> processVisionRequest(input)
                else -> processStandardLLMRequest(input)
            }
            
            // Simulate streaming by chunking response
            val fullText = result.outputs["text"] as? String ?: ""
            emitTextAsStream(fullText)
            
        } catch (e: Exception) {
            trySend(InferenceResult.error(RunnerError.runtimeError("Streaming failed: ${e.message}")))
        }
        close()
    }
    
    // Implementation continues with helper methods...
}
```

#### **3. Configuration System**

```kotlin
// File: LlamaStackConfig.kt  
data class LlamaStackConfig(
    val endpoint: String = "https://api.llamastack.ai",
    val apiKey: String? = null,
    val modelId: String = "llama-3-70b-instruct", 
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val enableVision: Boolean = false,
    val enableRAG: Boolean = false,
    val enableAgents: Boolean = false
) {
    companion object {
        fun fromParams(params: Map<String, Any>, defaultModelId: String): LlamaStackConfig {
            return LlamaStackConfig(
                endpoint = params["endpoint"] as? String ?: "https://api.llamastack.ai",
                apiKey = params["api_key"] as? String,
                modelId = params["model_id"] as? String ?: defaultModelId,
                temperature = (params["temperature"] as? Number)?.toFloat() ?: 0.7f,
                maxTokens = (params["max_tokens"] as? Number)?.toInt() ?: 4096,
                enableVision = params["enable_vision"] as? Boolean ?: false,
                enableRAG = params["enable_rag"] as? Boolean ?: false,
                enableAgents = params["enable_agents"] as? Boolean ?: false
            )
        }
        
        // Predefined configurations
        fun production(apiKey: String) = LlamaStackConfig(
            endpoint = "https://api.llamastack.ai",
            apiKey = apiKey,
            modelId = "llama-3-70b-instruct"
        )
        
        fun development() = LlamaStackConfig(
            endpoint = "http://localhost:8080",
            modelId = "llama-3-8b-instruct"
        )
        
        fun visionEnabled(apiKey: String) = LlamaStackConfig(
            endpoint = "https://api.llamastack.ai", 
            apiKey = apiKey,
            modelId = "llama-3-90b-vision-instruct",
            enableVision = true
        )
    }
}
```

#### **4. VLM Implementation**

```kotlin
private fun processVisionRequest(input: InferenceRequest): InferenceResult {
    val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String
    val image = input.inputs[InferenceRequest.INPUT_IMAGE] as? ByteArray
    
    if (text == null || image == null) {
        return InferenceResult.error(RunnerError.invalidInput("VLM requires both text and image"))
    }
    
    return try {
        val imageBase64 = Base64.encodeToString(image, Base64.DEFAULT)
        val messages = listOf(
            mapOf(
                "role" to "user",
                "content" to listOf(
                    mapOf("type" to "text", "text" to text),
                    mapOf(
                        "type" to "image",
                        "image_url" to mapOf("url" to "data:image/jpeg;base64,$imageBase64")
                    )
                )
            )
        )
        
        val params = InferenceChatCompletionParams.builder()
            .modelId(config!!.modelId)
            .messages(messages)
            .temperature(config!!.temperature)
            .maxTokens(config!!.maxTokens)
            .build()
        
        val result = remoteClient!!.inference().chatCompletion(params)
        val responseText = result.content().firstOrNull()?.text ?: ""
        
        InferenceResult.textOutput(
            text = responseText,
            metadata = mapOf(
                InferenceResult.META_MODEL_NAME to config!!.modelId,
                "inference_mode" to "remote",
                "capability_type" to "VLM",
                "llama_stack_version" to "0.2.14",
                "processing_time_ms" to System.currentTimeMillis()
            )
        )
    } catch (e: Exception) {
        Logger.e(TAG, "VLM processing failed", e)
        InferenceResult.error(RunnerError.runtimeError("Vision processing failed: ${e.message}"))
    }
}
```

### 🧪 **Phase 1 Testing**

```kotlin
// File: LlamaStackRunnerTest.kt
class LlamaStackRunnerTest {
    
    @Test
    fun `should load successfully with valid configuration`() {
        val runner = LlamaStackRunner()
        val settings = EngineSettings().withRunnerParameters("llama_stack", mapOf(
            "endpoint" to "https://api.llamastack.ai",
            "api_key" to "test-key",
            "model_id" to "llama-3-70b-instruct"
        ))
        
        val loaded = runner.load("llama-3-70b-instruct", settings)
        assertThat(loaded).isTrue()
    }
    
    @Test
    fun `should handle VLM requests correctly`() {
        // Mock image bytes
        val imageBytes = "mock-image-data".toByteArray()
        val request = InferenceRequest(
            sessionId = "vlm-test",
            inputs = mapOf(
                "text" to "What's in this image?",
                "image" to imageBytes
            )
        )
        
        val result = runner.run(request)
        assertThat(result.error).isNull()
        assertThat(result.outputs["text"]).isNotNull()
    }
}
```

---

## Phase 2: Advanced Features

### 🔍 **RAG Integration**

```kotlin
private fun processRAGRequest(input: InferenceRequest): InferenceResult {
    if (!config!!.enableRAG) {
        return processStandardLLMRequest(input)
    }
    
    val query = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
    val ragSources = input.params["rag_sources"] as? List<String> ?: emptyList()
    
    return try {
        // Enhanced prompt with RAG context
        val enhancedPrompt = buildRAGPrompt(query, ragSources)
        
        val params = InferenceChatCompletionParams.builder()
            .modelId(config!!.modelId)
            .messages(listOf(mapOf("role" to "user", "content" to enhancedPrompt)))
            .temperature(config!!.temperature)
            .build()
        
        val result = remoteClient!!.inference().chatCompletion(params)
        val responseText = result.content().firstOrNull()?.text ?: ""
        
        InferenceResult.textOutput(
            text = responseText,
            metadata = mapOf(
                "inference_mode" to "remote",
                "capability_type" to "RAG",
                "rag_sources_used" to ragSources.size
            )
        )
    } catch (e: Exception) {
        InferenceResult.error(RunnerError.runtimeError("RAG processing failed: ${e.message}"))
    }
}
```

### 🤖 **Agent Support**

```kotlin
private fun processAgentRequest(input: InferenceRequest): InferenceResult {
    if (!config!!.enableAgents) {
        return processStandardLLMRequest(input)
    }
    
    val query = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
    val availableTools = input.params["available_tools"] as? List<String> ?: emptyList()
    
    return try {
        // Agent workflow with tool calling
        val agentPrompt = buildAgentPrompt(query, availableTools)
        
        val params = InferenceChatCompletionParams.builder()
            .modelId(config!!.modelId)
            .messages(listOf(mapOf("role" to "user", "content" to agentPrompt)))
            .temperature(config!!.temperature)
            .build()
        
        val result = remoteClient!!.inference().chatCompletion(params)
        val responseText = result.content().firstOrNull()?.text ?: ""
        
        InferenceResult.textOutput(
            text = responseText,
            metadata = mapOf(
                "inference_mode" to "remote",
                "capability_type" to "AGENT",
                "tools_available" to availableTools.size
            )
        )
    } catch (e: Exception) {
        InferenceResult.error(RunnerError.runtimeError("Agent processing failed: ${e.message}"))
    }
}
```

---

## Configuration Guide

### 📋 **Quick Setup Examples**

#### **Development Setup**

```kotlin
val devSettings = EngineSettings().withRunnerParameters("llama_stack", mapOf(
    "endpoint" to "http://localhost:8080",
    "model_id" to "llama-3-8b-instruct",
    "temperature" to 0.7f,
    "max_tokens" to 2048
))

aiEngineManager.updateSettings(devSettings)
```

#### **Production Setup**

```kotlin
val prodSettings = EngineSettings().withRunnerParameters("llama_stack", mapOf(
    "endpoint" to "https://api.llamastack.ai",
    "api_key" to getSecureApiKey(), // From secure storage
    "model_id" to "llama-3-70b-instruct",
    "temperature" to 0.8f,
    "max_tokens" to 4096,
    "enable_vision" to true
))

aiEngineManager.updateSettings(prodSettings)
```

#### **VLM-Enabled Setup**

```kotlin
val vlmSettings = EngineSettings().withRunnerParameters("llama_stack", mapOf(
    "endpoint" to "https://api.llamastack.ai",
    "api_key" to getSecureApiKey(),
    "model_id" to "llama-3-90b-vision-instruct",
    "enable_vision" to true,
    "temperature" to 0.7f
))

aiEngineManager.updateSettings(vlmSettings)
```

#### **Advanced Features Setup**

```kotlin
val advancedSettings = EngineSettings().withRunnerParameters("llama_stack", mapOf(
    "endpoint" to "https://api.llamastack.ai",
    "api_key" to getSecureApiKey(),
    "model_id" to "llama-3-70b-instruct",
    "enable_vision" to true,
    "enable_rag" to true,
    "enable_agents" to true,
    "temperature" to 0.7f,
    "max_tokens" to 8192
))

aiEngineManager.updateSettings(advancedSettings)
```

### 🔧 **Configuration Parameters**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `endpoint` | String | `https://api.llamastack.ai` | LlamaStack server URL |
| `api_key` | String? | null | Authentication key (optional for localhost) |
| `model_id` | String | `llama-3-70b-instruct` | Model to use for inference |
| `temperature` | Float | 0.7 | Response randomness (0.0-1.0) |
| `max_tokens` | Int | 4096 | Maximum tokens to generate |
| `enable_vision` | Boolean | false | Enable VLM capabilities |
| `enable_rag` | Boolean | false | Enable RAG processing |
| `enable_agents` | Boolean | false | Enable agent capabilities |

---

## Testing Strategy

### 🧪 **Unit Tests**

```kotlin
// Core functionality tests
@Test fun `loads with valid config`()
@Test fun `handles invalid config gracefully`()  
@Test fun `processes standard LLM requests`()
@Test fun `processes VLM requests correctly`()
@Test fun `handles network errors appropriately`()
```

### 🔗 **Integration Tests**

```kotlin
// BreezeApp engine integration tests  
@Test fun `integrates with runner discovery system`()
@Test fun `coexists with ExecutorchLLMRunner`()
@Test fun `respects priority-based selection`()
@Test fun `works with Guardian pipeline`()
```

### 🎭 **Mock Testing**

```kotlin
// Mock server for development
@Test fun `works with local mock server`()
@Test fun `handles mock VLM responses`() 
@Test fun `simulates network failures`()
```

---

## Production Deployment

### 🚀 **Deployment Checklist**

#### **Pre-Deployment**
- [ ] API keys securely configured
- [ ] Endpoint URLs validated
- [ ] Model IDs verified
- [ ] Network connectivity tested
- [ ] Error handling validated

#### **Monitoring Setup**
- [ ] Performance metrics collection
- [ ] Error rate monitoring
- [ ] Token usage tracking
- [ ] Response time monitoring
- [ ] Availability checks

#### **Security Review**
- [ ] API key rotation strategy
- [ ] HTTPS enforcement
- [ ] Input sanitization
- [ ] Output filtering (Guardian)
- [ ] Rate limiting consideration

### 📊 **Performance Monitoring**

```kotlin
// Enhanced result with metrics
private fun enhanceResultWithMetrics(
    result: InferenceResult,
    startTime: Long,
    requestType: String
): InferenceResult {
    val processingTime = System.currentTimeMillis() - startTime
    
    val enhancedMetadata = result.metadata.toMutableMap().apply {
        put(InferenceResult.META_PROCESSING_TIME_MS, processingTime)
        put("inference_mode", "remote")
        put("request_type", requestType)
        put("llama_stack_version", "0.2.14")
        put("endpoint_used", config!!.endpoint)
        put("model_used", config!!.modelId)
    }
    
    return result.copy(metadata = enhancedMetadata)
}
```

---

## Future Enhancements

### 🔮 **Planned Enhancements**

#### **Phase 3: Performance Optimization** (Optional)
- Response caching for repeated queries
- Request batching for efficiency
- Connection pooling optimization
- Async processing improvements

#### **Phase 4: Local Integration** (Future)
- **Only if ExecuTorch versions align**
- Simple local fallback option
- Hybrid mode with graceful switching
- Maintained as optional enhancement

#### **Phase 5: Advanced Features** (Future)
- Custom tool integration for agents
- Advanced RAG with vector databases
- Multi-modal input support
- Custom model fine-tuning support

### 🛣️ **Migration Path for ExecuTorch Alignment**

**When LlamaStack upgrades to ExecuTorch 0.7.0+:**

```kotlin
// Simple enhancement to existing runner
enum class InferenceMode {
    REMOTE_ONLY,    // Current default
    REMOTE_FIRST,   // Try remote, fallback to local (new)
    LOCAL_FIRST     // Try local, fallback to remote (new)
}

// Minimal code addition - no architectural changes needed
private fun selectInferenceMode(): InferenceMode {
    return when {
        config!!.mode == "local_first" && isLocalAvailable() -> LOCAL_FIRST
        config!!.mode == "remote_first" -> REMOTE_FIRST
        else -> REMOTE_ONLY // Safe default
    }
}
```

### 📈 **Success Metrics**

**Technical KPIs:**
- Implementation time: ≤ 10 days total
- Critical bugs: ≤ 3 issues
- Performance: ≤ 2s response time for standard requests
- Reliability: ≥ 99% uptime for remote endpoints

**Business KPIs:**
- Developer productivity: 2x faster VLM feature development
- User experience: Seamless multimodal interactions
- Maintainability: ≤ 2 hours/month maintenance overhead

---

## 🎯 **Getting Started**

### **Immediate Next Steps:**

1. **Review Architecture** - Read `EXECUTORCH_COMPATIBILITY.md` for context
2. **Setup Development** - Configure local LlamaStack server
3. **Implement Phase 1** - Follow the MVP implementation guide
4. **Run Tests** - Execute unit and integration tests
5. **Deploy to Staging** - Test in staging environment

### **Implementation Timeline:**

- **Day 1**: Setup and core runner structure
- **Day 2-3**: VLM capabilities and configuration
- **Day 4**: Integration testing
- **Day 5**: Phase 1 completion and review
- **Week 2**: Phase 2 advanced features

### **Support Resources:**

- **Architecture Questions**: See [ARCHITECTURE.md](../../../../../../../docs/ARCHITECTURE.md)
- **Contributing Guidelines**: See [CONTRIBUTING.md](../../../../../../../docs/CONTRIBUTING.md)
- **Runner Development**: See [RUNNER_DEVELOPMENT.md](../../../../../../../docs/RUNNER_DEVELOPMENT.md)

---

**Status**: ✅ **Ready for Implementation**

**Strategy**: Remote-First with Future Local Enhancement Path

**Approval**: Senior Product Manager Approved ⭐⭐⭐⭐

---

*This implementation guide provides a clear, actionable path to integrate LlamaStack while maintaining BreezeApp-engine's architectural excellence and simplicity principles.*