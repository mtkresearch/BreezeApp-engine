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
    val durationMs: Int = 0,
    // Metrics transport
    val metrics: Map<String, String>? = null
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