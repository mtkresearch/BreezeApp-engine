# Security Best Practices

**Purpose**: Security guidelines for BreezeApp-engine development and deployment
**Last Updated**: 2025-11-10

---

## Overview

BreezeApp-engine implements a defense-in-depth security model with multiple layers:

1. **System-level**: Android signature-level permissions
2. **Application-level**: Signature verification in service binding
3. **Data-level**: Input validation and sanitization
4. **Privacy-level**: On-device processing, no data collection

---

## Signature-Based Security

### How It Works

```
Client App                    BreezeApp Engine
    |                               |
    |------ bindService() --------->|
    |                               |
    |      Android checks           |
    |      BIND_AI_SERVICE          |
    |      permission               |
    |                               |
    |<----- onBind() called --------|
    |                               |
    |         Get calling UID       |
    |         Extract cert          |
    |         Verify SHA-256        |
    |                               |
    |<----- Return binder ---------|  (if authorized)
    |<----- Return null -----------|  (if unauthorized)
```

### Implementation

**1. Define Custom Permission**

`AndroidManifest.xml`:
```xml
<permission
    android:name="com.mtkresearch.breezeapp.permission.BIND_ENGINE_SERVICE"
    android:protectionLevel="signature" />
```

**2. Enforce in Service**

```xml
<service
    android:name=".BreezeAppEngineService"
    android:permission="com.mtkresearch.breezeapp.permission.BIND_ENGINE_SERVICE"
    android:exported="true" />
```

**3. Verify in Code**

```kotlin
override fun onBind(intent: Intent?): IBinder? {
    val callingUid = Binder.getCallingUid()

    if (!SignatureValidator.verifyCallerSignature(this, callingUid)) {
        Log.w(TAG, "Unauthorized binding attempt from UID: $callingUid")
        return null  // Deny binding
    }

    return binder
}
```

---

## Input Validation

### AIDL Method Security

All AIDL methods must validate inputs:

```kotlin
override fun inferText(input: String, params: Bundle?): Bundle {
    // 1. Null checks
    require(input.isNotEmpty()) { "Input cannot be empty" }

    // 2. Length limits (prevent DoS)
    require(input.length <= MAX_PROMPT_LENGTH) {
        "Prompt exceeds maximum length: $MAX_PROMPT_LENGTH"
    }

    // 3. Sanitize input
    val sanitized = input.trim().take(MAX_PROMPT_LENGTH)

    // 4. Validate Bundle parameters
    params?.let { validateParams(it) }

    // 5. Process request
    return processInference(sanitized, params)
}

private fun validateParams(params: Bundle) {
    // Check for malicious keys
    for (key in params.keySet()) {
        require(key.matches(Regex("[a-zA-Z0-9_]+"))) {
            "Invalid parameter key: $key"
        }
    }

    // Validate specific parameters
    params.getInt("max_tokens", 0).let { tokens ->
        require(tokens in 1..MAX_TOKENS) {
            "max_tokens must be between 1 and $MAX_TOKENS"
        }
    }
}
```

### File Descriptor Validation

```kotlin
override fun recognizeSpeech(audioFd: ParcelFileDescriptor): Bundle {
    try {
        // Validate file descriptor
        require(audioFd.statSize >= 0) { "Invalid file descriptor" }

        // Limit file size (prevent DoS)
        val maxSize = 10 * 1024 * 1024 // 10MB
        require(audioFd.statSize <= maxSize) {
            "Audio file too large: ${audioFd.statSize} bytes"
        }

        // Process audio
        return processAudio(audioFd)
    } finally {
        audioFd.close()
    }
}
```

---

## Data Privacy

### Zero Data Collection

BreezeApp-engine follows strict privacy principles:

```kotlin
// ✅ CORRECT: All processing on-device
fun processInference(prompt: String): String {
    val model = loadLocalModel()
    return model.infer(prompt)  // Stays on device
}

// ❌ INCORRECT: Never send data externally
fun processInference(prompt: String): String {
    val api = NetworkClient()
    return api.sendToCloud(prompt)  // NEVER DO THIS
}
```

### No Logging of User Data

```kotlin
// ✅ CORRECT: Log technical info only
Log.d(TAG, "Inference started, model=${modelId}")

// ❌ INCORRECT: Never log user input
Log.d(TAG, "User prompt: $userPrompt")  // NEVER DO THIS
```

### Audit Logging

Audit logs are for security events only:

```kotlin
private fun logUnauthorizedAttempt(uid: Int, packageName: String) {
    val auditEntry = JSONObject().apply {
        put("timestamp", System.currentTimeMillis())
        put("uid", uid)
        put("package", packageName)
        put("event", "UNAUTHORIZED_BINDING")
        put("result", "DENIED")
        // ✅ No user data, only metadata
    }

    auditLogger.append(auditEntry.toString())
}
```

