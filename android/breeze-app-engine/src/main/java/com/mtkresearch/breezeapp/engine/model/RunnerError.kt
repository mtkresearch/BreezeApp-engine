package com.mtkresearch.breezeapp.engine.model

/**
 * Runner 錯誤處理格式
 * 統一的錯誤資訊結構
 */
data class RunnerError(
    val code: String,                        // 錯誤碼 (如 E101, E201)
    val message: String,                     // 錯誤描述
    val recoverable: Boolean = false,        // 是否可重試
    val cause: Throwable? = null            // 原始異常
) {
    /**
     * Predefined, consistent error codes for all runners.
     */
    object Code {
        // 1xx: Processing Errors
        const val PROCESSING_ERROR = "E101"      // General processing or inference failure
        const val ASR_FAILURE = "E102"           // ASR-specific transcription failure
        const val TTS_FAILURE = "E103"           // TTS-specific speech synthesis failure

        // 4xx: Client/Input Errors
        const val INVALID_INPUT = "E400"         // Invalid, missing, or unsupported input
        const val PERMISSION_DENIED = "E401"     // Permission denied
        const val RUNNER_NOT_FOUND = "E404"      // No suitable runner found for the request
        const val CAPABILITY_NOT_SUPPORTED = "E405" // Runner does not support the requested capability
        const val STREAMING_NOT_SUPPORTED = "E406" // Streaming is not supported by the runner

        // 5xx: Server/Resource Errors
        const val MODEL_DOWNLOAD_FAILED = "E500" // Failed to download a required model
        const val RESOURCE_UNAVAILABLE = "E501"  // A required resource is not available (e.g. model file not found, but not yet loaded)
        const val MODEL_LOAD_FAILED = "E502"     // A loaded model failed to initialize or run (e.g. corrupt file, runtime error)
        const val HARDWARE_NOT_SUPPORTED = "E503" // Runner hardware requirements not met
        const val INSUFFICIENT_RESOURCES = "E504" // Insufficient resources (e.g. RAM) to load or run the model
    }

    companion object {
        // Factory methods for common errors
        fun processingError(message: String, cause: Throwable? = null) = RunnerError(
            Code.PROCESSING_ERROR, message, true, cause
        )

        fun invalidInput(message: String) = RunnerError(
            Code.INVALID_INPUT, message, false
        )

        fun resourceUnavailable(message: String = "Model or resource not loaded") = RunnerError(
            Code.RESOURCE_UNAVAILABLE, message, true
        )

        fun runnerNotFound(message: String = "No suitable runner found") = RunnerError(
            Code.RUNNER_NOT_FOUND, message, false
        )

        fun runtimeError(message: String, cause: Throwable? = null) = RunnerError(
            Code.PROCESSING_ERROR, message, true, cause
        )

        fun modelNotLoaded(message: String = "Model not loaded or initialized") = RunnerError(
            Code.MODEL_LOAD_FAILED, message, true
        )
    }
} 