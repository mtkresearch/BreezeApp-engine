package com.mtkresearch.breezeapp.engine.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log

/**
 * Utility class for audio operations in TTS
 * Extracted from Sherpa TTS example for reuse
 */
object AudioUtil {
    private const val TAG = "AudioUtil"

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
}