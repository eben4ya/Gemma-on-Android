package com.example.scigemma

import android.content.Context
import com.example.scigemma.llm.LlamaCppModel
import kotlinx.coroutines.flow.SharedFlow

/**
 * Backward-compatible adapter that wraps [LlamaCppModel].
 *
 * Existing code (LoadingScreen, ChatViewModel) calls InferenceModel.getInstance(context)
 * and uses partialResults / generateResponseAsync — this adapter preserves that contract
 * while delegating to the new llama.cpp GGUF engine.
 *
 * In later phases, ChatViewModel is refactored to use ChatArchitecture directly,
 * and this adapter can be removed.
 */
class InferenceModel private constructor() {

    private val model: LlamaCppModel = LlamaCppModel.getInstance()

    /** SharedFlow of (token, isDone) pairs — same contract as the original MediaPipe version. */
    val partialResults: SharedFlow<Pair<String, Boolean>> = model.partialResults

    /**
     * Begin streaming generation. Results are emitted to [partialResults].
     * Called from a coroutine on the IO dispatcher (same as original).
     */
    fun generateResponseAsync(prompt: String) {
        model.generateStream(prompt, maxTokens = LlamaCppModel.DEFAULT_MAX_TOKENS)
    }

    companion object {
        @Volatile
        private var instance: InferenceModel? = null

        /**
         * Return the singleton InferenceModel, loading the GGUF model on first call.
         * Context is accepted for API compatibility but is not used (LlamaCppModel
         * reads directly from the filesystem path).
         *
         * @throws IllegalArgumentException if the GGUF model file is not found
         * @throws RuntimeException if native model loading fails
         */
        fun getInstance(context: Context): InferenceModel {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val m = LlamaCppModel.getInstance()
                    if (!m.isLoaded()) {
                        m.load() // uses LlamaCppModel.MODEL_PATH
                    }
                    InferenceModel().also { instance = it }
                }
            }
        }
    }
}
