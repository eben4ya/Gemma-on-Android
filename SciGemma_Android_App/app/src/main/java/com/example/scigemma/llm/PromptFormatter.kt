package com.example.scigemma.llm

/**
 * Gemma 2 chat template formatter.
 *
 * Gemma uses the following turn-based format:
 *   <start_of_turn>user\n{text}<end_of_turn>\n
 *   <start_of_turn>model\n{text}<end_of_turn>\n
 *
 * Note: Gemma does not have a dedicated "system" role.
 * System prompts are injected as an initial user/model exchange.
 *
 * ConversationHistory is a list of Pair(role, text) where role is "user" or "model".
 */
object PromptFormatter {

    private const val START = "<start_of_turn>"
    private const val END   = "<end_of_turn>"

    // ----------------------------------------------------------------
    // Core formatter
    // ----------------------------------------------------------------

    /**
     * Build a complete Gemma prompt from conversation history + new user message.
     *
     * @param systemPrompt  Optional instruction injected as a user/model exchange
     * @param history       Previous turns as (role, text) pairs
     * @param userMessage   The current user input to respond to
     * @param maxHistory    Maximum number of history turns to include (context window)
     */
    fun format(
        systemPrompt: String? = null,
        history: List<Pair<String, String>> = emptyList(),
        userMessage: String,
        maxHistory: Int = 4
    ): String = buildString {
        // System prompt as a synthetic user/model exchange
        if (!systemPrompt.isNullOrBlank()) {
            append("${START}user\n$systemPrompt${END}\n")
            append("${START}model\nUnderstood. I will follow these instructions.${END}\n")
        }

        // Include the last N turns of conversation history
        val trimmedHistory = if (history.size > maxHistory) history.takeLast(maxHistory)
                             else history
        for ((role, text) in trimmedHistory) {
            append("${START}$role\n$text${END}\n")
        }

        // Current user message (model turn left open for generation)
        append("${START}user\n$userMessage${END}\n")
        append("${START}model\n")
    }

    // ----------------------------------------------------------------
    // Prompt templates for the 3 architectures
    // ----------------------------------------------------------------

    /**
     * MULTITASK architecture prompt.
     * Instructs the model to detect emergency, anonymize, and respond
     * all in a single structured output.
     *
     * Expected model output format:
     *   EMERGENCY: YES or NO
     *   ANONYMIZED: <text with PII replaced>
     *   RESPONSE: <empathetic reply>
     */
    fun multitask(userMessage: String, history: List<Pair<String, String>> = emptyList()): String {
        val system = """You are a mental health support chatbot. For EVERY message, respond using EXACTLY this format (no extra text):
EMERGENCY: YES or NO
ANONYMIZED: <rewrite the user's message replacing real names with [NAME], locations with [LOCATION], phone numbers with [PHONE], email addresses with [EMAIL]>
RESPONSE: <an empathetic, supportive reply in 2-3 sentences>"""
        return format(system, history, userMessage)
    }

    /**
     * Standard chat-only prompt.
     * Used by SEPARATE and PIPELINE architectures where emergency detection
     * and anonymization are handled by dedicated modules.
     */
    fun chat(userMessage: String, history: List<Pair<String, String>> = emptyList()): String {
        val system = "You are a compassionate mental health support chatbot. " +
                     "Provide empathetic, supportive responses. " +
                     "If someone is in crisis, always encourage them to seek professional help."
        return format(system, history, userMessage)
    }

    /**
     * Summarization prompt for cloud fallback.
     * Used to compress a long conversation before sending to the cloud API.
     */
    fun summarize(conversation: String): String {
        val userMsg = """Summarize this mental health support conversation in 3-5 sentences.
Focus on: the main emotional concerns, key topics discussed, and any follow-up actions suggested.
Do NOT include any personal identifying information.

Conversation to summarize:
$conversation"""
        return format(null, emptyList(), userMsg)
    }

    // ----------------------------------------------------------------
    // Parser for MULTITASK structured output
    // ----------------------------------------------------------------

    data class MultitaskResponse(
        val isEmergency: Boolean,
        val anonymizedText: String,
        val response: String
    )

    /**
     * Parse the structured output from the MULTITASK prompt.
     * Falls back gracefully if the model doesn't follow the exact format.
     */
    fun parseMultitaskResponse(raw: String): MultitaskResponse {
        val cleaned = raw
            .replace("<start_of_turn>", "")
            .replace("<end_of_turn>", "")
            .trim()

        val emergencyLine  = extractField(cleaned, "EMERGENCY:")
        val anonymizedLine = extractField(cleaned, "ANONYMIZED:")
        val responseLine   = extractField(cleaned, "RESPONSE:")

        val isEmergency = emergencyLine.uppercase().contains("YES")

        // If parsing failed (model didn't follow format), return raw as response
        return if (responseLine.isBlank()) {
            MultitaskResponse(
                isEmergency  = isEmergency,
                anonymizedText = "",   // signal that anonymization failed
                response     = cleaned
            )
        } else {
            MultitaskResponse(
                isEmergency  = isEmergency,
                anonymizedText = anonymizedLine,
                response     = responseLine
            )
        }
    }

    private fun extractField(text: String, prefix: String): String {
        val lines = text.lines()
        for (i in lines.indices) {
            if (lines[i].startsWith(prefix, ignoreCase = true)) {
                return lines[i].removePrefix(prefix).trim()
            }
        }
        return ""
    }

    // ----------------------------------------------------------------
    // Response cleaner (shared across architectures)
    // ----------------------------------------------------------------

    /**
     * Strip Gemma turn markers and leading/trailing whitespace from
     * a generated response before displaying it in the UI.
     */
    fun cleanResponse(text: String): String = text
        .replace("<start_of_turn>", "")
        .replace("<end_of_turn>", "")
        .trim()
}
