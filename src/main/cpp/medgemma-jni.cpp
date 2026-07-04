#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <cmath>
#include <cstring>
#include <mutex>
#include <string>
#include <time.h>
#include <unistd.h>
#include <sched.h>
#include <errno.h>
#include <sys/resource.h>

#include "llama.h"
#include "ggml-backend.h"
#include "mtmd.h"
#include "mtmd-helper.h"
#define TAG "MedGemmaNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static llama_model * g_model = nullptr;
static llama_context * g_context = nullptr;
static mtmd_context * g_mtmd_ctx = nullptr;
static llama_sampler * g_sampler = nullptr;
static std::mutex g_mutex;
static volatile bool g_stop_generation = false;
static bool g_backend_initialized = false;

// Snapdragon big.LITTLE: 4 threads on perf cores beats oversubscription (see llama.cpp perf docs)
static constexpr int N_THREADS = 4;
static constexpr int DEFAULT_CONTEXT_SIZE = 2048;
static constexpr int DEFAULT_BATCH_SIZE = 512;
static constexpr int DEFAULT_MAX_TOKENS = 512;
static constexpr int MAX_THOUGHT_TOKENS = 128;
static constexpr int TOKEN_CALLBACK_BATCH = 6;

static void android_log_callback(ggml_log_level level, const char * text, void * user_data) {
    (void) user_data;
    int android_level = ANDROID_LOG_INFO;
    switch (level) {
        case GGML_LOG_LEVEL_INFO:  android_level = ANDROID_LOG_INFO;  break;
        case GGML_LOG_LEVEL_WARN:  android_level = ANDROID_LOG_WARN;  break;
        case GGML_LOG_LEVEL_ERROR: android_level = ANDROID_LOG_ERROR; break;
        case GGML_LOG_LEVEL_DEBUG: android_level = ANDROID_LOG_DEBUG; break;
        default: break;
    }
    __android_log_print(android_level, "LlamaNative", "%s", text);
}

static void set_performance_cores_affinity() {
    int n_cores = (int) sysconf(_SC_NPROCESSORS_CONF);
    if (setpriority(PRIO_PROCESS, 0, -10) != 0) {
        LOGI("Failed to set thread priority: %s", strerror(errno));
    }
    if (n_cores < 8) return;

    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    for (int i = 4; i < 8; i++) {
        CPU_SET(i, &cpuset);
    }
    sched_setaffinity(0, sizeof(cpu_set_t), &cpuset);
}

static llama_pos get_sequence_past(llama_context * ctx) {
    llama_memory_t mem = llama_get_memory(ctx);
    llama_pos max_pos = llama_memory_seq_pos_max(mem, 0);
    return max_pos < 0 ? 0 : max_pos + 1;
}

static void ensure_backend(const char * native_lib_dir) {
    if (g_backend_initialized) return;
    llama_log_set(android_log_callback, nullptr);
    mtmd_helper_log_set(android_log_callback, nullptr);
    if (native_lib_dir && native_lib_dir[0] != '\0') {
        LOGI("Loading CPU backends from %s", native_lib_dir);
        ggml_backend_load_all_from_path(native_lib_dir);
    } else {
        ggml_backend_load_all();
    }
    llama_backend_init();
    if (ggml_backend_reg_count() == 0) {
        LOGE("No GGML backends loaded — inference will fail");
    }
    g_backend_initialized = true;
}

static llama_sampler * create_sampler() {
    return llama_sampler_init_greedy();
}

