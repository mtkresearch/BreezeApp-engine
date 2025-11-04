package com.mtkresearch.breezeapp.client.version

import android.os.Bundle
import android.os.RemoteException
import com.mtkresearch.breezeapp.engine.IAIEngineService

/**
 * Client-Side Version Compatibility Checker
 *
 * This is example code for client applications to check compatibility
 * with the BreezeApp AI Engine service.
 *
 * Usage in client apps:
 * 1. Copy this file to your project
 * 2. After binding to engine service, call checkCompatibility()
 * 3. Handle the CompatibilityStatus result
 *
 * Version Negotiation Protocol (T122-T128):
 * - Client declares its expected API version
 * - Engine checks compatibility
 * - Client adapts behavior based on available features
 *
 * @author BreezeApp Team
 * @since 1.0.0
 */
class ClientVersionChecker(
    private val engineService: IAIEngineService
) {

    companion object {
        /**
         * Client's expected API version (T122)
         *
         * Update this constant when your client app adopts new API features.
         */
        const val CLIENT_API_VERSION = 1

        /**
         * Minimum engine API version required by this client (T123)
         *
         * If engine API version < MIN_REQUIRED_ENGINE_VERSION, client cannot function.
         */
        const val MIN_REQUIRED_ENGINE_VERSION = 1

        /**
         * Recommended engine API version for full features (T124)
         *
         * Client works with lower versions but some features unavailable.
         */
        const val RECOMMENDED_ENGINE_VERSION = 1
    }

    /**
     * Compatibility status result
     */
    sealed class CompatibilityStatus {
        /**
         * Fully compatible - all features available
         */
        object FullyCompatible : CompatibilityStatus()

        /**
         * Compatible with warnings - some features unavailable
         */
        data class CompatibleWithLimitations(
            val missingFeatures: List<String>,
            val message: String
        ) : CompatibilityStatus()

        /**
         * Incompatible - cannot proceed
         */
        data class Incompatible(
            val reason: String,
            val recommendation: String,
            val updateUrl: String?
        ) : CompatibilityStatus()

        /**
         * Check failed - unknown status
         */
        data class CheckFailed(
            val error: String
        ) : CompatibilityStatus()
    }

    /**
     * Check compatibility with engine (T125)
     *
     * This is the main method clients should call after binding to the engine.
     *
     * @return CompatibilityStatus indicating whether client can use the engine
     */
    fun checkCompatibility(): CompatibilityStatus {
        return try {
            // Step 1: Get engine API version
            val engineApiVersion = engineService.version

            // Step 2: Get detailed version info
            val versionInfo = engineService.versionInfo

            // Step 3: Check minimum requirement
            if (engineApiVersion < MIN_REQUIRED_ENGINE_VERSION) {
                return CompatibilityStatus.Incompatible(
                    reason = "Engine API version $engineApiVersion is too old. " +
                            "Minimum required: $MIN_REQUIRED_ENGINE_VERSION",
                    recommendation = "Please update BreezeApp AI Engine from Google Play Store.",
                    updateUrl = "market://details?id=com.mtkresearch.breezeapp.engine"
                )
            }

            // Step 4: Check for newer engine version
            if (engineApiVersion > CLIENT_API_VERSION) {
                // Engine is newer - client may not use all features, but that's OK
                // Log warning for developer
                android.util.Log.w(
                    "ClientVersionChecker",
                    "Engine API version ($engineApiVersion) is newer than client expects ($CLIENT_API_VERSION). " +
                    "Consider updating client app to use new features."
                )
            }

            // Step 5: Check feature availability
            val features = engineService.capabilities
            val missingFeatures = checkMissingFeatures(features)

            if (missingFeatures.isNotEmpty()) {
                return CompatibilityStatus.CompatibleWithLimitations(
                    missingFeatures = missingFeatures,
                    message = "Engine version $engineApiVersion lacks some features. " +
                            "Missing: ${missingFeatures.joinToString(", ")}. " +
                            "Consider updating to engine API version $RECOMMENDED_ENGINE_VERSION."
                )
            }

            // Fully compatible!
            CompatibilityStatus.FullyCompatible

        } catch (e: RemoteException) {
            CompatibilityStatus.CheckFailed("Failed to communicate with engine: ${e.message}")
        } catch (e: Exception) {
            CompatibilityStatus.CheckFailed("Unexpected error during version check: ${e.message}")
        }
    }

    /**
     * Check which required features are missing (T126)
     *
     * @param capabilities Bundle from engineService.getCapabilities()
     * @return List of missing feature names
     */
    private fun checkMissingFeatures(capabilities: Bundle): List<String> {
        val missing = mutableListOf<String>()

        // Check required features for this client
        val requiredFeatures = mapOf(
            "llm" to "LLM inference",
            "asr" to "Speech recognition",
            "tts" to "Text-to-speech",
            "streaming" to "Streaming inference"
        )

        requiredFeatures.forEach { (key, name) ->
            if (!capabilities.getBoolean(key, false)) {
                missing.add(name)
            }
        }

        return missing
    }

    /**
     * Get available features based on engine capabilities (T127)
     *
     * @return Set of feature names that are available
     */
    fun getAvailableFeatures(): Set<String> {
        return try {
            val capabilities = engineService.capabilities
            val available = mutableSetOf<String>()

            // Check all possible features
            val allFeatures = listOf("llm", "vlm", "asr", "tts", "streaming", "npu")
            allFeatures.forEach { feature ->
                if (capabilities.getBoolean(feature, false)) {
                    available.add(feature)
                }
            }

            available
        } catch (e: RemoteException) {
            emptySet()
        }
    }

    /**
     * Adaptive feature usage - graceful degradation (T128)
     *
     * Example: Use streaming if available, fall back to sync inference
     *
     * @param preferredMethod The method you want to use
     * @param fallbackMethod The method to use if preferred is unavailable
     * @return The method to actually use
     */
    fun <T> useFeatureOrFallback(
        preferredFeature: String,
        preferredMethod: () -> T,
        fallbackMethod: () -> T
    ): T {
        val available = getAvailableFeatures()
        return if (preferredFeature in available) {
            android.util.Log.d("ClientVersionChecker", "Using preferred feature: $preferredFeature")
            preferredMethod()
        } else {
            android.util.Log.w(
                "ClientVersionChecker",
                "Feature $preferredFeature unavailable, using fallback"
            )
            fallbackMethod()
        }
    }

    /**
     * Example: Adaptive LLM inference with graceful degradation
     */
    fun inferTextAdaptive(
        prompt: String,
        params: Bundle,
        onToken: ((String) -> Unit)? = null,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        useFeatureOrFallback(
            preferredFeature = "streaming",
            preferredMethod = {
                // Use streaming inference (newer feature)
                val callback = object : com.mtkresearch.breezeapp.engine.IStreamCallback.Stub() {
                    override fun onStart() { /* Started */ }
                    override fun onToken(token: String) {
                        onToken?.invoke(token)
                    }
                    override fun onComplete(fullText: String) {
                        onComplete(fullText)
                    }
                    override fun onError(errorCode: Int, message: String) {
                        onError(message)
                    }
                }
                engineService.inferTextStreaming(prompt, params, callback)
            },
            fallbackMethod = {
                // Fall back to synchronous inference
                try {
                    val result = engineService.inferText(prompt, params)
                    onComplete(result)
                } catch (e: RemoteException) {
                    onError("Inference failed: ${e.message}")
                }
            }
        )
    }

    /**
     * Display user-friendly compatibility message
     */
    fun getCompatibilityMessage(status: CompatibilityStatus): String {
        return when (status) {
            is CompatibilityStatus.FullyCompatible -> {
                "✅ BreezeApp AI Engine is up to date and fully compatible."
            }
            is CompatibilityStatus.CompatibleWithLimitations -> {
                "⚠️ ${status.message}\n\n" +
                "Available features will work, but you may want to update the engine for full functionality."
            }
            is CompatibilityStatus.Incompatible -> {
                "❌ ${status.reason}\n\n${status.recommendation}"
            }
            is CompatibilityStatus.CheckFailed -> {
                "❓ Could not verify compatibility: ${status.error}"
            }
        }
    }

    /**
     * Determine if user action is required
     */
    fun requiresUserAction(status: CompatibilityStatus): Boolean {
        return status is CompatibilityStatus.Incompatible
    }

    /**
     * Get Play Store URL for updating engine
     */
    fun getUpdateUrl(status: CompatibilityStatus): String? {
        return when (status) {
            is CompatibilityStatus.Incompatible -> status.updateUrl
            is CompatibilityStatus.CompatibleWithLimitations ->
                "market://details?id=com.mtkresearch.breezeapp.engine"
            else -> null
        }
    }
}

