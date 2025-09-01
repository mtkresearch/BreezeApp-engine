# Streaming Implementation Guide

This guide provides comprehensive streaming patterns for BreezeApp Engine runners. Use these patterns to implement proper streaming callbacks in your runners.

## Overview

Streaming enables real-time AI responses for better user experience. Key benefits:
- **Low latency**: Progressive results as they're generated
- **Better UX**: Users see responses building up in real-time
- **Cancellation support**: Can stop long-running operations
- **Memory efficiency**: Process results incrementally

## Core Streaming Principles

### 1. Partial vs Complete Results
```kotlin
// CRITICAL: Use partial flag correctly
emit(InferenceResult.success(
    outputs = mapOf("text" to currentText),
    partial = true   // For intermediate results
))

emit(InferenceResult.success(
    outputs = mapOf("text" to finalText),
    partial = false  // ONLY for the final result
))
```

### 2. Progress Metadata
```kotlin
emit(InferenceResult.success(
    outputs = mapOf("text" to currentText),
    metadata = mapOf(
        "progress" to progressPercent,
        "tokens_generated" to tokenCount,
        "estimated_total" to totalEstimated
    ),
    partial = !isComplete
))
```

### 3. Error Handling
```kotlin
try {
    // Your streaming logic
} catch (e: Exception) {
    emit(InferenceResult.error(RunnerError("E102", "Streaming failed: ${e.message}")))
}
```

## LLM Streaming Patterns

### Pattern 1: API-Based Streaming (OpenAI, Anthropic, etc.)
```kotlin
private fun streamLLM(input: InferenceRequest): Flow<InferenceResult> = flow {
    val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
    val temperature = (input.params["temperature"] as? Number)?.toFloat() ?: 0.7f
    
    // Replace with your streaming API client
    llmClient?.streamGenerate(text, temperature) { chunk ->
        emit(InferenceResult.success(
            outputs = mapOf(InferenceResult.OUTPUT_TEXT to chunk.text),
            metadata = mapOf(
                "tokens_generated" to chunk.tokenCount,
                "finish_reason" to chunk.finishReason,
                "model_name" to chunk.modelName,
                "generation_speed" to chunk.tokensPerSecond
            ),
            partial = !chunk.isComplete
        ))
    }
}
```

### Pattern 2: Local Model Streaming (ExecuTorch, ONNX, etc.)
```kotlin
private fun streamLLMLocal(input: InferenceRequest): Flow<InferenceResult> = flow {
    val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
    var accumulatedText = ""
    var tokenCount = 0
    
    localModel?.generateStream(text) { token, isComplete ->
        accumulatedText += token
        tokenCount++
        
        emit(InferenceResult.success(
            outputs = mapOf(InferenceResult.OUTPUT_TEXT to accumulatedText),
            metadata = mapOf(
                "tokens_generated" to tokenCount,
                "current_token" to token,
                "confidence" to getTokenConfidence(token),
                "progress_percent" to getProgressPercent(tokenCount)
            ),
            partial = !isComplete
        ))
        
        // Check for cancellation
        kotlinx.coroutines.ensureActive()
    }
}
```

### Pattern 3: Sentence-by-Sentence Streaming
```kotlin
private fun streamLLMSentences(input: InferenceRequest): Flow<InferenceResult> = flow {
    val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
    
    llmClient?.generateBySentence(text) { sentence, isComplete, metadata ->
        emit(InferenceResult.success(
            outputs = mapOf(InferenceResult.OUTPUT_TEXT to sentence),
            metadata = mapOf(
                "sentence_index" to metadata.sentenceIndex,
                "total_sentences_estimated" to metadata.totalEstimated,
                "confidence" to metadata.confidence,
                "reasoning_complete" to metadata.reasoningComplete
            ),
            partial = !isComplete
        ))
    }
}
```

## ASR Streaming Patterns

