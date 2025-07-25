package com.mtkresearch.breezeapp.engine.system

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Simple global library tracker stub for compilation.
 */
object GlobalLibraryTracker {
    private const val TAG = "GlobalLibraryTracker"
    private const val PREFS_NAME = "global_library_state"
    private const val KEY_INFERENCE_ACTIVE = "inference_active"
    private const val KEY_FORCE_STOP_COUNT = "force_stop_count"
    private const val KEY_LAST_LOAD_TIME = "last_load_time"
    private const val KEY_LAST_INFERENCE_TIME = "last_inference_time"
    private const val KEY_PROCESS_ID = "process_id"
    
    private var prefs: SharedPreferences? = null
    private var isLibraryActuallyLoaded = false
    
    // 使用 AtomicBoolean 來追蹤當前進程的推理狀態
    private val currentProcessInferenceActive = AtomicBoolean(false)
    
    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // 不立即測試庫，而是在需要時再測試
        isLibraryActuallyLoaded = false
        Log.d(TAG, "GlobalLibraryTracker initialized (library test deferred)")
    }
    
    private fun testLibraryLoaded(): Boolean {
        return try {
            // 測試 Sherpa ONNX 庫而不是 LLM 庫
            System.loadLibrary("sherpa-onnx-jni")
            true
        } catch (e: UnsatisfiedLinkError) {
            val errorMsg = e.message?.lowercase() ?: ""
            errorMsg.contains("already loaded")
        } catch (e: Exception) {
            false
        }
    }
    
    fun markInferenceStarted() {
        val currentTime = System.currentTimeMillis()
        val currentProcessId = android.os.Process.myPid()
        
        // 設置當前進程的推理狀態
        currentProcessInferenceActive.set(true)
        
        prefs?.edit()?.apply {
            putBoolean(KEY_INFERENCE_ACTIVE, true)
            putLong(KEY_LAST_INFERENCE_TIME, currentTime)
            putInt(KEY_PROCESS_ID, currentProcessId)
        }?.apply()
        Log.d(TAG, "Inference started at $currentTime in process $currentProcessId")
    }
    
    fun markInferenceCompleted() {
        // 清除當前進程的推理狀態
        currentProcessInferenceActive.set(false)
        
        prefs?.edit()?.putBoolean(KEY_INFERENCE_ACTIVE, false)?.apply()
        Log.d(TAG, "Inference completed in process ${android.os.Process.myPid()}")
    }
    
    /**
     * 重置推理狀態 - 用於處理異常情況
     */
    fun resetInferenceState() {
        currentProcessInferenceActive.set(false)
        prefs?.edit()?.putBoolean(KEY_INFERENCE_ACTIVE, false)?.apply()
        Log.d(TAG, "Inference state reset")
    }
    
    /**
     * 檢查是否是當前進程的推理狀態
     */
    private fun isCurrentProcessInference(): Boolean {
        val storedProcessId = prefs?.getInt(KEY_PROCESS_ID, -1) ?: -1
        val currentProcessId = android.os.Process.myPid()
        return storedProcessId == currentProcessId
    }
    
    /**
     * 檢查推理是否真的在進行中
     */
    private fun isInferenceActuallyActive(): Boolean {
        // 如果當前進程的推理狀態為 false，說明推理已完成
        if (!currentProcessInferenceActive.get()) {
            return false
        }
        
        // 檢查是否是當前進程的推理
        if (!isCurrentProcessInference()) {
            // 如果不是當前進程，說明是其他進程的殘留狀態
            Log.w(TAG, "Inference state from different process, resetting")
            resetInferenceState()
            return false
        }
        
        return true
    }
    
    fun isLibraryUsable(): Boolean {
        // 在需要時測試庫是否已載入
        if (!isLibraryActuallyLoaded) {
            isLibraryActuallyLoaded = testLibraryLoaded()
        }
        
        val wasInferenceActive = prefs?.getBoolean(KEY_INFERENCE_ACTIVE, false) ?: false
        
        // 如果 SharedPreferences 顯示推理活躍，但實際檢查發現不是，則重置
        if (wasInferenceActive && !isInferenceActuallyActive()) {
            Log.w(TAG, "Inference state mismatch detected, auto-resetting")
            resetInferenceState()
            return isLibraryActuallyLoaded
        }
        
        val result = isLibraryActuallyLoaded && !wasInferenceActive
        Log.d(TAG, "Library usable check: loaded=$isLibraryActuallyLoaded, inferenceActive=$wasInferenceActive, result=$result")
        return result
    }
    
    fun getDiagnosticInfo(): Map<String, Any> {
        val lastInferenceTime = prefs?.getLong(KEY_LAST_INFERENCE_TIME, 0) ?: 0
        val currentTime = System.currentTimeMillis()
        val storedProcessId = prefs?.getInt(KEY_PROCESS_ID, -1) ?: -1
        val currentProcessId = android.os.Process.myPid()
        
        return mapOf(
            "isLibraryActuallyLoaded" to isLibraryActuallyLoaded,
            "wasInferenceActive" to (prefs?.getBoolean(KEY_INFERENCE_ACTIVE, false) ?: false),
            "currentProcessInferenceActive" to currentProcessInferenceActive.get(),
            "storedProcessId" to storedProcessId,
            "currentProcessId" to currentProcessId,
            "isCurrentProcessInference" to isCurrentProcessInference(),
            "isInferenceActuallyActive" to isInferenceActuallyActive(),
            "forceStopCount" to (prefs?.getInt(KEY_FORCE_STOP_COUNT, 0) ?: 0),
            "lastLoadTime" to (prefs?.getLong(KEY_LAST_LOAD_TIME, 0) ?: 0),
            "lastInferenceTime" to lastInferenceTime,
            "timeSinceLastInference" to (currentTime - lastInferenceTime)
        )
    }
}