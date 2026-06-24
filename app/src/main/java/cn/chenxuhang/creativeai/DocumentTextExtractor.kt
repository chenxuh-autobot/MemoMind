package cn.chenxuhang.creativeai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Xml
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt
import org.xmlpull.v1.XmlPullParser

data class DocumentExtractionResult(
    val displayName: String,
    val text: String,
    val textPartCount: Int,
    val imageOcrCount: Int,
    val renderedPageCount: Int,
    val warning: String? = null,
)

suspend fun Context.extractDocumentTextFromAsset(
    asset: SelectedLocalAsset,
    cacheDirectory: File,
): DocumentExtractionResult {
    val uri = Uri.parse(asset.uri)
    return when (asset.detectDocumentKind()) {
        DocumentKind.PDF -> extractPdfTextWithOcr(
            uri = uri,
            displayName = asset.displayName,
            cacheDirectory = cacheDirectory,
        )
        DocumentKind.DOCX -> extractOfficeOpenXmlText(
            uri = uri,
            displayName = asset.displayName,
            cacheDirectory = cacheDirectory,
            kind = DocumentKind.DOCX,
        )
        DocumentKind.PPTX -> extractOfficeOpenXmlText(
            uri = uri,
            displayName = asset.displayName,
            cacheDirectory = cacheDirectory,
            kind = DocumentKind.PPTX,
        )
        DocumentKind.PLAIN_TEXT -> extractPlainText(uri = uri, displayName = asset.displayName)
        DocumentKind.LEGACY_WORD -> DocumentExtractionResult(
            displayName = asset.displayName,
            text = "",
            textPartCount = 0,
            imageOcrCount = 0,
            renderedPageCount = 0,
            warning = "旧版 .doc 暂不支持直接解析，请另存为 .docx 后再读取。",
        )
        DocumentKind.LEGACY_PPT -> DocumentExtractionResult(
            displayName = asset.displayName,
            text = "",
            textPartCount = 0,
            imageOcrCount = 0,
            renderedPageCount = 0,
            warning = "旧版 .ppt 暂不支持直接解析，请另存为 .pptx 后再读取。",
        )
        DocumentKind.UNKNOWN -> DocumentExtractionResult(
            displayName = asset.displayName,
            text = "",
            textPartCount = 0,
            imageOcrCount = 0,
            renderedPageCount = 0,
            warning = "暂不支持这种文件类型。",
        )
    }
}

private suspend fun Context.extractPdfTextWithOcr(
    uri: Uri,
    displayName: String,
    cacheDirectory: File,
): DocumentExtractionResult {
    val pageTexts = mutableListOf<String>()
    var totalPages = 0
    contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            totalPages = renderer.pageCount
            val pageCount = minOf(renderer.pageCount, MaxPdfOcrPages)
            repeat(pageCount) { pageIndex ->
                val page = renderer.openPage(pageIndex)
                try {
                    val bitmap = page.renderToBitmap()
                    val pageImageFile = cacheDirectory.createTempImageFile(
                        prefix = "pdf_${displayName.safeFileStem()}_${pageIndex + 1}",
                        extension = "jpg",
                    )
                    FileOutputStream(pageImageFile).use { output ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
                    }
                    bitmap.recycle()
                    val ocrText = runCatching {
                        runChineseImageOcr(Uri.fromFile(pageImageFile)).text
                    }.getOrDefault("")
                    pageImageFile.delete()
                    if (ocrText.isNotBlank()) {
                        pageTexts += "[第${pageIndex + 1}页]\n$ocrText"
                    }
                } finally {
                    page.close()
                }
            }
        }
    }
    val warning = when {
        totalPages > MaxPdfOcrPages -> "PDF 较长，已先读取前 $MaxPdfOcrPages 页。"
        pageTexts.isEmpty() -> "PDF 已扫描，但没有识别到稳定文字。"
        else -> null
    }
    return DocumentExtractionResult(
        displayName = displayName,
        text = pageTexts.joinToString("\n\n").limitDocumentChars(),
        textPartCount = pageTexts.size,
        imageOcrCount = 0,
        renderedPageCount = minOf(totalPages, MaxPdfOcrPages),
        warning = warning,
    )
}

