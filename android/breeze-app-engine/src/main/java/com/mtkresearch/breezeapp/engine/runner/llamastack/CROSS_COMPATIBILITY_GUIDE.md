# Cross-Compatibility Configuration Guide

**🔧 LlamaStackRunner Cross-API Compatibility**

This guide explains how to configure LlamaStackRunner to work with different OpenAI-compatible APIs.

---

## ✅ **Supported Endpoints**

### **1. LlamaStack (Native)**
```kotlin
val config = EngineSettings().withRunnerParameters("LlamaStackRunner", mapOf(
    "endpoint" to "https://api.llamastack.ai",
    "api_key" to "your-llamastack-key",
    "model_id" to "llama-3.2-90b-vision-instruct"
))
```

### **2. OpenRouter (Compatible)**
```kotlin
val config = EngineSettings().withRunnerParameters("LlamaStackRunner", mapOf(
    "endpoint" to "https://openrouter.ai/api/v1",
    "api_key" to "your-openrouter-key",
    "model_id" to "openai/gpt-4o-mini"  // Or any OpenRouter model
))
```

### **3. Custom OpenAI-Compatible APIs**
```kotlin
val config = EngineSettings().withRunnerParameters("LlamaStackRunner", mapOf(
    "endpoint" to "https://your-api.com/v1",
    "api_key" to "your-api-key",
    "model_id" to "your-model-id"
))
```

---

## 🎯 **Optimal Runner Selection**

| Endpoint | Recommended Runner | Reason |
|----------|-------------------|---------|
| `api.llamastack.ai` | **LlamaStackRunner** | Native support, full features |
| `openrouter.ai` | **OpenRouterLLMRunner** | Optimized for OpenRouter features |
| `api.openai.com` | **Dedicated OpenAI Runner** | API-specific optimizations |
| Generic OpenAI-compatible | **LlamaStackRunner** | Universal compatibility |

---

## ⚠️ **Compatibility Warnings**

The system will automatically detect and warn about suboptimal configurations:

### **Warning Example**
```
⚠️ Suboptimal runner selection detected:
   Endpoint: https://openrouter.ai/api/v1
   Selected: LlamaStackRunner  
   Suggested: OpenRouterLLMRunner (95% confidence)
   Reason: OpenRouter endpoints work best with OpenRouterLLMRunner
```

---

## 🔧 **Parameter Override System**

The robust parameter hierarchy ensures your endpoint choice is respected:

### **Parameter Layers (Priority Order)**
1. **Runner Defaults** - Base configuration from runner
2. **Engine Settings** - Your saved configuration  
3. **Client Overrides** - Dynamic runtime parameters ⭐

### **Client-Overridable Parameters**
- ✅ `endpoint` - API endpoint URL
- ✅ `api_key` - Authentication key  
- ✅ `model_id` - Model identifier
- ✅ `stream` - Streaming mode
- ✅ `response_format` - Response format
- ✅ `microphone_mode` - ASR microphone mode

---

## 🚀 **Smart URL Handling**

LlamaStackRunner automatically handles different endpoint formats:

### **Input → Output Examples**
- `https://openrouter.ai/api/v1` → `https://openrouter.ai/api/v1/chat/completions`
- `https://api.llamastack.ai` → `https://api.llamastack.ai/v1/chat/completions`
- `https://custom.ai/v1/chat/completions` → `https://custom.ai/v1/chat/completions` (unchanged)

---

## 🛠️ **Troubleshooting**

### **Issue: Endpoint Override Not Working**
**Solution**: Ensure you're using client parameter override:
```kotlin
// ✅ Correct - Client override
request.params["endpoint"] = "https://openrouter.ai/api/v1"

// ❌ Incorrect - Only saved in settings
settings.getRunnerParameters("LlamaStackRunner")["endpoint"]
```

### **Issue: Authentication Failures**
**Solution**: Match API key format to endpoint:
```kotlin
// OpenRouter format
"api_key" to "sk-or-v1-your-key"

// LlamaStack format  
"api_key" to "your-llamastack-token"
```

### **Issue: Model Not Found**
**Solution**: Use endpoint-appropriate model IDs:
```kotlin
// OpenRouter models
"model_id" to "openai/gpt-4o-mini"
"model_id" to "anthropic/claude-3-haiku"

// LlamaStack models
"model_id" to "llama-3.2-90b-vision-instruct"
```

---

## 📊 **Performance Recommendations**

### **Best Performance**
- Use **native runner** for your endpoint (OpenRouterLLMRunner for OpenRouter)
- Use **local endpoints** when possible for lower latency
- Use **appropriate model sizes** for your hardware

### **Best Compatibility**  
- Use **LlamaStackRunner** as universal fallback
- Enable **smart detection warnings** for guidance
- Test with **development endpoints** before production

---

## 🔒 **Security Best Practices**

1. **API Key Management**
   - Store keys securely using Android Keystore
   - Use environment-specific keys (dev/staging/prod)
   - Rotate keys regularly

2. **Endpoint Validation**
   - Verify HTTPS endpoints only
   - Validate endpoint certificates
   - Monitor for suspicious endpoint changes

3. **Parameter Sanitization**
   - Validate all input parameters
   - Log parameter changes (without sensitive data)
   - Use parameter schema validation

---

**Status**: ✅ **Cross-Compatibility Enabled**

This configuration allows maximum flexibility while providing smart guidance for optimal performance.