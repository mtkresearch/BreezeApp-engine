package com.mtkresearch.breezeapp.engine.core

import com.mtkresearch.breezeapp.engine.model.EngineSettings

/**
 * Defines the contract for persisting and retrieving engine settings.
 * This abstraction allows for different storage implementations (e.g., SharedPreferences, DataStore).
 */
interface StorageService {
    /**
     * Loads the engine settings from persistence.
     *
     * @return The loaded [EngineSettings], or default settings if none are stored.
     */
    fun loadSettings(): EngineSettings

    /**
     * Saves the given engine settings to persistence.
     *
     * @param settings The [EngineSettings] to save.
     */
    fun saveSettings(settings: EngineSettings)
}
