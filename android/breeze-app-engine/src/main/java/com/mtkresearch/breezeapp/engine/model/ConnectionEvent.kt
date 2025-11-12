package com.mtkresearch.breezeapp.engine.model

/**
 * Events for state change callbacks and logging.
 * Used for monitoring and analytics.
 */
sealed class ConnectionEvent {
    object EngineDetected : ConnectionEvent()
    object EngineNotFound : ConnectionEvent()

    data class PackageInstalled(val version: String) : ConnectionEvent()
    data class PackageUpdated(val oldVersion: String, val newVersion: String) : ConnectionEvent()
    data class PackageRemoved(val lastVersion: String) : ConnectionEvent()

    object BindingStarted : ConnectionEvent()
    data class BindingSucceeded(val version: String) : ConnectionEvent()
    data class BindingFailed(val reason: String, val willRetry: Boolean) : ConnectionEvent()

    object ReconnectionStarted : ConnectionEvent()
    data class ReconnectionCompleted(val duration: Long) : ConnectionEvent()
}