### Pattern 1: Real-time Audio Processing
```kotlin
private fun streamASR(input: InferenceRequest): Flow<InferenceResult> = flow {
    val audio = input.inputs[InferenceRequest.INPUT_AUDIO] as? ByteArray ?: byteArrayOf()
    val language = input.params["language"] as? String ?: "en-US"
    
    asrClient?.streamTranscribe(audio, language) { partial ->
        emit(InferenceResult.success(
            outputs = mapOf(InferenceResult.OUTPUT_TEXT to partial.transcript),
            metadata = mapOf(
                "confidence" to partial.confidence,
                "is_final" to partial.isFinal,
                "words" to partial.words, // Word-level results with timing
                "audio_duration_processed_ms" to partial.audioDurationMs,
                "speaker_id" to partial.speakerId,
                "language_detected" to partial.detectedLanguage
            ),
            partial = !partial.isFinal
        ))
    }
}
```

### Pattern 2: Chunk-based Processing
```kotlin
private fun streamASRChunked(input: InferenceRequest): Flow<InferenceResult> = flow {
    val audio = input.inputs[InferenceRequest.INPUT_AUDIO] as? ByteArray ?: byteArrayOf()
    val chunkSize = 4096
    var accumulatedTranscript = ""
    
    for (i in audio.indices step chunkSize) {
        val chunk = audio.sliceArray(i until minOf(i + chunkSize, audio.size))
        val partialResult = asrClient?.processChunk(chunk)
        
        if (partialResult != null) {
            accumulatedTranscript += " " + partialResult.transcript
            
            emit(InferenceResult.success(
                outputs = mapOf(InferenceResult.OUTPUT_TEXT to accumulatedTranscript.trim()),
                metadata = mapOf(
                    "chunk_index" to (i / chunkSize),
                    "chunks_processed" to ((i / chunkSize) + 1),
                    "total_chunks" to (audio.size / chunkSize + 1),
                    "confidence" to partialResult.confidence,
                    "processing_time_ms" to partialResult.processingTimeMs
                ),
                partial = i + chunkSize < audio.size
            ))
        }
        
        // Add small delay to prevent overwhelming the client
        kotlinx.coroutines.delay(10)
    }
}
```

### Pattern 3: Word-level Streaming
```kotlin
private fun streamASRWords(input: InferenceRequest): Flow<InferenceResult> = flow {
    val audio = input.inputs[InferenceRequest.INPUT_AUDIO] as? ByteArray ?: byteArrayOf()
    
    asrClient?.streamTranscribeWords(audio) { wordResult ->
        emit(InferenceResult.success(
            outputs = mapOf(
                InferenceResult.OUTPUT_TEXT to wordResult.fullTranscript,
                "current_word" to wordResult.currentWord,
                "word_confidence" to wordResult.wordConfidence,
                "word_start_time" to wordResult.startTime,
                "word_end_time" to wordResult.endTime
            ),
            metadata = mapOf(
                "words_recognized" to wordResult.totalWords,
                "overall_confidence" to wordResult.overallConfidence,
                "is_final" to wordResult.isFinal,
                "alternative_words" to wordResult.alternatives
            ),
            partial = !wordResult.isFinal
        ))
    }
}
```

## TTS Streaming Patterns

### Pattern 1: Progressive Audio Generation
```kotlin
private fun streamTTS(input: InferenceRequest): Flow<InferenceResult> = flow {
    val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
    val voice = input.params["voice"] as? String ?: "default"
    
    ttsClient?.streamSynthesize(text, voice) { audioChunk, isComplete ->
        emit(InferenceResult.success(
            outputs = mapOf(
                InferenceResult.OUTPUT_AUDIO to audioChunk.data,
                "sample_rate" to audioChunk.sampleRate,
                "channels" to audioChunk.channels,
                "chunk_index" to audioChunk.index
            ),
            metadata = mapOf(
                "text_characters_processed" to audioChunk.textProcessed,
                "audio_duration_ms" to audioChunk.durationMs,
                "synthesis_progress" to audioChunk.progressPercent,
                "voice_used" to voice,
                "quality_level" to audioChunk.quality
            ),
            partial = !isComplete
        ))
    }
}
```

