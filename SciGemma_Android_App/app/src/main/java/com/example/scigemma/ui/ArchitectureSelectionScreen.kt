package com.example.scigemma.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.scigemma.architecture.ArchitectureType

private val Purple       = Color(0xFF6A1B9A)
private val PurpleLight  = Color(0xFF9575CD)
private val PurpleSurface = Color(0xFFF3E5F5)

@Composable
fun ArchitectureSelectionScreen(
    onArchitectureSelected: (ArchitectureType) -> Unit,
    onRunBenchmark: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text  = "Select Architecture",
            style = MaterialTheme.typography.headlineSmall,
            color = Purple,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text  = "Choose how the mental health chatbot processes your messages.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF757575),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        ArchitectureType.values().forEach { arch ->
            ArchitectureCard(arch = arch, onSelect = { onArchitectureSelected(arch) })
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick  = onRunBenchmark,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = Purple)
        ) {
            Text("Run Full Benchmark (all 3 architectures)")
        }
    }
}

@Composable
private fun ArchitectureCard(
    arch: ArchitectureType,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = PurpleSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text  = arch.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = Purple,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text  = arch.description,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF424242)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick  = onSelect,
                modifier = Modifier.align(Alignment.End),
                colors   = ButtonDefaults.buttonColors(containerColor = Purple)
            ) {
                Text("Chat with ${arch.displayName}", color = Color.White)
            }
        }
    }
}
