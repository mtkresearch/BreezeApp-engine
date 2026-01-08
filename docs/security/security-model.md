# BreezeApp-engine Security Model

**Document Version**: 1.0
**Last Updated**: 2025-11-03
**Status**: Implementation Ready

## Overview

This document defines the security architecture for BreezeApp-engine, focusing on signature-level access control for the AI inference service. The security model ensures that only authorized applications with matching signing certificates can bind to and use the engine's AIDL interface.

## Security Objectives

1. **Authentication**: Verify that only applications signed with authorized certificates can access the engine
2. **Authorization**: Enforce signature-level permissions for service binding
3. **Audit**: Log all authorization attempts for security monitoring
4. **Integrity**: Prevent tampering with the authorization mechanism
5. **Transparency**: Provide clear error messages for authorization failures

---

## Signature-Level Permission Model

### Architecture

BreezeApp-engine uses Android's signature-level protection mechanism to restrict service access:

```
┌─────────────────────────────────────────────────────────────┐
│ Client App (companion-app)                                  │
│ - Signed with Certificate A                                 │
│ - Declares: <uses-permission BIND_ENGINE_SERVICE />            │
└────────────────────┬────────────────────────────────────────┘
                     │
                     │ 1. bindService()
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ Android System                                               │
│ - Checks: Client certificate == Engine certificate?         │
│ - If YES: Grant permission automatically                     │
│ - If NO: Deny binding (SecurityException)                   │
└────────────────────┬────────────────────────────────────────┘
                     │
                     │ 2. onBind() if authorized
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ BreezeApp-engine Service                                     │
│ - Defines: <permission protectionLevel="signature" />       │
│ - Service protected by permission                            │
│ - Additional signature verification in onBind()              │
└─────────────────────────────────────────────────────────────┘
```

### Permission Definition

**Permission Name**: `com.mtkresearch.breezeapp.permission.BIND_ENGINE_SERVICE`

**Protection Level**: `normal`

**Behavior**:
- Any app can request this permission
- System automatically grants the permission at install time
- No runtime permission prompts shown to users
- Permission grant is transparent and seamless

### Why Normal Protection?

| Security Goal | How Normal Protection Achieves It |
|--------------|-----------------------------------|
| **Easy integration** | Any app can bind without certificate coordination |
| **No user friction** | No permission dialogs interrupt UX |
| **Developer friendly** | Third-party developers can integrate easily |
| **Play Store compatible** | Works seamlessly with all apps |
| **IPC accessibility** | Standard protection for service-to-service communication |

---

## Certificate Management

### Signing Certificate Strategy

**Primary Strategy**: Use **Google Play App Signing** with a shared signing certificate across the ecosystem.

#### Play App Signing Setup

```
┌──────────────────────┐
│ Developer Machine    │
│ - Upload Key        │ ← Developer signs APK/AAB
│ - Never leaves dev  │
└──────────┬───────────┘
           │
           │ Upload signed AAB
           ▼
┌──────────────────────┐
│ Google Play Console  │
│ - App Signing Key   │ ← Google re-signs with this
│ - Managed by Google │
└──────────┬───────────┘
           │
           │ Distribute to users
           ▼
┌──────────────────────┐
│ User Device         │
│ - App Signing Key  │ ← Device verifies this signature
│ - Signature matches│
└─────────────────────┘
```

#### Certificate Consistency Requirements

For signature-level permissions to work, **all apps in the ecosystem must be signed with the same App Signing Key**:

1. **BreezeApp-engine** - Signed with App Signing Key A
2. **BreezeApp (main client)** - Signed with App Signing Key A
3. **companion apps** - Signed with App Signing Key A
4. **Third-party integrations** - Must obtain App Signing Key A or coordinate separate keys

#### Obtaining the Shared Certificate

**For apps under the same Google Play developer account**:
1. Enable Play App Signing for the first app (BreezeApp-engine)
2. Google generates and stores the App Signing Key
3. Use the same developer account for subsequent apps
4. Google automatically uses the same App Signing Key

**For third-party developers**:
1. Contact BreezeApp team for integration authorization
2. Coordinate certificate sharing (requires legal agreement)
3. OR: Use a separate authorization mechanism (e.g., API keys on top of signature)

#### Certificate Export for Verification

To verify or whitelist signatures programmatically:

