// IAIEngineService.aidl
// BreezeApp AI Engine Service Interface
// Package: com.mtkresearch.breezeapp.engine
//
// Version History:
// - V1 (API_VERSION = 1): Initial release with LLM, ASR, TTS
// - V2 (API_VERSION = 2): Added streaming, VLM support
//
// Breaking Changes Policy:
// - MAJOR version increment: Remove/rename methods, change signatures
// - MINOR version increment: Add new methods with default implementations
// - PATCH version increment: Internal bug fixes, no API changes

package com.mtkresearch.breezeapp.engine;

import com.mtkresearch.breezeapp.engine.IInferenceCallback;
import com.mtkresearch.breezeapp.engine.IStreamCallback;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

/**
 * Main AIDL interface for BreezeApp AI Engine service.
 *
 * Security:
 * - Protected by signature-level permission:
 *   com.mtkresearch.breezeapp.engine.permission.BIND_AI_SERVICE
 * - Only apps signed with the same certificate can bind this service
 *
 * Thread Safety:
 * - All methods are synchronous unless marked 'oneway'
 * - Callbacks are invoked on Binder thread pool (client must handle threading)
 *
 * Error Handling:
 * - Methods return error codes or throw RemoteException
 * - Use callbacks for async operations to report errors
 */
interface IAIEngineService {
    /**
     * API version constants for compatibility checking.
     * Clients MUST call getVersion() before using other methods.
     */
    const int API_VERSION_1_0 = 1;   // LLM, ASR, TTS support
    const int API_VERSION_2_0 = 2;   // Added streaming, VLM
    const int CURRENT_VERSION = 2;

    /**
     * Error codes returned by methods
     */
    const int ERROR_NONE = 0;
    const int ERROR_INVALID_PARAMS = -1;
    const int ERROR_MODEL_NOT_LOADED = -2;
    const int ERROR_INFERENCE_FAILED = -3;
    const int ERROR_PERMISSION_DENIED = -4;
    const int ERROR_VERSION_MISMATCH = -5;
    const int ERROR_RESOURCE_EXHAUSTED = -6;

    /**
     * Get the current API version of this service.
     *
     * @return API version number (CURRENT_VERSION)
     *
     * Usage:
     *   int version = service.getVersion();
     *   if (version < MIN_REQUIRED_VERSION) {
     *       // Prompt user to update engine
     *   }
     */
    int getVersion();

    /**
     * Get detailed version information including semantic version and build code.
     *
     * @return Bundle with keys:
     *   - "major" (int): Major version
     *   - "minor" (int): Minor version
     *   - "patch" (int): Patch version
     *   - "buildCode" (int): Android versionCode
     *   - "apiVersion" (int): AIDL API version
     *   - "semanticVersion" (String): e.g., "2.1.0"
     *
     * Added in: API_VERSION_2_0
     */
    Bundle getVersionInfo();

    /**
     * Get supported capabilities of this engine instance.
     *
     * @return Bundle with boolean flags:
     *   - "llm" (boolean): LLM inference support
     *   - "vlm" (boolean): Vision-language model support
     *   - "asr" (boolean): Speech recognition support
     *   - "tts" (boolean): Text-to-speech support
     *   - "streaming" (boolean): Streaming inference support
     *   - "npu" (boolean): NPU hardware acceleration available
     *
     * Added in: API_VERSION_2_0
     */
    Bundle getCapabilities();

    // ============================================================================
    // LLM (Large Language Model) Methods
    // ============================================================================

    /**
     * Perform synchronous text inference using LLM.
     *
     * @param input User input text (max 4096 characters for single request)
     * @param params Inference parameters Bundle with keys:
     *   - "temperature" (float): 0.0-2.0, default 0.7
     *   - "topK" (int): Top-K sampling, default 40
     *   - "topP" (float): Top-P sampling, default 0.9
     *   - "maxTokens" (int): Max output tokens, default 512
     *   - "stopSequences" (String[]): Stop sequences, optional
     *
     * @return Inference result text, or null on error
     *
     * Note: For responses >512KB, use inferTextAsync with callback.
     * Maximum response size: 500KB (Binder limit constraint)
     */
    String inferText(String input, in Bundle params);

    /**
     * Perform asynchronous text inference with callback for large responses.
     *
     * @param input User input text
     * @param params Inference parameters (same as inferText)
     * @param callback Callback for receiving chunked results
     *
     * Added in: API_VERSION_2_0
     */
    oneway void inferTextAsync(String input, in Bundle params, IInferenceCallback callback);

