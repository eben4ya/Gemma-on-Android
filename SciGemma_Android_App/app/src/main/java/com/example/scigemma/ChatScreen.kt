package com.example.scigemma

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scigemma.architecture.ArchitectureType
import com.example.scigemma.architecture.ProcessingMetrics
import com.example.scigemma.ui.CloudUploadDialog
import com.example.scigemma.ui.EmergencyAlertDialog

private val Purple        = Color(0xFF6A1B9A)
private val PurpleLight   = Color(0xFF9575CD)
private val EmergencyRed  = Color(0xFFD32F2F)

// ---- Route wrapper ----

@Composable
internal fun ChatRoute(
    archType: ArchitectureType,
    chatViewModel: ChatViewModel = viewModel(
        factory = ChatViewModel.getFactory(
            LocalContext.current.applicationContext, archType
        )
    )
) {
    val uiState          by chatViewModel.uiState.collectAsStateWithLifecycle()
    val textInputEnabled by chatViewModel.isTextInputEnabled.collectAsStateWithLifecycle()
    val lastMetrics      by chatViewModel.lastMetrics.collectAsStateWithLifecycle()
    val cloudPayload     by chatViewModel.cloudPayload.collectAsStateWithLifecycle()

    var showEmergency by rememberSaveable { mutableStateOf(false) }
    var showCloud     by rememberSaveable { mutableStateOf(false) }

    // Collect emergency events
    LaunchedEffect(Unit) {
        chatViewModel.emergencyEvents.collect { showEmergency = true }
    }

    // Collect cloud payload readiness
    LaunchedEffect(cloudPayload) {
        if (cloudPayload != null) showCloud = true
    }

    // Dialogs
    if (showEmergency) {
        EmergencyAlertDialog(onDismiss = { showEmergency = false })
    }
    if (showCloud && cloudPayload != null) {
        CloudUploadDialog(
            payload   = cloudPayload!!,
            onDismiss = {
                showCloud = false
                chatViewModel.clearCloudPayload()
            }
        )
    }

    ChatScreen(
        archType         = archType,
        uiState          = uiState,
        textInputEnabled = textInputEnabled,
        lastMetrics      = lastMetrics,
        onSendMessage    = { chatViewModel.sendMessage(it) },
        onCloudUpload    = { chatViewModel.prepareCloudPayload() }
    )
}

// ---- Main chat screen ----

@Composable
fun ChatScreen(
    archType: ArchitectureType,
    uiState: UiState,
    textInputEnabled: Boolean = true,
    lastMetrics: ProcessingMetrics?,
    onSendMessage: (String) -> Unit,
    onCloudUpload: () -> Unit
) {
    var userMessage by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Bottom
    ) {
        // Architecture badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF3E5F5))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text  = "Architecture: ",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF757575)
            )
            Text(
                text  = archType.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = Purple,
                fontFamily = FontFamily.Monospace
            )
        }

        // Message list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            reverseLayout = true
        ) {
            items(uiState.messages) { chat ->
                ChatItem(chat)
            }
        }

        // Live metrics strip (shown after first inference)
        lastMetrics?.let { m ->
            MetricsStrip(m)
        }

        // Input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor     = Purple,
                    unfocusedTextColor   = Purple,
                    focusedBorderColor   = Purple,
                    unfocusedBorderColor = Purple,
                    focusedLabelColor    = Purple,
                    unfocusedLabelColor  = Purple
                ),
                value = userMessage,
                onValueChange = { userMessage = it },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                label    = { Text("Message") },
                modifier = Modifier.weight(1f),
                enabled  = textInputEnabled
            )

            // Cloud upload button
            IconButton(
                onClick  = onCloudUpload,
                enabled  = textInputEnabled,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Icon(Icons.Default.Cloud, contentDescription = "Send to cloud", tint = PurpleLight)
            }

            // Send button
            IconButton(
                onClick = {
                    if (userMessage.isNotBlank()) {
                        onSendMessage(userMessage)
                        userMessage = ""
                    }
                },
                enabled = textInputEnabled
            ) {
                Icon(Icons.AutoMirrored.Default.Send, contentDescription = "Send", tint = Purple)
            }
        }
    }
}

// ---- Per-message bubble ----

@Composable
fun ChatItem(chatMessage: ChatMessage) {
    val bgColor    = if (chatMessage.isFromUser) Purple else PurpleLight
    val bubbleShape = if (chatMessage.isFromUser)
        RoundedCornerShape(20.dp, 4.dp, 20.dp, 20.dp)
    else
        RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)

    Column(
        horizontalAlignment = if (chatMessage.isFromUser) Alignment.End else Alignment.Start,
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .fillMaxWidth()
    ) {
        Text(
            text     = if (chatMessage.isFromUser) "You" else "Model",
            style    = MaterialTheme.typography.bodySmall,
            color    = Color(0xFF424242),
            modifier = Modifier.padding(bottom = 2.dp)
        )

        // Emergency indicator
        if (chatMessage.isEmergency) {
            Text(
                text     = "⚠ Crisis detected",
                style    = MaterialTheme.typography.labelSmall,
                color    = EmergencyRed,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        Row {
            BoxWithConstraints {
                Card(
                    colors   = CardDefaults.cardColors(containerColor = bgColor),
                    shape    = bubbleShape,
                    modifier = Modifier.widthIn(0.dp, maxWidth * 0.9f)
                ) {
                    if (chatMessage.isLoading) {
                        CircularProgressIndicator(
                            color    = Purple,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        Text(
                            text     = cleanDisplayText(chatMessage.message),
                            modifier = Modifier.padding(12.dp),
                            color    = Color.White
                        )
                    }
                }
            }
        }
    }
}

// ---- Live metrics strip ----

@Composable
fun MetricsStrip(m: ProcessingMetrics) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF3E5F5))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        MetricChip("${m.totalLatencyMs} ms")
        MetricChip(m.tokensPerSecondFormatted)
        MetricChip(m.ramFormatted)
        MetricChip("${m.emergencyDetectionMs}/${m.anonymizationMs}/${m.llmInferenceMs} ms")
    }
}

@Composable
private fun MetricChip(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.labelSmall,
        color    = Purple,
        fontSize = 10.sp
    )
}

// ---- Helpers ----

private fun cleanDisplayText(text: String): String = text
    .replace("<start_of_turn>", "")
    .replace("</start_of_turn>", "")
    .replace("<end_of_turn>", "")
    .replace("</end_of_turn>", "")
    .trim()
