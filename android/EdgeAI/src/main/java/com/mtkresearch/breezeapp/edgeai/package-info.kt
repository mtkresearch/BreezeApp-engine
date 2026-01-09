/**
 * # EdgeAI SDK
 *
 * Type-safe Kotlin API for Android apps to access on-device AI capabilities.
 *
 * ## Quick Start
 *
 * ```kotlin
 * // 1. Initialize
 * EdgeAI.initializeAndWait(context)
 *
 * // 2. Use API
 * val request = chatRequest(prompt = "Hello")
 * EdgeAI.chat(request).collect { response ->
 *     println(response.choices.first().message?.content)
 * }
 *
 * // 3. Cleanup
 * EdgeAI.shutdown()
 * ```
 *
 * ## Core APIs
 *
 * ### Initialization
 * - [EdgeAI.initialize] - Initialize SDK (returns Result)
 * - [EdgeAI.initializeAndWait] - Initialize and wait (throws exception)
 * - [EdgeAI.shutdown] - Release resources
 *
 * ### AI Capabilities
 * - [EdgeAI.chat] - LLM chat completion (streaming & non-streaming)
 * - [EdgeAI.tts] - Text-to-speech conversion
 * - [EdgeAI.asr] - Speech-to-text transcription
 *
 * ### Helper Functions
 * - [chatRequest] - Build simple chat request
 * - [chatRequestWithHistory] - Build chat with conversation history
 * - [ttsRequest] - Build TTS request
 * - [asrRequest] - Build ASR request
 * - [conversation] - DSL for building message history
 *
 * ## Architecture
 *
 * ```
 * Your App → EdgeAI SDK → AIDL IPC → BreezeApp Engine → AI Models
 * ```
 *
 * EdgeAI handles:
 * - Type-safe Kotlin API
 * - Service connection management
 * - AIDL communication
 * - Streaming via Kotlin Flow
 * - Error handling
 *
 * ## Examples
 *
 * See [EdgeAIContractTest] for comprehensive usage examples covering:
 * - Basic chat completion
 * - Streaming responses
 * - Multi-turn conversations
 * - Error handling
 * - Cancellation
 *
 * ## Error Handling
 *
 * All API calls may throw [EdgeAIException] or its subclasses:
 * - [ServiceConnectionException] - BreezeApp Engine unavailable
 * - [InvalidRequestException] - Invalid parameters
 * - [TimeoutException] - Request timeout
 * - [NetworkException] - Network issues
 *
 * ## Prerequisites
 *
 * - BreezeApp Engine must be installed on the device
 * - Minimum Android SDK: 34
 *
 * @see EdgeAI Main SDK object
 * @see EdgeAIContractTest Usage examples
 */
package com.mtkresearch.breezeapp.edgeai
