package com.mtkresearch.breezeapp.edgeai

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.File

/**
 * Request for Automatic Speech Recognition
 * Based on industry-standard API specifications
 */
@Parcelize
data class ASRRequest(
    private val _file: ByteArray,
    val model: String,
    val language: String? = null,
    val prompt: String? = null,
    val responseFormat: String? = "json",
    val include: List<String>? = null,
    val stream: Boolean? = false,
    val temperature: Float? = 0f,
    val timestampGranularities: List<String>? = listOf("segment"),
    val metadata: Map<String, String>? = null
) : Parcelable {
    private val internalFile: ByteArray = _file.copyOf() // defensive copy
    val file: ByteArray get() = internalFile.copyOf() // always return a copy

    init {
        val supportedFormats = listOf("json", "text", "srt", "verbose_json", "vtt")
        require(responseFormat == null || responseFormat in supportedFormats) {
            "Response format must be one of: ${supportedFormats.joinToString()}"
        }

        require(temperature == null || temperature in 0f..1f) {
            "Temperature must be between 0.0 and 1.0"
        }

        val supportedGranularities = listOf("word", "segment")
        timestampGranularities?.forEach { granularity ->
            require(granularity in supportedGranularities) {
                "Timestamp granularity must be one of: ${supportedGranularities.joinToString()}"
            }
        }

        if (timestampGranularities?.contains("word") == true) {
            require(responseFormat == "verbose_json") {
                "Timestamp granularity 'word' requires response_format='verbose_json'"
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ASRRequest

        if (!internalFile.contentEquals(other.internalFile)) return false
        if (model != other.model) return false
        if (language != other.language) return false
        if (prompt != other.prompt) return false
        if (responseFormat != other.responseFormat) return false
        if (include != other.include) return false
        if (stream != other.stream) return false
        if (temperature != other.temperature) return false
        if (timestampGranularities != other.timestampGranularities) return false
        if (metadata != other.metadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result = internalFile.contentHashCode()
        result = 31 * result + model.hashCode()
        result = 31 * result + (language?.hashCode() ?: 0)
        result = 31 * result + (prompt?.hashCode() ?: 0)
        result = 31 * result + (responseFormat?.hashCode() ?: 0)
        result = 31 * result + (include?.hashCode() ?: 0)
        result = 31 * result + (stream?.hashCode() ?: 0)
        result = 31 * result + (temperature?.hashCode() ?: 0)
        result = 31 * result + (timestampGranularities?.hashCode() ?: 0)
        result = 31 * result + (metadata?.hashCode() ?: 0)
        return result
    }
}

/**
 * Response from speech recognition
 * The exact structure depends on the responseFormat parameter
 */
@Parcelize
data class ASRResponse(
    /**
     * The transcribed text (always present regardless of format)
     */
    val text: String,
    
    /**
     * Detailed segments (only present in verbose_json format)
     */
    val segments: List<TranscriptionSegment>? = null,
    
    /**
     * Detected language (only present in verbose_json format)
     */
    val language: String? = null,
    
    /**
     * Raw response in the requested format (text, srt, vtt, etc.)
     */
    val rawResponse: String? = null,
    
    /**
     * Whether this is a streaming chunk (true) or final response (false)
     */
    val isChunk: Boolean = false,

    /**
     * Performance metrics from the engine
     */
    val metrics: Map<String, String>? = null
) : Parcelable

/**
 * Transcription segment for verbose_json format
 */
@Parcelize
data class TranscriptionSegment(
    /**
     * Segment identifier
     */
    val id: Int,
    
    /**
     * Seek position
     */
    val seek: Int,
    
    /**
     * Start time in seconds
     */
    val start: Float,
    
    /**
     * End time in seconds
     */
    val end: Float,
    
    /**
     * Transcribed text for this segment
     */
    val text: String,
    
    /**
     * Token IDs
     */
    val tokens: List<Int>? = null,
    
    /**
     * Temperature used for this segment
     */
    val temperature: Float? = null,
    
    /**
     * Average log probability
     */
    val avgLogprob: Float? = null,
    
    /**
     * Compression ratio
     */
    val compressionRatio: Float? = null,
    
    /**
     * No speech probability
     */
    val noSpeechProb: Float? = null,
    
    /**
     * Word-level timestamps (only when timestampGranularities includes "word")
     */
    val words: List<WordTimestamp>? = null
) : Parcelable

/**
 * Word-level timestamp information
 */
@Parcelize
data class WordTimestamp(
    /**
     * The word
     */
    val word: String,
    
    /**
     * Start time of the word in seconds
     */
    val start: Float,
    
    /**
     * End time of the word in seconds
     */
    val end: Float
) : Parcelable 