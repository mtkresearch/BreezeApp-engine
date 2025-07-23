package com.mtkresearch.breezeapp.engine.core

import android.util.Log

/**
 * A simple Android logging implementation that uses the standard Android Log class.
 * This provides a clean, single-point logging solution for the BreezeApp engine.
 */
object Logger {
    fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
} 