    /**
     * Perform streaming text inference with real-time token generation.
     *
     * @param input User input text
     * @param params Inference parameters
     * @param callback Stream callback receiving tokens as generated
     *
     * Added in: API_VERSION_2_0
     */
    oneway void inferTextStreaming(String input, in Bundle params, IStreamCallback callback);

    // ============================================================================
    // VLM (Vision-Language Model) Methods
    // ============================================================================

    /**
     * Perform vision-language inference with image and text input.
     *
     * @param imageFd ParcelFileDescriptor for image file (JPEG/PNG)
     * @param prompt Text prompt describing the task
     * @param params Inference parameters (similar to LLM)
     *
     * @return Inference result text
     *
     * Note: Image file is read and closed by service. Max image size: 10MB.
     * Supported formats: JPEG, PNG, WebP
     *
     * Added in: API_VERSION_2_0
     */
    String inferVision(in ParcelFileDescriptor imageFd, String prompt, in Bundle params);

    // ============================================================================
    // ASR (Automatic Speech Recognition) Methods
    // ============================================================================

    /**
     * Perform speech recognition on audio file.
     *
     * @param audioFd ParcelFileDescriptor for audio file (WAV, MP3, FLAC)
     * @param params Recognition parameters Bundle with keys:
     *   - "language" (String): Language code, e.g., "en-US", "zh-TW"
     *   - "enablePunctuation" (boolean): Add punctuation, default true
     *   - "enableWordTimestamps" (boolean): Include word timings, default false
     *
     * @return Bundle with keys:
     *   - "text" (String): Recognized text
     *   - "confidence" (float): Confidence score 0.0-1.0
     *   - "timestamps" (Parcelable[]): Word timestamps if enabled
     *
     * Added in: API_VERSION_1_0
     */
    Bundle recognizeSpeech(in ParcelFileDescriptor audioFd, in Bundle params);

    /**
     * Perform streaming speech recognition with real-time results.
     *
     * @param params Recognition parameters
     * @param callback Stream callback for partial results
     *
     * @return SharedMemory handle for writing audio data (client writes, service reads)
     *
     * Added in: API_VERSION_2_0
     */
    ParcelFileDescriptor recognizeSpeechStreaming(in Bundle params, IStreamCallback callback);

    // ============================================================================
    // TTS (Text-to-Speech) Methods
    // ============================================================================

    /**
     * Synthesize speech from text.
     *
     * @param text Input text to synthesize
     * @param params Synthesis parameters Bundle with keys:
     *   - "voice" (String): Voice ID, e.g., "en-US-neural"
     *   - "speakingRate" (float): Speed multiplier 0.5-2.0, default 1.0
     *   - "pitch" (float): Pitch adjustment -10 to +10, default 0
     *   - "format" (String): Output format "wav", "mp3", default "wav"
     *
     * @return ParcelFileDescriptor for reading audio data
     *
     * Added in: API_VERSION_1_0
     */
    ParcelFileDescriptor synthesizeSpeech(String text, in Bundle params);

    // ============================================================================
    // Model Management Methods
    // ============================================================================

    /**
     * Get list of available models.
     *
     * @param modelType Type of models to list: "llm", "vlm", "asr", "tts"
     *
     * @return Bundle array, each Bundle contains:
     *   - "id" (String): Model identifier
     *   - "name" (String): Display name
     *   - "size" (long): Model size in bytes
     *   - "loaded" (boolean): Whether currently loaded
     *   - "languages" (String[]): Supported languages
     *
     * Added in: API_VERSION_2_0
     */
    Bundle[] listModels(String modelType);

    /**
     * Load a specific model into memory.
     *
     * @param modelId Model identifier from listModels()
     *
     * @return ERROR_NONE on success, error code otherwise
     *
     * Added in: API_VERSION_2_0
     */
    int loadModel(String modelId);

    /**
     * Unload a model from memory to free resources.
     *
     * @param modelId Model identifier
     *
     * @return ERROR_NONE on success, error code otherwise
     *
     * Added in: API_VERSION_2_0
     */
    int unloadModel(String modelId);

    // ============================================================================
    // Health & Diagnostics Methods
    // ============================================================================

    /**
     * Check service health status.
     *
     * @return Bundle with keys:
     *   - "healthy" (boolean): Overall health status
     *   - "uptime" (long): Service uptime in milliseconds
     *   - "activeConnections" (int): Number of bound clients
     *   - "memoryUsage" (long): Memory usage in bytes
     *   - "lastInferenceTime" (long): Timestamp of last inference
     *
     * Added in: API_VERSION_2_0
     */
    Bundle getHealthStatus();

    /**
     * Ping service to check connectivity.
     *
     * @return Always returns ERROR_NONE if service is alive
     *
     * Added in: API_VERSION_1_0
     */
    int ping();
}
