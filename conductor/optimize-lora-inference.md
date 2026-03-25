# Plan: Optimize MedGemma LoRA Inference with MediaPipe

## Objective
Update the `MedGemma` application to correctly and efficiently connect a LoRA adapter to the MedGemma 1.5 4B base model using the MediaPipe GenAI SDK. The primary focus is on **performance** (via GPU acceleration) and **session-based adapter management**.

## Background & Analysis
- **Framework:** MediaPipe GenAI Task (`com.google.mediapipe:tasks-genai:0.10.32`).
- **Base Model:** MedGemma 1.5 4B (expected as a `.task` bundle).
- **LoRA Adapter:** TFLite format (`.tflite`).
- **Performance:** MediaPipe LoRA implementation **requires the GPU backend** for optimal performance and compatibility.
- **Architecture:** Use `LlmInferenceSession` for dynamic LoRA attachment, as it is more memory-efficient and follows modern MediaPipe patterns (v0.10.20+).

## Key Files & Context
- `src/main/java/com/example/medgemma/LlmInferenceManager.kt`: The core singleton managing model initialization and inference.
- `src/main/java/com/example/medgemma/ChatViewModel.kt`: Handles the chat state and interacts with the manager.
- `build.gradle.kts`: Project dependencies (verify `tasks-genai` version).

## Implementation Steps

### 1. Optimize `LlmInferenceManager.kt`
- **Switch to GPU Backend:** Change `preferredBackend` from `Backend.CPU` to `Backend.GPU`.
- **Tune Parameters:** Increase `maxTokens` from 256 to 512 (or 1024) for better medical context generation.
- **Session-Based LoRA:** Ensure `loraPath` is set in `LlmInferenceSessionOptions` rather than globally in `LlmInferenceOptions`.
- **Enhanced Error Handling:** Add specific checks for GPU support and clear error messages for LoRA initialization failures.

### 2. Update UI/UX (Optional but Recommended)
- Ensure the "Typing Indicator" in `MainActivity.kt` correctly reflects the inference state.
- Add a "GPU Enabled" status or more descriptive error UI if the model fails to load.

## Detailed Changes

### `LlmInferenceManager.kt`
```kotlin
// Change Backend to GPU
val options = LlmInferenceOptions.builder()
    .setModelPath(baseModelPath)
    .setMaxTokens(512) // Increased for better medical answers
    .setPreferredBackend(Backend.GPU) // PERFORMANCE: GPU is required for LoRA
    .build()

// Ensure Session uses LoRA
val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
    .setLoraPath(loraAdapterPath)
    .setTemperature(0.7f)
    .setTopK(40)
    .build()
```

## Verification & Testing
- **Initialization Test:** Verify that `AI initialized successfully with LoRA session` appears in Logcat.
- **Performance Test:** Compare inference speed (tokens/sec) with the previous CPU implementation.
- **Memory Test:** Monitor RAM usage via Android Studio Profiler to ensure the 4B model stays within limits (~3.5GB - 4.5GB).
- **Functional Test:** Verify that the model responds to medical queries with context that reflects the fine-tuned LoRA brain.

## Notes on "Best Performance"
- **Quantization:** Ensure the `medgemma_bundle.task` is a 4-bit quantized version (e.g., via `genai_converter`).
- **GPU Backend:** LoRA weights in MediaPipe are optimized for GPU kernels. CPU inference with LoRA is either unsupported or significantly slower.
- **Session Management:** Using `LlmInferenceSession` allows for potential future multi-LoRA switching without reloading the base model.
