package com.mtkresearch.breezeapp.engine.core

import android.content.Context
import com.mtkresearch.breezeapp.engine.runner.core.RunnerManager
import com.mtkresearch.breezeapp.engine.service.ModelRegistryService

/**
 * The main dependency container for the BreezeApp Engine.
 *
 * This class is responsible for instantiating and wiring up all the core components
 * of the engine, including the logger, runner manager, and engine manager.
 * It is instantiated by the [BreezeAppEngineService] and its lifecycle is tied to the service.
 */
class BreezeAppEngineConfigurator(context: Context) {

    // --- Core Dependencies ---
    // The order of initialization matters.

    /** Provides logging capabilities throughout the engine. */
    val logger = Logger

    /** A default, in-memory storage service. A proper implementation (e.g., SharedPreferences) should be injected here. */
    private val storageService: StorageService = InMemoryStorageService(logger)

    /** Provides model registry and metadata management from JSON configuration. */
    val modelRegistryService: ModelRegistryService = ModelRegistryService(context)

    /** Manages the registration and lifecycle of all runners. */
    val runnerManager: RunnerManager = RunnerManager(context, logger, storageService, modelRegistryService)

    /** The central use case for processing AI requests. */
    val engineManager: AIEngineManager = AIEngineManager(context, runnerManager, logger)

    init {
        // Initialize the annotation-based runner system
        runnerManager.initializeBlocking()
    }
} 