# API Evolution Strategy

**Purpose**: Long-term strategy for evolving the AIDL API while maintaining compatibility
**Audience**: Engine maintainers, architecture team
**Last Updated**: 2025-11-03

---

## Table of Contents

1. [Evolution Principles](#evolution-principles)
2. [Versioning Strategy](#versioning-strategy)
3. [Backward Compatibility Techniques](#backward-compatibility-techniques)
4. [Forward Compatibility Techniques](#forward-compatibility-techniques)
5. [API Design Patterns](#api-design-patterns)
6. [Breaking Change Guidelines](#breaking-change-guidelines)
7. [Future API Roadmap](#future-api-roadmap)

---

## Evolution Principles (T144)

### Core Principles for API Evolution

#### 1. **Stability Over Novelty**
- API changes must have strong justification
- Prefer extending existing methods over creating new ones
- Conservative approach to breaking changes

#### 2. **Gradual Migration**
- Support multiple API versions simultaneously during transitions
- Minimum 6-month overlap period
- Clear deprecation timelines

#### 3. **Extensibility by Design**
- Use `Bundle` parameters for future extensibility
- Avoid rigid parameter lists
- Plan for growth in initial design

#### 4. **Client-First Mindset**
- Minimize client-side migration effort
- Provide clear migration paths
- Default behaviors that work for most clients

#### 5. **Semantic Versioning**
- MAJOR: Breaking changes (remove methods, change signatures)
- MINOR: New features (add methods, backward-compatible)
- PATCH: Bug fixes (no API changes)

---

## Versioning Strategy (T145)

### Dual-Track Versioning

BreezeApp-engine uses two versioning schemes:

#### 1. AIDL API Version (Integer)

```java
interface IAIEngineService {
    const int API_VERSION_1 = 1;   // Initial release (2025-11)
    const int API_VERSION_2 = 2;   // Multi-turn context, VLM (2026-01)
    const int API_VERSION_3 = 3;   // Fine-tuning, quantization (2027-01)

    const int CURRENT_VERSION = 1;  // Updated with each MAJOR API change
}
```

**Increment Rules**:
- Increment for MAJOR changes (breaking)
- Do NOT increment for MINOR changes (backward-compatible additions)
- Clients use this to check compatibility

#### 2. Semantic App Version (MAJOR.MINOR.PATCH)

```
1.0.0 - Initial release
1.1.0 - Add streaming inference (MINOR - backward compatible)
1.2.0 - Add model management (MINOR)
2.0.0 - Remove deprecated methods, add API v2 (MAJOR - breaking)
```

**Mapping**:
- App version 1.x.x → API version 1
- App version 2.x.x → API version 2 (may support API v1 for compatibility)
- App version 3.x.x → API version 3

### Version Compatibility Matrix

| Engine Version | API Version | Supports | Notes |
|----------------|-------------|----------|-------|
| 1.0.0 - 1.4.x | 1 | v1 only | Initial release |
| 1.5.0 - 1.9.x | 1 | v1 only (v2 methods added but unofficial) | Transition |
| 2.0.0 - 2.9.x | 2 | v1 (deprecated), v2 | Overlap period |
| 3.0.0+ | 3 | v2 (deprecated), v3 | v1 removed |

---

## Backward Compatibility Techniques (T146)

### Technique 1: Optional Parameters with Defaults

**Anti-pattern** (breaking change):
```java
// v1
String inferText(String input);

// v2 - BREAKS v1 clients
String inferText(String input, Bundle params, String contextId);
```

**Pattern** (backward compatible):
```java
// v1
String inferText(String input);

// v2 - Add new method, keep old one
String inferText(String input);  // Still works
String inferTextWithContext(String input, Bundle params, String contextId);  // New
```

### Technique 2: Bundle-Based Parameters

**Extensible approach**:
```java
// v1
Bundle inferText(String input, in Bundle params);

// Client v1
Bundle params = new Bundle();
params.putFloat("temperature", 0.7f);
String result = service.inferText(prompt, params).getString("text");

// v2 - Add new parameter without breaking v1
// Client v2 (backward compatible - v1 clients ignore new param)
Bundle params = new Bundle();
params.putFloat("temperature", 0.7f);
params.putString("modelId", "llama-3b");  // NEW parameter
String result = service.inferText(prompt, params).getString("text");
```

**Advantages**:
- Add new parameters without method signature changes
- Old clients ignore unknown keys
- New clients can detect missing keys and use defaults

### Technique 3: Versioned Method Suffixes

```java
interface IAIEngineService {
    // v1
    String inferTextV1(String input, in Bundle params);

    // v2 - New method with suffix
    Bundle inferTextV2(String input, in Bundle params, String contextId);

    // v3 - Future
    Bundle inferTextV3(String input, in Bundle params, in Bundle context);
}
```

**Advantages**:
- Clear versioning
- All versions coexist
- No ambiguity about which version client is using

**Disadvantages**:
- More methods to maintain
- Eventual cleanup needed (deprecate v1, remove in v3)

### Technique 4: Default Implementations

```java
// IAIEngineService.aidl

interface IAIEngineService {
    /**
     * v2 method with default parameters
     */
    Bundle inferText(String input, in Bundle params, String contextId);
}

// Implementation
override fun inferText(input: String?, params: Bundle?, contextId: String?): Bundle {
    // contextId is nullable - v1 clients pass null, v2 clients pass ID
    val actualContextId = contextId ?: generateTemporaryContext()

    // Process inference with context
    // ...
}
```

### Technique 5: Feature Flags in Capabilities

```java
// Client checks what's available
Bundle capabilities = engineService.getCapabilities();

if (capabilities.getBoolean("streaming", false)) {
    // Use streaming inference
} else {
    // Fall back to synchronous
}

if (capabilities.getBoolean("vlm", false)) {
    // VLM available
} else {
    // VLM not available, hide feature in UI
}
```

---

## Forward Compatibility Techniques (T147)

### Technique 1: Reserved Fields in Bundles

```java
// Design for future expansion
Bundle result = new Bundle();
result.putString("text", generatedText);
result.putInt("tokensUsed", tokens);

// Reserved for future use
result.putBundle("metadata", new Bundle());  // Clients can ignore
result.putBundle("_reserved", new Bundle());  // Future expansion

// Future: Add telemetry without breaking old clients
Bundle metadata = result.getBundle("metadata");
metadata.putLong("inferenceTimeMs", duration);
metadata.putString("modelVersion", "1.2.3");
// Old clients ignore metadata, new clients use it
```

### Technique 2: Capability Negotiation

```java
// Client declares capabilities it can handle
Bundle clientCapabilities = new Bundle();
clientCapabilities.putInt("apiVersion", 2);
clientCapabilities.putBoolean("supportsStreaming", true);
clientCapabilities.putBoolean("supportsVLM", false);

// Engine adapts behavior
engineService.registerClientCapabilities(clientCapabilities);

// Engine knows not to send VLM-specific results to this client
```

### Technique 3: Extensible Error Codes

```java
// Error code strategy allows new errors without breaking clients

// v1 error codes
public static final int ERROR_NONE = 0;
public static final int ERROR_MODEL_NOT_LOADED = 1;
public static final int ERROR_INVALID_INPUT = 2;

// v2 adds new error codes (v1 clients see generic error)
public static final int ERROR_CONTEXT_NOT_FOUND = 10;  // v2
public static final int ERROR_QUOTA_EXCEEDED = 11;      // v2

// Client handling
int errorCode = result.getInt("errorCode");
switch (errorCode) {
    case ERROR_NONE: break;
    case ERROR_MODEL_NOT_LOADED: handleModelError(); break;
    case ERROR_INVALID_INPUT: handleInputError(); break;
    default:
        // Unknown error (future error code)
        handleGenericError(result.getString("errorMessage"));
        break;
}
```

---

## API Design Patterns (T148)

### Pattern 1: The Extension Pattern

**Principle**: Never modify existing methods, always add new ones

```java
// ❌ BAD: Modifying existing method
// v1
String getData();

// v2
String getData(int limit);  // BREAKING CHANGE!

// ✅ GOOD: Adding new method
// v1
String getData();

// v2
String getData();  // Keep original
String getDataWithLimit(int limit);  // Add new
```

### Pattern 2: The Delegation Pattern

**Principle**: New methods delegate to internal shared implementation

```java
class AIEngineServiceBinder : IAIEngineService.Stub() {

    override fun inferTextV1(input: String?, params: Bundle?): String {
        // Delegate to shared implementation
        val result = inferTextInternal(input, params, contextId = null)
        return result.getString("text") ?: ""
    }

    override fun inferTextV2(input: String?, params: Bundle?, contextId: String?): Bundle {
        // Delegate to same implementation
        return inferTextInternal(input, params, contextId)
    }

    private fun inferTextInternal(
        input: String?,
        params: Bundle?,
        contextId: String?
    ): Bundle {
        // Actual implementation shared by all versions
        // ...
    }
}
```

### Pattern 3: The Result Wrapper Pattern

**Principle**: Always return Bundle with structured results

```java
// ❌ BAD: Return raw types (not extensible)
String inferText(String input);

// ✅ GOOD: Return Bundle (extensible)
Bundle inferText(String input);

// Bundle structure:
{
    "success": true,
    "text": "Generated text...",
    "metadata": {
        "tokensUsed": 245,
        "inferenceTimeMs": 1250,
        "modelVersion": "1.2.3"
    },
    "errorCode": 0,
    "errorMessage": null
}
```

**Advantages**:
- Can add new fields without breaking changes
- Clients ignore unknown fields
- Consistent structure across all methods

### Pattern 4: The Callback Evolution Pattern

**Principle**: Callbacks should have default empty implementations

```java
// IInferenceCallback.aidl

interface IInferenceCallback {
    void onSuccess(in Bundle result);
    void onError(in Bundle error);

    // v2: Add new callback (clients can ignore)
    void onProgress(in Bundle progress) = default;  // Default: no-op

    // v3: Future callback
    void onMetrics(in Bundle metrics) = default;
}
```

**Note**: AIDL doesn't support default methods natively. Use versioned interfaces:

```java
// v1
interface IInferenceCallback {
    void onSuccess(in Bundle result);
    void onError(in Bundle error);
}

// v2
interface IInferenceCallbackV2 extends IInferenceCallback {
    void onProgress(in Bundle progress);
}

// Engine detects callback version
if (callback instanceof IInferenceCallbackV2) {
    ((IInferenceCallbackV2) callback).onProgress(progress);
}
```

---

## Breaking Change Guidelines (T149)

### When Breaking Changes Are Acceptable

Breaking changes are only acceptable in **MAJOR** version increments (1.x → 2.0):

1. **Removing Long-Deprecated Methods**
   - Method deprecated for ≥6 months
   - Migration guide published
   - Less than 5% active usage

2. **Fixing Critical Security Flaws**
   - Existing API has security vulnerability
   - No backward-compatible fix possible

3. **Fundamental Architecture Changes**
   - Current design prevents critical features
   - Benefits outweigh migration cost

### How to Introduce Breaking Changes

#### Step 1: Announce (N-6 months)
```markdown
## Deprecation Notice (v1.5.0)

The following methods will be removed in v2.0.0 (estimated June 2026):
- `inferTextV1()` → Migrate to `inferTextV2()`

Reason: Lacks multi-turn conversation support.

Migration guide: [link]
```

#### Step 2: Provide Alternatives (N-6 months)
- Release new methods alongside deprecated ones
- Provide migration guide with code examples
- Offer automated migration tools if possible

#### Step 3: Increase Warnings (N-3 months)
```kotlin
// v1.8.0 - 3 months before removal
@Deprecated(level = DeprecationLevel.ERROR)  // Escalate from WARNING
override fun inferTextV1(...): String {
    throw UnsupportedOperationException(
        "inferTextV1() will be removed in v2.0.0 (3 months). " +
        "Please migrate to inferTextV2() immediately."
    )
}
```

#### Step 4: Remove (N months - MAJOR release)
```kotlin
// v2.0.0 - Method completely removed
// inferTextV1() deleted from code
```

#### Step 5: Provide Shim Library (Optional)
```kotlin
// Optional: Publish separate compatibility library
// com.mtkresearch.breezeapp:engine-compat-v1:2.0.0

class EngineV1Compat(private val engineV2: IAIEngineService) {
    fun inferTextV1(input: String, params: Bundle): String {
        // Adapter that converts v1 calls to v2
        val result = engineV2.inferTextV2(input, params, null)
        if (result.getBoolean("success")) {
            return result.getString("text") ?: ""
        } else {
            throw Exception(result.getString("errorMessage"))
        }
    }
}
```

---

## Future API Roadmap

### Planned API Versions

#### API v1 (Current - 2025-11)
- Basic LLM, ASR, TTS inference
- Synchronous and streaming
- Model management

#### API v2 (Planned - 2026-01)
**New Features**:
- Multi-turn conversation context
- Vision-Language Model (VLM) support
- Custom model loading
- Structured error handling (Bundle-based)

**Breaking Changes**:
- inferText() signature change (add contextId parameter)
- Callback interfaces updated (Bundle-based results)

#### API v3 (Planned - 2027-01)
**New Features**:
- On-device fine-tuning
- Model quantization API
- Distributed inference (split across devices)
- Plugin system for custom backends

**Breaking Changes**:
- Remove API v1 methods
- New context management system (session-based)

### Long-Term Vision (2027+)

```
┌─────────────────────────────────────────────────────────────┐
│  Future BreezeApp API Architecture (v4+)                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Modular Plugin System:                                    │
│  • Core AIDL API (minimal, stable)                         │
│  • Plugin interfaces (extensible)                          │
│  • Third-party model providers                             │
│  • Custom inference backends                               │
│                                                             │
│  Federation Support:                                       │
│  • Multi-device inference                                  │
│  • Model sharding                                          │
│  • Collaborative learning                                  │
│                                                             │
│  Advanced Features:                                        │
│  • LoRA adapters                                           │
│  • Mixture-of-experts                                      │
│  • Speculative decoding                                    │
│  • Quantization-aware training                             │
└─────────────────────────────────────────────────────────────┘
```

---

## FAQ

**Q: Can I add new methods in a PATCH release?**
A: No. New methods = MINOR version. PATCH is for bug fixes only (no API changes).

**Q: How do I know if a change is breaking?**
A: If existing client code stops compiling or changes behavior, it's breaking.

**Q: Should I version every method (e.g., inferTextV1, V2, V3)?**
A: For major signature changes, yes. For minor additions, use Bundle extensibility.

**Q: How long should we support old API versions?**
A: Minimum 6 months after deprecation announcement, typically 9-12 months.

---

**Document Version**: 1.0
**Maintained By**: BreezeApp Team
**Last Updated**: 2025-11-03
