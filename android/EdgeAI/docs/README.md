# EdgeAI SDK Documentation

EdgeAI is an Android SDK for on-device AI capabilities (LLM chat, text-to-speech, speech-to-text) via BreezeApp Engine.

---

## Choose Your Path

### 👨‍💻 I want to use EdgeAI in my app

**→ [Client Developer Documentation](./client-developers/)**

- Quick integration guide
- API usage patterns
- Error handling
- Production best practices

**Quick start**: [5-minute integration guide](./client-developers/getting-started.md)

---

### 🔧 I want to contribute to EdgeAI

**→ [Maintainer Documentation](./maintainers/)**

- SDK architecture
- Development workflow
- Testing procedures
- Release process

**Quick start**: [Setup and first contribution](./maintainers/README.md#quick-start)

---

### 📚 I want to see the API reference

**→ [EdgeAI API Documentation](https://mtkresearch.github.io/BreezeApp-engine/EdgeAI/)** (auto-generated, always up-to-date)

**Working code examples**: [Unit tests](../src/test/java/com/mtkresearch/breezeapp/edgeai/)

> **Local generation**: Run `./gradlew :EdgeAI:dokkaHtml` from `android/` directory, then open `EdgeAI/build/dokka/index.html`

---

## Quick Links

**For App Developers**:
- [Getting Started](./client-developers/getting-started.md) - Install and first API call
- [Usage Guide](./client-developers/usage-guide.md) - Advanced patterns
- [Error Handling](./client-developers/error-handling.md) - Exception strategies
- [API Reference](https://mtkresearch.github.io/BreezeApp-engine/EdgeAI/) - Complete API docs

**For Contributors**:
- [Architecture](./maintainers/architecture.md) - How EdgeAI works
- [Contributing](./maintainers/contributing.md) - Development workflow
- [Testing](./maintainers/testing.md) - Test procedures

**Code Examples**:
- [Unit Tests](../src/test/java/com/mtkresearch/breezeapp/edgeai/) - Working examples for all APIs

---

## What is EdgeAI?

EdgeAI is a **client SDK** that provides a type-safe Kotlin API for Android apps to access AI capabilities through BreezeApp Engine.

**Architecture**: `Your App → EdgeAI SDK → AIDL IPC → BreezeApp Engine → AI Models`

**Key Features**:
- 🚀 Type-safe Kotlin API
- 📡 Streaming support via Flow
- 🔌 Automatic service connection
- ❌ Clear error handling
- 📱 Offline-first (on-device models)

**See**: [Architecture documentation](./maintainers/architecture.md) for details
