English | [繁體中文](./docs/i18n/README_zh.md)

# 🤖 Welcome to BreezeApp Engine!

Ready to build the future of on-device AI on Android? You're in the right place!

BreezeApp Engine is a next-generation framework for creating powerful, modular, and extensible AI-driven experiences. It's built with a focus on type safety, modern Android practices, and a great developer experience.

### 📦 Latest Versions

- `BreezeApp-engine`: `{{BREEZEAPP_ENGINE_VERSION}}`
- `EdgeAI`: `{{EDGEAI_VERSION}}`

## ✨ The Heart of the Project: The AI Engine

The most important part of this project is the **`android/breeze-app-engine`**.

Think of it as a powerful, "headless" **AI Brain** for Android. It runs as a background service, completely separate from any user interface. Its sole purpose is to manage, execute, and serve AI capabilities (like text generation, speech recognition, etc.) to any application that needs them.

By decoupling the complex AI logic from the UI, we empower app developers to add sophisticated AI features with minimal effort.

## 🔎 The Runtime View: How Client Talks to the Engine

At runtime, your app (the client) sends an `AIRequest` to the engine. The engine processes it and responds with an `AIResponse`. This interaction is completely decoupled from UI logic.

```mermaid
%%{init: {'flowchart': {'useMaxWidth': false, 'width': 800}}}%%
graph TD
    A["📱 Client App<br/>(breeze-app-client)"]
    B["🧠 AI Engine<br/>(breeze-app-engine)"]

    A -- "Send AIRequest<br>(e.g. EdgeAI.chat/EdgeAI.tts/EdgeAI.asr)" --> B
    B -- "Return AIResponse" --> A

    style A fill:#E8F5E9,stroke:#4CAF50
    style B fill:#E3F2FD,stroke:#2196F3
```

This clean separation allows the engine to remain UI-agnostic and service-oriented.

## 🚀 How to Get Started

Your path depends on your goal. Are you building an app *with* the engine, or building a new feature *for* the engine?

### 📱 For App Developers (Using the Engine)

If you want to add AI features to your Android app, this is your path.

1.  **Start Here:** Our **[BreezeApp Client Guide](https://github.com/mtkresearch/BreezeApp-client/blob/174b3717575664dd8b08f195cbfad9aad5c300f1/README.md)** is the best place to begin. It provides a step-by-step tutorial on how to integrate the `EdgeAI` SDK and make your first API call.
2.  **Explore the API:** The `EdgeAI` SDK is the public API for the engine. You can explore its features and data models in the **[EdgeAI README](./android/EdgeAI/README.md)**.

### 🧠 For AI/ML Engineers (Extending the Engine)

If you want to add a new model or AI capability to the engine itself.

1.  **Understand the Design:** The **[Architecture Guide](./docs/ARCHITECTURE.md)** explains the internal design of the engine and how all the pieces fit together.
2.  **Build a Runner:** Follow the **[Runner Development Guide](./docs/RUNNER_DEVELOPMENT.md)** to learn how to implement a new `Runner` that can be discovered by the engine.

## 🤝 Join Our Community & Contribute

Whether you're fixing a bug, improving the docs, or adding a revolutionary new runner, we welcome your contributions!

* **Contribution Guidelines:** Please read our **[Contributing Guide](./docs/CONTRIBUTING.md)**.
* **Have Questions?** Don't hesitate to open an issue! We're happy to help.