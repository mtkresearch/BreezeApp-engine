# 📦 EdgeAI Library JitPack Release SOP

> **Maintainer**: mtkresearch team  
> **Purpose**: Enable EdgeAI module to be imported by other applications via JitPack, and ensure only mtkresearch members can execute version releases

---

## 📁 Project Structure

Current BreezeApp-engine project structure:

```
BreezeApp-engine/
├── EdgeAI/
│   ├── build.gradle.kts         <-- Contains maven-publish configuration
│   ├── src/main/AndroidManifest.xml
│   └── docs/
│       └── JitPack_Release_SOP.md  <-- This file
├── breeze-app-engine/
├── build.gradle.kts             <-- Root build script
├── settings.gradle.kts          <-- Contains EdgeAI module
├── gradlew                      <-- Gradle wrapper
├── gradle/wrapper/
│   ├── gradle-wrapper.properties
│   └── gradle-wrapper.jar
├── jitpack.yml                  <-- JitPack configuration
└── README.md
```

---

## 🔧 EdgeAI Module Configuration

### `EdgeAI/build.gradle.kts`

```kotlin
@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    id("maven-publish")
}

group = "com.github.mtkresearch" // JitPack specific, keep unchanged
version = "edgeai-v0.1.0" // Version number, must match Git Tag

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.mtkresearch"
                artifactId = "EdgeAI"
                version = "edgeai-v0.1.0" // Must match the version above
            }
        }
    }
}

android {
    namespace = "com.mtkresearch.breezeapp.edgeai"
    compileSdk = 35

    defaultConfig {
        minSdk = 34
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        aidl = true
    }
}
```

---

## 📜 `jitpack.yml` Configuration

```yaml
jdk:
  - openjdk17

install:
  - ./gradlew :EdgeAI:publishToMavenLocal

build:
  script: ./gradlew :EdgeAI:assembleRelease

env:
  GRADLE_OPTS: "-Xmx2048m -Dfile.encoding=UTF-8"
```

---

## 🚀 Release Process (mtkresearch only)

### ✅ Step 1: Update Version Number
Open `EdgeAI/build.gradle.kts`:
```kotlin
version = "edgeai-v0.1.1"  // Increment version number
```

Also update the version in the `publishing` block:
```kotlin
version = "edgeai-v0.1.1" // Must match the version above
```

---

### ✅ Step 2: Commit Changes
```bash
git add EdgeAI/build.gradle.kts
git commit -m "Release EdgeAI edgeai-v0.1.1"
git push origin main
```

---

### ✅ Step 3: Create Git Tag
```bash
git tag edgeai-v0.1.1
git push origin edgeai-v0.1.1
```

---

### ✅ Step 4: Verify JitPack Success

Go to the following URL to confirm the build status is green (success):
```
https://jitpack.io/#mtkresearch/BreezeApp-engine/edgeai-v0.1.1
```

---

## 🔗 External Projects Importing EdgeAI

### Specify Version Number (Recommended)
```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.mtkresearch:BreezeApp-engine:edgeai-v0.1.1")
}
```

### Use Latest Version (No Version Specified)
```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.mtkresearch:BreezeApp-engine:edgeai-v0.1.1")
}
```

> 📌 **Note**: When no version is specified, JitPack will automatically use the latest tag version

---

## 🚫 Permissions and Community Guidelines

| Action | Permission | Notes |
|--------|------------|-------|
| Modify EdgeAI code | ✅ Everyone | Can submit PR |
| Release new EdgeAI version | 🚫 mtkresearch only | Only internal members can operate tags |
| Merge PR containing `version` or `tag` | ❌ Forbidden | Will be reverted |

---

## 🛠 Common Error Troubleshooting

| Problem | Error Message | Solution |
|---------|---------------|----------|
| Missing Gradle wrapper | `./gradlew: No such file or directory` | Ensure repo has `gradlew` + `gradle-wrapper.properties` |
| Module not found | `Could not find :EdgeAI:` | Ensure `settings.gradle.kts` has `include(":EdgeAI")` |
| Version not recognized | `version not found` | Ensure Git tag format is correct and matches `version` |
| Dependency resolution failed | `Failed to resolve: EdgeAI` | Check if correct version is used (not SNAPSHOT) |
| AIDL compilation error | `Couldn't find import` | Ensure AIDL filename matches interface name |

---

## ✅ Recommended GitHub Actions Check

You can add the following to `.github/workflows/edgeai-validate.yml`:

```yaml
name: Validate EdgeAI

on:
  push:
    paths:
      - 'EdgeAI/**'
      - '.github/workflows/edgeai-validate.yml'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Build EdgeAI
        run: ./gradlew :EdgeAI:assembleRelease
```

---

## 📌 Appendix: Version Naming Recommendations

Please use the following naming format for releases:

```
edgeai-v0.1.0
edgeai-v0.1.1
edgeai-v0.1.2
edgeai-v0.2.0
...
```

---

## 🧪 Quick Release Commands

```bash
# 1. Edit version number
vim EdgeAI/build.gradle.kts

# 2. Commit & Push
git commit -am "Release edgeai-v0.1.1"
git push origin main

# 3. Tag & Push
git tag edgeai-v0.1.1
git push origin edgeai-v0.1.1
```

---

## 🔍 Verify Release Success

1. **Check JitPack Status**:
   ```
   https://jitpack.io/#mtkresearch/BreezeApp-engine/edgeai-v0.1.1
   ```

2. **Test Dependency Import**:
   Add to test project:
   ```kotlin
   implementation("com.github.mtkresearch:BreezeApp-engine:edgeai-v0.1.1")
   ```

3. **Confirm Functionality**:
   ```kotlin
   import com.mtkresearch.breezeapp.edgeai.EdgeAI
   ```

---

© 2025 mtkresearch internal use only 