package com.mtkresearch.breezeapp.engine.runner.guardian

import android.content.Context
import com.mtkresearch.breezeapp.engine.R

/**
 * Provides user-friendly Guardian messages with suggestions
 */
class GuardianMessageProvider(private val context: Context) {
    
    fun getGuardianMessage(category: GuardianCategory, isInput: Boolean = true): String {
        val prefix = if (isInput) "guardian_input_" else "guardian_output_"
        val resourceName = prefix + category.name.lowercase()
        
        return try {
            val resourceId = context.resources.getIdentifier(resourceName, "string", context.packageName)
            if (resourceId != 0) {
                context.getString(resourceId)
            } else {
                getDefaultMessage(category, isInput)
            }
        } catch (e: Exception) {
            getDefaultMessage(category, isInput)
        }
    }
    
    fun getGuardianSuggestion(category: GuardianCategory): String {
        val resourceName = "guardian_suggestion_" + category.name.lowercase()
        
        return try {
            val resourceId = context.resources.getIdentifier(resourceName, "string", context.packageName)
            if (resourceId != 0) {
                context.getString(resourceId)
            } else {
                getDefaultSuggestion(category)
            }
        } catch (e: Exception) {
            getDefaultSuggestion(category)
        }
    }
    
    fun getGuardianReason(category: GuardianCategory, riskScore: Double): String {
        val categoryName = category.name.replace('_', ' ').split(' ').joinToString(" ") { it.capitalize() }
        return "檢測到${categoryName}內容 (風險分數: ${String.format("%.2f", riskScore)})"
    }
    
    private fun getDefaultMessage(category: GuardianCategory, isInput: Boolean): String {
        return if (isInput) {
            "您的訊息包含不適當的內容，請修改後重新發送"
        } else {
            "AI生成的內容包含不適當的內容，已被過濾"
        }
    }
    
    private fun getDefaultSuggestion(category: GuardianCategory): String {
        return when (category) {
            GuardianCategory.TOXICITY -> "建議使用更積極正面的表達方式"
            GuardianCategory.HATE_SPEECH -> "請使用包容和尊重的語言"
            GuardianCategory.VIOLENCE -> "請避免描述暴力或危險行為"
            GuardianCategory.SEXUAL_CONTENT -> "請保持內容適合所有年齡層"
            GuardianCategory.SPAM -> "請提供有意義的內容而非重複訊息"
            GuardianCategory.SELF_HARM -> "如果您或他人需要幫助，請尋求專業支援"
            GuardianCategory.PII -> "請避免分享個人敏感資訊"
            else -> "請確保內容安全且適當"
        }
    }
}