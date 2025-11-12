# BreezeApp-engine Development Guidelines

Auto-generated from all feature plans. Last updated: 2025-11-03

## Active Technologies
- SharedPreferences (via RunnerManager.saveSettings()) (001-engine-settings-ui-enhancement)
- Kotlin (Android Gradle Plugin 8.x, Java 11 compatibility) + Android SDK (PackageManager, BroadcastReceiver), Material Design Components (Snackbar, AlertDialog), existing AIDL binding infrastructure (001-auto-engine-binding)
- SharedPreferences for connection state persistence (optional), no database required (001-auto-engine-binding)

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
- 001-auto-engine-binding: Added Kotlin (Android Gradle Plugin 8.x, Java 11 compatibility) + Android SDK (PackageManager, BroadcastReceiver), Material Design Components (Snackbar, AlertDialog), existing AIDL binding infrastructure
- 001-engine-settings-ui-enhancement: Added Kotlin 100% (Java compatibility level 11)

- 001-engine-deployment-strategy: Added Kotlin 100% (Java compatibility level 11), Android Gradle Plugin 8.x

<!-- MANUAL ADDITIONS START -->

## Feature: Engine Settings UI Enhancement

**Status**: ✅ **PRODUCTION READY** - All core features + validation complete
**Branch**: `main` (merged from `001-engine-settings-ui-enhancement`)
**Total**: 16 commits, ~3,500 lines including tests
**Spec**: `specs/001-engine-settings-ui-enhancement/`
**Last Updated**: 2025-11-11

### Executive Summary

Complete overhaul of Engine Settings UI with dynamic model selection, unsaved changes protection, and intelligent parameter filtering. Users can now safely configure OpenRouter models with price filtering, automatic parameter discovery, and clear feedback for configuration changes.

---

## Implemented Features

### User Story 1: Unsaved Changes Protection ✅

**What it does**: Prevents accidental data loss by showing Material Design dialog when user attempts to navigate away with unsaved changes.

**Features**:
- Material Design Save/Discard/Cancel dialog
- Back button and toolbar navigation interception via `OnBackPressedCallback`
- Per-runner dirty state tracking across all capability tabs (LLM, ASR, TTS, VLM)
- Progress overlay with touch blocking during save operations
- Dynamic Save button enable/disable based on change detection
- Runner selection change tracking
- Error handling with Toast messages

**Key Pattern**: Initialization flag prevents false dirty state
```kotlin
var isInitializing = true
addTextChangedListener {
    if (isInitializing) {
        isInitializing = false
        return  // Skip setText() callback during initialization
    }
    // Track real user changes
}
```

### User Story 2: Dynamic Model Selection with OpenRouter API ✅

**What it does**: Fetches available models from OpenRouter API with price-based filtering and 24-hour caching.

**Features**:
- Dynamic model fetching from `https://openrouter.ai/api/v1/models`
- Price-based filtering with SeekBar UI (Free → $0.000001 → $0.0001 → $0.001 → $0.01)
- 24-hour caching in SharedPreferences (reduces API calls)
- Real-time model dropdown updates after refresh
- Automatic fallback to cached data on API failures
- Model metadata display (price, context length)
- Isolated API key authentication section

**Key Pattern**: Graceful degradation with caching
```kotlin
// Check cache first
if (!forceRefresh && !isCacheExpired()) {
    return getCachedModels()
}

// Fetch from API, cache results
val models = fetchFromAPI()
cacheModels(models)

// On error, return stale cache
.onFailure {
    return getCachedModels()  // Graceful fallback
}
```

### User Story 3: Dynamic Parameter Filtering ✅

**What it does**: Only shows parameters that the selected model actually supports, preventing user confusion.

**Features**:
- Fetches supported parameters from `https://openrouter.ai/api/v1/parameters/{author}/{slug}`
- Dynamically shows/hides parameter fields based on model capabilities
- Parses model IDs: `"google/gemini-flash-1.5:free"` → `author="google", slug="gemini-flash-1.5"`
- Regenerates UI when model changes
- Falls back to showing all parameters on API error

**Example**:
```
Select "google/gemini-flash-1.5:free"
  → Shows: temperature, top_p, max_tokens (Gemini's params)

Select "openai/gpt-4-turbo"
  → Shows: temperature, top_p, top_k, frequency_penalty, ... (GPT-4's params)

Parameters adapt automatically to each model!
```

