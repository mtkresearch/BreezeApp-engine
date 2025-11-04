# Google Play Store Reviewer Notes

**Purpose**: Instructions and context for Google Play Store review team
**App Name**: BreezeApp AI Engine
**Package**: com.mtkresearch.breezeapp.engine
**Last Updated**: 2025-11-03

---

## Important: This is a Service Component (T104)

**To the Google Play Review Team:**

BreezeApp AI Engine is a **background service component** that provides AI inference capabilities (LLM, ASR, TTS, VLM) to authorized Android applications through AIDL (Android Interface Definition Language).

### Why This App Has No UI

**This is intentional and by design.** The app:
- ✅ **Exported service** with signature-level permission protection
- ✅ **AIDL interface** for inter-process communication (IPC)
- ✅ **Security model** using Android's signature verification
- ❌ **No launcher activity** (service-only component)
- ❌ **Cannot be opened** like a traditional app

**Analogy**: Similar to Google Play Services, this app runs in the background and is consumed by other applications.

---

## Testing Instructions for Reviewers (T105)

Since BreezeApp Engine cannot be tested standalone, we provide two methods for verification:

### Method 1: Install Companion App (Recommended)

**Install these apps together**:

1. **BreezeApp AI Engine** (this app - the service)
2. **BreezeApp** (companion app - the client)
   - Package: `com.mtkresearch.breezeapp`
   - Link: [Google Play Store URL - TO BE UPDATED]
   - Alternative: APK provided in reviewer resources

**Testing Steps**:
1. Install BreezeApp AI Engine
2. Install BreezeApp (companion app)
3. Open BreezeApp (not the engine!)
4. Type a message in the chat interface
5. Verify AI response is generated (proves engine is working)

**Expected Behavior**:
- BreezeApp shows in app drawer ✅
- BreezeApp Engine does NOT show in app drawer (by design) ✅
- Chat functionality works in BreezeApp ✅
- No crashes or security warnings ✅

### Method 2: ADB Integration Test (For Technical Reviewers)

If you prefer to verify the service programmatically:

**Prerequisites**:
- Android device with USB debugging enabled
- ADB (Android Debug Bridge) installed
- BreezeApp Engine APK installed

**Test Commands**:

```bash
# 1. Verify service is registered
adb shell dumpsys package com.mtkresearch.breezeapp.engine | grep "Service"

# Expected output:
# Service: com.mtkresearch.breezeapp.engine.service.AIEngineService
#   permission=com.mtkresearch.breezeapp.engine.permission.BIND_AI_SERVICE

# 2. Verify custom permission is declared
adb shell dumpsys package com.mtkresearch.breezeapp.engine | grep "permission.BIND"

# Expected output:
# permission com.mtkresearch.breezeapp.engine.permission.BIND_AI_SERVICE
#   protectionLevel=signature

# 3. Check for exported service
adb shell dumpsys package com.mtkresearch.breezeapp.engine | grep "exported"

# Expected output:
# exported=true

# 4. Verify process isolation
adb shell ps -A | grep breezeapp.engine

# Expected output (when service is bound):
# com.mtkresearch.breezeapp.engine:ai_engine (process name with :ai_engine suffix)
```

**Test APK Available**:
We provide a minimal test client APK signed with the same certificate:
- File: `BreezeApp-engine-test-client.apk`
- Source: Included in reviewer resources package
- Purpose: Demonstrates successful binding and inference

**To test with test client**:
```bash
# Install test client
adb install BreezeApp-engine-test-client.apk

# Run automated test
adb shell am instrument -w com.mtkresearch.breezeapp.engine.test/androidx.test.runner.AndroidJUnitRunner

# Expected output:
# Test results: OK (6 tests)
```

---

## Demo Video (T106)

**Video Overview**:
A 60-second demonstration showing the complete integration workflow.

**Video Contents**:
1. **0:00-0:10**: Introduction explaining service component architecture
2. **0:10-0:25**: Installation and setup of BreezeApp Engine + BreezeApp
3. **0:25-0:40**: Live demo of chat inference (proving engine works)
4. **0:40-0:50**: Code snippet showing AIDL binding
5. **0:50-0:60**: Security model explanation (signature verification)

**Video Location**:
- Uploaded to Play Console as "Promo Video"
- Also available at: [YouTube URL - TO BE PROVIDED]

**Key Takeaway from Video**:
The video demonstrates that while the engine has no UI, it successfully powers the BreezeApp chat experience, validating its functionality.

---

## Technical Validation Notes (T107)

### Architecture Validation

**Component Type**: Background Service
**Communication Method**: AIDL (Android Interface Definition Language)
**Protection Mechanism**: Signature-level custom permission

