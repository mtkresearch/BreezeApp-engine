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
        
        // S1-S13 Guardian Violation Types mapping to resource string IDs
        private val VIOLATION_TYPE_CODES = listOf(
            "S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8", "S9", "S10", "S11", "S12", "S13"
        )
    }
    
    // Violation Type data class
    private data class ViolationType(
        val code: String,
        val category: String,
        val message: String
    ) {
        fun formatMessage(): String = "[$code: $category] $message"
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
                    filteredText = getLocalizedString("guardian_service_unavailable"), // Localized message for connection errors
                    details = mapOf(
                        "error_type" to "connection_unavailable",
                        "endpoint" to this.config!!.endpoint,
                        "fallback_reason" to "LlamaStack server not available",
                        "recommended_action" to "Check LlamaStack server status",
                        "violation_type" to "CONNECTION_ERROR",
                        "violation_message" to "[${getLocalizedString("guardian_connection_error")}] ${getLocalizedString("guardian_service_unavailable")}"
                    )
                )
            }
            
            // For other errors, still use safe fallback with violation type info
            GuardianAnalysisResult(
                status = GuardianStatus.SAFE,
                riskScore = 0.0,
                categories = emptyList(),
                action = GuardianAction.NONE,
                filteredText = getLocalizedString("guardian_analysis_error"), // Localized message for general errors
                details = mapOf(
                    "error" to (e.message ?: "Unknown error"), 
                    "fallback" to true,
                    "violation_type" to "ANALYSIS_ERROR",
                    "violation_message" to "[${getLocalizedString("guardian_analysis_error_type")}] ${getLocalizedString("guardian_analysis_error")}"
                )
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
    
    /**
     * Get localized string using context
     */
    private fun getLocalizedString(stringKey: String): String {
        if (context == null) {
            // Fallback to English if no context available
            return when (stringKey) {
                "guardian_service_unavailable" -> "Guardian service is temporarily unavailable, message allowed by system"
                "guardian_connection_error" -> "Connection Error"
                "guardian_analysis_error" -> "Guardian analysis error occurred, message allowed by system"
                "guardian_analysis_error_type" -> "Analysis Error"
                else -> "Guardian check failed"
            }
        }
        
        try {
            val resourceId = context.resources.getIdentifier(stringKey, "string", context.packageName)
            return if (resourceId != 0) {
                context.getString(resourceId)
            } else {
                // Fallback to English
                when (stringKey) {
                    "guardian_service_unavailable" -> "Guardian service is temporarily unavailable, message allowed by system"
                    "guardian_connection_error" -> "Connection Error"
                    "guardian_analysis_error" -> "Guardian analysis error occurred, message allowed by system"
                    "guardian_analysis_error_type" -> "Analysis Error"
                    else -> "Guardian check failed"
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get localized string for key: $stringKey", e)
            return "Guardian check failed"
        }
    }
    
    /**
     * Create localized ViolationType from violation code
     */
    private fun createViolationType(code: String): ViolationType? {
        if (context == null) {
            // Fallback to English if no context available
            return createEnglishViolationType(code)
        }
        
        try {
            val categoryKey = "guardian_violation_${code.lowercase()}_category"
            val messageKey = "guardian_violation_${code.lowercase()}_message"
            
            val categoryResourceId = context.resources.getIdentifier(categoryKey, "string", context.packageName)
            val messageResourceId = context.resources.getIdentifier(messageKey, "string", context.packageName)
            
            val category = if (categoryResourceId != 0) {
                context.getString(categoryResourceId)
            } else {
                createEnglishViolationType(code)?.category ?: "Safety Violation"
            }
            
            val message = if (messageResourceId != 0) {
                context.getString(messageResourceId)
            } else {
                createEnglishViolationType(code)?.message ?: "Content safety check failed"
            }
            
            return ViolationType(code, category, message)
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create localized violation type for code: $code", e)
            return createEnglishViolationType(code)
        }
    }
    
    /**
     * Create English fallback ViolationType
     */
    private fun createEnglishViolationType(code: String): ViolationType? {
        return when (code) {
            "S1" -> ViolationType("S1", "Violent Crimes", "Your message contains content that may involve violent crimes")
            "S2" -> ViolationType("S2", "Non-Violent Crimes", "Your message contains content that may involve non-violent crimes")
            "S3" -> ViolationType("S3", "Sex-Related Crimes", "Your message contains content that may involve sex-related crimes")
            "S4" -> ViolationType("S4", "Child Sexual Exploitation", "Your message contains content that may involve child sexual exploitation")
            "S5" -> ViolationType("S5", "Defamation", "Your message contains content that may be defamatory")
            "S6" -> ViolationType("S6", "Specialized Advice", "Your message contains specialized financial, medical, or legal advice")
            "S7" -> ViolationType("S7", "Privacy", "Your message contains sensitive personal information that may violate privacy")
            "S8" -> ViolationType("S8", "Intellectual Property", "Your message contains content that may violate intellectual property rights")
            "S9" -> ViolationType("S9", "Indiscriminate Weapons", "Your message contains content related to indiscriminate weapons")
            "S10" -> ViolationType("S10", "Hate", "Your message contains content that may involve hate speech")
            "S11" -> ViolationType("S11", "Suicide & Self-Harm", "Your message contains content that may involve suicide or self-harm")
            "S12" -> ViolationType("S12", "Sexual Content", "Your message contains adult sexual content")
            "S13" -> ViolationType("S13", "Elections", "Your message contains content related to elections")
            else -> null
        }
    }

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

        // Parse violation metadata to extract S1-S13 type information
        val violationMetadata = violation.metadata().toString()
        val violationType = extractViolationType(violationMetadata)
        
        Log.d(TAG, "Violation metadata: $violationMetadata")
        Log.d(TAG, "Extracted violation type: ${violationType?.code}")

        // Map violation to our guardian categories and determine overall risk
        val categories = mutableListOf<GuardianCategory>()
        
        val riskScore = when (violation.violationLevel().value()) {
            SafetyViolation.ViolationLevel.Value.INFO -> 0.2
            SafetyViolation.ViolationLevel.Value.WARN -> 0.6
            SafetyViolation.ViolationLevel.Value.ERROR -> 0.9
            else -> 0.5
        }

        // Map to specific guardian categories based on violation type
        categories.add(mapViolationTypeToCategory(violationType))

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

        // Build detailed result with violation type information
        val details = mutableMapOf<String, Any>(
            "violations_count" to 1,
            "strictness_applied" to strictness,
            "llamastack_shield" to true
        )

        // Add violation type information if available
        violationType?.let { vType ->
            details["violation_type"] = vType.code
            details["violation_category"] = vType.category
            details["violation_message"] = vType.formatMessage()
        }

        return GuardianAnalysisResult(
            status = overallStatus,
            riskScore = adjustedRiskScore,
            categories = categories.distinct(),
            action = action,
            filteredText = violationType?.formatMessage(), // Set formatted violation message as filtered text
            details = details.toMap()
        )
    }
    
    /**
     * Extract S1-S13 violation type from LlamaStack violation metadata
     */
    private fun extractViolationType(metadata: String): ViolationType? {
        // Try to find S1-S13 pattern in metadata
        val pattern = Regex("S(1[0-3]|[1-9])")
        val match = pattern.find(metadata.uppercase())
        
        return if (match != null) {
            val violationCode = match.value
            if (VIOLATION_TYPE_CODES.contains(violationCode)) {
                createViolationType(violationCode)
            } else {
                null
            }
        } else {
            // Fallback to mapping based on keywords in metadata
            mapMetadataToViolationType(metadata)
        }
    }
    
    /**
     * Map metadata keywords to S1-S13 violation types as fallback
     */
    private fun mapMetadataToViolationType(metadata: String): ViolationType? {
        val lowerMetadata = metadata.lowercase()
        
        val violationCode = when {
            lowerMetadata.contains("violence") || lowerMetadata.contains("violent") -> "S1"
            lowerMetadata.contains("crime") || lowerMetadata.contains("illegal") -> "S2"
            lowerMetadata.contains("sexual") || lowerMetadata.contains("adult") -> "S3"
            lowerMetadata.contains("child") || lowerMetadata.contains("minor") -> "S4"
            lowerMetadata.contains("defamatory") || lowerMetadata.contains("specialized") -> "S5"
            lowerMetadata.contains("privacy") || lowerMetadata.contains("personal") -> "S6"
            lowerMetadata.contains("intellectual") || lowerMetadata.contains("copyright") -> "S7"
            lowerMetadata.contains("weapon") || lowerMetadata.contains("indiscriminate") -> "S8"
            lowerMetadata.contains("hate") || lowerMetadata.contains("discrimination") -> "S9"
            lowerMetadata.contains("self-harm") || lowerMetadata.contains("suicide") -> "S10"
            lowerMetadata.contains("sexual content") -> "S11"
            lowerMetadata.contains("election") || lowerMetadata.contains("political") -> "S12"
            lowerMetadata.contains("code") || lowerMetadata.contains("interpreter") -> "S13"
            else -> null
        }
        
        return violationCode?.let { createViolationType(it) }
    }
    
    /**
     * Map S1-S13 violation type to GuardianCategory (corrected according to official specification)
     */
    private fun mapViolationTypeToCategory(violationType: ViolationType?): GuardianCategory {
        return when (violationType?.code) {
            "S1" -> GuardianCategory.VIOLENCE          // S1: Violent Crimes
            "S2" -> GuardianCategory.UNSAFE_CONTENT    // S2: Non-Violent Crimes  
            "S3" -> GuardianCategory.SEXUAL_CONTENT    // S3: Sex-Related Crimes
            "S4" -> GuardianCategory.SEXUAL_CONTENT    // S4: Child Sexual Exploitation
            "S5" -> GuardianCategory.UNSAFE_CONTENT    // S5: Defamation
            "S6" -> GuardianCategory.UNSAFE_CONTENT    // S6: Specialized Advice
            "S7" -> GuardianCategory.PII               // S7: Privacy  
            "S8" -> GuardianCategory.UNSAFE_CONTENT    // S8: Intellectual Property
            "S9" -> GuardianCategory.VIOLENCE          // S9: Indiscriminate Weapons
            "S10" -> GuardianCategory.HATE_SPEECH      // S10: Hate
            "S11" -> GuardianCategory.SELF_HARM        // S11: Suicide & Self-Harm
            "S12" -> GuardianCategory.SEXUAL_CONTENT   // S12: Sexual Content
            "S13" -> GuardianCategory.UNSAFE_CONTENT   // S13: Elections
            else -> GuardianCategory.UNSAFE_CONTENT
        }
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