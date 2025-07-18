package com.mtkresearch.breezeapp.edgeai

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

/**
 * Performance and Concurrency Tests for EdgeAI SDK
 * 
 * These tests verify that the EdgeAI SDK performs well under load,
 * handles concurrency correctly, and maintains performance characteristics.
 * 
 * As a Senior Android Architect, this approach:
 * 1. Validates performance under various load conditions
 * 2. Tests thread safety and concurrent access patterns
 * 3. Ensures memory efficiency and leak prevention
 * 4. Verifies scalability characteristics
 */
@RunWith(MockitoJUnitRunner::class)
class EdgeAIPerformanceTest {

    @Before
    fun setUp() {
        EdgeAI.shutdown()
    }

    @After
    fun tearDown() {
        EdgeAI.shutdown()
    }

    // === OBJECT CREATION PERFORMANCE TESTS ===

    @Test
    fun `test request object creation performance`() {
        val iterations = 10000
        
        // Test ChatRequest creation performance
        val chatCreationTime = measureTimeMillis {
            repeat(iterations) { i ->
                ChatRequest(
                    model = "gpt-4",
                    messages = listOf(
                        ChatMessage(role = "user", content = "Message $i")
                    ),
                    temperature = 0.7f
                )
            }
        }
        
        // Test TTSRequest creation performance
        val ttsCreationTime = measureTimeMillis {
            repeat(iterations) { i ->
                TTSRequest(
                    input = "Text to speech $i",
                    model = "tts-1",
                    voice = "alloy"
                )
            }
        }
        
        // Test ASRRequest creation performance
        val asrCreationTime = measureTimeMillis {
            repeat(iterations) { i ->
                ASRRequest(
                    byteArrayOf(i.toByte()),
                    model = "whisper-1"
                )
            }
        }
        
        // Performance assertions (should complete quickly)
        assertTrue("ChatRequest creation should be fast", chatCreationTime < 1000) // < 1 second for 10k objects
        assertTrue("TTSRequest creation should be fast", ttsCreationTime < 1000)
        assertTrue("ASRRequest creation should be fast", asrCreationTime < 1000)
        
        println("Performance Results:")
        println("  ChatRequest: ${chatCreationTime}ms for $iterations objects (${chatCreationTime.toDouble() / iterations}ms per object)")
        println("  TTSRequest: ${ttsCreationTime}ms for $iterations objects (${ttsCreationTime.toDouble() / iterations}ms per object)")
        println("  ASRRequest: ${asrCreationTime}ms for $iterations objects (${asrCreationTime.toDouble() / iterations}ms per object)")
    }

    @Test
    fun `test large object handling performance`() {
        // Test performance with large message lists
        val largeMessageList = (1..1000).map { i ->
            ChatMessage(role = if (i % 2 == 0) "user" else "assistant", content = "Message $i with some longer content to simulate real usage")
        }
        
        val largeRequestCreationTime = measureTimeMillis {
            repeat(100) {
                ChatRequest(
                    model = "gpt-4",
                    messages = largeMessageList,
                    temperature = 0.7f
                )
            }
        }
        
        // Test performance with large audio data
        val largeAudioData = ByteArray(1024 * 1024) { (it % 256).toByte() } // 1MB
        
        val largeAudioRequestTime = measureTimeMillis {
            repeat(10) {
                ASRRequest(
                    largeAudioData,
                    model = "whisper-1"
                )
            }
        }
        
        assertTrue("Large ChatRequest creation should be reasonable", largeRequestCreationTime < 2000)
        assertTrue("Large ASRRequest creation should be reasonable", largeAudioRequestTime < 1000)
        
        println("Large Object Performance:")
        println("  Large ChatRequest (1000 messages): ${largeRequestCreationTime}ms for 100 objects")
        println("  Large ASRRequest (1MB audio): ${largeAudioRequestTime}ms for 10 objects")
    }

    // === MEMORY EFFICIENCY TESTS ===

    @Test
    fun `test memory usage efficiency`() {
        val runtime = Runtime.getRuntime()
        
        // Force garbage collection and measure baseline
        System.gc()
        Thread.sleep(100)
        val baselineMemory = runtime.totalMemory() - runtime.freeMemory()
        
        // Create many objects
        val objects = mutableListOf<Any>()
        repeat(10000) { i ->
            objects.add(ChatRequest(
                model = "gpt-4",
                messages = listOf(ChatMessage(role = "user", content = "Message $i")),
                temperature = 0.7f
            ))
            
            objects.add(TTSRequest(
                input = "Text $i",
                model = "tts-1",
                voice = "alloy"
            ))
            
            objects.add(ASRRequest(
                byteArrayOf(i.toByte()),
                model = "whisper-1"
            ))
        }
        
        val afterCreationMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryUsed = afterCreationMemory - baselineMemory
        
        // Clear references and force GC
        objects.clear()
        System.gc()
        Thread.sleep(100)
        val afterGCMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryReclaimed = afterCreationMemory - afterGCMemory
        
        println("Memory Usage Analysis:")
        println("  Baseline: ${baselineMemory / 1024 / 1024}MB")
        println("  After creating 30k objects: ${afterCreationMemory / 1024 / 1024}MB")
        println("  Memory used: ${memoryUsed / 1024 / 1024}MB")
        println("  After GC: ${afterGCMemory / 1024 / 1024}MB")
        println("  Memory reclaimed: ${memoryReclaimed / 1024 / 1024}MB")
        
        // Memory usage should be reasonable
        assertTrue("Memory usage should be under 100MB for 30k objects", memoryUsed < 100 * 1024 * 1024)
        assertTrue("Should reclaim at least 50% of memory after GC", memoryReclaimed > memoryUsed * 0.5)
    }

