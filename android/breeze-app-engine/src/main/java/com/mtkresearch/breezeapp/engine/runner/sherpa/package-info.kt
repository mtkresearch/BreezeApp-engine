/**
 * Package containing all Sherpa ONNX runner implementations.
 *
 * This package follows Clean Architecture principles with a hierarchical class structure:
 *
 * 1. Base classes in the [com.mtkresearch.breezeapp.engine.runner.sherpa.base] package
 *    provide common functionality for all Sherpa runners.
 *
 * 2. Concrete implementations extend these base classes to provide specific functionality
 *    for ASR (Automatic Speech Recognition) and TTS (Text To Speech) operations.
 *
 * The hierarchy is:
 * ```
 * BaseSherpaRunner (abstract)
 * ├── BaseSherpaAsrRunner (abstract)
 * │   ├── SherpaASRRunner (streaming ASR)
 * │   └── SherpaOfflineASRRunner (offline ASR)
 * └── BaseSherpaTtsRunner (abstract)
 *     └── SherpaTTSRunner (TTS with audio playback)
 * ```
 *
 * This structure eliminates code duplication, improves maintainability, and ensures
 * consistent error handling and logging across all Sherpa-based runners.
 *
 * @see com.mtkresearch.breezeapp.engine.runner.sherpa.base.BaseSherpaRunner
 * @see com.mtkresearch.breezeapp.engine.runner.sherpa.base.BaseSherpaAsrRunner
 * @see com.mtkresearch.breezeapp.engine.runner.sherpa.base.BaseSherpaTtsRunner
 */
package com.mtkresearch.breezeapp.engine.runner.sherpa