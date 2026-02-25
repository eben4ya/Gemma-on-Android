package com.example.scigemma

import com.example.scigemma.architecture.ProcessingMetrics
import java.util.UUID

/**
 * Represents a single chat message in the conversation.
 *
 * New fields vs the original:
 *   isEmergency — whether this message triggered a crisis alert
 *   metrics     — per-message timing and memory data (null for user messages)
 *   timestamp   — wall-clock time of creation
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val message: String = "",
    val author: String,
    val isLoading: Boolean = false,
    val isEmergency: Boolean = false,
    val metrics: ProcessingMetrics? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isFromUser: Boolean
        get() = author == USER_PREFIX
}
