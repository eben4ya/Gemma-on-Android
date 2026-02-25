package com.example.scigemma.architecture

/**
 * The three architecture variants benchmarked in this study.
 *
 * MULTITASK  — One LLM call handles emergency detection, PII anonymization,
 *               and conversation response via structured prompt engineering.
 *
 * SEPARATE   — Independent modules run in parallel-like fashion:
 *               keyword-based emergency detection + regex anonymization + LLM conversation.
 *               Each module is specialized; results are combined afterward.
 *
 * PIPELINE   — Tasks run sequentially, each feeding into the next:
 *               Stage 1: Emergency detection (keyword)
 *               Stage 2: LLM conversation
 *               Stage 3: Anonymization (regex)
 *               Cloud pipeline: Anonymize → Summarize → Ready to send
 */
enum class ArchitectureType(
    val displayName: String,
    val shortName: String,
    val description: String
) {
    MULTITASK(
        displayName = "Multitask",
        shortName   = "MT",
        description = "Single LLM call handles emergency detection, anonymization, " +
                      "and response via structured prompt engineering."
    ),
    SEPARATE(
        displayName = "Separate",
        shortName   = "SEP",
        description = "Independent specialized modules: keyword emergency detector, " +
                      "regex anonymizer, and LLM conversation handler."
    ),
    PIPELINE(
        displayName = "Pipeline",
        shortName   = "PIPE",
        description = "Sequential chain: emergency detection → conversation → " +
                      "anonymization. Cloud path adds summarization stage."
    )
}
