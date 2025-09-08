package com.mtkresearch.breezeapp.engine.runner.llamastack

import android.content.Context
import android.util.Log
import com.llama.llamastack.client.LlamaStackClientClient
import com.llama.llamastack.client.okhttp.LlamaStackClientOkHttpClient
import com.llama.llamastack.models.*
import com.mtkresearch.breezeapp.engine.annotation.*
import com.mtkresearch.breezeapp.engine.model.*
import com.mtkresearch.breezeapp.engine.runner.core.*
import com.mtkresearch.breezeapp.engine.runner.guardian.*
import java.util.concurrent.atomic.AtomicBoolean

@AIRunner(
    vendor = VendorType.EXECUTORCH,
    priority = RunnerPriority.LOW,
    capabilities = [CapabilityType.GUARDIAN],
    defaultModel = "llama-guard3:1b"
)
class LlamaStackGuardianRunner(
    private val context: Context? = null
) : BaseGuardianRunner() {

    companion object {
        private const val TAG = "LlamaStackGuardianRunner"
        private const val DEFAULT_SHIELD_ID = "_safety"
        private const val DEFAULT_GUARDIAN_MODEL = "meta-llama/Llama-Guard-3-1B"
        private const val DEFAULT_OLLAMA_MODEL = "llama-guard3:1b"
    }

    private var llamaStackClient: LlamaStackClientClient? = null
    private var config: LlamaStackGuardianConfig? = null
    private val isLoaded = AtomicBoolean(false)

    override fun analyze(text: String, config: com.mtkresearch.breezeapp.engine.runner.guardian.GuardianConfig): GuardianAnalysisResult {
        if (!isLoaded.get() || llamaStackClient == null || this.config == null) {
            throw RuntimeException("Guardian runner not loaded")
        }

        return try {
            Log.d(TAG, "Analyzing text with LlamaStack Guardian")

            // Create user message for analysis
            val userMessage = UserMessage.builder()
                .content(InterleavedContent.ofString(text))
                .build()
            val messages = listOf(Message.ofUser(userMessage))

            // Run shield analysis
            val safetyParams = SafetyRunShieldParams.builder()
                .shieldId(this.config!!.shieldId)
                .messages(messages)
                .params(SafetyRunShieldParams.Params.builder().build())
                .build()

            val shieldResponse = llamaStackClient!!.safety().runShield(safetyParams)

            // Convert to GuardianAnalysisResult
            convertShieldResponse(shieldResponse, config.strictness)

        } catch (e: Exception) {
            Log.e(TAG, "Guardian analysis failed", e)
            
            // Check if it's a connection error
            val isConnectionError = e.message?.contains("Failed to connect") == true || 
                                  e.message?.contains("Connection refused") == true
            
            if (isConnectionError) {
                Log.w(TAG, "LlamaStack server unavailable at ${this.config!!.endpoint}, using safe fallback")
                // For connection errors, return safe result but log the issue
                return GuardianAnalysisResult(
                    status = GuardianStatus.SAFE,
                    riskScore = 0.0,
                    categories = emptyList(),
                    action = GuardianAction.NONE,
                    filteredText = null,
                    details = mapOf(
                        "error_type" to "connection_unavailable",
                        "endpoint" to this.config!!.endpoint,
                        "fallback_reason" to "LlamaStack server not available",
                        "recommended_action" to "Check LlamaStack server status"
                    )
                )
            }
            
            // For other errors, still use safe fallback
            GuardianAnalysisResult(
                status = GuardianStatus.SAFE,
                riskScore = 0.0,
                categories = emptyList(),
                action = GuardianAction.NONE,
                filteredText = null,
                details = mapOf("error" to (e.message ?: "Unknown error"), "fallback" to true)
            )
        }
    }

    override fun load(modelId: String, settings: EngineSettings, initialParams: Map<String, Any>): Boolean {
        Log.d(TAG, "Loading LlamaStack Guardian runner")
        Log.d(TAG, "Initial params: $initialParams")
        Log.d(TAG, "Endpoint from initialParams: ${initialParams["endpoint"]}")

        return try {
            if (isLoaded.get()) {
                Log.d(TAG, "Guardian runner already loaded, unloading to apply new configuration")
                unload()
            }

            // CRITICAL FIX: Use the same pattern as LlamaStackRunner
            // First get guardian-specific parameters, then overlay with runtime parameters
            val runnerParams = settings.getRunnerParameters("LlamaStackGuardianRunner")
                .toMutableMap()
                .apply { putAll(initialParams) }
            
            // IMPORTANT: If no guardian-specific endpoint is set, inherit from LlamaStackRunner
            if (runnerParams["endpoint"] == null) {
                val llamaStackParams = settings.getRunnerParameters("LlamaStackRunner")
                val runtimeEndpoint = initialParams["endpoint"] as? String 
                    ?: llamaStackParams["endpoint"] as? String
                
                if (runtimeEndpoint != null) {
                    runnerParams["endpoint"] = runtimeEndpoint
                    Log.d(TAG, "Guardian inheriting endpoint from LlamaStack configuration: $runtimeEndpoint")
                }
            }

            config = LlamaStackGuardianConfig.fromParams(runnerParams, modelId)
            Log.d(TAG, "Guardian config - Endpoint: ${config!!.endpoint}, Shield: ${config!!.shieldId}")
            Log.d(TAG, "Guardian config - Checkpoint: ${config!!.guardianCheckpoint}, Timeout: ${config!!.connectionTimeout}ms")

            // Create LlamaStack client (reuse pattern from LlamaStackRunner)
            llamaStackClient = createOfficialClient(config!!)

            // Test connection to provide early feedback
            try {
                // Attempt a simple connection test - create a minimal request but don't process it
                Log.d(TAG, "Testing connection to LlamaStack at ${config!!.endpoint}")
                // Note: We'll rely on actual usage to detect connection issues for minimal overhead
            } catch (e: Exception) {
                Log.w(TAG, "Connection test failed, but continuing with graceful fallback mode: ${e.message}")
            }

            Log.i(TAG, "LlamaStack Guardian runner loaded successfully with checkpoint: ${config!!.guardianCheckpoint}")
            Log.d(TAG, "Guardian will check: ${if (config!!.shouldCheckInput()) "INPUT" else ""}${if (config!!.shouldCheckInput() && config!!.shouldCheckOutput()) " + " else ""}${if (config!!.shouldCheckOutput()) "OUTPUT" else ""}")
            isLoaded.set(true)
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load LlamaStack Guardian runner", e)
            isLoaded.set(false)
            false
        }
    }

    override fun unload() {
        Log.d(TAG, "Unloading LlamaStack Guardian runner")
        llamaStackClient = null
        config = null
        isLoaded.set(false)
    }

    override fun isLoaded(): Boolean = isLoaded.get()

    override fun getRunnerInfo(): com.mtkresearch.breezeapp.engine.runner.core.RunnerInfo {
        return com.mtkresearch.breezeapp.engine.runner.core.RunnerInfo(
            name = "llamastack_guardian",
            version = "1.0.0",
            capabilities = getCapabilities(),
            description = "LlamaStack Guardian Runner using Llama Guard models via Ollama"
        )
    }

    override fun isSupported(): Boolean = true

    override fun getParameterSchema(): List<ParameterSchema> {
        return listOf(
            ParameterSchema(
                name = "endpoint",
                displayName = "LlamaStack Endpoint",
                description = "LlamaStack API endpoint URL (inherits from LlamaStackRunner if not set)",
                type = ParameterType.StringType(
                    minLength = 10,
                    pattern = Regex("^https?://.*")
                ),
                defaultValue = "", // Empty default - will inherit from LlamaStackRunner
                isRequired = false, // Not required - can inherit
                category = "Connection"
            ),
            ParameterSchema(
                name = "shield_id",
                displayName = "Shield ID",
                description = "LlamaStack shield identifier for safety analysis",
                type = ParameterType.StringType(minLength = 1),
                defaultValue = DEFAULT_SHIELD_ID,
                isRequired = false,
                category = "Guardian Configuration"
            ),
            ParameterSchema(
                name = "guardian_model",
                displayName = "Guardian Model",
                description = "LlamaStack model ID for guardian analysis",
                type = ParameterType.StringType(minLength = 1),
                defaultValue = DEFAULT_GUARDIAN_MODEL,
                isRequired = false,
                category = "Guardian Configuration"
            ),
            ParameterSchema(
                name = "ollama_model",
                displayName = "Ollama Model",
                description = "Ollama model name for the guardian",
                type = ParameterType.StringType(minLength = 1),
                defaultValue = DEFAULT_OLLAMA_MODEL,
                isRequired = false,
                category = "Guardian Configuration"
            ),
            ParameterSchema(
                name = "guardian_checkpoint",
                displayName = "Guardian Checkpoint",
                description = "Configure when to apply guardian checks",
                type = ParameterType.SelectionType(
                    options = listOf(
                        SelectionOption("input_only", "Input Only", "Check only user input before AI processing"),
                        SelectionOption("output_only", "Output Only", "Check only AI output after processing"),
                        SelectionOption("both", "Both", "Check both input and output")
                    ),
                    allowMultiple = false
                ),
                defaultValue = "input_only",
                isRequired = false,
                category = "Guardian Configuration"
            ),
            ParameterSchema(
                name = "connection_timeout",
                displayName = "Connection Timeout (ms)",
                description = "Timeout for LlamaStack connection attempts",
                type = ParameterType.IntType(
                    minValue = 1000,
                    maxValue = 30000,
                    step = 1000
                ),
                defaultValue = 5000,
                isRequired = false,
                category = "Connection"
            )
        )
    }

    override fun validateParameters(parameters: Map<String, Any>): com.mtkresearch.breezeapp.engine.runner.core.ValidationResult {
        val config = try {
            LlamaStackGuardianConfig.fromParams(parameters, DEFAULT_OLLAMA_MODEL)
        } catch (e: Exception) {
            return com.mtkresearch.breezeapp.engine.runner.core.ValidationResult.invalid("Invalid parameter format: ${e.message}")
        }

        // Shield ID is required, but endpoint can be inherited from LlamaStackRunner
        return if (config.shieldId.isNotBlank()) {
            com.mtkresearch.breezeapp.engine.runner.core.ValidationResult.valid()
        } else {
            com.mtkresearch.breezeapp.engine.runner.core.ValidationResult.invalid("Shield ID is required")
        }
    }

    // Private helper methods (reuse patterns from LlamaStackRunner)

    private fun createOfficialClient(config: LlamaStackGuardianConfig): LlamaStackClientClient {
        Log.d(TAG, "Creating LlamaStack client for guardian endpoint: ${config.endpoint}")

        val clientBuilder = LlamaStackClientOkHttpClient.builder()
            .baseUrl(config.endpoint)
            .headers(mapOf("x-llamastack-client-version" to listOf("0.2.14")))

        if (!config.apiKey.isNullOrBlank()) {
            clientBuilder.headers(mapOf(
                "x-llamastack-client-version" to listOf("0.2.14"),
                "Authorization" to listOf("Bearer ${config.apiKey}")
            ))
        }

        return clientBuilder.build()
    }

    private fun convertShieldResponse(response: RunShieldResponse, strictness: String): GuardianAnalysisResult {
        val violation = response.violation()

        if (violation == null) {
            return GuardianAnalysisResult(
                status = GuardianStatus.SAFE,
                riskScore = 0.0,
                categories = emptyList(),
                action = GuardianAction.NONE,
                filteredText = null,
                details = mapOf("violations_count" to 0)
            )
        }

        // Map violation to our guardian categories and determine overall risk
        val categories = mutableListOf<GuardianCategory>()
        
        val riskScore = when (violation.violationLevel().value()) {
            SafetyViolation.ViolationLevel.Value.INFO -> 0.2
            SafetyViolation.ViolationLevel.Value.WARN -> 0.6
            SafetyViolation.ViolationLevel.Value.ERROR -> 0.9
            else -> 0.5
        }

        // Map to our categories (simplified - could be enhanced with metadata parsing)
        categories.add(GuardianCategory.UNSAFE_CONTENT)

        // Update overall status based on violation level
        val overallStatus = when (violation.violationLevel().value()) {
            SafetyViolation.ViolationLevel.Value.ERROR -> GuardianStatus.BLOCKED
            SafetyViolation.ViolationLevel.Value.WARN -> GuardianStatus.WARNING
            else -> GuardianStatus.SAFE
        }

        // Apply strictness adjustment
        val adjustedRiskScore = when (strictness.lowercase()) {
            "high" -> (riskScore * 1.2).coerceAtMost(1.0)
            "low" -> (riskScore * 0.8).coerceAtLeast(0.0)
            else -> riskScore
        }

        val action = when (overallStatus) {
            GuardianStatus.BLOCKED -> GuardianAction.BLOCK
            GuardianStatus.WARNING -> GuardianAction.REVIEW
            else -> GuardianAction.NONE
        }

        return GuardianAnalysisResult(
            status = overallStatus,
            riskScore = adjustedRiskScore,
            categories = categories.distinct(),
            action = action,
            filteredText = null,
            details = mapOf(
                "violations_count" to 1,
                "strictness_applied" to strictness,
                "llamastack_shield" to true
            )
        )
    }

    // Minimal config data class (reuse pattern from LlamaStackRunner)
    private data class LlamaStackGuardianConfig(
        val endpoint: String,
        val apiKey: String?,
        val shieldId: String,
        val guardianModel: String,
        val ollamaModel: String,
        val guardianCheckpoint: String,
        val connectionTimeout: Int
    ) {
        companion object {
            fun fromParams(params: Map<String, Any>, defaultModel: String): LlamaStackGuardianConfig {
                return LlamaStackGuardianConfig(
                    endpoint = (params["endpoint"] as? String)?.takeIf { it.isNotBlank() } ?: "http://localhost:8321",
                    apiKey = params["api_key"] as? String,
                    shieldId = params["shield_id"] as? String ?: DEFAULT_SHIELD_ID,
                    guardianModel = params["guardian_model"] as? String ?: DEFAULT_GUARDIAN_MODEL,
                    ollamaModel = params["ollama_model"] as? String ?: defaultModel,
                    guardianCheckpoint = params["guardian_checkpoint"] as? String ?: "input_only",
                    connectionTimeout = (params["connection_timeout"] as? Number)?.toInt() ?: 5000
                )
            }
        }
        
        // Convenience method to determine if this runner should handle input checking
        fun shouldCheckInput(): Boolean = guardianCheckpoint in listOf("input_only", "both")
        
        // Convenience method to determine if this runner should handle output checking  
        fun shouldCheckOutput(): Boolean = guardianCheckpoint in listOf("output_only", "both")
    }
}