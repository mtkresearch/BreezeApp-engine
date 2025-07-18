// IBreezeAppEngineListener.aidl
package com.mtkresearch.breezeapp.edgeai;

import com.mtkresearch.breezeapp.edgeai.AIResponse;

/**
 * Callback interface for the BreezeAppEngineService to send responses back to the client.
 */
oneway interface IBreezeAppEngineListener {
    /**
     * Called when the service has a response to a request.
     * This may be called multiple times for a single request if the response is streamed.
     */
    void onResponse(in AIResponse response);
} 