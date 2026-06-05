package cn.chenxuhang.creativeai

import cn.chenxuhang.creativeai.core.filesystem.AppStorageDirectories
import java.io.File

data class OwnedOnDeviceAsrRuntimeStatus(
    val engineLabel: String,
    val modelId: String,
    val modelDirectory: String,
    val requiredFiles: List<String>,
    val isModelPresent: Boolean,
    val isRuntimeIntegrated: Boolean,
    val isReady: Boolean,
    val modeLabel: String,
    val statusMessage: String,
)

class OwnedOnDeviceAsrEngine(
    private val storage: AppStorageDirectories,
) {
    private val modelId = "asr-sensevoice-small"
    private val requiredFiles = listOf(
        "model.onnx",
        "tokens.txt",
    )

    fun inspectRuntime(): OwnedOnDeviceAsrRuntimeStatus {
        val modelDirectory = storage.modelDir(modelId)
        val isModelPresent = requiredFiles.all { name ->
            File(modelDirectory, name).exists()
        }
        val isRuntimeIntegrated = false
        val isReady = isModelPresent && isRuntimeIntegrated
        val statusMessage = when {
            isReady -> "自有端侧 ASR 已就绪，可直接处理录音。"
            isModelPresent -> "端侧 ASR 模型文件已在位，但当前工程仍缺少 Sherpa-ONNX runtime 接入。"
            else -> "端侧 ASR 方案已切换为自有引擎，但当前还没有把 ASR 模型打进工程。"
        }
        val modeLabel = when {
            isReady -> "自有端侧ASR（Sherpa-ONNX）"
            isModelPresent -> "自有端侧ASR（模型已在位，待接 runtime）"
            else -> "自有端侧ASR（待接模型）"
        }
        return OwnedOnDeviceAsrRuntimeStatus(
            engineLabel = "Sherpa-ONNX OfflineRecognizer",
            modelId = modelId,
            modelDirectory = modelDirectory.absolutePath,
            requiredFiles = requiredFiles,
            isModelPresent = isModelPresent,
            isRuntimeIntegrated = isRuntimeIntegrated,
            isReady = isReady,
            modeLabel = modeLabel,
            statusMessage = statusMessage,
        )
    }
}
