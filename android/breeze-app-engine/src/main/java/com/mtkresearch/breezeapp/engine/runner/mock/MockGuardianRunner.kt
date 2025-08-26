package com.mtkresearch.breezeapp.engine.runner.mock

import com.mtkresearch.breezeapp.engine.annotation.AIRunner
import com.mtkresearch.breezeapp.engine.annotation.RunnerPriority
import com.mtkresearch.breezeapp.engine.annotation.VendorType
import com.mtkresearch.breezeapp.engine.model.EngineSettings
import com.mtkresearch.breezeapp.engine.runner.core.RunnerInfo
import com.mtkresearch.breezeapp.engine.runner.guardian.*
import com.mtkresearch.breezeapp.engine.model.CapabilityType

/**
 * Mock Guardian Runner for testing and development purposes.
 * 
 * This implementation provides realistic content safety analysis by checking
 * against predefined patterns for various risk categories. It's designed to
 * be used during development and testing when a full guardian service is not available.
 */
@AIRunner(
    vendor = VendorType.UNKNOWN,
    priority = RunnerPriority.LOW,
    capabilities = [CapabilityType.GUARDIAN]
)
class MockGuardianRunner : BaseGuardianRunner() {
    
    private var loaded = false
    
    // Comprehensive patterns for different risk categories (English + Traditional Chinese)
    private val riskPatterns = mapOf(
        GuardianCategory.HATE_SPEECH to listOf(
            // English - Slurs and hate speech (sanitized for development purposes)
            "hate", "racist", "fascist", "nazi", "terrorist", "extremist",
            "supremacist", "discrimination", "prejudice", "bigot", "xenophobic",
            // Traditional Chinese - Hate speech and discrimination
            "仇恨", "種族主義", "歧視", "偏見", "仇外", "極端主義", "恐怖主義",
            "法西斯", "納粹", "至上主義", "排外", "種族歧視", "宗教歧視"
        ),
        
        GuardianCategory.VIOLENCE to listOf(
            // English - Violence and harm
            "kill", "murder", "assassinate", "torture", "hurt", "harm", 
            "violence", "attack", "assault", "fight", "punch", "shoot",
            "stab", "bomb", "explosion", "weapon", "gun", "knife", "blade",
            // Traditional Chinese - Violence and weapons
            "殺", "殺死", "謀殺", "暗殺", "折磨", "傷害", "暴力", "攻擊",
            "毆打", "打架", "開槍", "刺", "炸彈", "爆炸", "武器", "槍",
            "刀", "刀片", "兇器", "殺人", "血腥", "砍", "切", "射擊"
        ),
        
        GuardianCategory.SEXUAL_CONTENT to listOf(
            // English - Inappropriate sexual content
            "explicit", "pornographic", "sexual", "intimate", "adult content",
            "nsfw", "inappropriate", "seductive", "erotic",
            // Traditional Chinese - Sexual content
            "色情", "淫穢", "性愛", "裸體", "成人內容", "情色", "性感",
            "誘惑", "親密", "不雅", "猥褻", "淫蕩", "性行為", "做愛"
        ),
        
        GuardianCategory.SELF_HARM to listOf(
            // English - Self-harm and suicide related
            "suicide", "self-harm", "cutting", "overdose", "jump off",
            "end my life", "hurt myself", "kill myself", "depression extreme",
            // Traditional Chinese - Self-harm and suicide
            "自殺", "自殘", "自傷", "割腕", "跳樓", "結束生命", "了結生命",
            "尋死", "輕生", "自盡", "服毒", "上吊", "自我傷害", "想死",
            "不想活", "活不下去", "絕望", "厭世"
        ),
        
        GuardianCategory.PII to listOf(
            // English - Personal identifiable information patterns
            "ssn", "social security", "credit card", "password", "pin number",
            "bank account", "routing number", "driver license", "passport",
            // Traditional Chinese - Personal information
            "身分證", "身份證", "信用卡", "密碼", "銀行帳號", "帳戶", "護照",
            "駕照", "駕駛執照", "個人資料", "私人資訊", "機密資料", "帳密"
        ),
        
        GuardianCategory.TOXICITY to listOf(
            // English - Toxic behavior
            "toxic", "abuse", "harassment", "bullying", "trolling",
            "spam", "scam", "fraud", "manipulation", "threatening",
            // Traditional Chinese - Toxic behavior
            "毒性", "濫用", "騷擾", "霸凌", "網路霸凌", "惡搞", "垃圾訊息",
            "詐騙", "欺詐", "操縱", "威脅", "恐嚇", "辱罵", "謾罵",
            "人身攻擊", "惡意", "中傷", "誹謗", "羞辱"
        ),
        
        GuardianCategory.UNSAFE_CONTENT to listOf(
            // English - Generally unsafe content
            "illegal", "dangerous", "harmful", "risky", "unsafe",
            "prohibited", "banned", "restricted", "unauthorized",
            // Traditional Chinese - Unsafe content
            "非法", "危險", "有害", "風險", "不安全", "禁止", "被禁",
            "限制", "未授權", "違法", "犯罪", "不當", "有毒", "毒品",
            "賭博", "洗錢", "走私", "盜版", "侵權"
        )
    )
    
