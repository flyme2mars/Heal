package com.example.medgemma

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decode [uri] into a fixed [maxDim]×[maxDim] RGB888 buffer for the native vision path.
 *
 * Uses bounds + inSampleSize (never full-res camera bitmaps), EXIF orientation, and
 * aspect-preserving letterbox into a square so medgemma-jni keeps the 448³ fast path.
 */
fun uriToRgbByteArray(
    context: Context,
    uri: Uri,
    maxDim: Int = 448
): ByteArray? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDim, maxDim)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null

        bitmap = applyExifOrientation(context, uri, bitmap)

        val letterboxed = letterboxToSquare(bitmap, maxDim)
        if (letterboxed !== bitmap) bitmap.recycle()

        val rgb = bitmapToRgb888(letterboxed, maxDim)
        letterboxed.recycle()
        rgb
    } catch (_: Exception) {
        null
    }
}

private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        var halfH = height / 2
        var halfW = width / 2
        while ((halfH / inSampleSize) >= reqHeight && (halfW / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return max(1, inSampleSize)
}

private fun applyExifOrientation(context: Context, uri: Uri, source: Bitmap): Bitmap {
    val orientation = try {
        context.contentResolver.openInputStream(uri)?.use { stream: InputStream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (degrees == 0f) return source

    val matrix = Matrix().apply { postRotate(degrees) }
    val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    if (rotated !== source) source.recycle()
    return rotated
}

/** Fit image into [size]×[size] with black letterboxing (preserves aspect). */
private fun letterboxToSquare(source: Bitmap, size: Int): Bitmap {
    val scale = size.toFloat() / max(source.width, source.height)
    val w = max(1, (source.width * scale).roundToInt())
    val h = max(1, (source.height * scale).roundToInt())
    val scaled = if (source.width == w && source.height == h) {
        source
    } else {
        Bitmap.createScaledBitmap(source, w, h, true)
    }

    if (w == size && h == size) {
        return scaled
    }

    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    canvas.drawColor(android.graphics.Color.BLACK)
    val left = ((size - w) / 2f)
    val top = ((size - h) / 2f)
    canvas.drawBitmap(scaled, left, top, null)
    if (scaled !== source) scaled.recycle()
    return out
}

private fun bitmapToRgb888(bitmap: Bitmap, size: Int): ByteArray {
    val pixels = IntArray(size * size)
    bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
    val rgb = ByteArray(size * size * 3)
    var o = 0
    for (p in pixels) {
        rgb[o++] = ((p shr 16) and 0xFF).toByte()
        rgb[o++] = ((p shr 8) and 0xFF).toByte()
        rgb[o++] = (p and 0xFF).toByte()
    }
    return rgb
}
