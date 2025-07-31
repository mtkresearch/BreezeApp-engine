package com.mtkresearch.breezeapp.engine.core

/**
 * EngineConstants - Centralized configuration constants
 * 
 * This object contains all configuration constants used throughout
 * the BreezeApp Engine system, eliminating magic numbers and
 * hardcoded values.
 */
object EngineConstants {
    
    // Audio processing constants
    object Audio {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = 1 // MONO
        const val AUDIO_FORMAT = 2 // PCM_16BIT
        const val AUDIO_SOURCE = 1 // MIC
        
        // Buffer sizes
        const val CHUNK_INTERVAL_MS = 100L
        const val TAIL_PADDING_MS = 500L
        const val CANCELLATION_CHECK_DELAY_MS = 10L
        
        // Android 15+ delays
        const val FOREGROUND_SERVICE_DELAY_MS = 500L
    }
    
    // Request processing constants
    object Request {
        const val DEFAULT_TIMEOUT_MS = 10000L
        const val MAX_ACTIVE_REQUESTS = 10
        const val REQUEST_ID_PREFIX = "request-"
        const val STREAM_ID_PREFIX = "stream-"
    }
    
    // Service constants
    object Service {
        const val FOREGROUND_NOTIFICATION_ID = 1001
        const val PERMISSION = "com.mtkresearch.breezeapp.permission.BIND_AI_ROUTER_SERVICE"
        const val API_VERSION = 1
    }
    
    // Model constants
    object Model {
        const val DEFAULT_MODEL_TYPE = 0
        const val MODEL_PATH_PREFIX = "/data/local/tmp/models/"
    }
    
    // Error codes
    object ErrorCodes {
        const val RUNTIME_ERROR = "E101"
        const val MODEL_NOT_LOADED = "E201"
        const val INVALID_INPUT = "E301"
        const val PERMISSION_DENIED = "E401"
        const val RUNNER_NOT_FOUND = "E404"
        const val CAPABILITY_NOT_SUPPORTED = "E405"
        const val STREAMING_NOT_SUPPORTED = "E406"
        const val MODEL_LOAD_FAILED = "E501"
    }
} 