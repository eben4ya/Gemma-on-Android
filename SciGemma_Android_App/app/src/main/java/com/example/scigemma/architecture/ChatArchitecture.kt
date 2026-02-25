package com.example.scigemma.architecture

import com.example.scigemma.features.CloudFallback
import kotlinx.coroutines.flow.SharedFlow

/**
 * Common interface for all three architecture implementations.
 *
 * Each architecture processes user input and returns an [ArchitectureResult]
 * containing the response, emergency flag, anonymized text, and
 * per-task timing/memory metrics.
 *
 * Chat mode: [processMessageStream] emits tokens as they are generated
 *            (good UX); metrics are approximate for streaming.
 * Benchmark: [processMessageSync] blocks and returns complete result with
 *            precise metrics (used by BenchmarkRunner).
 */
interface ChatArchitecture {

    val type: ArchitectureType

    /** Whether the model is loaded and ready for inference. */
    fun isReady(): Boolean

    /**
     * Load the model and initialize any supporting modules.
     * Must be called before any inference method.
     * Safe to call repeatedly (no-op if already loaded).
     */
    fun initialize()

    /**
     * STREAMING mode (Chat UI).
     * Starts generation and emits (token, isDone) pairs via [tokenFlow].
     * Also returns an [ArchitectureResult] after generation completes
     * (collected from [resultFlow]).
     */
    val tokenFlow: SharedFlow<Pair<String, Boolean>>

    /**
     * Begin streaming generation. Tokens arrive via [tokenFlow].
     * [onResult] is called once with the final [ArchitectureResult]
     * when generation is complete.
     * Must be called from a background thread (IO dispatcher).
     */
    fun processMessageStream(
        userInput: String,
        history: List<Pair<String, String>>,
        onResult: (ArchitectureResult) -> Unit
    )

    /**
     * SYNCHRONOUS mode (Benchmarking).
     * Blocks until generation is complete. Returns full result with
     * precise timing and memory metrics.
     * Must be called from a background thread (IO dispatcher).
     */
    fun processMessageSync(
        userInput: String,
        history: List<Pair<String, String>>
    ): ArchitectureResult

    /**
     * Prepare the conversation for cloud upload.
     * Runs anonymization + optional summarization.
     * Must be called from a background thread.
     */
    fun prepareForCloud(history: List<Pair<String, String>>): CloudPayload

    /** Release native model resources. */
    fun shutdown()
}

// ---- Result data classes ----

data class ArchitectureResult(
    val response: String,
    val isEmergency: Boolean,
    val anonymizedInput: String,   // "" if not applicable (e.g. streaming)
    val metrics: ProcessingMetrics
)

data class ProcessingMetrics(
    val totalLatencyMs: Long,
    val emergencyDetectionMs: Long,
    val anonymizationMs: Long,
    val llmInferenceMs: Long,
    val timeToFirstTokenMs: Long,  // 0 for sync mode (not applicable)
    val tokensGenerated: Int,
    val tokensPerSecond: Float,
    val peakRamKb: Int,            // App PSS in KB via Debug.MemoryInfo
    val ramDeltaKb: Int            // RAM delta from start to end of processing
) {
    val tokensPerSecondFormatted: String
        get() = "%.1f tok/s".format(tokensPerSecond)

    val latencyFormatted: String
        get() = "${totalLatencyMs} ms"

    val ramFormatted: String
        get() = "${"%.1f".format(peakRamKb / 1024f)} MB"
}

data class CloudPayload(
    val prepared: CloudFallback.PreparedPayload,
    val anonymizationMs: Long,
    val summarizationMs: Long
)