// ============================================================================
// Example Usage in Client Application
// ============================================================================

/**
 * Example: Integrate version checking in your Activity
 */
/*
class MainActivity : AppCompatActivity() {
    private lateinit var engineService: IAIEngineService
    private lateinit var versionChecker: ClientVersionChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // After binding to engine service...
        lifecycleScope.launch {
            engineClient.connectionState.collect { state ->
                if (state is ConnectionState.Connected) {
                    engineService = state.service
                    checkVersionCompatibility()
                }
            }
        }
    }

    private fun checkVersionCompatibility() {
        versionChecker = ClientVersionChecker(engineService)

        when (val status = versionChecker.checkCompatibility()) {
            is CompatibilityStatus.FullyCompatible -> {
                // All good, proceed
                enableAllFeatures()
            }

            is CompatibilityStatus.CompatibleWithLimitations -> {
                // Show warning, disable unavailable features
                showWarningSnackbar(versionChecker.getCompatibilityMessage(status))
                disableFeatures(status.missingFeatures)
            }

            is CompatibilityStatus.Incompatible -> {
                // Block usage, prompt user to update
                showUpdateDialog(
                    message = versionChecker.getCompatibilityMessage(status),
                    updateUrl = versionChecker.getUpdateUrl(status)
                )
            }

            is CompatibilityStatus.CheckFailed -> {
                // Log error, maybe proceed with caution
                Log.e("MainActivity", "Version check failed: ${status.error}")
                showErrorDialog("Could not verify engine compatibility")
            }
        }
    }

    private fun showUpdateDialog(message: String, updateUrl: String?) {
        AlertDialog.Builder(this)
            .setTitle("Update Required")
            .setMessage(message)
            .setPositiveButton("Update") { _, _ ->
                updateUrl?.let { url ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                finish()  // Close app if engine incompatible
            }
            .setCancelable(false)
            .show()
    }

    private fun disableFeatures(missingFeatures: List<String>) {
        missingFeatures.forEach { feature ->
            when (feature) {
                "Streaming inference" -> {
                    // Disable streaming UI elements
                    streamingToggle.isEnabled = false
                    streamingToggle.alpha = 0.5f
                }
                "Text-to-speech" -> {
                    // Hide TTS button
                    ttsButton.visibility = View.GONE
                }
                // ... handle other features
            }
        }
    }
}
*/

// ============================================================================
// Example: Version-Aware Feature Implementation
// ============================================================================

/**
 * Example: Repository that adapts to engine version
 */
/*
class AIEngineRepository(
    private val engineService: IAIEngineService
) {
    private val versionChecker = ClientVersionChecker(engineService)

    suspend fun generateText(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        // Check compatibility first
        when (val status = versionChecker.checkCompatibility()) {
            is CompatibilityStatus.Incompatible -> {
                return@withContext Result.failure(
                    IllegalStateException("Engine incompatible: ${status.reason}")
                )
            }
            else -> { /* Proceed */ }
        }

        // Use adaptive inference
        try {
            val result = suspendCoroutine<String> { continuation ->
                versionChecker.inferTextAdaptive(
                    prompt = prompt,
                    params = Bundle(),
                    onComplete = { text -> continuation.resume(text) },
                    onError = { error -> continuation.resumeWithException(Exception(error)) }
                )
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
*/
