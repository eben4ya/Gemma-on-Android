package com.example.scigemma.features

import com.example.scigemma.llm.LlamaCppModel
import com.example.scigemma.llm.PromptFormatter

/**
 * LLM-based conversation summarizer.
 *
 * Summarizes a conversation before sending it to the cloud API
 * when the token count exceeds [TOKEN_THRESHOLD].
 * Uses [LlamaCppModel.generateSync] so it blocks until complete.
 *
 * Used by all 3 architectures when the user taps the cloud-upload button.
 * In PipelineArchitecture, summarization is a distinct pipeline stage.
 */
object Summarizer {

    /** Trigger summarization when conversation exceeds this many tokens. */
    const val TOKEN_THRESHOLD = 512

    data class Result(
        val text: String,          // the summary (or original if no summary needed)
        val wasSummarized: Boolean // true if LLM was called, false if original was short
    )

    /**
     * Summarize [conversation] if it exceeds [TOKEN_THRESHOLD] tokens.
     * Returns the original text unchanged if it is already short enough.
     *
     * Must be called from a background thread.
     *
     * @param model     Loaded [LlamaCppModel] instance
     * @param conversation  The full conversation text to potentially summarize
     */
    fun summarizeIfNeeded(model: LlamaCppModel, conversation: String): Result {
        val tokenCount = model.tokenCount(conversation)
        return if (tokenCount > TOKEN_THRESHOLD) {
            val prompt   = PromptFormatter.summarize(conversation)
            val raw      = model.generateSync(prompt, maxTokens = 200)
            val summary  = PromptFormatter.cleanResponse(raw)
            Result(text = summary.ifBlank { conversation }, wasSummarized = true)
        } else {
            Result(text = conversation, wasSummarized = false)
        }
    }
}
