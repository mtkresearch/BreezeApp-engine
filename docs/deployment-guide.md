# BreezeApp-engine Deployment Guide

**Version**: 1.0
**Last Updated**: 2025-11-03
**Target**: Google Play Store Release

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Play App Signing Setup](#play-app-signing-setup)
4. [Signature Requirements](#signature-requirements)
5. [Signature Verification Timing](#signature-verification-timing)
6. [Build Configuration](#build-configuration)
7. [Testing Before Release](#testing-before-release)
8. [Google Play Console Configuration](#google-play-console-configuration)
9. [Staged Rollout Strategy](#staged-rollout-strategy)
10. [Post-Deployment Monitoring](#post-deployment-monitoring)
11. [Troubleshooting](#troubleshooting)

---

## Overview

This guide provides step-by-step instructions for deploying BreezeApp-engine to the Google Play Store. The engine uses signature-level permissions to ensure only authorized applications can bind to its AI inference service.

**Key Deployment Principles**:
- ✅ All apps in the ecosystem share the same signing certificate
- ✅ Play App Signing manages the production certificate
- ✅ Signature verification happens at service binding time
- ✅ Staged rollout minimizes risk of breaking changes

---

## Prerequisites

### Development Environment

- **Android Studio**: Arctic Fox (2020.3.1) or later
- **Android SDK**: API 34+ (Android 14)
- **Java**: JDK 11 or later
- **Kotlin**: 1.9+
- **Gradle**: 8.0+ with Android Gradle Plugin 8.x

### Accounts & Access

- ✅ Google Play Console developer account
- ✅ Access to BreezeApp organization in Play Console
- ✅ Permissions to manage app signing keys
- ✅ Access to upload APK/AAB files

### Required Files

- ✅ `app/release/BreezeApp-engine-release.aab` (Android App Bundle)
- ✅ Release notes (Markdown format)
- ✅ Screenshots and feature graphics
- ✅ Store listing content (English & Chinese)

---

## Play App Signing Setup

### Why Play App Signing?

Play App Signing provides:
- **Security**: Google stores your app signing key securely
- **Recovery**: Generate new upload keys if lost
- **Consistency**: Same signature across all ecosystem apps
- **Automatic signing**: Google signs releases for you

### Initial Setup (First Time)

#### Step 1: Enable Play App Signing

1. Open [Google Play Console](https://play.google.com/console)
2. Select **BreezeApp-engine** app
3. Navigate to **Release** → **Setup** → **App integrity**
4. Click **Use Google-generated key** (recommended)
   - OR **Use an existing key** (if migrating from manual signing)

5. Review and accept the terms
6. Click **Enable**

Google will generate an app signing key and store it securely.

#### Step 2: Download Certificate Information

1. In **App integrity** page, find **App signing key certificate**
2. Click **Download certificate (DER)**
3. Save as `app-signing-cert.der`

4. Extract SHA-256 fingerprint:
   ```bash
   keytool -printcert -file app-signing-cert.der
   ```

   Output example:
   ```
   Owner: CN=Unknown, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown
   Issuer: CN=Unknown, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown
   Serial number: 1a2b3c4d
   Valid from: Mon Jan 01 00:00:00 UTC 2025 until: Fri Dec 31 23:59:59 UTC 2074
   Certificate fingerprints:
       SHA1: 12:34:56:78:9A:BC:DE:F0:12:34:56:78:9A:BC:DE:F0:12:34:56:78
       SHA256: AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89
   ```

5. **Save the SHA-256 fingerprint** - you'll need it for:
   - Whitelisting authorized client apps
   - Signature validation in `SignatureValidator.kt`
   - Documentation and integration guides

#### Step 3: Create Upload Key

The upload key is used to sign APK/AAB files before uploading to Play Console.

```bash
# Generate upload keystore
keytool -genkey -v \
  -keystore upload-keystore.jks \
  -alias upload \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass YOUR_STORE_PASSWORD \
  -keypass YOUR_KEY_PASSWORD

# Verify keystore
keytool -list -v -keystore upload-keystore.jks -storepass YOUR_STORE_PASSWORD
```

**Security Best Practices**:
- ⚠️ **Never commit keystore files to version control**
- ⚠️ Store passwords in a password manager
- ⚠️ Back up keystore in secure location (encrypted cloud storage)
- ⚠️ Use different keystores for debug/release builds

#### Step 4: Configure Gradle Signing

Add to `app/build.gradle.kts`:

```kotlin
android {
    // ... other config

    signingConfigs {
        create("release") {
            storeFile = file("../upload-keystore.jks")
            storePassword = System.getenv("UPLOAD_STORE_PASSWORD")
            keyAlias = "upload"
            keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

Set environment variables:
```bash
export UPLOAD_STORE_PASSWORD="your_store_password"
export UPLOAD_KEY_PASSWORD="your_key_password"
```

### Subsequent Apps in Ecosystem

For **BreezeApp** (main client) and **companion apps**:

1. Use the **same Google Play developer account**
2. Enable Play App Signing (Google will use the same app signing key)
3. Create separate upload keys for each app
4. Verify all apps share the same app signing certificate:

```bash
# Download certificates for all apps
# app1-signing-cert.der, app2-signing-cert.der, engine-signing-cert.der

# Compare SHA-256 fingerprints - they MUST match
keytool -printcert -file engine-signing-cert.der | grep SHA256
keytool -printcert -file app1-signing-cert.der | grep SHA256
keytool -printcert -file app2-signing-cert.der | grep SHA256
```

---

## Signature Requirements

### Certificate Consistency Rules

**CRITICAL**: For signature-level permissions to work, all ecosystem apps must share the **same app signing certificate**.

| Component | Signing Certificate | Requirement |
|-----------|-------------------|-------------|
| BreezeApp-engine | App Signing Key A | ✅ Required |
| BreezeApp (main) | App Signing Key A | ✅ Must match engine |
| companion apps | App Signing Key A | ✅ Must match engine |
| Third-party apps | App Signing Key A OR authorized separately | ⚠️ Coordinate with team |

### Why Certificate Consistency Matters

```
┌─────────────────────────────────────────────────────────┐
│ Scenario 1: Matching Certificates ✅                    │
├─────────────────────────────────────────────────────────┤
│ Engine: Signed with Cert A                             │
│ Client: Signed with Cert A                             │
│ Result: ✅ Binding succeeds, AIDL methods accessible   │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ Scenario 2: Mismatched Certificates ❌                  │
├─────────────────────────────────────────────────────────┤
│ Engine: Signed with Cert A                             │
│ Client: Signed with Cert B                             │
│ Result: ❌ SecurityException, binding denied           │
└─────────────────────────────────────────────────────────┘
```

### Verifying Certificate Consistency

Before releasing any app in the ecosystem:

```bash
#!/bin/bash
# verify-signatures.sh - Check all apps have matching signatures

APPS=("engine.apk" "breezeapp.apk" "companion-app.apk")
FINGERPRINTS=()

for app in "${APPS[@]}"; do
    echo "Checking $app..."
    fingerprint=$(apksigner verify --print-certs "$app" 2>/dev/null | grep "Signer #1 certificate SHA-256" | awk '{print $NF}')
    FINGERPRINTS+=("$fingerprint")
    echo "  Fingerprint: $fingerprint"
done

# Check all fingerprints match
first="${FINGERPRINTS[0]}"
for fp in "${FINGERPRINTS[@]}"; do
    if [ "$fp" != "$first" ]; then
        echo "❌ ERROR: Signature mismatch detected!"
        exit 1
    fi
done

echo "✅ All signatures match!"
```

---

## Signature Verification Timing

### When Signatures Are Verified

```
┌─────────────────────────────────────────────────────────────┐
│ Timeline: Service Binding with Signature Verification      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│ T0: Client calls bindService()                             │
│  ↓                                                          │
│ T1: Android System checks permission                       │
│     - Is BIND_AI_SERVICE declared in client manifest?      │
│     - Does client certificate match engine certificate?    │
│     - If NO → SecurityException, binding fails             │
│  ↓                                                          │
│ T2: System calls AIEngineService.onBind()                  │
│     - Additional verification by SignatureValidator        │
│     - Defensive check (optional but recommended)           │
│  ↓                                                          │
│ T3: Engine returns IBinder or null                         │
│     - If signature valid: return AIDL stub                 │
│     - If signature invalid: return null, log attempt       │
│  ↓                                                          │
│ T4: Client receives onServiceConnected() callback          │
│     - Client can now use AIDL methods                      │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Verification Points

#### Primary Verification: Android System

**When**: Before `onBind()` is called
**Who**: Android's permission system
**How**: Compares client and service signing certificates
**Result**: SecurityException if mismatch (binding never reaches service)

**Automatic behavior** - no code needed, enforced by:
```xml
<service
    android:permission="com.mtkresearch.breezeapp.engine.permission.BIND_AI_SERVICE" />
```

#### Secondary Verification: Service Code (Optional)

**When**: Inside `onBind()` method
**Who**: `SignatureValidator.verifyCallerSignature()`
**How**: Manually check caller's certificate
**Result**: Return `null` if verification fails

**Defense in depth** - additional security layer:
```kotlin
override fun onBind(intent: Intent?): IBinder? {
    val callingUid = Binder.getCallingUid()

    if (!SignatureValidator.verifyCallerSignature(this, callingUid)) {
        Log.w(TAG, "Unauthorized binding attempt from UID: $callingUid")
        return null  // Deny binding
    }

    return binder  // Allow binding
}
```

### Performance Requirements

From **NFR1** (Non-Functional Requirements):

| Metric | Target | Measurement Method |
|--------|--------|-------------------|
| Signature verification | **<10ms** | Measured in `SignatureValidator` |
| Service binding latency | **<100ms** | End-to-end bindService() to onServiceConnected() |
| AIDL call overhead | **<1ms** | Per method invocation |

Verification timing measured with:
```kotlin
val startTime = System.currentTimeMillis()
val isValid = SignatureValidator.verifyCallerSignature(context, uid)
val duration = System.currentTimeMillis() - startTime

if (duration > 10) {
    Log.w(TAG, "Signature verification took ${duration}ms (exceeds 10ms target)")
}
```

---

## Build Configuration

### Gradle Configuration

Update `app/build.gradle.kts`:

```kotlin
android {
    namespace = "com.mtkresearch.breezeapp.engine"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mtkresearch.breezeapp.engine"
        minSdk = 34
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}
```

### ProGuard Rules

Add to `proguard-rules.pro`:

```proguard
# Keep AIDL interfaces
-keep interface com.mtkresearch.breezeapp.engine.** { *; }
-keep class com.mtkresearch.breezeapp.engine.**$Stub { *; }

# Keep signature validation
-keep class com.mtkresearch.breezeapp.engine.security.SignatureValidator { *; }

# Keep version compatibility
-keep class com.mtkresearch.breezeapp.engine.version.** { *; }

# Keep service
-keep class com.mtkresearch.breezeapp.engine.service.AIEngineService { *; }
```

### Build Release AAB

```bash
# Clean build
./gradlew clean

# Build release AAB
./gradlew :app:bundleRelease

# Output location
ls -lh app/build/outputs/bundle/release/app-release.aab
```

---

## Testing Before Release

### Pre-Release Checklist

- [ ] All unit tests pass (`./gradlew test`)
- [ ] All integration tests pass (`./gradlew connectedAndroidTest`)
- [ ] Signature verification works with test certificates
- [ ] Service binding succeeds with authorized apps
- [ ] Service binding fails with unauthorized apps
- [ ] Performance requirements met (<10ms signature check)
- [ ] ProGuard doesn't break AIDL interfaces
- [ ] App runs on Android 14+ devices

### Testing Signature Verification

**Test 1: Authorized App (Should Succeed)**

1. Build and install engine with test signing key
2. Build and install client app with **same signing key**
3. Client attempts to bind service
4. **Expected**: Binding succeeds, AIDL methods work

**Test 2: Unauthorized App (Should Fail)**

1. Engine installed with signing key A
2. Install different app with signing key B
3. Unauthorized app attempts to bind
4. **Expected**: SecurityException or binding returns null

**Test 3: Performance Test**

```kotlin
@Test
fun testSignatureVerificationPerformance() {
    val iterations = 100
    val measurements = mutableListOf<Long>()

    repeat(iterations) {
        val start = System.nanoTime()
        SignatureValidator.verifyCallerSignature(context, testUid)
        val duration = System.nanoTime() - start
        measurements.add(duration / 1_000_000)  // Convert to ms
    }

    val average = measurements.average()
    assertTrue("Average verification time ${average}ms exceeds 10ms", average < 10.0)
}
```

---

## Google Play Console Configuration

### App Information

1. Navigate to **Store presence** → **Main store listing**
2. Fill in:
   - **App name**: BreezeApp AI Engine
   - **Short description**: Core AI engine for BreezeApp applications. Requires authorized companion app.
   - **Full description**: (See [Play Store Content Guide](./play-store/description-en.md))

3. **Category**: Tools
4. **Tags**: AI, Machine Learning, Service

### Content Rating

1. Navigate to **Policy** → **App content**
2. Complete **Content rating** questionnaire
3. This is a service app with no direct user content
4. Rating: E (Everyone)

### Privacy Policy

1. Required for apps on Play Store
2. URL: `https://breezeapp.mtkresearch.com/privacy`
3. Key points to include:
   - No user data collected
   - On-device AI processing only
   - Signature verification for security (non-PII)

---

## Staged Rollout Strategy

### Rollout Phases

From **FR6.6** (Version Management):

| Version Type | Rollout Schedule | Rationale |
|--------------|------------------|-----------|
| **PATCH** (1.0.0 → 1.0.1) | 50% → 100% (24 hours) | Low risk, bug fixes only |
| **MINOR** (1.0 → 1.1) | 10% → 50% → 100% (7 days) | New features, monitor compatibility |
| **MAJOR** (1.x → 2.0) | 5% → 20% → 50% → 100% (14 days) | Breaking changes, careful monitoring |

### Configuring Staged Rollout

1. In Play Console, go to **Release** → **Production**
2. Create new release
3. Under **Rollout percentage**, set initial percentage (e.g., 10%)
4. Click **Review release** → **Start rollout**

5. Monitor for 24-48 hours:
   - Crash rate
   - ANR (Application Not Responding) rate
   - User reviews
   - Binding success rate (via analytics)

6. If metrics are healthy, increase percentage:
   - **Release** → **Production** → **Manage** → **Update rollout**
   - Increase percentage (e.g., 10% → 50%)

7. Continue until 100% rollout

### Rollback Procedure

If critical issues detected:

1. **Halt rollout**:
   - **Release** → **Production** → **Manage** → **Halt rollout**

2. **Fix the issue**:
   - Create hotfix branch
   - Increment patch version (e.g., 1.1.0 → 1.1.1)
   - Build and test thoroughly

3. **Release hotfix**:
   - Upload new AAB
   - Start with smaller percentage (5%)
   - Monitor closely before increasing

---

## Post-Deployment Monitoring

### Key Metrics

Monitor these metrics in Play Console and Firebase:

| Metric | Target | Alert Threshold |
|--------|--------|----------------|
| Crash-free users | >99.5% | <99.0% |
| ANR rate | <0.1% | >0.5% |
| Uninstall rate | <5% | >10% |
| 1-star reviews | <5% | >10% |

### Signature Verification Metrics

Track in Firebase Analytics:

```kotlin
// Log successful binding
analytics.logEvent("engine_binding_success") {
    param("client_package", packageName)
    param("engine_version", BuildConfig.VERSION_NAME)
}

// Log unauthorized attempts
analytics.logEvent("engine_binding_denied") {
    param("client_package", packageName)
    param("reason", "signature_mismatch")
}
```

Monitor:
- **Unauthorized binding attempts**: Should be near zero
- **Binding success rate**: Should be >99%
- **Signature verification time**: Should be <10ms average

---

## Troubleshooting

### Common Signature Issues

#### Issue 1: "Permission denied" when binding

**Symptoms**:
- Client app cannot bind to engine
- SecurityException in logcat
- No `onServiceConnected()` callback

**Causes**:
1. Client and engine signed with different certificates
2. Client didn't declare `<uses-permission>` in manifest
3. Engine permission not declared correctly

**Solutions**:

```bash
# Check signatures match
apksigner verify --print-certs engine.apk | grep SHA-256
apksigner verify --print-certs client.apk | grep SHA-256

# Verify permission in client manifest
adb shell dumpsys package com.yourapp.client | grep BIND_AI_SERVICE

# Check engine permission definition
adb shell dumpsys package com.mtkresearch.breezeapp.engine | grep BIND_AI_SERVICE
```

#### Issue 2: Binding works on debug but fails on release

**Cause**: Debug and release builds use different signing keys

**Solution**:
1. Ensure release builds use Play App Signing
2. Test with release-signed APKs before Play Store upload
3. Use internal testing track for pre-release validation

#### Issue 3: "Signature verification timeout"

**Symptoms**: Binding takes >100ms, performance degraded

**Causes**:
1. Signature caching disabled
2. Too many apps to check in `getPackagesForUid()`
3. Slow device or heavy system load

**Solutions**:
```kotlin
// Enable caching in SignatureValidator
private val signatureCache = LruCache<Int, Boolean>(maxSize = 50)

// Cache results for 5 minutes
fun verifyCallerSignature(context: Context, uid: Int): Boolean {
    val cached = signatureCache.get(uid)
    if (cached != null) return cached

    val result = performVerification(context, uid)
    signatureCache.put(uid, result)
    return result
}
```

### Getting Help

- **Documentation**: [Security Model](./security/security-model.md)
- **Integration Guide**: [Integration Guide](./integration-guide.md)
- **GitHub Issues**: https://github.com/mtkresearch/BreezeApp-engine/issues
- **Support Email**: breezeapp-support@mtkresearch.com

---

## Appendix: Command Reference

### Keystore Management

```bash
# Generate new keystore
keytool -genkey -v -keystore my-release-key.jks -alias my-key-alias \
  -keyalg RSA -keysize 2048 -validity 10000

# List keys in keystore
keytool -list -v -keystore my-release-key.jks

# Export certificate
keytool -export -alias my-key-alias -keystore my-release-key.jks \
  -file my-cert.der

# Print certificate info
keytool -printcert -file my-cert.der
```

### APK/AAB Signing

```bash
# Sign APK manually (if not using Gradle)
apksigner sign --ks my-release-key.jks --ks-key-alias my-key-alias \
  --out app-signed.apk app-unsigned.apk

# Verify signature
apksigner verify --print-certs app-signed.apk

# Check if APK is properly signed
apksigner verify --verbose app-signed.apk
```

### Play Console CLI (Optional)

Install Google Play Developer API:
```bash
pip install google-api-python-client google-auth-httplib2 google-auth-oauthlib
```

---

**Document Version**: 1.0
**Maintained By**: BreezeApp Team
**Next Review**: Before each major release