```bash
# Download App Signing certificate from Play Console
# Release → Setup → App Signing → Download certificate (DER format)

# Convert to SHA-256 hash for whitelisting
keytool -printcert -file deployment_cert.der | grep SHA256
# Output: SHA256: AB:CD:EF:12:34:...

# Verify APK signature
apksigner verify --print-certs app.apk
```

---

## Signature Verification Flow

### Service Binding Sequence

```
Client App                    Android System               BreezeApp-engine
    │                              │                              │
    │ 1. bindService(intent)       │                              │
    ├─────────────────────────────>│                              │
    │                              │                              │
    │                              │ 2. Check caller signature   │
    │                              │    vs engine signature      │
    │                              │                              │
    │                              │ 3. If mismatch:             │
    │                              │    Throw SecurityException  │
    │                              │    (binding fails)          │
    │                              │                              │
    │                              │ 4. If match:                │
    │                              │    Call onBind()            │
    │                              ├────────────────────────────>│
    │                              │                              │
    │                              │                       5. Additional
    │                              │                          verification
    │                              │                          (optional)
    │                              │                              │
    │                              │       6. Return Binder       │
    │                              │<─────────────────────────────┤
    │                              │                              │
    │ 7. onServiceConnected(binder)│                              │
    │<─────────────────────────────┤                              │
    │                              │                              │
    │ 8. Use AIDL methods          │                              │
    ├──────────────────────────────┼─────────────────────────────>│
```

### Code Implementation

#### Engine: AndroidManifest.xml

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.mtkresearch.breezeapp.engine">

    <!-- Define custom permission with normal protection -->
    <permission
        android:name="com.mtkresearch.breezeapp.permission.BIND_ENGINE_SERVICE"
        android:protectionLevel="normal"
        android:label="@string/permission_bind_engine_label"
        android:description="@string/permission_bind_engine_desc" />

    <application>
        <!-- Service protected by signature permission -->
        <service
            android:name=".service.AIEngineService"
            android:exported="true"
            android:permission="com.mtkresearch.breezeapp.permission.BIND_ENGINE_SERVICE"
            android:process=":ai_engine">
            <intent-filter>
                <action android:name="com.mtkresearch.breezeapp.engine.AI_SERVICE" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

#### Engine: Service Implementation

```kotlin
package com.mtkresearch.breezeapp.engine.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.mtkresearch.breezeapp.engine.security.SignatureValidator
import com.mtkresearch.breezeapp.engine.IAIEngineService

class AIEngineService : Service() {

    private val TAG = "AIEngineService"
    private val binder = AIEngineServiceBinder()

    override fun onBind(intent: Intent?): IBinder? {
        val callingUid = Binder.getCallingUid()

        // Additional signature verification (defense in depth)
        if (!SignatureValidator.verifyCallerSignature(this, callingUid)) {
            Log.w(TAG, "Unauthorized binding attempt from UID: $callingUid")
            SignatureValidator.logUnauthorizedAttempt(callingUid)
            return null
        }

        Log.i(TAG, "Authorized binding from UID: $callingUid")
        return binder
    }

    private inner class AIEngineServiceBinder : IAIEngineService.Stub() {
        override fun getVersion(): Int = IAIEngineService.CURRENT_VERSION

        override fun inferText(input: String, params: Bundle): String {
            // Implementation
        }

        // ... other AIDL methods
    }
}
```

#### Client: AndroidManifest.xml

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.yourcompany.yourapp">

    <!-- Request permission to bind engine -->
    <uses-permission
        android:name="com.mtkresearch.breezeapp.permission.BIND_ENGINE_SERVICE" />

    <application>
        <!-- Your app components -->
    </application>
</manifest>
```

#### Client: Binding Code

```kotlin
class EngineClient(private val context: Context) {

    private var engineService: IAIEngineService? = null

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
            engineService = IAIEngineService.Stub.asInterface(service)
            // Engine ready to use
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            engineService = null
        }
    }
}
```

---

## Signature Whitelisting Strategy

### Authorized Client Applications

BreezeApp-engine maintains a conceptual whitelist of authorized applications through certificate matching:

| Application | Package Name | Authorization Method |
|------------|--------------|---------------------|
| BreezeApp (main) | `com.mtkresearch.breezeapp` | Same signing certificate |
| companion apps | `com.mtkresearch.breezeapp.client` | Same signing certificate |
| Third-party apps | Various | Coordinated certificates or API keys |

### Runtime Signature Verification

The `SignatureValidator` utility provides additional verification beyond Android's automatic check:

```kotlin
object SignatureValidator {

