# AIDL Interface Versioning Strategy

**Version**: 1.0
**Last Updated**: 2025-11-03
**Status**: Implementation Ready

## Overview

This document defines the versioning strategy for BreezeApp-engine's AIDL interfaces, ensuring backward compatibility and graceful version detection between engine and client applications.

## Versioning Approach

BreezeApp-engine uses a **dual-strategy versioning system**:

1. **Compile-time**: AIDL interface version constants
2. **Runtime**: Service version query methods

This allows clients to detect version mismatches and adapt behavior accordingly.

---

## AIDL Version Constants (T045-T046)

### Version Declaration in IAIEngineService.aidl

```java
interface IAIEngineService {
    // Version constants
    const int API_VERSION_1_0 = 1;   // Initial release: LLM, ASR, TTS
    const int API_VERSION_2_0 = 2;   // Added: Streaming, VLM
    const int CURRENT_VERSION = 2;    // Current API version

    // Core versioning methods
    int getVersion();                 // Returns CURRENT_VERSION
    Bundle getVersionInfo();          // Returns detailed version info
}
```

### Version Naming Convention

| Constant | Value | Features |
|----------|-------|----------|
| `API_VERSION_1_0` | 1 | LLM, ASR, TTS (basic) |
| `API_VERSION_2_0` | 2 | + Streaming, VLM, model management |
| `API_VERSION_3_0` | 3 | (Future) Additional features |
| `CURRENT_VERSION` | 2 | Always points to latest version |

### When to Increment Version

| Change Type | Version Increment | Example |
|------------|------------------|---------|
| **Add new method** | MINOR (1 → 2) | Added `inferTextStreaming()` |
| **Add default parameter** | MINOR | Added optional flags parameter |
| **Remove method** | MAJOR (breaking) | Removed deprecated method |
| **Change signature** | MAJOR (breaking) | Changed parameter types |
| **Bug fix** | None | Internal implementation fix |

---

## Runtime Version Checking (T047)

### Client-Side Version Detection

Clients should check the engine version before using advanced features:

```kotlin
class EngineClient(private val context: Context) {

    companion object {
        const val MIN_REQUIRED_VERSION = IAIEngineService.API_VERSION_1_0
        const val PREFERRED_VERSION = IAIEngineService.API_VERSION_2_0
    }

    private fun onServiceConnected(service: IBinder?) {
        val engineService = IAIEngineService.Stub.asInterface(service)

        try {
            val version = engineService.getVersion()

            when {
                version < MIN_REQUIRED_VERSION -> {
                    // Engine too old
                    handleIncompatibleVersion(version)
                }
                version < PREFERRED_VERSION -> {
                    // Engine works but missing some features
                    handleOldVersion(version)
                }
                else -> {
                    // Fully compatible
                    onEngineReady(version)
                }
            }
        } catch (e: RemoteException) {
            handleConnectionError(e)
        }
    }

    private fun handleIncompatibleVersion(version: Int) {
        AlertDialog.Builder(context)
            .setTitle("Update Required")
            .setMessage("Engine version $version is incompatible.\nRequired: $MIN_REQUIRED_VERSION+")
            .setPositiveButton("Update") { _, _ ->
                openPlayStore("com.mtkresearch.breezeapp.engine")
            }
            .setCancelable(false)
            .show()
    }
}
```

### Feature Detection

```kotlin
fun checkFeatureSupport(version: Int): FeatureSet {
    return when {
        version >= IAIEngineService.API_VERSION_2_0 -> {
            FeatureSet(
                llm = true,
                vlm = true,
                asr = true,
                tts = true,
                streaming = true,
                npu = true
            )
        }
        version >= IAIEngineService.API_VERSION_1_0 -> {
            FeatureSet(
                llm = true,
                vlm = false,  // Not available in v1
                asr = true,
                tts = true,
                streaming = false,  // Not available in v1
                npu = false
            )
        }
        else -> {
            FeatureSet()  // No features (incompatible)
        }
    }
}
```

---

## Backward Compatibility (T048)

### Adding New Methods

When adding new methods, use **default implementations** to maintain compatibility:

```java
interface IAIEngineService {
    // V1.0 method
    String inferText(String input, in Bundle params);

    // V2.0 method with default implementation
    String inferTextStreaming(String input, in Bundle params, IBinder callback) = "";
}
```

**How default implementations work**:
- Old clients calling new engine: Method exists, works normally
- New clients calling old engine: Method returns default value (`""`)
- Client should check version before using new methods

### Deprecated Methods

Mark deprecated methods with `@deprecated` annotation:

```java
interface IAIEngineService {
    /**
     * @deprecated Use inferTextV2() instead
     * Kept for backward compatibility until API_VERSION_3_0
     */
    String inferTextV1(String input);

    // Replacement method
    String inferTextV2(String input, in Bundle options);
}
```

**Deprecation Policy**:
- Keep deprecated methods for **at least 1 MAJOR version**
- Example: Deprecated in v2.0 → Remove in v3.0
- Document deprecation in version notes

### Version Transition Example

```
Timeline: v1.0 → v2.0 → v3.0

v1.0 (Initial):
  - inferText()

v2.0 (Add streaming):
  - inferText()           ← Still available
  - inferTextStreaming()  ← New method

v3.0 (Breaking changes):
  - inferTextV2()         ← New improved version
  - inferText() REMOVED   ← Old method removed after 1 major version
```

