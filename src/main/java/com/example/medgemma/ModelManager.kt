package com.example.medgemma

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class GgufModel(
    val name: String,
    val url: String,
    val fileName: String,
    val type: ModelType
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
        .build()

    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadProgress = _downloadProgress.asStateFlow()

    var hfToken: String? = null

    val availableLlmModels = listOf(
        GgufModel(
            "MedGemma o1 Q6_K",
            "https://huggingface.co/vyyyyyyy/medgemma-4b-o1-reasoning-gguf/resolve/main/medgemma-o1-q6_k.gguf?download=true",
            "medgemma-o1-q6_k.gguf",
            ModelType.LLM
        ),
        GgufModel(
            "MedGemma o1 Q4_K_M",
            "https://huggingface.co/vyyyyyyy/medgemma-4b-o1-reasoning-gguf/resolve/main/medgemma-o1-q4_k_m.gguf?download=true",
            "medgemma-o1-q4_k_m.gguf",
            ModelType.LLM
        )
    )

    val availableMmprojModels = listOf(
        GgufModel(
            "mmproj Q8_0",
            "https://huggingface.co/vyyyyyyy/medgemma-1.5-4b-it-vision-GGUF/resolve/main/mmproj-Q8_0.gguf?download=true",
            "mmproj-Q8_0.gguf",
            ModelType.MMPROJ
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

    fun getModelDir(): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun isModelDownloaded(fileName: String): Boolean {
        val file = File(getModelDir(), fileName)
        return file.exists() && !File(getModelDir(), "$fileName.tmp").exists()
    }

    suspend fun downloadModel(model: GgufModel) = withContext(Dispatchers.IO) {
        _downloadProgress.value += (model.fileName to DownloadProgress(model.fileName, 0f, true))

        try {
            val destinationFile = File(getModelDir(), model.fileName)
            val tempFile = File(getModelDir(), "${model.fileName}.tmp")
            
            // Delete existing models of the same type first
            val currentModels = getModelDir().listFiles() ?: emptyArray()
            for (file in currentModels) {
                if (file.name.endsWith(".gguf") && file.name != model.fileName) {
                    val isLlm = availableLlmModels.any { it.fileName == file.name }
                    val isMmproj = availableMmprojModels.any { it.fileName == file.name }
                    
                    if ((model.type == ModelType.LLM && isLlm) || (model.type == ModelType.MMPROJ && isMmproj)) {
                        Log.d(TAG, "Deleting old model: ${file.name}")
                        file.delete()
                    }
                }
            }

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
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorMsg = "Server returned ${response.code}: ${response.message}"
                    Log.e(TAG, errorMsg)
                    throw IOException(errorMsg)
                }

                val body = response.body ?: throw IOException("Empty response body")
                val fileLength = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(tempFile)

                val data = ByteArray(8192)
                var total: Long = 0
                var count: Int
                while (inputStream.read(data).also { count = it } != -1) {
                    total += count
                    if (fileLength > 0) {
                        val progress = total.toFloat() / fileLength.toFloat()
                        _downloadProgress.value += (model.fileName to DownloadProgress(model.fileName, progress, true))
                    }
                    outputStream.write(data, 0, count)
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()
            }

            // Rename temp to destination
            if (tempFile.renameTo(destinationFile)) {
                _downloadProgress.value += (model.fileName to DownloadProgress(model.fileName, 1.0f, false))
            } else {
                throw IOException("Failed to rename temp file")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for ${model.fileName}", e)
            _downloadProgress.value += (model.fileName to DownloadProgress(model.fileName, 0f, false, e.toString()))
            // Clean up partial file
            File(getModelDir(), "${model.fileName}.tmp").delete()
        }
    }

    fun deleteModel(fileName: String) {
        File(getModelDir(), fileName).delete()
        _downloadProgress.value -= fileName
    }
    
    fun getDownloadedLlmPath(): String? {
        return getModelDir().listFiles()?.find { file -> 
            availableLlmModels.any { it.fileName == file.name }
        }?.absolutePath
    }

    fun getDownloadedMmprojPath(): String? {
        return getModelDir().listFiles()?.find { file -> 
            availableMmprojModels.any { it.fileName == file.name }
        }?.absolutePath
    }
}
