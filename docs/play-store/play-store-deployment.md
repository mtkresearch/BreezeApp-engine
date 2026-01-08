# BreezeApp-engine Deployment Guide

Quick guide for deploying BreezeApp-engine to Google Play Store using automated build scripts.

---

## Prerequisites

- ✅ Google Play Console developer account
- ✅ Access to BreezeApp organization in Play Console
- ✅ Android Studio with Android SDK 34+
- ✅ Git repository access

---

## Quick Start

### 1. Setup Keystore (First Time Only)

Run the interactive keystore setup script:

```bash
cd android/scripts
./setup-keystore.sh
```

**What it does**:
- Guides you through keystore configuration
- Verifies keystore file and credentials
- Creates `keystore.properties` (auto-loaded by Gradle)
- Extracts certificate SHA-256 fingerprint

**See**: [android/scripts/README.md](../../android/scripts/README.md#1-setup-keystoresh) for detailed documentation.

---

### 2. Build Release

Run the automated build script:

```bash
cd android
./scripts/release-build.sh patch  # or: minor, major
```

**What it does**:
- Auto-increments version (patch: 1.0.0 → 1.0.1)
- Updates `versionCode` and `versionName` in `build.gradle.kts`
- Builds signed AAB (Android App Bundle) for Play Store
- Creates versioned copy in `breeze-app-engine/release/`

**Options**:
```bash
./scripts/release-build.sh patch           # Patch: 1.0.0 → 1.0.1
./scripts/release-build.sh minor           # Minor: 1.0.0 → 1.1.0
./scripts/release-build.sh major           # Major: 1.0.0 → 2.0.0
./scripts/release-build.sh -v 2.5.0        # Manual version
./scripts/release-build.sh -b aab patch    # AAB only (default: both AAB + APK)
```

**Output location**:
```
android/breeze-app-engine/release/BreezeApp-engine-vX.Y.Z-N.aab
```

**See**: [android/scripts/README.md](../../android/scripts/README.md#3-release-buildsh) for all options.

---

### 3. Upload to Play Store

1. Go to [Google Play Console](https://play.google.com/console)
2. Select **BreezeApp-engine** app
3. Navigate to **Release** → **Production**
4. Click **Create new release**
5. Upload AAB: `android/breeze-app-engine/release/BreezeApp-engine-vX.Y.Z-N.aab`
6. Add release notes (see [release-notes-template-en.md](./release-notes-template-en.md))
7. Review and start rollout

---

### 4. Tag Release in Git

```bash
# Commit version bump
git add android/breeze-app-engine/build.gradle.kts
git commit -m "chore: bump version to X.Y.Z"

# Create tag
git tag -a vX.Y.Z -m "Release X.Y.Z"

# Push to remote
git push origin main --tags
```

---

## Staged Rollout Strategy

Recommended rollout percentages based on version type:

| Version Type | Rollout Schedule | Rationale |
|--------------|------------------|-----------|
| **Patch** (1.0.0 → 1.0.1) | 50% → 100% (24 hours) | Low risk, bug fixes only |
| **Minor** (1.0.0 → 1.1.0) | 10% → 50% → 100% (7 days) | New features, monitor compatibility |
| **Major** (1.0.0 → 2.0.0) | 5% → 20% → 50% → 100% (14 days) | Breaking changes, careful monitoring |

**How to adjust rollout**:
1. In Play Console, go to **Release** → **Production** → **Manage**
2. Click **Update rollout**
3. Increase percentage
4. Monitor crash rate and user reviews before increasing further

---

## Troubleshooting

### Build Fails

**Solution**: Clean and rebuild
```bash
cd android
./gradlew clean
./gradlew --refresh-dependencies
./scripts/release-build.sh patch
```

### Keystore Issues

**Solution**: Re-run setup script
```bash
cd android/scripts
./setup-keystore.sh
```

### Version Conflicts

**Solution**: Manually edit version in `android/breeze-app-engine/build.gradle.kts`:
```kotlin
versionCode = N
versionName = "X.Y.Z"
```

### Script Permission Denied

**Solution**: Make scripts executable
```bash
chmod +x android/scripts/*.sh
```

---

## Build Outputs

After running `release-build.sh`, find your builds here:

**AAB (for Play Store)**:
```
android/breeze-app-engine/build/outputs/bundle/release/breeze-app-engine-release.aab
android/breeze-app-engine/release/BreezeApp-engine-vX.Y.Z-N.aab  (versioned copy)
```

**APK (for testing)**:
```
android/breeze-app-engine/build/outputs/apk/release/breeze-app-engine-release.apk
android/breeze-app-engine/release/BreezeApp-engine-vX.Y.Z-N.apk  (versioned copy)
```

---

## See Also

- **[Build Scripts Documentation](../../android/scripts/README.md)** - Detailed script usage and examples
- **[Release Notes Template](./release-notes-template-en.md)** - Template for Play Store release notes
- **[Security Model](../security/security-model.md)** - Permission-based access control
