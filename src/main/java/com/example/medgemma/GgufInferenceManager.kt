package com.example.medgemma

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File

class GgufInferenceManager {
    companion object {
        private const val TAG = "GgufInference"
        
        init {
            System.loadLibrary("medgemma-native")
        }
    }

    interface InferenceCallback {
        fun onToken(token: String)
    }

    private var isInitialized = false

    suspend fun initialize(modelPath: String, mmprojPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!File(modelPath).exists()) return@withContext Result.failure(Exception("Model file not found"))
            if (!File(mmprojPath).exists()) return@withContext Result.failure(Exception("mmproj file not found"))

            val status = initNative(modelPath, mmprojPath)
            if (status == 0) {
                isInitialized = true
                Result.success(Unit)
            } else {
                Result.failure(Exception("Native initialization failed with status: $status"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Initialization error", e)
            Result.failure(e)
        }
    }

    fun generateStream(prompt: String, imageBytes: ByteArray? = null): Flow<String> = callbackFlow {
        if (!isInitialized) {
            trySend("Error: GGUF Engine not initialized")
            close()
            return@callbackFlow
        }

        val callback = object : InferenceCallback {
            override fun onToken(token: String) {
                Log.d(TAG, "onToken: \"$token\"")
                trySend(token)
            }
        }

        withContext(Dispatchers.IO) {
            generateNative(prompt, imageBytes, callback)
        }
        
        close()
        awaitClose { /* Clean up if needed */ }
    }

    // Native methods
    private external fun initNative(modelPath: String, mmprojPath: String): Int
    private external fun generateNative(prompt: String, imageBytes: ByteArray?, callback: InferenceCallback)
}
