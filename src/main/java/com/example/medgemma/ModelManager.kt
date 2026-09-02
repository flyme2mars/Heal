package com.example.medgemma

import android.content.Context
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class GgufModel(
    val name: String,
    val url: String,
    val fileName: String,
    val type: ModelType,
    val sizeLabel: String = ""
)

enum class ModelType {
    LLM, MMPROJ
}

data class DownloadProgress(
    val fileName: String,
    val progress: Float, // 0.0 to 1.0
    val isDownloading: Boolean = false,
    val error: String? = null
)

class ModelManager(private val context: Context) {
    private val TAG = "ModelManager"
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        // Multi-GB model pulls need long read windows; connect stays tight.
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadProgress = _downloadProgress.asStateFlow()

    /** Cached on-disk set; avoids listFiles() from Compose every progress tick. */
    private val _downloadedFiles = MutableStateFlow<Set<String>>(emptySet())
    val downloadedFiles: StateFlow<Set<String>> = _downloadedFiles.asStateFlow()

    /** Active OkHttp calls so UI can cancel multi-GB pulls. */
    private val activeCalls = ConcurrentHashMap<String, Call>()

    var hfToken: String? = null

    val availableLlmModels = listOf(
        GgufModel(
            "MedGemma 1.5 TQ3 TurboQuant",
            "https://huggingface.co/unsloth/medgemma-1.5-4b-it-GGUF/resolve/main/medgemma-1.5-4b-it-UD-TQ3_0.gguf?download=true",
            "medgemma-1.5-4b-it-UD-TQ3_0.gguf",
            ModelType.LLM,
            "~1.5 GB"
        ),
        GgufModel(
            "MedGemma 1.5 Q6_K_XL",
            "https://huggingface.co/unsloth/medgemma-1.5-4b-it-GGUF/resolve/main/medgemma-1.5-4b-it-UD-Q6_K_XL.gguf?download=true",
            "medgemma-1.5-4b-it-UD-Q6_K_XL.gguf",
            ModelType.LLM,
            "~3.2 GB"
        ),
        GgufModel(
            "MedGemma o1 Q6_K",
            "https://huggingface.co/vyyyyyyy/medgemma-4b-o1-reasoning-gguf/resolve/main/medgemma-o1-q6_k.gguf?download=true",
            "medgemma-o1-q6_k.gguf",
            ModelType.LLM,
            "~3.4 GB"
        ),
        GgufModel(
            "MedGemma o1 Q4_K_M",
            "https://huggingface.co/vyyyyyyy/medgemma-4b-o1-reasoning-gguf/resolve/main/medgemma-o1-q4_k_m.gguf?download=true",
            "medgemma-o1-q4_k_m.gguf",
            ModelType.LLM,
            "~2.3 GB"
        )
    )

    val availableMmprojModels = listOf(
        GgufModel(
            "mmproj F16 (recommended)",
            "https://huggingface.co/unsloth/medgemma-1.5-4b-it-GGUF/resolve/main/mmproj-F16.gguf?download=true",
            "mmproj-F16.gguf",
            ModelType.MMPROJ,
            "~681 MB"
        ),
        GgufModel(
            "mmproj Q8_0",
            "https://huggingface.co/vyyyyyyy/medgemma-1.5-4b-it-vision-GGUF/resolve/main/mmproj-Q8_0.gguf?download=true",
            "mmproj-Q8_0.gguf",
            ModelType.MMPROJ,
            "~681 MB"
        ),
        GgufModel(
            "mmproj Q6_K",
            "https://huggingface.co/vyyyyyyy/medgemma-1.5-4b-it-vision-GGUF/resolve/main/mmproj-Q6_K.gguf?download=true",
            "mmproj-Q6_K.gguf",
            ModelType.MMPROJ
        ),
        GgufModel(
            "mmproj Q5_K_M",
            "https://huggingface.co/vyyyyyyy/medgemma-1.5-4b-it-vision-GGUF/resolve/main/mmproj-Q5_K_M.gguf?download=true",
            "mmproj-Q5_K_M.gguf",
            ModelType.MMPROJ
        ),
        GgufModel(
            "mmproj Q4_K_M",
            "https://huggingface.co/vyyyyyyy/medgemma-1.5-4b-it-vision-GGUF/resolve/main/mmproj-Q4_K_M.gguf?download=true",
            "mmproj-Q4_K_M.gguf",
            ModelType.MMPROJ
        )
    )