extern "C" {

JNIEXPORT jint JNICALL
Java_com_example_medgemma_GgufInferenceManager_initNative(
        JNIEnv *env, jobject thiz, jstring nativeLibDir, jstring modelPath, jstring mmprojPath) {
    std::lock_guard<std::mutex> lock(g_mutex);
    set_performance_cores_affinity();

    const char * c_native_lib_dir = env->GetStringUTFChars(nativeLibDir, nullptr);
    ensure_backend(c_native_lib_dir);
    env->ReleaseStringUTFChars(nativeLibDir, c_native_lib_dir);

    const char * c_model_path = env->GetStringUTFChars(modelPath, nullptr);
    const char * c_mmproj_path = env->GetStringUTFChars(mmprojPath, nullptr);

    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_mtmd_ctx) { mtmd_free(g_mtmd_ctx); g_mtmd_ctx = nullptr; }
    if (g_context) { llama_free(g_context); g_context = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }

    LOGI("Initializing with %d threads", N_THREADS);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;
    mparams.use_mmap = true;
    g_model = llama_model_load_from_file(c_model_path, mparams);
    if (!g_model) {
        LOGE("Failed to load model from %s", c_model_path);
        env->ReleaseStringUTFChars(modelPath, c_model_path);
        env->ReleaseStringUTFChars(mmprojPath, c_mmproj_path);
        return -1;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = DEFAULT_CONTEXT_SIZE;
    cparams.n_threads = N_THREADS;
    cparams.n_threads_batch = N_THREADS;
    cparams.n_batch = DEFAULT_BATCH_SIZE;
    cparams.n_ubatch = DEFAULT_BATCH_SIZE;
    // F16 KV is faster on decode than Q8_0 (no per-token dequant in attention)
    cparams.type_k = GGML_TYPE_F16;
    cparams.type_v = GGML_TYPE_F16;
    cparams.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;
    g_context = llama_init_from_model(g_model, cparams);
    if (!g_context) {
        LOGE("Failed to initialize llama context");
        env->ReleaseStringUTFChars(modelPath, c_model_path);
        env->ReleaseStringUTFChars(mmprojPath, c_mmproj_path);
        return -2;
    }

    mtmd_context_params mtparams = mtmd_context_params_default();
    mtparams.n_threads = N_THREADS;
    mtparams.use_gpu = false;
    mtparams.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_AUTO;
    mtparams.media_marker = "<start_of_image>";
    g_mtmd_ctx = mtmd_init_from_file(c_mmproj_path, g_model, mtparams);
    if (!g_mtmd_ctx) {
        LOGE("Failed to load mmproj from %s", c_mmproj_path);
        env->ReleaseStringUTFChars(modelPath, c_model_path);
        env->ReleaseStringUTFChars(mmprojPath, c_mmproj_path);
        return -3;
    }

    g_sampler = create_sampler();

    env->ReleaseStringUTFChars(modelPath, c_model_path);
    env->ReleaseStringUTFChars(mmprojPath, c_mmproj_path);
    LOGI("Engine ready: %s", llama_print_system_info());
    return 0;
}

JNIEXPORT void JNICALL
Java_com_example_medgemma_GgufInferenceManager_deinitNative(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_sampler) { llama_sampler_free(g_sampler); g_sampler = nullptr; }
    if (g_mtmd_ctx) { mtmd_free(g_mtmd_ctx); g_mtmd_ctx = nullptr; }
    if (g_context) { llama_free(g_context); g_context = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    if (g_backend_initialized) {
        llama_backend_free();
        g_backend_initialized = false;
    }
}

JNIEXPORT void JNICALL
Java_com_example_medgemma_GgufInferenceManager_resetContextNative(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_context) return;
    llama_memory_t mem = llama_get_memory(g_context);
    llama_memory_seq_rm(mem, -1, -1, -1);
    if (g_sampler) {
        llama_sampler_free(g_sampler);
        g_sampler = create_sampler();
    }
}

