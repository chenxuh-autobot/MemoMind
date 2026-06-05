package cn.chenxuhang.creativeai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import cn.chenxuhang.creativeai.ai.mnn.MnnRuntime
import cn.chenxuhang.creativeai.core.model.MnnSessionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VisionImageTextResult(
    val text: String,
)

suspend fun Context.describeImageWithVisionModel(
    runtime: MnnRuntime,
    config: MnnSessionConfig,
    imageUri: Uri,
): VisionImageTextResult = withContext(Dispatchers.IO) {
    val input = decodeVisionImageInput(imageUri)
        ?: return@withContext VisionImageTextResult("")
    try {
        val result = runtime.generateVisionText(
            config = config,
            prompt = "<img>image_0</img>请像高质量OCR一样忠实转写图片中的文字，保持原始语言和阅读顺序；如果能识别标题层级或表格标题，可用换行轻微整理，但不要补充解释，不要输出“图片”“paper”“OCR”“版面理解”等额外标签。",
            imageRgbBytes = input.rgbBytes,
            width = input.width,
            height = input.height,
            maxNewTokens = 192,
        )
        VisionImageTextResult(result.outputText.orEmpty().cleanVisionText())
    } finally {
        input.bitmap.recycle()
    }
}

private data class VisionImageInput(
    val bitmap: Bitmap,
    val rgbBytes: ByteArray,
    val width: Int,
    val height: Int,
)

private fun Context.decodeVisionImageInput(
    uri: Uri,
): VisionImageInput? {
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
            val maxEdge = maxOf(info.size.width, info.size.height)
            if (maxEdge > 1280) {
                val ratio = 1280f / maxEdge.toFloat()
                decoder.setTargetSize(
                    (info.size.width * ratio).toInt().coerceAtLeast(1),
                    (info.size.height * ratio).toInt().coerceAtLeast(1),
                )
            }
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(contentResolver, uri)
    } ?: return null

    val software = if (bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false).also { bitmap.recycle() }
    } else if (bitmap.config != Bitmap.Config.ARGB_8888) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false).also { bitmap.recycle() }
    } else {
        bitmap
    }

    val pixels = IntArray(software.width * software.height)
    software.getPixels(pixels, 0, software.width, 0, 0, software.width, software.height)
    val rgb = ByteArray(software.width * software.height * 3)
    var offset = 0
    for (pixel in pixels) {
        rgb[offset++] = ((pixel shr 16) and 0xFF).toByte()
        rgb[offset++] = ((pixel shr 8) and 0xFF).toByte()
        rgb[offset++] = (pixel and 0xFF).toByte()
    }
    return VisionImageInput(
        bitmap = software,
        rgbBytes = rgb,
        width = software.width,
        height = software.height,
    )
}

private fun String.cleanVisionText(): String {
    return replace(Regex("""<think>[\s\S]*?</think>"""), "")
        .replace(Regex("""</?tool_call[^>]*>"""), "")
        .replace("</think>", "")
        .replace("<think>", "")
        .replace(Regex("""^\s*paper\s*$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""^\s*ocr\s*$""", RegexOption.IGNORE_CASE), "")
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { it.equals("paper", ignoreCase = true) || it.equals("ocr", ignoreCase = true) }
        .joinToString("\n")
}
