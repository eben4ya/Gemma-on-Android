package com.example.scigemma.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scigemma.benchmark.ArchitectureSummary

private val Purple      = Color(0xFF6A1B9A)
private val PurpleLight = Color(0xFF9575CD)
private val GoldWinner  = Color(0xFFF9A825)

@Composable
fun BenchmarkResultsScreen(
    summaries: List<ArchitectureSummary>,
    saveDir: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val winner  = summaries.maxByOrNull { it.overallScore }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text       = "Benchmark Results",
            style      = MaterialTheme.typography.headlineSmall,
            color      = Purple,
            fontWeight = FontWeight.Bold
        )

        if (winner != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors   = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text      = "Best Architecture: ${winner.architecture}  " +
                                "(score ${winner.overallScoreFormatted})",
                    color     = GoldWinner,
                    fontWeight = FontWeight.Bold,
                    modifier  = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---- Comparison table (horizontal scroll for many columns) ----
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            ComparisonTable(summaries)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---- Per-architecture detail cards ----
        summaries.forEach { s ->
            ArchitectureDetailCard(s, isWinner = (s == winner))
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Results directory info
        Card(
            colors   = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Results saved to:", color = Purple, fontWeight = FontWeight.SemiBold,
                     fontSize = 13.sp)
                Text(saveDir, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                     color = Color(0xFF424242))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "adb pull /sdcard/Android/data/com.example.scigemma/files/benchmark_results/ .",
                    fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF757575)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick  = onBack,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(containerColor = Purple)
        ) { Text("Back to Architecture Selection", color = Color.White) }
    }
}

@Composable
private fun ComparisonTable(summaries: List<ArchitectureSummary>) {
    Column {
        val cols = summaries.map { it.architecture }

        TableRow("Metric", cols, isHeader = true)
        Divider(color = Purple, thickness = 1.dp)

        TableRow("**Safety**", cols.map { "" })
        TableRow("Emerg. Recall ↑",    summaries.map { it.emergencyRecallFormatted })
        TableRow("Emerg. Precision ↑", summaries.map { "${"%.1f".format(it.emergencyPrecision * 100)}%" })
        TableRow("Emerg. F1 ↑",        summaries.map { "${"%.1f".format(it.emergencyF1 * 100)}%" })
        TableRow("TP/FP/TN/FN",        summaries.map { "${it.emergencyTP}/${it.emergencyFP}/${it.emergencyTN}/${it.emergencyFN}" })

        Divider(color = Color(0xFFCCC), thickness = 0.5.dp)
        TableRow("**Privacy**", cols.map { "" })
        TableRow("Anon. Rate ↑",       summaries.map { it.anonymizationRateFormatted })
        TableRow("PII Leakage ↓",      summaries.map { "${"%.1f".format(it.piiLeakageRate * 100)}%" })

        Divider(color = Color(0xFFCCC), thickness = 0.5.dp)
        TableRow("**Performance**", cols.map { "" })
        TableRow("Mean Latency ↓",     summaries.map { it.meanTotalLatencyFormatted })
        TableRow("Max Latency ↓",      summaries.map { "${it.maxTotalLatencyMs} ms" })
        TableRow("Tokens/sec ↑",       summaries.map { it.meanTpsFormatted })

        Divider(color = Color(0xFFCCC), thickness = 0.5.dp)
        TableRow("**Memory**", cols.map { "" })
        TableRow("Mean Peak RAM ↓",    summaries.map { it.meanPeakRamMbFormatted })

        Divider(color = Purple, thickness = 1.dp)
        TableRow("Overall Score ↑",    summaries.map { it.overallScoreFormatted }, isHighlight = true)
    }
}

@Composable
private fun TableRow(
    label: String,
    values: List<String>,
    isHeader: Boolean = false,
    isHighlight: Boolean = false
) {
    val colW = 110.dp
    val bg   = when {
        isHeader    -> Color(0xFFEDE7F6)
        isHighlight -> Color(0xFFF3E5F5)
        else        -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .then(if (bg != Color.Transparent)
                Modifier.then(Modifier.fillMaxWidth()) else Modifier)
            .padding(vertical = 3.dp)
    ) {
        Text(
            text     = label.replace("**", ""),
            modifier = Modifier.width(130.dp),
            fontSize = 12.sp,
            color    = if (isHeader || label.startsWith("**")) Purple else Color(0xFF212121),
            fontWeight = if (isHeader || label.startsWith("**") || isHighlight)
                FontWeight.Bold else FontWeight.Normal
        )
        values.forEach { v ->
            Text(
                text     = v,
                modifier = Modifier.width(colW),
                fontSize = 12.sp,
                color    = if (isHighlight) Purple else Color(0xFF424242),
                fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun ArchitectureDetailCard(s: ArchitectureSummary, isWinner: Boolean) {
    Card(
        colors   = CardDefaults.cardColors(
            containerColor = if (isWinner) Color(0xFFFFF8E1) else Color(0xFFF3E5F5)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text  = s.architecture + (if (isWinner) " ★ Best" else ""),
                color = if (isWinner) GoldWinner else Purple,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(6.dp))
            DetailLine("Emergency Recall", s.emergencyRecallFormatted)
            DetailLine("Anon. Rate", s.anonymizationRateFormatted)
            DetailLine("Mean Latency", s.meanTotalLatencyFormatted)
            DetailLine("Tokens/sec", s.meanTpsFormatted)
            DetailLine("Mean Peak RAM", s.meanPeakRamMbFormatted)
            DetailLine("Overall Score", s.overallScoreFormatted)
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(text = "$label: ", fontSize = 12.sp, color = Color(0xFF757575))
        Text(text = value,      fontSize = 12.sp, color = Color(0xFF212121), fontWeight = FontWeight.Medium)
    }
}
