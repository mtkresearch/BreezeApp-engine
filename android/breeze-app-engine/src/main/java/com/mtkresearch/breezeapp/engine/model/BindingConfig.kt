package com.mtkresearch.breezeapp.engine.model

/**
 * Configuration for retry and timeout behavior.
 *
 * @param retryDelayMs Delay before retry attempt (default 2 seconds per clarification)
 * @param bindingTimeoutMs Max time to wait for binding completion (default 20 seconds per Android standard)
 * @param debounceDelayMs Delay before showing reconnecting UI (prevents flicker per risk mitigation)
 * @param maxReconnectAttempts Number of retry attempts (default 1 per clarification)
 */
data class BindingConfig(
    val retryDelayMs: Long = 2000L,
    val bindingTimeoutMs: Long = 20000L,
    val debounceDelayMs: Long = 500L,
    val maxReconnectAttempts: Int = 1
) {
    init {
        require(retryDelayMs > 0) { "Retry delay must be positive" }
        require(bindingTimeoutMs > 0) { "Binding timeout must be positive" }
        require(debounceDelayMs > 0) { "Debounce delay must be positive" }
        require(retryDelayMs < bindingTimeoutMs) { "Retry delay should be less than binding timeout" }
        require(debounceDelayMs < retryDelayMs) { "Debounce delay should be less than retry delay" }
        require(maxReconnectAttempts >= 0) { "Max reconnect attempts cannot be negative" }
    }
}
