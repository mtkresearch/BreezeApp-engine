# Release Scripts

Automated versioning and build scripts for BreezeApp-engine releases.

## Scripts Overview

| Script | Purpose | Interactive | Best For |
|--------|---------|-------------|----------|
| `prepare-release.sh` | Full release preparation | Yes | Manual releases with testing |
| `quick-release.sh` | Quick version bump & build | No | Fast builds, CI/CD |

---

## 1. prepare-release.sh (Full Release)

**Interactive script with all release steps.**

### Features
- ✅ Auto-increments version code
- ✅ Updates semantic version (MAJOR.MINOR.PATCH)
- ✅ Runs tests (optional)
- ✅ Builds release AAB
- ✅ Builds release APK (optional)
- ✅ Creates git commit
- ✅ Creates git tag
- ✅ Interactive confirmations at each step

### Usage

```bash
# Auto-increment patch version (1.0.0 → 1.0.1)
./scripts/prepare-release.sh

# Increment minor version (1.0.0 → 1.1.0)
./scripts/prepare-release.sh --minor

# Increment major version (1.0.0 → 2.0.0)
./scripts/prepare-release.sh --major

# Set specific version
./scripts/prepare-release.sh 1.2.3
```

### Example Output

```
========================================
BreezeApp-engine Release Preparation
========================================
ℹ Current Version Code: 1
ℹ Current Version Name: 1.0.0

ℹ Auto-incrementing PATCH version

ℹ New Version Code: 2
ℹ New Version Name: 1.0.1

Proceed with version update and build? (y/N): y
✓ Updated build.gradle

Run tests before building? (y/N): y
========================================
Running Tests
========================================
✓ All tests passed

========================================
Building Release AAB
========================================
ℹ Cleaning previous builds...
ℹ Building release AAB (this may take a while)...
✓ Release AAB built successfully
ℹ AAB Location: app/build/outputs/bundle/release/app-release.aab
ℹ AAB Size: 2.3M

Also build APK? (y/N): n

Commit version changes to git? (y/N): y
========================================
Committing Version Changes
========================================
✓ Committed version changes

Create git tag v1.0.1? (y/N): y
========================================
Creating Git Tag
========================================
✓ Created tag: v1.0.1
ℹ Push tag with: git push origin v1.0.1

========================================
Release Preparation Complete
========================================
✓ Version updated: 1.0.0 → 1.0.1
✓ Version code updated: 1 → 2
✓ AAB built: app/build/outputs/bundle/release/app-release.aab

ℹ Next steps:
  1. Test the AAB locally: bundletool build-apks && bundletool install-apks
  2. Push commit: git push origin 001-engine-deployment-strategy
  3. Push tag: git push origin v1.0.1
  4. Upload AAB to Play Console
  5. Update production certificate SHA-256 in SignatureValidator.kt
```

---

## 2. quick-release.sh (Fast Build)

**Non-interactive script for quick builds.**

### Features
- ✅ Auto-increments version code
- ✅ Updates version name
- ✅ Builds release AAB
- ❌ No prompts (fully automated)
- ❌ No git operations (unless uncommented)

### Usage

```bash
# Auto-increment patch (1.0.0 → 1.0.1)
./scripts/quick-release.sh

# Increment patch explicitly
./scripts/quick-release.sh patch

# Increment minor (1.0.0 → 1.1.0)
./scripts/quick-release.sh minor

# Increment major (1.0.0 → 2.0.0)
./scripts/quick-release.sh major

# Set specific version
./scripts/quick-release.sh 1.2.3
```

### Example Output

```
Starting quick release build...
Current: v1.0.0 (code: 1)
New: v1.0.1 (code: 2)
✓ Updated versions
Building release AAB...
✓ AAB built successfully (2.3M)
Location: app/build/outputs/bundle/release/app-release.aab
Done! Version: 1.0.0 → 1.0.1
```

---

## Version Management

### How Version Code is Incremented

```
versionCode is ALWAYS incremented by +1
```

**Example**:
- Current: `versionCode 1`
- After script: `versionCode 2`
- Next run: `versionCode 3`
- And so on...

### How Version Name is Incremented

Follows **Semantic Versioning** (MAJOR.MINOR.PATCH):

| Increment Type | Example | When to Use |
|----------------|---------|-------------|
| **Patch** | 1.0.0 → 1.0.1 | Bug fixes, minor changes |
| **Minor** | 1.0.0 → 1.1.0 | New features (backward compatible) |
| **Major** | 1.0.0 → 2.0.0 | Breaking changes |

### Version Code vs Version Name

```
build.gradle:
  versionCode 2          ← Integer (for Play Store, must increment)
  versionName "1.0.1"    ← String (for users, semantic version)
```

- **versionCode**: Must be unique and incrementing for each Play Store upload
- **versionName**: Human-readable version displayed to users

---

## Common Workflows

### Workflow 1: Hotfix Release (Patch)