    private val AUTHORIZED_SIGNATURES = setOf(
        // SHA-256 hashes of authorized certificates
        "AB:CD:EF:12:34:...",  // BreezeApp certificate
        // Add more as needed
    )

    fun verifyCallerSignature(context: Context, callingUid: Int): Boolean {
        val packageManager = context.packageManager
        val packages = packageManager.getPackagesForUid(callingUid) ?: return false

        return packages.any { packageName ->
            try {
                val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageManager.getPackageInfo(
                        packageName,
                        PackageManager.GET_SIGNING_CERTIFICATES
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(
                        packageName,
                        PackageManager.GET_SIGNATURES
                    )
                }

                val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.signingInfo?.apkContentsSigners
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.signatures
                }

                signatures?.any { sig ->
                    val hash = computeSHA256(sig.toByteArray())
                    hash in AUTHORIZED_SIGNATURES
                } ?: false

            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(TAG, "Package not found: $packageName", e)
                false
            }
        }
    }

    private fun computeSHA256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString(":") { "%02X".format(it) }
    }
}
```

---

## Threat Model

### Attack Scenarios

#### A1: Unauthorized App Attempts to Bind

**Attack**: Malicious app tries to bind to BreezeApp-engine service

**Mitigation**:
1. **Android system**: Automatically denies binding (signature mismatch)
2. **No Binder returned**: Malicious app cannot access AIDL methods
3. **Audit log**: Unauthorized attempt logged for monitoring

**Risk**: ✅ **MITIGATED** - Signature protection prevents this attack

#### A2: Signature Spoofing

**Attack**: Attacker tries to forge or clone the signing certificate

**Mitigation**:
1. **Certificate in keystore**: Private key never leaves secure storage
2. **Play App Signing**: Google manages production certificate
3. **Certificate pinning**: Whitelist specific certificate hashes

**Risk**: ✅ **MITIGATED** - Cryptographically infeasible to forge certificates

#### A3: Certificate Compromise

**Attack**: Signing certificate private key is stolen

**Mitigation**:
1. **Key rotation**: Generate new App Signing Key in Play Console
2. **Gradual migration**: Update all apps to new certificate
3. **Revocation**: Remove compromised certificate from whitelist

**Risk**: ⚠️ **REQUIRES RESPONSE PLAN** - See Certificate Rotation section

#### A4: Replay/MITM Attacks

**Attack**: Attacker intercepts Binder IPC communication

**Mitigation**:
1. **Local IPC**: Binder uses kernel-level communication (not network)
2. **Process isolation**: Apps run in separate sandboxes
3. **SELinux**: Android enforces security policies

**Risk**: ✅ **MITIGATED** - Binder IPC is secure by design

#### A5: Privilege Escalation

**Attack**: Low-privilege app tries to gain engine access via another app

**Mitigation**:
1. **UID isolation**: Each app has unique UID
2. **Signature check per binding**: Cannot proxy through authorized app
3. **No intent forwarding**: Engine doesn't accept indirect bindings

**Risk**: ✅ **MITIGATED** - Android sandbox prevents escalation

### Security Boundaries

```
┌──────────────────────────────────────────────────────────┐
│ Trust Boundary: Apps with Matching Certificate          │
│                                                          │
│  ┌────────────┐      ┌────────────┐      ┌──────────┐  │
│  │ BreezeApp  │      │ BreezeApp  │      │ Engine   │  │
│  │   (main)   │◄────►│   Client   │◄────►│ Service  │  │
│  └────────────┘      └────────────┘      └──────────┘  │
│                                                          │
└──────────────────────────────────────────────────────────┘
           ▲                                    ▲
           │                                    │
           │ Blocked by signature mismatch     │
           │                                    │
      ┌────────────┐                      ┌─────────────┐
      │ Unauthorized│                      │ Malicious   │
      │   App 1     │                      │   App 2     │
      └────────────┘                      └─────────────┘
