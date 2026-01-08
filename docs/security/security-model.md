# BreezeApp-engine Security Model

**Document Version**: 2.0  
**Last Updated**: 2026-01-08  
**Status**: Reflects Current Implementation

## Overview

This document defines the security architecture for BreezeApp-engine. The engine uses Android's standard permission system to control access to the AI inference service via AIDL.

## Security Objectives

1. **Authorization**: Enforce permission-based access for service binding
2. **Audit**: Log authorization attempts for monitoring
3. **Transparency**: Provide clear error messages for failures

---

## Permission Model

### Architecture

BreezeApp-engine uses **normal protection level** for service access:

```
┌─────────────────────────────────────────────────────────────┐
│ Client App (any app)                                         │
│ - Declares: <uses-permission BIND_ENGINE_SERVICE />         │
└────────────────────┬────────────────────────────────────────┘
                     │
                     │ 1. bindService()
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ Android System                                               │
│ - Checks: Permission declared in manifest?                  │
│ - Grants permission automatically at install time           │
└────────────────────┬────────────────────────────────────────┘
                     │
                     │ 2. onBind()
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ BreezeApp-engine Service                                     │
│ - Protected by BIND_ENGINE_SERVICE permission               │
└─────────────────────────────────────────────────────────────┘
```

### Permission Definition

**Name**: `com.mtkresearch.breezeapp.permission.BIND_ENGINE_SERVICE`  
**Protection Level**: `normal`

**Behavior**:
- Any app can request this permission
- Granted automatically at install time
- No user prompts required
- Enables third-party integration

---

## Implementation

### Engine: AndroidManifest.xml

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
            android:name=".service.BreezeAppEngineService"
            android:exported="true"
            android:permission="com.mtkresearch.breezeapp.permission.BIND_ENGINE_SERVICE">
            <intent-filter>
                <action android:name="com.mtkresearch.breezeapp.engine.AI_SERVICE" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

### Client: AndroidManifest.xml

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.yourcompany.yourapp">

    <!-- Request permission -->
    <uses-permission
        android:name="com.mtkresearch.breezeapp.permission.BIND_ENGINE_SERVICE" />

    <application>
        <!-- Your app components -->
    </application>
</manifest>
```

### Client: Binding Code

```kotlin
class EngineClient(private val context: Context) {
    private var engineService: IBreezeAppEngineService? = null

    fun bindEngine() {
        val intent = Intent("com.mtkresearch.breezeapp.engine.AI_SERVICE")
        intent.setPackage("com.mtkresearch.breezeapp.engine")

        val bound = context.bindService(
            intent,
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )

        if (!bound) {
            // Engine not installed or permission denied
            handleBindingFailure()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            engineService = IBreezeAppEngineService.Stub.asInterface(service)
            // Engine ready to use
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            engineService = null
        }
    }
}
```

---

## Security Considerations

### Current Model

**Protection Level**: `normal`

**Access Control**:
- Any app that declares the permission can bind
- Permission granted automatically at install
- No signature verification
- Suitable for public API services

### Threat Model

| Threat | Mitigation |
|--------|-----------|
| **Unauthorized binding** | Permission declaration required |
| **Malicious apps** | App review process (Play Store) |
| **Resource abuse** | Rate limiting (future enhancement) |
| **Data privacy** | No PII stored in engine |

### Future Enhancements

If stricter access control is needed in the future:

1. **Upgrade to `signature` protection** - Only apps signed with same certificate can bind
2. **Add API key authentication** - Require API keys on top of permission
3. **Implement rate limiting** - Prevent resource abuse
4. **Add usage analytics** - Monitor and detect anomalous patterns

---

## Testing

### Verification Checklist

- [ ] Permission defined in engine AndroidManifest.xml
- [ ] Service protected by permission
- [ ] Client declares permission in manifest
- [ ] Binding succeeds for authorized clients
- [ ] Binding fails for clients without permission

### Test Commands

```bash
# Check if permission is defined
adb shell dumpsys package com.mtkresearch.breezeapp.engine | grep BIND_ENGINE_SERVICE

# Check if client has permission
adb shell dumpsys package com.yourapp.client | grep BIND_ENGINE_SERVICE

# Test binding
adb logcat | grep "BreezeAppEngineService"
```

---

## References

- [Android Permissions](https://developer.android.com/guide/topics/permissions/overview)
- [Protection Levels](https://developer.android.com/guide/topics/manifest/permission-element#plevel)
- [AIDL Security](https://developer.android.com/develop/background-work/services/aidl)

---

**Document Version**: 2.0  
**Status**: Reflects current implementation with normal protection level
