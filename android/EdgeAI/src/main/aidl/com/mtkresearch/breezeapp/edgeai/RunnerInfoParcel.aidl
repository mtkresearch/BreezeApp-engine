package com.mtkresearch.breezeapp.edgeai;

/**
 * Parcelable data class for runner information
 * Used for IPC communication between EdgeAI SDK and Engine Service
 */
parcelable RunnerInfoParcel {
    String name;
    boolean supportsStreaming;
    String[] capabilities;
    String vendor;
}
