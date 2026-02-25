package com.example.scigemma.llm

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

/**
 * High-level Kotlin wrapper for the llama.cpp GGUF inference engine.
 *
 * Singleton: call [getInstance] to get the shared instance.
 *
 * Streaming (for Chat UI):
 *   Call [generateStream] from a background thread; observe [partialResults].
 *   Each emission is Pair(token_text, isDone).
 *
 * Synchronous (for Benchmarking):
 *   Call [generateSync] from a background thread; it blocks and returns
 *   the complete response string.
 *
 * Model file location on device:
 *   Push via: adb push gemma-2b-it-q4_k_m.gguf /data/local/tmp/llm/
 */
class LlamaCppModel private constructor() {

    private var handle: Long = 0L

    // SharedFlow used by the Chat UI to collect streaming tokens
    private val _partialResults = MutableSharedFlow<Pair<String, Boolean>>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val partialResults: SharedFlow<Pair<String, Boolean>> = _partialResults.asSharedFlow()

    // ----------------------------------------------------------------
    // Lifecycle
    // ----------------------------------------------------------------

    /**
     * Load the GGUF model into memory. Call once, typically on a background thread.
     *
     * @param modelPath Absolute path to the .gguf file (default: [MODEL_PATH])
     * @param nCtx      Context window in tokens (2048 is safe on Nokia 5.4 / 6 GB RAM)
     * @param nThreads  CPU threads to use (4 = big cores on Snapdragon 662)
     */
    fun load(
        modelPath: String = MODEL_PATH,
        nCtx: Int = DEFAULT_N_CTX,
        nThreads: Int = DEFAULT_N_THREADS
    ) {
        if (!File(modelPath).exists()) {
            throw IllegalArgumentException(
                "GGUF model not found at: $modelPath\n" +
                "Push model with: adb push <model.gguf> /data/local/tmp/llm/"
            )
        }
        if (handle != 0L) unload()
        handle = LlamaCppNative.nativeLoadModel(modelPath, nCtx, nThreads)
        if (handle == 0L) {
            throw RuntimeException("llama.cpp failed to load model from: $modelPath")
        }
    }

    fun isLoaded(): Boolean = handle != 0L

    /** Release native memory. Safe to call multiple times. */
    fun unload() {
        if (handle != 0L) {
            LlamaCppNative.nativeFreeModel(handle)
            handle = 0L
        }
    }

    // ----------------------------------------------------------------
    // Inference – Streaming (Chat UI)
    // ----------------------------------------------------------------

    /**
     * Generate a response and emit each token to [partialResults].
     * MUST be called from a background thread (not the main thread).
     *
     * @param prompt    Fully formatted prompt (use [PromptFormatter])
     * @param maxTokens Maximum tokens to generate
     */
    fun generateStream(prompt: String, maxTokens: Int = DEFAULT_MAX_TOKENS) {
        requireLoaded()
        LlamaCppNative.nativeGenerateStream(handle, prompt, maxTokens) { token, isDone ->
            _partialResults.tryEmit(token to isDone)
        }
    }

    // ----------------------------------------------------------------
    // Inference – Synchronous (Benchmarking / Summarization)
    // ----------------------------------------------------------------

    /**
     * Generate a complete response synchronously.
     * MUST be called from a background thread.
     *
     * @return The generated text (excluding the prompt)
     */
    fun generateSync(prompt: String, maxTokens: Int = DEFAULT_MAX_TOKENS): String {
        requireLoaded()
        return LlamaCppNative.nativeGenerateSync(handle, prompt, maxTokens)
    }

    // ----------------------------------------------------------------
    // Utilities
    // ----------------------------------------------------------------

    /**
     * Count tokens in [text]. Used to decide whether summarization is needed
     * before cloud upload (threshold: 512 tokens).
     */
    fun tokenCount(text: String): Int {
        requireLoaded()
        return LlamaCppNative.nativeTokenCount(handle, text)
    }

    private fun requireLoaded() {
        check(handle != 0L) { "Model is not loaded. Call load() first." }
    }

    // ----------------------------------------------------------------
    // Singleton
    // ----------------------------------------------------------------

    companion object {
        /** Default GGUF model path on the device. Push with adb. */
        const val MODEL_PATH = "/data/local/tmp/llm/gemma-2b-it-q4_k_m.gguf"

        /** Safe context size for Nokia 5.4 (Snapdragon 662, 6 GB RAM). */
        const val DEFAULT_N_CTX = 2048

        /** Use 4 big cores of the Snapdragon 662 (Cortex-A73). */
        const val DEFAULT_N_THREADS = 4

        /** Default max tokens generated per turn. */
        const val DEFAULT_MAX_TOKENS = 512

        @Volatile private var instance: LlamaCppModel? = null

        fun getInstance(): LlamaCppModel =
            instance ?: synchronized(this) {
                instance ?: LlamaCppModel().also { instance = it }
            }
    }
}
