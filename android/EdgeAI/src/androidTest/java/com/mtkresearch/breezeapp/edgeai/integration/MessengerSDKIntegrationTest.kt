package com.mtkresearch.breezeapp.edgeai.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtkresearch.breezeapp.edgeai.ChatMessage
import com.mtkresearch.breezeapp.edgeai.EdgeAI
import com.mtkresearch.breezeapp.edgeai.chatRequestWithHistory
import com.mtkresearch.breezeapp.edgeai.integration.helpers.SDKTestBase
import com.mtkresearch.breezeapp.edgeai.integration.helpers.SignalAppBreezeResponseParser
import com.mtkresearch.breezeapp.edgeai.integration.helpers.TestSystemPrompt
import com.mtkresearch.breezeapp.edgeai.integration.helpers.TestDataLoader
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.onEach

/**
 * Category 5: Integration Readiness Tests (EdgeAI SDK Version)
 *
 * Purpose: Verify end-to-end integration scenarios typical of Signal App.
 * Matches strictly with TDD Reference Plan definitions.
 */
@RunWith(AndroidJUnit4::class)
class MessengerSDKIntegrationTest : SDKTestBase() {

    // Load test data lazily
    private val testData by lazy { TestDataLoader.loadCategory5Data() }

    @Test
    fun integration_simulateSignalAppWorkflow() = runBlocking {
        logReport("========================================")
        logReport("Test 5.1: Signal App Workflow Simulation")
        logReport("========================================")
        
        val systemPrompt = TestSystemPrompt.FULL_PROMPT
        val steps = testData.workflowSimulation
        
        // Note: Workflow simulation strictly implies sequence, but often stateless between specific independent actions 
        // unless context is carried. Here we simulate "App Usage Session" where user does X then Y.
        
        steps.forEachIndexed { index, step ->
            logReport("\n--- Step ${index + 1}: ${step.stepName} ---")
            logReport("Input: ${step.input}")
            
            val history = mutableListOf<ChatMessage>()
            history.add(ChatMessage(role = "system", content = systemPrompt))
            history.add(ChatMessage(role = "user", content = step.input))
            
            val request = chatRequestWithHistory(messages = history)
            val responses = EdgeAI.chat(request).toList()
            assertTrue("Step ${index+1} should get response", responses.isNotEmpty())
            
            val output = responses.last().choices.first().message!!.content ?: ""
            logReport("Output: $output")
            
            val json = JSONObject(output)
            assertEquals("Step ${index+1} type match", step.expectedType, json.optString("type"))
            
            if (step.expectedRecipient != null) {
                assertEquals("Recipient match", step.expectedRecipient, json.optString("recipient"))
            }
            
            if (step.checkCompleteness) {
                // Ensure text or draft present based on type
                if (step.expectedType == "response") {
                    assertFalse("Text should not be empty", json.optString("text").isEmpty())
                } else if (step.expectedType == "draft") {
                    assertFalse("Draft message should not be empty", json.optString("draft_message").isEmpty())
                }
            }
            logReport("✅ Step ${index+1} PASSED")
        }
    }

