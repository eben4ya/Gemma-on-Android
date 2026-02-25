package com.example.scigemma.features

/**
 * Regex rule-based PII anonymizer.
 *
 * Ported from experiments/privacy/rule_based.py.
 * Replaces personally identifiable information (PII) with placeholder tokens
 * before text is sent to a cloud API or stored.
 *
 * Covers: phone numbers (Indonesian + international), email addresses,
 * Indonesian NIK (national ID), generic long numeric IDs, URLs, IP addresses,
 * dates of birth, and name-title patterns.
 *
 * Used by: SeparateArchitecture, PipelineArchitecture
 * Note:    MultitaskArchitecture instructs the LLM to anonymize instead.
 */
object Anonymizer {

    data class Replacement(
        val original: String,
        val placeholder: String,
        val type: String
    )

    data class Result(
        val anonymizedText: String,
        val replacementCount: Int,
        val replacements: List<Replacement>
    )

    // ---- Ordered rules (most specific first) ----
    private data class Rule(val regex: Regex, val placeholder: String, val type: String)

    private val RULES: List<Rule> = listOf(

        // Indonesian mobile: +62 or 08, 8–13 digits after prefix
        Rule(Regex("""(\+62|0)8[0-9]{8,12}"""),              "[PHONE]", "PHONE_ID"),

        // Indonesian landline: area code (2–4 digits) + number
        Rule(Regex("""\(0\d{1,3}\)\s?\d{5,8}"""),            "[PHONE]", "PHONE_ID_LAND"),

        // International E.164 format: +country (1–3 digits) space/dash + digits
        Rule(Regex("""\+[1-9]\d{1,2}[\s\-]\d{3,14}"""),     "[PHONE]", "PHONE_INTL"),

        // North American: 3-3-4 with separators
        Rule(Regex("""\(?\d{3}\)?[\s\-\.]\d{3}[\s\-\.]\d{4}"""), "[PHONE]", "PHONE_NA"),

        // Email addresses
        Rule(Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}"""),
             "[EMAIL]", "EMAIL"),

        // Indonesian NIK: exactly 16 digits
        Rule(Regex("""\b\d{16}\b"""),                         "[NIK]", "NIK"),

        // Other long numeric IDs: 14–15 digits (not NIK)
        Rule(Regex("""\b\d{14,15}\b"""),                      "[ID]", "NUMERIC_ID"),

        // HTTPS/HTTP URLs
        Rule(Regex("""https?://[^\s<>"{}|\\^`\[\]]+"""),     "[URL]", "URL"),

        // www. URLs
        Rule(Regex("""www\.[A-Za-z0-9\-]+\.[A-Za-z]{2,}[^\s]*"""), "[URL]", "URL_WWW"),

        // IPv4 addresses
        Rule(Regex("""(?<!\d)(?:\d{1,3}\.){3}\d{1,3}(?!\d)"""), "[IP]", "IP"),

        // Dates of birth: dd/mm/yyyy, dd-mm-yyyy, mm/dd/yyyy (requires 19xx or 20xx year)
        Rule(
            Regex("""\b(?:0?[1-9]|[12]\d|3[01])[/\-](?:0?[1-9]|1[0-2])[/\-](?:19|20)\d{2}\b"""),
            "[DOB]", "DOB"
        ),

        // Indonesian name titles followed by a capitalized name (1–3 words)
        Rule(
            Regex("""(?:Bapak|Ibu|Pak|Bu|Tuan|Nyonya|Dr\.|dr\.|Prof\.|Sdr\.|Sdri\.)\s+[A-Z][a-z]+(?:\s+[A-Z][a-z]+){0,2}"""),
            "[NAME]", "NAME_TITLE"
        ),

        // English name titles
        Rule(
            Regex("""(?:Mr\.|Mrs\.|Ms\.|Miss|Dr\.|Prof\.)\s+[A-Z][a-z]+(?:\s+[A-Z][a-z]+){0,2}"""),
            "[NAME]", "NAME_TITLE_EN"
        )
    )

    /**
     * Anonymize [text] by replacing all detected PII with placeholders.
     * Rules are applied in order; replacements in each pass are non-overlapping.
     */
    fun anonymize(text: String): Result {
        var current = text
        val allReplacements = mutableListOf<Replacement>()

        for (rule in RULES) {
            val matches = rule.regex.findAll(current).toList()
            for (match in matches) {
                allReplacements.add(Replacement(match.value, rule.placeholder, rule.type))
            }
            current = rule.regex.replace(current, rule.placeholder)
        }

        return Result(
            anonymizedText  = current,
            replacementCount = allReplacements.size,
            replacements    = allReplacements
        )
    }

    /** Count how many PII entities are detectable in [text] without replacing them. */
    fun countPii(text: String): Int =
        RULES.sumOf { rule -> rule.regex.findAll(text).count() }
}
