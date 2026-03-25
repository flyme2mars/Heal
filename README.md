# Heal - Local Medical AI on Android

Heal is a native Android application that runs a powerful medical domain-specific Large Language Model locally on your device. The app utilizes a highly optimized `llama.cpp` backend via JNI to achieve impressive on-device inference speeds using ARMv8.2-A optimizations (I8MM & dotprod), completely offline.

## Architecture

*   **UI:** Jetpack Compose (Material 3)
*   **Engine:** `llama.cpp` + `mtmd` (Multimodal support)
*   **Hardware Acceleration:** Native ARM NEON & I8MM instructions.

## Prerequisites

Before building and running the project, ensure you have the following installed:

*   **Android Studio:** To build and run the project.
*   **Android SDK & NDK:** Required for building the C++ JNI bridge and the Android app. (See instructions below).
*   **adb (Android Debug Bridge):** Required for pushing models and installing the app to your device.
*   **wget or curl:** For downloading the model files via the terminal.
*   **A capable Android device:** We recommend a device with at least 8GB of RAM (12GB+ preferred) and an ARMv8.2-A compliant CPU.

### Installing the NDK via Android Studio

To compile the native C++ code (llama.cpp) you must install the Android NDK (Native Development Kit) and CMake via Android Studio:

1. Open **Android Studio**.
2. Go to **Tools > SDK Manager** (or click the SDK Manager icon in the toolbar).
3. Select the **SDK Tools** tab.
4. Check the boxes next to **NDK (Side by side)** and **CMake**.
5. Click **Apply** and then **OK** to download and install them.

## Getting Started

Follow these steps exactly to download the models, push them to your device, and run the app. You can copy and paste the commands directly into your terminal.

### 1. Download the Models

The app currently uses MedGemma 1.5 4B (Instruct) as its core model. It requires two GGUF files: the quantized language model and the multimodal projector (`mmproj`).

Create a `local_models` directory if it doesn't exist, and download the models directly from Hugging Face:

```bash
# Navigate to the project root
cd /path/to/your/Heal

# Create the local_models directory
mkdir -p local_models
cd local_models

# Download the main language model (Q6_K_XL quantization for balanced speed/quality)
wget -O model.gguf "https://huggingface.co/unsloth/medgemma-1.5-4b-it-GGUF/resolve/main/medgemma-1.5-4b-it-UD-Q6_K_XL.gguf?download=true"

# Download the vision projector model (FP16)
wget -O mmproj.gguf "https://huggingface.co/unsloth/medgemma-1.5-4b-it-GGUF/resolve/main/mmproj-F16.gguf?download=true"

cd ..
```

*(Note: These files are quite large (~4GB total) and may take some time to download depending on your internet connection).*

### 2. Push Models to the Android Device

The Android app is configured to load the models from a specific temporary directory on your device. Connect your phone via USB (ensure USB Debugging is enabled), and run the following commands to create the directory and push the files:

```bash
# Ensure your device is connected and authorized
adb devices

# Create the target directory on the device
adb shell mkdir -p /data/local/tmp/models/

# Push the language model
adb push local_models/model.gguf /data/local/tmp/models/model.gguf

# Push the vision projector
adb push local_models/mmproj.gguf /data/local/tmp/models/mmproj.gguf
```

### 3. Build and Install the Application

Use Gradle to compile the C++ libraries via CMake and build the Android APK. Once built, install it and launch the main activity.

```bash
# Build the debug APK
./gradlew assembleDebug

# Install the APK onto the device
adb install -r build/outputs/apk/debug/MedGemma-debug.apk

# Launch the application
adb shell am start -n com.example.medgemma/com.example.medgemma.MainActivity
```

### 4. Monitor Performance (Optional)

If you wish to view the native inference logs and monitor the generation speed in real-time, you can tail the Android Logcat:

```bash
adb logcat -v time -v color | grep -E "LlamaNative|MedGemmaNative"
```

## Troubleshooting

*   **App crashes immediately after typing a message:** Ensure that both `model.gguf` and `mmproj.gguf` were pushed successfully and are located exactly at `/data/local/tmp/models/` on the device.
*   **Inference is extremely slow:** Ensure your device's CPU supports ARMv8.2-A. The build system is specifically tuned to use `I8MM` instructions. Older devices will fallback to generic matrix multiplication which is significantly slower.
*   **Model generates "nonsense":** The app uses a precise chat template. If you modify the Kotlin code, ensure you do not add leading spaces or alter the `<start_of_turn>` and `<end_of_turn>` tags.
