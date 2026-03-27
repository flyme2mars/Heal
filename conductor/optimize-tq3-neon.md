# Plan: Optimize TQ3_0 Performance on ARM (NEON)

## Objective
Significant performance improvement for MedGemma 1.5 (TurboQuant) multimodal inference by implementing ARM NEON optimized kernels for `TQ3_0` quantization, dequantization, and dot products.

## Background
TurboQuant (`TQ3_0`) is a 3-bit compression technique that uses a Walsh-Hadamard Transform (WHT) to "Gaussianize" weights before Lloyd-Max quantization. The current `llama.cpp` implementation uses a reference C++ path for `TQ3_0` dot products, which performs a 32-point WHT for every block in every dot product. This is extremely slow during prefill (vision encoding) and multimodal input processing.

## Key Files & Context
- `src/main/cpp/llama.cpp/ggml/src/ggml-quants.c`: Contains the reference `TQ3_0` implementation.
- `src/main/cpp/llama.cpp/ggml/src/ggml-cpu/arch/arm/quants.c`: Contains optimized ARM kernels for other types (TQ1, TQ2, etc.).
- `src/main/cpp/medgemma-jni.cpp`: App entry point where threads and context are configured.

## Proposed Solution
1. **Implement `WHT32_NEON`**: A highly optimized 32-point Walsh-Hadamard Transform using NEON intrinsics.
2. **Optimize `dequantize_row_tq3_0`**: Vectorize the unpacking of 3-bit indices, centroid lookup, and the inverse RHT (WHT + sign flip).
3. **Optimize `ggml_vec_dot_tq3_0_q8_0`**: Implement a NEON dot product kernel that minimizes dequantization overhead.
4. **Integration**: Add these kernels to the ARM-specific quants file to ensure they are picked up by the build system.

## Implementation Steps

### Phase 1: WHT32 NEON Implementation
- Implement a 5-stage vectorized WHT.
- Use `vaddq_f32` and `vsubq_f32` for large strides (16, 8, 4).
- Use `vtrn`, `vuzp`, and `vzip` for small strides (2, 1) within registers.

### Phase 2: TQ3_0 Kernel Implementation
- **Unpacking**: Use bitwise shifts and masks on NEON registers to extract 3-bit indices from the 12-byte packed buffer.
- **Centroid Lookup**: Use `vdup_n_f32` or a small table if possible to map indices to centroids.
- **Dequantization**: Combine WHT, sign flip (`TQ3_0_SIGNS`), and RMS scaling (`block_tq3_0.d`).

### Phase 3: Integration & Testing
- Update `src/main/cpp/llama.cpp/ggml/src/ggml-cpu/arch/arm/quants.c` with the new kernels.
- (Optional) Reduce `n_threads` to 4 for heterogeneous core stability on Snapdragon 8+ Gen 1 if necessary.

## Verification
- Measure the "image slice encoded" time in logcat. Expect a 10x-50x speedup.
- Measure "Prefill (eval_chunks)" time.
- Verify output quality (correctness of dequantization).
