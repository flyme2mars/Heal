# Plan: Transition to GGUF-Native Architecture (llama.cpp)

## Objective
Replace the MediaPipe/LiteRT inference engine with a high-performance, native GGUF engine powered by `llama.cpp`. This will enable better support for MedGemma 1.5 4B's multimodal capabilities, dynamic LoRA loading, and streaming responses on Android.

## Background & Rationale
- **Control:** `llama.cpp` allows fine-grained control over hardware acceleration (NEON, I8MM, KleidiAI).
- **Multimodal:** Supports separate loading of LLM and Vision Projector (`mmproj`), avoiding complex bundling.
- **Performance:** Native C++ kernels are generally faster than the generic GenAI Task API for 4-bit and 6-bit quants on mobile.
- **Architecture:** Transition from "Single Response" to "Token Streaming" for a better UX.

## Key Files & Structure
- `src/main/cpp/`:
    - `llama.cpp/`: The core engine source.
    - `CMakeLists.txt`: Build configuration for the native library.
    - `medgemma-jni.cpp`: JNI bridge between Kotlin and C++.
- `src/main/java/com/example/medgemma/`:
    - `GgufInferenceManager.kt`: New singleton managing the native engine.
    - `ChatViewModel.kt`: Updated to handle `Flow<String>` for streaming.
    - `MainActivity.kt`: Updated UI for streaming and image input.

## Implementation Steps

### Phase 1: Native Environment Setup
1. **Initialize C++ Directory:** Create `src/main/cpp`.
2. **Integrate llama.cpp:** Download/Link the latest stable `llama.cpp` source.
3. **Configure CMake:** Create a `CMakeLists.txt` that enables:
    - `-O3` optimizations.
    - `GGML_NEON=ON` (ARM math acceleration).
    - `GGML_I8MM=ON` (Modern ARM integer math).
    - Links the core library with the multimodal `libmtmd`.

### Phase 2: JNI Bridge Development
1. **Implement `medgemma-jni.cpp`:**
    - `loadModel(path)`: Loads the main GGUF.
    - `loadProjector(path)`: Loads the MedSigLIP mmproj.
    - `generate(prompt, imageBytes)`: Performs inference and triggers callbacks for tokens.
2. **Image Processing:** Implement SigLIP-specific normalization (resizing, mean/std) in C++.

### Phase 3: Kotlin Integration
1. **Create `GgufInferenceManager`:**
    - Handles the `loadLibrary` call.
    - Wraps native calls in a thread-safe `CoroutineDispatcher`.
    - Exposes a `StateFlow` for engine status and a `Flow` for generation.
2. **Update `ChatViewModel`:**
    - Switch from `generateResponse` (suspend) to `collect` from the GGUF stream.
    - Append tokens to the UI state in real-time.

### Phase 4: Build & Deploy
1. **Update `build.gradle.kts`:**
    - Add `externalNativeBuild { cmake { ... } }`.
    - Specify `ndkVersion`.
2. **Deployment:** Push `medgemma-1.5-4b-it-UD-Q6_K_XL.gguf` and `mmproj-F16.gguf` to `/data/local/tmp/models/`.

## Verification & Testing
- **Build Test:** Ensure `./gradlew assembleDebug` succeeds with NDK compilation.
- **Init Test:** Verify `llama_model_load` success in Logcat.
- **Inference Test:** Check for real-time token streaming in the UI.
- **Performance Test:** Verify NEON/I8MM acceleration via execution time logs.

## Dependencies Required
- Android NDK (Side-by-side).
- CMake 3.22.1+.
- llama.cpp (Target Tag: Latest stable).
