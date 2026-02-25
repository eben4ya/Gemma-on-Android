package com.example.scigemma.benchmark

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import com.example.scigemma.architecture.ChatArchitecture
import com.example.scigemma.features.Anonymizer
import kotlin.math.sqrt

/**
 * Runs the synthetic benchmark dataset through each architecture
 * and computes aggregated [ArchitectureSummary] statistics.
 *
 * Usage:
 *   val runner = BenchmarkRunner(context)
 *   runner.onProgress = { msg -> /* update UI */ }
 *   val (samples, summary) = runner.run(architecture, SyntheticDataset.ALL)
 *
 * All methods must be called from a background thread (IO dispatcher).
 */
class BenchmarkRunner(private val context: Context) {

    /** Progress callback: called with a human-readable status string. */
    var onProgress: ((String) -> Unit)? = null

    /**
     * Run [dataset] through [architecture] and return per-sample results
     * along with an aggregated summary.
     */
    fun run(
        architecture: ChatArchitecture,
        dataset: List<TestSample>
    ): Pair<List<BenchmarkSample>, ArchitectureSummary> {

        onProgress?.invoke("Initializing ${architecture.type.displayName}…")
        architecture.initialize()

        val results = mutableListOf<BenchmarkSample>()

        for ((index, sample) in dataset.withIndex()) {
            onProgress?.invoke(
                "[${architecture.type.shortName}] Sample ${index + 1}/${dataset.size}: " +
                sample.text.take(40) + "…"
            )

            val availRamMb = getAvailableRamMb()
            val result = architecture.processMessageSync(sample.text, emptyList())

            // Count PII entities detected by the anonymizer in this sample
            val detectedPii = Anonymizer.anonymize(sample.text).replacementCount

            results.add(
                BenchmarkSample(
                    architecture            = architecture.type.displayName,
                    sampleIndex             = index,
                    inputText               = sample.text,
                    response                = result.response,
                    anonymizedText          = result.anonymizedInput,
                    isEmergencyPredicted    = result.isEmergency,
                    isEmergencyActual       = sample.isEmergency,
                    groundTruthPiiCount     = sample.piiEntityCount,
                    detectedPiiCount        = detectedPii,
                    totalLatencyMs          = result.metrics.totalLatencyMs,
                    emergencyDetectionMs    = result.metrics.emergencyDetectionMs,
                    anonymizationMs         = result.metrics.anonymizationMs,
                    llmInferenceMs          = result.metrics.llmInferenceMs,
                    tokensGenerated         = result.metrics.tokensGenerated,
                    tokensPerSecond         = result.metrics.tokensPerSecond,
                    peakRamKb               = result.metrics.peakRamKb,
                    ramDeltaKb              = result.metrics.ramDeltaKb,
                    availableRamMb          = availRamMb
                )
            )
        }

        onProgress?.invoke("Computing summary for ${architecture.type.displayName}…")
        val summary = computeSummary(results)
        return Pair(results, summary)
    }

    // ---- Summary computation ----

