package com.mtkresearch.breezeapp.engine.service

import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.mtkresearch.breezeapp.edgeai.IBreezeAppEngineService
import com.mtkresearch.breezeapp.edgeai.IBreezeAppEngineListener
import com.mtkresearch.breezeapp.edgeai.ChatRequest
import com.mtkresearch.breezeapp.edgeai.TTSRequest
import com.mtkresearch.breezeapp.edgeai.ASRRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * EngineServiceBinder - Clean AIDL Interface Implementation
 * 
 * This class handles ONLY the AIDL interface implementation, following Single Responsibility Principle.
 * It delegates all business logic to appropriate use case components and focuses solely on:
 * - AIDL method implementation
 * - Input validation
 * - Coroutine management for async operations
 * 
 * ## Architecture Benefits
 * - Single Responsibility: Only AIDL interface concerns
 * - Testable: Can be unit tested independently of Android Service
 * - Clean: No Android Service lifecycle concerns
 * - Focused: Clear separation from business logic
 */
class EngineServiceBinder(
    private val clientManager: ClientManager,
    private val requestCoordinator: RequestCoordinator
) {
    companion object {
        private const val TAG = "EngineServiceBinder"
        private const val API_VERSION = 1
    }
    
    // Coroutine scope for async operations
    private val binderJob = SupervisorJob()
    private val binderScope = CoroutineScope(Dispatchers.IO + binderJob)
    
    // AIDL Binder Implementation
    private val binder = object : IBreezeAppEngineService.Stub() {
        
        /**
         * Returns the current API version of the BreezeApp Engine Service.
         */
        override fun getApiVersion(): Int {
            Log.d(TAG, "getApiVersion() called")
            return API_VERSION
        }
        
        /**
         * Processes a chat completion request asynchronously.
         */
        override fun sendChatRequest(requestId: String?, request: ChatRequest?) {
            Log.d(TAG, "sendChatRequest() called: requestId=$requestId")
            
            // Input validation
            if (requestId == null || request == null) {
                Log.w(TAG, "sendChatRequest received null parameters")
                clientManager.notifyError(requestId ?: "unknown", "Invalid request parameters")
                return
            }
            
            // Delegate to request coordinator asynchronously
            binderScope.launch {
                try {
                    requestCoordinator.processChatRequest(requestId, request)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing chat request: $requestId", e)
                    clientManager.notifyError(requestId, "Chat request processing failed: ${e.message}")
                }
            }
        }
        
        /**
         * Converts text to speech and returns audio data.
         */
        override fun sendTTSRequest(requestId: String?, request: TTSRequest?) {
            Log.d(TAG, "sendTTSRequest() called: requestId=$requestId")
            
            // Input validation
            if (requestId == null || request == null) {
                Log.w(TAG, "sendTTSRequest received null parameters")
                clientManager.notifyError(requestId ?: "unknown", "Invalid request parameters")
                return
            }
            
            // Delegate to request coordinator asynchronously
            binderScope.launch {
                try {
                    requestCoordinator.processTTSRequest(requestId, request)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing TTS request: $requestId", e)
                    clientManager.notifyError(requestId, "TTS request processing failed: ${e.message}")
                }
            }
        }
        
        /**
         * Converts speech to text and returns transcription.
         */
        override fun sendASRRequest(requestId: String?, request: ASRRequest?) {
            Log.d(TAG, "sendASRRequest() called: requestId=$requestId")
            
            // Input validation
            if (requestId == null || request == null) {
                Log.w(TAG, "sendASRRequest received null parameters")
                clientManager.notifyError(requestId ?: "unknown", "Invalid request parameters")
                return
            }
            
            // Delegate to request coordinator asynchronously
            binderScope.launch {
                try {
                    requestCoordinator.processASRRequest(requestId, request)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing ASR request: $requestId", e)
                    clientManager.notifyError(requestId, "ASR request processing failed: ${e.message}")
                }
            }
        }
        
        /**
         * Cancels an active request by its ID.
         */
        override fun cancelRequest(requestId: String?): Boolean {
            Log.d(TAG, "cancelRequest() called: $requestId")
            
            if (requestId == null) {
                Log.w(TAG, "cancelRequest received null requestId")
                return false
            }
            
            return try {
                // Delegate to request coordinator for actual cancellation
                requestCoordinator.cancelRequest(requestId)
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling request: $requestId", e)
                false
            }
        }
        
        /**
         * Registers a listener to receive AI processing responses.
         */
        override fun registerListener(listener: IBreezeAppEngineListener?) {
            Log.d(TAG, "registerListener() called")
            
            if (listener != null) {
                try {
                    clientManager.registerListener(listener)
                    Log.d(TAG, "Client listener registered successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error registering listener", e)
                }
            } else {
                Log.w(TAG, "registerListener received null listener")
            }
        }
        
        /**
         * Unregisters a previously registered listener.
         */
        override fun unregisterListener(listener: IBreezeAppEngineListener?) {
            Log.d(TAG, "unregisterListener() called")
            
            if (listener != null) {
                try {
                    clientManager.unregisterListener(listener)
                    Log.d(TAG, "Client listener unregistered successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error unregistering listener", e)
                }
            } else {
                Log.w(TAG, "unregisterListener received null listener")
            }
        }
        
        /**
         * Checks if the service has a specific capability.
         */
        override fun hasCapability(capabilityName: String?): Boolean {
            Log.d(TAG, "hasCapability() called: $capabilityName")
            return true // Simple implementation - assume all capabilities available
        }
        
    }
    
    /**
     * Get the IBinder for service binding.
     */
    fun getBinder(): IBinder = binder
    
    /**
     * Cleanup resources when service is destroyed.
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up EngineServiceBinder")
        binderJob.cancel()
    }
}