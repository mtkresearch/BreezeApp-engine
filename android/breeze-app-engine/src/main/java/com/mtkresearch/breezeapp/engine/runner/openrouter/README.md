# OpenRouter Runner Implementation

## Overview

The OpenRouterLLMRunner is a complete implementation of the BreezeApp Engine runner architecture that provides access to multiple AI models through OpenRouter's unified API. This implementation follows clean architecture principles and integrates seamlessly with the existing BreezeApp infrastructure.

## Features

### ✅ Core Capabilities
- **Multiple Model Support**: Access to OpenAI, Anthropic, and other models through OpenRouter
- **Streaming Support**: Real-time token streaming with SSE (Server-Sent Events)
- **Non-streaming Support**: Traditional request-response pattern
- **API Key Management**: Secure API key handling with multiple configuration sources
- **Error Handling**: Comprehensive error mapping from HTTP status codes to domain errors
- **Network Resilience**: Timeout handling, connection validation, and graceful failures

### ✅ Architecture Compliance
- **Clean Architecture**: Implements domain interfaces (`BaseRunner`, `FlowStreamingRunner`)
- **Dependency Injection**: Support for Context injection via constructor
- **Annotation-based Discovery**: Uses `@AIRunner` annotation for automatic registration
- **Thread Safety**: Atomic operations and proper synchronization
- **Resource Management**: Proper cleanup and lifecycle management

### ✅ OpenRouter Integration
- **OpenAI-compatible API**: Uses `/api/v1/chat/completions` endpoint
- **Authentication**: Bearer token authentication with API key
- **Parameter Mapping**: Converts BreezeApp parameters to OpenRouter format
- **Response Parsing**: Handles both streaming and non-streaming responses
- **Error Mapping**: Maps OpenRouter error codes to BreezeApp domain errors

## Implementation Details

### Class Structure
```
OpenRouterLLMRunner
├── Constructors (API key, Context, default)
├── Lifecycle Methods (load, unload, isLoaded, isSupported)
├── Inference Methods (run, runAsFlow)
├── HTTP Communication (createConnection, makeHttpRequest)
├── Request/Response Processing (buildRequestBody, parseResponse)
├── Error Handling (mapExceptionToError, mapHttpErrorCode)
└── Utility Methods (validation, connectivity checks)
```

### Key Files
- **Implementation**: `OpenRouterLLMRunner.kt` - Main runner implementation
- **Tests**: `OpenRouterLLMRunnerTest.kt` - Comprehensive unit tests
- **Documentation**: `README.md` - This documentation file

### Configuration
The runner supports multiple API key configuration sources:
1. Constructor parameter (highest priority)
2. ModelConfig parameters (`api_key` or `openrouter_api_key`)
3. System properties (`openrouter.api.key`)
4. Environment variables (implementation dependent)

### Usage Example
```kotlin
// Via annotation discovery (automatic)
@AIRunner(
    vendor = VendorType.OPENROUTER,
    priority = RunnerPriority.NORMAL,
    capabilities = [CapabilityType.LLM]
)

// Manual instantiation
val runner = OpenRouterLLMRunner(apiKey = "sk-your-api-key")
val config = ModelConfig(
    modelName = "openai/gpt-4",
    parameters = mapOf("temperature" to 0.7f, "max_tokens" to 2048)
)

runner.load(config)

// Non-streaming inference
val request = InferenceRequest(
    sessionId = "chat-123",
    inputs = mapOf(InferenceRequest.INPUT_TEXT to "Hello!"),
    params = mapOf(InferenceRequest.PARAM_TEMPERATURE to 0.8f)
)
val result = runner.run(request, stream = false)

// Streaming inference
runner.runAsFlow(request).collect { partialResult ->
    println("Token: ${partialResult.outputs[InferenceResult.OUTPUT_TEXT]}")
}
```

### Error Handling
The implementation maps OpenRouter HTTP errors to BreezeApp domain errors:
- `400 Bad Request` → Invalid input error
- `401 Unauthorized` → Authentication error (invalid API key)
- `402 Payment Required` → Quota exceeded error
- `403 Forbidden` → Content filtered error
- `408/429` → Timeout/rate limit errors
- `502/503` → Service unavailable errors

### Testing
The implementation includes comprehensive unit tests covering:
- ✅ Constructor variations and dependency injection
- ✅ Configuration loading and validation
- ✅ API key management and security
- ✅ Capability and metadata reporting
- ✅ Error handling for various scenarios
- ✅ Hardware support validation
- ✅ Lifecycle management (load/unload operations)

### Security Considerations
- API keys are never logged or exposed in error messages
- HTTPS-only communication with OpenRouter
- Request/response sanitization
- Secure credential handling across constructor options

## Integration Status

### ✅ Completed
1. **Core Implementation**: Full OpenRouterLLMRunner class with all required methods
2. **Annotation Support**: Proper `@AIRunner` annotation for auto-discovery
3. **HTTP Client**: Complete HTTP communication layer with timeouts
4. **Streaming Support**: SSE parsing and flow-based streaming
5. **Error Handling**: Comprehensive error mapping and recovery
6. **Testing**: Unit test suite covering major functionality
7. **Documentation**: Implementation guide and API documentation
8. **Build Verification**: Successful compilation and debug build

### ✅ Auto-Discovery Integration
The runner will be automatically discovered and registered by the BreezeApp Engine because:
- Uses `@AIRunner` annotation with proper metadata
- Extends `BaseRunner` and implements `FlowStreamingRunner`
- Provides required constructors for dependency injection
- Passes hardware support validation (`isSupported()` returns true with internet)

### Next Steps for Users
1. **Add API Key**: Configure OpenRouter API key through any supported method
2. **Enable Network**: Ensure device has internet connectivity for cloud API access
3. **Test Integration**: The runner will be available for LLM capability requests
4. **Monitor Usage**: Check logs for runner registration and execution

## OpenRouter API Coverage

### ✅ Implemented Features
- Chat completions endpoint (`/api/v1/chat/completions`)
- Streaming and non-streaming modes
- Model selection and parameter passing
- Authentication and authorization
- Error handling and status codes
- Request/response header management

### 🔄 Advanced Features (Future)
- Tool calling support
- Function calling integration
- Response format specifications
- Provider-specific routing
- Usage tracking and cost monitoring

The OpenRouterLLMRunner provides a solid foundation for cloud-based AI inference within the BreezeApp ecosystem, offering reliable access to multiple AI models through a single, unified interface.