    @Test
    fun `test memory leak prevention`() {
        val runtime = Runtime.getRuntime()
        
        // Baseline measurement
        System.gc()
        Thread.sleep(100)
        val baselineMemory = runtime.totalMemory() - runtime.freeMemory()
        
        // Simulate multiple SDK usage cycles
        repeat(100) { cycle ->
            // Create and immediately discard objects
            val requests = (1..100).map { i ->
                ChatRequest(
                    model = "gpt-4",
                    messages = listOf(ChatMessage(role = "user", content = "Cycle $cycle Message $i"))
                )
            }
            
            // Simulate some processing
            requests.forEach { request ->
                request.toString() // Force object access
                request.copy(temperature = 0.8f) // Create copies
            }
            
            // Objects should be eligible for GC after this scope
        }
        
        // Force garbage collection
        System.gc()
        Thread.sleep(100)
        val finalMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryIncrease = finalMemory - baselineMemory
        
        println("Memory Leak Test:")
        println("  Baseline: ${baselineMemory / 1024 / 1024}MB")
        println("  Final: ${finalMemory / 1024 / 1024}MB")
        println("  Increase: ${memoryIncrease / 1024 / 1024}MB")
        
        // Should not have significant memory increase after GC
        assertTrue("Should not have memory leaks", memoryIncrease < 10 * 1024 * 1024) // Less than 10MB increase
    }

    // === CONCURRENCY TESTS ===

    @Test
    fun `test concurrent object creation thread safety`() = runTest {
        val createdObjects = ConcurrentHashMap<Int, ChatRequest>()
        val errorCount = AtomicInteger(0)
        
        // Create objects concurrently from multiple coroutines
        val jobs = (1..100).map { threadId ->
            launch {
                try {
                    repeat(100) { i ->
                        val request = ChatRequest(
                            model = "gpt-4",
                            messages = listOf(ChatMessage(role = "user", content = "Thread $threadId Message $i")),
                            temperature = (threadId * 0.01f) % 2.0f
                        )
                        createdObjects[threadId * 1000 + i] = request
                    }
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                }
            }
        }
        
        // Wait for all jobs to complete
        jobs.joinAll()
        
        // Verify results
        assertEquals("Should have no errors", 0, errorCount.get())
        assertEquals("Should have created all objects", 10000, createdObjects.size)
        
        // Verify object integrity
        createdObjects.values.forEach { request ->
            assertNotNull("Request should not be null", request)
            assertNotNull("Model should not be null", request.model)
            assertNotNull("Messages should not be null", request.messages)
            assertTrue("Should have at least one message", request.messages.isNotEmpty())
        }
    }

    @Test
    fun `test SDK state access thread safety`() = runTest {
        val results = ConcurrentHashMap<Int, Pair<Boolean, Boolean>>()
        val errorCount = AtomicInteger(0)
        
        // Access SDK state from multiple threads concurrently
        val jobs = (1..1000).map { threadId ->
            launch {
                try {
                    val isInitialized = EdgeAI.isInitialized()
                    val isReady = EdgeAI.isReady()
                    results[threadId] = Pair(isInitialized, isReady)
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                }
            }
        }
        
        jobs.joinAll()
        
        // Verify results
        assertEquals("Should have no errors", 0, errorCount.get())
        assertEquals("Should have results from all threads", 1000, results.size)
        
        // All results should be consistent (false, false since not initialized)
        results.values.forEach { (initialized, ready) ->
            assertFalse("Should not be initialized", initialized)
            assertFalse("Should not be ready", ready)
        }
    }

    @Test
    fun `test concurrent shutdown operations`() = runTest {
        val errorCount = AtomicInteger(0)
        
        // Call shutdown from multiple threads concurrently
        val jobs = (1..100).map {
            launch {
                try {
                    EdgeAI.shutdown()
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                }
            }
        }
        
        jobs.joinAll()
        
        // Should handle concurrent shutdowns gracefully
        assertEquals("Concurrent shutdowns should not cause errors", 0, errorCount.get())
        assertFalse("Should be uninitialized after concurrent shutdowns", EdgeAI.isInitialized())
        assertFalse("Should not be ready after concurrent shutdowns", EdgeAI.isReady())
    }

