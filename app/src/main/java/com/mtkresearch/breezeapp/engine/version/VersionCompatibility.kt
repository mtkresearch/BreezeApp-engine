package com.mtkresearch.breezeapp.engine.version

import android.os.Bundle
import com.mtkresearch.breezeapp.engine.BuildConfig
import com.mtkresearch.breezeapp.engine.IAIEngineService

/**
 * Version Compatibility Manager
 *
 * Handles version checking, compatibility validation, and feature detection
 * between client applications and the BreezeApp AI Engine service.
 *
 * Version Strategy (T115):
 * - AIDL API versions: Integer constants (1, 2, 3, ...)
 * - Semantic versions: MAJOR.MINOR.PATCH (e.g., 1.2.3)
 * - Compatibility: Backward-compatible within MAJOR version
 *
 * @author BreezeApp Team
 * @since 1.0.0
 */
object VersionCompatibility {

    /**
     * Current AIDL API version (T116)
     *
     * Increment rules:
     * - MAJOR API change (breaking): Increment to next integer (1 → 2)
     * - MINOR API change (backward-compatible): Keep same, document in changelog
     * - PATCH: No API changes, no version increment
     */
    const val CURRENT_API_VERSION = IAIEngineService.CURRENT_VERSION

    /**
     * Minimum supported client API version (T117)
     *
     * Clients with API version < MIN_SUPPORTED_CLIENT_VERSION will be rejected.
     * This allows deprecation of old API versions.
     */
    const val MIN_SUPPORTED_CLIENT_VERSION = 1

    /**
     * Semantic version components
     */
    data class SemanticVersion(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val buildCode: Int = 0
    ) : Comparable<SemanticVersion> {

        override fun compareTo(other: SemanticVersion): Int {
            return when {
                major != other.major -> major.compareTo(other.major)
                minor != other.minor -> minor.compareTo(other.minor)
                patch != other.patch -> patch.compareTo(other.patch)
                else -> buildCode.compareTo(other.buildCode)
            }
        }

        override fun toString(): String = "$major.$minor.$patch"

        fun toBundle(): Bundle = Bundle().apply {
            putInt("major", major)
            putInt("minor", minor)
            putInt("patch", patch)
            putInt("buildCode", buildCode)
        }

        companion object {
            /**
             * Parse semantic version from string (e.g., "1.2.3")
             */
            fun parse(versionString: String): SemanticVersion? {
                val parts = versionString.split(".")
                if (parts.size < 2 || parts.size > 3) return null

                val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
                val minor = parts.getOrNull(1)?.toIntOrNull() ?: return null
                val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0

                return SemanticVersion(major, minor, patch)
            }

            /**
             * Create from Bundle (from getVersionInfo())
             */
            fun fromBundle(bundle: Bundle): SemanticVersion {
                return SemanticVersion(
                    major = bundle.getInt("major", 1),
                    minor = bundle.getInt("minor", 0),
                    patch = bundle.getInt("patch", 0),
                    buildCode = bundle.getInt("buildCode", 0)
                )
            }
        }
    }

    /**
     * Current engine semantic version (T118)
     */
    val CURRENT_SEMANTIC_VERSION: SemanticVersion by lazy {
        SemanticVersion.parse(BuildConfig.VERSION_NAME)
            ?: SemanticVersion(1, 0, 0, BuildConfig.VERSION_CODE)
    }

    /**
     * Compatibility check result (T119)
     */
    sealed class CompatibilityResult {
        /**
         * Client is fully compatible with current engine version
         */
        data class Compatible(
            val engineVersion: Int,
            val clientVersion: Int,
            val warnings: List<String> = emptyList()
        ) : CompatibilityResult()

        /**
         * Client is incompatible (too old or too new)
         */
        data class Incompatible(
            val engineVersion: Int,
            val clientVersion: Int,
            val reason: IncompatibilityReason,
            val recommendation: String
        ) : CompatibilityResult()

        /**
         * Compatibility status unknown (error during check)
         */
        data class Unknown(
            val error: String
        ) : CompatibilityResult()
    }

    /**
     * Reasons for incompatibility
     */
    enum class IncompatibilityReason {
        CLIENT_TOO_OLD,      // Client API version < MIN_SUPPORTED_CLIENT_VERSION
        CLIENT_TOO_NEW,      // Client expects features not yet implemented
        MAJOR_VERSION_MISMATCH,  // Different MAJOR versions (breaking changes)
        ENGINE_DEPRECATED    // This engine version is deprecated
    }

