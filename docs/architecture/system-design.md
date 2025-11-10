# BreezeApp-engine System Architecture

**Purpose**: High-level system architecture overview
**Audience**: Architects, senior developers, technical decision-makers
**Last Updated**: 2025-11-03

---

## Table of Contents

1. [System Overview](#system-overview)
2. [Architectural Principles](#architectural-principles)
3. [Component Architecture](#component-architecture)
4. [Layer Architecture](#layer-architecture)
5. [Security Architecture](#security-architecture)
6. [Deployment Architecture](#deployment-architecture)
7. [Technology Stack](#technology-stack)
8. [Design Decisions](#design-decisions)
9. [Quality Attributes](#quality-attributes)

---

## System Overview (T059)

### Purpose

BreezeApp-engine is an **Android service APK** that provides AI inference capabilities to authorized client applications through secure AIDL (Android Interface Definition Language) interfaces.

### Core Capabilities

```
┌─────────────────────────────────────────────────────────┐
│            BreezeApp AI Engine Service                  │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  🧠 LLM (Large Language Model)                         │
│     • Text generation and completion                   │
│     • Conversational AI                                │
│     • Context-aware responses                          │
│                                                         │
│  👁️ VLM (Vision-Language Model)                        │
│     • Image understanding                              │
│     • Visual question answering                        │
│     • Multimodal reasoning                             │
│                                                         │
│  🎤 ASR (Automatic Speech Recognition)                 │
│     • Speech-to-text conversion                        │
│     • Real-time streaming recognition                  │
│     • Multiple language support                        │
│                                                         │
│  🔊 TTS (Text-to-Speech)                               │
│     • Natural voice synthesis                          │
│     • Multiple voice options                           │
│     • Streaming audio output                           │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### High-Level Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                    Ecosystem Layer                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  BreezeApp   │  │ BreezeApp    │  │  3rd Party   │      │
│  │  (Main App)  │  │   Client     │  │  Apps        │      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
└─────────┼──────────────────┼──────────────────┼──────────────┘
          │                  │                  │
          │    AIDL Interface (Signature Protected)
          │                  │                  │
┌─────────▼──────────────────▼──────────────────▼──────────────┐
│                  Service Layer                               │
│  ┌──────────────────────────────────────────────────┐       │
│  │         AIEngineService (Main Service)           │       │
│  │  • Service binding & lifecycle management        │       │
│  │  • Permission & signature verification           │       │
│  │  • Request routing & orchestration               │       │
│  └────────────────────┬─────────────────────────────┘       │
│                       │                                      │
│  ┌────────────────────▼─────────────────────────────┐       │
│  │        Business Logic Layer                      │       │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐        │       │
│  │  │   LLM    │ │   VLM    │ │   ASR    │        │       │
│  │  │ Manager  │ │ Manager  │ │ Manager  │ ...    │       │
│  │  └──────────┘ └──────────┘ └──────────┘        │       │
│  └────────────────────┬─────────────────────────────┘       │
│                       │                                      │
│  ┌────────────────────▼─────────────────────────────┐       │
│  │        AI Inference Layer                        │       │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐        │       │
│  │  │ExecuTorch│ │  Sherpa  │ │ MTK NPU  │        │       │
│  │  │ Runtime  │ │  ONNX    │ │ Backend  │        │       │
│  │  └──────────┘ └──────────┘ └──────────┘        │       │
│  └──────────────────────────────────────────────────┘       │
│                                                              │
│  ┌──────────────────────────────────────────────────┐       │
│  │        Data Layer                                │       │
│  │  • Model storage & management                    │       │
│  │  • Cache management                              │       │
│  │  • Configuration storage                         │       │
│  └──────────────────────────────────────────────────┘       │
└──────────────────────────────────────────────────────────────┘
          │                  │                  │
┌─────────▼──────────────────▼──────────────────▼──────────────┐
│              Infrastructure Layer                            │
│  • Android OS (API 34+)                                     │
│  • File System (AI models, cache)                          │
│  • Hardware Acceleration (NPU, GPU)                        │
└──────────────────────────────────────────────────────────────┘
```

### Key Characteristics

| Characteristic | Description |
|----------------|-------------|
| **Architecture Style** | Service-Oriented Architecture (SOA) |
| **Communication** | AIDL (Binder IPC) |
| **Security Model** | Signature-level permission + runtime verification |
| **Process Model** | Isolated process (`:ai_engine`) |
| **State Management** | Stateless service (models cached) |
| **Scalability** | Multiple concurrent clients (up to 50 connections) |
| **Deployment** | Single APK, multiple client bindings |

---

## Architectural Principles (T060)

### 1. **Separation of Concerns**

Each layer has a single, well-defined responsibility:
- **Service Layer**: Client communication, security, lifecycle
- **Business Logic Layer**: AI capability orchestration
- **Inference Layer**: Low-level AI model execution
- **Data Layer**: Persistence and caching

### 2. **Principle of Least Privilege**

Security enforced at multiple levels:
- Android permission system (signature-level)
- Runtime signature verification
- Process isolation (separate process)
- Minimal permission requests (WAKE_LOCK, FOREGROUND_SERVICE only)

### 3. **Defense in Depth**

Multiple security layers:
```
Layer 1: Android Permission Check (system)
         ↓
Layer 2: Signature Verification (SignatureValidator)
         ↓
Layer 3: onBind() Authorization (AIEngineService)
         ↓
Layer 4: Audit Logging (unauthorized attempts)
```

### 4. **Privacy by Design**

- **Zero Data Collection**: No user data leaves the device
- **No Network Dependency**: Fully functional offline
- **No Telemetry**: No analytics or tracking
- **On-Device Only**: All inference happens locally

### 5. **Performance Isolation**

AI inference runs in separate process (`:ai_engine`):
- **Memory Isolation**: AI models don't affect client app memory
- **Crash Isolation**: Engine crash doesn't crash client apps
- **Resource Management**: Easier to kill/restart heavy process
- **Thermal Management**: Separate thermal throttling

### 6. **Fail-Safe Defaults**

- Unknown clients → **Deny** binding
- Missing signature → **Deny** access
- Version mismatch → **Return error** (not crash)
- Model loading failure → **Graceful degradation**
- Network unavailable → **Continue** (offline mode)

### 7. **Open/Closed Principle**

- **Open for Extension**: New AI capabilities via new managers
- **Closed for Modification**: Core AIDL interface stable (versioned)
- **Backward Compatibility**: Old clients work with new engine

### 8. **Interface Segregation**

Separate AIDL interfaces for different concerns:
- `IAIEngineService.aidl`: Main service interface
- `IInferenceCallback.aidl`: Async inference callbacks
- `IStreamCallback.aidl`: Streaming data callbacks
- `IModelManager.aidl`: (Future) Model management

---

## Component Architecture (T061)

### Component Diagram

```
┌────────────────────────────────────────────────────────┐
│              Client Application Layer                  │
│  ┌──────────────────────────────────────────────┐     │
│  │         EngineClient (Integration SDK)       │     │
│  │  • Service binding management                │     │
│  │  • Version compatibility checking            │     │
│  │  • Lifecycle coordination                    │     │
│  └──────────────────┬───────────────────────────┘     │
└─────────────────────┼───────────────────────────────────┘
                      │ AIDL Calls
                      ▼
┌────────────────────────────────────────────────────────┐
│           Service Component (Engine APK)               │
│                                                        │
│  ┌──────────────────────────────────────────────┐    │
│  │  AIEngineService (Android Service)           │    │
│  │  ┌────────────────────────────────────────┐  │    │
│  │  │  onBind()                              │  │    │
│  │  │  • SignatureValidator.verify()         │  │    │
│  │  │  • Return binder or null               │  │    │
│  │  └────────────────────────────────────────┘  │    │
│  │                                               │    │
│  │  ┌────────────────────────────────────────┐  │    │
│  │  │  AIEngineServiceBinder                 │  │    │
│  │  │  (AIDL Implementation)                 │  │    │
│  │  │  • getVersion()                        │  │    │
│  │  │  • getCapabilities()                   │  │    │
│  │  │  • inferText() / inferTextAsync()      │  │    │
│  │  │  • inferVision()                       │  │    │
│  │  │  • recognizeSpeech()                   │  │    │
│  │  │  • synthesizeSpeech()                  │  │    │
│  │  └────────────┬───────────────────────────┘  │    │
│  └───────────────┼───────────────────────────────┘    │
│                  │                                     │
│  ┌───────────────▼───────────────────────────────┐    │
│  │       Capability Managers                     │    │
│  │  ┌──────────────┐  ┌──────────────┐          │    │
│  │  │  LLMManager  │  │  VLMManager  │          │    │
│  │  │  • Load LLM  │  │  • Load VLM  │          │    │
│  │  │  • Inference │  │  • Inference │          │    │
│  │  │  • Streaming │  │  • Vision    │          │    │
│  │  └──────────────┘  └──────────────┘          │    │
│  │  ┌──────────────┐  ┌──────────────┐          │    │
│  │  │  ASRManager  │  │  TTSManager  │          │    │
│  │  │  • Load ASR  │  │  • Load TTS  │          │    │
│  │  │  • Recognize │  │  • Synthesize│          │    │
│  │  └──────────────┘  └──────────────┘          │    │
│  └───────────────┬───────────────────────────────┘    │
│                  │                                     │
│  ┌───────────────▼───────────────────────────────┐    │
│  │       Inference Engines                       │    │
│  │  ┌──────────────┐  ┌──────────────┐          │    │
│  │  │ ExecuTorch   │  │ Sherpa ONNX  │          │    │
│  │  │ Runtime      │  │ Runtime      │          │    │
│  │  └──────────────┘  └──────────────┘          │    │
│  │  ┌──────────────┐  ┌──────────────┐          │    │
│  │  │ MTK NPU      │  │ NNAPI        │          │    │
│  │  │ Backend      │  │ Delegate     │          │    │
│  │  └──────────────┘  └──────────────┘          │    │
│  └────────────────────────────────────────────────    │
│                                                        │
│  ┌──────────────────────────────────────────────┐    │
│  │       Supporting Components                  │    │
│  │  ┌──────────────────────────────────────┐    │    │
│  │  │  SignatureValidator                  │    │    │
│  │  │  • Signature verification            │    │    │
│  │  │  • Caching (LRU, 5-min TTL)         │    │    │
│  │  │  • Audit logging                     │    │    │
│  │  └──────────────────────────────────────┘    │    │
│  │  ┌──────────────────────────────────────┐    │    │
│  │  │  ModelManager                        │    │    │
│  │  │  • Model discovery                   │    │    │
│  │  │  • Model loading/unloading           │    │    │
│  │  │  • Cache management                  │    │    │
│  │  └──────────────────────────────────────┘    │    │
│  │  ┌──────────────────────────────────────┐    │    │
│  │  │  ConfigurationManager                │    │    │
│  │  │  • Runtime settings                  │    │    │
│  │  │  • Performance tuning                │    │    │
│  │  └──────────────────────────────────────┘    │    │
│  └──────────────────────────────────────────────┘    │
└────────────────────────────────────────────────────────┘
```

### Component Responsibilities

#### 1. **AIEngineService** (Main Service)
- **Lifecycle**: `onCreate()`, `onBind()`, `onUnbind()`, `onDestroy()`
- **Security**: Signature verification via `SignatureValidator`
- **Binding**: Return AIDL binder or null based on authorization
- **Process**: Runs in `:ai_engine` isolated process

#### 2. **AIEngineServiceBinder** (AIDL Implementation)
- **Version Management**: `getVersion()`, `getVersionInfo()`
- **Capability Query**: `getCapabilities()`
- **LLM Methods**: `inferText()`, `inferTextAsync()`, `inferTextStreaming()`
- **VLM Methods**: `inferVision()`
- **ASR Methods**: `recognizeSpeech()`, `recognizeSpeechStreaming()`
- **TTS Methods**: `synthesizeSpeech()`
- **Model Management**: `listModels()`, `loadModel()`, `unloadModel()`

#### 3. **SignatureValidator** (Security)
- **Verification**: Check caller UID signature against authorized signatures
- **Performance**: LRU cache (50 entries, 5-minute TTL) for <10ms checks
- **Audit**: Log unauthorized attempts to local file (30-day retention)
- **Thread Safety**: Singleton with synchronized methods

#### 4. **Capability Managers**
- **LLMManager**: Manages LLM model loading and text inference
- **VLMManager**: Manages vision-language model and multimodal inference
- **ASRManager**: Manages speech recognition models
- **TTSManager**: Manages text-to-speech synthesis
- **Shared**: Model lifecycle, backend selection (CPU/NPU), error handling

#### 5. **Inference Engines**
- **ExecuTorch**: PyTorch mobile runtime for LLM/VLM
- **Sherpa ONNX**: ONNX runtime for ASR/TTS
- **MTK NPU Backend**: Hardware acceleration for MediaTek devices
- **NNAPI Delegate**: Android Neural Networks API for broad compatibility

#### 6. **ModelManager**
- **Discovery**: Scan predefined directories for AI models
- **Loading**: Load models into memory on-demand
- **Unloading**: Free memory when models not in use
- **Caching**: Keep frequently-used models in memory

#### 7. **ConfigurationManager**
- **Settings**: LLM parameters (temperature, top-K, max tokens)
- **Backends**: CPU vs. NPU selection
- **Performance**: Memory limits, thread pools
- **Persistence**: Save/load configuration from SharedPreferences

---

## Layer Architecture (T062)

### 4-Tier Architecture

```
┌──────────────────────────────────────────────────────┐
│  Layer 1: Presentation Layer (Client Side)          │
│  • EngineClient SDK                                 │
│  • AIDL stub generation                             │
│  • Lifecycle management                             │
│  • Error handling & retries                         │
└──────────────────┬───────────────────────────────────┘
                   │ AIDL Interface
┌──────────────────▼───────────────────────────────────┐
│  Layer 2: Service Layer (Engine Side)               │
│  • AIEngineService                                  │
│  • Security & permission checks                     │
│  • Request validation                               │
│  • Response serialization                           │
└──────────────────┬───────────────────────────────────┘
                   │ Internal API
┌──────────────────▼───────────────────────────────────┐
│  Layer 3: Business Logic Layer                      │
│  • Capability managers (LLM, VLM, ASR, TTS)        │
│  • Inference orchestration                          │
│  • Model lifecycle management                       │
│  • Caching strategies                               │
└──────────────────┬───────────────────────────────────┘
                   │ Native Interfaces
┌──────────────────▼───────────────────────────────────┐
│  Layer 4: Data & Inference Layer                    │
│  • ExecuTorch runtime (LLM/VLM)                    │
│  • Sherpa ONNX runtime (ASR/TTS)                   │
│  • MTK NPU backend                                  │
│  • Model file I/O                                   │
│  • Cache management                                 │
└──────────────────────────────────────────────────────┘
```

### Layer Interaction Rules

| From Layer | To Layer | Allowed? | Communication Method |
|------------|----------|----------|----------------------|
| 1 → 2 | Presentation → Service | ✅ Yes | AIDL (Binder IPC) |
| 2 → 3 | Service → Business | ✅ Yes | Direct method calls |
| 3 → 4 | Business → Data | ✅ Yes | JNI / Direct calls |
| 1 → 3 | Presentation → Business | ❌ No | Must go through service layer |
| 1 → 4 | Presentation → Data | ❌ No | Violates encapsulation |
| 4 → 2 | Data → Service | ❌ No | Callbacks only via layer 3 |

### Cross-Cutting Concerns

```
┌──────────────────────────────────────────────────────┐
│            Cross-Cutting Concerns                    │
├──────────────────────────────────────────────────────┤
│  🔒 Security: SignatureValidator (all layers)       │
│  📊 Logging: Structured logging (all layers)        │
│  ⚠️  Error Handling: Standardized error codes       │
│  📈 Monitoring: Performance metrics                 │
│  🔧 Configuration: ConfigurationManager             │
└──────────────────────────────────────────────────────┘
```

---

## Security Architecture (T063)

### Security Layers

```
┌──────────────────────────────────────────────────────┐
│  Layer 1: Android Permission System                 │
│  • Custom permission: BIND_AI_SERVICE               │
│  • Protection level: signature                      │
│  • Enforced by Android OS before binding            │
└──────────────────┬───────────────────────────────────┘
                   │ If permission granted
┌──────────────────▼───────────────────────────────────┐
│  Layer 2: Runtime Signature Verification            │
│  • SignatureValidator.verifyCallerSignature()      │
│  • Check caller UID against authorized signatures   │
│  • Performance: <10ms (LRU cache)                  │
│  • Audit: Log unauthorized attempts                │
└──────────────────┬───────────────────────────────────┘
                   │ If signature matches
┌──────────────────▼───────────────────────────────────┐
│  Layer 3: Service Binding Authorization             │
│  • AIEngineService.onBind()                        │
│  • Return binder if authorized, null if denied      │
│  • Log binding events                              │
└──────────────────┬───────────────────────────────────┘
                   │ Binder returned
┌──────────────────▼───────────────────────────────────┐
│  Layer 4: Process Isolation                         │
│  • Engine runs in :ai_engine process               │
│  • Separate memory space                           │
│  • Crash isolation                                  │
└──────────────────────────────────────────────────────┘
```

### Threat Model

| Threat | Mitigation | Layer |
|--------|------------|-------|
| **T1: Unauthorized app binding** | Signature-level permission | 1, 2 |
| **T2: Permission bypass** | Runtime signature verification | 2, 3 |
| **T3: Data interception** | Process isolation (IPC only) | 4 |
| **T4: Model theft** | File permissions (app-private) | OS |
| **T5: Malicious input** | Input validation & sanitization | 3 |
| **T6: DoS (resource exhaustion)** | Request rate limiting, timeouts | 3 |
| **T7: Privacy leak** | Zero network, on-device only | Design |
| **T8: Signature spoofing** | SHA-256 hash verification | 2 |

### Certificate Management

```
Development Environment:
  • Debug keystore (temporary, for testing)
  • Self-signed certificate
  • Known SHA-256 hash (hardcoded for dev)

Production Environment:
  • Play App Signing (Google-managed)
  • Production certificate
  • SHA-256 hash updated in SignatureValidator
  • All ecosystem apps signed with same cert
```

### Audit Logging

```kotlin
// Log format (JSON)
{
  "timestamp": "2025-11-03T10:30:45.123Z",
  "event": "UNAUTHORIZED_BINDING_ATTEMPT",
  "uid": 10234,
  "packageName": "com.malicious.app",
  "signatureHash": "ABC123...",
  "result": "DENIED"
}

// Storage: /data/data/com.mtkresearch.breezeapp.engine/files/audit/
// Retention: 30 days (automatic cleanup)
// Access: Root or app owner only
```

---

## Deployment Architecture (T064)

### Single APK, Multiple Clients

```
┌──────────────────────────────────────────────────────┐
│            Android Device                            │
│                                                      │
│  ┌────────────────────────────────────────────┐     │
│  │  com.mtkresearch.breezeapp                │     │
│  │  (BreezeApp Main App)                     │     │
│  │  • Package: installed                     │     │
│  │  • Signature: CERT_XYZ                    │     │
│  └──────────────────┬─────────────────────────┘     │
│                     │ Bind via AIDL                  │
│  ┌────────────────────────────────────────────┐     │
│  │  com.mtkresearch.breezeapp.client            │     │
│  │  (companion apps)                          │     │
│  │  • Package: installed                     │     │
│  │  • Signature: CERT_XYZ (same)             │     │
│  └──────────────────┬─────────────────────────┘     │
│                     │ Bind via AIDL                  │
│  ┌────────────────────────────────────────────┐     │
│  │  com.example.thirdparty                   │     │
│  │  (Third-party App)                        │     │
│  │  • Package: installed                     │     │
│  │  • Signature: CERT_XYZ (same)             │     │
│  └──────────────────┬─────────────────────────┘     │
│                     │                                │
│         All bind to ▼                                │
│  ┌────────────────────────────────────────────┐     │
│  │  com.mtkresearch.breezeapp.engine         │     │
│  │  (AI Engine Service - SINGLE INSTANCE)    │     │
│  │  • Package: installed once                │     │
│  │  • Signature: CERT_XYZ                    │     │
│  │  • Process: :ai_engine (isolated)         │     │
│  │  • Models: stored in /data/data/...       │     │
│  └────────────────────────────────────────────┘     │
│                                                      │
└──────────────────────────────────────────────────────┘
```

### Process Topology

```
Android System Process (system_server)
  │
  ├─ com.mtkresearch.breezeapp (UID: 10101)
  │    └─ Main process
  │
  ├─ com.mtkresearch.breezeapp.client (UID: 10102)
  │    └─ Main process
  │
  └─ com.mtkresearch.breezeapp.engine (UID: 10103)
       ├─ Main process (minimal, just service registration)
       └─ :ai_engine process (heavy lifting, AI inference)
            • Memory: 2-4GB (LLM models loaded)
            • CPU: High usage during inference
            • Lifetime: Bound to client lifecycle
```

### Storage Layout

```
/data/data/com.mtkresearch.breezeapp.engine/
├── files/
│   ├── models/                    # AI model files
│   │   ├── llm/
│   │   │   └── llama-3b-q4.pte   # ExecuTorch model
│   │   ├── asr/
│   │   │   └── sherpa-onnx-*.onnx
│   │   └── tts/
│   │       └── vits-*.onnx
│   ├── cache/                     # Inference cache
│   │   └── ... (temporary)
│   └── audit/                     # Security audit logs
│       └── audit-2025-11.jsonl
├── shared_prefs/
│   └── engine_config.xml          # Configuration
└── databases/                     # (Future) Model metadata
```

### Resource Requirements

| Resource | Minimum | Recommended | Notes |
|----------|---------|-------------|-------|
| **RAM** | 4GB | 8GB | LLM models require 2-4GB |
| **Storage** | 2GB free | 4GB free | Models + cache |
| **Android Version** | 14 (API 34) | 14+ | Minimum requirement |
| **CPU** | ARMv8-A | ARMv8.2+ | NPU requires newer chips |
| **Chipset** | Any | MediaTek with NPU | Hardware acceleration |

---

## Technology Stack

### Platform

- **OS**: Android 14+ (API 34)
- **Language**: Kotlin 100% (Java 11 compatibility)
- **Build System**: Gradle 8.x, Android Gradle Plugin 8.x
- **Minimum SDK**: 34
- **Target SDK**: 34

### AI Frameworks

| Framework | Purpose | Version |
|-----------|---------|---------|
| **ExecuTorch** | LLM/VLM inference | 0.2.0+ |
| **Sherpa ONNX** | ASR/TTS | Latest |
| **MTK NPU SDK** | Hardware acceleration | MediaTek proprietary |
| **NNAPI** | Fallback acceleration | Android built-in |

### Android Components

- **Service**: Background service with AIDL
- **AIDL**: 4 interface files (`IAIEngineService.aidl`, callbacks)
- **Permissions**: 1 custom signature-level permission
- **Process**: Isolated process (`:ai_engine`)
- **Foreground Service**: Type `dataSync`

### Testing

- **Unit Tests**: JUnit 4, Mockk, Robolectric
- **Integration Tests**: AndroidJUnit4, Espresso
- **Coverage**: ~85% for security-critical code

### Build & Release

- **ProGuard/R8**: Code shrinking and obfuscation
- **Play App Signing**: Google-managed certificate
- **Versioning**: Semantic versioning (MAJOR.MINOR.PATCH)

---

## Design Decisions

### DD1: Why AIDL instead of REST API?

**Decision**: Use AIDL (Android IPC) for service communication.

**Rationale**:
- ✅ Native Android mechanism (no network overhead)
- ✅ Type-safe interface generation
- ✅ Better performance (<1ms overhead vs. HTTP ~50ms)
- ✅ Automatic marshaling/unmarshaling
- ✅ Built-in lifecycle management
- ✅ No need for localhost server (security risk)

**Trade-offs**:
- ❌ Android-specific (not cross-platform)
- ❌ Limited to 1MB transaction size (Binder limit)
- ❌ More complex than REST for developers unfamiliar with Android

**Mitigation**: For large data (images, audio), use `ParcelFileDescriptor` to transfer file handles instead of raw bytes.

### DD2: Why Signature-Level Permission?

**Decision**: Use `signature` protection level instead of `normal` or `dangerous`.

**Rationale**:
- ✅ Only apps from same developer can bind
- ✅ No user prompt (seamless UX)
- ✅ Prevents unauthorized third-party access
- ✅ Supports Play App Signing (Google manages cert)

**Trade-offs**:
- ❌ Third-party developers must coordinate certificate signing
- ❌ Requires Play Console configuration for multiple apps

**Alternative Considered**: `dangerous` permission (user grants) - rejected due to poor UX and still requires permission declaration.

### DD3: Why Separate Process (`:ai_engine`)?

**Decision**: Run service in isolated process.

**Rationale**:
- ✅ Memory isolation (LLM models use 2-4GB RAM)
- ✅ Crash isolation (engine crash doesn't crash clients)
- ✅ Easier to kill/restart for memory management
- ✅ Better thermal management

**Trade-offs**:
- ❌ IPC overhead (~0.5ms per call)
- ❌ More complex debugging (multiple processes)

**Benchmarks**: IPC overhead negligible compared to inference time (100ms-10s).

### DD4: Why Zero Data Collection?

**Decision**: No telemetry, analytics, or user data collection.

**Rationale**:
- ✅ User privacy (core value proposition)
- ✅ GDPR/CCPA compliance (no PII collection)
- ✅ Offline-first design (no dependency on servers)
- ✅ Trust building (transparent open-source)

**Trade-offs**:
- ❌ No usage analytics for product improvements
- ❌ Harder to diagnose issues in the wild

**Mitigation**: Provide opt-in crash reporting via Firebase Crashlytics (future).

### DD5: Why LRU Cache for Signature Verification?

**Decision**: Cache signature verification results (5-minute TTL).

**Rationale**:
- ✅ Performance target: <10ms per verification
- ✅ Clients bind/unbind frequently (activity lifecycle)
- ✅ Signature won't change within session

**Trade-offs**:
- ❌ Potential security window (5 minutes) if app is reinstalled with different cert

**Mitigation**: 5-minute TTL is short enough; also check cache validity on app update events.

---

## Quality Attributes

### Performance

| Metric | Target | Current | Status |
|--------|--------|---------|--------|
| **Signature Verification** | <10ms | ~3ms (cached) | ✅ Met |
| **Service Binding** | <100ms | ~50ms | ✅ Met |
| **LLM Inference (3B)** | <2s (first token) | ~1.5s | ✅ Met |
| **ASR (Real-time)** | <200ms latency | ~150ms | ✅ Met |
| **Memory Usage (Idle)** | <100MB | ~80MB | ✅ Met |
| **Memory Usage (LLM Loaded)** | <3GB | ~2.5GB | ✅ Met |

### Scalability

- **Concurrent Clients**: 50 (LRU cache size)
- **Model Loading**: 1 model per capability (LLM, VLM, ASR, TTS)
- **Request Queue**: 100 requests (beyond this, return BUSY error)

### Reliability

- **Uptime Target**: 99.9% (excluding device reboots)
- **Crash Rate**: <0.1% per session
- **Recovery**: Automatic service restart on crash (Android OS)

### Security

- **Signature Verification**: 100% of bindings checked
- **Audit Logging**: 100% of unauthorized attempts logged
- **Data Leakage**: 0% (no network, no logging of user content)

### Maintainability

- **Test Coverage**: 85% (security-critical paths)
- **Code Quality**: Kotlin lint (0 errors, <10 warnings)
- **Documentation**: 100% of public APIs documented

---

**Document Version**: 1.0
**Maintained By**: BreezeApp Team
**Last Updated**: 2025-11-03
