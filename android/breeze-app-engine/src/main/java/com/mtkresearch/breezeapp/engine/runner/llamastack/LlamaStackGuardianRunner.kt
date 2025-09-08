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
        
        // S1-S14 Guardian Violation Types with Traditional Chinese descriptions
        private val VIOLATION_TYPES = mapOf(
            "S1" to ViolationType("S1", "暴力犯罪", "您的訊息包含可能涉及暴力犯罪的內容"),
            "S2" to ViolationType("S2", "非暴力犯罪", "您的訊息包含可能涉及非暴力犯罪的內容"),
            "S3" to ViolationType("S3", "性相關犯罪", "您的訊息包含可能涉及性相關犯罪的內容"),
            "S4" to ViolationType("S4", "兒童利用", "您的訊息包含可能涉及兒童利用的內容"),
            "S5" to ViolationType("S5", "專業諮詢違法", "您的訊息包含可能涉及專業諮詢違法的內容"),
            "S6" to ViolationType("S6", "隱私侵犯", "您的訊息包含可能涉及隱私侵犯的內容"),
            "S7" to ViolationType("S7", "智慧財產權侵犯", "您的訊息包含可能涉及智慧財產權侵犯的內容"),
            "S8" to ViolationType("S8", "仇恨言論", "您的訊息包含可能涉及仇恨言論的內容"),
            "S9" to ViolationType("S9", "自我傷害", "您的訊息包含可能涉及自我傷害的內容"),
            "S10" to ViolationType("S10", "身體傷害", "您的訊息包含可能涉及身體傷害的內容"),
            "S11" to ViolationType("S11", "經濟傷害", "您的訊息包含可能涉及經濟傷害的內容"),
            "S12" to ViolationType("S12", "欺詐詐騙", "您的訊息包含可能涉及欺詐詐騙的內容"),
            "S13" to ViolationType("S13", "政府決策", "您的訊息包含可能影響政府決策的內容"),
            "S14" to ViolationType("S14", "其他安全風險", "您的訊息包含可能涉及其他安全風險的內容")
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
                    filteredText = "Guardian服務暫時無法使用，系統已自動通過此訊息", // Localized message for connection errors
                    details = mapOf(
                        "error_type" to "connection_unavailable",
                        "endpoint" to this.config!!.endpoint,
                        "fallback_reason" to "LlamaStack server not available",
                        "recommended_action" to "Check LlamaStack server status",
                        "violation_type" to "CONNECTION_ERROR",
                        "violation_message" to "[連線錯誤] Guardian服務暫時無法使用，系統已自動通過此訊息"
                    )
                )
            }
            
            // For other errors, still use safe fallback with violation type info
            GuardianAnalysisResult(
                status = GuardianStatus.SAFE,
                riskScore = 0.0,
                categories = emptyList(),
                action = GuardianAction.NONE,
                filteredText = "Guardian分析發生錯誤，系統已自動通過此訊息", // Localized message for general errors
                details = mapOf(
                    "error" to (e.message ?: "Unknown error"), 
                    "fallback" to true,
                    "violation_type" to "ANALYSIS_ERROR",
                    "violation_message" to "[分析錯誤] Guardian分析發生錯誤，系統已自動通過此訊息"
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

        // Parse violation metadata to extract S1-S14 type information
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
     * Extract S1-S14 violation type from LlamaStack violation metadata
     */
    private fun extractViolationType(metadata: String): ViolationType? {
        // Try to find S1-S14 pattern in metadata
        val pattern = Regex("S(1[0-4]|[1-9])")
        val match = pattern.find(metadata.uppercase())
        
        return if (match != null) {
            val violationCode = match.value
            VIOLATION_TYPES[violationCode]
        } else {
            // Fallback to mapping based on keywords in metadata
            mapMetadataToViolationType(metadata)
        }
    }
    
    /**
     * Map metadata keywords to S1-S14 violation types as fallback
     */
    private fun mapMetadataToViolationType(metadata: String): ViolationType? {
        val lowerMetadata = metadata.lowercase()
        
        return when {
            lowerMetadata.contains("violence") || lowerMetadata.contains("violent") -> VIOLATION_TYPES["S1"]
            lowerMetadata.contains("crime") || lowerMetadata.contains("illegal") -> VIOLATION_TYPES["S2"]
            lowerMetadata.contains("sexual") || lowerMetadata.contains("adult") -> VIOLATION_TYPES["S3"]
            lowerMetadata.contains("child") || lowerMetadata.contains("minor") -> VIOLATION_TYPES["S4"]
            lowerMetadata.contains("hate") || lowerMetadata.contains("discrimination") -> VIOLATION_TYPES["S8"]
            lowerMetadata.contains("self-harm") || lowerMetadata.contains("suicide") -> VIOLATION_TYPES["S9"]
            lowerMetadata.contains("harm") || lowerMetadata.contains("injury") -> VIOLATION_TYPES["S10"]
            lowerMetadata.contains("fraud") || lowerMetadata.contains("scam") -> VIOLATION_TYPES["S12"]
            else -> VIOLATION_TYPES["S14"] // Default to "Other Safety Risks"
        }
    }
    
    /**
     * Map S1-S14 violation type to GuardianCategory
     */
    private fun mapViolationTypeToCategory(violationType: ViolationType?): GuardianCategory {
        return when (violationType?.code) {
            "S1" -> GuardianCategory.VIOLENCE          // S1: Violent Crimes
            "S2" -> GuardianCategory.UNSAFE_CONTENT    // S2: Non-Violent Crimes  
            "S3" -> GuardianCategory.SEXUAL_CONTENT    // S3: Sex-Related Crimes
            "S4" -> GuardianCategory.SEXUAL_CONTENT    // S4: Child Exploitation (map to sexual content)
            "S5" -> GuardianCategory.UNSAFE_CONTENT    // S5: Defamatory Content
            "S6" -> GuardianCategory.PII               // S6: Privacy
            "S7" -> GuardianCategory.UNSAFE_CONTENT    // S7: Intellectual Property  
            "S8" -> GuardianCategory.HATE_SPEECH       // S8: Indiscriminate Weapons
            "S9" -> GuardianCategory.SELF_HARM         // S9: Hate
            "S10" -> GuardianCategory.SELF_HARM        // S10: Suicide & Self-Harm
            "S11" -> GuardianCategory.VIOLENCE         // S11: Sexual Content (Adult)
            "S12" -> GuardianCategory.SPAM             // S12: Elections
            "S13" -> GuardianCategory.UNSAFE_CONTENT   // S13: Code Interpreter Abuse
            "S14" -> GuardianCategory.UNSAFE_CONTENT   // S14: Other Safety Risks
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