    @Test
    fun integration_meetsResponseTimeRequirement() = runBlocking {
        logReport("========================================")
        logReport("Test 5.2: Response Time Requirement")
        logReport("========================================")
        
        val systemPrompt = TestSystemPrompt.FULL_PROMPT
        val scenarios = testData.latencyTests
        
        val ttftValues = mutableListOf<Long>()
        val totalTimeValues = mutableListOf<Long>()
        
        scenarios.forEach { scenario ->
            val input = scenario.input
            val history = mutableListOf<ChatMessage>()
            history.add(ChatMessage(role = "system", content = systemPrompt))
            
            // Build history based on JSON config
            if (scenario.useHistory && scenario.historyMessages != null) {
                val contextBuilder = StringBuilder()
                contextBuilder.append("Conversation History:\n")
                scenario.historyMessages.forEach { msg ->
                    contextBuilder.append("[${msg.sender}]: ${msg.body}\n")
                }
                contextBuilder.append("\nUser Command: $input")
                
                // Replace the simple input with the context-rich input
                history.add(ChatMessage(role = "user", content = contextBuilder.toString()))
                
            } else {
                history.add(ChatMessage(role = "user", content = input))
            }
            
            logReport("Executing: $input (History: ${scenario.useHistory})")
            
            var ttft: Long? = null
            val startTime = System.currentTimeMillis()
            val fullResponseBuilder = StringBuilder()
            
            val request = chatRequestWithHistory(messages = history, stream = true)
            val response = EdgeAI.chat(request)
                .onEach { chunk ->
                    if (ttft == null) {
                        ttft = System.currentTimeMillis() - startTime
                    }
                    // For streaming, content is in 'delta'; for non-streaming, in 'message'
                    // Safely check both or prefer delta for streaming
                    val content = chunk.choices.firstOrNull()?.let { choice ->
                        choice.delta?.content ?: choice.message?.content
                    }
                    
                    content?.let {
                        fullResponseBuilder.append(it)
                    }
                }
                .toList()
                
            val totalTime = System.currentTimeMillis() - startTime
            val finalOutput = fullResponseBuilder.toString()
            
            assertTrue("Should get response", response.isNotEmpty())
            
            if (ttft != null) {
                ttftValues.add(ttft!!)
                totalTimeValues.add(totalTime)
                logReport("TTFT: ${ttft}ms | Total: ${totalTime}ms")
            }
            logReport("Model Output: $finalOutput")
        }
        
        val avgTotalTime = totalTimeValues.average()
        val maxTotalTime = totalTimeValues.maxOrNull() ?: 0L
        val avgTtft = ttftValues.average()
        val maxTtft = ttftValues.maxOrNull() ?: 0L
        
        logReport("End-to-End Response Time Results:")
        logReport("Average Total: ${avgTotalTime}ms")
        logReport("Max Total: ${maxTotalTime}ms")
        logReport("Average TTFT: ${avgTtft}ms")
        logReport("Max TTFT: ${maxTtft}ms")
        
        // Assertions based on "Time To First Token" (Perceived Latency)
        assertTrue("Average TTFT must be <3000ms (got ${avgTtft}ms)", avgTtft < 3000)
        assertTrue("Max TTFT must be <5000ms (got ${maxTtft}ms)", maxTtft < 5000)
    }
    
    @Test
    fun integration_apiCompatibleWithSignalApp() = runBlocking {
        logReport("========================================")
        logReport("Test 5.3: API Compatibility")
        logReport("========================================")
        
        val systemPrompt = TestSystemPrompt.FULL_PROMPT
        val tests = testData.compatibilityTests
        
        tests.forEach { test ->
            val history = mutableListOf(
                 ChatMessage(role = "system", content = systemPrompt),
                 ChatMessage(role = "user", content = test.input)
            )
            logReport("\nInput: ${test.input}")

            val request = chatRequestWithHistory(messages = history)
            val responses = EdgeAI.chat(request).toList()
            val engineResponseText = responses.last().choices.first().message!!.content
            logReport("Output: $engineResponseText")
            
            if (test.expectValidSignalParsing) {
                val parsed = SignalAppBreezeResponseParser.parse(engineResponseText)
                assertNotNull("Signal Parser failed to parse output", parsed)
                
                if (test.expectedParsedType != null) {
                    assertEquals("Parsed type mismatch", test.expectedParsedType, parsed?.type)
                }

                when (parsed?.type) {
                    "response" -> {
                        assertFalse("Response text should not be empty", parsed.text.isEmpty())
                    }
                    "draft" -> {
                        // For drafts, text might be empty, we check draft_message instead
                        val signalParsedDraft = SignalAppBreezeResponseParser.parse(engineResponseText)
                        assertFalse("Draft message should not be empty", signalParsedDraft?.draftMessage.isNullOrEmpty())
                        assertFalse("Recipient should not be empty", signalParsedDraft?.recipient.isNullOrEmpty())
                    }
                    else -> {
                        fail("Unknown response type: ${parsed?.type}")
                    }
                }

                logReport("✅ Signal Parser successfully parsed as '${parsed?.type}'")
            }
        }
    }
}