    init {
        refreshDownloadedCache()
    }

    fun getModelDir(): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun refreshDownloadedCache() {
        val names = getModelDir().listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.name.endsWith(".gguf") && !it.name.endsWith(".tmp") }
            ?.map { it.name }
            ?.toSet()
            ?: emptySet()
        _downloadedFiles.value = names
    }

    fun isModelDownloaded(fileName: String): Boolean =
        fileName in _downloadedFiles.value

    /**
     * Download [model] with throttled progress (max ~1% or 200ms), larger I/O buffer,
     * free-space check, and delete-previous-only-after-success semantics.
     * Cancellable via [cancelDownload].
     */
    suspend fun downloadModel(model: GgufModel) = withContext(Dispatchers.IO) {
        // Cancel any in-flight pull of the same file first.
        cancelDownload(model.fileName)

        emitProgress(model.fileName, 0f, isDownloading = true)

        val modelDir = getModelDir()
        val destinationFile = File(modelDir, model.fileName)
        val tempFile = File(modelDir, "${model.fileName}.tmp")
        tempFile.delete()

        try {
            // Rough lower bound from size label when present (e.g. "~1.5 GB"); else 500MB.
            val minBytes = estimateMinBytes(model.sizeLabel)
            ensureFreeSpace(modelDir, minBytes)

            val requestBuilder = Request.Builder()
                .url(model.url)
                .header("User-Agent", "Mozilla/5.0")

            hfToken?.let { token ->
                if (token.isNotBlank()) {
                    Log.d(TAG, "Using HF Token for authentication")
                    requestBuilder.header("Authorization", "Bearer $token")
                }
            }

            val request = requestBuilder.build()
            val call = client.newCall(request)
            activeCalls[model.fileName] = call

            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errorMsg = "Server returned ${response.code}: ${response.message}"
                    Log.e(TAG, errorMsg)
                    throw IOException(errorMsg)
                }

                val body = response.body ?: throw IOException("Empty response body")
                val fileLength = body.contentLength()
                if (fileLength > 0) {
                    ensureFreeSpace(modelDir, fileLength + MIN_FREE_MARGIN_BYTES)
                }

                body.byteStream().use { inputStream ->
                    FileOutputStream(tempFile).use { outputStream ->
                        val data = ByteArray(IO_BUFFER_BYTES)
                        var total = 0L
                        var lastEmitAt = 0L
                        var lastEmitProgress = -1f
                        var count: Int
                        while (inputStream.read(data).also { count = it } != -1) {
                            if (call.isCanceled()) {
                                throw DownloadCancelledException()
                            }
                            outputStream.write(data, 0, count)
                            total += count
                            if (fileLength > 0) {
                                val progress = (total.toFloat() / fileLength.toFloat()).coerceIn(0f, 1f)
                                val now = System.currentTimeMillis()
                                val progressDelta = progress - lastEmitProgress
                                val timeDelta = now - lastEmitAt
                                if (progressDelta >= PROGRESS_STEP || timeDelta >= PROGRESS_INTERVAL_MS || progress >= 1f) {
                                    emitProgress(model.fileName, progress, isDownloading = true)
                                    lastEmitAt = now
                                    lastEmitProgress = progress
                                }
                            }
                        }
                        outputStream.flush()
                    }
                }

                if (fileLength > 0 && tempFile.length() != fileLength) {
                    throw IOException(
                        "Download size mismatch: expected $fileLength, got ${tempFile.length()}"
                    )
                }
            }

            // Promote temp → final, then remove other models of the same type.
            if (destinationFile.exists()) destinationFile.delete()
            if (!tempFile.renameTo(destinationFile)) {
                // Cross-filesystem fallback
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
            }

            deleteSiblingModels(model)
            refreshDownloadedCache()
            emitProgress(model.fileName, 1f, isDownloading = false)
        } catch (e: DownloadCancelledException) {
            Log.i(TAG, "Download cancelled: ${model.fileName}")
            tempFile.delete()
            emitProgress(model.fileName, 0f, isDownloading = false, error = "Cancelled")
        } catch (e: Exception) {
            if (e is IOException && activeCalls[model.fileName]?.isCanceled() == true) {
                Log.i(TAG, "Download cancelled: ${model.fileName}")
                tempFile.delete()
                emitProgress(model.fileName, 0f, isDownloading = false, error = "Cancelled")
            } else {
                Log.e(TAG, "Download failed for ${model.fileName}", e)
                tempFile.delete()
                emitProgress(
                    model.fileName,
                    0f,
                    isDownloading = false,
                    error = e.message ?: e.toString()
                )
            }
        } finally {
            activeCalls.remove(model.fileName)
        }
    }

    /** Abort an in-flight download and delete the partial .tmp file. */
    fun cancelDownload(fileName: String) {
        activeCalls.remove(fileName)?.cancel()
        File(getModelDir(), "$fileName.tmp").delete()
    }

    fun deleteModel(fileName: String) {
        cancelDownload(fileName)
        File(getModelDir(), fileName).delete()
        File(getModelDir(), "$fileName.tmp").delete()
        _downloadProgress.value = _downloadProgress.value - fileName
        refreshDownloadedCache()
    }

    private class DownloadCancelledException : IOException("Download cancelled")

    fun getDownloadedLlmPath(): String? {
        adbWeightPath(ADB_LLM_NAME)?.let { return it }
        val names = _downloadedFiles.value
        val fileName = availableLlmModels.firstOrNull { it.fileName in names }?.fileName ?: return null
        val file = File(getModelDir(), fileName)
        return if (file.exists()) file.absolutePath else null
    }

    fun getDownloadedMmprojPath(): String? {
        adbWeightPath(ADB_MMPROJ_NAME)?.let { return it }
        val names = _downloadedFiles.value
        val fileName = availableMmprojModels.firstOrNull { it.fileName in names }?.fileName ?: return null
        val file = File(getModelDir(), fileName)
        return if (file.exists()) file.absolutePath else null
    }

    /** On-device weights pushed via adb to /data/local/tmp/models. */
    private fun adbWeightPath(fileName: String): String? {
        val file = File(ADB_MODEL_DIR, fileName)
        return if (file.isFile && file.canRead()) file.absolutePath else null
    }

    private fun emitProgress(
        fileName: String,
        progress: Float,
        isDownloading: Boolean,
        error: String? = null
    ) {
        _downloadProgress.value = _downloadProgress.value + (
            fileName to DownloadProgress(fileName, progress, isDownloading, error)
            )
    }

    private fun deleteSiblingModels(model: GgufModel) {
        val currentModels = getModelDir().listFiles() ?: return
        for (file in currentModels) {
            if (!file.name.endsWith(".gguf") || file.name == model.fileName) continue
            val isLlm = availableLlmModels.any { it.fileName == file.name }
            val isMmproj = availableMmprojModels.any { it.fileName == file.name }
            if ((model.type == ModelType.LLM && isLlm) || (model.type == ModelType.MMPROJ && isMmproj)) {
                Log.d(TAG, "Deleting old model after successful download: ${file.name}")
                file.delete()
            }
        }
    }

    private fun ensureFreeSpace(dir: File, requiredBytes: Long) {
        val stat = StatFs(dir.absolutePath)
        val available = stat.availableBlocksLong * stat.blockSizeLong
        if (available < requiredBytes) {
            throw IOException(
                "Not enough free space (need ~${requiredBytes / (1024 * 1024)} MB, " +
                    "have ~${available / (1024 * 1024)} MB)"
            )
        }
    }

    private fun estimateMinBytes(sizeLabel: String): Long {
        // Parse "~1.5 GB" / "~681 MB" loosely; fall back to 500 MB.
        val normalized = sizeLabel.lowercase().replace("~", "").trim()
        val number = Regex("""([\d.]+)""").find(normalized)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: return 500L * 1024 * 1024
        return when {
            "gb" in normalized -> (number * 1024 * 1024 * 1024).toLong()
            "mb" in normalized -> (number * 1024 * 1024).toLong()
            else -> 500L * 1024 * 1024
        }
    }

    companion object {
        private const val IO_BUFFER_BYTES = 256 * 1024
        private const val PROGRESS_STEP = 0.01f
        private const val PROGRESS_INTERVAL_MS = 200L
        private const val MIN_FREE_MARGIN_BYTES = 64L * 1024 * 1024
        const val ADB_MODEL_DIR = "/data/local/tmp/models"
        const val ADB_LLM_NAME = "model.gguf"
        const val ADB_MMPROJ_NAME = "mmproj.gguf"
    }
}