---

## Version Information Bundle (T049)

### Detailed Version Info

The `getVersionInfo()` method returns comprehensive version details:

```kotlin
// Engine implementation
override fun getVersionInfo(): Bundle {
    return Bundle().apply {
        putInt("major", 1)
        putInt("minor", 2)
        putInt("patch", 0)
        putInt("buildCode", BuildConfig.VERSION_CODE)
        putInt("apiVersion", IAIEngineService.CURRENT_VERSION)
        putString("semanticVersion", "1.2.0")
        putString("buildType", BuildConfig.BUILD_TYPE)  // "debug" or "release"
        putLong("buildTimestamp", BuildConfig.BUILD_TIMESTAMP)
    }
}
```

### Client Usage

```kotlin
val versionInfo = engineService.getVersionInfo()

val major = versionInfo.getInt("major")
val minor = versionInfo.getInt("minor")
val patch = versionInfo.getInt("patch")
val apiVersion = versionInfo.getInt("apiVersion")
val semanticVersion = versionInfo.getString("semanticVersion")

Log.i(TAG, "Engine version: $semanticVersion (API v$apiVersion)")

// Use semantic version for display
textView.text = "Engine: v$semanticVersion"

// Use API version for compatibility checks
if (apiVersion >= 2) {
    enableStreamingFeatures()
}
```

---

## Compatibility Matrix

### Supported Version Combinations

| Engine Version | Client Min | Client Max | Status |
|----------------|-----------|-----------|--------|
| 1.0.x | 1.0.0 | 1.x.x | ✅ Fully compatible |
| 1.1.x | 1.0.0 | 1.x.x | ✅ Backward compatible |
| 2.0.x | 1.5.0 | 2.x.x | ⚠️ Some features require client ≥2.0.0 |
| 2.0.x | 1.0.0 | 1.4.x | ⚠️ Basic features only |
| 3.0.x | 2.0.0 | 3.x.x | ✅ Compatible (when released) |
| 3.0.x | 1.x.x | 1.x.x | ❌ Incompatible (breaking changes) |

### Version Check Logic

```kotlin
fun isCompatible(engineVersion: Int, clientMinVersion: Int): Boolean {
    // Same major version = compatible
    val engineMajor = engineVersion
    val clientMajor = clientMinVersion

    return engineMajor >= clientMajor
}
```

---

## Testing Version Compatibility

### Unit Tests

```kotlin
@Test
fun testVersionCompatibility() {
    // Engine v2, Client requires v1 = Compatible
    assertTrue(isCompatible(engineVersion = 2, clientMinVersion = 1))

    // Engine v1, Client requires v2 = Incompatible
    assertFalse(isCompatible(engineVersion = 1, clientMinVersion = 2))

    // Engine v2, Client requires v2 = Compatible
    assertTrue(isCompatible(engineVersion = 2, clientMinVersion = 2))
}
```

### Integration Tests

```kotlin
@Test
fun testOldClientNewEngine() {
    // Simulate: Client built for API v1, Engine is API v2
    val service = connectToEngine()

    // Old methods should still work
    val result = service.inferText("test", Bundle.EMPTY)
    assertNotNull(result)

    // New methods available but not required
    val version = service.getVersion()
    assertEquals(2, version)
}
```

---

## Version Migration Guide

### For Engine Developers

When releasing a new version:

1. **Increment CURRENT_VERSION**:
   ```java
   const int CURRENT_VERSION = 3;  // Was 2
   ```

2. **Add new version constant**:
   ```java
   const int API_VERSION_3_0 = 3;
   ```

3. **Update getVersion() implementation**:
   ```kotlin
   override fun getVersion(): Int = IAIEngineService.CURRENT_VERSION
   ```

4. **Document changes** in version history comment

5. **Update compatibility-matrix.md** with new version

### For Client Developers

When engine is upgraded:

1. **Check new version** on service connection

2. **Feature detection**:
   ```kotlin
   if (engineVersion >= IAIEngineService.API_VERSION_3_0) {
       // Use v3.0 features
   } else if (engineVersion >= IAIEngineService.API_VERSION_2_0) {
       // Use v2.0 features
   }
   ```

3. **Update MIN_REQUIRED_VERSION** if needed

4. **Test with** both old and new engine versions

---

## Best Practices

### DO ✅

- Always call `getVersion()` before using advanced features
- Use version constants for compatibility checks
- Provide graceful fallbacks for older versions
- Document version requirements in method comments
- Test with multiple engine versions

### DON'T ❌

- Don't assume latest version is always available
- Don't break existing methods without deprecation period
- Don't use version checks for security (use permissions)
- Don't increment version for internal changes
- Don't remove deprecated methods without notice

---

## References

- **AIDL Interface**: `app/src/main/aidl/com/mtkresearch/breezeapp/engine/IAIEngineService.aidl`
- **Research**: `specs/001-engine-deployment-strategy/research.md` (Section R2)
- **Data Model**: `specs/001-engine-deployment-strategy/data-model.md` (Entity E1: EngineVersion)

---

**Document Version**: 1.0
**Maintained By**: BreezeApp Team
**Next Review**: Before each AIDL interface change
