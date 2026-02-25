package com.example.scigemma.features

/**
 * Keyword-based emergency / crisis detector.
 *
 * Ported from experiments/architectures/mock_llm.py (EMERGENCY_KEYWORDS).
 * Checks for suicidal ideation, self-harm, overdose, and related phrases
 * in English and Indonesian.
 *
 * Used by: SeparateArchitecture, PipelineArchitecture (dedicated module)
 * Note:    MultitaskArchitecture uses the LLM for this instead.
 */
object EmergencyDetector {

    // Matched against lowercased input — same list as the Python experiments
    private val EMERGENCY_KEYWORDS = listOf(
        // English — suicidal ideation
        "suicide", "suicidal",
        "kill myself", "killing myself",
        "end my life", "ending my life",
        "don't want to live", "dont want to live",
        "want to die", "wanna die",
        "better off dead", "better off without me",
        "no point in going on", "no reason to live",
        "tonight is the night",
        "goodbye letter", "farewell letter",
        // English — self-harm / overdose
        "take all", "took too many", "took all my pills",
        "jump off", "jumped off",
        "overdose", "overdosed",
        "cutting myself", "cut myself", "self-harm", "selfharm",
        "hurt myself", "hurting myself",
        "stockpiling pills", "stockpiling medication",
        // English — extreme hopelessness
        "going to end it", "going to end things",
        "can't go on", "cannot go on",
        "ready to die",
        // Indonesian
        "bunuh diri",
        "mau mati",
        "ingin mati",
        "tidak mau hidup",
        "tidak ingin hidup",
        "menyakiti diri",
        "minum obat banyak",
        "overdosis"
    )

    data class Result(
        val isEmergency: Boolean,
        val matchedKeywords: List<String>,
        val confidence: Float   // 0.0 – 1.0 based on keyword density
    )

    /**
     * Fast keyword-based check. Returns true if any keyword matches.
     * O(n·k) where n = text length, k = keyword count.
     */
    fun detect(text: String): Boolean {
        val lower = text.lowercase()
        return EMERGENCY_KEYWORDS.any { lower.contains(it) }
    }

    /** Detailed result including which keywords matched and a confidence score. */
    fun detectDetailed(text: String): Result {
        val lower = text.lowercase()
        val matched = EMERGENCY_KEYWORDS.filter { lower.contains(it) }
        // Confidence: 1 keyword → 0.33, 2 → 0.67, 3+ → 1.0
        val confidence = (matched.size.toFloat() / 3f).coerceIn(0f, 1f)
        return Result(matched.isNotEmpty(), matched, confidence)
    }

    // ---- Crisis resources ----
    val CRISIS_HOTLINES: Map<String, String> = linkedMapOf(
        "Indonesia"     to "Into The Light: 119 ext 8",
        "International" to "findahelpline.com",
        "USA"           to "988 Suicide & Crisis Lifeline: call/text 988",
        "UK"            to "Samaritans: 116 123"
    )

    fun hotlineMessage(): String = buildString {
        appendLine("Please reach out to a crisis support line:")
        CRISIS_HOTLINES.forEach { (region, info) ->
            appendLine("  • $region — $info")
        }
    }
}