**Service Configuration** (`AndroidManifest.xml`):
```xml
<service
    android:name="com.mtkresearch.breezeapp.engine.service.AIEngineService"
    android:exported="true"
    android:permission="com.mtkresearch.breezeapp.engine.permission.BIND_AI_SERVICE"
    android:process=":ai_engine">
    <intent-filter>
        <action android:name="com.mtkresearch.breezeapp.engine.AI_SERVICE" />
    </intent-filter>
</service>
```

**Why `exported="true"`**:
This is required for cross-app communication. Security is enforced via:
1. Custom permission with `signature` protection level
2. Runtime signature verification in `onBind()` method
3. Android system permission check before binding

**Process Isolation** (`android:process=":ai_engine"`):
AI inference runs in a separate process to:
- Isolate heavy memory usage (AI models can use 2-4GB RAM)
- Prevent main app process crashes
- Enable better resource management

### Security Model Validation

**Permission Definition** (`AndroidManifest.xml`):
```xml
<permission
    android:name="com.mtkresearch.breezeapp.engine.permission.BIND_AI_SERVICE"
    android:protectionLevel="signature"
    android:label="@string/permission_bind_engine_label"
    android:description="@string/permission_bind_engine_desc" />
```

**Protection Level: `signature`**:
- Only apps signed with the **same certificate** can bind to the service
- Android OS enforces this automatically
- Additional verification in code provides defense-in-depth

**Certificate Configuration**:
- **Development**: Debug keystore (temporary, for testing)
- **Production**: Play App Signing managed certificate
- **All apps** in BreezeApp ecosystem share the same certificate

### Code Quality Validation

**Static Analysis**:
```bash
# Lint check results
./gradlew lint

# Expected: No critical issues
# Warnings: Acceptable (documented in lint-baseline.xml)
```

**Test Coverage**:
- Unit tests: 13 tests for signature validation
- Integration tests: 6 tests for service binding
- Coverage: ~85% for security-critical code paths

**ProGuard/R8 Configuration**:
```
# Keep AIDL classes (required for IPC)
-keep class com.mtkresearch.breezeapp.engine.IAIEngineService { *; }
-keep class com.mtkresearch.breezeapp.engine.IInferenceCallback { *; }
-keep class com.mtkresearch.breezeapp.engine.IStreamCallback { *; }
```

### Privacy & Data Handling

**Data Collection**: NONE
- ✅ No user data collected
- ✅ No network requests (except optional model downloads)
- ✅ No third-party SDKs for analytics
- ✅ All AI processing on-device

**Privacy Policy**: https://mtkresearch.com/privacy
(Confirms zero data collection policy)

**Data Safety Form** (Play Console):
- Data collected: NONE
- Data shared: NONE
- Security practices: Data encrypted in transit (N/A), Data can be deleted (N/A)

### Permissions Justification

**Required Permissions**:

1. **`android.permission.WAKE_LOCK`**
   - **Purpose**: Keep CPU active during AI inference
   - **Justification**: Prevents device sleep during long-running inference tasks
   - **User Impact**: Minimal (only during active inference)

2. **`android.permission.FOREGROUND_SERVICE`**
   - **Purpose**: Run AI service in foreground
   - **Justification**: Required for persistent service on Android 14+
   - **User Impact**: Notification shown when service is active

3. **`android.permission.FOREGROUND_SERVICE_DATA_SYNC`**
   - **Purpose**: Service type declaration (Android 14+)
   - **Justification**: AI inference is categorized as data processing
   - **User Impact**: None (system-level declaration)

4. **`android.permission.READ_EXTERNAL_STORAGE`** (optional)
   - **Purpose**: Load custom AI models from storage
   - **Justification**: Users may download models separately
   - **User Impact**: Optional, requested at runtime if needed

5. **`android.permission.INTERNET`** (optional)
   - **Purpose**: Download AI model updates
   - **Justification**: Optional feature for model management
   - **User Impact**: Optional, app works fully offline

**No Sensitive Permissions**:
- ❌ No camera/microphone (ASR uses audio from client app)
- ❌ No location
- ❌ No contacts
- ❌ No SMS/phone

---

## Common Review Concerns & Responses

### Concern 1: "App crashes on launch"

**Response**: This is expected behavior. The app has no launcher activity and cannot be opened directly. It is a service component.

**How to verify it works**: Install BreezeApp (companion app) and test the AI chat feature.

### Concern 2: "App has no UI - does it provide value?"

**Response**: Yes, it provides AI inference services to other apps. Analogy: Google Play Services, Android System WebView - both are service components without UI.

**User benefit**: Centralized AI engine shared by multiple apps, reducing storage (no duplicate models).

### Concern 3: "Exported service with `exported=true` - security risk?"

**Response**: This is secure by design:
1. Custom permission with `signature` protection level
2. Only apps signed with same certificate can bind
3. Runtime signature verification in code
4. Audit logging for unauthorized attempts