private suspend fun Context.extractOfficeOpenXmlText(
    uri: Uri,
    displayName: String,
    cacheDirectory: File,
    kind: DocumentKind,
): DocumentExtractionResult {
    val bodyParts = mutableListOf<String>()
    val imageOcrParts = mutableListOf<String>()
    var skippedImageCount = 0
    contentResolver.openInputStream(uri)?.use { input ->
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val entryName = entry.name
                if (!entry.isDirectory && kind.isTextXmlEntry(entryName)) {
                    runCatching {
                        val text = zip.extractTextNodesFromXml()
                        if (text.isNotBlank()) {
                            bodyParts += text
                        }
                    }
                } else if (!entry.isDirectory && kind.isMediaImageEntry(entryName)) {
                    if (imageOcrParts.size < MaxOfficeImageOcrCount) {
                        val mediaFile = cacheDirectory.createTempImageFile(
                            prefix = "${kind.name.lowercase(Locale.US)}_${displayName.safeFileStem()}_${imageOcrParts.size + 1}",
                            extension = entryName.substringAfterLast('.', "jpg"),
                        )
                        FileOutputStream(mediaFile).use { output ->
                            zip.copyTo(output)
                        }
                        val ocrText = runCatching {
                            runChineseImageOcr(Uri.fromFile(mediaFile)).text
                        }.getOrDefault("")
                        mediaFile.delete()
                        if (ocrText.isNotBlank()) {
                            imageOcrParts += "[内嵌图片${imageOcrParts.size + 1}]\n$ocrText"
                        }
                    } else {
                        skippedImageCount += 1
                    }
                }
                zip.closeEntry()
            }
        }
    }

    val bodyText = bodyParts.joinToString("\n").normalizeExtractedWhitespace()
    val mergedText = buildList {
        if (bodyText.isNotBlank()) {
            add("[正文]\n$bodyText")
        }
        addAll(imageOcrParts)
    }.joinToString("\n\n").limitDocumentChars()
    val warning = when {
        bodyText.isBlank() && imageOcrParts.isEmpty() -> "文档已读取，但没有提取到可用文字。"
        skippedImageCount > 0 -> "文档图片较多，已先 OCR 前 $MaxOfficeImageOcrCount 张。"
        else -> null
    }
    return DocumentExtractionResult(
        displayName = displayName,
        text = mergedText,
        textPartCount = bodyParts.size,
        imageOcrCount = imageOcrParts.size,
        renderedPageCount = 0,
        warning = warning,
    )
}

private fun Context.extractPlainText(
    uri: Uri,
    displayName: String,
): DocumentExtractionResult {
    val text = contentResolver.openInputStream(uri)
        ?.use { input -> input.readBytes(MaxPlainTextBytes).toString(Charsets.UTF_8) }
        .orEmpty()
        .normalizeExtractedWhitespace()
        .limitDocumentChars()
    return DocumentExtractionResult(
        displayName = displayName,
        text = text,
        textPartCount = if (text.isBlank()) 0 else 1,
        imageOcrCount = 0,
        renderedPageCount = 0,
        warning = if (text.isBlank()) "文本文件为空或无法读取。" else null,
    )
}

private fun PdfRenderer.Page.renderToBitmap(): Bitmap {
    val longEdge = maxOf(width, height).coerceAtLeast(1)
    val scale = (PdfRenderLongEdgePx / longEdge.toFloat()).coerceIn(1f, 2.4f)
    val bitmap = Bitmap.createBitmap(
        (width * scale).roundToInt().coerceAtLeast(1),
        (height * scale).roundToInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    bitmap.eraseColor(Color.WHITE)
    render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    return bitmap
}

private fun ZipInputStream.extractTextNodesFromXml(): String {
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
    parser.setInput(this, "UTF-8")
    val pieces = mutableListOf<String>()
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        if (event == XmlPullParser.START_TAG && parser.name == "t") {
            val text = parser.nextText().trim()
            if (text.isNotBlank()) {
                pieces += text
            }
        }
        event = parser.next()
    }
    return pieces.joinToString(" ").normalizeExtractedWhitespace()
}