    // High-risk words that always trigger blocking (English + Traditional Chinese)
    private val highRiskWords = setOf(
        // English high-risk words
        "bomb", "terrorist", "kill", "murder", "suicide", "nazi", "weapon",
        // Traditional Chinese high-risk words
        "炸彈", "恐怖主義", "殺死", "謀殺", "自殺", "納粹", "武器",
        "殺人", "恐怖分子", "爆炸", "暗殺", "自盡", "輕生"
    )
    
    // Medium-risk words that trigger warnings (English + Traditional Chinese)
    private val mediumRiskWords = setOf(
        // English medium-risk words
        "fight", "hurt", "hate", "violence", "attack", "dangerous", "illegal",
        // Traditional Chinese medium-risk words
        "打架", "傷害", "仇恨", "暴力", "攻擊", "危險", "非法",
        "毆打", "歧視", "威脅", "恐嚇", "霸凌", "騷擾", "違法"
    )
    
    override fun analyze(text: String, config: GuardianConfig): GuardianAnalysisResult {
        val lowercaseText = text.lowercase()
        val detectedCategories = mutableListOf<GuardianCategory>()
        var maxRiskScore = 0.0
        var hasHighRisk = false
        var hasMediumRisk = false
        
        // Check for patterns in each category
        for ((category, patterns) in riskPatterns) {
            val matches = patterns.filter { pattern ->
                lowercaseText.contains(pattern.lowercase())
            }
            
            if (matches.isNotEmpty()) {
                detectedCategories.add(category)
                
                // Calculate risk score based on matches and category severity
                val categoryRisk = when (category) {
                    GuardianCategory.VIOLENCE, GuardianCategory.SELF_HARM -> 0.8
                    GuardianCategory.HATE_SPEECH, GuardianCategory.SEXUAL_CONTENT -> 0.7
                    GuardianCategory.TOXICITY, GuardianCategory.UNSAFE_CONTENT -> 0.6
                    GuardianCategory.PII -> 0.5
                    GuardianCategory.SPAM -> 0.3
                    else -> 0.4
                }
                
                maxRiskScore = maxOf(maxRiskScore, categoryRisk + (matches.size * 0.05))
            }
        }
        
        // Check for high and medium risk words
        for (word in highRiskWords) {
            if (lowercaseText.contains(word)) {
                hasHighRisk = true
                maxRiskScore = maxOf(maxRiskScore, 0.9)
            }
        }
        
        for (word in mediumRiskWords) {
            if (lowercaseText.contains(word)) {
                hasMediumRisk = true
                maxRiskScore = maxOf(maxRiskScore, 0.6)
            }
        }
        
        // Adjust risk score based on strictness level
        val adjustedRiskScore = when (config.strictness.lowercase()) {
            "high" -> maxRiskScore * 1.2 // More sensitive
            "low" -> maxRiskScore * 0.8  // Less sensitive
            else -> maxRiskScore // Medium (default)
        }.coerceIn(0.0, 1.0)
        
        // Determine status and action based on risk score
        val (status, action) = when {
            adjustedRiskScore >= 0.8 || hasHighRisk -> GuardianStatus.BLOCKED to GuardianAction.BLOCK
            adjustedRiskScore >= 0.5 || hasMediumRisk || detectedCategories.isNotEmpty() -> 
                GuardianStatus.WARNING to GuardianAction.REVIEW
            else -> GuardianStatus.SAFE to GuardianAction.NONE
        }
        
        // Generate filtered text if needed
        val filteredText = if (status == GuardianStatus.BLOCKED) {
            filterContent(text, detectedCategories)
        } else null
        
        return GuardianAnalysisResult(
            status = status,
            riskScore = adjustedRiskScore,
            categories = detectedCategories,
            action = action,
            filteredText = filteredText,
            details = mapOf(
                "high_risk_detected" to hasHighRisk,
                "medium_risk_detected" to hasMediumRisk,
                "pattern_matches" to detectedCategories.size,
                "strictness_applied" to config.strictness
            )
        )
    }
    
    /**
     * Filters content by replacing detected risky words with placeholders
     */
    private fun filterContent(text: String, categories: List<GuardianCategory>): String {
        var filtered = text
        
        // Replace high-risk words
        for (word in highRiskWords) {
            if (text.lowercase().contains(word)) {
                filtered = filtered.replace(word, "[BLOCKED]", ignoreCase = true)
            }
        }
        
        // Replace medium-risk words in blocked content
        for (word in mediumRiskWords) {
            if (text.lowercase().contains(word)) {
                filtered = filtered.replace(word, "[FILTERED]", ignoreCase = true)
            }
        }
        
        // Add category-specific filtering
        for (category in categories) {
            riskPatterns[category]?.forEach { pattern ->
                if (text.lowercase().contains(pattern.lowercase())) {
                    filtered = filtered.replace(pattern, "[${category.name}]", ignoreCase = true)
                }
            }
        }
        
        return filtered
    }
    
    override fun load(modelId: String, settings: EngineSettings, initialParams: Map<String, Any>): Boolean {
        loaded = true
        return true
    }
    
    override fun unload() {
        loaded = false
    }
    
    override fun isLoaded(): Boolean = loaded
    
    override fun getRunnerInfo(): RunnerInfo {
        return RunnerInfo(
            name = "mock_guardian",
            version = "1.2.0",
            capabilities = getCapabilities(),
            description = "Mock Guardian Runner with comprehensive content safety analysis for development and testing"
        )
    }
    
    override fun isSupported(): Boolean = true
}