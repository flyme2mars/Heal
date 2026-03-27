#if defined(__ARM_NEON)
#include <arm_neon.h>
#include <math.h>

static const float TQ3_0_CENTROIDS_F32[8] = {
    -2.1519f, -1.3439f, -0.7560f, -0.2451f,
     0.2451f,  0.7560f,  1.3439f,  2.1519f
};

static const float TQ3_0_SIGNS_F32[32] = {
    +1.0f, -1.0f, +1.0f, -1.0f, +1.0f, +1.0f, -1.0f, +1.0f,
    -1.0f, -1.0f, +1.0f, -1.0f, +1.0f, +1.0f, -1.0f, +1.0f,
    -1.0f, -1.0f, +1.0f, -1.0f, +1.0f, -1.0f, -1.0f, +1.0f,
    -1.0f, +1.0f, +1.0f, -1.0f, +1.0f, -1.0f, -1.0f, +1.0f,
};

static inline void ggml_tq3_0_wht32_neon(float32x4_t * v) {
    // Stage 5 (stride 16)
    for (int i = 0; i < 4; i++) {
        float32x4_t a = v[i];
        float32x4_t b = v[i + 4];
        v[i]     = vaddq_f32(a, b);
        v[i + 4] = vsubq_f32(a, b);
    }
    // Stage 4 (stride 8)
    for (int i = 0; i < 2; i++) {
        for (int j = 0; j < 2; j++) {
            float32x4_t a = v[i*4 + j];
            float32x4_t b = v[i*4 + j + 2];
            v[i*4 + j]     = vaddq_f32(a, b);
            v[i*4 + j + 2] = vsubq_f32(a, b);
        }
    }
    // Stage 3 (stride 4)
    for (int i = 0; i < 4; i++) {
        float32x4_t a = v[2*i];
        float32x4_t b = v[2*i + 1];
        v[2*i]     = vaddq_f32(a, b);
        v[2*i + 1] = vsubq_f32(a, b);
    }
    // Stage 2 (stride 2) - [0, 1, 2, 3] -> [0+2, 1+3, 0-2, 1-3]
    for (int i = 0; i < 8; i++) {
        float32x2_t low  = vget_low_f32(v[i]);
        float32x2_t high = vget_high_f32(v[i]);
        v[i] = vcombine_f32(vadd_f32(low, high), vsub_f32(low, high));
    }
    // Stage 1 (stride 1) - [x0, x1] -> [x0+x1, x0-x1]
    for (int i = 0; i < 8; i++) {
        float32x4_t v_rev = vrev64q_f32(v[i]);
        float32x4_t v_sum  = vaddq_f32(v[i], v_rev);
        float32x4_t v_diff = vsubq_f32(v[i], v_rev);
        v[i] = vzip1q_f32(v_sum, v_diff);
    }
}

static inline void dequantize_block_tq3_0_neon(const block_tq3_0 * GGML_RESTRICT x, float32x4_t * v) {
    const float d = GGML_CPU_FP16_TO_FP32(x->d);
    
    uint8_t indices[32];
    for (int g = 0; g < 4; g++) {
        const uint8_t * qp = x->qs + g * 3;
        indices[g * 8 + 0] =  qp[0] & 0x07;
        indices[g * 8 + 1] = (qp[0] >> 3) & 0x07;
        indices[g * 8 + 2] = (qp[0] >> 6) | ((qp[1] << 2) & 0x07);
        indices[g * 8 + 3] = (qp[1] >> 1) & 0x07;
        indices[g * 8 + 4] = (qp[1] >> 4) & 0x07;
        indices[g * 8 + 5] = (qp[1] >> 7) | ((qp[2] << 1) & 0x07);
        indices[g * 8 + 6] = (qp[2] >> 2) & 0x07;
        indices[g * 8 + 7] = (qp[2] >> 5) & 0x07;
    }

    for (int i = 0; i < 8; i++) {
        v[i] = vsetq_lane_f32(TQ3_0_CENTROIDS_F32[indices[i*4 + 0]], v[i], 0);
        v[i] = vsetq_lane_f32(TQ3_0_CENTROIDS_F32[indices[i*4 + 1]], v[i], 1);
        v[i] = vsetq_lane_f32(TQ3_0_CENTROIDS_F32[indices[i*4 + 2]], v[i], 2);
        v[i] = vsetq_lane_f32(TQ3_0_CENTROIDS_F32[indices[i*4 + 3]], v[i], 3);
    }

    ggml_tq3_0_wht32_neon(v);

    const float norm = 1.0f / sqrtf(32.0f);
    const float scale = d * norm;

    for (int i = 0; i < 8; i++) {
        float32x4_t signs = vld1q_f32(TQ3_0_SIGNS_F32 + i*4);
        v[i] = vmulq_n_f32(vmulq_f32(v[i], signs), scale);
    }
}
#endif
