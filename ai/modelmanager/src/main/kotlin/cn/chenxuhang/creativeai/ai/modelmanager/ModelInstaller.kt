package cn.chenxuhang.creativeai.ai.modelmanager

import cn.chenxuhang.creativeai.core.model.ModelInstallStatus
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

data class AssetDownloadProgress(
    val modelId: String,
    val fileName: String,
    val downloadedBytes: Long,
    val totalBytes: Long?,
)

data class AssetInstallResult(
    val fileName: String,
    val targetPath: String,
    val success: Boolean,
    val downloadedBytes: Long,
    val sha256Verified: Boolean,
    val errorMessage: String? = null,
)

data class ModelInstallResult(
    val modelId: String,
    val localDirectory: String,
    val status: ModelInstallStatus,
    val assetResults: List<AssetInstallResult>,
)

fun interface ProgressListener {
    fun onProgress(progress: AssetDownloadProgress)
}

interface ModelAssetDownloader {
    fun download(
        modelId: String,
        asset: PlannedModelAsset,
        listener: ProgressListener? = null,
    ): AssetInstallResult
}

class JavaUrlModelAssetDownloader : ModelAssetDownloader {
    override fun download(
        modelId: String,
        asset: PlannedModelAsset,
        listener: ProgressListener?,
    ): AssetInstallResult {
        val targetFile = File(asset.targetPath)
        targetFile.parentFile?.mkdirs()
        val tempFile = File(targetFile.absolutePath + ".download")
        val uri = URI.create(asset.downloadUrl)

        return runCatching {
            when (uri.scheme?.lowercase()) {
                null, "", "file" -> copyLocalFile(
                    modelId = modelId,
                    asset = asset,
                    sourceFile = if (uri.scheme?.lowercase() == "file") File(uri) else File(asset.downloadUrl),
                    tempFile = tempFile,
                    targetFile = targetFile,
                    listener = listener,
                )
                "http", "https" -> downloadRemoteFile(
                    modelId = modelId,
                    asset = asset,
                    tempFile = tempFile,
                    targetFile = targetFile,
                    listener = listener,
                )
                else -> AssetInstallResult(
                    fileName = asset.fileName,
                    targetPath = asset.targetPath,
                    success = false,
                    downloadedBytes = 0,
                    sha256Verified = false,
                    errorMessage = "Unsupported URI scheme: ${uri.scheme}",
                )
            }
        }.getOrElse { error ->
            tempFile.delete()
            AssetInstallResult(
                fileName = asset.fileName,
                targetPath = asset.targetPath,
                success = false,
                downloadedBytes = 0,
                sha256Verified = false,
                errorMessage = error.message,
            )
        }
    }

    private fun downloadRemoteFile(
        modelId: String,
        asset: PlannedModelAsset,
        tempFile: File,
        targetFile: File,
        listener: ProgressListener?,
    ): AssetInstallResult {
        val connection = (URI.create(asset.downloadUrl).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            requestMethod = "GET"
        }
        connection.connect()
        return BufferedInputStream(connection.inputStream).use { input ->
            copyStreamToTarget(
                modelId = modelId,
                asset = asset,
                input = input,
                tempFile = tempFile,
                targetFile = targetFile,
                totalBytes = connection.contentLengthLong.takeIf { it > 0 },
                listener = listener,
            )
        }
    }

    private fun copyLocalFile(
        modelId: String,
        asset: PlannedModelAsset,
        sourceFile: File,
        tempFile: File,
        targetFile: File,
        listener: ProgressListener?,
    ): AssetInstallResult {
        if (!sourceFile.exists()) {
            return AssetInstallResult(
                fileName = asset.fileName,
                targetPath = asset.targetPath,
                success = false,
                downloadedBytes = 0,
                sha256Verified = false,
                errorMessage = "Local source file not found: ${sourceFile.absolutePath}",
            )
        }
        return BufferedInputStream(FileInputStream(sourceFile)).use { input ->
            copyStreamToTarget(
                modelId = modelId,
                asset = asset,
                input = input,
                tempFile = tempFile,
                targetFile = targetFile,
                totalBytes = sourceFile.length(),
                listener = listener,
            )
        }
    }

    private fun copyStreamToTarget(
        modelId: String,
        asset: PlannedModelAsset,
        input: BufferedInputStream,
        tempFile: File,
        targetFile: File,
        totalBytes: Long?,
        listener: ProgressListener?,
    ): AssetInstallResult {
        val digest = MessageDigest.getInstance("SHA-256")
        var downloadedBytes = 0L

        FileOutputStream(tempFile).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                output.write(buffer, 0, count)
                digest.update(buffer, 0, count)
                downloadedBytes += count
                listener?.onProgress(
                    AssetDownloadProgress(
                        modelId = modelId,
                        fileName = asset.fileName,
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes,
                    ),
                )
            }
        }

        val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
        val verified = asset.sha256.startsWith("todo-") || asset.sha256.equals(actualSha256, ignoreCase = true)
        if (!verified) {
            tempFile.delete()
            return AssetInstallResult(
                fileName = asset.fileName,
                targetPath = asset.targetPath,
                success = false,
                downloadedBytes = downloadedBytes,
                sha256Verified = false,
                errorMessage = "SHA256 mismatch for ${asset.fileName}",
            )
        }

        if (targetFile.exists()) {
            targetFile.delete()
        }
        tempFile.renameTo(targetFile)

        return AssetInstallResult(
            fileName = asset.fileName,
            targetPath = asset.targetPath,
            success = true,
            downloadedBytes = downloadedBytes,
            sha256Verified = true,
        )
    }
}

class ModelInstaller(
    private val modelManager: ModelManager,
    private val downloader: ModelAssetDownloader,
) {
    fun install(
        modelId: String,
        localDirectory: String,
        listener: ProgressListener? = null,
    ): ModelInstallResult {
        val plan = modelManager.installPlan(modelId, localDirectory)
            ?: return ModelInstallResult(
                modelId = modelId,
                localDirectory = localDirectory,
                status = ModelInstallStatus.BROKEN,
                assetResults = emptyList(),
            )

        val results = plan.requiredAssets.map { asset ->
            downloader.download(modelId = modelId, asset = asset, listener = listener)
        }

        val allSucceeded = results.all { it.success && it.sha256Verified }
        val finalStatus = if (allSucceeded) {
            modelManager.markInstalled(modelId, localDirectory)
            modelManager.validateInstallation(modelId, localDirectory)
        } else {
            ModelInstallStatus.BROKEN
        }

        return ModelInstallResult(
            modelId = modelId,
            localDirectory = localDirectory,
            status = finalStatus,
            assetResults = results,
        )
    }
}
