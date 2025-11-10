# BreezeApp-engine Integration Guide

**Version**: 1.0
**Last Updated**: 2025-11-03
**Audience**: Android developers integrating with BreezeApp-engine

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Quick Start](#quick-start)
4. [Custom Permission Usage](#custom-permission-usage)
5. [Client App Integration](#client-app-integration)
6. [Version Compatibility](#version-compatibility)
7. [Error Handling](#error-handling)
8. [Best Practices](#best-practices)
9. [Troubleshooting](#troubleshooting)
10. [Testing Integration](#testing-integration)

---

## Overview

BreezeApp-engine provides AI inference capabilities (LLM, VLM, ASR, TTS) through a secure AIDL service interface. This guide shows you how to integrate your Android application with the engine.

**Key Concepts**:
- **Engine**: Background service providing AI inference (service provider)
- **Client App**: Your application consuming AI services (service consumer)
- **AIDL**: Android Interface Definition Language for cross-app communication
- **Signature Permission**: Security mechanism ensuring only authorized apps can bind

**Architecture**:
```
┌─────────────────────┐
│   Your Client App   │
│   (UI + Business)   │
└──────────┬──────────┘
           │ AIDL Binding
           │ (Signature Protected)
           ▼
┌─────────────────────┐
│ BreezeApp-engine    │
│ (AI Inference Core) │
└─────────────────────┘
```

---

## Prerequisites

### Development Environment

- **Android Studio**: Arctic Fox (2020.3.1) or later
- **Minimum SDK**: API 34 (Android 14)
- **Target SDK**: API 34 or higher
- **Kotlin**: 1.9+ (or Java 11+)

### Required Knowledge

- Android Services and binding
- AIDL basics
- Android permissions system
- Kotlin coroutines (for async operations)

### Signing Requirements

⚠️ **CRITICAL**: Your app must be signed with the **same certificate** as BreezeApp-engine.

For development:
- Contact BreezeApp team for debug signing key
- Or request signature whitelisting (development only)

For production:
- Use Google Play App Signing
- Coordinate with BreezeApp team to use shared certificate

---

## Quick Start

### Step 1: Add AIDL Interfaces

Download AIDL files from [GitHub](https://github.com/mtkresearch/BreezeApp-engine/tree/main/aidl) or copy from engine project:

```
your-app/
├── app/src/main/
│   └── aidl/
│       └── com/mtkresearch/breezeapp/engine/
│           ├── IAIEngineService.aidl
│           ├── IInferenceCallback.aidl
│           └── IStreamCallback.aidl
```

### Step 2: Declare Permission in Manifest

Add to your `AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.yourcompany.yourapp">

    <!-- Request permission to bind to BreezeApp Engine -->
    <uses-permission
        android:name="com.mtkresearch.breezeapp.engine.permission.BIND_AI_SERVICE" />

    <application ... >
        <!-- Your app components -->
    </application>
</manifest>
```

### Step 3: Add Dependencies

In your `app/build.gradle.kts`:

```kotlin
dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")

    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Optional: Play Core for in-app updates
    implementation("com.google.android.play:core-ktx:1.8.1")
}
```

### Step 4: Create Engine Client

```kotlin
// EngineClient.kt
package com.yourcompany.yourapp.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import com.mtkresearch.breezeapp.engine.IAIEngineService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class EngineClient(private val context: Context) {

    companion object {
        private const val TAG = "EngineClient"
        private const val ENGINE_PACKAGE = "com.mtkresearch.breezeapp.engine"
        private const val ENGINE_ACTION = "com.mtkresearch.breezeapp.engine.AI_SERVICE"
        const val MIN_REQUIRED_VERSION = 1
    }

    private var engineService: IAIEngineService? = null
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        data class Connected(val version: Int, val capabilities: Bundle) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service connected: $name")

            try {
                engineService = IAIEngineService.Stub.asInterface(service)

                // Check version compatibility
                val version = engineService?.version ?: 0
                if (version < MIN_REQUIRED_VERSION) {
                    _connectionState.value = ConnectionState.Error(
                        "Engine version $version incompatible (required: $MIN_REQUIRED_VERSION+)"
                    )
                    return
                }

                // Get capabilities
                val capabilities = engineService?.capabilities ?: Bundle.EMPTY
                _connectionState.value = ConnectionState.Connected(version, capabilities)

                Log.i(TAG, "Engine connected successfully. Version: $version")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to communicate with engine", e)
                _connectionState.value = ConnectionState.Error(
                    "Communication failed: ${e.message}"
                )
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "Service disconnected: $name")
            engineService = null
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    fun bind() {
        if (_connectionState.value is ConnectionState.Connected) {
            Log.d(TAG, "Already connected")
            return
        }

        _connectionState.value = ConnectionState.Connecting

        val intent = Intent(ENGINE_ACTION).apply {
            setPackage(ENGINE_PACKAGE)
        }

        val bound = context.bindService(
            intent,
            serviceConnection,
            Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT
        )

        if (!bound) {
            _connectionState.value = ConnectionState.Error(
                "Engine not installed or incompatible"
            )
        }
    }

    fun unbind() {
        if (engineService != null) {
            context.unbindService(serviceConnection)
            engineService = null
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    fun getService(): IAIEngineService? = engineService

    fun isConnected(): Boolean = _connectionState.value is ConnectionState.Connected
}
```

### Step 5: Use in Your Activity/Fragment

```kotlin
class MainActivity : AppCompatActivity() {

    private lateinit var engineClient: EngineClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        engineClient = EngineClient(this)

        // Observe connection state
        lifecycleScope.launch {
            engineClient.connectionState.collect { state ->
                when (state) {
                    is EngineClient.ConnectionState.Connected -> {
                        onEngineReady(state.version, state.capabilities)
                    }
                    is EngineClient.ConnectionState.Error -> {
                        showError(state.message)
                    }
                    else -> { /* Handle other states */ }
                }
            }
        }

        // Bind to engine
        engineClient.bind()
    }

    override fun onDestroy() {
        super.onDestroy()
        engineClient.unbind()
    }

    private fun onEngineReady(version: Int, capabilities: Bundle) {
        Log.d(TAG, "Engine ready! Version: $version")
        // Start using AI features
    }
}
```

---

## Custom Permission Usage

### Permission Declaration (T035)

The custom permission `com.mtkresearch.breezeapp.engine.permission.BIND_AI_SERVICE` is defined by the engine and protected at the `signature` level.

**What this means**:
- ✅ Your app and the engine must be signed with the **same certificate**
- ✅ Permission is **automatically granted** by the system (no user prompt)
- ✅ No runtime permission checks needed
- ❌ Apps with different signatures **cannot** bind to the engine

### Client App Manifest (T036)

Your `AndroidManifest.xml` must include:

```xml
<manifest ...>
    <!-- Request binding permission -->
    <uses-permission
        android:name="com.mtkresearch.breezeapp.engine.permission.BIND_AI_SERVICE" />

    <application ... >
        <!-- Your components -->
    </application>
</manifest>
```

**Important Notes**:
- Use exact permission name (case-sensitive)
- No additional attributes needed (`android:required`, `android:maxSdkVersion` not applicable)
- Permission is defined by engine, not your app

### Permission Verification

To verify permission is correctly declared:

```bash
# Check if permission is in manifest
adb shell dumpsys package com.yourcompany.yourapp | grep BIND_AI_SERVICE

# Expected output:
# requested permissions:
#   com.mtkresearch.breezeapp.engine.permission.BIND_AI_SERVICE
```

---

## Client App Integration

### Binding Lifecycle

```
┌─────────────────────────────────────────────────────────┐
│ Recommended Binding Lifecycle                           │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ onCreate()       → Initialize EngineClient             │
│ onStart()        → Bind to engine                      │
│ onStop()         → Keep binding (if needed)            │
│ onDestroy()      → Unbind from engine                  │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

**Best Practice**: Bind in `onCreate()` or `onStart()`, unbind in `onDestroy()`.

### Basic LLM Inference

```kotlin
suspend fun performInference(prompt: String): String? = withContext(Dispatchers.IO) {
    val service = engineClient.getService() ?: run {
        Log.e(TAG, "Engine not connected")
        return@withContext null
    }

    try {
        val params = Bundle().apply {
            putFloat("temperature", 0.7f)
            putInt("topK", 40)
            putFloat("topP", 0.9f)
            putInt("maxTokens", 512)
        }

        service.inferText(prompt, params)
    } catch (e: RemoteException) {
        Log.e(TAG, "Inference failed", e)
        null
    }
}
```

### Streaming Inference

```kotlin
class StreamingHelper(
    private val engineClient: EngineClient,
    private val onToken: (String) -> Unit,
    private val onComplete: () -> Unit
) {

    private val streamCallback = object : IStreamCallback.Stub() {
        override fun onToken(token: String, metadata: Bundle?) {
            Handler(Looper.getMainLooper()).post {
                onToken(token)
            }
        }

        override fun onStreamComplete(metadata: Bundle?) {
            Handler(Looper.getMainLooper()).post {
                onComplete()
            }
        }

        override fun onError(errorCode: Int, message: String?) {
            Log.e(TAG, "Stream error: $message")
        }

        // Implement other required methods...
    }

    fun startStreaming(prompt: String) {
        val service = engineClient.getService() ?: return

        val params = Bundle().apply {
            putFloat("temperature", 0.7f)
        }

        service.inferTextStreaming(prompt, params, streamCallback)
    }
}
```

---

## Version Compatibility

### Checking Engine Version

Always check version before using advanced features:

```kotlin
fun checkCompatibility() {
    val state = engineClient.connectionState.value
    if (state is EngineClient.ConnectionState.Connected) {
        when {
            state.version >= 2 -> {
                // Full feature set (streaming, VLM)
                enableAllFeatures()
            }
            state.version >= 1 -> {
                // Basic features (LLM, ASR, TTS)
                enableBasicFeatures()
            }
            else -> {
                showUpdateDialog()
            }
        }
    }
}
```

### Handling Version Mismatches

```kotlin
private fun handleVersionMismatch(engineVersion: Int) {
    AlertDialog.Builder(this)
        .setTitle("Update Required")
        .setMessage("""
            Engine version $engineVersion is incompatible.
            Required: $MIN_REQUIRED_VERSION or higher.
        """.trimIndent())
        .setPositiveButton("Update") { _, _ ->
            openPlayStore("com.mtkresearch.breezeapp.engine")
        }
        .setCancelable(false)
        .show()
}
```

---

## Error Handling

### Common Errors (T038)

#### 1. Engine Not Installed

**Error**: `bindService()` returns `false`

**Solution**:
```kotlin
if (!bound) {
    AlertDialog.Builder(this)
        .setTitle("BreezeApp Engine Required")
        .setMessage("This app requires BreezeApp Engine. Install it?")
        .setPositiveButton("Install") { _, _ ->
            openPlayStore("com.mtkresearch.breezeapp.engine")
        }
        .show()
}
```

#### 2. Permission Denied

**Error**: SecurityException or binding fails silently

**Cause**: Signature mismatch between your app and engine

**Solution**:
```bash
# Verify signatures match
apksigner verify --print-certs your-app.apk | grep SHA-256
apksigner verify --print-certs engine.apk | grep SHA-256

# Signatures must be identical
```

#### 3. Version Incompatible

**Error**: Engine version < required version

**Solution**: Prompt user to update engine (see [Version Compatibility](#version-compatibility))

#### 4. Service Timeout

**Error**: `onServiceConnected()` never called

**Solution**:
```kotlin
private val bindingTimeout = Handler(Looper.getMainLooper()).postDelayed({
    if (connectionState.value is ConnectionState.Connecting) {
        connectionState.value = ConnectionState.Error("Binding timeout")
    }
}, 30_000)  // 30 second timeout
```

---

## Best Practices

### 1. Always Check Connection State

```kotlin
// ❌ BAD: Direct service access
val result = engineService?.inferText(prompt, params)

// ✅ GOOD: Check connection first
if (engineClient.isConnected()) {
    val result = engineClient.getService()?.inferText(prompt, params)
}
```

### 2. Handle Service Disconnection

```kotlin
override fun onServiceDisconnected(name: ComponentName?) {
    engineService = null
    connectionState.value = ConnectionState.Disconnected

    // Attempt reconnection
    Handler(Looper.getMainLooper()).postDelayed({
        bind()
    }, 5000)  // Retry after 5 seconds
}
```

### 3. Use Coroutines for Long Operations

```kotlin
viewModelScope.launch {
    val result = withContext(Dispatchers.IO) {
        engineClient.getService()?.inferText(prompt, params)
    }
    withContext(Dispatchers.Main) {
        displayResult(result)
    }
}
```

### 4. Implement Exponential Backoff for Retries

```kotlin
private var retryCount = 0
private val maxRetries = 3

private fun bindWithRetry() {
    bind()

    if (connectionState.value is ConnectionState.Error && retryCount < maxRetries) {
        val delay = (2.0.pow(retryCount) * 1000).toLong()
        Handler(Looper.getMainLooper()).postDelayed({
            retryCount++
            bindWithRetry()
        }, delay)
    }
}
```

### 5. Clean Up Resources

```kotlin
override fun onDestroy() {
    super.onDestroy()
    engineClient.unbind()
    // Cancel any pending operations
    scope.cancel()
}
```

---

## Troubleshooting

### Problem: Binding Fails Silently

**Symptoms**: No exception, but `onServiceConnected()` never called

**Checklist**:
- [ ] Engine APK installed? (`adb shell pm list packages | grep engine`)
- [ ] Permission declared in manifest?
- [ ] Signatures match?
- [ ] Correct package name and action?

**Debug Commands**:
```bash
# Check if engine is installed
adb shell pm list packages | grep com.mtkresearch.breezeapp.engine

# Check if service is exported
adb shell dumpsys package com.mtkresearch.breezeapp.engine | grep AIEngineService

# Test binding manually
adb shell am start-service com.mtkresearch.breezeapp.engine/.service.AIEngineService
```

### Problem: SecurityException When Binding

**Error**: `java.lang.SecurityException: Not allowed to bind to service Intent`

**Cause**: Signature mismatch

**Solution**:
1. Verify both APKs signed with same certificate
2. For development, use debug signing key provided by BreezeApp team
3. For production, coordinate Play App Signing certificates

### Problem: RemoteException During AIDL Calls

**Error**: `android.os.RemoteException: Transaction failed`

**Causes**:
- Data too large (>1MB Binder limit)
- Engine process crashed
- Invalid parameters

**Solutions**:
```kotlin
try {
    val result = service.inferText(prompt, params)
} catch (e: RemoteException) {
    Log.e(TAG, "AIDL call failed", e)
    // Check if service is still alive
    if (service.asBinder().isBinderAlive) {
        // Retry
    } else {
        // Reconnect
        engineClient.unbind()
        engineClient.bind()
    }
}
```

---

## Testing Integration

### Unit Testing

```kotlin
@Test
fun `test engine client binding`() = runTest {
    val mockContext = mockk<Context>(relaxed = true)
    val client = EngineClient(mockContext)

    client.bind()

    // Verify bindService was called
    verify {
        mockContext.bindService(any(), any(), any())
    }
}
```

### Integration Testing

Requires real device with engine installed:

```kotlin
@RunWith(AndroidJUnit4::class)
class EngineIntegrationTest {

    @Test
    fun testRealEngineBinding() {
        val scenario = ActivityScenario.launch(TestActivity::class.java)

        scenario.onActivity { activity ->
            val client = EngineClient(activity)
            client.bind()

            // Wait for connection
            Thread.sleep(2000)

            val state = client.connectionState.value
            assertTrue(state is EngineClient.ConnectionState.Connected)
        }
    }
}
```

---

## Next Steps

- **API Reference**: See [AIDL Reference](./api/aidl-reference.md) for complete method documentation
- **Security**: Review [Security Model](./security/security-model.md) for best practices
- **Examples**: Check [quickstart.md](../specs/001-engine-deployment-strategy/quickstart.md) for more examples
- **Deployment**: See [Deployment Guide](./deployment-guide.md) for Play Store publishing

## Support

- **Documentation**: https://github.com/mtkresearch/BreezeApp-engine/docs
- **Issues**: https://github.com/mtkresearch/BreezeApp-engine/issues
- **Email**: breezeapp-support@mtkresearch.com

---

**Document Version**: 1.0
**Maintained By**: BreezeApp Team
**Last Review**: 2025-11-03
