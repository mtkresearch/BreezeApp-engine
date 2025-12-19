package com.mtkresearch.breezeapp.engine.model

/**
 * Test metrics for different AI capabilities.
 * All runner-provided fields are nullable for graceful degradation.
 */
sealed class TestMetrics(
    open val success: Boolean,
    open val errorMessage: String?
) {
    /**
     * LLM (Language Model) test metrics.
     * Focus: Time to first token, throughput
     */
    data class LLM(
        val totalLatency: Long,
        override val success: Boolean,
        override val errorMessage: String?,
        val timeToFirstToken: Long? = null,      // Nullable - may not have streaming
        val responseLength: Int? = null,
        val tokenCount: Int? = null              // From metadata if available
    ) : TestMetrics(success, errorMessage)
    
    /**
     * TTS (Text-to-Speech) test metrics.
     * Focus: Time to first audio, audio quality
     */
    data class TTS(
        val totalLatency: Long,
        override val success: Boolean,
        override val errorMessage: String?,
        val timeToFirstAudio: Long? = null,      // Nullable - may not be streaming
        val audioSize: Int? = null,              // From result if available
        val sampleRate: Int? = null,             // From metadata if available
        val audioDurationMs: Long? = null        // From metadata if available
    ) : TestMetrics(success, errorMessage)
    
    /**
     * ASR (Speech Recognition) test metrics.
     * Focus: Transcription speed, accuracy
     */
    data class ASR(
        val totalLatency: Long,
        override val success: Boolean,
        override val errorMessage: String?,
        val transcriptionLength: Int? = null,
        val wordCount: Int? = null,
        val confidence: Float? = null            // From metadata if available
    ) : TestMetrics(success, errorMessage)
    
    /**
     * Guardrail (Content Safety) test metrics.
     * Focus: Response time, safety classification
     */
    data class Guardrail(
        val totalLatency: Long,
        override val success: Boolean,
        override val errorMessage: String?,
        val isSafe: Boolean? = null,
        val categories: List<String>? = null,
        val confidence: Float? = null
    ) : TestMetrics(success, errorMessage)
}