### Phase 5: Inline Parameter Validation ✅

**What it does**: Validates parameter values in real-time and prevents saving invalid configurations.

**Features**:
- Real-time validation on parameter value changes
- Save button disabled when any parameter invalid
- Pre-save comprehensive validation pass
- Error count display in toast messages
- Validation state tracked per runner/capability
- Integration with existing unsaved changes logic

**Key Implementation**:
```kotlin
// T045: Add validation state
private val validationState = ParameterValidationState()

// T048: Validate on parameter change
fun onParameterChanged(param: String, value: Any?, original: Any?) {
    // ... track unsaved changes ...

    // Validate using schema
    val schema = schemas.find { it.name == param }
    if (schema != null) {
        validationState.validateParameter(capability, runner, param, value, schema)
    }

    // T050: Update Save button (requires BOTH dirty AND valid)
    btnSave.isEnabled = hasDirtyChanges && isValid
}

// T051: Pre-save validation
fun saveSettings() {
    val isValid = validationState.validateRunner(capability, runner, params, schemas)
    if (!isValid) {
        val errorCount = validationState.getErrorCount(capability, runner)
        Toast.makeText("Cannot save: $errorCount parameter(s) invalid").show()
        return  // Prevent save
    }
    // ... proceed with save ...
}
```

---

## Architecture

### New Components

**State Management** (`model/ui/`):
- `UnsavedChangesState.kt` - Tracks dirty state per runner/capability
  - `trackChange()` - Record parameter modifications
  - `hasAnyUnsavedChanges()` - Check if save needed
  - `getDirtyRunners()` - List all modified runners
  - `clearAll()` - Reset after save/discard

- `ParameterValidationState.kt` - Validation error storage (ready for inline validation)

**Model Fetching** (`runner/openrouter/models/`):
- `ModelInfo.kt` - Model metadata (id, name, pricing, context length)
- `OpenRouterModelFetcher.kt` - API client with caching
  - `fetchModels()` - Get all models with 24hr cache
  - `filterByPrice()` - Filter by max price threshold
  - Handles API errors gracefully

- `ModelParametersFetcher.kt` - Fetch model-specific parameters
  - `fetchSupportedParameters()` - Get params for specific model
  - `parseModelId()` - Split model ID into author/slug

**UI Components** (`ui/dialogs/`):
- `UnsavedChangesDialog.kt` - Reusable Material Design dialog extension

**Enums**:
- `SaveOperationState` - IDLE, IN_PROGRESS, SUCCESS, FAILED

### UI Layout Changes

**New Cards** (`activity_engine_settings.xml`):
1. Runner Selection Card (always visible)
2. Authentication Card (API Key) - shows for OpenRouter only
3. Model Price Filter Card - shows for OpenRouter only
4. Runner Parameters Card - dynamically filtered
5. Progress Overlay - touch-blocking during saves

**UI Flow**:
```
Capability Tabs (LLM/ASR/TTS/VLM)
  ↓
Runner Selection Dropdown
  ↓ (if OpenRouter selected)
API Key Card
  ↓
Model Price Filter (SeekBar + Refresh button)
  ↓
Filtered Parameters (temperature, top_p, etc.)
  ↓
Save Settings Button
```

---

## Key Implementation Patterns

### Pattern 1: Preventing False Dirty State

**Problem**: `setText()` triggers `afterTextChanged()` immediately during initialization.

**Solution**: Use initialization flag for ALL parameter types
```kotlin
val initialValue = currentValues[schema.name] ?: schema.defaultValue

// Initialize memory BEFORE creating UI
if (initialValue != null) {
    currentRunnerParameters[schema.name] = initialValue
}

var isInitializing = true
addTextChangedListener {
    if (isInitializing) {
        isInitializing = false
        return  // Skip init callback
    }
    // Track real changes
}
```

### Pattern 2: Distinguishing User vs Programmatic Changes

**Problem**: Spinner changes trigger listener during tab switches and initial load.

