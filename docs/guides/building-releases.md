# Build Guide

**Purpose**: Instructions for building and releasing BreezeApp-engine
**Last Updated**: 2025-11-10

---

## Prerequisites

- **JDK**: Java 11 or higher
- **Android SDK**: API 34 (Android 14.0)
- **Gradle**: 8.2.0+ (wrapper included)
- **Git**: For version control

---

## Build Commands

### Development Builds

```bash
# Clean build
./gradlew clean

# Debug build (development)
./gradlew assembleDebug

# Output: android/breeze-app-engine/build/outputs/apk/debug/
```

### Release Builds

```bash
# Release AAB (for Play Store)
./gradlew bundleRelease

# Output: android/breeze-app-engine/build/outputs/bundle/release/app-release.aab

# Release APK (for direct distribution)
./gradlew assembleRelease

# Output: android/breeze-app-engine/build/outputs/apk/release/app-release.apk
```

### Automated Release Script

Use the automated release build script with version management:

```bash
# From android/ directory
cd android

# Patch version bump (1.0.0 → 1.0.1)
./release-build.sh patch

# Minor version bump (1.0.0 → 1.1.0)
./release-build.sh minor

# Major version bump (1.0.0 → 2.0.0)
./release-build.sh major

# Manual version
./release-build.sh -v 1.5.0

# Build only AAB (for Play Store)
./release-build.sh -b aab patch

# Build only APK
./release-build.sh -b apk patch
```

**Features**:
- ✅ Auto-increments `versionCode`
- ✅ Updates `versionName` (semantic versioning)
- ✅ Builds AAB and/or APK
- ✅ Interactive confirmation
- ✅ Shows next steps (commit, tag, push)

**See `android/scripts/README.md` for detailed documentation.**

---

## Build Configuration

### Version Management

Version numbers are defined in `android/EdgeAI/build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        versionCode = 1          // Integer, increments with each release
        versionName = "1.0.0"    // Semantic version (MAJOR.MINOR.PATCH)
    }
}
```

### Build Types

#### Debug Build
- **Minification**: Disabled
- **Debugging**: Enabled
- **App ID**: `com.mtkresearch.breezeapp.engine.debug`
- **Logging**: Verbose (DEBUG level)

#### Release Build
- **Minification**: Enabled (R8)
- **Shrink Resources**: Enabled
- **Debugging**: Disabled
- **Logging**: Minimal (ERROR/WARN only)
- **ProGuard**: Optimized

---

## ProGuard/R8 Configuration

### Essential Rules

The following ProGuard rules are **critical** and must not be modified:

```proguard
# AIDL Interfaces - MUST NOT be obfuscated
-keep class com.mtkresearch.breezeapp.engine.IAIEngineService { *; }
-keep class com.mtkresearch.breezeapp.engine.IInferenceCallback { *; }
-keep class com.mtkresearch.breezeapp.engine.IStreamCallback { *; }
-keep class com.mtkresearch.breezeapp.engine.IModelManager { *; }

# Keep AIDL Stub and Proxy classes
-keep class com.mtkresearch.breezeapp.engine.IAIEngineService$Stub { *; }
-keep class com.mtkresearch.breezeapp.engine.IAIEngineService$Stub$Proxy { *; }

# Service class
-keep public class com.mtkresearch.breezeapp.engine.BreezeAppEngineService {
    public <init>();
}

# Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
```

**Location**: `android/breeze-app-engine/proguard-rules.pro`

---

## Release Signing

### Play App Signing (Recommended)

Google manages the production signing key for you:

1. **First Release**:
   - Build release AAB: `./gradlew bundleRelease`
   - Upload to Play Console
   - Opt into Play App Signing
   - Google generates app signing key

2. **Subsequent Releases**:
   - Use upload key (separate from app signing key)
   - Google re-signs with app signing key
   - No risk of key loss

### Certificate Fingerprint

After uploading to Play Console:

```bash
# View certificate SHA-256
# Play Console → Setup → App signing → App signing key certificate

# Example:
# SHA-256: AB:CD:EF:12:34:56:...
```

**Important**: Update `SignatureValidator.kt` with production certificate hash:

```kotlin
private val AUTHORIZED_SIGNATURES = setOf(
    "ABCDEF12345678..."  // Replace with actual SHA-256 (no colons)
)
```

---

## Testing Builds

### Unit Tests

```bash
# Run all unit tests
./gradlew test

# Run specific test
./gradlew test --tests "SignatureValidatorTest"

# Generate coverage report
./gradlew test jacocoTestReport

# Report: build/reports/tests/test/index.html
```

### Lint Checks

```bash
# Run lint on release build
./gradlew lintRelease

# Report: build/reports/lint-results-release.html
```

### Build Verification

```bash
# Verify APK signature
apksigner verify --print-certs app-release.apk

# Expected: Signer #1 certificate SHA-256 matches upload key
```

---

## Gradle Properties

### Performance Optimization

Add to `gradle.properties`:

```properties
# Memory allocation
org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=1024m

# Build performance
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configureondemand=true

# R8 optimization
android.enableR8.fullMode=true
```

---

## Troubleshooting

### Build Fails with "Out of Memory"

Increase Gradle heap size:

```properties
# gradle.properties
org.gradle.jvmargs=-Xmx6144m -XX:MaxMetaspaceSize=1024m
```

### ProGuard Errors

Check `build/outputs/mapping/release/usage.txt` for removed code.

Verify AIDL interfaces are preserved:

```bash
grep "IAIEngineService" build/outputs/mapping/release/mapping.txt
# Should show: com.mtkresearch.breezeapp.engine.IAIEngineService -> com.mtkresearch.breezeapp.engine.IAIEngineService
```

### Signature Verification Issues

Extract and verify certificate:

```bash
# From APK
apksigner verify --print-certs app-release.apk | grep SHA-256

# From keystore
keytool -list -v -keystore upload-key.jks -alias upload-key
```

---

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Build Release

on:
  push:
    tags:
      - 'v*.*.*'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 11
        uses: actions/setup-java@v3
        with:
          java-version: '11'

      - name: Build Release AAB
        run: ./gradlew bundleRelease

      - name: Upload AAB
        uses: actions/upload-artifact@v3
        with:
          name: app-release.aab
          path: android/breeze-app-engine/build/outputs/bundle/release/
```

---

## Build Outputs

### APK Structure

```
app-release.apk
├── AndroidManifest.xml         # App manifest
├── classes.dex                 # Compiled code (R8 optimized)
├── resources.arsc              # Resources
├── res/                        # Drawables, layouts
├── lib/                        # Native libraries (if any)
└── META-INF/                   # Signatures
    ├── MANIFEST.MF
    ├── CERT.SF
    └── CERT.RSA
```

### AAB Structure

```
app-release.aab
├── base/                       # Base module
│   ├── manifest/
│   ├── dex/
│   ├── res/
│   └── assets/
├── BundleConfig.pb             # Bundle configuration
└── META-INF/                   # Signatures
```

---

## Next Steps

After building:

1. **Test locally**: Install APK on device
2. **Verify functionality**: Run integration tests
3. **Upload to Play Console**: Submit AAB for release
4. **Monitor**: Check crash reports and ANR rates

---

**Document Version**: 2.0
**Maintained By**: BreezeApp Team
**Last Updated**: 2025-11-10
