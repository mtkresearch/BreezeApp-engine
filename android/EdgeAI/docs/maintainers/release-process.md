# EdgeAI Release Process

## Quick Release

Use the automated release script:

```bash
cd android/scripts
./release-edgeai.sh [patch|minor|major]
```

**Examples**:
- `./release-edgeai.sh` - Patch release (0.1.4 → 0.1.5)
- `./release-edgeai.sh minor` - Minor release (0.1.4 → 0.2.0)
- `./release-edgeai.sh major` - Major release (0.1.4 → 1.0.0)
- `./release-edgeai.sh -v 1.0.0` - Specific version

**What it does**:
1. Increments version in `build.gradle.kts`
2. Commits version bump
3. Creates git tag (`EdgeAI-vX.Y.Z`)
4. Pushes to GitHub
5. Triggers JitPack build

---

## Verify Release

**Check JitPack build status**:
```
https://jitpack.io/#mtkresearch/BreezeApp-engine/EdgeAI-vX.Y.Z
```

**Test in client project**:
```kotlin
dependencies {
    implementation("com.github.mtkresearch.BreezeApp-engine:EdgeAI:EdgeAI-vX.Y.Z")
}
```

---

## CI/CD

**Validation** (automatic on push/PR):
- Builds EdgeAI module
- Runs unit tests
- Validates AIDL compilation

**See**: `.github/workflows/edgeai-validate.yml`

**JitPack** (automatic on tag push):
- Detects new tags
- Builds library
- Publishes to JitPack

**See**: `jitpack.yml`

---

## Troubleshooting

**Script fails**: Check git is clean, no uncommitted changes  
**JitPack build fails**: Check `jitpack.yml` and `build.gradle.kts`  
**Version conflict**: Ensure tag matches version in `build.gradle.kts`

**See**: `android/scripts/release-edgeai.sh --help` for full options