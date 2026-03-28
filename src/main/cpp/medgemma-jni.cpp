#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <mutex>
#include <time.h>
#include <sched.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>

#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#define TAG "MedGemmaNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static llama_model * g_model = nullptr;
static llama_context * g_context = nullptr;
static mtmd_context * g_mtmd_ctx = nullptr;
static std::mutex g_mutex;

#include <sys/resource.h>

static void set_performance_cores_affinity() {
    int n_cores = sysconf(_SC_NPROCESSORS_CONF);
    
    // Set thread priority to high (-20 is highest, 19 is lowest)
    // This helps the scheduler prioritize these worker threads.
    if (setpriority(PRIO_PROCESS, 0, -10) != 0) {
        LOGI("Failed to set thread priority: %s", strerror(errno));
    }

    if (n_cores < 8) {
        LOGI("Device has %d cores, skipping affinity optimization", n_cores);
        return;
    }
    
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    // On most 8-core Android devices (Snapdragon/Exynos), the last 4 cores (4-7) are the performance ones.
    // This pins the current thread and its children to these cores.
    for (int i = 4; i < 8; i++) {
        CPU_SET(i, &cpuset);
    }

    if (sched_setaffinity(0, sizeof(cpu_set_t), &cpuset) == 0) {
        LOGI("Thread affinity successfully set to performance cores (4-7)");
    } else {
        LOGE("Failed to set thread affinity: %s", strerror(errno));
    }
}

