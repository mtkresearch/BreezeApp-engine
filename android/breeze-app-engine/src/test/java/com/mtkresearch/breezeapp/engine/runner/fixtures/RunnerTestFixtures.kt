package com.mtkresearch.breezeapp.engine.runner.fixtures

import com.mtkresearch.breezeapp.engine.model.InferenceRequest
import java.util.UUID

/**
 * RunnerTestFixtures - 標準化測試資料生成器
 * 
 * 提供各種類型的測試資料，確保測試的一致性和可重複性。
 * 
 * ## 使用方式
 * ```kotlin
 * val request = RunnerTestFixtures.createTextRequest("Hello")
 * val audioRequest = RunnerTestFixtures.createAudioRequest(durationMs = 1000)
 * val edgeCaseRequest = RunnerTestFixtures.createLargeTextRequest(sizeKB = 100)
 * ```
 * 
 * @since Engine API v2.2
 */
object RunnerTestFixtures {
    
    // ═══════════════════════════════════════════════════════════════════
    // LLM Test Data
    // ═══════════════════════════════════════════════════════════════════
    
    /**
     * 建立標準文字請求
     */
    fun createTextRequest(
        text: String = "Hello, world!",
        sessionId: String = generateSessionId()
    ): InferenceRequest {
        return InferenceRequest(
            sessionId = sessionId,
            inputs = mapOf(InferenceRequest.INPUT_TEXT to text)
        )
    }
    
    /**
     * 建立含對話歷史的請求
     */
    fun createConversationRequest(
        messages: List<String>,
        sessionId: String = generateSessionId()
    ): InferenceRequest {
        return InferenceRequest(
            sessionId = sessionId,
            inputs = mapOf(
                InferenceRequest.INPUT_TEXT to messages.last(),
                "conversation_history" to messages.dropLast(1)
            )
        )
    }
    
