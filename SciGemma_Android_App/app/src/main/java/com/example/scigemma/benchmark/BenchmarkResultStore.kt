package com.example.scigemma.benchmark

import android.content.Context
import android.util.Log
import com.google.gson.GsonBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves benchmark results to app-specific external storage.
 *
 * Output directory:
 *   /sdcard/Android/data/com.example.scigemma/files/benchmark_results/
 *
 * Pull results with:
 *   adb pull /sdcard/Android/data/com.example.scigemma/files/benchmark_results/ .
 *
 * No WRITE_EXTERNAL_STORAGE permission required (app-specific directory, API 19+).
 */
object BenchmarkResultStore {

    private const val TAG = "BenchmarkResultStore"
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val dateFormatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    data class SaveResult(
        val directory: String,
        val rawJsonPath: String,
        val summaryJsonPath: String,
        val csvPath: String,
        val markdownPath: String
    )

    /**
     * Save all benchmark results to external storage.
     *
     * @param context          Application context
     * @param allSamples       Map from architecture name to list of per-sample results
     * @param summaries        One summary per architecture
     * @return [SaveResult] with file paths of all written files
     */
    fun save(
        context: Context,
        allSamples: Map<String, List<BenchmarkSample>>,
        summaries: List<ArchitectureSummary>
    ): SaveResult {
        val dir = File(context.getExternalFilesDir(null), "benchmark_results")
        dir.mkdirs()
        val timestamp = dateFormatter.format(Date())

        val rawPath  = File(dir, "raw_${timestamp}.json").also {
            it.writeText(gson.toJson(allSamples))
        }.absolutePath

        val summPath = File(dir, "summary_${timestamp}.json").also {
            it.writeText(gson.toJson(summaries))
        }.absolutePath

        val csvPath  = File(dir, "results_${timestamp}.csv").also {
            it.writeText(toCsv(allSamples))
        }.absolutePath

        val mdPath   = File(dir, "report_${timestamp}.md").also {
            it.writeText(toMarkdown(summaries))
        }.absolutePath

        Log.i(TAG, "Results saved to: ${dir.absolutePath}")
        return SaveResult(dir.absolutePath, rawPath, summPath, csvPath, mdPath)
    }

    // ---- CSV export ----

    private fun toCsv(allSamples: Map<String, List<BenchmarkSample>>): String {
        val sb = StringBuilder()
        // Header
        sb.appendLine(
            "architecture,sample_index,label_actual,label_predicted,pii_gt,pii_detected," +
            "total_latency_ms,emergency_detection_ms,anonymization_ms,llm_inference_ms," +
            "tokens_generated,tokens_per_second,peak_ram_kb,ram_delta_kb,available_ram_mb"
        )
        for ((_, samples) in allSamples) {
            for (s in samples) {
                val actual    = if (s.isEmergencyActual)    "emergency" else "normal"
                val predicted = if (s.isEmergencyPredicted) "emergency" else "normal"
                sb.appendLine(
                    "${s.architecture},${s.sampleIndex},$actual,$predicted," +
                    "${s.groundTruthPiiCount},${s.detectedPiiCount}," +
                    "${s.totalLatencyMs},${s.emergencyDetectionMs}," +
                    "${s.anonymizationMs},${s.llmInferenceMs}," +
                    "${s.tokensGenerated},${"%.2f".format(s.tokensPerSecond)}," +
                    "${s.peakRamKb},${s.ramDeltaKb},${"%.1f".format(s.availableRamMb)}"
                )
            }
        }
        return sb.toString()
    }

    // ---- Markdown report ----

