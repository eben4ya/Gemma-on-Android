package com.example.scigemma

import androidx.compose.runtime.toMutableStateList
import com.example.scigemma.architecture.ProcessingMetrics

const val USER_PREFIX  = "user"
const val MODEL_PREFIX = "model"

interface UiState {
    val messages: List<ChatMessage>
    val fullPrompt: String

    fun createLoadingMessage(): String
    fun appendMessage(id: String, text: String, done: Boolean = false)
    fun addMessage(
        text: String,
        author: String,
        isEmergency: Boolean = false,
        metrics: ProcessingMetrics? = null
    ): String
}

/**
 * Generic UiState implementation — not Gemma-specific.
 */
class ChatUiState(
    messages: List<ChatMessage> = emptyList()
) : UiState {
    private val _messages: MutableList<ChatMessage> = messages.toMutableStateList()
    override val messages: List<ChatMessage> get() = _messages.reversed()

    override val fullPrompt: String
        get() = _messages.joinToString(separator = "\n") { it.message }

    override fun createLoadingMessage(): String {
        val msg = ChatMessage(author = MODEL_PREFIX, isLoading = true)
        _messages.add(msg)
        return msg.id
    }

    override fun appendMessage(id: String, text: String, done: Boolean) {
        val index = _messages.indexOfFirst { it.id == id }
        if (index != -1) {
            _messages[index] = _messages[index].copy(
                message   = _messages[index].message + text,
                isLoading = false
            )
        }
    }

    override fun addMessage(
        text: String,
        author: String,
        isEmergency: Boolean,
        metrics: ProcessingMetrics?
    ): String {
        val msg = ChatMessage(message = text, author = author,
                              isEmergency = isEmergency, metrics = metrics)
        _messages.add(msg)
        return msg.id
    }
}

/**
 * Gemma-specific UiState with turn markers (<start_of_turn> / <end_of_turn>).
 * Keeps only the last [MAX_HISTORY] turns in the context window.
 */
class GemmaUiState(
    messages: List<ChatMessage> = emptyList()
) : UiState {
    private val START_TURN = "<start_of_turn>"
    private val END_TURN   = "<end_of_turn>"
    private val MAX_HISTORY = 6   // last 3 user + 3 model turns

    private val _messages: MutableList<ChatMessage> = messages.toMutableStateList()

    override val messages: List<ChatMessage>
        get() = _messages.map {
            it.copy(
                message = it.message
                    .replace("$START_TURN${it.author}\n", "")
                    .replace(END_TURN, "")
                    .trim()
            )
        }.reversed()

    override val fullPrompt: String
        get() = _messages.takeLast(MAX_HISTORY).joinToString("\n") { it.message }

    override fun createLoadingMessage(): String {
        val msg = ChatMessage(author = MODEL_PREFIX, isLoading = true)
        _messages.add(msg)
        return msg.id
    }

    fun appendFirstMessage(id: String, text: String) {
        appendMessage(id, "$START_TURN$MODEL_PREFIX\n$text", false)
    }

    override fun appendMessage(id: String, text: String, done: Boolean) {
        val index = _messages.indexOfFirst { it.id == id }
        if (index != -1) {
            val newText = _messages[index].message + text +
                          (if (done) END_TURN else "")
            _messages[index] = _messages[index].copy(message = newText, isLoading = false)
        }
    }

    override fun addMessage(
        text: String,
        author: String,
        isEmergency: Boolean,
        metrics: ProcessingMetrics?
    ): String {
        val msg = ChatMessage(
            message     = "$START_TURN$author\n$text$END_TURN",
            author      = author,
            isEmergency = isEmergency,
            metrics     = metrics
        )
        _messages.add(msg)
        return msg.id
    }

    /** Return conversation history as (role, text) pairs for the architecture modules. */
    fun toHistory(): List<Pair<String, String>> =
        _messages.takeLast(MAX_HISTORY).map { msg ->
            val text = msg.message
                .replace("$START_TURN${msg.author}\n", "")
                .replace(END_TURN, "")
                .trim()
            Pair(msg.author, text)
        }
}
