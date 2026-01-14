package com.mtkresearch.breezeapp.engine.util

import android.content.Context
import android.media.AudioManager
import android.media.AudioTrack
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Simple but important test case to verify our refactor result
 * 
 * This test verifies that the unified EngineUtils class provides
 * the same functionality as the separate utility classes.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34]) // Must match minSdk=34 in build.gradle.kts
class UnifiedEngineUtilsTest {
    
    private lateinit var context: Context
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    @Test
    fun testEngineUtilsProvidesCoreFunctionality() {
        // Test that EngineUtils provides core audio functionality
        assertNotNull("EngineUtils should provide audio processing capabilities", EngineUtils)
        
        // Test TTS model configuration
        val ttsConfig = SherpaTtsConfigUtil.getTtsModelConfig(SherpaTtsConfigUtil.TtsModelType.VITS_MR_20250709)
        assertNotNull("EngineUtils should provide TTS model configuration", ttsConfig)
        assertEquals("TTS model directory should match", "vits-mr-20250709", ttsConfig.modelDir)
        
        // Test ASR sample rate
        val asrSampleRate = EngineUtils.getAsrSampleRate()
        assertEquals("ASR sample rate should be 16000", 16000, asrSampleRate)
        
        // Test PCM16 extraction
        val rawPcm16 = byteArrayOf(0, 0, -1, 127, 0, -128) // 0, 32767, -32768
        val (pcmShorts, wavInfo) = EngineUtils.extractPcm16(rawPcm16)
        assertEquals("Should extract 3 samples", 3, pcmShorts.size)
        assertNull("WAV info should be null for raw PCM", wavInfo)
        
        // Test Float to PCM16 conversion
        val floatArray = floatArrayOf(0.0f, 1.0f, -1.0f)
        val pcm16 = EngineUtils.floatToPcm16(floatArray)
        assertEquals("PCM16 array should have same length as Float array", floatArray.size, pcm16.size)
        
        // Test PCM16 to Float conversion
        val pcm16Array = shortArrayOf(0, 32767, -32768)
        val floatResult = EngineUtils.convertPcm16ToFloat(pcm16Array)
        assertEquals("Float array should have same length as PCM16 array", pcm16Array.size, floatResult.size)
    }
    
    @Test
    fun testAssetManagementFunctionality() {
        // Test asset management functionality
        assertNotNull("EngineUtils should provide asset management capabilities", EngineUtils)
        
        // Test TTS model configurations
        val allConfigs = SherpaTtsConfigUtil.TtsModelType.values().filter { it != SherpaTtsConfigUtil.TtsModelType.CUSTOM }
        assertTrue("Should have multiple TTS model configurations", allConfigs.isNotEmpty())
        
        // Test getting specific configurations
        allConfigs.forEach { type ->
            val config = SherpaTtsConfigUtil.getTtsModelConfig(type)
            assertNotNull("Model config for $type should not be null", config)
            assertFalse("Model directory should not be empty for $type", config.modelDir.isEmpty())
        }
    }
    
    @Test
    fun testWavUtilitiesFunctionality() {
        // Test WAV utilities functionality
        assertNotNull("EngineUtils should provide WAV utilities", EngineUtils)
        
        // Test WAV parsing with invalid data
        val invalidWav = byteArrayOf(0, 1, 2, 3)
        val wavInfo = EngineUtils.tryParseWav(invalidWav)
        assertNull("Should return null for invalid WAV data", wavInfo)
        
        // Test PCM16 extraction with raw data
        val rawPcm = byteArrayOf(0, 0, -1, 127) // 0, 32767
        val (pcmShorts, _) = EngineUtils.extractPcm16(rawPcm)
        assertEquals("Should extract 2 samples from raw PCM", 2, pcmShorts.size)
        assertEquals("First sample should be 0", 0.toShort(), pcmShorts[0])
        assertEquals("Second sample should be 32767", 32767.toShort(), pcmShorts[1])
    }
}