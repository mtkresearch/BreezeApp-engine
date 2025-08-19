package com.mtkresearch.breezeapp.engine.core

import com.mtkresearch.breezeapp.engine.model.EngineSettings

/**
 * A default, in-memory implementation of [StorageService] to act as a placeholder.
 * This allows the application to compile and run without a full persistence layer.
 * It can be replaced by a proper injected implementation (e.g., using SharedPreferences).
 *
 * @param logger A logger instance for diagnostics.
 */
internal class InMemoryStorageService(private val logger: Logger) : StorageService {

    override fun loadSettings(): EngineSettings {
        logger.d("InMemoryStorageService", "Loading settings (returning defaults).")
        // For now, always return default settings.
        return EngineSettings.default()
    }

    override fun saveSettings(settings: EngineSettings) {
        // This is a no-op for the in-memory version.
        // A real implementation would persist the settings to disk.
        logger.d("InMemoryStorageService", "saveSettings called, but it's a no-op. Settings: $settings")
    }
}
