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
 * Unit tests for EngineUtils
 * 
 * These tests verify that the unified utility class works correctly
 * and provides the same functionality as the separate utility classes.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28]) // Use SDK 28 for better Robolectric support
class EngineUtilsTest {
    
    private lateinit var context: Context
    
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    @Test
    fun testCreateAudioTrack() {
        // Test that we can create an AudioTrack
        val sampleRate = 22050
        val track = EngineUtils.createAudioTrack(sampleRate)
        assertNotNull("AudioTrack should not be null", track)
        assertEquals("AudioTrack should be in playing state", AudioTrack.PLAYSTATE_PLAYING, track.playState)
        track.release()
    }
    
    @Test
    fun testGetAsrSampleRate() {
        // Test that we get the correct ASR sample rate
        val sampleRate = EngineUtils.getAsrSampleRate()
        assertEquals("ASR sample rate should be 16000", 16000, sampleRate)
    }
    
    @Test
    fun testConvertPcm16ToFloat() {
        // Test PCM16 to Float conversion
        val pcm16 = shortArrayOf(0, 32767, -32768, 16384)
        val floatArray = EngineUtils.convertPcm16ToFloat(pcm16)
        
        assertEquals("Float array should have same length as PCM16 array", pcm16.size, floatArray.size)
        assertEquals("Zero should convert to 0.0f", 0.0f, floatArray[0], 0.0001f)
        assertEquals("Max positive should convert to 1.0f", 1.0f, floatArray[1], 0.0001f)
        assertEquals("Min negative should convert to -1.0f", -1.0f, floatArray[2], 0.0001f)
        assertEquals("Half positive should convert to 0.5f", 0.5f, floatArray[3], 0.0001f)
    }
    
    @Test
    fun testTtsModelConfig() {
        // Test that we can get TTS model configurations
        val config = SherpaTtsConfigUtil.getTtsModelConfig(SherpaTtsConfigUtil.TtsModelType.VITS_MR_20250709)
        assertNotNull("Model config should not be null", config)
        assertEquals("Model directory should match", "vits-mr-20250709", config.modelDir)
        assertEquals("Model name should match", "vits-mr-20250709.onnx", config.modelName)
    }
    
    @Test
    fun testExtractPcm16() {
        // Test extracting PCM16 from raw bytes
        val rawPcm16 = byteArrayOf(0, 0, -1, 127, 0, -128) // 0, 32767, -32768
        val (pcmShorts, wavInfo) = EngineUtils.extractPcm16(rawPcm16)
        
        assertEquals("Should extract 3 samples", 3, pcmShorts.size)
        assertEquals("First sample should be 0", 0.toShort(), pcmShorts[0])
        assertEquals("Second sample should be 32767", 32767.toShort(), pcmShorts[1])
        assertEquals("Third sample should be -32768", (-32768).toShort(), pcmShorts[2])
        assertNull("WAV info should be null for raw PCM", wavInfo)
    }
    
    /*
    @Test
    fun testFloatToPcm16() {
        // Test Float to PCM16 conversion
        val floatArray = floatArrayOf(0.0f, 1.0f, -1.0f, 0.5f)
        val pcm16 = EngineUtils.floatToPcm16(floatArray)
        
        assertEquals("PCM16 array should have same length as Float array", floatArray.size, pcm16.size)
        assertEquals("Zero should convert to 0", 0.toShort(), pcm16[0])
        assertEquals("One should convert to 32767", 32767.toShort(), pcm16[1])
        assertEquals("Negative one should convert to -32768", (-32768).toShort(), pcm16[2])
        assertEquals("Half should convert to 16384", 16384.toShort(), pcm16[3])
    }
    */
}