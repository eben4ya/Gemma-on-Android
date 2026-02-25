package com.example.scigemma.architecture

import android.os.Debug
import com.example.scigemma.features.Anonymizer
import com.example.scigemma.features.CloudFallback
import com.example.scigemma.features.EmergencyDetector
import com.example.scigemma.features.Summarizer
import com.example.scigemma.llm.LlamaCppModel
import com.example.scigemma.llm.PromptFormatter
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * PIPELINE Architecture.
 *
 * Tasks are chained sequentially; each stage's output feeds the next:
 *
 *   Per-message pipeline:
 *     Stage 1 — Emergency Detection  (keyword-based)
 *               ↓ [isEmergency flag + possibly short-circuit to crisis prompt]
 *     Stage 2 — LLM Conversation     (with original or emergency-adapted prompt)
 *               ↓ [response text]
 *     Stage 3 — Anonymization        (regex on user input, stored for context)
 *
 *   Cloud preparation pipeline (triggered by cloud-upload button):
 *     Stage A — Anonymize entire conversation history
 *               ↓ [anonymized text]
 *     Stage B — Summarize if token count > threshold
 *               ↓ [final payload ready to send]
 *
 * Tradeoffs vs Separate:
 *   ~ Same anonymization quality (both use regex)
 *   ~ Same emergency detection quality (both use keywords)
 *   + Explicit pipeline makes stage ordering and timing visible
 *   + Cloud preparation is a formal pipeline stage (cleaner architecture)
 *   − Sequential execution exposes pipeline overhead vs true parallel
 *
 * Tradeoffs vs Multitask:
 *   + More reliable anonymization (deterministic regex)
 *   + More reliable emergency detection (keywords)
 *   − More LLM inference calls for summarization vs single multitask prompt
 */
class PipelineArchitecture : ChatArchitecture {

    override val type: ArchitectureType = ArchitectureType.PIPELINE

    private val model = LlamaCppModel.getInstance()

    private val _tokenFlow = MutableSharedFlow<Pair<String, Boolean>>(
        extraBufferCapacity = 1,
        onBufferOverflow    = BufferOverflow.DROP_OLDEST
    )
    override val tokenFlow: SharedFlow<Pair<String, Boolean>> = _tokenFlow.asSharedFlow()

    override fun isReady(): Boolean = model.isLoaded()

    override fun initialize() {
        if (!model.isLoaded()) model.load()
    }

    // ---- Streaming (Chat UI) ----

    override fun processMessageStream(
        userInput: String,
        history: List<Pair<String, String>>,
        onResult: (ArchitectureResult) -> Unit
    ) {
        val ramBefore = currentPssKb()
        val tTotal    = System.currentTimeMillis()

        // Pipeline Stage 1: Emergency Detection
        val tEmerg      = System.currentTimeMillis()
        val isEmergency = EmergencyDetector.detect(userInput)
        val emergMs     = System.currentTimeMillis() - tEmerg

        // Pipeline Stage 2: LLM Conversation
        // If emergency, use a crisis-specific system prompt
        val tLlm   = System.currentTimeMillis()
        val prompt = if (isEmergency) {
            PromptFormatter.chat(
                "I'm in crisis and need support right now. $userInput",
                history
            )
        } else {
            PromptFormatter.chat(userInput, history)
        }

        // Streaming: tokens emitted to model.partialResults (observed by ChatViewModel)
        model.generateStream(prompt, maxTokens = LlamaCppModel.DEFAULT_MAX_TOKENS)

        val llmMs = System.currentTimeMillis() - tLlm

        // Pipeline Stage 3: Anonymization (runs after LLM starts, doesn't block streaming)
        val tAnon      = System.currentTimeMillis()
        val anonResult = Anonymizer.anonymize(userInput)
        val anonMs     = System.currentTimeMillis() - tAnon

        val totalMs  = System.currentTimeMillis() - tTotal
        val ramAfter = currentPssKb()

        onResult(
            ArchitectureResult(
                response        = "",  // built from partialResults by ChatViewModel
                isEmergency     = isEmergency,
                anonymizedInput = anonResult.anonymizedText,
                metrics = ProcessingMetrics(
                    totalLatencyMs       = totalMs,
                    emergencyDetectionMs = emergMs,
                    anonymizationMs      = anonMs,
                    llmInferenceMs       = llmMs,
                    timeToFirstTokenMs   = 0L,
                    tokensGenerated      = 0,
                    tokensPerSecond      = 0f,
                    peakRamKb            = maxOf(ramBefore, ramAfter),
                    ramDeltaKb           = ramAfter - ramBefore
                )
            )
        )
    }