    fun computeSummary(samples: List<BenchmarkSample>): ArchitectureSummary {
        if (samples.isEmpty()) {
            return emptyArchitectureSummary(samples.firstOrNull()?.architecture ?: "Unknown")
        }

        val arch = samples.first().architecture

        // -- Safety metrics --
        val tp = samples.count {  it.isEmergencyPredicted &&  it.isEmergencyActual }
        val fp = samples.count {  it.isEmergencyPredicted && !it.isEmergencyActual }
        val tn = samples.count { !it.isEmergencyPredicted && !it.isEmergencyActual }
        val fn = samples.count { !it.isEmergencyPredicted &&  it.isEmergencyActual }

        val recall    = if (tp + fn > 0) tp.toFloat() / (tp + fn) else 0f
        val precision = if (tp + fp > 0) tp.toFloat() / (tp + fp) else 0f
        val f1        = if (precision + recall > 0) 2 * precision * recall / (precision + recall) else 0f
        val accuracy  = (tp + tn).toFloat() / samples.size

        // -- Privacy metrics --
        val totalPiiGt   = samples.sumOf { it.groundTruthPiiCount }
        val totalPiiDet  = samples.sumOf { it.detectedPiiCount }
        // Anonymization rate = detected / ground-truth (capped at 1.0)
        val anonRate     = if (totalPiiGt > 0) (totalPiiDet.toFloat() / totalPiiGt).coerceAtMost(1f) else 1f
        val leakageRate  = 1f - anonRate

        // -- Performance metrics --
        val latencies  = samples.map { it.totalLatencyMs }
        val meanLat    = latencies.average().toFloat()
        val maxLat     = latencies.max()
        val minLat     = latencies.min()
        val stdLat     = stdDev(latencies.map { it.toFloat() })

        val meanLlmLat = samples.map { it.llmInferenceMs }.average().toFloat()
        val meanEmgLat = samples.map { it.emergencyDetectionMs }.average().toFloat()
        val meanAnoLat = samples.map { it.anonymizationMs }.average().toFloat()
        val meanTps    = samples.map { it.tokensPerSecond }.average().toFloat()

        // -- Memory metrics --
        val meanRamKb  = samples.map { it.peakRamKb }.average().toFloat()
        val maxRamKb   = samples.maxOf { it.peakRamKb }
        val meanDeltaKb = samples.map { it.ramDeltaKb }.average().toFloat()

        // -- Composite score --
        // Normalize latency: lower is better → use 1 / (1 + meanLat_sec)
        val latScore  = 1f / (1f + meanLat / 1000f)
        // Weighted: 40% recall (safety) + 30% anon rate (privacy) + 30% performance (latency)
        val composite = 0.40f * recall + 0.30f * anonRate + 0.30f * latScore

        return ArchitectureSummary(
            architecture              = arch,
            sampleCount               = samples.size,
            emergencyTP               = tp,
            emergencyFP               = fp,
            emergencyTN               = tn,
            emergencyFN               = fn,
            emergencyRecall           = recall,
            emergencyPrecision        = precision,
            emergencyF1               = f1,
            emergencyAccuracy         = accuracy,
            totalPiiInDataset         = totalPiiGt,
            totalPiiDetected          = totalPiiDet,
            anonymizationRate         = anonRate,
            piiLeakageRate            = leakageRate,
            meanTotalLatencyMs        = meanLat,
            maxTotalLatencyMs         = maxLat,
            minTotalLatencyMs         = minLat,
            stdTotalLatencyMs         = stdLat,
            meanLlmLatencyMs          = meanLlmLat,
            meanEmergencyDetectionMs  = meanEmgLat,
            meanAnonymizationMs       = meanAnoLat,
            meanTokensPerSecond       = meanTps,
            meanPeakRamKb             = meanRamKb,
            maxPeakRamKb              = maxRamKb,
            meanRamDeltaKb            = meanDeltaKb,
            overallScore              = composite
        )
    }

    // ---- Utilities ----

    private fun stdDev(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val mean = values.average().toFloat()
        val variance = values.sumOf { ((it - mean) * (it - mean)).toDouble() } / values.size
        return sqrt(variance.toFloat())
    }

    private fun getAvailableRamMb(): Float {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.availMem / (1024f * 1024f)
    }

    private fun emptyArchitectureSummary(arch: String) = ArchitectureSummary(
        architecture = arch, sampleCount = 0,
        emergencyTP = 0, emergencyFP = 0, emergencyTN = 0, emergencyFN = 0,
        emergencyRecall = 0f, emergencyPrecision = 0f, emergencyF1 = 0f, emergencyAccuracy = 0f,
        totalPiiInDataset = 0, totalPiiDetected = 0, anonymizationRate = 0f, piiLeakageRate = 1f,
        meanTotalLatencyMs = 0f, maxTotalLatencyMs = 0L, minTotalLatencyMs = 0L,
        stdTotalLatencyMs = 0f, meanLlmLatencyMs = 0f, meanEmergencyDetectionMs = 0f,
        meanAnonymizationMs = 0f, meanTokensPerSecond = 0f,
        meanPeakRamKb = 0f, maxPeakRamKb = 0, meanRamDeltaKb = 0f, overallScore = 0f
    )
}