    fun toMarkdown(summaries: List<ArchitectureSummary>): String = buildString {
        appendLine("# Benchmark Report — Mental Health Chatbot Architecture Comparison")
        appendLine()
        appendLine("Generated: ${dateFormatter.format(Date())}")
        appendLine()
        appendLine("## Comparison Table")
        appendLine()

        // Table header
        val headers = listOf("Metric") + summaries.map { it.architecture }
        appendLine("| ${headers.joinToString(" | ")} |")
        appendLine("| ${headers.map { "---" }.joinToString(" | ")} |")

        fun row(label: String, values: List<String>) {
            appendLine("| $label | ${values.joinToString(" | ")} |")
        }

        // Safety
        appendLine("| **Safety** | ${summaries.map { "" }.joinToString(" | ")} |")
        row("Emergency Recall ↑",    summaries.map { it.emergencyRecallFormatted })
        row("Emergency Precision ↑", summaries.map { "${"%.1f".format(it.emergencyPrecision * 100)}%" })
        row("Emergency F1 ↑",        summaries.map { "${"%.1f".format(it.emergencyF1 * 100)}%" })
        row("Emergency Accuracy ↑",  summaries.map { "${"%.1f".format(it.emergencyAccuracy * 100)}%" })
        row("TP / FP / TN / FN",     summaries.map { "${it.emergencyTP}/${it.emergencyFP}/${it.emergencyTN}/${it.emergencyFN}" })

        // Privacy
        appendLine("| **Privacy** | ${summaries.map { "" }.joinToString(" | ")} |")
        row("Anonymization Rate ↑",  summaries.map { it.anonymizationRateFormatted })
        row("PII Leakage Rate ↓",    summaries.map { "${"%.1f".format(it.piiLeakageRate * 100)}%" })
        row("PII Detected / GT",     summaries.map { "${it.totalPiiDetected} / ${it.totalPiiInDataset}" })

        // Performance
        appendLine("| **Performance** | ${summaries.map { "" }.joinToString(" | ")} |")
        row("Mean Total Latency ↓",  summaries.map { it.meanTotalLatencyFormatted })
        row("Max Latency ↓",         summaries.map { "${it.maxTotalLatencyMs} ms" })
        row("Std Latency",           summaries.map { "${"%.0f".format(it.stdTotalLatencyMs)} ms" })
        row("Mean LLM Latency ↓",    summaries.map { "${"%.0f".format(it.meanLlmLatencyMs)} ms" })
        row("Mean Tokens/sec ↑",     summaries.map { it.meanTpsFormatted })

        // Memory
        appendLine("| **Memory** | ${summaries.map { "" }.joinToString(" | ")} |")
        row("Mean Peak RAM ↓",       summaries.map { it.meanPeakRamMbFormatted })
        row("Max Peak RAM ↓",        summaries.map { "${"%.1f".format(it.maxPeakRamKb / 1024f)} MB" })

        // Composite
        appendLine("| **Overall** | ${summaries.map { "" }.joinToString(" | ")} |")
        row("Overall Score ↑",       summaries.map { it.overallScoreFormatted })

        appendLine()
        appendLine("_↑ higher is better · ↓ lower is better_")
        appendLine()

        // Winner
        val winner = summaries.maxByOrNull { it.overallScore }
        if (winner != null) {
            appendLine("## Winner: ${winner.architecture}")
            appendLine("Overall score: ${winner.overallScoreFormatted} " +
                "(40% emergency recall + 30% anonymization rate + 30% latency score)")
            appendLine()
        }

        // Per-architecture detail
        appendLine("## Per-Architecture Details")
        for (s in summaries) {
            appendLine()
            appendLine("### ${s.architecture}")
            appendLine("- Samples: ${s.sampleCount}")
            appendLine("- Emergency: Recall=${s.emergencyRecallFormatted}, " +
                "Precision=${"%.1f".format(s.emergencyPrecision * 100)}%, " +
                "F1=${"%.1f".format(s.emergencyF1 * 100)}%")
            appendLine("- Privacy: Anon rate=${s.anonymizationRateFormatted}, " +
                "Leakage=${"%.1f".format(s.piiLeakageRate * 100)}%")
            appendLine("- Latency: mean=${s.meanTotalLatencyFormatted}, " +
                "max=${s.maxTotalLatencyMs} ms, std=${"%.0f".format(s.stdTotalLatencyMs)} ms")
            appendLine("- LLM latency: ${"%.0f".format(s.meanLlmLatencyMs)} ms avg")
            appendLine("- Throughput: ${s.meanTpsFormatted}")
            appendLine("- RAM: mean=${s.meanPeakRamMbFormatted}, " +
                "max=${"%.1f".format(s.maxPeakRamKb / 1024f)} MB")
        }
    }
}