```bash
# Quick patch for bug fix
./scripts/prepare-release.sh --patch

# Or just
./scripts/prepare-release.sh  # defaults to patch

# Result: 1.0.0 → 1.0.1
```

### Workflow 2: Feature Release (Minor)

```bash
# New features added
./scripts/prepare-release.sh --minor

# Result: 1.0.1 → 1.1.0
```

### Workflow 3: Breaking Changes (Major)

```bash
# API v2 with breaking changes
./scripts/prepare-release.sh --major

# Result: 1.1.0 → 2.0.0
```

### Workflow 4: CI/CD Automated Build

```bash
# In GitHub Actions or Jenkins
./scripts/quick-release.sh patch

# No prompts, automatic build
```

### Workflow 5: Specific Version

```bash
# Set exact version (e.g., aligning with spec)
./scripts/prepare-release.sh 1.5.2

# Result: current → 1.5.2
```

---

## What Happens to build.gradle

### Before Running Script

```gradle
defaultConfig {
    applicationId "com.mtkresearch.breezeapp.engine"
    minSdk 34
    targetSdk 34
    versionCode 1
    versionName "1.0.0"
    // ...
}
```

### After Running Script (patch increment)

```gradle
defaultConfig {
    applicationId "com.mtkresearch.breezeapp.engine"
    minSdk 34
    targetSdk 34
    versionCode 2          // ← Incremented
    versionName "1.0.1"    // ← Updated
    // ...
}
```

---

## Git Operations

### Tags Created

```bash
# prepare-release.sh creates annotated tags:
git tag -a v1.0.1 -m "Release version 1.0.1

- Version Code: 2
- Version Name: 1.0.1
- Build Date: 2025-11-04 14:30:00

Generated by prepare-release.sh"
```

### Commits Created

```bash
# prepare-release.sh creates version bump commits:
git commit -m "chore: bump version to 1.0.1

- Updated versionCode to 2
- Updated versionName to 1.0.1

[skip ci]"
```

### Pushing to Remote

```bash
# After script completes, push:
git push origin 001-engine-deployment-strategy
git push origin v1.0.1
```

---

## Output Files

After running either script:

```
app/build/outputs/bundle/release/
└── app-release.aab              ← Upload this to Play Console

app/build/outputs/apk/release/
└── app-release.apk              ← Optional (if built with prepare-release.sh)

app/build/outputs/mapping/release/
└── mapping.txt                  ← ProGuard mapping (save for crash decoding)
```

---

## Troubleshooting

### Issue 1: "Permission denied"

```bash
# Make scripts executable
chmod +x scripts/prepare-release.sh
chmod +x scripts/quick-release.sh
```

### Issue 2: Build fails

```bash
# Check Gradle
./gradlew --version

# Clean and retry
./gradlew clean
./scripts/prepare-release.sh
```

### Issue 3: Version not updating

```bash
# Check build.gradle syntax
cat app/build.gradle | grep version

# Manually verify:
# versionCode 1
# versionName "1.0.0"
```

### Issue 4: Script can't find build.gradle

```bash
# Run from project root
cd /Volumes/HomeEX/muximacmini/Project/MTK_BreezeApp/BreezeApp/BreezeApp-engine
./scripts/prepare-release.sh
```

---

## Advanced Usage

### Custom Version Increment

Edit `scripts/prepare-release.sh` and modify the `increment_version_name` function:

```bash
increment_version_name() {
    # Your custom logic here
}
```

### Auto-Commit in quick-release.sh

Uncomment these lines in `scripts/quick-release.sh`:

```bash
# Auto-commit (optional, uncomment if desired)
git add "$GRADLE_FILE"
git commit -m "chore: bump version to ${NEW_NAME}"
git tag -a "v${NEW_NAME}" -m "Release ${NEW_NAME}"
```

### Run Tests Automatically

Edit `scripts/prepare-release.sh` to skip the prompt:

```bash
# Change this:
read -p "Run tests before building? (y/N): " -n 1 -r

# To this (always run):
REPLY="y"
```

---

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Release Build

on:
  workflow_dispatch:
    inputs:
      version_type:
        description: 'Version increment type'
        required: true
        default: 'patch'
        type: choice
        options:
          - patch
          - minor
          - major

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 11
        uses: actions/setup-java@v3
        with:
          java-version: '11'

      - name: Build release
        run: |
          chmod +x scripts/quick-release.sh
          ./scripts/quick-release.sh ${{ github.event.inputs.version_type }}

      - name: Upload AAB
        uses: actions/upload-artifact@v3
        with:
          name: release-aab
          path: app/build/outputs/bundle/release/app-release.aab
```

---

## Best Practices

1. **Always run tests** before release builds (use `prepare-release.sh`)
2. **Test the AAB locally** with bundletool before uploading to Play Store
3. **Save ProGuard mapping.txt** for each release (for crash decoding)
4. **Push git tags** to remote after creating release
5. **Update production certificate** SHA-256 after first Play Console upload

---

**Last Updated**: 2025-11-04
**Maintained By**: BreezeApp Team