    // === SCALABILITY TESTS ===

    @Test
    fun `test request validation performance at scale`() {
        val validationTimes = mutableListOf<Long>()
        
        // Test validation performance for different request sizes
        listOf(1, 10, 100, 1000).forEach { messageCount ->
            val messages = (1..messageCount).map { i ->
                ChatMessage(role = if (i % 2 == 0) "user" else "assistant", content = "Message $i")
            }
            
            val validationTime = measureTimeMillis {
                repeat(1000) {
                    try {
                        ChatRequest(
                            model = "gpt-4",
                            messages = messages,
                            temperature = 0.7f
                        )
                    } catch (e: Exception) {
                        // Validation should not throw for valid data
                        fail("Validation should not fail for valid data: ${e.message}")
                    }
                }
            }
            
            validationTimes.add(validationTime)
            println("Validation performance for $messageCount messages: ${validationTime}ms for 1000 requests")
        }
        
        // Validation time should scale reasonably
        assertTrue("Validation should complete quickly even for large requests", 
                  validationTimes.all { it < 5000 }) // All should be under 5 seconds
    }

    @Test
    fun `test error handling performance`() {
        // Test that error handling doesn't significantly impact performance
        val errorHandlingTime = measureTimeMillis {
            repeat(10000) {
                try {
                    // This should throw ServiceConnectionException quickly
                    EdgeAI.chat(ChatRequest(
                        model = "test",
                        messages = listOf(ChatMessage(role = "user", content = "test"))
                    ))
                } catch (e: ServiceConnectionException) {
                    // Expected - measure how quickly this fails
                }
            }
        }
        
        println("Error handling performance: ${errorHandlingTime}ms for 10000 failed calls")
        assertTrue("Error handling should be fast", errorHandlingTime < 2000) // Should complete in under 2 seconds
        
        // Test exception creation performance
        val exceptionCreationTime = measureTimeMillis {
            repeat(10000) {
                ServiceConnectionException("Test error message $it")
                InvalidInputException("Validation error $it")
                InternalErrorException("Internal error $it")
            }
        }
        
        println("Exception creation performance: ${exceptionCreationTime}ms for 30000 exceptions")
        assertTrue("Exception creation should be fast", exceptionCreationTime < 1000)
    }

    // === STRESS TESTS ===

    @Test
    fun `test high frequency object creation stress test`() {
        val startTime = System.currentTimeMillis()
        val objectCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)
        
        // Create objects at high frequency for a short duration
        val duration = 5000L // 5 seconds
        
        while (System.currentTimeMillis() - startTime < duration) {
            try {
                ChatRequest(
                    model = "gpt-4",
                    messages = listOf(ChatMessage(role = "user", content = "Stress test ${objectCount.get()}")),
                    temperature = (objectCount.get() % 200) / 100.0f
                )
                objectCount.incrementAndGet()
            } catch (e: Exception) {
                errorCount.incrementAndGet()
            }
        }
        
        val actualDuration = System.currentTimeMillis() - startTime
        val objectsPerSecond = (objectCount.get() * 1000.0) / actualDuration
        
        println("Stress Test Results:")
        println("  Duration: ${actualDuration}ms")
        println("  Objects created: ${objectCount.get()}")
        println("  Errors: ${errorCount.get()}")
        println("  Objects per second: $objectsPerSecond")
        
        assertEquals("Should have no errors during stress test", 0, errorCount.get())
        assertTrue("Should create at least 1000 objects per second", objectsPerSecond > 1000)
    }

    @Test
    fun `test memory pressure handling`() {
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()
        
        // Create objects until we approach memory limits
        val objects = mutableListOf<Any>()
        var creationCount = 0
        
        try {
            while (creationCount < 100000) { // Limit to prevent OOM in test
                val currentMemory = runtime.totalMemory() - runtime.freeMemory()
                val memoryUsed = currentMemory - initialMemory
                
                // Stop if we're using too much memory
                if (memoryUsed > 200 * 1024 * 1024) { // 200MB limit
                    break
                }
                
                objects.add(ChatRequest(
                    model = "gpt-4",
                    messages = listOf(ChatMessage(role = "user", content = "Memory pressure test $creationCount")),
                    temperature = 0.7f
                ))
                
                creationCount++
                
                // Periodically check if we can still create objects
                if (creationCount % 1000 == 0) {
                    System.gc() // Suggest GC to free up space
                }
            }
        } catch (e: OutOfMemoryError) {
            fail("Should handle memory pressure gracefully, not throw OOM at $creationCount objects")
        }
        
        println("Memory Pressure Test:")
        println("  Created $creationCount objects before reaching memory limit")
        println("  Final memory usage: ${(runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024}MB")
        
        assertTrue("Should create at least 10000 objects before memory pressure", creationCount > 10000)
        
        // Clean up
        objects.clear()
        System.gc()
    }
}