extern "C" {

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

JNIEXPORT jint JNICALL
Java_com_example_medgemma_GgufInferenceManager_initNative(JNIEnv *env, jobject thiz, jstring modelPath, jstring mmprojPath) {

    std::lock_guard<std::mutex> lock(g_mutex);
    
    set_performance_cores_affinity();
    
    mtmd_helper_log_set(android_log_callback, nullptr);
    llama_log_set(android_log_callback, nullptr);
    
    const char * c_model_path = env->GetStringUTFChars(modelPath, nullptr);
    const char * c_mmproj_path = env->GetStringUTFChars(mmprojPath, nullptr);

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 33; // Offload most layers to GPU (Vulkan/CANN/OpenCL if available)
    
    g_model = llama_model_load_from_file(c_model_path, mparams);
    if (!g_model) {
        LOGE("Failed to load model from %s", c_model_path);
        return -1;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 4096;         // Reduced to 4096 for better cache/RAM locality
    cparams.n_threads = 4;        // Limit to 4 Performance cores
    cparams.n_threads_batch = 4;  // Consistent threading for better stability on big.LITTLE
    cparams.n_ubatch = 512;       
    cparams.type_k = GGML_TYPE_Q8_0; // Use Q8_0 for faster CPU prefill than TQ3_0
    cparams.type_v = GGML_TYPE_Q8_0; 
    cparams.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED; 
    
    LOGI("Initializing llama context with n_ctx=%u, KV=Q8_0, n_threads=4", cparams.n_ctx);
    
    g_context = llama_init_from_model(g_model, cparams);
    if (!g_context) {
        LOGE("Failed to initialize llama context (llama_init_from_model returned NULL)");
        return -2;
    }

    mtmd_context_params mtparams = mtmd_context_params_default();
    mtparams.n_threads = 4; // Target 4 Performance cores for CLIP encoding
    mtparams.use_gpu = false; 
    mtparams.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_AUTO; 
    mtparams.media_marker = "<image>";
    g_mtmd_ctx = mtmd_init_from_file(c_mmproj_path, g_model, mtparams);
    if (!g_mtmd_ctx) {
        LOGE("Failed to load mmproj from %s", c_mmproj_path);
        return -3;
    }
    
    env->ReleaseStringUTFChars(modelPath, c_model_path);
    env->ReleaseStringUTFChars(mmprojPath, c_mmproj_path);

    LOGI("Native engine initialized successfully");
    return 0;
}

JNIEXPORT void JNICALL
Java_com_example_medgemma_GgufInferenceManager_deinitNative(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_mutex);
    
    if (g_mtmd_ctx) {
        mtmd_free(g_mtmd_ctx);
        g_mtmd_ctx = nullptr;
    }
    
    if (g_context) {
        llama_free(g_context);
        g_context = nullptr;
    }
    
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    
    llama_backend_free();
    LOGI("Native engine deinitialized");
}

JNIEXPORT void JNICALL
Java_com_example_medgemma_GgufInferenceManager_generateNative(JNIEnv *env, jobject thiz, jstring prompt, jbyteArray imageBytes, jobject callback) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_context || !g_mtmd_ctx) return;

    set_performance_cores_affinity();

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");

    const char * raw_prompt = env->GetStringUTFChars(prompt, nullptr);
    LOGI("generateNative starting. prompt length: %zu", strlen(raw_prompt));
    
    // 1. Clear KV cache
    llama_memory_t mem = llama_get_memory(g_context);
    llama_memory_seq_rm(mem, -1, -1, -1);
    LOGI("KV cache cleared");

    // 2. Handle Image if provided
    mtmd_bitmap * bitmap = nullptr;
    if (imageBytes != nullptr) {
        jsize len = env->GetArrayLength(imageBytes);
        LOGI("Processing image: %d bytes", len);
        jbyte * data = env->GetByteArrayElements(imageBytes, nullptr);
        
        // If length matches 448x448x3, assume raw RGB888. Otherwise assume JPEG/PNG and decode.
        if (len == 448 * 448 * 3) {
            LOGI("Raw RGB888 pixels detected, bypassing decoder");
            bitmap = mtmd_bitmap_init(448, 448, (const unsigned char *)data);
        } else {
            LOGI("Encoded image detected, using mtmd_helper_bitmap_init_from_buf");
            bitmap = mtmd_helper_bitmap_init_from_buf(g_mtmd_ctx, (const unsigned char *)data, len);
        }
        
        env->ReleaseByteArrayElements(imageBytes, data, JNI_ABORT);
        if (!bitmap) {
            LOGE("MTMD: Failed to initialize bitmap");
            jstring jerr = env->NewStringUTF("Error: Failed to initialize image bitmap");
            env->CallVoidMethod(callback, onTokenMethod, jerr);
            env->DeleteLocalRef(jerr);
            env->ReleaseStringUTFChars(prompt, raw_prompt);
            return;
        }
        LOGI("Image bitmap initialized successfully");
    }

    // 3. Tokenize and Prefill
    mtmd_input_text itext = { raw_prompt, true, true };
    mtmd_input_chunks * chunks = mtmd_input_chunks_init();
    
    const mtmd_bitmap * bitmaps_array[1];
    size_t n_bitmaps = 0;
    if (bitmap) {
        bitmaps_array[0] = bitmap;
        n_bitmaps = 1;
    }

    LOGI("Tokenizing input chunks...");
    int32_t token_res = mtmd_tokenize(g_mtmd_ctx, chunks, &itext, n_bitmaps > 0 ? bitmaps_array : nullptr, n_bitmaps);
    if (token_res != 0) {
        LOGE("MTMD: Tokenization failed with res %d", token_res);
        jstring jerr = env->NewStringUTF("Error: Tokenization failed - ensure <image> marker is present");
        env->CallVoidMethod(callback, onTokenMethod, jerr);
        env->DeleteLocalRef(jerr);
        if (bitmap) mtmd_bitmap_free(bitmap);
        mtmd_input_chunks_free(chunks);
        env->ReleaseStringUTFChars(prompt, raw_prompt);
        return;
    }

    size_t n_chunks = mtmd_input_chunks_size(chunks);
    LOGI("Tokenization successful. Chunks: %zu. Starting prefill (eval_chunks)...", n_chunks);
    
    llama_pos n_past = 0;
    int64_t tp_start = ggml_time_ms();
    int32_t eval_res = mtmd_helper_eval_chunks(g_mtmd_ctx, g_context, chunks, 4, 0, 512, true, &n_past);
    int64_t tp_end = ggml_time_ms();
    LOGI("Prefill (eval_chunks) finished in %.2f seconds", (tp_end - tp_start) / 1000.0);
    
    if (eval_res != 0) {
        LOGE("MTMD: eval_chunks failed with res %d", eval_res);
        jstring jerr = env->NewStringUTF("Error: Prefill (eval_chunks) failed");
        env->CallVoidMethod(callback, onTokenMethod, jerr);
        env->DeleteLocalRef(jerr);
        if (bitmap) mtmd_bitmap_free(bitmap);
        mtmd_input_chunks_free(chunks);
        env->ReleaseStringUTFChars(prompt, raw_prompt);
        return;
    }

    LOGI("Prefill complete. n_past = %d. Starting generation loop...", n_past);

    // 4. Generation Loop
    struct llama_sampler * sampler = llama_sampler_init_greedy();
    const struct llama_vocab * vocab = llama_model_get_vocab(g_model);
    int n_decode = 0;
    
    struct timespec t_start, t_end;
    clock_gettime(CLOCK_MONOTONIC, &t_start);

    // Prepare a batch for single token decoding
    llama_batch batch = llama_batch_init(1, 0, 1);

    while (n_decode < 1024) {
        llama_token id = llama_sampler_sample(sampler, g_context, -1);
        llama_sampler_accept(sampler, id);

        LOGI("Sampled token %d", id);

        if (llama_vocab_is_eog(vocab, id)) {
            LOGI("EOG detected");
            break;
        }

        // Send token piece to UI
        char piece[128];
        int n = llama_token_to_piece(vocab, id, piece, sizeof(piece), 0, true);
        if (n > 0) {
            std::string s_piece(piece, n);
            // Handle reasoning tags by explicitly wrapping the content for the Kotlin layer to parse
            if (s_piece.find("<unused94>") != std::string::npos) {
                s_piece = "[THOUGHT_START]";
            } else if (s_piece.find("<unused95>") != std::string::npos) {
                s_piece = "[THOUGHT_END]";
            } else if (s_piece.find("<unused") != std::string::npos) {
                // Ignore other unknown unused tags
                continue;
            }
            
            jstring jpiece = env->NewStringUTF(s_piece.c_str());
            env->CallVoidMethod(callback, onTokenMethod, jpiece);
            env->DeleteLocalRef(jpiece);
        }

        // Decode next token
        batch.n_tokens = 1;
        batch.token[0] = id;
        batch.pos[0] = n_past;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = true;

        if (llama_decode(g_context, batch) != 0) {
            LOGE("Decode failed at step %d", n_decode);
            break;
        }

        n_past++;
        n_decode++;
    }

    clock_gettime(CLOCK_MONOTONIC, &t_end);
    double total_time = (t_end.tv_sec - t_start.tv_sec) + (t_end.tv_nsec - t_start.tv_nsec) / 1e9;
    double speed = n_decode / total_time;
    LOGI("Stats: Tokens=%d, Time=%.2fs, Speed=%.2f t/s", n_decode, total_time, speed);

    char stats_buf[128];
    snprintf(stats_buf, sizeof(stats_buf), "[STATS] %d tokens • %.2fs • %.2f t/s", n_decode, total_time, speed);
    jstring jstats = env->NewStringUTF(stats_buf);
    env->CallVoidMethod(callback, onTokenMethod, jstats);
    env->DeleteLocalRef(jstats);

    llama_batch_free(batch);
    llama_sampler_free(sampler);
    if (bitmap) mtmd_bitmap_free(bitmap);
    mtmd_input_chunks_free(chunks);
    env->ReleaseStringUTFChars(prompt, raw_prompt);
}

}

