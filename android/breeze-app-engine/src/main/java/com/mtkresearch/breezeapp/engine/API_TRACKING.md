# API Tracking Table

This document tracks all APIs we've examined to ensure we don't have duplicate implementations.

| Class | Method | Purpose | Status |
|-------|--------|---------|--------|
| BreezeAppEngineService | onCreate() | Service initialization | Examined |
| BreezeAppEngineService | onBind() | Client binding | Examined |
| BreezeAppEngineService | onUnbind() | Client unbinding | Examined |
| BreezeAppEngineService | onStartCommand() | Service start handling | Examined |
| BreezeAppEngineService | onDestroy() | Service cleanup | Examined |
| BreezeAppEngineService | isModelReadyForInference() | Model readiness check | Examined |
| BreezeAppEngineService | isCategoryReadyForInference() | Category readiness check | Examined |
| ServiceOrchestrator | initialize() | Component initialization | Examined |
| ServiceOrchestrator | getServiceBinder() | Get service binder | Examined |
| ServiceOrchestrator | updateForegroundServiceType() | Update service type | Examined |
| ServiceOrchestrator | getCurrentServiceType() | Get current service type | Examined |
| ServiceOrchestrator | forceForegroundForMicrophone() | Force foreground state | Examined |
| ServiceOrchestrator | cleanup() | Resource cleanup | Examined |
| BreezeAppEngineCore | initialize() | Engine initialization | Examined |
| BreezeAppEngineCore | shutdown() | Engine shutdown | Examined |
| BreezeAppEngineCore | processInferenceRequest() | Process inference request | Examined |
| BreezeAppEngineCore | processStreamingRequest() | Process streaming request | Examined |
| BreezeAppEngineCore | getEngineStatus() | Get engine status | Examined |
| BreezeAppEngineCore | getPerformanceMetrics() | Get performance metrics | Examined |
| BreezeAppEngineCore | generateRequestId() | Generate request ID | Examined |
| AIEngineManager | process() | Process inference request | Examined |
| AIEngineManager | processStream() | Process streaming request | Examined |
| AIEngineManager | cancelRequest() | Cancel request | Examined |
| AIEngineManager | cleanup() | Cleanup resources | Examined |
| AIEngineManager | unloadAllModels() | Unload models | Examined |
| AIEngineManager | forceCleanupAll() | Force cleanup | Examined |
| BaseRunner | load() | Load model | Examined |
| BaseRunner | run() | Run inference | Examined |
| BaseRunner | unload() | Unload model | Examined |
| BaseRunner | getCapabilities() | Get capabilities | Examined |
| BaseRunner | isLoaded() | Check if loaded | Examined |
| BaseRunner | getRunnerInfo() | Get runner info | Examined |
| FlowStreamingRunner | runAsFlow() | Run as flow | Examined |
| RunnerRegistry | register() | Register runner | Examined |
| RunnerRegistry | unregister() | Unregister runner | Examined |
| RunnerRegistry | createRunner() | Create runner instance | Examined |
| RunnerRegistry | getRunnerForCapability() | Get runner for capability | Examined |
| RunnerRegistry | isRegistered() | Check if registered | Examined |
| RunnerRegistry | getRegisteredRunners() | Get registered runners | Examined |
| RunnerRegistry | getRunnersForCapability() | Get runners for capability | Examined |
| RunnerRegistry | getSupportedCapabilities() | Get supported capabilities | Examined |
| RequestCoordinator | processChatRequest() | Process chat request | Examined |
| RequestCoordinator | processTTSRequest() | Process TTS request | Examined |
| RequestCoordinator | processASRRequest() | Process ASR request | Examined |
| RequestCoordinator | cancelRequest() | Cancel request | Examined |
| RequestCoordinator | processCapabilityRequest() | Process capability request | Examined |
| RequestProcessor | processNonStreamingRequest() | Process non-streaming request | Examined |
| RequestProcessor | processStreamingRequest() | Process streaming request | Examined |
| CancellationManager | registerRequest() | Register request | Examined |
| CancellationManager | unregisterRequest() | Unregister request | Examined |
| CancellationManager | cancelRequest() | Cancel request | Examined |
| CancellationManager | isContextActive() | Check context active | Examined |
| CancellationManager | checkCancellation() | Check cancellation | Examined |
| CancellationManager | handleCancellationException() | Handle cancellation exception | Examined |
| CancellationManager | cleanup() | Cleanup resources | Examined |
| CancellationManager | getActiveRequestCount() | Get active request count | Examined |
| BaseSherpaRunner | initializeModel() | Initialize model | Examined |
| BaseSherpaRunner | releaseModel() | Release model | Examined |
| BaseSherpaAsrRunner | processSamplesForSingleInference() | Process samples | Examined |
| BaseSherpaAsrRunner | processSamplesAsFlow() | Process samples as flow | Examined |
| BaseSherpaAsrRunner | processMicrophoneAsFlow() | Process microphone as flow | Examined |
| BaseSherpaAsrRunner | convertPcm16ToFloat() | Convert PCM16 to float | Examined |
| SherpaASRRunner | run() | Run ASR | Examined |
| SherpaASRRunner | runAsFlow() | Run ASR as flow | Examined |
| NotificationManager | createNotificationChannel() | Create notification channel | Examined |
| NotificationManager | areNotificationsEnabled() | Check notifications enabled | Examined |
| NotificationManager | openNotificationSettings() | Open notification settings | Examined |
| NotificationManager | createNotification() | Create notification | Examined |
| NotificationManager | updateNotification() | Update notification | Examined |
| NotificationManager | clearNotification() | Clear notification | Examined |
| PermissionManager | isNotificationPermissionRequired() | Check notification permission required | Examined |
| PermissionManager | isNotificationPermissionGranted() | Check notification permission granted | Examined |
| PermissionManager | isMicrophonePermissionGranted() | Check microphone permission granted | Examined |
| PermissionManager | isOverlayPermissionGranted() | Check overlay permission granted | Examined |
| PermissionManager | getCurrentPermissionState() | Get current permission state | Examined |
| PermissionManager | isAllRequiredPermissionsGranted() | Check all permissions granted | Examined |
| PermissionManager | requestNotificationPermission() | Request notification permission | Examined |
| PermissionManager | requestMicrophonePermission() | Request microphone permission | Examined |
| PermissionManager | openOverlayPermissionSettings() | Open overlay permission settings | Examined |
| PermissionManager | requestAllPermissions() | Request all permissions | Examined |
| PermissionManager | handlePermissionResult() | Handle permission result | Examined |
| VisualStateManager | updateVisualState() | Update visual state | Examined |
| VisualStateManager | cleanup() | Cleanup visual components | Examined |
| BreathingBorderManager | showBreathingBorder() | Show breathing border | Examined |
| BreathingBorderManager | hideBreathingBorder() | Hide breathing border | Examined |
| BreathingBorderManager | isPermissionGranted() | Check permission granted | Examined |
| BreathingBorderManager | isOverlayVisible() | Check overlay visible | Examined |
| BreathingBorderManager | cleanup() | Cleanup resources | Examined |
| BreathingBorderView | startAnimation() | Start breathing animation | Examined |
| BreathingBorderView | stopAnimation() | Stop breathing animation | Examined |
| ModelManagementCenter | getInstance() | Get singleton instance | Examined |
| ModelManagementCenter | setStatusManager() | Set status manager | Examined |
| ModelManagementCenter | getModelsByCategory() | Get models by category | Examined |
| ModelManagementCenter | getAvailableModels() | Get available models | Examined |
| ModelManagementCenter | getDownloadedModels() | Get downloaded models | Examined |
| ModelManagementCenter | getDefaultModel() | Get default model | Examined |
| ModelManagementCenter | getModelState() | Get model state | Examined |
| ModelManagementCenter | downloadModel() | Download model | Examined |
| ModelManagementCenter | ensureDefaultModelReady() | Ensure default model ready | Examined |
| ModelManagementCenter | deleteModel() | Delete model | Examined |
| ModelManagementCenter | downloadDefaultModels() | Download default models | Examined |
| ModelManagementCenter | cleanupStorage() | Cleanup storage | Examined |
| ModelManagementCenter | calculateTotalStorageUsed() | Calculate storage used | Examined |
| ModelManagementCenter | getStorageUsageByCategory() | Get storage usage by category | Examined |
| ModelManager | listAvailableModels() | List available models | Examined |
| ModelManager | listDownloadedModels() | List downloaded models | Examined |
| ModelManager | getCurrentModel() | Get current model | Examined |
| ModelManager | downloadModel() | Download model | Examined |
| ModelManager | switchModel() | Switch model | Examined |
| ModelManager | deleteModel() | Delete model | Examined |
| ModelManager | cleanupOldVersions() | Cleanup old versions | Examined |
| ModelRegistry | listAllModels() | List all models | Examined |
| ModelRegistry | getModelInfo() | Get model info | Examined |
| ModelRegistry | filterByHardware() | Filter by hardware | Examined |
| ModelVersionStore | getDownloadedModels() | Get downloaded models | Examined |
| ModelVersionStore | getModelFiles() | Get model files | Examined |
| ModelVersionStore | saveModelMetadata() | Save model metadata | Examined |
| ModelVersionStore | removeModel() | Remove model | Examined |
| ModelVersionStore | getCurrentModelId() | Get current model ID | Examined |
| ModelVersionStore | setCurrentModelId() | Set current model ID | Examined |
| ModelVersionStore | validateModelFiles() | Validate model files | Examined |
| SherpaLibraryManager | initializeCompleteSystem() | Initialize complete system | Examined |
| SherpaLibraryManager | initializeGlobally() | Initialize globally | Examined |
| SherpaLibraryManager | isLibraryReady() | Check library ready | Examined |
| SherpaLibraryManager | markInferenceStarted() | Mark inference started | Examined |
| SherpaLibraryManager | markInferenceCompleted() | Mark inference completed | Examined |
| SherpaLibraryManager | getDiagnosticInfo() | Get diagnostic info | Examined |
| SherpaLibraryManager | forceCleanup() | Force cleanup | Examined |
| GlobalLibraryTracker | initialize() | Initialize tracker | Examined |
| GlobalLibraryTracker | markInferenceStarted() | Mark inference started | Examined |
| GlobalLibraryTracker | markInferenceCompleted() | Mark inference completed | Examined |
| GlobalLibraryTracker | resetInferenceState() | Reset inference state | Examined |
| GlobalLibraryTracker | isLibraryUsable() | Check library usable | Examined |
| GlobalLibraryTracker | getDiagnosticInfo() | Get diagnostic info | Examined |
| NativeLibraryGuardian | registerLibrary() | Register library | Examined |
| NativeLibraryGuardian | unregisterLibrary() | Unregister library | Examined |
| NativeLibraryGuardian | forceCleanupAll() | Force cleanup all | Examined |
| AudioUtil | createAudioTrack() | Create audio track | Examined |
| AudioUtil | writeAudioSamples() | Write audio samples | Examined |
| AudioUtil | prepareForPlayback() | Prepare for playback | Examined |
| AudioUtil | stopAndCleanup() | Stop and cleanup | Examined |
| AudioUtil | hasRecordAudioPermission() | Check record audio permission | Examined |
| AudioUtil | createAudioRecord() | Create audio record | Examined |
| AudioUtil | startRecording() | Start recording | Examined |
| AudioUtil | stopAndReleaseAudioRecord() | Stop and release audio record | Examined |
| AudioUtil | readAudioSamples() | Read audio samples | Examined |
| AudioUtil | createMicrophoneAudioFlow() | Create microphone audio flow | Examined |
| AudioUtil | convertPcm16ToFloat() | Convert PCM16 to float | Examined |
| AudioUtil | convertPcm16BytesToFloat() | Convert PCM16 bytes to float | Examined |
| AudioUtil | getAsrSampleRate() | Get ASR sample rate | Examined |
| AudioUtil | prepareAsrFloatSamples() | Prepare ASR float samples | Examined |
| AudioUtil | resampleLinear() | Resample linear | Examined |
| AudioUtil | floatToPcm16() | Convert float to PCM16 | Examined |
| SherpaTtsConfigUtil | getTtsModelConfig() | Get TTS model config | Examined |
| SherpaTtsConfigUtil | createOfflineTtsConfig() | Create offline TTS config | Examined |
| SherpaTtsConfigUtil | createCustomConfig() | Create custom config | Examined |
| SherpaTtsConfigUtil | getAllModelConfigs() | Get all model configs | Examined |
| SherpaTtsConfigUtil | validateModelAssets() | Validate model assets | Examined |
| AssetCopyUtil | copyAssetsToExternalFiles() | Copy assets to external files | Examined |
| AssetCopyUtil | copyAssetsToInternalFiles() | Copy assets to internal files | Examined |
| EngineServiceBinder | getBinder() | Get binder | Examined |
| ClientManager | registerListener() | Register listener | Examined |
| ClientManager | unregisterListener() | Unregister listener | Examined |
| ClientManager | notifyResponse() | Notify response | Examined |
| ClientManager | notifyChatResponse() | Notify chat response | Examined |
| ClientManager | notifyTTSResponse() | Notify TTS response | Examined |
| ClientManager | notifyASRResponse() | Notify ASR response | Examined |
| ClientManager | notifyError() | Notify error | Examined |
| ClientManager | getClientCount() | Get client count | Examined |
| ClientManager | hasActiveClients() | Check active clients | Examined |
| ClientManager | getLastClientActivity() | Get last client activity | Examined |
| ClientManager | isClientTimeoutReached() | Check client timeout | Examined |
| ClientManager | updateClientActivity() | Update client activity | Examined |
| ClientManager | cleanup() | Cleanup resources | Examined |
| ModelDownloader | listAvailableModels() | List available models | Examined |
| ModelDownloader | getModelInfo() | Get model info | Examined |
| ModelDownloader | downloadModel() | Download model | Examined |
| ModelDownloader | downloadDefaultModel() | Download default model | Examined |
| TtsTestUtil | runComprehensiveTest() | Run comprehensive test | Examined |
| TtsTestUtil | runQuickTest() | Run quick test | Examined |
| SherpaTTSRunner | run() | Run TTS | Examined |
| SherpaTTSRunner | runAsFlow() | Run TTS as flow | Examined |
| BaseSherpaTtsRunner | initAudioPlayback() | Initialize audio playback | Examined |
| BaseSherpaTtsRunner | stopAudioPlayback() | Stop audio playback | Examined |
| BaseSherpaTtsRunner | amplifyVolume() | Amplify volume | Examined |
| BaseSherpaTtsRunner | floatArrayToPCM16() | Convert float to PCM16 | Examined |
| BaseSherpaTtsRunner | validateTtsInput() | Validate TTS input | Examined |
| BaseSherpaTtsRunner | validateSpeakerId() | Validate speaker ID | Examined |
| BaseSherpaTtsRunner | validateSpeed() | Validate speed | Examined |
| BaseSherpaRunner | load() | Load model | Examined |
| BaseSherpaRunner | unload() | Unload model | Examined |
| BaseSherpaRunner | isLoaded() | Check if loaded | Examined |
| BaseSherpaRunner | getCapabilities() | Get capabilities | Examined |
| BaseSherpaRunner | getRunnerInfo() | Get runner info | Examined |
| BaseSherpaRunner | initializeModel() | Initialize model | Examined |
| BaseSherpaRunner | releaseModel() | Release model | Examined |
| BreezeAppEngineLauncherActivity | onCreate() | Activity creation | Examined |
| BreezeAppEngineLauncherActivity | checkNotificationPermissionAndStartService() | Check notification permission | Examined |
| BreezeAppEngineLauncherActivity | startBreezeAppEngineService() | Start engine service | Examined |
| ConfigurationManager | loadAndRegisterRunners() | Load and register runners | Examined |
| DownloadModelUseCase | ensureDefaultModelReady() | Ensure default model ready | Examined |
| DownloadModelUseCase | downloadModel() | Download model | Examined |
| Logger | d() | Debug log | Examined |
| Logger | w() | Warning log | Examined |
| Logger | e() | Error log | Examined |
| ExceptionHandler | handleException() | Handle exception | Examined |
| ExceptionHandler | handleFlowException() | Handle flow exception | Examined |
| ExceptionHandler | isNormalCancellation() | Check normal cancellation | Examined |
| EngineConstants | Audio | Audio constants | Examined |
| EngineConstants | Request | Request constants | Examined |
| EngineConstants | Service | Service constants | Examined |
| EngineConstants | Model | Model constants | Examined |
| EngineConstants | ErrorCodes | Error codes | Examined |