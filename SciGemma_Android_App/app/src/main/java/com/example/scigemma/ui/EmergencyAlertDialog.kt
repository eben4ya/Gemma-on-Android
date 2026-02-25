package com.example.scigemma.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.scigemma.features.EmergencyDetector

private val EmergencyRed  = Color(0xFFD32F2F)
private val EmergencyBg   = Color(0xFFFFEBEE)

@Composable
fun EmergencyAlertDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text       = "Crisis Support Available",
                color      = EmergencyRed,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text  = "It seems you may be going through a difficult time. " +
                            "Please consider reaching out to a crisis support line:",
                    color = Color(0xFF212121)
                )
                Spacer(modifier = Modifier.height(12.dp))
                EmergencyDetector.CRISIS_HOTLINES.forEach { (region, info) ->
                    Text(
                        text     = "• $region: $info",
                        color    = Color(0xFF424242),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text  = "You are not alone. Help is available.",
                    color = EmergencyRed,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK, I understand", color = EmergencyRed)
            }
        },
        containerColor = EmergencyBg
    )
}
