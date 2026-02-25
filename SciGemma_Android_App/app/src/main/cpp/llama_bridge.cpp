/**
 * llama_bridge.cpp
 *
 * JNI bridge between Kotlin and llama.cpp for GGUF model inference.
 * Supports: model load/unload, synchronous generation, streaming generation,
 * and token counting.
 *
 * Requires: llama.cpp cloned at app/src/main/cpp/llama.cpp/
 *   git submodule add https://github.com/ggerganov/llama.cpp \
 *       app/src/main/cpp/llama.cpp
 */

#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "LlamaBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ---- Internal state held per loaded model ----
struct LlamaState {
    llama_model*   model;
    llama_context* ctx;
    llama_sampler* sampler;
    int            n_ctx;
    int            n_threads;
};

// ---- Helpers ----

// Tokenize a UTF-8 string to llama tokens.
static std::vector<llama_token> tokenize_text(
        llama_model* model, const std::string& text, bool add_bos)
{
    int n = -llama_tokenize(
        model, text.c_str(), (int32_t)text.size(),
        nullptr, 0, add_bos, /*special=*/true);
    std::vector<llama_token> result(n);
    llama_tokenize(
        model, text.c_str(), (int32_t)text.size(),
        result.data(), n, add_bos, /*special=*/true);
    return result;
}

// Convert a single token id to its UTF-8 string representation.
static std::string token_to_str(llama_model* model, llama_token token)
{
    std::string buf(16, '\0');
    int n = llama_token_to_piece(model, token, buf.data(), (int)buf.size(), 0, true);
    if (n < 0) {                         // buffer was too small
        buf.resize(-n);
        llama_token_to_piece(model, token, buf.data(), (int)buf.size(), 0, true);
        buf.resize(-n);
    } else {
        buf.resize(n);
    }
    return buf;
}

// Add one token to a batch (manual helper, avoids dependency on common.h).
static void batch_add_token(llama_batch& batch, llama_token token,
                            llama_pos pos, bool compute_logits)
{
    batch.token    [batch.n_tokens] = token;
    batch.pos      [batch.n_tokens] = pos;
    batch.n_seq_id [batch.n_tokens] = 1;
    batch.seq_id   [batch.n_tokens][0] = 0;
    batch.logits   [batch.n_tokens] = compute_logits ? 1 : 0;
    batch.n_tokens++;
}

// Process a full prompt through the KV cache.
// Returns the position after the last prompt token (used as base for generation).
static int eval_prompt(llama_context* ctx, const std::vector<llama_token>& tokens)
{
    int n_prompt = (int)tokens.size();
    int n_ctx    = (int)llama_n_ctx(ctx);
    int batch_sz = n_prompt < 512 ? n_prompt : 512; // process in chunks if large

    llama_kv_cache_clear(ctx);

    for (int i = 0; i < n_prompt; ) {
        int chunk = (n_prompt - i < batch_sz) ? n_prompt - i : batch_sz;
        llama_batch batch = llama_batch_init(chunk, 0, 1);

        for (int j = 0; j < chunk; j++) {
            bool last = (i + j == n_prompt - 1);
            batch_add_token(batch, tokens[i + j], i + j, last);
        }

        if (llama_decode(ctx, batch) != 0) {
            LOGE("eval_prompt: llama_decode failed at offset %d", i);
            llama_batch_free(batch);
            return -1;
        }
        llama_batch_free(batch);
        i += chunk;
    }
    return n_prompt;
}

// ---- JNI functions ----

extern "C" {

// ----------------------------------------------------------------
// Load model and return an opaque handle (pointer cast to jlong).
// Returns 0 on failure.
// ----------------------------------------------------------------
JNIEXPORT jlong JNICALL
Java_com_example_scigemma_llm_LlamaCppNative_nativeLoadModel(
        JNIEnv* env, jobject /*thiz*/,
        jstring modelPath, jint nCtx, jint nThreads)
{
    llama_backend_init();

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading GGUF model from: %s  nCtx=%d  nThreads=%d", path, nCtx, nThreads);

    // -- Load model weights --
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;   // CPU-only (change to 99 for full GPU if Vulkan enabled)

    llama_model* model = llama_load_model_from_file(path, mp);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!model) { LOGE("Failed to load model"); return 0L; }

    // -- Create inference context --
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx          = (uint32_t)nCtx;
    cp.n_threads      = (uint32_t)nThreads;
    cp.n_threads_batch = (uint32_t)nThreads;
    cp.flash_attn     = false;

    llama_context* ctx = llama_new_context_with_model(model, cp);
    if (!ctx) {
        LOGE("Failed to create context");
        llama_model_free(model);
        return 0L;
    }

    // -- Sampler chain (top-k → top-p → temperature → dist) --
    llama_sampler_chain_params scp = llama_sampler_chain_default_params();
    llama_sampler* sampler = llama_sampler_chain_init(scp);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.95f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    auto* state = new LlamaState{model, ctx, sampler, (int)nCtx, (int)nThreads};
    LOGI("Model loaded successfully. Vocab size: %d", llama_n_vocab(model));
    return reinterpret_cast<jlong>(state);
}