    /**
     * Check compatibility between client and engine (T120)
     *
     * @param clientApiVersion The AIDL API version the client expects
     * @param clientSemanticVersion Optional semantic version of client app
     * @return CompatibilityResult indicating compatibility status
     */
    fun checkCompatibility(
        clientApiVersion: Int,
        clientSemanticVersion: SemanticVersion? = null
    ): CompatibilityResult {
        val engineVersion = CURRENT_API_VERSION
        val warnings = mutableListOf<String>()

        // Check 1: Client too old (unsupported API version)
        if (clientApiVersion < MIN_SUPPORTED_CLIENT_VERSION) {
            return CompatibilityResult.Incompatible(
                engineVersion = engineVersion,
                clientVersion = clientApiVersion,
                reason = IncompatibilityReason.CLIENT_TOO_OLD,
                recommendation = "Please update your client application to support API version $MIN_SUPPORTED_CLIENT_VERSION or higher. " +
                        "Current client API version: $clientApiVersion"
            )
        }

        // Check 2: Client too new (expects future API)
        if (clientApiVersion > engineVersion) {
            return CompatibilityResult.Incompatible(
                engineVersion = engineVersion,
                clientVersion = clientApiVersion,
                reason = IncompatibilityReason.CLIENT_TOO_NEW,
                recommendation = "Please update BreezeApp AI Engine to version supporting API $clientApiVersion. " +
                        "Current engine API version: $engineVersion"
            )
        }

        // Check 3: Deprecated features warning
        if (clientApiVersion < engineVersion) {
            warnings.add(
                "Client is using API version $clientApiVersion, but engine supports $engineVersion. " +
                "Consider updating client to use latest features."
            )
        }

        // Check 4: Semantic version compatibility (if provided)
        clientSemanticVersion?.let { clientSemVer ->
            val engineSemVer = CURRENT_SEMANTIC_VERSION

            // Major version mismatch = breaking changes
            if (clientSemVer.major != engineSemVer.major) {
                return CompatibilityResult.Incompatible(
                    engineVersion = engineVersion,
                    clientVersion = clientApiVersion,
                    reason = IncompatibilityReason.MAJOR_VERSION_MISMATCH,
                    recommendation = "Client app major version (${clientSemVer.major}) does not match " +
                            "engine major version (${engineSemVer.major}). " +
                            "Please update both apps to compatible versions."
                )
            }

            // Minor version mismatch = new features available
            if (clientSemVer.minor < engineSemVer.minor) {
                warnings.add(
                    "New features available in engine version $engineSemVer. " +
                    "Client version $clientSemVer may not have access to all features."
                )
            }
        }

        // Compatible!
        return CompatibilityResult.Compatible(
            engineVersion = engineVersion,
            clientVersion = clientApiVersion,
            warnings = warnings
        )
    }

    /**
     * Get feature availability based on client API version (T121)
     *
     * @param clientApiVersion Client's AIDL API version
     * @return Bundle of feature flags (feature_name -> available: Boolean)
     */
    fun getFeatureAvailability(clientApiVersion: Int): Bundle {
        return Bundle().apply {
            // API v1 features (always available if client >= 1)
            putBoolean("llm_inference", clientApiVersion >= 1)
            putBoolean("asr_recognition", clientApiVersion >= 1)
            putBoolean("tts_synthesis", clientApiVersion >= 1)
            putBoolean("sync_inference", clientApiVersion >= 1)

            // API v1 features (introduced in v1.0.0)
            putBoolean("async_inference", clientApiVersion >= 1)
            putBoolean("streaming_inference", clientApiVersion >= 1)
            putBoolean("model_management", clientApiVersion >= 1)

            // Future API v2 features (not yet implemented)
            putBoolean("vlm_inference", clientApiVersion >= 2)  // Will be in API v2
            putBoolean("multi_turn_context", clientApiVersion >= 2)
            putBoolean("custom_model_loading", clientApiVersion >= 2)

            // Future API v3 features (planned)
            putBoolean("fine_tuning", clientApiVersion >= 3)
            putBoolean("model_quantization", clientApiVersion >= 3)
            putBoolean("distributed_inference", clientApiVersion >= 3)

            // Hardware acceleration (available if device supports)
            putBoolean("npu_acceleration", isNpuAvailable())
            putBoolean("gpu_acceleration", isGpuAvailable())
        }
    }

    /**
     * Check if NPU hardware acceleration is available
     */
    private fun isNpuAvailable(): Boolean {
        // TODO: Implement actual NPU detection
        // For now, return false (CPU mode only)
        return false
    }

