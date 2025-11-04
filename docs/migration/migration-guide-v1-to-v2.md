# Migration Guide: API v1 → v2

**Target Audience**: Client app developers upgrading to AIDL API version 2
**Estimated Migration Time**: 2-4 hours
**Breaking Changes**: Yes (MAJOR version increment)
**Last Updated**: 2025-11-03

---

## Table of Contents

1. [Overview](#overview)
2. [Breaking Changes](#breaking-changes)
3. [New Features](#new-features)
4. [Migration Steps](#migration-steps)
5. [Code Examples](#code-examples)
6. [Testing](#testing)
7. [Rollback Plan](#rollback-plan)

---

## Overview (T129)

### What's Changing

API v2 introduces breaking changes to the AIDL interface to support:
- Vision-Language Model (VLM) inference
- Multi-turn conversation context management
- Custom model loading from external sources
- Enhanced error reporting with structured error bundles

### Timeline

- **API v1**: Supported from 2025-11 to 2026-06 (7 months)
- **API v2**: Available from 2026-01 onwards
- **Deprecation**: API v1 deprecated in 2026-03, removed in 2026-06
- **Overlap Period**: 5 months (2026-01 to 2026-06) where both APIs are supported

### Compatibility Matrix

| Engine Version | Supports API v1 | Supports API v2 | Recommended Client Action |
|----------------|-----------------|-----------------|---------------------------|
| 1.0.0 - 1.9.x | ✅ Yes | ❌ No | Use API v1 |
| 2.0.0 - 2.9.x | ✅ Yes (deprecated) | ✅ Yes | Migrate to API v2 |
| 3.0.0+ | ❌ No | ✅ Yes | Must use API v2 |

---

## Breaking Changes (T130)

### BC1: `inferText()` Signature Change

**v1 Signature**:
```java
String inferText(String input, in Bundle params);
```

**v2 Signature**:
```java
String inferText(String input, in Bundle params, String contextId);
```

**Reason**: Support multi-turn conversations with context tracking.

**Migration**:
```kotlin
// v1 (old code)
val result = engineService.inferText(prompt, params)

// v2 (new code)
val contextId = "conversation-123"  // Unique ID for this conversation
val result = engineService.inferText(prompt, params, contextId)

// Or for single-turn (no context)
val result = engineService.inferText(prompt, params, null)
```

---

### BC2: Error Handling - Return `Bundle` Instead of Throwing Exceptions

**v1 Behavior**:
```kotlin
try {
    val result = engineService.inferText(prompt, params)
    // Use result
} catch (e: RemoteException) {
    // Handle error
}
```

**v2 Behavior**:
```kotlin
val resultBundle = engineService.inferTextV2(prompt, params, contextId)
val success = resultBundle.getBoolean("success", false)

if (success) {
    val text = resultBundle.getString("text")
    // Use text
} else {
    val errorCode = resultBundle.getInt("errorCode")
    val errorMessage = resultBundle.getString("errorMessage")
    // Handle structured error
}
```

**Reason**: Better error granularity, no exception overhead across IPC.

---

### BC3: `IInferenceCallback` Interface Change

**v1 Interface**:
```java
interface IInferenceCallback {
    void onSuccess(String result);
    void onError(int errorCode, String message);
}
```

**v2 Interface**:
```java
interface IInferenceCallback {
    void onSuccess(in Bundle result);  // Changed: Bundle instead of String
    void onError(in Bundle error);     // Changed: Bundle instead of separate params
    void onProgress(in Bundle progress);  // New: Progress updates
}
```

**Migration**:
```kotlin
// v1 callback
val callbackV1 = object : IInferenceCallback.Stub() {
    override fun onSuccess(result: String) {
        handleResult(result)
    }
    override fun onError(errorCode: Int, message: String) {
        handleError(errorCode, message)
    }
}

// v2 callback
val callbackV2 = object : IInferenceCallback.Stub() {
    override fun onSuccess(result: Bundle) {
        val text = result.getString("text")
        val metadata = result.getBundle("metadata")  // NEW: Additional metadata
        handleResult(text, metadata)
    }

    override fun onError(error: Bundle) {
        val errorCode = error.getInt("code")
        val message = error.getString("message")
        val details = error.getBundle("details")  // NEW: Error details
        handleError(errorCode, message, details)
    }

    override fun onProgress(progress: Bundle) {  // NEW method
        val percentage = progress.getInt("percentage")
        updateProgressBar(percentage)
    }
}
```

---

### BC4: Model Loading - Mandatory Model ID

**v1 (Implicit Default Model)**:
```kotlin
// v1: Default model loaded automatically
val result = engineService.inferText(prompt, params)
```

**v2 (Explicit Model Selection)**:
```kotlin
// v2: Must specify model ID
val modelId = "llama-3b-q4"
engineService.loadModel(modelId)  // Explicit loading required

val params = Bundle().apply {
    putString("modelId", modelId)  // NEW: Model ID in params
}
val result = engineService.inferText(prompt, params, null)
```

**Reason**: Support custom models and multiple models loaded simultaneously.

---

## New Features (T131)

### Feature 1: Vision-Language Model (VLM) Support

**New AIDL Method**:
```java
Bundle inferVisionV2(
    in ParcelFileDescriptor imageFd,
    String prompt,
    in Bundle params,
    String contextId
);
```

**Example Usage**:
```kotlin
val imageFile = File("/path/to/image.jpg")
val imageFd = ParcelFileDescriptor.open(imageFile, ParcelFileDescriptor.MODE_READ_ONLY)

val params = Bundle().apply {
    putString("modelId", "llava-7b")
}

val result = engineService.inferVisionV2(
    imageFd,
    "What's in this image?",
    params,
    contextId = "vision-conversation-1"
)

imageFd.close()

if (result.getBoolean("success")) {
    val description = result.getString("text")
    println("Image description: $description")
}
```

---

### Feature 2: Multi-Turn Conversation Context

**Context Management**:
```kotlin
// Start a conversation
val contextId = UUID.randomUUID().toString()

// Turn 1
val response1 = engineService.inferText(
    "What is machine learning?",
    params,
    contextId
)

// Turn 2 - engine remembers previous context
val response2 = engineService.inferText(
    "Can you give me an example?",  // "it" refers to ML
    params,
    contextId  // Same context ID
)

// Clear context when done
engineService.clearContext(contextId)
```

---

### Feature 3: Custom Model Loading

**New Methods**:
```java
interface IAIEngineService {
    Bundle loadCustomModel(String modelPath, in Bundle config);
    void unloadModel(String modelId);
    Bundle listLoadedModels();
}
```

**Example**:
```kotlin
// Load a custom model from external storage
val modelPath = "/sdcard/Download/custom-llama-7b.pte"
val config = Bundle().apply {
    putString("modelType", "llm")
    putString("format", "executorch")
    putInt("contextLength", 2048)
}

val result = engineService.loadCustomModel(modelPath, config)

if (result.getBoolean("success")) {
    val modelId = result.getString("modelId")  // e.g., "custom-llama-7b"

    // Use the custom model
    val params = Bundle().apply {
        putString("modelId", modelId)
    }
    val inference = engineService.inferText(prompt, params, null)
}
```

---

## Migration Steps (T132-T133)

### Step-by-Step Migration Process

#### Step 1: Update AIDL Files (T132)

```bash
# In your client project
cd app/src/main/aidl/com/mtkresearch/breezeapp/engine/

# Download new AIDL files from engine repository
wget https://github.com/mtkresearch/BreezeApp-engine/releases/download/v2.0.0/aidl-v2.zip
unzip aidl-v2.zip

# Your project should now have:
# - IAIEngineService.aidl (updated)
# - IInferenceCallback.aidl (updated)
# - IStreamCallback.aidl (updated - minor changes)
```

#### Step 2: Update Dependencies

**build.gradle (Module: app)**:
```gradle
dependencies {
    // Update to v2-compatible version
    implementation 'com.github.mtkresearch:BreezeApp-engine:v2.0.0'  // Updated
}
```

#### Step 3: Update Version Constants

```kotlin
// Before
class EngineClient {
    companion object {
        const val MIN_REQUIRED_API_VERSION = 1
    }
}

// After
class EngineClient {
    companion object {
        const val MIN_REQUIRED_API_VERSION = 2  // Updated
        const val SUPPORTS_API_V1 = true  // For backward compat during transition
    }
}
```

#### Step 4: Migrate Inference Calls (T133)

**Create Compatibility Layer** (recommended during transition):

```kotlin
class CompatibleEngineClient(
    private val engineService: IAIEngineService
) {
    private val apiVersion = engineService.version

    fun inferText(prompt: String, params: Bundle): String {
        return if (apiVersion >= 2) {
            // Use v2 API
            val result = engineService.inferText(prompt, params, null)
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
}
```

#### Step 5: Update Callbacks

```kotlin
// Create adapter for v1 → v2 callback compatibility
class CallbackAdapter(
    private val onSuccess: (String) -> Unit,
    private val onError: (Int, String) -> Unit
) : IInferenceCallback.Stub() {

    // v2 interface
    override fun onSuccess(result: Bundle) {
        val text = result.getString("text") ?: ""
        onSuccess(text)
    }

    override fun onError(error: Bundle) {
        val code = error.getInt("code")
        val message = error.getString("message") ?: "Unknown error"
        onError(code, message)
    }

    override fun onProgress(progress: Bundle) {
        // Optional: handle progress
    }
}
```

#### Step 6: Test Compatibility

```kotlin
@Test
fun testApiV2Compatibility() {
    // Bind to engine
    val engineService = bindToEngine()

    // Check version
    val version = engineService.version
    assertTrue("Engine must support API v2", version >= 2)

    // Test basic inference
    val result = engineService.inferText("Hello", Bundle(), null)
    assertTrue(result.getBoolean("success"))

    // Test context management
    val contextId = "test-context"
    val result1 = engineService.inferText("What is AI?", Bundle(), contextId)
    val result2 = engineService.inferText("Tell me more", Bundle(), contextId)
    assertTrue(result2.getBoolean("success"))

    // Cleanup
    engineService.clearContext(contextId)
}
```

---

## Code Examples (T134)

### Complete Before/After Example

**Before (API v1)**:
```kotlin
class ChatRepository(private val engineService: IAIEngineService) {

    suspend fun sendMessage(message: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val params = Bundle().apply {
                putFloat("temperature", 0.7f)
                putInt("max_tokens", 512)
            }

            val response = engineService.inferText(message, params)
            Result.success(response)

        } catch (e: RemoteException) {
            Result.failure(e)
        }
    }
}
```

**After (API v2)**:
```kotlin
class ChatRepository(private val engineService: IAIEngineService) {

    private val conversationContexts = mutableMapOf<String, String>()

    suspend fun sendMessage(
        message: String,
        conversationId: String
    ): Result<String> = withContext(Dispatchers.IO) {

        // Get or create context ID for this conversation
        val contextId = conversationContexts.getOrPut(conversationId) {
            UUID.randomUUID().toString()
        }

        val params = Bundle().apply {
            putFloat("temperature", 0.7f)
            putInt("max_tokens", 512)
            putString("modelId", "llama-3b-q4")  // NEW: Explicit model
        }

        // v2 API returns Bundle
        val result = engineService.inferText(message, params, contextId)

        if (result.getBoolean("success")) {
            val text = result.getString("text") ?: ""
            val metadata = result.getBundle("metadata")

            // NEW: Access metadata
            val tokensUsed = metadata?.getInt("tokensUsed") ?: 0
            val inferenceTimeMs = metadata?.getLong("inferenceTimeMs") ?: 0L

            logMetrics(tokensUsed, inferenceTimeMs)

            Result.success(text)
        } else {
            val errorCode = result.getInt("errorCode")
            val errorMessage = result.getString("errorMessage") ?: "Unknown error"
            val errorDetails = result.getBundle("errorDetails")

            Result.failure(Exception("[$errorCode] $errorMessage"))
        }
    }

    fun clearConversation(conversationId: String) {
        conversationContexts[conversationId]?.let { contextId ->
            engineService.clearContext(contextId)
            conversationContexts.remove(conversationId)
        }
    }
}
```

---

## Testing (T135)

### Migration Testing Checklist

#### Automated Tests

```kotlin
class MigrationTestSuite {

    @Test
    fun testBackwardCompatibility() {
        // Ensure v1 clients can still work with v2 engine
        // during transition period
    }

    @Test
    fun testContextPersistence() {
        // Verify multi-turn conversations work correctly
    }

    @Test
    fun testCustomModelLoading() {
        // Test new custom model feature
    }

    @Test
    fun testErrorHandling() {
        // Verify structured error bundles
    }

    @Test
    fun testPerformanceRegression() {
        // Ensure v2 is not slower than v1
    }
}
```

#### Manual Testing

- [ ] Basic text inference works
- [ ] Multi-turn conversation maintains context
- [ ] VLM inference works with image input
- [ ] Custom model loading succeeds
- [ ] Error messages are clear and actionable
- [ ] Performance is acceptable (no regressions)
- [ ] App works on both API v1 and v2 engines (during transition)

---

## Rollback Plan (T136)

### If Migration Fails

#### Option 1: Runtime Version Detection (Recommended)

Keep both v1 and v2 code paths:

```kotlin
class AdaptiveEngineClient(private val engineService: IAIEngineService) {

    private val apiVersion = engineService.version

    fun inferText(prompt: String, params: Bundle): String {
        return when {
            apiVersion >= 2 -> inferTextV2(prompt, params)
            else -> inferTextV1(prompt, params)
        }
    }

    private fun inferTextV2(prompt: String, params: Bundle): String {
        val result = engineService.inferText(prompt, params, null)
        if (result.getBoolean("success")) {
            return result.getString("text") ?: ""
        } else {
            throw Exception(result.getString("errorMessage"))
        }
    }

    @Suppress("DEPRECATION")
    private fun inferTextV1(prompt: String, params: Bundle): String {
        return engineService.inferTextV1(prompt, params)
    }
}
```

#### Option 2: Feature Flags

```kotlin
object FeatureFlags {
    var USE_API_V2 = true  // Toggle via remote config

    fun enableApiV2() { USE_API_V2 = true }
    fun disableApiV2() { USE_API_V2 = false }
}

// Usage
fun inferText(prompt: String): String {
    return if (FeatureFlags.USE_API_V2) {
        inferTextV2(prompt)
    } else {
        inferTextV1(prompt)
    }
}
```

#### Option 3: Staged Rollout

- Release to 5% of users first
- Monitor crash rates and errors
- If issues found, disable v2 via feature flag
- Fix issues and re-release
- Gradually increase rollout percentage

---

## FAQ

**Q: Do I need to update my client app immediately?**
A: No, API v1 will be supported until June 2026. You have 5 months to migrate.

**Q: What happens if my app uses v1 with a v3 engine?**
A: The engine will reject your binding attempt. You'll need to update your app.

**Q: Can I use v2 features with a v1 engine?**
A: No, but you should check `engineService.version` and gracefully degrade features.

**Q: How do I test with both v1 and v2 engines?**
A: Install different engine versions on different test devices, or use emulators.

---

**Document Version**: 1.0
**Maintained By**: BreezeApp Team
**Last Updated**: 2025-11-03
