package com.mtkresearch.breezeapp.engine.runner.core

import android.content.Context
import com.mtkresearch.breezeapp.engine.annotation.AIRunner
import com.mtkresearch.breezeapp.engine.annotation.HardwareRequirement
import com.mtkresearch.breezeapp.engine.core.Logger
import java.lang.reflect.Constructor

/**
 * RunnerFactory - Simple Runner Instance Creation
 * 
 * Creates runner instances from annotated classes using reflection with dependency injection.
 * This replaces the complex vendor-specific factory logic with a simple, unified approach
 * that relies on @AIRunner annotations for all configuration.
 * 
 * ## Creation Strategy
 * The factory attempts constructor injection in this order:
 * 1. **Constructor(Context)** - For runners needing Android context
 * 2. **Constructor()** - For simple runners with no dependencies
 * 
 * ## Dependency Injection
 * - **Context**: Automatically injected for Android-specific operations
 * - **Future**: Could be extended for other dependencies (Logger, etc.)
 * 
 * ## Error Handling
 * - Missing constructors are logged and return null
 * - Constructor exceptions are caught and logged
 * - Hardware requirement validation failures are handled gracefully
 * 
 * @param context Android context for dependency injection
 * @param logger Logger instance for debugging and monitoring
 * 
 * @since Engine API v2.0
 */
class RunnerFactory(
    private val context: Context,
    private val logger: Logger
) {
    companion object {
        private const val TAG = "RunnerFactory"
    }
    
    /**
     * Create a runner instance from an annotated class.
     * 
     * @param runnerClass The annotated runner class to instantiate
     * @return New runner instance, or null if creation failed
     */
    fun createRunner(runnerClass: Class<out BaseRunner>): BaseRunner? {
        try {
            logger.d(TAG, "Creating runner instance: ${runnerClass.simpleName}")
            
            // Validate annotation before creation
            val annotation = runnerClass.getAnnotation(AIRunner::class.java)
            if (annotation == null) {
                logger.w(TAG, "Class ${runnerClass.simpleName} missing @AIRunner annotation")
                return null
            }
            
            // Create instance using appropriate constructor
            val instance = instantiateRunner(runnerClass)
            if (instance == null) {
                logger.e(TAG, "Failed to instantiate ${runnerClass.simpleName}")
                return null
            }
            
            logger.d(TAG, "Successfully created runner: ${runnerClass.simpleName}")
            return instance
            
        } catch (e: Exception) {
            logger.e(TAG, "Error creating runner ${runnerClass.simpleName}", e)
            return null
        }
    }
    
    /**
     * Validate that hardware requirements are satisfied.
     * 
     * @param annotation The @AIRunner annotation containing requirements
     * @return true if all hardware requirements are met
     */
    fun validateHardwareRequirements(annotation: AIRunner): Boolean {
        if (annotation.hardwareRequirements.isEmpty()) {
            return true
        }
        
        val unmetRequirements = mutableListOf<HardwareRequirement>()
        
        for (requirement in annotation.hardwareRequirements) {
            try {
                if (!requirement.isSatisfied(context)) {
                    unmetRequirements.add(requirement)
                }
            } catch (e: Exception) {
                logger.w(TAG, "Error validating hardware requirement $requirement: ${e.message}")
                unmetRequirements.add(requirement)
            }
        }
        
        if (unmetRequirements.isNotEmpty()) {
            logger.d(TAG, "Unmet hardware requirements: ${unmetRequirements.joinToString()}")
            return false
        }
        
        return true
    }
    
    /**
     * Check if a runner class is supported on the current device.
     * This combines annotation validation and hardware requirement checking.
     * 
     * @param runnerClass The runner class to check
     * @return true if the runner is supported on this device
     */
    fun isRunnerSupported(runnerClass: Class<out BaseRunner>): Boolean {
        val annotation = runnerClass.getAnnotation(AIRunner::class.java)
            ?: return false
        
        if (!annotation.enabled) {
            logger.d(TAG, "Runner ${runnerClass.simpleName} is disabled via annotation")
            return false
        }
        
        return validateHardwareRequirements(annotation)
    }
    
    /**
     * Get creation statistics for debugging.
     * 
     * @return Map containing factory performance metrics
     */
    fun getCreationStats(): Map<String, Any> {
        return mapOf(
            "factoryType" to "Reflection-based with dependency injection",
            "supportedConstructors" to listOf("(Context)", "()"),
            "dependencyInjection" to "Context auto-injection"
        )
    }
    
    // Private implementation methods
    
    /**
     * Instantiate a runner using the most appropriate constructor.
     * 
     * @param runnerClass The class to instantiate
     * @return New instance, or null if instantiation failed
     */
    private fun instantiateRunner(runnerClass: Class<out BaseRunner>): BaseRunner? {
        // Try Context constructor first (preferred for Android runners)
        val contextConstructor = findContextConstructor(runnerClass)
        if (contextConstructor != null) {
            return tryCreateWithConstructor(contextConstructor, context)
        }
        
        // Fallback to default constructor
        val defaultConstructor = findDefaultConstructor(runnerClass)
        if (defaultConstructor != null) {
            return tryCreateWithConstructor(defaultConstructor)
        }
        
        logger.w(TAG, "No suitable constructor found for ${runnerClass.simpleName}")
        return null
    }
    
    /**
     * Find a constructor that takes a Context parameter.
     * 
     * @param runnerClass The class to search
     * @return Constructor(Context) if found, null otherwise
     */
    private fun findContextConstructor(runnerClass: Class<out BaseRunner>): Constructor<out BaseRunner>? {
        return try {
            runnerClass.getConstructor(Context::class.java)
        } catch (e: NoSuchMethodException) {
            null
        }
    }
    
    /**
     * Find the default no-argument constructor.
     * 
     * @param runnerClass The class to search
     * @return Constructor() if found, null otherwise
     */
    private fun findDefaultConstructor(runnerClass: Class<out BaseRunner>): Constructor<out BaseRunner>? {
        return try {
            runnerClass.getConstructor()
        } catch (e: NoSuchMethodException) {
            null
        }
    }
    
    /**
     * Attempt to create an instance using a specific constructor with arguments.
     * 
     * @param constructor The constructor to use
     * @param args Arguments to pass to the constructor
     * @return New instance, or null if creation failed
     */
    private fun tryCreateWithConstructor(
        constructor: Constructor<out BaseRunner>,
        vararg args: Any
    ): BaseRunner? {
        return try {
            constructor.newInstance(*args)
        } catch (e: Exception) {
            logger.w(TAG, "Failed to create instance using constructor ${constructor}: ${e.message}")
            null
        }
    }
    
    /**
     * Inject dependencies into a runner instance (for future extensibility).
     * Currently a no-op, but could be extended for complex dependency injection.
     * 
     * @param runner The runner instance to inject dependencies into
     * @return The runner with dependencies injected
     */
    private fun injectDependencies(runner: BaseRunner): BaseRunner {
        // Future: Could inject additional dependencies here
        // - Logger
        // - Configuration
        // - Hardware managers
        // - etc.
        return runner
    }
}