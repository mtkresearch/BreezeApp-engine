# API Deprecation Policy

**Purpose**: Define how AIDL methods, parameters, and features are deprecated and removed
**Audience**: Client app developers, engine maintainers
**Last Updated**: 2025-11-03

---

## Table of Contents

1. [Deprecation Principles](#deprecation-principles)
2. [Deprecation Timeline](#deprecation-timeline)
3. [Deprecation Markers](#deprecation-markers)
4. [Deprecated Methods Registry](#deprecated-methods-registry)
5. [Client Migration Support](#client-migration-support)
6. [Removal Process](#removal-process)

---

## Deprecation Principles (T137)

### Core Principles

1. **Gradual Deprecation**: No method is removed immediately. Minimum 6-month deprecation period.

2. **Clear Communication**: Deprecation notices in:
   - AIDL comments
   - Runtime warnings (logcat)
   - Play Store release notes
   - Migration guides
   - GitHub changelog

3. **Backward Compatibility**: Deprecated methods continue to work during deprecation period.

4. **Alternative Provided**: Every deprecated method has a recommended replacement.

5. **Version-Based Lifecycle**:
   - **Introduced**: Method added in version X.Y.Z
   - **Deprecated**: Method marked deprecated in version A.B.C
   - **Removed**: Method removed in version D.E.F (MAJOR version increment)

---

## Deprecation Timeline (T138)

### Standard Deprecation Lifecycle

```
┌──────────────────────────────────────────────────────────────────┐
│  Method Lifecycle Timeline                                       │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Version 1.0.0                Version 1.5.0         Version 2.0.0│
│  │                            │                     │            │
│  ├────── INTRODUCED ──────────┼──── DEPRECATED ────►│            │
│  │                            │                     │  REMOVED   │
│  │  ✅ Stable                 │  ⚠️ Deprecated      │  ❌ Gone    │
│  │  • Full support            │  • Still works      │  • Deleted │
│  │  • No warnings             │  • Runtime warning  │  • Error   │
│  │  • Recommended use         │  • Not recommended  │            │
│  │                            │                     │            │
│  └────────────────────────────┴─────────────────────┴────────────│
│                                                                  │
│  Minimum Deprecation Period: 6 months                           │
│  Typical Deprecation Period: 6-12 months                        │
└──────────────────────────────────────────────────────────────────┘
```

### Deprecation Duration by Change Type

| Change Type | Minimum Period | Typical Period | Removal Version |
|-------------|----------------|----------------|-----------------|
| **Parameter deprecation** | 3 months | 6 months | Next MINOR |
| **Method deprecation** | 6 months | 9 months | Next MAJOR |
| **Interface deprecation** | 9 months | 12 months | Next MAJOR |
| **Feature deprecation** | 12 months | 18 months | Next MAJOR |

### Example Timeline

```
2025-11: v1.0.0 - inferTextV1() introduced
2026-01: v1.5.0 - inferTextV1() deprecated, inferTextV2() added
2026-03: v1.7.0 - Warning increased (log every call)
2026-06: v2.0.0 - inferTextV1() REMOVED
```

---

## Deprecation Markers (T139)

### AIDL Deprecation Annotation

```java
// IAIEngineService.aidl

interface IAIEngineService {

    /**
     * Synchronous text inference (DEPRECATED)
     *
     * @deprecated Since API v2 (engine 1.5.0). Use inferTextV2() instead.
     *             This method will be removed in API v3 (engine 2.0.0).
     *
     * Reason: Lacks context management and structured error handling.
     *
     * Migration:
     *   Before: String result = service.inferTextV1(prompt, params);
     *   After:  Bundle result = service.inferTextV2(prompt, params, contextId);
     *           String text = result.getString("text");
     *
     * @param input User prompt
     * @param params Inference parameters
     * @return Generated text (may throw RemoteException on error)
     */
    @Deprecated
    String inferTextV1(String input, in Bundle params);

    /**
     * Synchronous text inference with context support (CURRENT)
     *
     * @since API v2 (engine 1.5.0)
     * @param input User prompt
     * @param params Inference parameters (must include "modelId")
     * @param contextId Conversation context ID (null for single-turn)
     * @return Bundle with "success", "text", "metadata", "errorCode", "errorMessage"
     */
    Bundle inferTextV2(String input, in Bundle params, String contextId);
}
```

### Kotlin Implementation Markers (T140)

```kotlin
// AIEngineService.kt

class AIEngineServiceBinder : IAIEngineService.Stub() {

    /**
     * DEPRECATED: Use inferTextV2() instead
     *
     * This method is maintained for backward compatibility with API v1 clients.
     * It will be removed in engine version 2.0.0 (June 2026).
     */
    @Deprecated(
        message = "Use inferTextV2() for context support and better error handling",
        replaceWith = ReplaceWith(
            "inferTextV2(input, params, null)",
            "com.mtkresearch.breezeapp.engine.IAIEngineService"
        ),
        level = DeprecationLevel.WARNING  // Will become ERROR in v2.0.0
    )
    override fun inferTextV1(input: String?, params: Bundle?): String {
        // Log deprecation warning
        logDeprecationWarning(
            method = "inferTextV1",
            deprecatedSince = "1.5.0",
            removalVersion = "2.0.0",
            alternative = "inferTextV2"
        )

        // Forward to new implementation
        val result = inferTextV2(input, params, contextId = null)

        // Convert v2 Bundle result to v1 String (for compatibility)
        return if (result.getBoolean("success")) {
            result.getString("text") ?: ""
        } else {
            // v1 throws exceptions, v2 returns errors in Bundle
            throw RemoteException(result.getString("errorMessage"))
        }
    }

    override fun inferTextV2(input: String?, params: Bundle?, contextId: String?): Bundle {
        // Current implementation
        // ...
    }

    private fun logDeprecationWarning(
        method: String,
        deprecatedSince: String,
        removalVersion: String,
        alternative: String
    ) {
        val callingUid = Binder.getCallingUid()
        val packageName = getPackageNameFromUid(callingUid)

        Log.w(
            "DeprecationWarning",
            "⚠️ Client '$packageName' (UID: $callingUid) is using deprecated method '$method'. " +
            "Deprecated since v$deprecatedSince, will be removed in v$removalVersion. " +
            "Please migrate to '$alternative'."
        )

        // Increment deprecation counter for analytics (optional)
        DeprecationMetrics.increment(method, packageName)
    }
}
```

---

## Deprecated Methods Registry (T141)

### Currently Deprecated Methods

| Method | Deprecated In | Removal In | Alternative | Reason |
|--------|---------------|------------|-------------|--------|
| `inferTextV1(String, Bundle)` | v1.5.0 (2026-01) | v2.0.0 (2026-06) | `inferTextV2(String, Bundle, String)` | Lacks context support |
| `inferVision(ParcelFileDescriptor, String, Bundle)` | v1.5.0 | v2.0.0 | `inferVisionV2(...)` | No multi-modal context |
| `listModels(String)` | v1.8.0 | v2.0.0 | `listModelsV2(Bundle)` | Limited filtering options |

### Future Deprecations (Planned)

| Method | Planned Deprecation | Planned Removal | Reason |
|--------|---------------------|-----------------|--------|
| `synthesizeSpeech(String, Bundle)` | v2.2.0 (2026-09) | v3.0.0 (2027-03) | Will add voice cloning support |
| `recognizeSpeech(ParcelFileDescriptor, Bundle)` | v2.5.0 (2027-01) | v3.0.0 | Streaming-first approach |

---

## Client Migration Support (T142)

### Deprecation Notifications

#### 1. Runtime Warnings (Logcat)

```
W/DeprecationWarning: ⚠️ Client 'com.example.myapp' (UID: 10123) is using deprecated method 'inferTextV1'.
W/DeprecationWarning:    Deprecated since v1.5.0, will be removed in v2.0.0.
W/DeprecationWarning:    Please migrate to 'inferTextV2'. See: https://docs.breezeapp.com/migration/v1-to-v2
W/DeprecationWarning:    Migration deadline: 2026-06-01
```

#### 2. Play Store Release Notes

```markdown
Version 1.5.0 - Deprecation Notice

⚠️ DEPRECATION NOTICE
The following methods are now deprecated and will be removed in v2.0.0 (June 2026):
- inferTextV1() → Use inferTextV2()
- inferVision() → Use inferVisionV2()
- listModels(String) → Use listModelsV2(Bundle)

Migration guide: https://docs.breezeapp.com/migration/v1-to-v2

Update your client app before June 2026 to avoid breaking changes.
```

#### 3. In-App Developer Notifications (Optional)

```kotlin
// Engine can optionally notify client developers via callback

interface IDeprecationListener extends IInterface {
    void onDeprecatedMethodUsed(String methodName, String removalVersion, String migrationUrl);
}

// Client registers listener (optional)
engineService.registerDeprecationListener(listener)

// Engine calls listener when deprecated method used
listener.onDeprecatedMethodUsed(
    "inferTextV1",
    "2.0.0",
    "https://docs.breezeapp.com/migration/v1-to-v2#infertext-migration"
)
```

### Migration Assistance Tools (T143)

#### Automated Migration Script

```kotlin
/**
 * Migration Helper - Automatically adapts v1 calls to v2
 *
 * Usage:
 *   val helper = MigrationHelper(engineService)
 *   val result = helper.inferText(prompt, params)  // Automatically uses v2 if available
 */
class MigrationHelper(private val engineService: IAIEngineService) {

    private val apiVersion = engineService.version
    private val useV2 = apiVersion >= 2

    fun inferText(prompt: String, params: Bundle): String {
        return if (useV2) {
            // Use v2 API
            val result = engineService.inferTextV2(prompt, params, null)
            if (result.getBoolean("success")) {
                result.getString("text") ?: ""
            } else {
                throw Exception(result.getString("errorMessage"))
            }
        } else {
            // Fall back to v1 API
            @Suppress("DEPRECATION")
            engineService.inferTextV1(prompt, params)
        }
    }

    fun isFeatureAvailable(feature: String): Boolean {
        return when (feature) {
            "multi_turn_context" -> useV2
            "vlm" -> useV2
            "custom_models" -> useV2
            else -> true  // Basic features available in v1
        }
    }
}
```

#### Deprecation Linter (Android Studio Plugin - Future)

```
// Hypothetical Android Studio lint rule

DeprecatedAidlMethodDetector:
  - Detects calls to deprecated AIDL methods in client code
  - Shows warning with migration suggestion
  - Provides quick-fix to auto-migrate to new method

Example Warning:
┌──────────────────────────────────────────────────────────┐
│ ⚠️ Deprecated Method                                     │
├──────────────────────────────────────────────────────────┤
│ inferTextV1() is deprecated since BreezeApp Engine 1.5.0 │
│ and will be removed in 2.0.0.                            │
│                                                          │
│ Recommended: Use inferTextV2() instead                   │
│                                                          │
│ [Quick Fix: Migrate to V2] [Learn More] [Ignore]        │
└──────────────────────────────────────────────────────────┘
```

---

## Removal Process

### Pre-Removal Checklist (MAJOR Version Release)

Before removing deprecated methods in a MAJOR version:

- [ ] Deprecation period ≥ 6 months completed
- [ ] Migration guide published and tested
- [ ] All first-party apps (BreezeApp, BreezeApp Dot) migrated
- [ ] Release notes clearly state breaking changes
- [ ] Play Store description updated with compatibility info
- [ ] Deprecated methods logged usage metrics (track adoption)
- [ ] Less than 5% of active clients still using deprecated methods

### Removal Implementation

```kotlin
// v1.5.0 - Method deprecated
@Deprecated(message = "...", level = DeprecationLevel.WARNING)
override fun inferTextV1(...): String { ... }

// v1.9.0 - Increase deprecation level (3 months before removal)
@Deprecated(message = "...", level = DeprecationLevel.ERROR)
override fun inferTextV1(...): String {
    throw UnsupportedOperationException(
        "inferTextV1() is no longer supported. " +
        "This method will be completely removed in engine v2.0.0. " +
        "Please update to inferTextV2(). " +
        "See migration guide: https://docs.breezeapp.com/migration/v1-to-v2"
    )
}

// v2.0.0 - Method completely removed
// inferTextV1() deleted from AIDL and implementation
```

### Rollback Plan

If too many users are affected after MAJOR release:

1. **Option 1: Delay Removal**
   - Keep deprecated method for one more MAJOR version cycle
   - Increase communication efforts

2. **Option 2: Re-introduce as Shim**
   - Add back deprecated method as thin wrapper
   - Log aggressive warnings
   - Remove in next MAJOR version

3. **Option 3: Provide Legacy Compatibility Library**
   - Separate AAR library with adapters
   - Clients opt-in to legacy support
   - Not maintained long-term

---

## FAQ

**Q: What if I can't migrate within 6 months?**
A: Contact breezeapp-support@mtkresearch.com. We may extend support for enterprise clients.

**Q: Will deprecated methods still work during the deprecation period?**
A: Yes, they will continue to function normally with only log warnings.

**Q: How do I know which methods are deprecated?**
A: Check the migration guide, release notes, or use Android Studio's deprecation warnings.

**Q: Can I disable deprecation warnings?**
A: No, warnings are important for migration planning. Migrate to the new API instead.

---

**Document Version**: 1.0
**Maintained By**: BreezeApp Team
**Last Updated**: 2025-11-03
