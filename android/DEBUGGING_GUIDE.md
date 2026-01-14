# Download Progress UI Debugging Guide

## Current Issue
Neither breathing border nor download bottom sheet are showing.

## Debug Steps

### Step 1: Test Basic Download UI (Engine Settings)
1. Open Engine Settings
2. Click "Test Download Progress" button
3. **Expected**: Download progress bottom sheet should appear
4. **Check logs for**: 
   - `DownloadUIManager: Current activity updated: EngineSettingsActivity`
   - `DownloadEventManager: Broadcasting download started`
   - `DownloadUIManager: Download progress UI shown`

### Step 2: Test Breathing Border (Basic AI Request)
1. Make a simple chat request through the engine
2. **Expected**: Breathing border should appear during processing
3. **Check logs for**: 
   - `BreathingBorderManager: Starting breathing border`
   - Visual breathing border animation

### Step 3: Test Download Service
1. Check if ModelDownloadService starts properly
2. **Check logs for**:
   - `ModelDownloadService: ModelDownloadService created`
   - `ModelDownloadService: Started ModelDownloadService for model`

### Step 4: Test Activity Tracking
1. Open any activity
2. **Check logs for**:
   - `DownloadUIManager: Current activity updated: [ActivityName]`

## Potential Issues

### Issue 1: DownloadUIManager Not Initialized
**Symptom**: No activity tracking logs
**Fix**: Check if `BreezeAppEngineService.onCreate()` is called
**Log check**: `DownloadUIManager: DownloadUIManager initialized`

### Issue 2: BaseDownloadAwareActivity Conflicts
**Symptom**: EngineSettingsActivity behavior changed
**Fix**: Ensure BaseDownloadAwareActivity doesn't interfere

### Issue 3: Event System Not Working
**Symptom**: Download service starts but no UI
**Log check**: 
- `DownloadEventManager: Broadcasting download started`
- `DownloadUIManager: Download progress UI shown`

### Issue 4: Fragment Manager Issues
**Symptom**: UI system fails silently
**Check**: Fragment transactions and lifecycle

## Quick Rollback Test
To isolate the issue, temporarily restore the direct UI call:

```kotlin
// In showTestDownloadProgress()
DownloadProgressBottomSheet.show(this, supportFragmentManager)
```

If this works, the issue is with the global system.
If this doesn't work, the issue is more fundamental.

## Log Commands
```bash
# Monitor all download-related logs
adb logcat -s DownloadUIManager:D DownloadEventManager:D ModelDownloadService:D EngineSettingsActivity:D

# Monitor breathing border logs  
adb logcat -s BreathingBorderManager:D

# Monitor engine service logs
adb logcat -s BreezeAppEngineService:D
```

## Expected Log Flow
```
BreezeAppEngineService: BreezeAppEngineService creating...
DownloadUIManager: DownloadUIManager initialized
DownloadUIManager: Current activity updated: EngineSettingsActivity
EngineSettingsActivity: Test download button clicked
ModelDownloadService: Started ModelDownloadService for model: test-model-id
DownloadEventManager: Broadcasting download started for model: test-model-id
DownloadUIManager: Download progress UI shown in EngineSettingsActivity
```