package com.mtkresearch.breezeapp.engine.util

/**
 * Simple utility for collecting timing metrics during test execution.
 * 
 * Usage:
 * ```
 * val collector = MetricsCollector()
 * collector.mark("request_sent")
 * // ... do work ...
 * collector.mark("response_received")
 * val duration = collector.duration("request_sent", "response_received")
 * ```
 */
class MetricsCollector {
    private val marks = mutableMapOf<String, Long>()
    
    /**
     * Record a timestamp for the given event name.
     */
    fun mark(event: String) {
        marks[event] = System.currentTimeMillis()
    }
    
    /**
     * Calculate duration between two events.
     * Returns null if either event wasn't marked.
     */
    fun duration(from: String, to: String): Long? {
        val start = marks[from] ?: return null
        val end = marks[to] ?: return null
        return end - start
    }
    
    /**
     * Get the timestamp for a specific event.
     * Returns null if event wasn't marked.
     */
    fun get(event: String): Long? = marks[event]
    
    /**
     * Clear all recorded marks.
     */
    fun clear() {
        marks.clear()
    }
}
