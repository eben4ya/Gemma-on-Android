package com.example.scigemma.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scigemma.architecture.CloudPayload

private val Purple = Color(0xFF6A1B9A)

@Composable
fun CloudUploadDialog(
    payload: CloudPayload,
    onDismiss: () -> Unit
) {
    val p = payload.prepared.payload

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text       = "Cloud Upload Payload (Mock)",
                color      = Purple,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 400.dp)
            ) {
                // Info row
                InfoRow("Architecture", p.architectureUsed)
                InfoRow("Tokens (original)", p.tokenCountOriginal.toString())
                InfoRow("Tokens (final)", p.tokenCountFinal.toString())
                InfoRow("Summarized", if (p.wasSummarized) "Yes" else "No")
                InfoRow("Anonymization", "${payload.anonymizationMs} ms")
                if (p.wasSummarized) {
                    InfoRow("Summarization", "${payload.summarizationMs} ms")
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text       = "Payload preview:",
                    color      = Purple,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))

                Card(
                    colors    = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5)),
                    modifier  = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text       = payload.prepared.json.take(800) +
                                     if (payload.prepared.json.length > 800) "\n…" else "",
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 11.sp,
                        color      = Color(0xFF212121),
                        modifier   = Modifier
                            .padding(8.dp)
                            .horizontalScroll(rememberScrollState())
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text  = "ℹ In mock mode, no data is transmitted. " +
                            "PII has been removed before this preview.",
                    color = Color(0xFF757575),
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Purple)
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Text(
        text  = "$label: $value",
        color = Color(0xFF424242),
        fontSize = 13.sp,
        modifier = Modifier.padding(vertical = 1.dp)
    )
}
