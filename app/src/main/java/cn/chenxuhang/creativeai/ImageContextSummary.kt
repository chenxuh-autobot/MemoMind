package cn.chenxuhang.creativeai

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImageContextSummaryResult(
    val text: String,
)

suspend fun Context.buildImageContextSummary(
    imageUri: Uri,
): ImageContextSummaryResult = withContext(Dispatchers.Default) {
    val labels = runCatching { extractImageSemanticSummary(imageUri) }.getOrNull()
    val ocr = runCatching { runChineseImageOcr(imageUri) }.getOrNull()

    val lines = buildList {
        labels?.summaryText
            ?.takeIf { it.isNotBlank() }
            ?.let { add("画面语义：$it") }
        ocr?.text
            ?.takeIf { it.isNotBlank() }
            ?.let { text ->
                val excerpt = text
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(6)
                    .joinToString("；")
                    .take(220)
                if (excerpt.isNotBlank()) {
                    add("可见文字要点：$excerpt")
                }
            }
    }

    ImageContextSummaryResult(
        text = lines.joinToString(separator = "\n").trim(),
    )
}
