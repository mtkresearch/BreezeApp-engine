package com.mtkresearch.breezeapp.engine.runner.sherpa.base

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.mtkresearch.breezeapp.engine.domain.model.InferenceRequest
import com.mtkresearch.breezeapp.engine.domain.model.InferenceResult
import com.mtkresearch.breezeapp.engine.domain.model.RunnerError
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Base class for Sherpa TTS runners
 * 
 * This abstract class provides common functionality for all Sherpa TTS runners,
 * including audio playback management and common TTS operations.
 */
abstract class BaseSherpaTtsRunner(context: Context) : BaseSherpaRunner(context) {
    companion object {
        const val TAG = "BaseSherpaTtsRunner"
    }
    
    // Audio playback components with thread safety
    protected var audioTrack: AudioTrack? = null
    protected var sampleRate: Int = 22050
    protected val isPlaying = AtomicBoolean(false)
    protected val isStopped = AtomicBoolean(false)
    protected val audioLock = Any()

    override fun getTag(): String = TAG
    
    /**
     * Initialize audio playback with robust error handling
     */
    protected fun initAudioPlayback(sampleRate: Int) {
        try {
            Log.d(TAG, "Initializing audio playback")
            isStopped.set(false)
            isPlaying.set(false)
            
            synchronized(audioLock) {
                if (audioTrack == null || this.sampleRate != sampleRate) {
                    audioTrack?.stop()
                    audioTrack?.release()
                    this.sampleRate = sampleRate
                    
                    val bufLength = AudioTrack.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_FLOAT
                    )
                    
                    if (bufLength == AudioTrack.ERROR_BAD_VALUE || bufLength == AudioTrack.ERROR) {
                        throw Exception("Invalid audio parameters for AudioTrack")
                    }
                    
                    val attr = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()

                    val format = AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setSampleRate(sampleRate)
                        .build()

                    audioTrack = AudioTrack(
                        attr, format, bufLength, AudioTrack.MODE_STREAM,
                        AudioManager.AUDIO_SESSION_ID_GENERATE
                    )
                    
                    if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                        throw Exception("Failed to initialize AudioTrack")
                    }
                    
                    // Set volume to maximum and ensure proper audio routing for TTS
                    audioTrack?.setVolume(AudioTrack.getMaxVolume())
                    audioTrack?.play()
                    isPlaying.set(true)
                    
                    Log.d(TAG, "Audio playback initialized - sampleRate: $sampleRate")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio playback", e)
            throw e
        }
    }
    
    /**
     * Stop audio playback with robust cleanup
     */
    protected fun stopAudioPlayback() {
        try {
            isStopped.set(true)
            synchronized(audioLock) {
                audioTrack?.let { track ->
                    try {
                        track.pause()
                        track.flush()
                        track.stop()
                        track.release()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during AudioTrack cleanup", e)
                    }
                }
                audioTrack = null
                isPlaying.set(false)
            }
            Log.d(TAG, "Audio playback stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio playback", e)
        }
    }

    /**
     * Amplify volume of audio samples
     */
    protected fun amplifyVolume(samples: FloatArray, gainFactor: Float): FloatArray {
        return samples.map { sample ->
            (sample * gainFactor).coerceIn(-1.0f, 1.0f)
        }.toFloatArray()
    }

    /**
     * PCM float [-1,1] 轉 PCM 16bit Little Endian ByteArray
     */
    protected fun floatArrayToPCM16(samples: FloatArray): ByteArray {
        val out = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = (samples[i].coerceIn(-1f, 1f) * 32767).toInt()
            out[i * 2] = (v and 0xFF).toByte()
            out[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return out
    }
    
    /**
     * Validate TTS input text
     * 
     * @param input The inference request containing input data and parameters
     * @return Pair of (text, null) if valid, or (null, errorResult) if invalid
     */
    protected fun validateTtsInput(input: InferenceRequest): Pair<String?, InferenceResult?> {
        try {
            val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String
            return if (!text.isNullOrBlank()) {
                text to null
            } else {
                null to InferenceResult.error(
                    RunnerError.invalidInput("Text input required for TTS processing")
                )
            }
        } catch (e: Exception) {
            return null to InferenceResult.error(
                RunnerError.invalidInput("Error parsing text input: ${e.message}")
            )
        }
    }
    
    /**
     * Validate speaker ID parameter
     * 
     * @param input The inference request containing input data and parameters
     * @return Valid speaker ID (non-negative integer)
     */
    protected fun validateSpeakerId(input: InferenceRequest): Int {
        val speakerId = (input.inputs["speaker_id"] as? Number)?.toInt() ?: 0
        if (speakerId < 0) {
            throw IllegalArgumentException("Speaker ID must be non-negative")
        }
        return speakerId
    }
    
    /**
     * Validate speed parameter
     * 
     * @param input The inference request containing input data and parameters
     * @return Valid speed (positive float)
     */
    protected fun validateSpeed(input: InferenceRequest): Float {
        val speed = (input.inputs["speed"] as? Number)?.toFloat() ?: 1.0f
        if (speed <= 0) {
            throw IllegalArgumentException("Speed must be positive")
        }
        return speed
    }
}