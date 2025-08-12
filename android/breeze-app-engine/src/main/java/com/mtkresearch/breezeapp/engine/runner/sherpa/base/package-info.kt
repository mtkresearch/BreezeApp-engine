/**
 * Package containing base classes for Sherpa ONNX runners.
 *
 * This package provides abstract base classes that implement common functionality
 * for all Sherpa-based runners, following Clean Architecture principles:
 *
 * 1. [BaseSherpaRunner] - 
 *    Base class for all Sherpa runners with common functionality like lifecycle 
 *    management, logging, and error handling.
 *
 * 2. [BaseSherpaAsrRunner] - 
 *    Base class for ASR runners with common ASR functionality like audio processing,
 *    streaming support, and microphone handling.
 *
 * 3. [BaseSherpaTtsRunner] - 
 *    Base class for TTS runners with common TTS functionality like audio playback
 *    management and PCM conversion utilities.
 *
 * These base classes eliminate code duplication and ensure consistent implementation
 * patterns across all Sherpa runner implementations.
 *
 * @see BaseSherpaRunner
 * @see BaseSherpaAsrRunner
 * @see BaseSherpaTtsRunner
 */
package com.mtkresearch.breezeapp.engine.runner.sherpa.base