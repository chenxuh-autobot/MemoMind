package cn.chenxuhang.creativeai

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BundledModelBootstrapResult(
    val success: Boolean,
    val copiedFiles: List<String>,
    val targetDirectory: String,
    val message: String,
)

suspend fun Context.ensureBundledModelInstalled(
    assetDirectory: String,
    targetDirectory: File,
    requiredFiles: List<String>,
    bundleVersion: String,
): BundledModelBootstrapResult = withContext(Dispatchers.IO) {
    targetDirectory.mkdirs()
    val availableAssets = assets.list(assetDirectory)?.toSet().orEmpty()
    val missingBundledAssets = requiredFiles.filterNot(availableAssets::contains)
    val readyMarker = File(targetDirectory, ".bundled-model-ready")
    if (missingBundledAssets.isNotEmpty()) {
        return@withContext BundledModelBootstrapResult(
            success = false,
            copiedFiles = emptyList(),
            targetDirectory = targetDirectory.absolutePath,
            message = "Bundled model assets missing: ${missingBundledAssets.joinToString()}",
        )
    }

    val readyMarkerVersion = readyMarker.takeIf(File::exists)?.readText()?.trim().orEmpty()
    val alreadyReady = readyMarkerVersion == bundleVersion && requiredFiles.all { fileName ->
        val targetFile = File(targetDirectory, fileName)
        targetFile.exists() && targetFile.length() > 0L
    }
    if (alreadyReady) {
        return@withContext BundledModelBootstrapResult(
            success = true,
            copiedFiles = emptyList(),
            targetDirectory = targetDirectory.absolutePath,
            message = "Bundled model already available.",
        )
    }

    val copiedFiles = mutableListOf<String>()
    requiredFiles.forEach { fileName ->
        val targetFile = File(targetDirectory, fileName)
        targetFile.parentFile?.mkdirs()
        assets.open("$assetDirectory/$fileName").use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output, bufferSize = 1024 * 128)
            }
        }
        copiedFiles += fileName
    }

    val unresolvedFiles = requiredFiles.filterNot { fileName ->
        val targetFile = File(targetDirectory, fileName)
        targetFile.exists() && targetFile.length() > 0L
    }
    if (unresolvedFiles.isNotEmpty()) {
        return@withContext BundledModelBootstrapResult(
            success = false,
            copiedFiles = copiedFiles,
            targetDirectory = targetDirectory.absolutePath,
            message = "Bundled model install incomplete: ${unresolvedFiles.joinToString()}",
        )
    }
    readyMarker.writeText("$bundleVersion\n")

    BundledModelBootstrapResult(
        success = true,
        copiedFiles = copiedFiles,
        targetDirectory = targetDirectory.absolutePath,
        message = if (copiedFiles.isEmpty()) {
            "Bundled model already available."
        } else {
            "Bundled model installed: ${copiedFiles.joinToString()}."
        },
    )
}
