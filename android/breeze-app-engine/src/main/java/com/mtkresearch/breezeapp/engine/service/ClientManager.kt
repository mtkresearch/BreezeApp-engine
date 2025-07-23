package com.mtkresearch.breezeapp.engine.service

import android.os.RemoteCallbackList
import android.os.RemoteException
import android.util.Log
import com.mtkresearch.breezeapp.edgeai.IBreezeAppEngineListener
import com.mtkresearch.breezeapp.edgeai.AIResponse
import java.util.concurrent.atomic.AtomicLong

/**
 * ClientManager - Handles Client Connection Management
 * 
 * This class manages client connections and notifications, following Single Responsibility Principle.
 * It handles ONLY client-related concerns:
 * - Client listener registration/unregistration
 * - Client notification delivery
 * - Client lifecycle tracking
 * - Client timeout management
 * 
 * ## Architecture Benefits
 * - Single Responsibility: Only client management
 * - Thread-Safe: Proper handling of RemoteCallbackList
 * - Clean: No business logic or Android Service concerns
 * - Testable: Can be unit tested independently
 */
class ClientManager {
    companion object {
        private const val TAG = "ClientManager"
        private const val CLIENT_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
    }
    
    // Thread-safe client listener management
    private val listeners = RemoteCallbackList<IBreezeAppEngineListener>()
    private val lastClientActivity = AtomicLong(System.currentTimeMillis())
    
    /**
     * Register a client listener to receive AI processing responses.
     */
    fun registerListener(listener: IBreezeAppEngineListener) {
        try {
            listeners.register(listener)
            lastClientActivity.set(System.currentTimeMillis())
            
            val clientCount = listeners.registeredCallbackCount
            Log.d(TAG, "Client listener registered. Total clients: $clientCount")
            
            // Notify about client count change if needed
            onClientCountChanged(clientCount)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error registering client listener", e)
            throw e
        }
    }
    
    /**
     * Unregister a client listener.
     */
    fun unregisterListener(listener: IBreezeAppEngineListener) {
        try {
            val wasRegistered = listeners.unregister(listener)
            
            if (wasRegistered) {
                val clientCount = listeners.registeredCallbackCount
                Log.d(TAG, "Client listener unregistered. Total clients: $clientCount")
                
                // Notify about client count change if needed
                onClientCountChanged(clientCount)
            } else {
                Log.w(TAG, "Attempted to unregister listener that was not registered")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering client listener", e)
            throw e
        }
    }
    
    /**
     * Notify all registered clients with any response.
     */
    fun notifyResponse(response: AIResponse) {
        notifyClients { listener ->
            listener.onResponse(response)
        }
    }
    
    /**
     * Notify all registered clients with a chat response.
     */
    fun notifyChatResponse(response: AIResponse) {
        notifyResponse(response)
    }
    
    /**
     * Notify all registered clients with a TTS response.
     */
    fun notifyTTSResponse(response: AIResponse) {
        notifyResponse(response)
    }
    
    /**
     * Notify all registered clients with an ASR response.
     */
    fun notifyASRResponse(response: AIResponse) {
        notifyResponse(response)
    }
    
    /**
     * Notify all registered clients about an error.
     */
    fun notifyError(requestId: String, errorMessage: String) {
        Log.w(TAG, "Notifying clients of error for request $requestId: $errorMessage")
        
        val errorResponse = AIResponse(
            requestId = requestId,
            text = "",
            isComplete = true,
            state = AIResponse.ResponseState.ERROR,
            error = errorMessage
        )
        
        // Send error as chat response (most common case)
        notifyChatResponse(errorResponse)
    }
    
    /**
     * Get the current number of registered clients.
     */
    fun getClientCount(): Int {
        return listeners.registeredCallbackCount
    }
    
    /**
     * Check if there are any active clients.
     */
    fun hasActiveClients(): Boolean {
        return listeners.registeredCallbackCount > 0
    }
    
    /**
     * Get the time of last client activity.
     */
    fun getLastClientActivity(): Long {
        return lastClientActivity.get()
    }
    
    /**
     * Check if clients have been inactive for too long.
     */
    fun isClientTimeoutReached(): Boolean {
        val timeSinceLastActivity = System.currentTimeMillis() - lastClientActivity.get()
        return timeSinceLastActivity > CLIENT_TIMEOUT_MS
    }
    
    /**
     * Update client activity timestamp.
     */
    fun updateClientActivity() {
        lastClientActivity.set(System.currentTimeMillis())
    }
    
    /**
     * Cleanup all client connections.
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up ClientManager")
        try {
            listeners.kill()
            Log.d(TAG, "All client listeners cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error during client cleanup", e)
        }
    }
    
    // Private helper methods
    
    /**
     * Generic method to notify all clients with proper error handling.
     */
    private fun notifyClients(action: (IBreezeAppEngineListener) -> Unit) {
        try {
            val count = listeners.beginBroadcast()
            
            for (i in 0 until count) {
                try {
                    val listener = listeners.getBroadcastItem(i)
                    action(listener)
                } catch (e: RemoteException) {
                    Log.w(TAG, "Error notifying client $i (client may have disconnected)", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error notifying client $i", e)
                }
            }
            
            listeners.finishBroadcast()
            
            // Update activity timestamp when we successfully notify clients
            if (count > 0) {
                updateClientActivity()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in client notification broadcast", e)
        }
    }
    
    /**
     * Called when client count changes. Can be overridden for custom behavior.
     */
    private fun onClientCountChanged(newCount: Int) {
        Log.d(TAG, "Client count changed to: $newCount")
        // This could trigger status updates, notifications, etc.
        // For now, just logging - can be extended as needed
    }
}