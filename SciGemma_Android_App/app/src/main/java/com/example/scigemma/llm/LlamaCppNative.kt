package com.example.scigemma.llm

/**
 * JNI declarations for the llama.cpp bridge library (libllama_bridge.so).
 * The native library is built from app/src/main/cpp/llama_bridge.cpp
 * using CMake + Android NDK.
 *
 * Setup (run once in the repo root):
 *   git submodule add https://github.com/ggerganov/llama.cpp \
 *       app/src/main/cpp/llama.cpp
 *   git submodule update --init --recursive
 */
object LlamaCppNative {

    init {
        System.loadLibrary("llama_bridge")
    }

    /**
     * Load a GGUF model file and return an opaque handle.
     * Returns 0 on failure.
     *
     * @param modelPath Absolute path to the .gguf file on the device
     * @param nCtx      Context window size in tokens (2048 recommended for Nokia 5.4)
     * @param nThreads  Number of CPU threads (4 for Snapdragon 662 big cores)
     */
    external fun nativeLoadModel(modelPath: String, nCtx: Int, nThreads: Int): Long

    /**
     * Generate a complete response synchronously.
     * Blocks until generation is complete — use only from a background thread.
     * Preferred for benchmarking (clean timing).
     *
     * @param handle    Handle from [nativeLoadModel]
     * @param prompt    Full formatted prompt string
     * @param maxTokens Maximum number of tokens to generate
     * @return          Generated text (not including the prompt)
     */
    external fun nativeGenerateSync(handle: Long, prompt: String, maxTokens: Int): String

    /**
     * Generate a response and stream each token via [callback].
     * Blocks on the calling thread; callback is invoked synchronously for each token.
     * Preferred for the chat UI (real-time display).
     *
     * @param handle    Handle from [nativeLoadModel]
     * @param prompt    Full formatted prompt string
     * @param maxTokens Maximum number of tokens to generate
     * @param callback  Receives (token: String, isDone: Boolean) for each piece
     */
    external fun nativeGenerateStream(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        callback: TokenCallback
    )

    /**
     * Count the number of tokens in a text string.
     * Used to decide whether summarization is needed before cloud upload.
     */
    external fun nativeTokenCount(handle: Long, text: String): Int

    /**
     * Free all resources associated with the model handle.
     */
    external fun nativeFreeModel(handle: Long)
}

/**
 * Functional interface for streaming token callbacks from JNI.
 * Called once per generated token; [isDone] is true on the final call.
 */
fun interface TokenCallback {
    fun onToken(token: String, isDone: Boolean)
}