---

## Dependency Security

### Scan for Vulnerabilities

```bash
# Add OWASP Dependency-Check plugin
# build.gradle (project)
plugins {
    id "org.owasp.dependencycheck" version "8.4.0"
}

# Run vulnerability scan
./gradlew dependencyCheckAnalyze

# Report: build/reports/dependency-check-report.html
```

### Keep Dependencies Updated

```bash
# Check for updates
./gradlew dependencyUpdates

# Update dependencies in build.gradle
```

### Use Trusted Sources Only

```gradle
repositories {
    google()           // ✅ Official Google repository
    mavenCentral()     // ✅ Official Maven Central
    // ❌ Avoid unknown repositories
}
```

---

## Code Obfuscation

### ProGuard/R8 Rules

**Critical**: AIDL interfaces must NOT be obfuscated:

```proguard
# Keep AIDL interfaces
-keep class com.mtkresearch.breezeapp.engine.IAIEngineService { *; }
-keep class com.mtkresearch.breezeapp.engine.IInferenceCallback { *; }
-keep class com.mtkresearch.breezeapp.engine.IStreamCallback { *; }
-keep class com.mtkresearch.breezeapp.engine.IModelManager { *; }

# Keep public API classes
-keep public class com.mtkresearch.breezeapp.engine.** { *; }

# Remove debug logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
```

---

## Network Security

### No Network by Default

BreezeApp-engine does not require internet for core functionality:

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
<!-- Only for optional model downloads -->
```

### HTTPS Only (if network used)

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

---

## File System Security

### App-Private Storage

All data must be stored in app-private directories:

```kotlin
// ✅ CORRECT: App-private storage
val modelDir = context.filesDir.resolve("models")
val file = File(modelDir, "llama-model.bin")

// ❌ INCORRECT: External storage (world-readable)
val file = File(Environment.getExternalStorageDirectory(), "model.bin")
```

### File Permissions

```kotlin
// Set restrictive permissions
file.setReadable(false, false)  // Not readable by others
file.setReadable(true, true)    // Readable by owner only
file.setWritable(false, false)  // Not writable by others
file.setWritable(true, true)    // Writable by owner only
```

---

## IPC Security

### Binder Transaction Limits

Respect Binder transaction size limits (<1MB):

```kotlin
override fun inferText(input: String, params: Bundle?): Bundle {
    // Check input size
    val inputSize = input.toByteArray().size
    require(inputSize < BINDER_SIZE_LIMIT) {
        "Input too large: $inputSize bytes (limit: $BINDER_SIZE_LIMIT)"
    }

    // For large outputs, use ParcelFileDescriptor
    val result = processInference(input)
    return if (result.length > BINDER_SIZE_LIMIT) {
        bundleWithFileDescriptor(result)
    } else {
        Bundle().apply { putString("result", result) }
    }
}

companion object {
    const val BINDER_SIZE_LIMIT = 900_000  // ~900KB (safety margin)
}
```

### Thread Safety

```kotlin
class AIEngineService : Service() {
    // ✅ CORRECT: Thread-safe access
    private val lock = ReentrantLock()
    private var currentModel: Model? = null

    fun loadModel(modelId: String) {
        lock.withLock {
            currentModel?.unload()
            currentModel = ModelLoader.load(modelId)
        }
    }
}
```

---

## Security Checklist

### Before Release

- [ ] No hardcoded secrets or API keys
- [ ] Production certificate SHA-256 updated
- [ ] Input validation on all AIDL methods
- [ ] No user data in logs
- [ ] ProGuard rules verified
- [ ] Dependency vulnerability scan clean
- [ ] File permissions restrictive (0600)
- [ ] No cleartext traffic allowed
- [ ] Signature verification tested
- [ ] Audit logging implemented

### Regular Security Reviews

- [ ] Monthly dependency scans
- [ ] Quarterly security audit
- [ ] Review audit logs for suspicious activity
- [ ] Update security documentation
- [ ] Respond to security reports within 24 hours

---

## Incident Response

### If Vulnerability Discovered

1. **Assess Severity** (CVSS score)
2. **Critical (9.0-10.0)**: Emergency patch within 24 hours
3. **High (7.0-8.9)**: Patch within 72 hours
4. **Medium (4.0-6.9)**: Patch in next release
5. **Low (0.1-3.9)**: Document and plan fix

### Security Disclosure

Report security issues to: `security@mtkresearch.com`

**Do not** report publicly until patch is available.

---

## Resources

- **OWASP Mobile Security**: https://owasp.org/www-project-mobile-security/
- **Android Security Best Practices**: https://developer.android.com/topic/security/best-practices
- **CVE Database**: https://cve.mitre.org/

---

**Document Version**: 1.0
**Maintained By**: BreezeApp Team
**Last Updated**: 2025-11-10
