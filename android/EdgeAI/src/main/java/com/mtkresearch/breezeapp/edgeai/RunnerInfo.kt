package com.mtkresearch.breezeapp.edgeai

/**
 * Information about an AI runner's capabilities
 * 
 * @property name The unique name of the runner
 * @property supportsStreaming Whether the runner supports real-time streaming
 * @property capabilities List of capability types this runner supports (ASR, TTS, LLM, etc.)
 * @property vendor The vendor/provider of this runner
 */
data class RunnerInfo(
    val name: String,
    val supportsStreaming: Boolean,
    val capabilities: List<String>,
    val vendor: String = ""
)