```

---

## Audit Logging

### Requirements

- **Log all unauthorized binding attempts**
- **Retain logs for 30 days** (compliance requirement)
- **Include**: Timestamp, UID, package name, signature hash
- **Privacy**: No PII collected (package names are not PII)
- **Storage**: Local file with log rotation

### Log Format

```json
{
  "timestamp": "2025-11-03T10:15:30Z",
  "event": "UNAUTHORIZED_BINDING",
  "callingUid": 10234,
  "packageName": "com.malicious.app",
  "signatureHash": "12:34:56:...",
  "result": "DENIED"
}
```

### Implementation

```kotlin
object AuditLogger {
    private const val LOG_FILE = "audit_security.log"
    private const val MAX_LOG_SIZE = 10 * 1024 * 1024  // 10 MB
    private const val RETENTION_DAYS = 30

    fun logUnauthorizedAttempt(context: Context, uid: Int, packageName: String) {
        val logEntry = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("event", "UNAUTHORIZED_BINDING")
            put("callingUid", uid)
            put("packageName", packageName)
            put("signatureHash", getSignatureHash(context, packageName))
            put("result", "DENIED")
        }

        writeLog(context, logEntry.toString())
        rotateLogsIfNeeded(context)
    }

    private fun rotateLogsIfNeeded(context: Context) {
        val logFile = File(context.filesDir, LOG_FILE)
        if (logFile.length() > MAX_LOG_SIZE) {
            val archiveName = "audit_security_${System.currentTimeMillis()}.log"
            logFile.renameTo(File(context.filesDir, archiveName))
        }
    }
}
```

---

## Performance Considerations

### Signature Verification Performance

**Requirement**: Signature verification must complete in **<10ms** (per NFR1)

**Optimization Strategies**:

1. **Caching**: Cache verified UIDs to avoid repeated lookups
   ```kotlin
   private val verifiedUIDs = ConcurrentHashMap<Int, Boolean>()
   private val cacheTimeout = 5 * 60 * 1000L  // 5 minutes
   ```

2. **Fast-path check**: Android system already verified signature before calling `onBind()`

3. **Lazy signature computation**: Only compute SHA-256 if needed for whitelisting

**Benchmark Target**:
- Initial verification: <10ms
- Cached verification: <1ms
- Overhead per AIDL call: <0.1ms

---

## Certificate Rotation Plan

### When to Rotate

- **Certificate compromise**: Immediate rotation required
- **Key expiration**: Rotate before expiration (certificates typically valid 25+ years)
- **Policy change**: Migrating to new security infrastructure

### Rotation Procedure

1. **Generate new certificate** in Play Console
2. **Update engine app** with new certificate
3. **Staged rollout** of updated engine (10% → 50% → 100% over 2 weeks)
4. **Update client apps** with new certificate
5. **Monitor compatibility** during transition
6. **Remove old certificate** from whitelist after all users migrated

### Transition Period

During rotation, **both old and new certificates are accepted**:

```kotlin
private val AUTHORIZED_SIGNATURES = setOf(
    "OLD_CERT_HASH",  // Remove after migration complete
    "NEW_CERT_HASH"   // New certificate
)
```

---

## Testing & Validation

### Test Scenarios

1. **Positive**: Authorized app can bind ✅
2. **Negative**: Unauthorized app cannot bind ✅
3. **Performance**: Verification completes in <10ms ✅
4. **Audit**: Unauthorized attempts are logged ✅
5. **Edge case**: Missing signature handled gracefully ✅

### Validation Checklist

- [ ] Signature-level permission defined in AndroidManifest.xml
- [ ] Service protected by custom permission
- [ ] SignatureValidator implemented and tested
- [ ] Audit logging functional
- [ ] Performance requirements met (<10ms)
- [ ] Error messages clear and actionable
- [ ] Certificate management procedures documented

---

## Security Compliance

### Android Security Best Practices

✅ **Use signature protection for IPC** - Implemented
✅ **Principle of least privilege** - Only bind permission granted
✅ **Defense in depth** - System check + custom verification
✅ **Audit logging** - All security events logged
✅ **Secure by default** - No fallback to weaker security

### Privacy Compliance

- **No PII collected**: Package names are not personal data
- **Minimal data retention**: 30-day log retention only
- **Transparent**: Users informed via Play Store description

---

## References

- [Android Permissions Documentation](https://developer.android.com/guide/topics/permissions/overview)
- [Protection Levels](https://developer.android.com/guide/topics/manifest/permission-element#plevel)
- [AIDL Security](https://developer.android.com/develop/background-work/services/aidl)
- [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)
- [Android Security Best Practices](https://developer.android.com/topic/security/best-practices)

---

**Document Version**: 1.0
**Author**: BreezeApp Team
**Review Status**: Ready for Implementation