### Pattern 2: Sentence-based TTS
```kotlin
private fun streamTTSSentences(input: InferenceRequest): Flow<InferenceResult> = flow {
    val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
    val sentences = text.split(". ", "! ", "? ").filter { it.isNotBlank() }
    
    for ((index, sentence) in sentences.withIndex()) {
        val audioData = ttsClient?.synthesizeSentence(sentence)
        val isLast = index == sentences.size - 1
        
        if (audioData != null) {
            emit(InferenceResult.success(
                outputs = mapOf(
                    InferenceResult.OUTPUT_AUDIO to audioData.data,
                    "sample_rate" to audioData.sampleRate,
                    "channels" to audioData.channels
                ),
                metadata = mapOf(
                    "sentence_index" to index,
                    "total_sentences" to sentences.size,
                    "sentence_text" to sentence,
                    "audio_duration_ms" to audioData.durationMs,
                    "cumulative_duration_ms" to audioData.cumulativeDuration
                ),
                partial = !isLast
            ))
        }
        
        // Small delay between sentences for natural pacing
        kotlinx.coroutines.delay(100)
    }
}
```

## VLM Streaming Patterns

### Pattern 1: Progressive Analysis
```kotlin
private fun streamVLM(input: InferenceRequest): Flow<InferenceResult> = flow {
    val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
    val image = input.inputs[InferenceRequest.INPUT_IMAGE] as? ByteArray ?: byteArrayOf()
    
    vlmClient?.analyzeImageProgressive(image, text) { analysisStep ->
        emit(InferenceResult.success(
            outputs = mapOf(
                InferenceResult.OUTPUT_TEXT to analysisStep.currentAnalysis,
                "detected_objects" to analysisStep.objects,
                "scene_description" to analysisStep.sceneDescription
            ),
            metadata = mapOf(
                "analysis_step" to analysisStep.stepName,
                "confidence" to analysisStep.confidence,
                "processing_region" to analysisStep.processingRegion,
                "total_steps" to analysisStep.totalSteps,
                "current_step" to analysisStep.currentStep
            ),
            partial = !analysisStep.isComplete
        ))
    }
}
```

## Guardian Streaming Patterns

### Pattern 1: Progressive Safety Analysis
```kotlin
private fun streamGuardian(input: InferenceRequest): Flow<InferenceResult> = flow {
    val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
    
    guardianClient?.analyzeSafetyProgressive(text) { safetyStep ->
        emit(InferenceResult.success(
            outputs = mapOf(
                "safety_status" to safetyStep.currentStatus,
                "risk_score" to safetyStep.riskScore,
                "analysis_progress" to safetyStep.analysisProgress
            ),
            metadata = mapOf(
                "check_type" to safetyStep.checkType, // "toxicity", "harassment", etc.
                "confidence" to safetyStep.confidence,
                "detected_issues" to safetyStep.detectedIssues,
                "text_portion_analyzed" to safetyStep.textAnalyzed,
                "total_checks" to safetyStep.totalChecks,
                "completed_checks" to safetyStep.completedChecks
            ),
            partial = !safetyStep.isComplete
        ))
    }
}
```

## Advanced Streaming Techniques

### 1. Backpressure Handling
```kotlin
override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = flow {
    // Use buffer to handle backpressure
}.buffer(capacity = 50) // Adjust based on your needs
 .flowOn(Dispatchers.IO) // Use appropriate dispatcher
```

### 2. Cancellation Support
```kotlin
private fun streamWithCancellation(input: InferenceRequest): Flow<InferenceResult> = flow {
    try {
        while (hasMoreData()) {
            // Check for cancellation frequently
            ensureActive()
            
            val result = processNextChunk()
            emit(result)
            
            delay(10) // Small delay to allow cancellation checks
        }
    } catch (e: CancellationException) {
        Log.d(TAG, "Streaming cancelled")
        throw e // Re-throw to properly handle cancellation
    }
}
```

