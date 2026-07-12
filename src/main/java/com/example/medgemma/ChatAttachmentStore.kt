package com.example.medgemma

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Copies picker/content URIs into app-private storage so chat thumbs and lightbox
 * keep working after process death and temporary grant expiry.
 */
object ChatAttachmentStore {
    private const val DIR = "chat_images"

    fun getImagesDir(context: Context): File {
        val dir = File(context.filesDir, DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Persist [source] into filesDir/chat_images/ and return a stable file:// Uri.
     * Returns null if copy fails.
     */
    fun persistImage(context: Context, source: Uri): Uri? {
        return try {
            val ext = guessExtension(context, source)
            val dest = File(getImagesDir(context), "img_${UUID.randomUUID()}$ext")
            context.contentResolver.openInputStream(source)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output, bufferSize = 64 * 1024) }
            } ?: return null
            if (!dest.exists() || dest.length() == 0L) {
                dest.delete()
                return null
            }
            Uri.fromFile(dest)
        } catch (_: Exception) {
            null
        }
    }

    private fun guessExtension(context: Context, uri: Uri): String {
        val type = context.contentResolver.getType(uri)?.lowercase().orEmpty()
        return when {
            "png" in type -> ".png"
            "webp" in type -> ".webp"
            "gif" in type -> ".gif"
            "heic" in type || "heif" in type -> ".jpg"
            else -> ".jpg"
        }
    }
}