**Industry standard**: Similar to AIDL services in banking apps, password managers, VPN providers.

### Concern 4: "Why does it need WAKE_LOCK and FOREGROUND_SERVICE?"

**Response**: AI inference is CPU-intensive:
- **WAKE_LOCK**: Prevents sleep during 10-30 second inference tasks
- **FOREGROUND_SERVICE**: Ensures Android doesn't kill the process mid-inference

**User experience impact**: Minimal. Foreground notification only shown during active AI tasks.

### Concern 5: "Large APK size (>100MB) - why?"

**Response**: Contains AI models (LLM, ASR, TTS):
- LLM model: ~80MB (quantized)
- ASR model: ~40MB
- TTS model: ~30MB

**Mitigation**: We're exploring Play Asset Delivery for future versions.

### Concern 6: "No content rating needed?"

**Response**: Content rating is E (Everyone). AI models can generate any text, but:
- Guardrails implemented (content filtering)
- Companion apps (BreezeApp) handle user interaction
- Engine just provides inference capability (like a calculator)

**Rating**: E (Everyone) - no objectionable content generated by default models.

---

## Version Information

**Initial Release**: Version 1.0.0
**Version Code**: 1
**AIDL API Version**: 1

**Target SDK**: 34 (Android 14)
**Min SDK**: 34 (Android 14)

**Future Versions**:
- 1.1.0: NPU acceleration for MediaTek devices
- 1.2.0: VLM (vision-language model) support
- 2.0.0: AIDL API v2 with breaking changes (2025 Q3)

---

## Reviewer Resources Package

**Included Files**:

1. **`BreezeApp-engine.apk`** (this app - service)
2. **`BreezeApp.apk`** (companion app for testing)
3. **`BreezeApp-engine-test-client.apk`** (automated test harness)
4. **`test-results.txt`** (pre-run test output)
5. **`architecture-diagram.png`** (visual reference)
6. **`integration-example.kt`** (code sample)
7. **`reviewer-guide.pdf`** (this document in PDF format)

**Download**: [Google Drive Link - TO BE PROVIDED]

---

## Contact Information

**Developer**: MTK Research
**Support Email**: breezeapp-support@mtkresearch.com
**Response Time**: 24-48 hours for reviewer inquiries

**For urgent review questions**:
- Email: breezeapp-support@mtkresearch.com
- Subject line: "URGENT: Play Store Review - [Your Question]"
- We monitor this address during business hours (GMT+8)

---

## Policy Compliance Checklist

**Google Play Policies**:

- ✅ **User Data**: No user data collected (compliant)
- ✅ **Permissions**: All permissions justified (see above)
- ✅ **Malware**: Clean (no malicious code)
- ✅ **Deceptive Behavior**: Clearly disclosed as service component
- ✅ **Monetization**: Free app, no ads, no in-app purchases
- ✅ **Intellectual Property**: Original code, open-source (Apache 2.0)
- ✅ **Device & Network Abuse**: No excessive resource usage
- ✅ **Families Policy**: N/A (not targeting children)
- ✅ **Health**: N/A (no health-related features)
- ✅ **Copyrights**: No copyright violations

**Additional Compliance**:
- ✅ Privacy Policy URL provided
- ✅ Content rating: E (Everyone)
- ✅ APK signed with valid certificate
- ✅ No obfuscated functionality
- ✅ Terms of Service: https://mtkresearch.com/terms

---

## Frequently Asked Questions (For Reviewers)

**Q: How do I test this app if it has no UI?**
A: Install BreezeApp (companion app) alongside this engine, then test the chat feature in BreezeApp.

**Q: Is this a duplicate/spam app?**
A: No, it's a service component. Similar to how Google Play Services is separate from Gmail/Maps.

**Q: Why is the service exported?**
A: Required for cross-app communication via AIDL. Security is enforced through signature-level permissions.

**Q: Does it access the internet?**
A: Optionally, only for downloading AI model updates. Core functionality is fully offline.

**Q: What's the business model?**
A: Free and open-source (Apache 2.0). No monetization. Research project by MTK Research.

**Q: How many users do you expect?**
A: Initial target: 10,000-50,000 developers and early adopters (2025 Q2-Q3).

**Q: Will this work on all Android devices?**
A: Requires Android 14+, 4GB+ RAM. Optimized for MediaTek devices but works on all chipsets.

---

## Acknowledgments

Thank you for reviewing BreezeApp AI Engine. We understand that service components without UI are unusual, and we appreciate your thorough review.

If you have any questions or need additional information, please don't hesitate to contact us at breezeapp-support@mtkresearch.com.

**We're committed to compliance and user safety.** 🔒

---

**Document Version**: 1.0
**Maintained By**: BreezeApp Team
**Last Updated**: 2025-11-03
**Reviewer Access**: Public (included in Play Console submission)