    /**
     * Check if GPU acceleration is available
     */
    private fun isGpuAvailable(): Boolean {
        // TODO: Implement GPU detection via NNAPI
        return false
    }

    /**
     * Get recommended actions for incompatible clients
     */
    fun getRecommendedAction(result: CompatibilityResult.Incompatible): RecommendedAction {
        return when (result.reason) {
            IncompatibilityReason.CLIENT_TOO_OLD -> {
                RecommendedAction(
                    action = Action.UPDATE_CLIENT,
                    priority = Priority.HIGH,
                    message = result.recommendation,
                    playStoreUrl = "market://details?id=com.mtkresearch.breezeapp"
                )
            }
            IncompatibilityReason.CLIENT_TOO_NEW -> {
                RecommendedAction(
                    action = Action.UPDATE_ENGINE,
                    priority = Priority.CRITICAL,
                    message = result.recommendation,
                    playStoreUrl = "market://details?id=com.mtkresearch.breezeapp.engine"
                )
            }
            IncompatibilityReason.MAJOR_VERSION_MISMATCH -> {
                RecommendedAction(
                    action = Action.UPDATE_BOTH,
                    priority = Priority.CRITICAL,
                    message = result.recommendation,
                    playStoreUrl = null  // User needs to update both apps
                )
            }
            IncompatibilityReason.ENGINE_DEPRECATED -> {
                RecommendedAction(
                    action = Action.UPDATE_ENGINE,
                    priority = Priority.HIGH,
                    message = result.recommendation,
                    playStoreUrl = "market://details?id=com.mtkresearch.breezeapp.engine"
                )
            }
        }
    }

    /**
     * Recommended action for resolving incompatibility
     */
    data class RecommendedAction(
        val action: Action,
        val priority: Priority,
        val message: String,
        val playStoreUrl: String?
    )

    enum class Action {
        UPDATE_CLIENT,   // Client app needs update
        UPDATE_ENGINE,   // Engine needs update
        UPDATE_BOTH,     // Both need updates
        NO_ACTION        // Compatible, no action needed
    }

    enum class Priority {
        LOW,       // Feature unavailable, but app works
        MEDIUM,    // Some features degraded
        HIGH,      // Significant incompatibility
        CRITICAL   // App cannot function
    }

    /**
     * Create version info bundle for client communication
     */
    fun createVersionInfoBundle(): Bundle {
        return Bundle().apply {
            putInt("apiVersion", CURRENT_API_VERSION)
            putInt("minSupportedClientVersion", MIN_SUPPORTED_CLIENT_VERSION)
            putString("semanticVersion", CURRENT_SEMANTIC_VERSION.toString())
            putInt("major", CURRENT_SEMANTIC_VERSION.major)
            putInt("minor", CURRENT_SEMANTIC_VERSION.minor)
            putInt("patch", CURRENT_SEMANTIC_VERSION.patch)
            putInt("buildCode", BuildConfig.VERSION_CODE)
            putString("buildType", BuildConfig.BUILD_TYPE)
            putLong("buildTimestamp", getBuildTimestamp())
        }
    }

    /**
     * Get build timestamp (for version comparison)
     */
    private fun getBuildTimestamp(): Long {
        // TODO: Embed actual build timestamp during build process
        return System.currentTimeMillis()
    }

    /**
     * Log compatibility check result (for diagnostics)
     */
    fun logCompatibilityResult(result: CompatibilityResult, clientPackageName: String) {
        when (result) {
            is CompatibilityResult.Compatible -> {
                android.util.Log.i(
                    "VersionCompatibility",
                    "✅ Compatible: $clientPackageName (API ${result.clientVersion}) " +
                            "with engine (API ${result.engineVersion}). " +
                            "Warnings: ${result.warnings.size}"
                )
                result.warnings.forEach { warning ->
                    android.util.Log.w("VersionCompatibility", "  ⚠️ $warning")
                }
            }
            is CompatibilityResult.Incompatible -> {
                android.util.Log.e(
                    "VersionCompatibility",
                    "❌ Incompatible: $clientPackageName (API ${result.clientVersion}) " +
                            "with engine (API ${result.engineVersion}). " +
                            "Reason: ${result.reason}. " +
                            "Recommendation: ${result.recommendation}"
                )
            }
            is CompatibilityResult.Unknown -> {
                android.util.Log.w(
                    "VersionCompatibility",
                    "❓ Unknown compatibility for $clientPackageName: ${result.error}"
                )
            }
        }
    }
}
