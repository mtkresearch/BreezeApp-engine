# BreezeApp-engine Development Guidelines

Auto-generated from all feature plans. Last updated: 2025-11-03

## Active Technologies

- Kotlin 100% (Java compatibility level 11), Android Gradle Plugin 8.x (001-engine-deployment-strategy)

## Project Structure

```text
src/
tests/
```

## Commands

### Build Commands
- **Main build**: `cd android && ./gradlew build`
- **Clean build**: `cd android && ./gradlew clean build`
- **Build AAR**: `cd android && ./gradlew :EdgeAI:assembleRelease`
- **Automated Release Build**: `cd android && ./release-build.sh [OPTIONS] [VERSION_TYPE]` - Auto-increments version and builds AAR
  - Version types: `patch` (default), `minor`, `major`
  - Manual version: `-v VERSION`
  - See `android/RELEASE_BUILD.md` for detailed documentation

### Publishing to JitPack
1. **Build release**: `./release-build.sh`
2. **Commit version**: `git add EdgeAI/build.gradle.kts && git commit -m "chore: bump version to EdgeAI-vX.Y.Z"`
3. **Create tag**: `git tag -a EdgeAI-vX.Y.Z -m "Release EdgeAI-vX.Y.Z"`
4. **Push tag**: `git push origin main --tags`
5. **JitPack builds automatically** from the tag

### Testing Commands
- **Run unit tests**: `cd android && ./gradlew :EdgeAI:test`
- **Run tests with report**: Test reports at `EdgeAI/build/reports/tests/test/index.html`

## Code Style

Kotlin 100% (Java compatibility level 11), Android Gradle Plugin 8.x: Follow standard conventions

## Recent Changes

- 001-engine-deployment-strategy: Added Kotlin 100% (Java compatibility level 11), Android Gradle Plugin 8.x

<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
