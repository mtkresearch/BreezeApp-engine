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
import com.k2fsa.sherpa.onnx.WaveReader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unified utility class for BreezeApp Engine
 * 
 * This class consolidates functionality from multiple utility classes:
 * - Audio processing (TTS playback and ASR recording)
 * - Asset management (copying assets to internal/external storage)
 * - TTS model configuration
 * 
 * The goal is to reduce redundancy and provide a single point of access
 * for common engine operations.
 */
object EngineUtils {
    private const val TAG = "EngineUtils"
    
    // Audio constants
    private const val SAMPLE_RATE_ASR = 16000
    private const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT_ASR = AudioFormat.ENCODING_PCM_16BIT
    private const val AUDIO_SOURCE = MediaRecorder.AudioSource.MIC
    
    // TTS model types
    enum class TtsModelType {
        VITS_MR_20250709,       // Your custom model
        VITS_MELO_ZH_EN,        // VITS MeloTTS model
        VITS_PIPER_EN_US_AMY,   // English Piper model
        VITS_ICEFALL_ZH,        // Chinese VITS model
        MATCHA_ICEFALL_ZH,      // Chinese Matcha model
        KOKORO_EN,              // English Kokoro model
        CUSTOM                  // Custom configuration
    }
    
    // TTS model configuration data class
    data class TtsModelConfig(
        val modelDir: String,
        val modelName: String = "",
        val acousticModelName: String = "",
        val vocoder: String = "",
        val voices: String = "",
        val lexicon: String = "",
        val dataDir: String = "",
        val dictDir: String = "",
        val ruleFsts: String = "",
        val ruleFars: String = "",
        val description: String = ""
    )
    
    // WAV info data class
    data class WavInfo(
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val dataOffset: Int,
        val dataLength: Int
    )
    
    // ========== Audio Processing Functions ==========
    
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

    // ========== Asset Management Functions ==========
    
    /**
     * Copy assets directory to external files directory
     * @param context Application context
     * @param assetPath Path in assets folder
     * @return Absolute path of copied directory
     */
    fun copyAssetsToExternalFiles(context: Context, assetPath: String): String {
        Log.i(TAG, "Copying assets from $assetPath to external files")
        copyAssets(context, assetPath)
        val newDir = context.getExternalFilesDir(null)!!.absolutePath
        Log.i(TAG, "Assets copied to: $newDir")
        return newDir
    }

    /**
     * Copy assets directory to internal files directory
     * @param context Application context
     * @param assetPath Path in assets folder
     * @return Absolute path of copied directory
     */
    fun copyAssetsToInternalFiles(context: Context, assetPath: String): String {
        Log.i(TAG, "Copying assets from $assetPath to internal files")
        copyAssetsToInternal(context, assetPath)
        val newDir = context.filesDir.absolutePath
        Log.i(TAG, "Assets copied to: $newDir")
        return newDir
    }

    /**
     * Recursively copy assets to external files directory
     */
    private fun copyAssets(context: Context, path: String) {
        val assets: Array<String>?
        try {
            assets = context.assets.list(path)
            if (assets.isNullOrEmpty()) {
                copyFileToExternal(context, path)
            } else {
                val fullPath = "${context.getExternalFilesDir(null)}/$path"
                val dir = File(fullPath)
                dir.mkdirs()
                for (asset in assets) {
                    val p: String = if (path == "") "" else "$path/"
                    copyAssets(context, "$p$asset")
                }
            }
        } catch (ex: IOException) {
            Log.e(TAG, "Failed to copy $path. $ex")
            throw ex
        }
    }

    /**
     * Recursively copy assets to internal files directory
     */
    private fun copyAssetsToInternal(context: Context, path: String) {
        val assets: Array<String>?
        try {
            assets = context.assets.list(path)
            if (assets.isNullOrEmpty()) {
                copyFileToInternal(context, path)
            } else {
                val fullPath = "${context.filesDir}/$path"
                val dir = File(fullPath)
                dir.mkdirs()
                for (asset in assets) {
                    val p: String = if (path == "") "" else "$path/"
                    copyAssetsToInternal(context, "$p$asset")
                }
            }
        } catch (ex: IOException) {
            Log.e(TAG, "Failed to copy $path. $ex")
            throw ex
        }
    }

    /**
     * Copy single file to external files directory
     */
    private fun copyFileToExternal(context: Context, filename: String) {
        try {
            val istream = context.assets.open(filename)
            val newFilename = "${context.getExternalFilesDir(null)}/$filename"
            val ostream = File(newFilename).outputStream()
            copyStream(istream, ostream)
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to copy $filename to external, $ex")
            throw ex
        }
    }

