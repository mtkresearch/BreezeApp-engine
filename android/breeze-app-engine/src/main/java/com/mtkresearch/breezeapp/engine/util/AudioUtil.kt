package com.mtkresearch.breezeapp.engine.util

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Utility class for audio operations in TTS and ASR
 * Extracted from Sherpa TTS example and extended with microphone recording capabilities
 */
object AudioUtil {
    private const val TAG = "AudioUtil"
    
    // ASR Recording constants (following Sherpa-onnx official example)
    private const val SAMPLE_RATE_ASR = 16000
    private const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT_ASR = AudioFormat.ENCODING_PCM_16BIT
    private const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC

    // ========== TTS Audio Playback Functions ==========

    /**
     * Create and configure AudioTrack for TTS playback
     * @param sampleRate Sample rate from TTS model
     * @return Configured AudioTrack instance
     */
    fun createAudioTrack(sampleRate: Int): AudioTrack {
        val bufLength = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        Log.i(TAG, "Creating AudioTrack - sampleRate: $sampleRate, bufferLength: $bufLength")

        val attr = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()

        val track = AudioTrack(
            attr, format, bufLength, AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        
        track.play()
        return track
    }

    /**
     * Write audio samples to AudioTrack
     * @param track AudioTrack instance
     * @param samples Float array of audio samples
     * @param stopped Flag to check if playback should stop
     * @return 1 if successful, 0 if stopped
     */
    fun writeAudioSamples(track: AudioTrack, samples: FloatArray, stopped: Boolean): Int {
        return if (!stopped) {
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            1
        } else {
            track.stop()
            0
        }
    }

    /**
     * Prepare AudioTrack for new playback
     * @param track AudioTrack instance
     */
    fun prepareForPlayback(track: AudioTrack) {
        track.pause()
        track.flush()
        track.play()
    }

    /**
     * Stop and cleanup AudioTrack
     * @param track AudioTrack instance
     */
    fun stopAndCleanup(track: AudioTrack) {
        track.pause()
        track.flush()
        track.stop()
    }

    // ========== ASR Microphone Recording Functions ==========

    /**
     * Check if RECORD_AUDIO permission is granted
     * @param context Application context
     * @return true if permission is granted
     */
    fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Create and configure AudioRecord for ASR microphone input
     * Following Sherpa-onnx official example configuration
     * @param context Application context for permission check
     * @return Configured AudioRecord instance or null if permission denied
     */
    fun createAudioRecord(context: Context): AudioRecord? {
        if (!hasRecordAudioPermission(context)) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            return null
        }

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_ASR,
            CHANNEL_CONFIG_IN,
            AUDIO_FORMAT_ASR
        )
        
        Log.i(TAG, "Creating AudioRecord - sampleRate: $SAMPLE_RATE_ASR, bufferSize: $bufferSize")
        Log.i(TAG, "Buffer size in milliseconds: ${bufferSize * 1000.0f / SAMPLE_RATE_ASR}")

