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
 * MULTITASK Architecture.
 *
 * A single LLM call handles ALL tasks via structured prompt engineering:
 *   1. Emergency detection  — model outputs "EMERGENCY: YES/NO"
 *   2. PII anonymization    — model outputs "ANONYMIZED: <text>"
 *   3. Conversation response — model outputs "RESPONSE: <text>"
 *
 * Tradeoffs vs Separate/Pipeline:
 *   + Single LLM call (no switching overhead)
 *   − Larger prompt → higher latency per call
 *   − LLM may hallucinate anonymization (less reliable than regex)
 *   − Emergency detection depends on model reasoning (may miss edge cases)
 *
 * Model path: [LlamaCppModel.MODEL_PATH]
 */
class MultitaskArchitecture : ChatArchitecture {

    override val type: ArchitectureType = ArchitectureType.MULTITASK

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
        val wallStart = System.currentTimeMillis()

        val prompt = PromptFormatter.multitask(userInput, history)

        // Buffer generated tokens so we can parse structure after completion
        val buffer = StringBuilder()
        var firstTokenMs = 0L
        var firstToken = true
        var tokenCount = 0

        model.generateStream(prompt, maxTokens = 512) // emits to model.partialResults

        // Note: streaming is driven by LlamaCppModel.partialResults SharedFlow.
        // We hook into it by calling nativeGenerateStream which emits to _tokenFlow here.
        // We re-route via processMessageSync for the result, and forward stream separately.
    }

    // ---- Synchronous (Benchmarking) ----

    override fun processMessageSync(
        userInput: String,
        history: List<Pair<String, String>>
    ): ArchitectureResult {
        val ramBefore = currentPssKb()
        val tTotal    = System.currentTimeMillis()

        val prompt = PromptFormatter.multitask(userInput, history)

        val tLlm = System.currentTimeMillis()
        val raw  = model.generateSync(prompt, maxTokens = 512)
        val llmMs = System.currentTimeMillis() - tLlm

        val totalMs = System.currentTimeMillis() - tTotal
        val ramAfter = currentPssKb()

        // Estimate tokens from character count (rough: ~4 chars/token for Gemma)
        val approxTokens = (raw.length / 4).coerceAtLeast(1)
        val tps = if (llmMs > 0) (approxTokens * 1000f / llmMs) else 0f

        val parsed = PromptFormatter.parseMultitaskResponse(raw)

        // Fallback: if LLM didn't follow structured format, use keyword emergency detection
        val isEmergency = if (parsed.response.isBlank()) {
            EmergencyDetector.detect(userInput)
        } else {
            parsed.isEmergency
        }

        val response = if (parsed.response.isBlank()) {
            PromptFormatter.cleanResponse(raw)
        } else {
            parsed.response
        }

        return ArchitectureResult(
            response       = response,
            isEmergency    = isEmergency,
            anonymizedInput = parsed.anonymizedText,
            metrics = ProcessingMetrics(
                totalLatencyMs       = totalMs,
                emergencyDetectionMs = 0L,      // embedded in LLM call
                anonymizationMs      = 0L,      // embedded in LLM call
                llmInferenceMs       = llmMs,
                timeToFirstTokenMs   = 0L,      // N/A for sync
                tokensGenerated      = approxTokens,
                tokensPerSecond      = tps,
                peakRamKb            = maxOf(ramBefore, ramAfter),
                ramDeltaKb           = ramAfter - ramBefore
            )
        )
    }

    // ---- Streaming workaround: use sync + forward tokens from model ----
    // For the Chat UI, MultitaskArchitecture streams via the model's partialResults flow.
    // ChatViewModel observes model.partialResults directly for the streaming path.
    fun streamForChat(userInput: String, history: List<Pair<String, String>>) {
        val prompt = PromptFormatter.multitask(userInput, history)
        model.generateStream(prompt, maxTokens = 512)
        // Tokens emitted to model.partialResults → observed by ChatViewModel
    }

    // ---- Cloud preparation ----

    override fun prepareForCloud(history: List<Pair<String, String>>): CloudPayload {
        val conversation = history.joinToString("\n") { (role, text) -> "$role: $text" }
        val tokenCountOrig = model.tokenCount(conversation)

        val tAnon = System.currentTimeMillis()
        // Multitask: anonymization done by LLM at chat time; for cloud prep, use regex as safety net
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
