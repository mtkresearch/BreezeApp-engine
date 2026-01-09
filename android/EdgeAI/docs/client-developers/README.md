# EdgeAI SDK - Client Developer Documentation

Complete guides for integrating EdgeAI SDK into your Android app.

## Getting Started

New to EdgeAI? Start here:

1. **[Getting Started](./getting-started.md)** - Install, initialize, make your first API call
2. **[API Reference](./api-reference.md)** - Complete API documentation
3. **[Usage Guide](./usage-guide.md)** - Advanced usage & configuration

## Documentation

### Core Guides

- **[Getting Started](./getting-started.md)** - Quick start guide
  - Installation (JitPack dependency)
  - Prerequisites (BreezeApp Engine)
  - 3-step integration
  - First API call

- **[API Reference](./api-reference.md)** - Complete API documentation
  - Chat API (LLM text generation)
  - ASR API (Speech-to-text)
  - TTS API (Text-to-speech)
  - Guardrail API (Content safety)
  - Request/response models
  - All parameters

- **[Usage Guide](./usage-guide.md)** - Advanced usage
  - Configuration options
  - Permissions
  - Multi-turn conversations
  - Custom parameters
  - FAQ

### Best Practices

- **[Error Handling](./error-handling.md)** - Exception handling
  - Exception hierarchy
  - Error types
  - Handling strategies
  - Recovery patterns

- **[Best Practices](./best-practices.md)** - Lifecycle & UI integration
  - Lifecycle management
  - State management
  - UI/UX patterns
  - Performance optimization

## Examples

**See unit tests for complete examples**:
- [`EdgeAITest.kt`](../../src/test/java/com/mtkresearch/breezeapp/edgeai/EdgeAITest.kt)
- [`EdgeAIUsageExample.kt`](../../src/main/java/com/mtkresearch/breezeapp/edgeai/EdgeAIUsageExample.kt)

## Related Documentation

- **[Main README](../../README.md)** - SDK overview & quick start
- **[BreezeApp Engine](../../../breeze-app-engine/README.md)** - Engine architecture
- **[Model Management](../../../../docs/guides/model-management.md)** - Model lifecycle