    /**
     * 建立含系統提示的請求
     */
    fun createSystemPromptRequest(
        userMessage: String,
        systemPrompt: String = "You are a helpful assistant.",
        sessionId: String = generateSessionId()
    ): InferenceRequest {
        return InferenceRequest(
            sessionId = sessionId,
            inputs = mapOf(
                InferenceRequest.INPUT_TEXT to userMessage,
                "system_prompt" to systemPrompt
            )
        )
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // ASR Test Data
    // ═══════════════════════════════════════════════════════════════════
    
    /**
     * 建立音訊請求
     * 
     * @param durationMs 音訊長度（毫秒）
     * @param sampleRate 取樣率
     * @param channels 聲道數
     */
    fun createAudioRequest(
        durationMs: Int = 1000,
        sampleRate: Int = 16000,
        channels: Int = 1,
        sessionId: String = generateSessionId()
    ): InferenceRequest {
        val bytesPerSample = 2 // 16-bit audio
        val numSamples = (durationMs * sampleRate) / 1000
        val audioSize = numSamples * bytesPerSample * channels
        
        // 生成模擬音訊資料（簡單的正弦波模式）
        val audioData = ByteArray(audioSize) { index ->
            val sample = index / bytesPerSample
            val angle = (sample * 2 * Math.PI * 440.0) / sampleRate // 440Hz tone
            val value = (Math.sin(angle) * 127).toInt().toByte()
            value
        }
        
        return InferenceRequest(
            sessionId = sessionId,
            inputs = mapOf(
                InferenceRequest.INPUT_AUDIO to audioData,
                "sample_rate" to sampleRate,
                "channels" to channels,
                "duration_ms" to durationMs
            )
        )
    }
    
    /**
     * 建立靜音音訊請求
     */
    fun createSilentAudioRequest(
        durationMs: Int = 1000,
        sampleRate: Int = 16000,
        sessionId: String = generateSessionId()
    ): InferenceRequest {
        val bytesPerSample = 2
        val numSamples = (durationMs * sampleRate) / 1000
        val audioData = ByteArray(numSamples * bytesPerSample) { 0 }
        
        return InferenceRequest(
            sessionId = sessionId,
            inputs = mapOf(
                InferenceRequest.INPUT_AUDIO to audioData,
                "sample_rate" to sampleRate
            )
        )
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // TTS Test Data
    // ═══════════════════════════════════════════════════════════════════
    
    /**
     * 建立 TTS 請求
     */
    fun createTTSRequest(
        text: String = "Hello, world!",
        voice: String = "default",
        speed: Float = 1.0f,
        sessionId: String = generateSessionId()
    ): InferenceRequest {
        return InferenceRequest(
            sessionId = sessionId,
            inputs = mapOf(
                InferenceRequest.INPUT_TEXT to text,
                "voice" to voice,
                "speed" to speed
            )
        )
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════════════════════════════════
    
    /**
     * 建立空請求
     */
    fun createEmptyRequest(
        sessionId: String = generateSessionId()
    ): InferenceRequest {
        return InferenceRequest(
            sessionId = sessionId,
            inputs = emptyMap()
        )
    }
    
    /**
     * 建立大型文字請求
     * 
     * @param sizeKB 目標大小（KB）
     */
    fun createLargeTextRequest(
        sizeKB: Int = 100,
        sessionId: String = generateSessionId()
    ): InferenceRequest {
        val targetSize = sizeKB * 1024
        val baseText = "This is a test sentence for large input testing. "
        val repeats = targetSize / baseText.length + 1
        val largeText = baseText.repeat(repeats).take(targetSize)
        
        return InferenceRequest(
            sessionId = sessionId,
            inputs = mapOf(InferenceRequest.INPUT_TEXT to largeText)
        )
    }
    
    /**
     * 建立 Unicode 測試請求
     */
    fun createUnicodeRequest(
        sessionId: String = generateSessionId()
    ): InferenceRequest {
        val unicodeText = buildString {
            append("English: Hello World\n")
            append("中文: 你好世界\n")
            append("日本語: こんにちは世界\n")
            append("한국어: 안녕하세요 세계\n")
            append("العربية: مرحبا بالعالم\n")
            append("Emoji: 🌍🎉✨🚀❤️\n")
            append("Symbols: ©®™§¶†‡•\n")
        }
        
        return InferenceRequest(
            sessionId = sessionId,
            inputs = mapOf(InferenceRequest.INPUT_TEXT to unicodeText)
        )
    }
    
    /**
     * 建立特殊字元測試請求
     */
    fun createSpecialCharsRequest(
        sessionId: String = generateSessionId()
    ): InferenceRequest {
        val specialText = """
            Special chars: !@#$%^&*()_+-=[]{}|;':",./<>?`~
            XML entities: <tag attr="value">content</tag>
            JSON: {"key": "value", "array": [1, 2, 3]}
            Newlines: Line1
            Line2
            	Tab indented
            Quotes: "double" 'single' `backtick`
        """.trimIndent()
        
        return InferenceRequest(
            sessionId = sessionId,
            inputs = mapOf(InferenceRequest.INPUT_TEXT to specialText)
        )
    }
    
    /**
     * 建立空白字串請求
     */
    fun createWhitespaceRequest(
        sessionId: String = generateSessionId()
    ): InferenceRequest {
        return InferenceRequest(
            sessionId = sessionId,
            inputs = mapOf(InferenceRequest.INPUT_TEXT to "   \t\n   ")
        )
    }
    
    /**
     * 建立只有換行的請求
     */
    fun createNewlineOnlyRequest(
        sessionId: String = generateSessionId()
    ): InferenceRequest {
        return InferenceRequest(
            sessionId = sessionId,
            inputs = mapOf(InferenceRequest.INPUT_TEXT to "\n\n\n")
        )
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // Utility Methods
    // ═══════════════════════════════════════════════════════════════════
    
    /**
     * 產生唯一的 Session ID
     */
    fun generateSessionId(): String {
        return "test-${UUID.randomUUID()}"
    }
    
    /**
     * 建立指定數量的連續請求
     */
    fun createBatchRequests(
        count: Int,
        generator: (Int) -> InferenceRequest = { createTextRequest("Message $it") }
    ): List<InferenceRequest> {
        return (0 until count).map(generator)
    }
    
    /**
     * 標準測試文字集合
     */
    val standardTestTexts = listOf(
        "Hello, world!",
        "How are you today?",
        "What is the capital of France?",
        "Please explain quantum computing in simple terms.",
        "Write a haiku about programming.",
        "這是一個中文測試。",
        "1234567890",
        ""
    )
    
    /**
     * 壓力測試用的長文字
     */
    val stressTestText: String by lazy {
        createLargeTextRequest(500).inputs[InferenceRequest.INPUT_TEXT] as String
    }
}