### 3. Error Recovery
```kotlin
private fun streamWithRetry(input: InferenceRequest): Flow<InferenceResult> = flow {
    var retryCount = 0
    val maxRetries = 3
    
    while (retryCount <= maxRetries) {
        try {
            streamingClient.process(input) { chunk ->
                emit(InferenceResult.success(
                    outputs = mapOf("text" to chunk.text),
                    partial = !chunk.isComplete
                ))
            }
            break // Success, exit retry loop
        } catch (e: Exception) {
            retryCount++
            if (retryCount > maxRetries) {
                emit(InferenceResult.error(RunnerError("E102", 
                    "Streaming failed after $maxRetries retries: ${e.message}")))
                break
            }
            delay(1000 * retryCount) // Exponential backoff
        }
    }
}
```

### 4. Multi-capability Streaming
```kotlin
override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = flow {
    when {
        // Route to appropriate streaming implementation
        CapabilityType.LLM in capabilities && isTextInput(input) -> {
            streamLLM(input).collect { emit(it) }
        }
        CapabilityType.ASR in capabilities && isAudioInput(input) -> {
            streamASR(input).collect { emit(it) }
        }
        CapabilityType.TTS in capabilities && isTextToAudioRequest(input) -> {
            streamTTS(input).collect { emit(it) }
        }
        else -> {
            // Fallback to non-streaming
            emit(run(input, false))
        }
    }
}
```

## Best Practices

### 1. Timing and Delays
```kotlin
// Good: Realistic timing for better UX
delay(50) // For word-level updates
delay(100) // For sentence-level updates
delay(200) // For chunk-level updates

// Bad: Too fast (overwhelming) or too slow (laggy)
delay(5)   // Too fast
delay(2000) // Too slow
```

### 2. Metadata Design
```kotlin
// Good: Comprehensive, useful metadata
metadata = mapOf(
    "progress_percent" to ((current * 100) / total),
    "estimated_remaining_ms" to estimatedTime,
    "quality_score" to qualityMetric,
    "model_confidence" to confidence
)

// Bad: Minimal or unclear metadata
metadata = mapOf("step" to currentStep)
```

### 3. Resource Management
```kotlin
private fun streamWithResourceManagement(): Flow<InferenceResult> = flow {
    val resources = acquireResources()
    try {
        // Streaming logic
    } finally {
        resources.release()
    }
}
```

### 4. Testing Streaming
```kotlin
@Test
fun testStreamingOutput() = runBlocking {
    val results = mutableListOf<InferenceResult>()
    
    runner.runAsFlow(testInput).collect { result ->
        results.add(result)
    }
    
    // Verify progressive results
    assertTrue("Should have multiple results", results.size > 1)
    assertTrue("Except last should be partial", 
        results.dropLast(1).all { it.partial })
    assertFalse("Last result should be complete", 
        results.last().partial)
}
```

## Common Pitfalls

❌ **Wrong**: Setting partial=false for intermediate results  
✅ **Correct**: Only final result has partial=false

❌ **Wrong**: No error handling in streaming callbacks  
✅ **Correct**: Wrap streaming logic in try-catch

❌ **Wrong**: Blocking operations in Flow  
✅ **Correct**: Use suspend functions and proper coroutines

❌ **Wrong**: No cancellation support  
✅ **Correct**: Regular ensureActive() checks

❌ **Wrong**: Overwhelming the client with too frequent updates  
✅ **Correct**: Reasonable delays and batching

## Performance Tips

1. **Batch small updates**: Don't emit every single character
2. **Use appropriate buffers**: Balance memory vs latency
3. **Monitor backpressure**: Handle slow consumers gracefully
4. **Profile your streaming**: Measure actual performance impact
5. **Consider caching**: Cache expensive computations where possible

---

**Remember**: Streaming should enhance user experience, not complicate your implementation. Start with simple patterns and iterate based on real usage patterns.