    // ---- Synchronous (Benchmarking) ----

    override fun processMessageSync(
        userInput: String,
        history: List<Pair<String, String>>
    ): ArchitectureResult {
        val ramBefore = currentPssKb()
        val tTotal    = System.currentTimeMillis()

        // Pipeline Stage 1: Emergency Detection
        val tEmerg      = System.currentTimeMillis()
        val isEmergency = EmergencyDetector.detect(userInput)
        val emergMs     = System.currentTimeMillis() - tEmerg

        // Pipeline Stage 2: LLM Conversation
        val tLlm   = System.currentTimeMillis()
        val prompt = if (isEmergency) {
            PromptFormatter.chat(
                "I'm in crisis and need support right now. $userInput",
                history
            )
        } else {
            PromptFormatter.chat(userInput, history)
        }
        val raw   = model.generateSync(prompt, maxTokens = LlamaCppModel.DEFAULT_MAX_TOKENS)
        val llmMs = System.currentTimeMillis() - tLlm

        // Pipeline Stage 3: Anonymization
        val tAnon      = System.currentTimeMillis()
        val anonResult = Anonymizer.anonymize(userInput)
        val anonMs     = System.currentTimeMillis() - tAnon

        val totalMs  = System.currentTimeMillis() - tTotal
        val ramAfter = currentPssKb()

        val approxTokens = (raw.length / 4).coerceAtLeast(1)
        val tps = if (llmMs > 0) (approxTokens * 1000f / llmMs) else 0f

        return ArchitectureResult(
            response        = PromptFormatter.cleanResponse(raw),
            isEmergency     = isEmergency,
            anonymizedInput = anonResult.anonymizedText,
            metrics = ProcessingMetrics(
                totalLatencyMs       = totalMs,
                emergencyDetectionMs = emergMs,
                anonymizationMs      = anonMs,
                llmInferenceMs       = llmMs,
                timeToFirstTokenMs   = 0L,
                tokensGenerated      = approxTokens,
                tokensPerSecond      = tps,
                peakRamKb            = maxOf(ramBefore, ramAfter),
                ramDeltaKb           = ramAfter - ramBefore
            )
        )
    }

    // ---- Cloud preparation — explicit pipeline: Anonymize → Summarize ----

    override fun prepareForCloud(history: List<Pair<String, String>>): CloudPayload {
        val conversation = history.joinToString("\n") { (role, text) -> "$role: $text" }
        val tokenCountOrig = model.tokenCount(conversation)

        // Pipeline Stage A: Anonymize
        val tAnon = System.currentTimeMillis()
        val anonResult = Anonymizer.anonymize(conversation)
        val anonMs = System.currentTimeMillis() - tAnon

        // Pipeline Stage B: Summarize (chained from anonymized output)
        val tSumm = System.currentTimeMillis()
        val summResult = Summarizer.summarizeIfNeeded(model, anonResult.anonymizedText)
        val summMs = System.currentTimeMillis() - tSumm

        val tokenCountFinal = model.tokenCount(summResult.text)

        val prepared = CloudFallback.prepare(
            anonymizedConversation = anonResult.anonymizedText,
            summary                = summResult.text,
            wasSummarized          = summResult.wasSummarized,
            architectureUsed       = type.displayName,
            tokenCountOriginal     = tokenCountOrig,
            tokenCountFinal        = tokenCountFinal
        )

        return CloudPayload(prepared, anonMs, summMs)
    }

    override fun shutdown() { /* Model is a singleton; do not unload here */ }

    private fun currentPssKb(): Int {
        val mi = Debug.MemoryInfo()
        Debug.getMemoryInfo(mi)
        return mi.totalPss
    }
}
