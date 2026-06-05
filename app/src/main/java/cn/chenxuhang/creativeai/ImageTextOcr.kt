package cn.chenxuhang.creativeai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class OcrRecognitionResult(
    val text: String,
    val blockCount: Int,
)

suspend fun Context.runChineseImageOcr(
    imageUri: Uri,
): OcrRecognitionResult {
    val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    val originalImage = InputImage.fromFilePath(this, imageUri)
    val enhancedBitmap = decodeBitmapFromUri(imageUri)
        ?.let(::prepareBitmapForOcr)
    val enhancedImage = enhancedBitmap?.let { InputImage.fromBitmap(it, 0) }
    return try {
        val originalResult = recognizer.processAwait(originalImage)
        val enhancedResult = enhancedImage
            ?.let { recognizer.processAwait(it) }
            ?: OcrRecognitionResult(text = "", blockCount = 0)

        pickBetterOcrResult(
            original = originalResult,
            enhanced = enhancedResult,
        )
    } finally {
        enhancedBitmap?.recycle()
        recognizer.close()
    }
}

private suspend fun com.google.mlkit.vision.text.TextRecognizer.processAwait(
    image: InputImage,
): OcrRecognitionResult {
    return suspendCancellableCoroutine { continuation ->
        process(image)
            .addOnSuccessListener { result ->
                continuation.resume(
                    OcrRecognitionResult(
                        text = result.text.cleanOcrText(),
                        blockCount = result.textBlocks.size,
                    ),
                )
            }
            .addOnFailureListener { error ->
                continuation.resumeWithException(error)
            }
    }
}

private fun pickBetterOcrResult(
    original: OcrRecognitionResult,
    enhanced: OcrRecognitionResult,
): OcrRecognitionResult {
    val originalScore = original.text.length + original.blockCount * 16
    val enhancedScore = enhanced.text.length + enhanced.blockCount * 16
    return if (enhancedScore > originalScore) enhanced else original
}

private fun Context.decodeBitmapFromUri(
    uri: Uri,
): Bitmap? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
            val maxEdge = maxOf(info.size.width, info.size.height)
            if (maxEdge > 2400) {
                val ratio = 2400f / maxEdge.toFloat()
                decoder.setTargetSize(
                    (info.size.width * ratio).toInt().coerceAtLeast(1),
                    (info.size.height * ratio).toInt().coerceAtLeast(1),
                )
            }
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(contentResolver, uri)
            ?.let { bitmap ->
                if (maxOf(bitmap.width, bitmap.height) > 2400) {
                    val ratio = 2400f / maxOf(bitmap.width, bitmap.height).toFloat()
                    Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * ratio).toInt().coerceAtLeast(1),
                        (bitmap.height * ratio).toInt().coerceAtLeast(1),
                        true,
                    ).also {
                        if (it != bitmap) {
                            bitmap.recycle()
                        }
                    }
                } else {
                    bitmap
                }
            }
    }
}

private fun prepareBitmapForOcr(
    source: Bitmap,
): Bitmap {
    val softwareSource = source.toSoftwareBitmap()
    val upscaled = upscaleIfNeeded(softwareSource)
    val grayscale = Bitmap.createBitmap(upscaled.width, upscaled.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(grayscale)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val matrix = ColorMatrix().apply {
        setSaturation(0f)
        postConcat(
            ColorMatrix(
                floatArrayOf(
                    1.35f, 0f, 0f, 0f, -18f,
                    0f, 1.35f, 0f, 0f, -18f,
                    0f, 0f, 1.35f, 0f, -18f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }
    paint.colorFilter = ColorMatrixColorFilter(matrix)
    canvas.drawBitmap(upscaled, 0f, 0f, paint)
    if (upscaled != source) {
        upscaled.recycle()
    }
    if (softwareSource != source && softwareSource != upscaled) {
        softwareSource.recycle()
    }
    return grayscale.toHighContrastBinary()
}

private fun upscaleIfNeeded(
    source: Bitmap,
): Bitmap {
    val maxEdge = maxOf(source.width, source.height)
    if (maxEdge >= 1800) return source
    val ratio = (1800f / maxEdge.toFloat()).coerceAtMost(2.2f)
    return Bitmap.createScaledBitmap(
        source,
        (source.width * ratio).toInt().coerceAtLeast(1),
        (source.height * ratio).toInt().coerceAtLeast(1),
        true,
    )
}

private fun Bitmap.toHighContrastBinary(): Bitmap {
    val result = copy(Bitmap.Config.ARGB_8888, true)
    val pixels = IntArray(result.width * result.height)
    result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
    val averageLuma = pixels
        .map { pixel ->
            (Color.red(pixel) * 0.299f) + (Color.green(pixel) * 0.587f) + (Color.blue(pixel) * 0.114f)
        }
        .average()
        .toFloat()
    val threshold = averageLuma.coerceIn(110f, 190f)
    for (index in pixels.indices) {
        val pixel = pixels[index]
        val luma = (Color.red(pixel) * 0.299f) + (Color.green(pixel) * 0.587f) + (Color.blue(pixel) * 0.114f)
        pixels[index] = if (luma >= threshold) Color.WHITE else Color.BLACK
    }
    result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
    return result
}

private fun Bitmap.toSoftwareBitmap(): Bitmap {
    if (config != Bitmap.Config.HARDWARE) return this
    return copy(Bitmap.Config.ARGB_8888, false)
}

private fun String.cleanOcrText(): String {
    return lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString("\n")
}