**Solution**: Use `isLoadingRunners` flag
```kotlin
private var isLoadingRunners = false

fun loadRunnersForCapability() {
    isLoadingRunners = true
    // ... populate spinner ...
    isLoadingRunners = false
}

spinnerRunners.onItemSelectedListener = {
    if (isLoadingRunners) return  // Skip programmatic changes
    // Track user selection
}
```

### Pattern 3: Preserving State During Refresh

**Problem**: `clearParameterViews()` cleared ALL parameters including API key.

**Solution**: Preserve specific parameters
```kotlin
fun clearParameterViews() {
    containerParameters.removeAllViews()

    // Preserve API key (it's in separate card, not regenerated)
    val preservedApiKey = currentRunnerParameters["api_key"]
    currentRunnerParameters.clear()

    if (preservedApiKey != null) {
        currentRunnerParameters["api_key"] = preservedApiKey
    }
}
```

### Pattern 4: Context-Aware User Feedback

**Problem**: Users didn't know model changes require new conversation.

**Solution**: Detect what changed and show appropriate message
```kotlin
val oldModel = currentSettings.getRunnerParameters(runner)["model"]
val newModel = currentRunnerParameters["model"]
val modelChanged = oldModel != newModel

val message = if (modelChanged) {
    "Settings saved! Please start a new conversation to use the new model."
} else {
    "Settings saved successfully"
}
Toast.makeText(this, message, Toast.LENGTH_LONG).show()
```

---

## Testing

### Automated Tests (New in 2025-11-11)

**Phase 2: Foundational Unit Tests** (`src/test/java/.../model/ui/`):
- `UnsavedChangesStateTest.kt` (16 tests)
  - Per-capability independence
  - Original parameter restoration
  - Null value handling (null→value, value→null, null→null)
  - Specific runner clearing
  - Value reversion clearing dirty state
  - Multiple parameters per runner
  - Multi-capability dirty runner detection

- `ParameterValidationStateTest.kt` (21 tests)
  - validateRunner with multiple parameters
  - isAllValid across multiple runners
  - clearError for individual parameters
  - Error persistence across updates
  - Per-runner isolation
  - Edge cases coverage

**Phase 3: User Story 1 Tests**:
- `UnsavedChangesDialogTest.kt` (9 Robolectric tests)
  - Dialog title and message correctness
  - Three buttons (Save/Discard/Cancel) present
  - Each button triggers correct callback
  - Dialog not cancelable on outside touch
  - Multiple dirty runners listed correctly

- `UnsavedChangesFlowTest.kt` (7 UI tests)
  - Full flow: modify → back press → dialog appears
  - Cancel dismisses dialog and stays in activity
  - Discard exits without saving
  - Save persists changes and exits
  - No changes = immediate exit (no dialog)
  - Save button disabled/enabled based on changes
  - Direct save button click works

**Running Tests**:
```bash
# All unit tests
cd android && ./gradlew :breeze-app-engine:test

# UI tests (requires device/emulator)
cd android && ./gradlew :breeze-app-engine:connectedAndroidTest

# Test reports
android/breeze-app-engine/build/reports/tests/testDebugUnitTest/index.html
```

### Manual Test Checklist

**Basic Flow**:
1. ✅ Open Engine Settings → LLM tab
2. ✅ Runner dropdown shows saved runner (not default)
3. ✅ Save button disabled initially (gray)
4. ✅ Change runner → Save button enables (orange)
5. ✅ Save → Runner persists on reopen

**OpenRouter Flow**:
1. ✅ Select "OpenRouterLLMRunner"
2. ✅ API Key card appears
3. ✅ Enter API key → Save button enables
4. ✅ Click "Refresh" → Models load
5. ✅ Slide price filter → Model count updates
6. ✅ Select model (e.g., "google/gemini-3n-e4b-it:free")
7. ✅ Parameters filter to model-specific ones
8. ✅ Change model → Parameters dynamically update
9. ✅ Save → Toast: "Please start new conversation"
10. ✅ Go to chat → New conversation → Uses selected model

**Unsaved Changes**:
1. ✅ Modify parameter → Save button enables
2. ✅ Press back → Dialog appears
3. ✅ Test Save/Discard/Cancel actions
4. ✅ Works for runner selection changes
5. ✅ Works for parameter changes
6. ✅ Works across all tabs (LLM/ASR/TTS/VLM)

