# BreezeApp-engine Development Guidelines

Auto-generated from all feature plans. Last updated: 2025-11-03

## Active Technologies
- SharedPreferences (via RunnerManager.saveSettings()) (001-engine-settings-ui-enhancement)

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
- 001-engine-settings-ui-enhancement: Added Kotlin 100% (Java compatibility level 11)

- 001-engine-deployment-strategy: Added Kotlin 100% (Java compatibility level 11), Android Gradle Plugin 8.x

<!-- MANUAL ADDITIONS START -->

## Feature: Engine Settings UI Enhancement

**Status**: MVP Complete (Phase 3 of 6)
**Branch**: `001-engine-settings-ui-enhancement`
**Spec**: `specs/001-engine-settings-ui-enhancement/`

### What's Implemented

**User Story 1: Unsaved Changes Protection** ✅
- Material Design dialog with Save/Discard/Cancel options
- Back button and toolbar navigation interception
- Per-runner dirty state tracking
- Progress indicator during save operations
- Save button enable/disable based on changes
- Error handling with Toast messages

### Architecture

**New State Management Classes**:
- `UnsavedChangesState` - Tracks parameter modifications per runner/capability
- `ParameterValidationState` - Manages validation errors (ready for US3)
- `SaveOperationState` - Tracks async save operations

**UI Components**:
- `UnsavedChangesDialog` - Reusable Material dialog extension function
- Progress overlay with touch blocking in `activity_engine_settings.xml`

### Key Implementation Patterns

**Dirty State Tracking**:
```kotlin
// Track changes as user modifies parameters
unsavedChangesState.trackChange(capability, runnerName, paramName, original, current)

// Check if navigation should show dialog
if (unsavedChangesState.hasAnyUnsavedChanges()) {
    showUnsavedChangesDialog()
}
```

**Navigation Interception**:
```kotlin
// OnBackPressedCallback for back button
onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

// Override finish() for toolbar navigation
override fun finish() {
    if (unsavedChangesState.hasAnyUnsavedChanges() && !navigationConfirmed) {
        showUnsavedChangesDialog()
    } else {
        super.finish()
    }
}
```

**Avoiding False Dirty State**:
- Use `isInitializing` flag to skip first listener call during field setup
- Prevents `setText()` from triggering change tracking on page load

### Testing

**Manual Test Flow**:
1. Open Engine Settings
2. Verify Save button is disabled (gray)
3. Modify a parameter value
4. Verify Save button enables (orange/blue)
5. Press back button → Dialog appears
6. Test Save/Discard/Cancel actions

**Debug Logging**:
```bash
adb logcat -s EngineSettingsActivity:D
```

### Next Steps

- **User Story 2**: Dynamic model selection dropdown (9 tasks)
- **User Story 3**: Inline validation errors (16 tasks)
- **Polish**: Comprehensive test coverage

### Files Modified

**New Files** (5):
- `model/ui/UnsavedChangesState.kt` - State tracking
- `model/ui/ParameterValidationState.kt` - Validation management
- `ui/dialogs/UnsavedChangesDialog.kt` - Material dialog
- `test/.../UnsavedChangesStateTest.kt` - Unit tests
- `test/.../ParameterValidationStateTest.kt` - Unit tests

**Modified Files** (3):
- `ui/EngineSettingsActivity.kt` - Navigation hooks, state integration
- `res/layout/activity_engine_settings.xml` - Progress overlay
- `res/values/strings.xml` - Dialog strings

<!-- MANUAL ADDITIONS END -->
