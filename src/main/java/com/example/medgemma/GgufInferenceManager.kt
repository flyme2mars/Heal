package com.example.medgemma

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GgufInferenceManager(private val context: Context) {
    companion object {
        init {
            System.loadLibrary("medgemma-native")
        }
    }

    interface InferenceCallback {
        fun onToken(token: String)
    }

    var isInitialized = false
        private set

    suspend fun initialize(modelPath: String, mmprojPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!File(modelPath).exists()) return@withContext Result.failure(Exception("Model file not found"))
            if (!File(mmprojPath).exists()) return@withContext Result.failure(Exception("mmproj file not found"))
            if (isInitialized) {
                deinitNative()
                isInitialized = false
            }
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            val status = initNative(nativeLibDir, modelPath, mmprojPath)
            if (status == 0) {
                isInitialized = true
                Result.success(Unit)
            } else {
                Result.failure(Exception("Native init failed: $status"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deinitialize() {
        if (isInitialized) {
            deinitNative()
            isInitialized = false
        }
    }

    fun resetContext() {
        if (isInitialized) resetContextNative()
    }

    fun stopGeneration() {
        if (isInitialized) stopNative()
    }

    fun generateStream(
        prompt: String,
        imageBytes: ByteArray? = null,
        clearContext: Boolean = false
    ): Flow<String> = callbackFlow {
        if (!isInitialized) {
            trySend("Error: GGUF Engine not initialized")
            close()
            return@callbackFlow
        }
        val tokenChannel = Channel<String>(Channel.UNLIMITED)
        val callback = object : InferenceCallback {
            override fun onToken(token: String) {
                tokenChannel.trySend(token)
            }
        }
        val generationJob = launch(Dispatchers.IO) {
            try {
                generateNative(prompt, imageBytes, clearContext, callback)
            } finally {
                tokenChannel.close()
            }
        }
        tokenChannel.consumeAsFlow().collect { token ->
            trySend(token)
        }
        generationJob.join()
        close()
        awaitClose {
            stopGeneration()
            generationJob.cancel()
            tokenChannel.close()
        }
    }

    private external fun initNative(nativeLibDir: String, modelPath: String, mmprojPath: String): Int
    private external fun deinitNative()
    private external fun resetContextNative()
    private external fun stopNative()
    private external fun generateNative(
        prompt: String,
        imageBytes: ByteArray?,
        clearContext: Boolean,
        callback: InferenceCallback
    )
}