    /**
     * Copy single file to internal files directory
     */
    private fun copyFileToInternal(context: Context, filename: String) {
        try {
            val istream = context.assets.open(filename)
            val newFilename = "${context.filesDir}/$filename"
            val ostream = File(newFilename).outputStream()
            copyStream(istream, ostream)
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to copy $filename to internal, $ex")
            throw ex
        }
    }

    /**
     * Copy input stream to output stream
     */
    private fun copyStream(istream: java.io.InputStream, ostream: java.io.OutputStream) {
        val buffer = ByteArray(1024)
        var read = istream.read(buffer)
        while (read != -1) {
            ostream.write(buffer, 0, read)
            read = istream.read(buffer)
        }
        istream.close()
        ostream.flush()
        ostream.close()
    }
    
    // ========== TTS Model Configuration Functions ==========
    
    /**
     * Get predefined TTS model configuration
     */
    fun getTtsModelConfig(type: TtsModelType): TtsModelConfig {
        return when (type) {
            TtsModelType.VITS_MR_20250709 -> TtsModelConfig(
                modelDir = "vits-mr-20250709",
                modelName = "vits-mr-20250709.onnx",
                lexicon = "lexicon.txt",
                description = "VITS MR custom TTS model (2025-07-09)"
            )
            
            TtsModelType.VITS_MELO_ZH_EN -> TtsModelConfig(
                modelDir = "vits-melo-tts-zh_en",
                modelName = "model.onnx",
                lexicon = "lexicon.txt",
                dictDir = "vits-melo-tts-zh_en/dict",
                description = "VITS MeloTTS Chinese-English bilingual model"
            )
            
            TtsModelType.VITS_PIPER_EN_US_AMY -> TtsModelConfig(
                modelDir = "vits-piper-en_US-amy-low",
                modelName = "en_US-amy-low.onnx",
                dataDir = "vits-piper-en_US-amy-low/espeak-ng-data",
                description = "VITS Piper English Amy voice"
            )
            
            TtsModelType.VITS_ICEFALL_ZH -> TtsModelConfig(
                modelDir = "vits-icefall-zh-aishell3",
                modelName = "model.onnx",
                ruleFars = "vits-icefall-zh-aishell3/rule.far",
                lexicon = "lexicon.txt",
                description = "VITS Icefall Chinese AISHELL3 model"
            )
            
            TtsModelType.MATCHA_ICEFALL_ZH -> TtsModelConfig(
                modelDir = "matcha-icefall-zh-baker",
                acousticModelName = "model-steps-3.onnx",
                vocoder = "vocos-22khz-univ.onnx",
                lexicon = "lexicon.txt",
                dictDir = "matcha-icefall-zh-baker/dict",
                description = "Matcha Icefall Chinese Baker model"
            )
            
            TtsModelType.KOKORO_EN -> TtsModelConfig(
                modelDir = "kokoro-en-v0_19",
                modelName = "model.onnx",
                voices = "voices.bin",
                dataDir = "kokoro-en-v0_19/espeak-ng-data",
                description = "Kokoro English model"
            )
            
            TtsModelType.CUSTOM -> TtsModelConfig(
                modelDir = "",
                description = "Custom TTS model configuration"
            )
        }
    }
    
    // ========== WAV Utilities (Diagnostics) ==========
    
