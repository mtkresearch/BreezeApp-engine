package com.mtkresearch.breezeapp.edgeai.integration.helpers


import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*


/**
 * Data loader helper for EdgeAI Integration tests.
 *
 * This object is responsible for providing structured test scenarios to the
 * integration tests. While the original design pattern may suggest loading from
 * a JSON asset (`assets/test_data/asr_category2_behavior.json`), this implementation
 * provides type-safe, hardcoded data to ensure tests compile and run reliably
 * without requiring external JSON parsing dependencies or file I/O during object initialization.
 */
object TestASRDataLoader {

    /**
     * Loads the Category 2 ASR Test Data.
     * Used by [com.mtkresearch.breezeapp.edgeai.integration.EdgeAIASRBehaviorTest]
     */
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().context // Use test context for assets

    fun loadAudioFromAssets(filename: String): ByteArray? {
        return try {
             context.assets.open("test_data/$filename").use { inputStream ->
                inputStream.readBytes()
            }
        } catch (e: Exception) {
            val errorMsg = "Load test data failed: ${e.message}"
            println(errorMsg)
            null
        }
    }
    fun loadASRCategory2Data(): ASRCategory2Data {
        return ASRCategory2Data(
            englishAccuracyTests = getEnglishScenarios(),
            chineseAccuracyTests = getChineseScenarios()
        )
    }

    private fun getEnglishScenarios(): List<EnglishTestScenario> {
        return listOf(
            EnglishTestScenario(
                audioFile = "test_audio_hello.wav",
                expectedPhrases = listOf("hello", "hi", "greetings", "good morning")
            ),
            EnglishTestScenario(
                audioFile = "test_audio_goodbye.wav",
                expectedPhrases = listOf("good bye", "bye", "goodbye")
            ),
            EnglishTestScenario(
                audioFile = "test_audio_thanks.wav",
                expectedPhrases = listOf("thank you", "thank", "thanks")
            ),
            EnglishTestScenario(
                audioFile = "test_audio_question.wav",
                expectedPhrases = listOf("how are you", "how's it going")
            )
        )
    }

    private fun getChineseScenarios(): List<ChineseTestScenario> {
        return listOf(
            ChineseTestScenario(
                audioFile = "test_audio_nihao.wav",
                expectedCharacters = listOf("你好", "您好")
            ),
            ChineseTestScenario(
                audioFile = "test_audio_xiexie.wav",
                expectedCharacters = listOf("謝謝", "谢谢")
            ),
            ChineseTestScenario(
                audioFile = "test_audio_zaijian.wav",
                expectedCharacters = listOf("再見", "再见")
            ),
        )
    }
}

/**
 * Container for ASR Category 2 Test Data.
 */
data class ASRCategory2Data(
    val englishAccuracyTests: List<EnglishTestScenario>,
    val chineseAccuracyTests: List<ChineseTestScenario>
)

/**
 * Represents a single English transcription test case.
 * Matches usage: testCase.audioFile, testCase.expectedPhrases
 */
data class EnglishTestScenario(
    val audioFile: String,
    val expectedPhrases: List<String>
)

/**
 * Represents a single Chinese transcription test case.
 * Matches usage: testCase.audioFile, testCase.expectedCharacters
 */
data class ChineseTestScenario(
    val audioFile: String,
    val expectedCharacters: List<String>
)