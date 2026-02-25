package com.example.scigemma.benchmark

/**
 * Data classes for the benchmarking system.
 *
 * BenchmarkSample  — per-sample raw result from one architecture run
 * ArchitectureSummary — aggregated statistics across all samples for one architecture
 */

// ---- Per-sample result ----

data class BenchmarkSample(
    // Identification
    val architecture: String,
    val sampleIndex: Int,
    val inputText: String,

    // Response
    val response: String,
    val anonymizedText: String,

    // Safety labels
    val isEmergencyPredicted: Boolean,
    val isEmergencyActual: Boolean,

    // Privacy labels
    val groundTruthPiiCount: Int,   // PII entities in ground-truth annotation
    val detectedPiiCount: Int,      // Entities replaced by the anonymizer

    // Timing (ms)
    val totalLatencyMs: Long,
    val emergencyDetectionMs: Long,
    val anonymizationMs: Long,
    val llmInferenceMs: Long,

    // Throughput
    val tokensGenerated: Int,
    val tokensPerSecond: Float,

    // Memory
    val peakRamKb: Int,
    val ramDeltaKb: Int,
    val availableRamMb: Float       // System-wide free RAM at time of measurement
)

// ---- Aggregated per-architecture summary ----

data class ArchitectureSummary(
    val architecture: String,
    val sampleCount: Int,

    // --- Safety metrics ---
    val emergencyTP: Int,   // True Positive  (predicted YES, actual YES)
    val emergencyFP: Int,   // False Positive (predicted YES, actual NO)
    val emergencyTN: Int,   // True Negative  (predicted NO,  actual NO)
    val emergencyFN: Int,   // False Negative (predicted NO,  actual YES)
    val emergencyRecall: Float,     // TP / (TP + FN)  — most important for safety
    val emergencyPrecision: Float,  // TP / (TP + FP)
    val emergencyF1: Float,         // 2 * P * R / (P + R)
    val emergencyAccuracy: Float,   // (TP + TN) / total

    // --- Privacy metrics ---
    val totalPiiInDataset: Int,
    val totalPiiDetected: Int,
    val anonymizationRate: Float,   // totalPiiDetected / totalPiiInDataset
    val piiLeakageRate: Float,      // 1 - anonymizationRate

    // --- Performance metrics ---
    val meanTotalLatencyMs: Float,
    val maxTotalLatencyMs: Long,
    val minTotalLatencyMs: Long,
    val stdTotalLatencyMs: Float,
    val meanLlmLatencyMs: Float,
    val meanEmergencyDetectionMs: Float,
    val meanAnonymizationMs: Float,
    val meanTokensPerSecond: Float,

    // --- Memory metrics ---
    val meanPeakRamKb: Float,
    val maxPeakRamKb: Int,
    val meanRamDeltaKb: Float,

    // --- Composite score ---
    // Weighted: 40% safety (recall) + 30% privacy (anon rate) + 30% performance (latency)
    val overallScore: Float
) {
    // Formatted accessors for display
    val meanTotalLatencyFormatted: String get() = "${"%.0f".format(meanTotalLatencyMs)} ms"
    val meanPeakRamMbFormatted: String    get() = "${"%.1f".format(meanPeakRamKb / 1024f)} MB"
    val emergencyRecallFormatted: String  get() = "${"%.1f".format(emergencyRecall * 100)}%"
    val anonymizationRateFormatted: String get() = "${"%.1f".format(anonymizationRate * 100)}%"
    val meanTpsFormatted: String          get() = "${"%.1f".format(meanTokensPerSecond)} tok/s"
    val overallScoreFormatted: String     get() = "%.3f".format(overallScore)
}