// ----------------------------------------------------------------
// Synchronous generation – returns the full response as a String.
// Used by the benchmarking system for clean timing.
// ----------------------------------------------------------------
JNIEXPORT jstring JNICALL
Java_com_example_scigemma_llm_LlamaCppNative_nativeGenerateSync(
        JNIEnv* env, jobject /*thiz*/,
        jlong handle, jstring prompt, jint maxTokens)
{
    auto* s = reinterpret_cast<LlamaState*>(handle);
    if (!s) { return env->NewStringUTF(""); }

    const char* p = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_str(p);
    env->ReleaseStringUTFChars(prompt, p);

    auto tokens = tokenize_text(s->model, prompt_str, /*add_bos=*/true);
    int base_pos = eval_prompt(s->ctx, tokens);
    if (base_pos < 0) { return env->NewStringUTF("[error: prompt eval failed]"); }

    llama_sampler_reset(s->sampler);

    std::string response;
    for (int i = 0; i < (int)maxTokens; i++) {
        llama_token tok = llama_sampler_sample(s->sampler, s->ctx, -1);
        llama_sampler_accept(s->sampler, tok);

        if (llama_token_is_eog(s->model, tok)) break;

        response += token_to_str(s->model, tok);

        llama_batch batch = llama_batch_init(1, 0, 1);
        batch_add_token(batch, tok, base_pos + i, true);
        if (llama_decode(s->ctx, batch) != 0) {
            llama_batch_free(batch);
            break;
        }
        llama_batch_free(batch);
    }

    llama_sampler_reset(s->sampler);
    return env->NewStringUTF(response.c_str());
}

// ----------------------------------------------------------------
// Streaming generation – calls callback.onToken(token, isDone) for
// each generated piece.  Used by the chat UI for real-time display.
// The callback interface is: fun onToken(token: String, isDone: Boolean)
// ----------------------------------------------------------------
JNIEXPORT void JNICALL
Java_com_example_scigemma_llm_LlamaCppNative_nativeGenerateStream(
        JNIEnv* env, jobject /*thiz*/,
        jlong handle, jstring prompt, jint maxTokens, jobject callback)
{
    auto* s = reinterpret_cast<LlamaState*>(handle);
    if (!s) return;

    // Get Kotlin callback method
    jclass cbClass = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;Z)V");
    if (!onToken) { LOGE("onToken method not found on callback"); return; }

    const char* p = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_str(p);
    env->ReleaseStringUTFChars(prompt, p);

    auto tokens = tokenize_text(s->model, prompt_str, /*add_bos=*/true);
    int base_pos = eval_prompt(s->ctx, tokens);
    if (base_pos < 0) {
        jstring errStr = env->NewStringUTF("[error]");
        env->CallVoidMethod(callback, onToken, errStr, (jboolean)true);
        env->DeleteLocalRef(errStr);
        return;
    }

    llama_sampler_reset(s->sampler);

    for (int i = 0; i < (int)maxTokens; i++) {
        llama_token tok = llama_sampler_sample(s->sampler, s->ctx, -1);
        llama_sampler_accept(s->sampler, tok);

        bool is_eog = llama_token_is_eog(s->model, tok);
        std::string piece = is_eog ? "" : token_to_str(s->model, tok);

        jstring jPiece = env->NewStringUTF(piece.c_str());
        env->CallVoidMethod(callback, onToken, jPiece, (jboolean)is_eog);
        env->DeleteLocalRef(jPiece);

        if (is_eog) {
            llama_sampler_reset(s->sampler);
            return;
        }

        llama_batch batch = llama_batch_init(1, 0, 1);
        batch_add_token(batch, tok, base_pos + i, true);
        if (llama_decode(s->ctx, batch) != 0) {
            llama_batch_free(batch);
            break;
        }
        llama_batch_free(batch);
    }

    // Signal done if max tokens reached without EOG
    jstring empty = env->NewStringUTF("");
    env->CallVoidMethod(callback, onToken, empty, (jboolean)true);
    env->DeleteLocalRef(empty);

    llama_sampler_reset(s->sampler);
}

// ----------------------------------------------------------------
// Count the number of tokens in a string (without add_bos).
// Used to check if summarization is needed before cloud upload.
// ----------------------------------------------------------------
JNIEXPORT jint JNICALL
Java_com_example_scigemma_llm_LlamaCppNative_nativeTokenCount(
        JNIEnv* env, jobject /*thiz*/,
        jlong handle, jstring text)
{
    auto* s = reinterpret_cast<LlamaState*>(handle);
    if (!s) return 0;

    const char* t = env->GetStringUTFChars(text, nullptr);
    std::string ts(t);
    env->ReleaseStringUTFChars(text, t);

    auto tokens = tokenize_text(s->model, ts, /*add_bos=*/false);
    return (jint)tokens.size();
}

// ----------------------------------------------------------------
// Free model resources.
// ----------------------------------------------------------------
JNIEXPORT void JNICALL
Java_com_example_scigemma_llm_LlamaCppNative_nativeFreeModel(
        JNIEnv* env, jobject /*thiz*/,
        jlong handle)
{
    auto* s = reinterpret_cast<LlamaState*>(handle);
    if (!s) return;

    llama_sampler_free(s->sampler);
    llama_free(s->ctx);
    llama_model_free(s->model);
    delete s;

    llama_backend_free();
    LOGI("Model freed");
}

} // extern "C"
