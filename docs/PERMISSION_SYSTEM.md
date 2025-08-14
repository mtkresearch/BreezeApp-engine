# Permission System Documentation

## Overview

The BreezeApp Engine implements a unified permission management system through the `PermissionManager` class. This system handles all Android permissions required by the engine, including notification, microphone, overlay permissions, and audio focus management.

## Architecture

The permission system follows Clean Architecture principles by separating concerns:
- **Permission Checking**: Determining if permissions are granted
- **Permission Requesting**: Requesting permissions from the user
- **Audio Focus Management**: Managing audio focus for microphone operations
- **State Management**: Tracking permission states

## Components

### PermissionManager

The `PermissionManager` is the central component for all permission-related operations:

#### Key Responsibilities
1. **Permission Status Checking**
   - Notification permission (Android 13+)
   - Microphone permission
   - Overlay permission

2. **Permission Requesting**
   - Request individual permissions
   - Request all required permissions

3. **Audio Focus Management**
   - Request audio focus for microphone operations
   - Abandon audio focus when no longer needed
   - Handle audio focus changes

4. **State Tracking**
   - Track current permission states
   - Provide permission state information to other components

#### API Overview

```kotlin
class PermissionManager(private val context: Context) {
    // Permission checking
    fun isNotificationPermissionRequired(): Boolean
    fun isNotificationPermissionGranted(): Boolean
    fun isMicrophonePermissionGranted(): Boolean
    fun isOverlayPermissionGranted(): Boolean
    fun hasAudioFocus(): Boolean
    
    // Permission requesting
    fun requestNotificationPermission(activity: Activity)
    fun requestMicrophonePermission(activity: Activity)
    fun openOverlayPermissionSettings(activity: Activity)
    fun requestAllPermissions(activity: Activity)
    fun requestAudioFocus(): Boolean
    
    // State management
    fun getCurrentPermissionState(): PermissionState
    fun isAllRequiredPermissionsGranted(): Boolean
    
    // Audio focus management
    fun abandonAudioFocus()
    
    // Permission result handling
    fun handlePermissionResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean
}
```

### PermissionState

A data class that represents the current state of all required permissions:

```kotlin
data class PermissionState(
    val notificationGranted: Boolean,
    val microphoneGranted: Boolean,
    val overlayGranted: Boolean
)
```

## Usage Patterns

### Checking Permissions

```kotlin
val permissionManager = PermissionManager(context)

// Check individual permissions
if (permissionManager.isMicrophonePermissionGranted()) {
    // Microphone is available
}

// Check all permissions
if (permissionManager.isAllRequiredPermissionsGranted()) {
    // All permissions granted
}

// Get detailed permission state
val state = permissionManager.getCurrentPermissionState()
```

### Requesting Permissions

```kotlin
// Request specific permissions
permissionManager.requestMicrophonePermission(activity)

// Request all permissions
permissionManager.requestAllPermissions(activity)

// Request audio focus for microphone operations
if (permissionManager.requestAudioFocus()) {
    // Audio focus granted
}
```

### Handling Permission Results

```kotlin
override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    if (permissionManager.handlePermissionResult(requestCode, permissions, grantResults)) {
        // Permission granted
    } else {
        // Permission denied
    }
}
```

## Audio Focus Management

The permission system includes comprehensive audio focus management for microphone operations:

### Requesting Audio Focus

```kotlin
// Request audio focus with proper attributes for voice communication
val focusGranted = permissionManager.requestAudioFocus()
if (focusGranted) {
    // Start microphone operations
}
```

### Abandoning Audio Focus

```kotlin
// Always abandon audio focus when operations are complete
permissionManager.abandonAudioFocus()
```

### Audio Focus Change Handling

The system automatically handles audio focus changes:
- `AUDIOFOCUS_GAIN`: Audio focus acquired
- `AUDIOFOCUS_LOSS`: Permanent loss of audio focus
- `AUDIOFOCUS_LOSS_TRANSIENT`: Temporary loss of audio focus
- `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK`: Loss of audio focus but can duck

## Integration with Service Components

The `ServiceOrchestrator` integrates with the `PermissionManager` for audio focus operations:

```kotlin
class ServiceOrchestrator {
    private lateinit var permissionManager: PermissionManager
    
    fun forceForegroundForMicrophone() {
        // Request audio focus through PermissionManager
        val focusGranted = permissionManager.requestAudioFocus()
        
        if (focusGranted) {
            // Proceed with microphone operations
        }
    }
    
    fun cleanup() {
        // Abandon audio focus through PermissionManager
        permissionManager.abandonAudioFocus()
    }
}
```

## Best Practices

### 1. Always Check Permissions Before Use
```kotlin
if (permissionManager.isMicrophonePermissionGranted()) {
    // Safe to use microphone
}
```

### 2. Request Audio Focus for Microphone Operations
```kotlin
if (permissionManager.requestAudioFocus()) {
    // Start recording
}
```

### 3. Properly Abandon Audio Focus
```kotlin
// In cleanup/destroy methods
permissionManager.abandonAudioFocus()
```

### 4. Handle Permission Results
```kotlin
override fun onRequestPermissionsResult(...) {
    permissionManager.handlePermissionResult(...)
}
```

## Error Handling

The permission system includes robust error handling:
- Graceful degradation when permissions are denied
- Logging for debugging permission issues
- Proper resource cleanup even when errors occur

## Testing

The permission system is designed to be testable:
- Mock permission states for unit tests
- Verify permission requests are made correctly
- Test audio focus management scenarios

## Future Extensions

The unified permission system makes it easy to add new permissions:
1. Add new permission checks to `PermissionManager`
2. Add new request methods
3. Update `PermissionState` if needed
4. No changes required in client code due to unified interface