package com.mtkresearch.breezeapp.edgeai

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler

/**
 * 簡化版 AIResponse - 內部服務回應格式
 * 
 * 這是 BreezeApp Engine Service 返回的標準化回應格式，
 * 在簡化架構中直接轉換為標準化 API 格式。
 */
@Parcelize
@TypeParceler<AIResponse.ResponseState, ResponseStateParceler>()
data class AIResponse(
    val requestId: String,
    val text: String,
    val isComplete: Boolean,
    val state: ResponseState,
    val error: String? = null,
    val audioData: ByteArray? = null,  // For TTS responses
    // chunk streaming meta
    val chunkIndex: Int = 0,
    val isLastChunk: Boolean = true,
    val format: String = "pcm16",
    val sampleRate: Int = 16000,
    val channels: Int = 1,
    val bitDepth: Int = 16,
    val durationMs: Int = 0
) : Parcelable {

    /**
     * 回應狀態
     */
    enum class ResponseState {
        PROCESSING,
        STREAMING,
        COMPLETED,
        ERROR
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AIResponse

        if (requestId != other.requestId) return false
        if (text != other.text) return false
        if (isComplete != other.isComplete) return false
        if (state != other.state) return false
        if (error != other.error) return false
        if (audioData != null) {
            if (other.audioData == null) return false
            if (!audioData.contentEquals(other.audioData)) return false
        } else if (other.audioData != null) return false
        if (chunkIndex != other.chunkIndex) return false
        if (isLastChunk != other.isLastChunk) return false
        if (format != other.format) return false
        if (sampleRate != other.sampleRate) return false
        if (channels != other.channels) return false
        if (bitDepth != other.bitDepth) return false
        if (durationMs != other.durationMs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = requestId.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + isComplete.hashCode()
        result = 31 * result + state.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + (audioData?.contentHashCode() ?: 0)
        result = 31 * result + chunkIndex
        result = 31 * result + isLastChunk.hashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channels
        result = 31 * result + bitDepth
        result = 31 * result + durationMs
        return result
    }
}

/**
 * ResponseState 的 Parceler
 */
object ResponseStateParceler : Parceler<AIResponse.ResponseState> {
    override fun create(parcel: Parcel): AIResponse.ResponseState {
        val name = parcel.readString() ?: return AIResponse.ResponseState.ERROR
        return try {
            AIResponse.ResponseState.valueOf(name)
        } catch (e: IllegalArgumentException) {
            AIResponse.ResponseState.ERROR
        }
    }

    override fun AIResponse.ResponseState.write(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
    }
} 