package com.mtkresearch.breezeapp.engine.model

/**
 * Represents the current state of the AIDL service binding connection.
 *
 * State transitions:
 * - Disconnected → Connecting → Connected
 * - Disconnected → Connecting → Error
 * - Connected → Reconnecting → Connected
 * - Connected → Reconnecting → Error
 * - Connected → Disconnected (unbind or engine uninstalled)
 * - Error → Connecting (user retry)
 */
sealed class EngineConnectionState {
    /**
     * Engine is not bound. Initial state or after unbinding.
     */
    object Disconnected : EngineConnectionState()

    /**
     * Attempting initial binding to engine service.
     */
    object Connecting : EngineConnectionState()

    /**
     * Successfully bound to engine service.
     * @param version Engine version string (e.g., "1.5.0")
     * @param timestamp When connection was established (System.currentTimeMillis())
     */
    data class Connected(
        val version: String,
        val timestamp: Long
    ) : EngineConnectionState() {
        init {
            require(version.isNotEmpty()) { "Version cannot be empty" }
            require(timestamp > 0) { "Timestamp must be positive" }
        }
    }

    /**
     * Attempting to rebind after detecting package change.
     */
    object Reconnecting : EngineConnectionState()

    /**
     * Binding failed after retries.
     * @param message User-friendly error description
     * @param errorCode Optional technical error code
     * @param timestamp When error occurred
     */
    data class Error(
        val message: String,
        val errorCode: String? = null,
        val timestamp: Long
    ) : EngineConnectionState() {
        init {
            require(message.isNotEmpty()) { "Error message cannot be empty" }
            require(timestamp > 0) { "Timestamp must be positive" }
        }
    }
}
