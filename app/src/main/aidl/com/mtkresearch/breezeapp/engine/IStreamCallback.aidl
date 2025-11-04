// IStreamCallback.aidl
// Callback interface for streaming inference operations
// Package: com.mtkresearch.breezeapp.engine

package com.mtkresearch.breezeapp.engine;

import android.os.Bundle;

/**
 * Callback interface for receiving real-time streaming inference results.
 *
 * Usage Pattern (LLM Streaming):
 *   1. Client calls inferTextStreaming()
 *   2. Service generates tokens in real-time
 *   3. Each token is sent via onToken() as it's generated
 *   4. onStreamComplete() is called when generation finishes
 *
 * Usage Pattern (ASR Streaming):
 *   1. Client calls recognizeSpeechStreaming()
 *   2. Client writes audio data to returned ParcelFileDescriptor
 *   3. Service sends partial recognition results via onPartialResult()
 *   4. Final result sent via onFinalResult()
 *
 * Thread Safety:
 *   - All callbacks invoked on Binder thread pool
 *   - Callbacks are sequential (no concurrent calls)
 *
 * Example Implementation:
 *   class StreamCallback extends IStreamCallback.Stub {
 *       override fun onToken(token: String, metadata: Bundle) {
 *           runOnUiThread {
 *               textView.append(token)
 *           }
 *       }
 *
 *       override fun onStreamComplete(metadata: Bundle) {
 *           val totalTokens = metadata.getInt("totalTokens")
 *           Log.d(TAG, "Stream complete: $totalTokens tokens")
 *       }
 *   }
 */
interface IStreamCallback {
    /**
     * Called when a new token is generated (LLM streaming).
     *
     * @param token The generated token (may be subword, word, or phrase)
     * @param metadata Optional metadata Bundle with keys:
     *   - "tokenIndex" (int): 0-based index of this token
     *   - "probability" (float): Token probability score 0.0-1.0
     *   - "isEndOfSentence" (boolean): Whether this token ends a sentence
     *
     * Note: Tokens are sent in generation order. Client should append to display.
     */
    oneway void onToken(String token, in Bundle metadata);

    /**
     * Called when partial recognition result is available (ASR streaming).
     *
     * @param partialText Current partial transcription
     * @param isFinal Whether this is a final result for this utterance
     * @param metadata Optional metadata Bundle with keys:
     *   - "confidence" (float): Confidence score 0.0-1.0
     *   - "startTimeMs" (long): Start time of utterance in audio stream
     *   - "endTimeMs" (long): End time of utterance in audio stream
     *
     * Note: Partial results may be superseded by later results.
     */
    oneway void onPartialResult(String partialText, boolean isFinal, in Bundle metadata);

    /**
     * Called when the final recognition result is available (ASR streaming).
     *
     * @param finalText Final transcription text
     * @param metadata Metadata Bundle with keys:
     *   - "confidence" (float): Overall confidence score
     *   - "durationMs" (long): Total audio duration processed
     *   - "wordTimestamps" (Bundle[]): Word-level timestamps if enabled
     */
    oneway void onFinalResult(String finalText, in Bundle metadata);

    /**
     * Called when streaming is complete (all modalities).
     *
     * @param metadata Completion metadata Bundle with keys:
     *   - "totalTokens" (int): Total tokens generated (LLM)
     *   - "totalDurationMs" (long): Total streaming duration
     *   - "averageLatencyMs" (long): Average latency per token/chunk
     */
    oneway void onStreamComplete(in Bundle metadata);

    /**
     * Called when streaming encounters an error.
     *
     * @param errorCode Error code (see IAIEngineService.ERROR_*)
     * @param message Human-readable error message
     *
     * Note: After onError(), no more callbacks will be invoked.
     */
    oneway void onError(int errorCode, String message);

    /**
     * Called when streaming is cancelled by user or system.
     *
     * @param reason Cancellation reason (e.g., "User cancelled", "Timeout")
     */
    oneway void onCancelled(String reason);
}
