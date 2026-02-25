package com.example.scigemma.features

import android.os.Build
import com.google.gson.GsonBuilder

/**
 * Cloud fallback helper — prepares a JSON payload for cloud API review.
 *
 * This is MOCK MODE: the payload is prepared and displayed to the user
 * in a confirmation dialog. No actual network call is made.
 * (A real API endpoint can be wired up here in future work.)
 *
 * The payload contains:
 *   - The anonymized conversation (PII removed)
 *   - An optional LLM summary (if conversation was too long)
 *   - The architecture used for this session
 *   - Timestamp and device info (for research context)
 */
object CloudFallback {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    data class Payload(
        val anonymizedConversation: String,
        val summary: String,
        val wasSummarized: Boolean,
        val architectureUsed: String,
        val timestamp: Long,
        val deviceModel: String,
        val androidVersion: Int,
        val tokenCountOriginal: Int,
        val tokenCountFinal: Int
    )

    data class PreparedPayload(
        val json: String,       // Pretty-printed JSON for display
        val payload: Payload    // Structured data
    )

    /**
     * Build the cloud payload after anonymization + optional summarization.
     *
     * @param anonymizedConversation  Conversation with PII replaced
     * @param summary                 LLM summary (or same as anonymizedConversation if not summarized)
     * @param wasSummarized           Whether the LLM was called to summarize
     * @param architectureUsed        Name of the active architecture
     * @param tokenCountOriginal      Token count before summarization
     * @param tokenCountFinal         Token count of the final text sent
     */
    fun prepare(
        anonymizedConversation: String,
        summary: String,
        wasSummarized: Boolean,
        architectureUsed: String,
        tokenCountOriginal: Int,
        tokenCountFinal: Int
    ): PreparedPayload {
        val payload = Payload(
            anonymizedConversation = anonymizedConversation,
            summary                = summary,
            wasSummarized          = wasSummarized,
            architectureUsed       = architectureUsed,
            timestamp              = System.currentTimeMillis(),
            deviceModel            = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion         = Build.VERSION.SDK_INT,
            tokenCountOriginal     = tokenCountOriginal,
            tokenCountFinal        = tokenCountFinal
        )
        return PreparedPayload(json = gson.toJson(payload), payload = payload)
    }

    /**
     * Human-readable summary for display in the cloud upload dialog.
     */
    fun summaryText(prepared: PreparedPayload): String = buildString {
        with(prepared.payload) {
            appendLine("Architecture: $architectureUsed")
            appendLine("Original tokens: $tokenCountOriginal")
            appendLine("Final tokens sent: $tokenCountFinal")
            if (wasSummarized) appendLine("✓ Conversation was summarized before sending")
            appendLine()
            appendLine("--- Anonymized text ---")
            appendLine(summary.take(400) + if (summary.length > 400) "…" else "")
        }
    }
}
