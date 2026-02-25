package com.example.scigemma

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.scigemma.architecture.ArchitectureType
import com.example.scigemma.architecture.ChatArchitecture
import com.example.scigemma.architecture.CloudPayload
import com.example.scigemma.architecture.MultitaskArchitecture
import com.example.scigemma.architecture.PipelineArchitecture
import com.example.scigemma.architecture.ProcessingMetrics
import com.example.scigemma.architecture.SeparateArchitecture
import com.example.scigemma.llm.LlamaCppModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.launch

/**
 * ViewModel for the chat screen.
 *
 * Delegates inference to the selected [ChatArchitecture].
 * Streaming tokens come from [LlamaCppModel.partialResults] (same flow as before,
 * since all architectures ultimately call [LlamaCppModel.generateStream]).
 *
 * Additional state exposed:
 *   [lastMetrics]      — ProcessingMetrics for the most recent model turn
 *   [emergencyEvents]  — SharedFlow that emits true when a crisis is detected
 *   [architectureType] — The active architecture
 */
class ChatViewModel(
    val architectureType: ArchitectureType,
    private val architecture: ChatArchitecture
) : ViewModel() {

    private val _uiState = MutableStateFlow<GemmaUiState>(GemmaUiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _textInputEnabled = MutableStateFlow(true)
    val isTextInputEnabled: StateFlow<Boolean> = _textInputEnabled.asStateFlow()

    private val _lastMetrics = MutableStateFlow<ProcessingMetrics?>(null)
    val lastMetrics: StateFlow<ProcessingMetrics?> = _lastMetrics.asStateFlow()

    private val _emergencyEvents = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val emergencyEvents: SharedFlow<Boolean> = _emergencyEvents.asSharedFlow()

    private val _cloudPayload = MutableStateFlow<CloudPayload?>(null)
    val cloudPayload: StateFlow<CloudPayload?> = _cloudPayload.asStateFlow()

    private val model = LlamaCppModel.getInstance()

    // ---- Send a user message ----

    fun sendMessage(userMessage: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val history = _uiState.value.toHistory()

            _uiState.value.addMessage(userMessage, USER_PREFIX)
            val currentMessageId = _uiState.value.createLoadingMessage()
            setInputEnabled(false)

            try {
                // Start streaming generation via the architecture
                architecture.processMessageStream(
                    userInput = userMessage,
                    history   = history,
                    onResult  = { result ->
                        _lastMetrics.value = result.metrics
                        if (result.isEmergency) {
                            _emergencyEvents.tryEmit(true)
                        }
                    }
                )

                // Collect streaming tokens from the model's SharedFlow
                model.partialResults.collectIndexed { index, (partialResult, done) ->
                    if (index == 0) {
                        _uiState.value.appendFirstMessage(currentMessageId, partialResult)
                    } else {
                        _uiState.value.appendMessage(currentMessageId, partialResult, done)
                    }
                    if (done) {
                        setInputEnabled(true)
                        return@collectIndexed
                    }
                }
            } catch (e: Exception) {
                _uiState.value.addMessage(
                    text   = e.localizedMessage ?: "Unknown error during inference",
                    author = MODEL_PREFIX
                )
                setInputEnabled(true)
            }
        }
    }

    // ---- Cloud upload preparation ----

    fun prepareCloudPayload() {
        viewModelScope.launch(Dispatchers.IO) {
            val history = _uiState.value.toHistory()
            if (history.isEmpty()) return@launch
            val payload = architecture.prepareForCloud(history)
            _cloudPayload.value = payload
        }
    }

    fun clearCloudPayload() { _cloudPayload.value = null }

    // ---- Helpers ----

    private fun setInputEnabled(isEnabled: Boolean) {
        _textInputEnabled.value = isEnabled
    }

    // ---- Factory ----

    companion object {
        fun getFactory(context: Context, archType: ArchitectureType) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    val arch: ChatArchitecture = when (archType) {
                        ArchitectureType.MULTITASK -> MultitaskArchitecture()
                        ArchitectureType.SEPARATE  -> SeparateArchitecture()
                        ArchitectureType.PIPELINE  -> PipelineArchitecture()
                    }
                    return ChatViewModel(archType, arch) as T
                }
            }
    }
}