**Cross-Tab Persistence**:
1. ✅ LLM tab: Change to OpenRouter → Save
2. ✅ Switch to ASR tab → Change runner → Save
3. ✅ Switch back to LLM → Still shows OpenRouter
4. ✅ Reopen activity → All selections persist

### Debug Logging

```bash
# Monitor all Engine Settings activity
adb logcat -s EngineSettingsActivity:D ModelFetcher:D ModelParamsFetcher:D AIEngineManager:D

# Expected output when selecting model:
D  Fetching supported parameters for model: google/gemini-flash-1.5:free
D  Model supports 8 parameters: [temperature, top_p, max_tokens, ...]
D  Filtered parameters for model: showing [model, temperature, top_p, ...]

# Expected output when saving:
D  Runner changed from 'ExecutorchLLMRunner' to 'OpenRouterLLMRunner'
D  Settings saved successfully

# Expected output during inference:
D  Using model from settings: 'google/gemini-flash-1.5:free'
D  Loading OpenRouterLLMRunner with model 'google/gemini-flash-1.5:free'
D  API key found, proceeding with inference
```

---

## Files Changed

### New Files (9)

**State Management**:
- `android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/model/ui/UnsavedChangesState.kt` (125 lines)
- `android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/model/ui/ParameterValidationState.kt` (95 lines)

**Model Fetching**:
- `android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/runner/openrouter/models/ModelInfo.kt` (89 lines)
- `android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/runner/openrouter/models/OpenRouterModelFetcher.kt` (235 lines)
- `android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/runner/openrouter/models/ModelParametersFetcher.kt` (125 lines)

**UI Components**:
- `android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/ui/dialogs/UnsavedChangesDialog.kt` (45 lines)

**Tests** (stubs for future expansion):
- `android/breeze-app-engine/src/test/java/.../UnsavedChangesStateTest.kt`
- `android/breeze-app-engine/src/test/java/.../ParameterValidationStateTest.kt`
- Test directories for UI tests

### Modified Files (6)

**Core Activity** (+650 lines):
- `android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/ui/EngineSettingsActivity.kt`
  - Added state tracking and API integration
  - Added parameter initialization logic
  - Added runner selection tracking
  - Added model-specific parameter filtering

**UI Layout** (+130 lines):
- `android/breeze-app-engine/src/main/res/layout/activity_engine_settings.xml`
  - Added API Key card
  - Added Model Price Filter card
  - Added progress overlay

**Resources**:
- `android/breeze-app-engine/src/main/res/values/strings.xml`
  - Added dialog strings

**Core Engine**:
- `android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/core/AIEngineManager.kt`
  - Fixed model parameter name (`"model"` not `"model_id"`)
  - Added cloud runner RAM check bypass

**Runner Configuration**:
- `android/breeze-app-engine/src/main/java/com/mtkresearch/breezeapp/engine/runner/openrouter/OpenRouterLLMRunner.kt`
  - Reduced preset models to free tier only
  - Updated schema for API-driven model selection

---

## Known Patterns & Lessons Learned

### 1. Always Initialize Parameters Before Creating UI

**Why**: Prevents parameters from being lost when user doesn't manually change them.

```kotlin
// ✅ CORRECT: Initialize first
val initialValue = currentValues[schema.name]
if (initialValue != null) {
    currentRunnerParameters[schema.name] = initialValue
}
// Then create UI field

// ❌ WRONG: Only add on user change
addTextChangedListener {
    currentRunnerParameters[schema.name] = value  // Lost if user doesn't change!
}
```

### 2. Use Flags to Distinguish Change Sources

**Why**: Prevents false dirty state from programmatic changes (tab switches, initial loads).

```kotlin
private var isLoadingRunners = false  // Programmatic changes
var isInitializing = true             // Initialization in each field

// Check flag before tracking
if (!isLoadingRunners && !isInitializing) {
    trackChange()
}
```

### 3. Save Both Runner Selection AND Parameters

**Why**: Runner selection and parameters are separate concerns in EngineSettings.

```kotlin
// ✅ CORRECT: Save both
var settings = currentSettings.withRunnerSelection(capability, runner)
settings = settings.withRunnerParameters(runner, params)

// ❌ WRONG: Only saving parameters loses runner selection
val settings = currentSettings.withRunnerParameters(runner, params)
```

