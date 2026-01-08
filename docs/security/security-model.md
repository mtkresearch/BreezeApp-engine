# BreezeApp-engine Security Model

## Overview

The engine uses Android's standard permission system to control access to the AI inference service. Apps must declare the required permission in their manifest to use AI features via the EdgeAI SDK.

---

## Permission Model

### Permission Required

**Name**: `com.mtkresearch.breezeapp.permission.BIND_ENGINE_SERVICE`  
**Protection Level**: `normal`

**Behavior**:
- Any app can request this permission
- Granted automatically at install time
- No user prompts required

---

## Client Integration

### Step 1: Add Permission to Manifest

Add the permission declaration to your app's `AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.yourcompany.yourapp">

    <!-- Request permission to use BreezeApp Engine -->
    <uses-permission
        android:name="com.mtkresearch.breezeapp.permission.BIND_ENGINE_SERVICE" />

    <application>
        <!-- Your app components -->
    </application>
</manifest>
```

### Step 2: Use EdgeAI SDK

The EdgeAI SDK handles all AIDL service binding internally:

```kotlin
// Initialize EdgeAI SDK (handles service binding)
EdgeAI.initialize(context)

// Use AI features directly
val chatFlow = EdgeAI.chat(ChatRequest(
    sessionId = "session-123",
    message = "Hello, AI!"
))

chatFlow.collect { response ->
    println(response.message)
}
```

> **Note**: The EdgeAI SDK abstracts all AIDL complexity. You never need to call `bindService()` or manage `ServiceConnection` yourself.

---

## Engine Configuration

The engine's `AndroidManifest.xml` defines and enforces the permission:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.mtkresearch.breezeapp.engine">

    <!-- Define permission -->
    <permission
        android:name="com.mtkresearch.breezeapp.permission.BIND_ENGINE_SERVICE"
        android:protectionLevel="normal"
        android:label="@string/permission_bind_engine_label"
        android:description="@string/permission_bind_engine_desc" />

    <application>
        <!-- Service protected by permission -->
        <service
            android:name=".BreezeAppEngineService"
            android:exported="true"
            android:permission="com.mtkresearch.breezeapp.permission.BIND_ENGINE_SERVICE">
            
            <!-- Action for EdgeAI SDK binding -->
            <intent-filter>
                <action android:name="com.mtkresearch.breezeapp.engine.BreezeAppEngineService" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

**Note**: The intent action `com.mtkresearch.breezeapp.engine.BreezeAppEngineService` is used by EdgeAI SDK to bind to the service.

---

## Verification

### Check Permission

```bash
# Verify engine defines the permission
adb shell dumpsys package com.mtkresearch.breezeapp.engine | grep BIND_ENGINE_SERVICE

# Verify client has the permission
adb shell dumpsys package com.yourapp.package | grep BIND_ENGINE_SERVICE
```

### Test Connection

```bash
# Monitor service binding
adb logcat | grep "BreezeAppEngineService\|EdgeAI"
```

---

## References

- [Android Permissions](https://developer.android.com/guide/topics/permissions/overview)
- [Protection Levels](https://developer.android.com/guide/topics/manifest/permission-element#plevel)
- [AIDL Security](https://developer.android.com/develop/background-work/services/aidl)
