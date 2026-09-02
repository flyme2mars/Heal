package com.example.medgemma

import java.io.File

/**
 * Detects on-device GGUF weights pushed via adb to `/data/local/tmp/models`.
 * No catalog, token, or in-app download.
 */
class ModelManager {
    fun refreshDownloadedCache() {
        // Paths are probed live in [getDownloadedLlmPath] / [getDownloadedMmprojPath].
    }

    fun getDownloadedLlmPath(): String? = adbWeightPath(ADB_LLM_NAME)

    fun getDownloadedMmprojPath(): String? = adbWeightPath(ADB_MMPROJ_NAME)

    private fun adbWeightPath(fileName: String): String? {
        val file = File(ADB_MODEL_DIR, fileName)
        return if (file.isFile && file.canRead()) file.absolutePath else null
    }

    companion object {
        const val ADB_MODEL_DIR = "/data/local/tmp/models"
        const val ADB_LLM_NAME = "model.gguf"
        const val ADB_MMPROJ_NAME = "mmproj.gguf"
    }
}