### 4. Update currentSettings After Save

**Why**: Allows subsequent operations (like Refresh) to access newly saved values.

```kotlin
runnerManager?.saveSettings(updatedSettings)
currentSettings = updatedSettings  // ← Essential!
```

### 5. Provide Context-Aware Feedback

**Why**: Users need to know when changes require special actions (like starting new conversation).

```kotlin
val modelChanged = detectModelChange()
val message = if (modelChanged) {
    "Please start a new conversation to use the new model"
} else {
    "Settings saved successfully"
}
```

---

## Integration with Other Systems

### AIEngineManager Integration

**Cloud Runner Detection**:
```kotlin
val isCloudRunner = runnerName.contains("OpenRouter", ignoreCase = true) ||
                   runnerName.contains("cloud", ignoreCase = true)

if (isCloudRunner) {
    // Skip RAM validation for cloud runners
    logger.d("Skipping RAM check for cloud runner: $runnerName")
}
```

**Model Parameter Resolution**:
```kotlin
// Reads "model" parameter (not "model_id")
val resolvedModel = settings.getRunnerParameters(runnerName)[InferenceRequest.PARAM_MODEL]
    ?: getDefaultModelForRunner(runnerName)
```

### RunnerManager Integration

**Settings Persistence**:
```kotlin
// Save both runner selection and parameters
val updatedSettings = currentSettings
    .withRunnerSelection(capability, selectedRunner)
    .withRunnerParameters(selectedRunner, currentRunnerParameters)

runnerManager?.saveSettings(updatedSettings)
```

---

## Performance Considerations

**Caching Strategy**:
- Model list cached for 24 hours in SharedPreferences
- Reduces API calls from dozens/day to 1/day
- Graceful fallback to stale cache on API failures

**Async Operations**:
- All API calls use `lifecycleScope.launch`
- Non-blocking UI during network operations
- Progress indicators for user feedback

**Memory Management**:
- No Activity references in callbacks (prevents leaks)
- Coroutines tied to Activity lifecycle
- Proper null safety (`?.` not `!!`)

---

## Future Enhancements (Optional)

### Phase 5: Inline Validation
- Real-time validation errors displayed inline
- Min/max range validation for numeric fields
- Pre-save validation pass preventing invalid saves

### Phase 6: Comprehensive Testing
- Unit tests for state management classes
- UI tests for critical user flows
- Integration tests with mock API responses

### Phase 7: Advanced Features
- Model search/filter by name
- Favorite models bookmarking
- Parameter presets (e.g., "Creative", "Precise", "Balanced")
- Model comparison tool

---

## Troubleshooting

**Issue**: Save button doesn't enable when changing parameter
**Fix**: Check that `onParameterChanged()` is called and `isInitializing` is handled correctly

**Issue**: Runner selection doesn't persist
**Fix**: Verify `withRunnerSelection()` is called in both save methods

**Issue**: Model selection shows old model after change
**Fix**: Check that `currentSettings` is updated after save

**Issue**: Parameters don't filter for selected model
**Fix**: Verify API key is set and check logs for parameter fetch errors

**Issue**: API key lost after refresh
**Fix**: Check that `clearParameterViews()` preserves API key

---

## Summary

The Engine Settings UI Enhancement delivers a production-ready configuration experience with:
- ✅ Safe configuration changes (no data loss via unsaved changes protection)
- ✅ Dynamic model discovery (always up-to-date via OpenRouter API)
- ✅ Intelligent parameter filtering (only show what model supports)
- ✅ Parameter validation (prevents invalid configurations)
- ✅ Comprehensive testing (53 automated tests)
- ✅ Clear user feedback (knows what to expect)
- ✅ Robust error handling (graceful degradation)

**Implementation Stats**:
- 16 commits (13 feature + 3 test)
- ~3,500 lines of code and tests
- 53 automated tests (37 unit + 16 UI)
- 0 new dependencies
- Production-ready and merged to main!

**Test Coverage**:
- UnsavedChangesState: 16 tests covering all methods
- ParameterValidationState: 21 tests covering validation logic
- Unsaved Changes Dialog: 9 Robolectric tests
- Complete User Flow: 7 UI tests (Espresso)

<!-- MANUAL ADDITIONS END -->
