# BreezeApp-engine Scripts

Automated scripts for building, releasing, and configuring BreezeApp-engine.

---

## 📜 Available Scripts

### 1. `setup-keystore.sh`

**Location**: `android/scripts/setup-keystore.sh`

**Purpose**: Interactive setup for release signing keystore configuration.

**Usage**:
```bash
cd android/scripts
./setup-keystore.sh
```

**What it does**:
- ✅ Guides you through keystore configuration
- ✅ Verifies keystore file and credentials
- ✅ Lists available key aliases
- ✅ Creates `keystore.properties` file
- ✅ Extracts certificate SHA-256 fingerprint
- ✅ Optionally creates new keystore if needed

**Important Notes**:
- **Use the SAME keystore** as BreezeApp for signature-level permissions to work
- The keystore.properties file is in .gitignore (never committed)
- Back up your keystore file - losing it means you can't update your app!

---

### 2. `release-build.sh`

**Location**: `android/release-build.sh`

**Purpose**: Automate version bumping and AAB/APK building for releases.

**Usage**:
```bash
cd android
./release-build.sh [OPTIONS] [VERSION_TYPE]
```

**Version Types**:
- `patch` - Increment patch version (1.0.0 → 1.0.1)
- `minor` - Increment minor version (1.0.0 → 1.1.0)
- `major` - Increment major version (1.0.0 → 2.0.0)

**Options**:
- `-v, --version VERSION` - Set specific version (e.g., 2.0.0)
- `-b, --build TYPE` - Build type: `aab`, `apk`, or `both` (default: both)
- `-h, --help` - Show help message

---

## 📖 Examples

### Patch Release (1.0.0 → 1.0.1)
```bash
./release-build.sh patch
```

### Minor Release (1.0.0 → 1.1.0)
```bash
./release-build.sh minor
```

### Major Release (1.0.0 → 2.0.0)
```bash
./release-build.sh major
```

### Set Specific Version
```bash
./release-build.sh -v 2.5.0
```

### Build Only AAB (for Play Store)
```bash
./release-build.sh -b aab patch
```

### Build Only APK (for direct distribution)
```bash
./release-build.sh -b apk patch
```

---

## 🔄 Release Workflow

### 1. Build Release
```bash
cd android
./release-build.sh patch  # or minor/major
```

### 2. Commit Version Bump
```bash
git add breeze-app-engine/build.gradle.kts
git commit -m "chore: bump version to 1.0.1"
```

### 3. Create Git Tag
```bash
git tag -a v1.0.1 -m "Release 1.0.1"
```

### 4. Push to Remote
```bash
git push origin main --tags
```

### 5. Upload to Play Store
- Navigate to: `breeze-app-engine/build/outputs/bundle/release/`
- Upload `breeze-app-engine-release.aab` to Play Console

---

## 📂 Build Outputs

After running the script, find your builds here:

**AAB (Android App Bundle)**:
```
android/breeze-app-engine/build/outputs/bundle/release/breeze-app-engine-release.aab
```

**APK (Android Package)**:
```
android/breeze-app-engine/build/outputs/apk/release/breeze-app-engine-release.apk
```

---

## ⚙️ What the Script Does

1. ✅ Reads current version from `build.gradle.kts`
2. ✅ Increments version based on type (patch/minor/major)
3. ✅ Updates `versionCode` (auto-increments)
4. ✅ Updates `versionName` (semantic version)
5. ✅ Runs Gradle clean
6. ✅ Builds AAB and/or APK
7. ✅ Displays build output locations
8. ✅ Shows next steps (commit, tag, push)

---

## 🛠️ Troubleshooting

### "Gradle file not found"
**Solution**: Make sure you're running the script from the `android/` directory:
```bash
cd android
./release-build.sh
```

### "Permission denied"
**Solution**: Make the script executable:
```bash
chmod +x release-build.sh
```

### Build fails
**Solution**: Check Gradle output and ensure dependencies are up-to-date:
```bash
./gradlew --refresh-dependencies
```

---

## 📝 Notes

- **Version Code**: Always auto-increments (+1 each build)
- **Version Name**: Follows semantic versioning (MAJOR.MINOR.PATCH)
- **Default Behavior**: Builds both AAB and APK
- **Interactive**: Script asks for confirmation before building
- **Git**: Script does NOT automatically commit/push (you control git operations)

---

**Last Updated**: 2025-11-10
