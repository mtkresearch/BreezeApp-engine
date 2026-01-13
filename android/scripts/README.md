# BreezeApp-engine Scripts

Automated scripts for building, releasing, testing and configuring BreezeApp-engine.

---

## 📜 Available Scripts

### 0. `runner-test.sh` (NEW)

**Location**: `android/scripts/runner-test.sh`

**Purpose**: Terminal-based testing tool for AI Runners. Allows developers to verify Runner compliance and test with dynamic parameters.

**Usage**:
```bash
cd android/scripts
./runner-test.sh [OPTIONS] <COMMAND> [RUNNER_TYPE]
```

**Commands**:
- `test` - Execute Runner tests
- `quick-test` - Run rapid single-input tests (CLI optimized)
- `verify` - Verify Runner contract compliance
- `list` - List all available Runners
- `help` - Show help message

**Examples**:
```bash
# Test all LLM Runners
./runner-test.sh test llm

# Test specific Runner with custom parameters
./runner-test.sh --runner=MockLLMRunner \
  --param:temperature=0.7 \
  --param:max_tokens=1024 \
  test llm

# Use config file for testing
./runner-test.sh --config=test-configs/runners/llm/mock-llm-basic.json test

# CI mode with JUnit output
./runner-test.sh --ci --output=junit test all

# Quick Test (Rapid Feedback)
./runner-test.sh --runner=MockLLMRunner \
  --input="Hello World" \
  --expect-contains="Hello" \
  quick-test
```

**Options**:
- `--runner=<CLASS>` - Specify Runner class name
- `--config=<FILE>` - Load test configuration from JSON
- `--param:<KEY>=<VAL>` - Override specific parameters
- `--model=<ID>` - Specify model ID
- `--input=<TEXT>` - Input text for quick-test
- `--expect=<TEXT>` - Expected exact output for quick-test
- `--expect-contains=<TEXT>` - Expected partial output for quick-test
- `--output=<FORMAT>` - Output format (console/json/junit)
- `--ci` - CI mode: strict execution
- `--mock-only` - Only run Mock tests
- `--verbose` - Detailed output

For detailed documentation, see [Testing Guide](../docs/guides/testing-guide.md).

---

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

### 2. `release-edgeai.sh`

**Location**: `android/scripts/release-edgeai.sh`

**Purpose**: Release EdgeAI library to JitPack by version bumping, committing, tagging, and pushing.

**Usage**:
```bash
cd android/scripts
./release-edgeai.sh [VERSION_TYPE]
```

**What it does**:
- ✅ Increments version in `EdgeAI/build.gradle.kts`
- ✅ Updates both `version` and `publishing.version`
- ✅ Commits the version change
- ✅ Creates git tag `EdgeAI-vX.Y.Z`
- ✅ Pushes to remote repository
- ✅ Triggers JitPack automatic build

**Examples**:
```bash
./release-edgeai.sh              # Patch: EdgeAI-v0.1.4 → v0.1.5
./release-edgeai.sh minor        # Minor: EdgeAI-v0.1.4 → v0.2.0
./release-edgeai.sh major        # Major: EdgeAI-v0.1.4 → v1.0.0
./release-edgeai.sh -v 1.0.0     # Manual version
```

**JitPack Usage**:
```kotlin
dependencies {
    implementation("com.github.mtkresearch.BreezeApp-engine:EdgeAI:EdgeAI-v0.1.5")
}
```

---

### 3. `release-build.sh`

**Location**: `android/scripts/release-build.sh`

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
