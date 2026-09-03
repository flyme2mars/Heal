# Heal

Heal is an Android chat app that runs a MedGemma GGUF locally through a `llama.cpp` JNI engine. The UI is Jetpack Compose (Material 3). Inference is CPU-only (`n_gpu_layers = 0`); there is no in-app model download.

This repo is the on-device app (`com.example.medgemma`). It is not a training or fine-tuning project.

## What is in this repo

*   **On-device chat** — text and optional image turns. Multi-turn reuse keeps native KV and sends only the new user turn.
*   **MedGemma via llama.cpp** — `src/main/cpp/medgemma-jni.cpp` loads a GGUF + `mtmd` multimodal projector. Prompts use Gemma `<start_of_turn>` / `<end_of_turn>` tags (`ChatPromptPolicy.kt`).
*   **GGUF path** — the app looks only at `/data/local/tmp/models/model.gguf` and `/data/local/tmp/models/mmproj.gguf` (`ModelManager.kt`).
*   **TurboQuant (`TQ3_0`)** — ARM NEON dequant / dot-product kernels for llama.cpp `TQ3_0` *weights* live in `src/main/cpp/llama.cpp/ggml/src/ggml-cpu/arch/arm/quants.c` (see `conductor/optimize-tq3-neon.md`). Those kernels run when the GGUF contains `TQ3_0` tensors. They are not applied to the KV cache.
*   **KV cache** — JNI sets `type_k` / `type_v` to `GGML_TYPE_F16`. Context length is `DEFAULT_CONTEXT_SIZE = 2048` tokens (`medgemma-jni.cpp`).
*   **ARM build** — `arm64-v8a` only, compiled with `-march=armv8.4-a+dotprod+i8mm` (`build.gradle.kts`, `src/main/cpp/CMakeLists.txt`).

QLoRA / PEFT adapters are not present in the app code and are not loaded by JNI. llama.cpp's generic LoRA APIs exist only in the vendored tree.

4k+ context and an 8GB RAM requirement are **not** asserted here: the source sets 2048 tokens, and no RAM floor is documented outside an older README recommendation.

## Architecture

*   **UI:** Jetpack Compose (Material 3)
*   **Engine:** `llama.cpp` + `mtmd` (CPU, mmap, flash attention on)
*   **Threads:** 4 (pinned to CPUs 4–7 when 8+ cores are present)
*   **Vision:** images are letterboxed to 448×448 RGB for the JNI fast path (`ImageDecode.kt`)

## Prerequisites

*   **Android Studio** (or the Android SDK + NDK + CMake from the command line)
*   **adb** — push models and install the APK
*   **wget or curl** — download the GGUFs
*   **An arm64 Android device** — the APK is `arm64-v8a` only. I8MM / dotprod CPUs match the build flags.

### Installing the NDK via Android Studio

To compile the native C++ code (`llama.cpp`) install the Android NDK and CMake:

1. Open **Android Studio**.
2. Go to **Tools > SDK Manager**.
3. Select the **SDK Tools** tab.
4. Check **NDK (Side by side)** and **CMake**.
5. Click **Apply**, then **OK**.

## Getting Started

### 1. Download the Models

The app does not pin a Hugging Face repo in code; it loads whatever files sit at the paths above. This README's download step uses Unsloth's MedGemma 1.5 4B Instruct GGUFs (language model + `mmproj`):

```bash
cd /path/to/your/Heal
mkdir -p local_models
cd local_models

wget -O model.gguf "https://huggingface.co/unsloth/medgemma-1.5-4b-it-GGUF/resolve/main/medgemma-1.5-4b-it-UD-Q6_K_XL.gguf?download=true"
wget -O mmproj.gguf "https://huggingface.co/unsloth/medgemma-1.5-4b-it-GGUF/resolve/main/mmproj-F16.gguf?download=true"

cd ..
```

These files are large (multiple GB). The `UD-Q6_K_XL` filename is a Q6_K-class quant, not `TQ3_0`. A `TQ3_0` GGUF would be needed to exercise the TurboQuant kernels.

### 2. Push Models to the Android Device

```bash
adb devices
adb shell mkdir -p /data/local/tmp/models/
adb push local_models/model.gguf /data/local/tmp/models/model.gguf
adb push local_models/mmproj.gguf /data/local/tmp/models/mmproj.gguf
```

### 3. Build and Install

```bash
./gradlew assembleDebug
adb install -r build/outputs/apk/debug/MedGemma-debug.apk
adb shell am start -n com.example.medgemma/com.example.medgemma.MainActivity
```

### 4. Monitor Performance (Optional)

```bash
adb logcat -v time -v color | grep -E "LlamaNative|MedGemmaNative"
```

## Troubleshooting

*   **Model not found:** both `model.gguf` and `mmproj.gguf` must be readable at `/data/local/tmp/models/`.
*   **Slow inference:** the build targets `armv8.4-a+dotprod+i8mm`. Older CPUs fall back to generic matmul.
*   **Nonsense output:** keep the Gemma chat template (`<start_of_turn>` / `<end_of_turn>`). Do not add a leading space or extra roles.
*   **Not medical advice:** the system prefix and the in-app disclaimer say the same thing — consult a healthcare professional.
