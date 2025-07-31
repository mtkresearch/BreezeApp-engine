package com.mtkresearch.breezeapp.engine.model

/**
 * Permission State - Data model for permission status
 * 
 * This data class represents the current state of all required permissions
 * for the BreezeApp Engine service.
 */
data class PermissionState(
    val notificationGranted: Boolean,
    val microphoneGranted: Boolean,
    val overlayGranted: Boolean
) {
    /**
     * Returns true if all required permissions are granted
     */
    fun isAllGranted(): Boolean = 
        notificationGranted && microphoneGranted && overlayGranted
    
    /**
     * Returns the number of granted permissions
     */
    fun getGrantedCount(): Int = 
        listOf(notificationGranted, microphoneGranted, overlayGranted)
            .count { it }
    
    /**
     * Returns the total number of required permissions
     */
    fun getTotalCount(): Int = 3
    
    /**
     * Returns a list of missing permissions
     */
    fun getMissingPermissions(): List<String> {
        val missing = mutableListOf<String>()
        if (!notificationGranted) missing.add("Notification")
        if (!microphoneGranted) missing.add("Microphone")
        if (!overlayGranted) missing.add("Overlay")
        return missing
    }
    
    companion object {
        /**
         * Creates a PermissionState with all permissions granted
         */
        fun allGranted() = PermissionState(
            notificationGranted = true,
            microphoneGranted = true,
            overlayGranted = true
        )
        
        /**
         * Creates a PermissionState with all permissions denied
         */
        fun allDenied() = PermissionState(
            notificationGranted = false,
            microphoneGranted = false,
            overlayGranted = false
        )
    }
} 