JNIEXPORT void JNICALL
Java_com_example_medgemma_GgufInferenceManager_generateNative(
        JNIEnv *env, jobject thiz, jstring prompt, jbyteArray imageBytes,
        jboolean clearContext, jobject callback) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_context || !g_mtmd_ctx || !g_sampler) return;

    set_performance_cores_affinity();
    g_stop_generation = false;

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");

    if (clearContext) {
        llama_memory_t mem = llama_get_memory(g_context);
        llama_memory_seq_rm(mem, -1, -1, -1);
        llama_sampler_free(g_sampler);
        g_sampler = create_sampler();
    }

    const char * raw_prompt = env->GetStringUTFChars(prompt, nullptr);

    mtmd_bitmap * bitmap = nullptr;
    if (imageBytes != nullptr) {
        jsize len = env->GetArrayLength(imageBytes);
        jbyte * data = env->GetByteArrayElements(imageBytes, nullptr);
        if (len == 448 * 448 * 3) {
            bitmap = mtmd_bitmap_init(448, 448, (const unsigned char *) data);
        } else {
            auto wrapper = mtmd_helper_bitmap_init_from_buf(
                    g_mtmd_ctx, (const unsigned char *) data, (size_t) len, false);
            bitmap = wrapper.bitmap;
        }
        env->ReleaseByteArrayElements(imageBytes, data, JNI_ABORT);
    }

    // add_special=true only on fresh context (adds BOS); false when appending to KV cache
    mtmd_input_text itext = { raw_prompt, (bool) clearContext, true };
    mtmd_input_chunks * chunks = mtmd_input_chunks_init();
    const mtmd_bitmap * bitmaps_array[1];
    size_t n_bitmaps = 0;
    if (bitmap) {
        bitmaps_array[0] = bitmap;
        n_bitmaps = 1;
    }

    if (mtmd_tokenize(g_mtmd_ctx, chunks, &itext,
                      n_bitmaps > 0 ? bitmaps_array : nullptr, n_bitmaps) != 0) {
        jstring jerr = env->NewStringUTF("Error: tokenization failed");
        env->CallVoidMethod(callback, onTokenMethod, jerr);
        env->DeleteLocalRef(jerr);
        if (bitmap) mtmd_bitmap_free(bitmap);
        mtmd_input_chunks_free(chunks);
        env->ReleaseStringUTFChars(prompt, raw_prompt);
        return;
    }

    llama_pos n_past = clearContext ? 0 : get_sequence_past(g_context);
    const int n_batch = DEFAULT_BATCH_SIZE;
    if (mtmd_helper_eval_chunks(g_mtmd_ctx, g_context, chunks, n_past, 0, n_batch, true, &n_past) != 0) {
        jstring jerr = env->NewStringUTF("Error: eval failed");
        env->CallVoidMethod(callback, onTokenMethod, jerr);
        env->DeleteLocalRef(jerr);
        if (bitmap) mtmd_bitmap_free(bitmap);
        mtmd_input_chunks_free(chunks);
        env->ReleaseStringUTFChars(prompt, raw_prompt);
        return;
    }

    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    int n_decode = 0;
    int n_thought = 0;
    bool in_thought = false;
    int batch_count = 0;
    std::string pending_out;
    struct timespec t_start, t_end;
    clock_gettime(CLOCK_MONOTONIC, &t_start);
    llama_batch batch = llama_batch_init(1, 0, 1);

    auto flush_pending = [&]() {
        if (pending_out.empty()) return;
        jstring jpiece = env->NewStringUTF(pending_out.c_str());
        env->CallVoidMethod(callback, onTokenMethod, jpiece);
        env->DeleteLocalRef(jpiece);
        pending_out.clear();
        batch_count = 0;
    };

    auto emit_token = [&](const std::string & s_piece, bool force_flush) {
        if (s_piece == "[THOUGHT_START]") {
            flush_pending();
            in_thought = true;
            n_thought = 0;
            jstring jpiece = env->NewStringUTF(s_piece.c_str());
            env->CallVoidMethod(callback, onTokenMethod, jpiece);
            env->DeleteLocalRef(jpiece);
            return;
        }
        if (s_piece == "[THOUGHT_END]") {
            flush_pending();
            in_thought = false;
            n_thought = 0;
            jstring jpiece = env->NewStringUTF(s_piece.c_str());
            env->CallVoidMethod(callback, onTokenMethod, jpiece);
            env->DeleteLocalRef(jpiece);
            return;
        }
        pending_out += s_piece;
        batch_count++;
        if (force_flush || batch_count >= TOKEN_CALLBACK_BATCH) {
            flush_pending();
        }
    };

    while (n_decode < DEFAULT_MAX_TOKENS) {
        if (g_stop_generation) break;
        if (in_thought && n_thought >= MAX_THOUGHT_TOKENS) {
            flush_pending();
            in_thought = false;
            n_thought = 0;
            jstring jend = env->NewStringUTF("[THOUGHT_END]");
            env->CallVoidMethod(callback, onTokenMethod, jend);
            env->DeleteLocalRef(jend);
        }

        llama_token id = llama_sampler_sample(g_sampler, g_context, -1);
        llama_sampler_accept(g_sampler, id);

        if (llama_vocab_is_eog(vocab, id)) break;

        char piece[128];
        int n = llama_token_to_piece(vocab, id, piece, sizeof(piece), 0, true);
        if (n > 0) {
            std::string s_piece(piece, (size_t) n);
            if (s_piece.find("<unused94>") != std::string::npos) {
                emit_token("[THOUGHT_START]", true);
            } else if (s_piece.find("<unused95>") != std::string::npos) {
                emit_token("[THOUGHT_END]", true);
            } else if (s_piece.find("<unused") != std::string::npos) {
                if (in_thought) n_thought++;
            } else {
                emit_token(s_piece, false);
                if (in_thought) n_thought++;
            }
        }

        batch.n_tokens = 1;
        batch.token[0] = id;
        batch.pos[0] = n_past;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = true;
        if (llama_decode(g_context, batch) != 0) break;
        n_past++;
        n_decode++;
    }

    flush_pending();

    clock_gettime(CLOCK_MONOTONIC, &t_end);
    double total_time = (t_end.tv_sec - t_start.tv_sec) + (t_end.tv_nsec - t_start.tv_nsec) / 1e9;
    double speed = total_time > 0 ? n_decode / total_time : 0.0;
    char stats_buf[128];
    snprintf(stats_buf, sizeof(stats_buf), "[STATS] %d tokens • %.2fs • %.2f t/s", n_decode, total_time, speed);
    LOGI("%s", stats_buf);
    jstring jstats = env->NewStringUTF(stats_buf);
    env->CallVoidMethod(callback, onTokenMethod, jstats);
    env->DeleteLocalRef(jstats);

    llama_batch_free(batch);
    if (bitmap) mtmd_bitmap_free(bitmap);
    mtmd_input_chunks_free(chunks);
    env->ReleaseStringUTFChars(prompt, raw_prompt);
}

JNIEXPORT void JNICALL
Java_com_example_medgemma_GgufInferenceManager_stopNative(JNIEnv *env, jobject thiz) {
    g_stop_generation = true;
}

}