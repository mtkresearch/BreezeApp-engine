# BreezeApp-engine Deployment Architecture

**Purpose**: Physical deployment topology, infrastructure requirements, and operational considerations
**Audience**: DevOps engineers, system administrators, deployment managers
**Last Updated**: 2025-11-03

---

## Table of Contents

1. [Deployment Overview](#deployment-overview)
2. [Physical Deployment Topology](#physical-deployment-topology)
3. [Process Architecture](#process-architecture)
4. [Resource Allocation](#resource-allocation)
5. [Storage Architecture](#storage-architecture)
6. [Network Architecture](#network-architecture)
7. [Multi-APK Deployment Strategy](#multi-apk-deployment-strategy)
8. [Certificate Management](#certificate-management)
9. [Update Strategy](#update-strategy)
10. [Monitoring & Health Checks](#monitoring--health-checks)

---

## Deployment Overview (T070)

### Deployment Model

BreezeApp-engine follows a **service-oriented deployment model** where a single APK is installed on Android devices and provides services to multiple client applications.

```
┌──────────────────────────────────────────────────────────────┐
│                    Android Device                            │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │            Application Layer (Client APKs)             │ │
│  │  ┌───────────────┐ ┌───────────────┐ ┌──────────────┐ │ │
│  │  │   BreezeApp   │ │ companion apps │ │ Third-Party  │ │ │
│  │  │   (Main UI)   │ │ (Voice UI)    │ │  Apps        │ │ │
│  │  └───────┬───────┘ └───────┬───────┘ └──────┬───────┘ │ │
│  └──────────┼─────────────────┼─────────────────┼─────────┘ │
│             │                 │                 │           │
│             └────────── AIDL Binding ───────────┘           │
│                         │                                   │
│  ┌──────────────────────▼───────────────────────────────┐  │
│  │          Service Layer (Engine APK)                  │  │
│  │  ┌────────────────────────────────────────────────┐  │  │
│  │  │  BreezeApp AI Engine Service                   │  │  │
│  │  │  • Process: :ai_engine (isolated)              │  │  │
│  │  │  • Memory: 2-4GB (dynamic)                     │  │  │
│  │  │  │  • Lifecycle: Bound to client connections    │  │  │
│  │  └────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────┘  │
│                         │                                   │
│  ┌──────────────────────▼───────────────────────────────┐  │
│  │            Infrastructure Layer                       │  │
│  │  • Android OS (API 34+)                              │  │
│  │  • File System (/data/data/...)                     │  │
│  │  • Hardware (NPU, GPU, CPU)                         │  │
│  └──────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

### Key Deployment Characteristics

| Characteristic | Value | Notes |
|----------------|-------|-------|
| **Deployment Unit** | Single APK | `BreezeApp-engine.apk` |
| **Installation Method** | Google Play Store, APK sideload | Primary: Play Store |
| **Update Frequency** | 2-4 weeks (stable), as-needed (hotfix) | Semantic versioning |
| **Rollout Strategy** | Staged rollout (5%→100% over 7-14 days) | MAJOR releases slower |
| **Target Devices** | Android 14+ smartphones/tablets | Minimum 4GB RAM |
| **Geographic Distribution** | Global (Play Store worldwide) | Initial: US, Taiwan |
| **Certificate Strategy** | Play App Signing (Google-managed) | Same cert for all ecosystem apps |

---

## Physical Deployment Topology (T071)

### Single-Device Topology

```
┌───────────────────────────────────────────────────────────────┐
│  Physical Android Device (e.g., Pixel 8, MediaTek Dimensity)  │
├───────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  User Space                                             │ │
│  │                                                         │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │ │
│  │  │  UID: 10101  │  │  UID: 10102  │  │  UID: 10103  │ │ │
│  │  │              │  │              │  │              │ │ │
│  │  │  BreezeApp   │  │ BreezeApp    │  │  Engine      │ │ │
│  │  │  (Main)      │  │ Client       │  │  Service     │ │ │
│  │  │              │  │              │  │              │ │ │
│  │  │  Process:    │  │  Process:    │  │  Process:    │ │ │
│  │  │  main        │  │  main        │  │  :ai_engine  │ │ │
│  │  │              │  │              │  │              │ │ │
│  │  │  Memory:     │  │  Memory:     │  │  Memory:     │ │ │
│  │  │  ~200MB      │  │  ~150MB      │  │  ~3GB        │ │ │
│  │  └──────┬───────┘  └──────┬───────┘  └──────▲───────┘ │ │
│  │         │                 │                 │         │ │
│  │         └─────── Binder IPC ────────────────┘         │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  Kernel Space                                           │ │
│  │  • Binder driver (/dev/binder)                         │ │
│  │  • Process scheduler (CFS)                             │ │
│  │  • Memory management (LMKD)                            │ │
│  │  • Filesystem (ext4, F2FS)                             │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  Hardware Layer                                         │ │
│  │  • CPU: 8 cores (2x A78 + 6x A55)                      │ │
│  │  • NPU: MediaTek APU (optional)                        │ │
│  │  • RAM: 8GB LPDDR5                                     │ │
│  │  • Storage: 128GB UFS 3.1                              │ │
│  └─────────────────────────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────────┘
```

### Multi-Device Distribution

```
┌─────────────────────────────────────────────────────────────┐
│                Google Play Store Infrastructure             │
│                                                             │
│  ┌────────────────────────────────────────────────────────┐│
│  │  Play Console (Developer Upload)                       ││
│  │  • Upload signed APK/AAB                              ││
│  │  • Configure staged rollout                           ││
│  │  • Monitor crash reports                              ││
│  └───────────────────┬────────────────────────────────────┘│
│                      │                                      │
│  ┌───────────────────▼────────────────────────────────────┐│
│  │  Play App Signing (Certificate Management)            ││
│  │  • Sign APK with production certificate               ││
│  │  • Manage signing keys securely                       ││
│  └───────────────────┬────────────────────────────────────┘│
│                      │                                      │
│  ┌───────────────────▼────────────────────────────────────┐│
│  │  Content Delivery Network (Global CDN)                ││
│  │  • Americas: us-east, us-west, sa-east                ││
│  │  • Europe: eu-west, eu-central                        ││
│  │  • Asia-Pacific: ap-southeast, ap-northeast           ││
│  └───────────────────┬────────────────────────────────────┘│
└────────────────────┼───────────────────────────────────────┘
                      │
          ┌───────────┼──────────┐
          │           │          │
    ┌─────▼───┐ ┌─────▼───┐ ┌────▼────┐
    │ Device  │ │ Device  │ │ Device  │
    │ (US)    │ │ (TW)    │ │ (EU)    │
    └─────────┘ └─────────┘ └─────────┘
    Pixel 8     Dimensity   Galaxy S24
    100,000     50,000      20,000
    users       users       users
```

---

## Process Architecture (T072)

### Process Isolation Model

```
Android System (PID: 1)
│
├─ system_server (PID: 500)
│   └─ ActivityManagerService
│       ├─ Manages app lifecycle
│       └─ Enforces permissions
│
├─ com.mtkresearch.breezeapp (UID: 10101, PID: 1234)
│   └─ Main process
│       ├─ Activities, Fragments
│       ├─ ViewModels
│       └─ EngineClient (binds to engine)
│
├─ com.mtkresearch.breezeapp.client (UID: 10102, PID: 1235)
│   └─ Main process
│       ├─ Voice UI
│       └─ EngineClient (binds to engine)
│
└─ com.mtkresearch.breezeapp.engine (UID: 10103)
    ├─ Main process (PID: 1236)
    │   └─ Minimal - just registers service
    │
    └─ :ai_engine process (PID: 1237)
        ├─ AIEngineService
        ├─ LLMManager
        ├─ VLMManager
        ├─ ASRManager
        ├─ TTSManager
        └─ AI model runtimes (ExecuTorch, Sherpa)
```

### Process Lifecycle

```
┌─────────────────────────────────────────────────────────────┐
│  Engine Process Lifecycle States                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌───────────┐                                             │
│  │  NOT      │  First client binds                         │
│  │  STARTED  ├─────────────────────────────────┐           │
│  └───────────┘                                 │           │
│                                                 ▼           │
│                                         ┌───────────────┐   │
│                                         │  CREATED      │   │
│                                         │  • onCreate() │   │
│                                         └───────┬───────┘   │
│                                                 │           │
│                                Client binds     │           │
│                                                 ▼           │
│                                         ┌───────────────┐   │
│                                         │  BOUND        │   │
│                                         │  • onBind()   │   │
│  ┌────────────────┐                    │  • Active     │   │
│  │  DESTROYED     │<───────────────────┤    clients: 1 │   │
│  │  • onDestroy() │  All clients       └───────┬───────┘   │
│  └────────────────┘  unbind & timeout          │           │
│                                                 │           │
│                          More clients bind      │           │
│                          ┌──────────────────────┘           │
│                          │                                  │
│                          ▼                                  │
│                  ┌───────────────┐                          │
│                  │  BOUND        │                          │
│                  │  (Multiple)   │                          │
│                  │  • Active     │                          │
│                  │    clients: N │                          │
│                  └───────────────┘                          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Memory Management

**Low Memory Killer Daemon (LMKD) Priority**:

```
Priority Level     │ Process Type               │ Action
───────────────────┼────────────────────────────┼─────────────────
FOREGROUND_APP     │ Active UI app              │ Never kill
(0-100)            │ (BreezeApp when visible)   │
                   │                            │
VISIBLE_APP        │ Visible but not foreground │ Kill if critical
(100-200)          │ (BreezeApp in background   │ low memory
                   │  with active inference)    │
                   │                            │
SERVICE            │ Bound service with clients │ Kill only if
(200-300)          │ (Engine :ai_engine)        │ very low memory
                   │                            │ (< 200MB free)
                   │                            │
CACHED_APP         │ No active components       │ Kill first
(900-1000)         │ (BreezeApp fully closed)   │ when memory low
```

**Engine Service Protection**:
- When clients are bound → **SERVICE** priority (high)
- When no clients → **CACHED_APP** priority (low, killed first)
- **Foreground service** notification keeps it alive during inference

---

## Resource Allocation (T073)

### CPU Allocation

```
┌─────────────────────────────────────────────────────────────┐
│  CPU Core Allocation Strategy                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  BIG Cores (Performance)  │  LITTLE Cores (Efficiency)     │
│  • 2x Cortex-A78          │  • 6x Cortex-A55               │
│  • 2.85 GHz               │  • 2.0 GHz                     │
│                           │                                │
│  ┌──────────────────────┐ │  ┌──────────────────────────┐  │
│  │  LLM Inference       │ │  │  Background tasks        │  │
│  │  • Tokenization      │ │  │  • AIDL IPC              │  │
│  │  │  • Matrix ops       │ │  │  • File I/O              │  │
│  │  • Attention         │ │  │  • Logging               │  │
│  │  (Compute-heavy)     │ │  │  (Light-weight)          │  │
│  └──────────────────────┘ │  └──────────────────────────┘  │
│                           │                                │
│  ┌──────────────────────┐ │                                │
│  │  NPU (if available)  │ │                                │
│  │  • MediaTek APU      │ │                                │
│  │  • Offload inference │ │                                │
│  │  • -40% CPU usage    │ │                                │
│  └──────────────────────┘ │                                │
└─────────────────────────────────────────────────────────────┘
```

**CPU Affinity Configuration**:
```kotlin
// Set thread affinity to BIG cores for inference
val bigCoreMask = 0b00000011  // Cores 0-1 (BIG)
Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
// Native code: pthread_setaffinity_np() to pin threads
```

### Memory Allocation

```
┌─────────────────────────────────────────────────────────────┐
│  Memory Allocation Breakdown (Typical 3B LLM Model)        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Total Device RAM: 8GB                                     │
│  ├─ System Reserved: ~2GB (Android OS)                    │
│  ├─ Other Apps: ~2GB (background apps)                    │
│  └─ Available for Engine: ~4GB                            │
│                                                             │
│  Engine Process Breakdown:                                 │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  :ai_engine process (max heap: 4GB)                 │   │
│  │                                                     │   │
│  │  ├─ LLM Model Weights: ~2.5GB                      │   │
│  │  │   • Q4 quantization (4-bit per weight)          │   │
│  │  │   • 3B parameters × 4 bits = ~1.5GB             │   │
│  │  │   • Embeddings + overhead: +1GB                 │   │
│  │  │                                                  │   │
│  │  ├─ KV Cache: ~512MB                               │   │
│  │  │   • Stores attention key-value pairs            │   │
│  │  │   • Grows with context length                   │   │
│  │  │                                                  │   │
│  │  ├─ Input/Output Buffers: ~128MB                   │   │
│  │  │   • Tokenizer buffers                           │   │
│  │  │   • Inference queue                             │   │
│  │  │                                                  │   │
│  │  ├─ ExecuTorch Runtime: ~256MB                     │   │
│  │  │   • Runtime overhead                            │   │
│  │  │   • Graph execution engine                      │   │
│  │  │                                                  │   │
│  │  └─ Other (Sherpa, system): ~256MB                 │   │
│  │      • ASR/TTS models (loaded on-demand)           │   │
│  │      • Java heap, native allocations               │   │
│  │                                                     │   │
│  │  Total: ~3.6GB (leaving ~400MB headroom)           │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

**Heap Size Configuration** (`AndroidManifest.xml`):
```xml
<application
    android:largeHeap="true">  <!-- Increases max heap to ~512MB -->
    <!-- Native memory (AI models) allocated outside Java heap -->
</application>
```

### Storage Allocation

```
/data/data/com.mtkresearch.breezeapp.engine/
├─ files/                       [3.5GB total]
│  ├─ models/                   [3.2GB]
│  │  ├─ llm/
│  │  │  ├─ llama-3b-q4.pte    [2.5GB]  ← Largest file
│  │  │  └─ llama-7b-q8.pte    [~7GB]   ← Optional
│  │  ├─ asr/
│  │  │  └─ sherpa-*.onnx      [~200MB]
│  │  └─ tts/
│  │     └─ vits-*.onnx        [~150MB]
│  ├─ cache/                    [~200MB, temporary]
│  │  └─ inference_cache/
│  └─ audit/                    [~50MB, 30-day logs]
│     └─ audit-2025-11.jsonl
├─ shared_prefs/                [~1MB]
│  └─ engine_config.xml
└─ code_cache/                  [~50MB, R8/dex cache]
```

---

## Storage Architecture (T074)

### File System Layout

```
Device Storage
│
├─ /data/data/com.mtkresearch.breezeapp.engine/
│  │  [App-private directory, accessible only to engine UID]
│  │
│  ├─ files/ [Application files]
│  │  ├─ models/ [AI model binaries]
│  │  │  ├─ llm/
│  │  │  │  ├─ llama-3b-q4.pte
│  │  │  │  └─ manifest.json [Model metadata]
│  │  │  ├─ vlm/
│  │  │  ├─ asr/
│  │  │  └─ tts/
│  │  │
│  │  ├─ cache/ [Temporary inference data]
│  │  │  ├─ kv_cache/ [Attention cache for sessions]
│  │  │  └─ tts_output/ [Temporary audio files]
│  │  │
│  │  └─ audit/ [Security audit logs]
│  │     ├─ audit-2025-11-01.jsonl
│  │     ├─ audit-2025-11-02.jsonl
│  │     └─ ... [Auto-deleted after 30 days]
│  │
│  ├─ shared_prefs/ [Configuration]
│  │  └─ engine_config.xml
│  │     • Last used model
│  │     • Performance settings
│  │     • Backend selection (CPU/NPU)
│  │
│  ├─ databases/ [Future: Model metadata DB]
│  │  └─ models.db
│  │
│  └─ code_cache/ [Dex/R8 cache]
│     └─ ... [Android-managed]
│
├─ /storage/emulated/0/ [External storage - NOT USED]
│  [Engine doesn't access external storage for privacy]
│
└─ /data/app/com.mtkresearch.breezeapp.engine-*.apk
   [APK installation directory - read-only]
```

### Storage Security

**File Permissions**:
```bash
# App-private directory (Linux permissions)
drwx------  /data/data/com.mtkresearch.breezeapp.engine/
# Owner: u0_a103 (engine UID)
# Group: u0_a103
# Mode: 0700 (owner read/write/execute only)

# Model files
-rw-------  /data/data/.../files/models/llm/llama-3b-q4.pte
# Mode: 0600 (owner read/write only)
```

**Prevents**:
- ❌ Other apps cannot read model files (different UID)
- ❌ Other apps cannot read audit logs
- ❌ Model files not accessible via USB (unless device rooted)

**Access Control**:
```kotlin
// When saving files, set restrictive permissions
File(filesDir, "models/llm/model.pte").apply {
    setReadable(false, false)  // Not readable by others
    setWritable(false, false)  // Not writable by others
    setExecutable(false, false) // Not executable by others
    setReadable(true, true)    // Readable by owner only
    setWritable(true, true)    // Writable by owner only
}
```

### Cache Management Strategy

```
┌─────────────────────────────────────────────────────────────┐
│  Cache Lifecycle Management                                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Inference Cache (KV Cache):                               │
│  ├─ Created: When conversation starts                      │
│  ├─ Lifetime: Duration of conversation session             │
│  ├─ Size: Grows with context (max 2048 tokens → ~512MB)   │
│  └─ Cleanup: Deleted when client unbinds                   │
│                                                             │
│  TTS Output Cache:                                         │
│  ├─ Created: For each TTS request                          │
│  ├─ Lifetime: Until client reads audio                     │
│  ├─ Size: ~1-10MB per request                             │
│  └─ Cleanup: Deleted after 1 hour or on app restart       │
│                                                             │
│  Audit Logs:                                               │
│  ├─ Created: Daily (rotated at midnight)                   │
│  ├─ Lifetime: 30 days                                      │
│  ├─ Size: ~1-5MB per day                                  │
│  └─ Cleanup: Auto-delete logs older than 30 days          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**Cleanup Implementation**:
```kotlin
// Triggered on service startup and daily
fun cleanupOldCaches() {
    // Delete old audit logs (30+ days)
    val auditDir = File(filesDir, "audit")
    val cutoffTime = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
    auditDir.listFiles()?.forEach { file ->
        if (file.lastModified() < cutoffTime) {
            file.delete()
        }
    }

    // Delete orphaned TTS files (1+ hour old)
    val ttsDir = File(filesDir, "cache/tts_output")
    val ttsCtoffTime = System.currentTimeMillis() - (60 * 60 * 1000L)
    ttsDir.listFiles()?.forEach { file ->
        if (file.lastModified() < ttsCtoffTime) {
            file.delete()
        }
    }
}
```

---

## Network Architecture

### Offline-First Design

```
┌─────────────────────────────────────────────────────────────┐
│  Network Connectivity (Optional)                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Core Functionality: NO NETWORK REQUIRED                   │
│  • AI inference: 100% on-device                            │
│  • Model loading: From local storage                       │
│  • AIDL communication: Local IPC (Binder)                  │
│                                                             │
│  Optional Network Usage:                                   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Model Download Service (Future Feature)           │   │
│  │  • User-initiated only                             │   │
│  │  │  • Download new models from CDN                   │   │
│  │  • HTTPS only (no plaintext)                       │   │
│  │  • WiFi-only by default (data saver)               │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  No Outbound Connections:                                  │
│  ❌ No telemetry                                           │
│  ❌ No analytics                                           │
│  ❌ No crash reporting (unless opt-in)                     │
│  ❌ No ad networks                                         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**Manifest Declaration** (shows network is optional):
```xml
<!-- Internet permission declared but NOT required -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-feature
    android:name="android.hardware.wifi"
    android:required="false" />  <!-- Works without WiFi -->
```

---

## Multi-APK Deployment Strategy

### Ecosystem Deployment

```
┌─────────────────────────────────────────────────────────────┐
│  BreezeApp Ecosystem Deployment (Google Play Store)        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Package 1: com.mtkresearch.breezeapp.engine               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  BreezeApp AI Engine (Service)                      │   │
│  │  • Category: Tools                                  │   │
│  │  • Version: 1.0.0                                   │   │
│  │  • Size: ~120MB (APK + models)                     │   │
│  │  • Dependencies: None                               │   │
│  │  • Required by: BreezeApp, companion apps           │   │
│  └─────────────────────────────────────────────────────┘   │
│                      ▲                                      │
│                      │ Dependency                           │
│  ┌───────────────────┴──────────────────┐                  │
│  │                                      │                  │
│  ▼                                      ▼                  │
│  Package 2:                   Package 3:                   │
│  com.mtkresearch.breezeapp    com.mtkresearch.breezeapp.client│
│  ┌────────────────────────┐  ┌────────────────────────┐    │
│  │  BreezeApp (Main UI)   │  │  companion apps (Voice) │    │
│  │  • Category: Productivity│  │  • Category: Tools     │    │
│  │  • Version: 1.0.0      │  │  • Version: 1.0.0      │    │
│  │  • Size: ~50MB         │  │  • Size: ~30MB         │    │
│  │  • Requires: Engine    │  │  • Requires: Engine    │    │
│  └────────────────────────┘  └────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

### Installation Flow

```
User Journey:
1. User discovers BreezeApp in Play Store
2. User clicks "Install"
3. Play Store checks dependencies:
   - BreezeApp requires com.mtkresearch.breezeapp.engine
4. Play Store prompts:
   "This app requires BreezeApp AI Engine. Install both?"
   [Install Both] [Cancel]
5. User accepts → Both APKs installed
6. User opens BreezeApp → Works immediately (engine already installed)

Alternative Flow (Engine-first):
1. Developer user discovers Engine
2. Installs Engine standalone
3. Sees in description: "Install BreezeApp to use this service"
4. Clicks BreezeApp link → Installs companion app
```

**Dependency Declaration** (BreezeApp manifest):
```xml
<!-- Not native Android feature, but Play Console metadata -->
<!-- Communicated via app description and deep links -->
```

---

## Certificate Management

### Production Certificate Strategy

```
┌─────────────────────────────────────────────────────────────┐
│  Play App Signing Architecture                             │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Developer Environment:                                    │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Upload Key (Developer-managed)                     │   │
│  │  • RSA 2048-bit                                     │   │
│  │  • Stored in keystore (password-protected)          │   │
│  │  • Used to sign APK before upload                   │   │
│  │  • SHA-256: ABC123... (upload key fingerprint)     │   │
│  └────────────────────┬────────────────────────────────┘   │
│                       │ Sign & Upload                       │
│                       ▼                                     │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Google Play Console                                │   │
│  │  • Receives signed APK                              │   │
│  │  • Verifies upload key signature                    │   │
│  │  • Strips upload key signature                      │   │
│  └────────────────────┬────────────────────────────────┘   │
│                       │ Re-sign with App Signing Key        │
│                       ▼                                     │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  App Signing Key (Google-managed)                   │   │
│  │  • RSA 2048-bit (stored in Google HSM)             │   │
│  │  • Cannot be exported                               │   │
│  │  • Same key for all BreezeApp ecosystem apps        │   │
│  │  • SHA-256: XYZ789... (production fingerprint)     │   │
│  └────────────────────┬────────────────────────────────┘   │
│                       │ Sign for distribution               │
│                       ▼                                     │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Signed APK (Distributed to users)                  │   │
│  │  • Signature matches App Signing Key               │   │
│  │  • All ecosystem apps have identical signature      │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### Signature Verification Update

**Development**:
```kotlin
// SignatureValidator.kt (dev environment)
private val AUTHORIZED_SIGNATURES = setOf(
    "DEBUG_CERTIFICATE_SHA256_HASH"  // Debug keystore
)
```

**Production**:
```kotlin
// SignatureValidator.kt (production release)
private val AUTHORIZED_SIGNATURES = setOf(
    "XYZ789..."  // Production App Signing Key SHA-256
)
// Update this value after first Play Store upload
```

**Obtaining Production Fingerprint**:
```bash
# After first upload to Play Console
# Go to: Play Console → Setup → App signing
# Copy "SHA-256 certificate fingerprint"
# Example: AB:CD:EF:12:34:56:78:90:...
# Remove colons → update AUTHORIZED_SIGNATURES
```

---

## Update Strategy

### Semantic Versioning

```
Version Format: MAJOR.MINOR.PATCH (e.g., 1.2.3)

MAJOR (1.x.x):
  • Breaking AIDL changes
  • Incompatible with old clients
  • Example: AIDL API v2 (new required parameters)

MINOR (x.1.x):
  • New features (backward compatible)
  • New AIDL methods (with default implementations)
  • Example: Add VLM support

PATCH (x.x.1):
  • Bug fixes
  • Security patches
  • No AIDL changes
  • Example: Fix crash on Android 14.1
```

### Staged Rollout Strategy

```
┌─────────────────────────────────────────────────────────────┐
│  Rollout Timeline (MINOR Release Example)                  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Day 0:   5% of users (Alpha testers)                      │
│  ├─ Monitor: Crash rate, performance metrics              │
│  └─ Halt if: Crash rate > 0.5%                            │
│                                                             │
│  Day 2:   20% of users                                     │
│  ├─ Expand to early adopters                              │
│  └─ Monitor: User reviews, compatibility                   │
│                                                             │
│  Day 4:   50% of users                                     │
│  ├─ Mainstream rollout begins                             │
│  └─ Monitor: Server load (model downloads if applicable)   │
│                                                             │
│  Day 7:   100% of users                                    │
│  └─ Full availability                                      │
│                                                             │
│  MAJOR releases: Slower (14-day rollout)                   │
│  PATCH releases: Faster (5%→100% in 3 days)               │
│  Hotfix: Emergency (100% within 24 hours)                  │
└─────────────────────────────────────────────────────────────┘
```

---

## Monitoring & Health Checks

### Health Check Endpoint

```kotlin
// Available via AIDL
override fun getHealthStatus(): Bundle {
    return Bundle().apply {
        putBoolean("healthy", true)
        putLong("uptime", SystemClock.eluptedRealtime())
        putInt("activeConnections", getActiveClientCount())
        putLong("memoryUsage", Runtime.getRuntime().totalMemory())
        putLong("freeMemory", Runtime.getRuntime().freeMemory())
        putFloat("cpuUsage", getCpuUsage())
        putString("loadedModels", getLoadedModelIds().joinToString(","))
        putLong("lastInferenceTime", getLastInferenceTimestamp())
        putInt("inferenceQueueSize", getQueueSize())
    }
}
```

### Metrics Collection (Optional, Opt-in)

```
If user opts into crash reporting:
  • Firebase Crashlytics (crash logs only)
  • No PII (personally identifiable information)
  • Only technical telemetry:
    - Crash stack traces
    - Device model, Android version
    - Engine version
    - Memory state at crash

No usage analytics:
  ❌ No inference content logging
  ❌ No user behavior tracking
  ❌ No performance profiling (unless user-initiated)
```

---

**Document Version**: 1.0
**Maintained By**: BreezeApp Team
**Last Updated**: 2025-11-03
