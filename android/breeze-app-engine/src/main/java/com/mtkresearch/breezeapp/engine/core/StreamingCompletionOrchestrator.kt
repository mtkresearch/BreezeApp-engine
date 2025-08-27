package com.mtkresearch.breezeapp.engine.core

import com.mtkresearch.breezeapp.engine.model.InferenceRequest
import com.mtkresearch.breezeapp.engine.model.StreamingChatResult
import com.mtkresearch.breezeapp.engine.runner.core.FlowStreamingRunner
import com.mtkresearch.breezeapp.engine.runner.guardian.GuardianPipeline
import com.mtkresearch.breezeapp.engine.runner.guardian.GuardianPipelineConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * Orchestrates a concurrent streaming chat session, merging the output of the LLM
 * with real-time analysis from the Guardian pipeline.
 *
 * @param llmRunner The runner responsible for generating text.
 * @param guardianPipeline The pipeline for performing safety analysis.
 * @param request The original inference request.
 * @param guardianConfig The configuration for the guardian pipeline.
 * @param logger A logger instance.
 */
class StreamingCompletionOrchestrator(
    private val llmRunner: FlowStreamingRunner,
    private val guardianPipeline: GuardianPipeline,
    private val request: InferenceRequest,
    private val guardianConfig: GuardianPipelineConfig,
    private val logger: Logger
) {
    private val accumulatedText = StringBuilder()
    private val outputChannel = Channel<StreamingChatResult>(Channel.UNLIMITED)

    companion object {
        private const val TAG = "StreamingOrchestrator"
    }

    fun execute(): Flow<StreamingChatResult> = flow {
        try {
            supervisorScope { 
                val llmJob = launchLlmJob(this)
                val guardianJob = launchGuardianJob(this)

                // Ensure all jobs are cancelled when the scope is left
                llmJob.invokeOnCompletion { guardianJob.cancel() }
                guardianJob.invokeOnCompletion { llmJob.cancel() }

                // Consume the output channel and emit results to the client
                for (result in outputChannel) {
                    emit(result)
                }
            }
        } finally {
            outputChannel.close()
            logger.d(TAG, "Orchestrator finished and channel closed.")
        }
    }

    private fun launchLlmJob(scope: CoroutineScope): Job = scope.launch {
        logger.d(TAG, "LLM Feeder Job started.")
        llmRunner.runAsFlow(request)
            .onCompletion { cause ->
                if (cause == null) {
                    outputChannel.trySend(StreamingChatResult.Complete(accumulatedText.toString()))
                }
                outputChannel.close() // Close the channel when the LLM stream is done
            }
            .collect { result ->
                if (result.error != null) {
                    outputChannel.trySend(StreamingChatResult.Error(result.error))
                    return@collect
                }

                // Immediately send the content chunk to the client for low latency
                outputChannel.trySend(StreamingChatResult.Content(result))

                // Append to buffer for the guardian
                val textChunk = guardianPipeline.extractTextFromResult(result)
                if (!textChunk.isNullOrBlank()) {
                    accumulatedText.append(textChunk)
                }
            }
    }

    private val sentMasks = java.util.concurrent.ConcurrentHashMap.newKeySet<com.mtkresearch.breezeapp.engine.runner.guardian.GuardianMaskingResult>()

    private fun launchGuardianJob(scope: CoroutineScope): Job = scope.launch {
        logger.d(TAG, "Guardian Watcher Job started.")
        // This is a simplified trigger. A more advanced version could use a ticker
        // or buffer chunks to avoid checking on every single token.
        while (!outputChannel.isClosedForSend) {
            kotlinx.coroutines.delay(150) // Check every 150ms

            val currentBuffer = accumulatedText.toString()
            if (currentBuffer.isNotBlank()) {
                val maskingResults = guardianPipeline.checkProgressiveOutput(currentBuffer, guardianConfig)
                maskingResults.forEach { mask ->
                    // ONLY send the mask if we haven't sent this exact one before.
                    if (sentMasks.add(mask)) {
                        // Send any new masking actions to the client
                        outputChannel.trySend(StreamingChatResult.Mask(mask))
                    }
                }
            }
        }
        logger.d(TAG, "Guardian Watcher Job finished.")
    }
}