        return try {
            val audioRecord = AudioRecord(
                AUDIO_SOURCE,
                SAMPLE_RATE_ASR,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT_ASR,
                bufferSize * 2 // Double buffer size as in official example
            )
            
            // Verify AudioRecord was created successfully
            if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                Log.i(TAG, "AudioRecord created successfully with state: ${audioRecord.state}")
                audioRecord
            } else {
                Log.e(TAG, "AudioRecord creation failed - state: ${audioRecord.state}")
                audioRecord.release()
                null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException creating AudioRecord - permission denied", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioRecord", e)
            null
        }
    }

    /**
     * Start recording with AudioRecord
     * @param audioRecord AudioRecord instance
     * @return true if recording started successfully
     */
    fun startRecording(audioRecord: AudioRecord): Boolean {
        return try {
            if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord.startRecording()
                Log.i(TAG, "AudioRecord started recording, state: ${audioRecord.recordingState}")
                true
            } else {
                Log.e(TAG, "AudioRecord not initialized, state: ${audioRecord.state}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            false
        }
    }

    /**
     * Stop recording and release AudioRecord
     * @param audioRecord AudioRecord instance
     */
    fun stopAndReleaseAudioRecord(audioRecord: AudioRecord) {
        try {
            if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.stop()
                Log.i(TAG, "AudioRecord stopped recording")
            }
            audioRecord.release()
            Log.i(TAG, "AudioRecord released")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping/releasing AudioRecord", e)
        }
    }

    /**
     * Read audio samples from AudioRecord
     * @param audioRecord AudioRecord instance
     * @param buffer ShortArray buffer to read into
     * @return Number of samples read, or negative on error
     */
    fun readAudioSamples(audioRecord: AudioRecord, buffer: ShortArray): Int {
        return try {
            audioRecord.read(buffer, 0, buffer.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading audio samples", e)
            -1
        }
    }

    /**
     * Create a Flow that continuously reads audio from microphone
     * Following the Sherpa-onnx official example pattern
     * @param context Application context
     * @param isRecording AtomicBoolean to control recording state
     * @return Flow of ShortArray audio chunks
     */
    fun createMicrophoneAudioFlow(
        context: Context,
        isRecording: AtomicBoolean
    ): Flow<ShortArray> = flow {
        Log.i(TAG, "Creating microphone audio flow...")
        
        val audioRecord = createAudioRecord(context)
        if (audioRecord == null) {
            Log.e(TAG, "Failed to create AudioRecord - check permissions")
            return@flow
        }

        Log.i(TAG, "AudioRecord created, state: ${audioRecord.state}")

        if (!startRecording(audioRecord)) {
            Log.e(TAG, "Failed to start recording")
            audioRecord.release()
            return@flow
        }

        Log.i(TAG, "Recording started successfully")

        try {
            // Buffer size for 100ms chunks (same as official example)
            val interval = 0.1 // 100 ms
            val bufferSize = (interval * SAMPLE_RATE_ASR).toInt()
            val buffer = ShortArray(bufferSize)

            Log.i(TAG, "Starting microphone audio flow with buffer size: $bufferSize")

            while (isRecording.get()) {
                val samplesRead = readAudioSamples(audioRecord, buffer)
                if (samplesRead > 0) {
                    Log.d(TAG, "Read $samplesRead audio samples")
                    // Emit a copy of the buffer with actual samples read
                    val audioChunk = buffer.copyOf(samplesRead)
                    emit(audioChunk)
                } else if (samplesRead < 0) {
                    Log.e(TAG, "Error reading audio samples: $samplesRead")
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in microphone audio flow", e)
        } finally {
            stopAndReleaseAudioRecord(audioRecord)
            Log.i(TAG, "Microphone audio flow ended")
        }
    }

    /**
     * Convert ShortArray (PCM16) to FloatArray for Sherpa processing
     * @param audioData PCM16 audio data
     * @return Float array normalized to [-1.0, 1.0]
     */
    fun convertPcm16ToFloat(audioData: ShortArray): FloatArray {
        return FloatArray(audioData.size) { i ->
            audioData[i] / 32768.0f
        }
    }

    /**
     * Convert ByteArray (PCM16) to FloatArray for Sherpa processing
     * @param audioData PCM16 audio data as ByteArray
     * @return Float array normalized to [-1.0, 1.0]
     */
    fun convertPcm16BytesToFloat(audioData: ByteArray): FloatArray {
        return FloatArray(audioData.size / 2) { i ->
            val sample = ((audioData[i * 2 + 1].toInt() and 0xFF) shl 8) or
                        (audioData[i * 2].toInt() and 0xFF)
            sample / 32768.0f
        }
    }

    /**
     * Get the sample rate used for ASR recording
     * @return Sample rate in Hz
     */
    fun getAsrSampleRate(): Int = SAMPLE_RATE_ASR
}