    /**
     * Simple WAV header parser for PCM 16-bit little-endian files.
     * Returns null if the input is not a valid PCM WAV.
     */
    fun tryParseWav(bytes: ByteArray): WavInfo? {
        if (bytes.size < 44) return null

        // Check RIFF/WAVE
        if (!(bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte())) return null
        if (!(bytes[8] == 'W'.code.toByte() && bytes[9] == 'A'.code.toByte() && bytes[10] == 'V'.code.toByte() && bytes[11] == 'E'.code.toByte())) return null

        var offset = 12
        var fmtFound = false
        var dataFound = false
        var audioFormat = 1
        var channels = 1
        var sampleRate = 16000
        var bitsPerSample = 16
        var dataOffset = -1
        var dataLength = -1

        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4)
            val chunkSize = ((bytes[offset + 4].toInt() and 0xFF)) or
                ((bytes[offset + 5].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 6].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 7].toInt() and 0xFF) shl 24)
            val chunkDataStart = offset + 8

            when (chunkId) {
                "fmt " -> {
                    if (chunkDataStart + 16 > bytes.size) return null
                    audioFormat = ((bytes[chunkDataStart + 0].toInt() and 0xFF)) or ((bytes[chunkDataStart + 1].toInt() and 0xFF) shl 8)
                    channels = ((bytes[chunkDataStart + 2].toInt() and 0xFF)) or ((bytes[chunkDataStart + 3].toInt() and 0xFF) shl 8)
                    sampleRate = (bytes[chunkDataStart + 4].toInt() and 0xFF) or
                        ((bytes[chunkDataStart + 5].toInt() and 0xFF) shl 8) or
                        ((bytes[chunkDataStart + 6].toInt() and 0xFF) shl 16) or
                        ((bytes[chunkDataStart + 7].toInt() and 0xFF) shl 24)
                    bitsPerSample = ((bytes[chunkDataStart + 14].toInt() and 0xFF)) or ((bytes[chunkDataStart + 15].toInt() and 0xFF) shl 8)
                    fmtFound = true
                }
                "data" -> {
                    dataOffset = chunkDataStart
                    dataLength = chunkSize
                    dataFound = true
                }
            }

            offset = chunkDataStart + chunkSize
            if (offset % 2 == 1) offset++ // pad to even

            if (fmtFound && dataFound) break
        }

        if (!fmtFound || !dataFound) return null
        if (audioFormat != 1) return null // only PCM
        if (bitsPerSample != 16) return null // only 16-bit PCM
        if (dataOffset < 0 || dataLength <= 0) return null
        if (dataOffset + dataLength > bytes.size) return null

        return WavInfo(sampleRate, channels, bitsPerSample, dataOffset, dataLength)
    }

    /**
     * Extract PCM16 ShortArray from either raw PCM16 bytes or WAV bytes.
     * Returns Pair<pcmShorts, wavInfo?>; wavInfo is null when input is raw PCM.
     */
    fun extractPcm16(bytes: ByteArray): Pair<ShortArray, WavInfo?> {
        val wav = tryParseWav(bytes)
        return if (wav != null) {
            val pcmBytes = bytes.copyOfRange(wav.dataOffset, wav.dataOffset + wav.dataLength)
            val samples = ShortArray(pcmBytes.size / 2)
            var j = 0
            for (i in samples.indices) {
                val lo = pcmBytes[j].toInt() and 0xFF
                val hi = pcmBytes[j + 1].toInt() and 0xFF
                samples[i] = ((hi shl 8) or lo).toShort()
                j += 2
            }
            Pair(samples, wav)
        } else {
            val samples = ShortArray(bytes.size / 2)
            var j = 0
            for (i in samples.indices) {
                val lo = bytes[j].toInt() and 0xFF
                val hi = bytes[j + 1].toInt() and 0xFF
                samples[i] = ((hi shl 8) or lo).toShort()
                j += 2
            }
            Pair(samples, null)
        }
    }

    /**
     * Save PCM16 data to a WAV file.
     */
    fun savePcm16AsWav(file: File, pcm: ShortArray, sampleRate: Int, channels: Int = 1, bitsPerSample: Int = 16) {
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val dataSize = pcm.size * 2
        val totalDataLen = 36 + dataSize

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        // RIFF
        header.put('R'.code.toByte()).put('I'.code.toByte()).put('F'.code.toByte()).put('F'.code.toByte())
        header.putInt(totalDataLen)
        header.put('W'.code.toByte()).put('A'.code.toByte()).put('V'.code.toByte()).put('E'.code.toByte())
        // fmt chunk
        header.put('f'.code.toByte()).put('m'.code.toByte()).put('t'.code.toByte()).put(' '.code.toByte())
        header.putInt(16) // PCM chunk size
        header.putShort(1) // PCM format
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort((channels * (bitsPerSample / 8)).toShort())
        header.putShort(bitsPerSample.toShort())
        // data chunk
        header.put('d'.code.toByte()).put('a'.code.toByte()).put('t'.code.toByte()).put('a'.code.toByte())
        header.putInt(dataSize)

        val fos = file.outputStream().buffered()
        try {
            fos.write(header.array())
            // write little-endian samples
            val buffer = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcm) buffer.putShort(s)
            fos.write(buffer.array())
            fos.flush()
        } finally {
            try { fos.close() } catch (_: Exception) {}
        }
    }

    /**
     * Save a diagnostics WAV file under app files/diagnostics.
     * Returns the File if succeeded; null otherwise.
     */
    fun saveDiagnosticsWav(context: Context, fileName: String, pcm: ShortArray, sampleRate: Int, channels: Int = 1): File? {
        return try {
            val dir = File(context.filesDir, "diagnostics")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            savePcm16AsWav(file, pcm, sampleRate, channels)
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save diagnostics WAV", e)
            null
        }
    }

    /**
     * Prepare ASR input from raw bytes using Sherpa's WaveReader when possible.
     * - If bytes look like a WAV, write to a temp file and decode via WaveReader to FloatArray.
     * - Otherwise, treat as raw PCM16 mono at defaultSampleRate and convert to FloatArray.
     * Returns Pair<FloatArray samples, Int sampleRate>.
     */
    fun prepareAsrFloatSamples(
        context: Context,
        sessionId: String,
        audioBytes: ByteArray,
        defaultSampleRate: Int = SAMPLE_RATE_ASR,
        resampleToDefault: Boolean = false,
        saveDiagnostics: Boolean = false
    ): Pair<FloatArray, Int> {
        val isLikelyWav = audioBytes.size >= 12 &&
            audioBytes[0] == 'R'.code.toByte() && audioBytes[1] == 'I'.code.toByte() &&
            audioBytes[2] == 'F'.code.toByte() && audioBytes[3] == 'F'.code.toByte() &&
            audioBytes[8] == 'W'.code.toByte() && audioBytes[9] == 'A'.code.toByte() &&
            audioBytes[10] == 'V'.code.toByte() && audioBytes[11] == 'E'.code.toByte()

        var result: Pair<FloatArray, Int> = if (isLikelyWav) {
            // Persist the bytes so WaveReader can read reliably
            val tmpName = "asr_input_${sessionId}_${System.currentTimeMillis()}.wav"
            val wavFile = File(context.cacheDir, tmpName)
            kotlin.runCatching { wavFile.outputStream().use { it.write(audioBytes) } }
            try {
                val waveData = WaveReader.readWave(wavFile.absolutePath)
                Log.d(TAG, "WaveReader WAV - sr=${waveData.sampleRate}, frames=${waveData.samples.size}")
                if (saveDiagnostics) {
                    kotlin.runCatching {
                        val pcm = floatToPcm16(waveData.samples)
                        val diagName = "asr_input_wav_${sessionId}_${System.currentTimeMillis()}.wav"
                        saveDiagnosticsWav(context, diagName, pcm, waveData.sampleRate, 1)
                    }
                }
                Pair(waveData.samples, waveData.sampleRate)
            } catch (e: Throwable) {
                Log.w(TAG, "WaveReader failed. Falling back to raw PCM conversion.", e)
                val (pcmShorts, _) = extractPcm16(audioBytes)
                val floats = FloatArray(pcmShorts.size) { i -> pcmShorts[i] / 32768.0f }
                if (saveDiagnostics) {
                    kotlin.runCatching {
                        val diagName = "asr_input_pcm_${sessionId}_${System.currentTimeMillis()}.wav"
                        saveDiagnosticsWav(context, diagName, pcmShorts, defaultSampleRate, 1)
                    }
                }
                Pair(floats, defaultSampleRate)
            }
        } else {
            // Raw PCM16 path
            val (pcmShorts, _) = extractPcm16(audioBytes)
            if (saveDiagnostics) {
                // Save a diagnostics WAV to inspect contents later
                kotlin.runCatching {
                    val diagName = "asr_input_pcm_${sessionId}_${System.currentTimeMillis()}.wav"
                    saveDiagnosticsWav(context, diagName, pcmShorts, defaultSampleRate, 1)
                }
            }
            val floats = FloatArray(pcmShorts.size) { i -> pcmShorts[i] / 32768.0f }
            Pair(floats, defaultSampleRate)
        }

        // Ensure target sample rate if requested
        if (resampleToDefault && result.second != defaultSampleRate) {
            val (samples, srcRate) = result
            Log.w(TAG, "ASR input sampleRate=$srcRate; resampling to $defaultSampleRate for model compatibility")
            val resampled = resampleLinear(samples, srcRate, defaultSampleRate)
            if (saveDiagnostics) {
                // Save resampled diagnostics
                kotlin.runCatching {
                    val pcm = floatToPcm16(resampled)
                    val diagName = "asr_input_resampled_${sessionId}_${System.currentTimeMillis()}.wav"
                    saveDiagnosticsWav(context, diagName, pcm, defaultSampleRate, 1)
                }
            }
            result = Pair(resampled, defaultSampleRate)
        }

        return result
    }

    /**
     * Simple high-quality linear resampler for FloatArray audio.
     * Preserves endpoints and avoids aliasing for moderate ratios.
     */
    fun resampleLinear(input: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        if (srcRate == dstRate || input.isEmpty()) return input
        val ratio = dstRate.toDouble() / srcRate.toDouble()
        val outLen = kotlin.math.max(1, kotlin.math.floor(input.size * ratio).toInt())
        val out = FloatArray(outLen)
        var i = 0
        while (i < outLen) {
            val srcPos = i / ratio
            val idx = srcPos.toInt().coerceIn(0, input.size - 1)
            val frac = (srcPos - idx).toFloat()
            val s0 = input[idx]
            val s1 = if (idx + 1 < input.size) input[idx + 1] else s0
            out[i] = s0 + (s1 - s0) * frac
            i++
        }
        return out
    }

    /** Convert FloatArray [-1,1] to PCM16 shorts with clipping. */
    fun floatToPcm16(input: FloatArray): ShortArray {
        val out = ShortArray(input.size)
        for (i in input.indices) {
            var v = input[i]
            if (v > 1f) v = 1f
            if (v < -1f) v = -1f
            // Use standard conversion with proper rounding
            out[i] = (v * 32767.0f).toInt().toShort()
        }
        return out
    }
}