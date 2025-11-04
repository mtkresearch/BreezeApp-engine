// IInferenceCallback.aidl
// Callback interface for asynchronous inference operations
// Package: com.mtkresearch.breezeapp.engine

package com.mtkresearch.breezeapp.engine;

import android.os.Bundle;

/**
 * Callback interface for receiving chunked inference results.
 *
 * Usage Pattern:
 *   1. Service performs inference
 *   2. Result is split into chunks (max 256KB per chunk)
 *   3. Each chunk is sent via onChunk()
 *   4. onComplete() is called when all chunks sent
 *   5. onError() is called if inference fails
 *
 * Thread Safety:
 *   - Callbacks are invoked on Binder thread pool
 *   - Client MUST marshal to appropriate thread (e.g., UI thread)
 *
 * Example Implementation:
 *   class MyCallback extends IInferenceCallback.Stub {
 *       private val chunks = mutableListOf<String>()
 *
 *       override fun onChunk(chunk: String, sequenceNumber: Int) {
 *           runOnUiThread {
 *               chunks.add(sequenceNumber, chunk)
 *           }
 *       }
 *
 *       override fun onComplete() {
 *           val fullResult = chunks.joinToString("")
 *           // Process complete result
 *       }
 *
 *       override fun onError(errorCode: Int, message: String) {
 *           Log.e(TAG, "Inference failed: $message")
 *       }
 *   }
 */
interface IInferenceCallback {
    /**
     * Called when a chunk of inference result is available.
     *
     * @param chunk Part of the inference result (UTF-8 encoded text)
     * @param sequenceNumber 0-based chunk sequence number for ordering
     *
     * Note: Chunks may not arrive in order. Use sequenceNumber to reassemble.
     */
    oneway void onChunk(String chunk, int sequenceNumber);

    /**
     * Called when inference is complete and all chunks have been sent.
     *
     * @param metadata Optional metadata Bundle with keys:
     *   - "totalChunks" (int): Total number of chunks sent
     *   - "totalBytes" (long): Total size in bytes
     *   - "inferenceTimeMs" (long): Time taken for inference
     *   - "tokensGenerated" (int): Number of tokens generated (LLM only)
     */
    oneway void onComplete(in Bundle metadata);

    /**
     * Called when inference fails.
     *
     * @param errorCode Error code (see IAIEngineService.ERROR_*)
     * @param message Human-readable error message
     */
    oneway void onError(int errorCode, String message);

    /**
     * Called periodically to report inference progress.
     *
     * @param progress Progress percentage 0-100
     * @param status Current status message (e.g., "Generating tokens...")
     *
     * Added in: API_VERSION_2_0
     */
    oneway void onProgress(int progress, String status);
}
