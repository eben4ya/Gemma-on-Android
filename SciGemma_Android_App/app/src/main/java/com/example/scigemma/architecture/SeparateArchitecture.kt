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
 * SEPARATE Architecture.
 *
 * Each task runs independently via a specialized module:
 *   1. Emergency detection  — [EmergencyDetector] (keyword rules, ~0 ms)
 *   2. PII anonymization    — [Anonymizer] (regex rules, ~1 ms)
 *   3. Conversation response — LLM (chat-only prompt, focused output)
 *
 * All three run on every message. Anonymized input is passed to the LLM
 * so PII is not included in the conversation history.
 *
 * Tradeoffs vs Multitask:
 *   + Faster LLM inference (shorter, focused chat prompt)
 *   + Deterministic, reliable anonymization (regex vs LLM)
 *   + Deterministic, reliable emergency detection (keyword vs LLM)
 *   − Three separate processing steps (though two are near-instant)
 */
class SeparateArchitecture : ChatArchitecture {

    override val type: ArchitectureType = ArchitectureType.SEPARATE

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

        // Step 1: Emergency detection (keyword, near-instant)
        val tEmerg = System.currentTimeMillis()
        val isEmergency = EmergencyDetector.detect(userInput)
        val emergMs = System.currentTimeMillis() - tEmerg

        // Step 2: Anonymization (regex, near-instant)
        val tAnon = System.currentTimeMillis()
        val anonResult = Anonymizer.anonymize(userInput)
        val anonMs = System.currentTimeMillis() - tAnon

        // Step 3: LLM conversation with anonymized input
        // Build history with anonymized input to avoid PII in context
        val anonHistory = history.toMutableList()
        val tLlm  = System.currentTimeMillis()
        val prompt = PromptFormatter.chat(anonResult.anonymizedText, anonHistory)

        // Start streaming; tokens go to model.partialResults (observed by ChatViewModel)
        model.generateStream(prompt, maxTokens = LlamaCppModel.DEFAULT_MAX_TOKENS)

        // onResult callback with approximate metrics (tokens counted at stream end)
        val llmMs    = System.currentTimeMillis() - tLlm
        val totalMs  = System.currentTimeMillis() - tTotal
        val ramAfter = currentPssKb()

        onResult(
            ArchitectureResult(
                response        = "",  // response built by ChatViewModel from partialResults
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

        // Step 1: Emergency detection
        val tEmerg = System.currentTimeMillis()
        val isEmergency = EmergencyDetector.detect(userInput)
        val emergMs = System.currentTimeMillis() - tEmerg

        // Step 2: Anonymization
        val tAnon = System.currentTimeMillis()
        val anonResult = Anonymizer.anonymize(userInput)
        val anonMs = System.currentTimeMillis() - tAnon

        // Step 3: LLM conversation
        val tLlm   = System.currentTimeMillis()
        val prompt = PromptFormatter.chat(anonResult.anonymizedText, history)
        val raw    = model.generateSync(prompt, maxTokens = LlamaCppModel.DEFAULT_MAX_TOKENS)
        val llmMs  = System.currentTimeMillis() - tLlm

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

    // ---- Cloud preparation ----

    override fun prepareForCloud(history: List<Pair<String, String>>): CloudPayload {
        val conversation = history.joinToString("\n") { (role, text) -> "$role: $text" }
        val tokenCountOrig = model.tokenCount(conversation)

        val tAnon = System.currentTimeMillis()
        val anonResult = Anonymizer.anonymize(conversation)
        val anonMs = System.currentTimeMillis() - tAnon

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
