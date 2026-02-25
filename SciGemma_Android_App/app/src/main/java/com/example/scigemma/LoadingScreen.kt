package com.example.scigemma

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.scigemma.architecture.ArchitectureType
import com.example.scigemma.llm.LlamaCppModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun LoadingRoute(
    archType: ArchitectureType,
    onModelLoaded: () -> Unit = {}
) {
    var errorMessage by remember { mutableStateOf("") }

    if (errorMessage.isNotEmpty()) {
        ErrorMessage(errorMessage)
    } else {
        LoadingIndicator(archType)
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val model = LlamaCppModel.getInstance()
                if (!model.isLoaded()) {
                    model.load()
                }
                withContext(Dispatchers.Main) { onModelLoaded() }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Failed to load model"
            }
        }
    }
}

@Composable
fun LoadingIndicator(archType: ArchitectureType) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = Color(0XFF9575CD))
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text  = "Loading model…",
            color = Color(0XFF6A1B9A),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text  = "Architecture: ${archType.displayName}",
            color = Color(0XFF9575CD),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text  = LlamaCppModel.MODEL_PATH,
            color = Color(0xFF757575),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ErrorMessage(errorMessage: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text  = "Failed to load model",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text      = errorMessage,
                color     = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                style     = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text      = "Push model with:\nadb push gemma-2b-it-q4_k_m.gguf /data/local/tmp/llm/",
                color     = Color(0xFF757575),
                textAlign = TextAlign.Center,
                style     = MaterialTheme.typography.bodySmall
            )
        }
    }
}
