package com.mtkresearch.breezeapp.engine.core

import android.content.Context
import com.mtkresearch.breezeapp.engine.runner.core.RunnerConfigurationManager
import com.mtkresearch.breezeapp.engine.runner.core.RunnerRegistry

/**
 * The main dependency container for the BreezeApp Engine.
 *
 * This class is responsible for instantiating and wiring up all the core components
 * of the engine, including the logger, registry, engine manager, and configuration manager.
 * It is instantiated by the [BreezeAppEngineService] and its lifecycle is tied to the service.
 */
class BreezeAppEngineConfigurator(context: Context) {

    // --- Core Dependencies ---
    // The order of initialization matters.

    /** Provides logging capabilities throughout the engine. */
    val logger = Logger

    /** Manages the registration and lifecycle of all runners. */
    val runnerRegistry: RunnerRegistry = RunnerRegistry(logger)

    /** The central use case for processing AI requests. */
    val engineManager: AIEngineManager = AIEngineManager(context, runnerRegistry, logger)
    
    /** Manages loading runner configurations from external files. */
    private val configurationManager: RunnerConfigurationManager = RunnerConfigurationManager(context.applicationContext, logger)

    init {
        // This is the final step: load configurations and register all runners.
        configurationManager.loadAndRegisterRunners(runnerRegistry)
    }
} 