private fun java.io.InputStream.readBytes(
    maxBytes: Int,
): ByteArray {
    val buffer = ByteArray(maxBytes)
    var total = 0
    while (total < maxBytes) {
        val read = read(buffer, total, maxBytes - total)
        if (read <= 0) break
        total += read
    }
    return buffer.copyOf(total)
}

private fun File.createTempImageFile(
    prefix: String,
    extension: String,
): File {
    mkdirs()
    val safeExtension = extension.lowercase(Locale.US)
        .substringBefore('?')
        .ifBlank { "jpg" }
    return File(this, "${prefix.safeFileStem()}_${System.nanoTime()}.$safeExtension")
}

private fun SelectedLocalAsset.detectDocumentKind(): DocumentKind {
    val name = displayName.lowercase(Locale.US)
    val mime = mimeTypeLabel.lowercase(Locale.US)
    return when {
        mime.startsWith("text/") || name.endsWith(".txt") || name.endsWith(".md") -> DocumentKind.PLAIN_TEXT
        mime.contains("pdf") || name.endsWith(".pdf") -> DocumentKind.PDF
        mime.contains("presentationml") || name.endsWith(".pptx") -> DocumentKind.PPTX
        mime.contains("powerpoint") || name.endsWith(".ppt") -> DocumentKind.LEGACY_PPT
        mime.contains("wordprocessingml") || name.endsWith(".docx") -> DocumentKind.DOCX
        mime == "application/msword" || name.endsWith(".doc") -> DocumentKind.LEGACY_WORD
        else -> DocumentKind.UNKNOWN
    }
}

private fun DocumentKind.isTextXmlEntry(
    entryName: String,
): Boolean {
    return when (this) {
        DocumentKind.DOCX -> entryName == "word/document.xml" ||
            Regex("""word/(header|footer)\d*\.xml""").matches(entryName)
        DocumentKind.PPTX -> Regex("""ppt/slides/slide\d+\.xml""").matches(entryName) ||
            Regex("""ppt/notesSlides/notesSlide\d+\.xml""").matches(entryName)
        else -> false
    }
}

private fun DocumentKind.isMediaImageEntry(
    entryName: String,
): Boolean {
    val prefixMatches = when (this) {
        DocumentKind.DOCX -> entryName.startsWith("word/media/")
        DocumentKind.PPTX -> entryName.startsWith("ppt/media/")
        else -> false
    }
    return prefixMatches && entryName.substringAfterLast('.', "").lowercase(Locale.US) in ImageFileExtensions
}

private fun String.normalizeExtractedWhitespace(): String {
    return lineSequence()
        .map { line -> line.replace(Regex("""\s+"""), " ").trim() }
        .filter { it.isNotBlank() }
        .joinToString("\n")
}

private fun String.limitDocumentChars(): String {
    return if (length <= MaxDocumentTextChars) {
        this
    } else {
        take(MaxDocumentTextChars) + "\n\n[内容较长，已截取前 $MaxDocumentTextChars 字。]"
    }
}

private fun String.safeFileStem(): String {
    return substringBeforeLast('.')
        .replace(Regex("""[^A-Za-z0-9._-]+"""), "_")
        .take(48)
        .ifBlank { "document" }
}

private enum class DocumentKind {
    PDF,
    DOCX,
    PPTX,
    LEGACY_WORD,
    LEGACY_PPT,
    PLAIN_TEXT,
    UNKNOWN,
}

private val ImageFileExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp")
private const val MaxPdfOcrPages = 8
private const val MaxOfficeImageOcrCount = 8
private const val MaxDocumentTextChars = 24_000
private const val MaxPlainTextBytes = 512 * 1024
private const val PdfRenderLongEdgePx = 1_900
