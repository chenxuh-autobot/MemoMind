package cn.chenxuhang.creativeai.core.model

enum class AttachmentKind {
    IMAGE,
    AUDIO,
    TEXT,
}

enum class ProcessingMode {
    LOCAL_ONLY,
    LOCAL_PREFERRED,
    CLOUD_ASSISTED,
}

enum class SourceInputChannel {
    IMAGE_BRIEF,
    OCR_TEXT,
    AUDIO_TRANSCRIPT,
    SUPPLEMENTAL_TEXT,
}

data class SourceInputSection(
    val channel: SourceInputChannel,
    val label: String,
    val content: String,
)

data class MemoTask(
    val id: String,
    val title: String,
    val type: String,
    val status: String,
    val summary: String = "",
    val processingMode: ProcessingMode = ProcessingMode.LOCAL_ONLY,
    val sourceText: String = "",
    val sourceSections: List<SourceInputSection> = emptyList(),
    val sourceChannels: List<String> = emptyList(),
    val assetRefs: List<String> = emptyList(),
    val isArchived: Boolean = false,
    val archiveFolder: String? = null,
)

data class Attachment(
    val id: String,
    val taskId: String,
    val kind: AttachmentKind,
    val localUri: String,
    val durationMs: Long? = null,
    val mimeType: String,
    val sortOrder: Int,
    val preprocessText: String? = null,
)

data class TopicSummary(
    val name: String,
    val summary: String,
)

data class ActionItem(
    val task: String,
    val owner: String,
    val deadline: String? = null,
)

data class StructuredMemo(
    val taskId: String,
    val oneLineSummary: String,
    val background: String,
    val topics: List<TopicSummary>,
    val facts: List<String>,
    val decisions: List<String>,
    val actionItems: List<ActionItem>,
    val risks: List<String>,
    val quotes: List<String>,
    val tags: List<String>,
    val rawJson: String,
    val sourceTrace: List<String> = emptyList(),
    val sourceOutline: List<String> = emptyList(),
    val assetRefs: List<String> = emptyList(),
)

data class DeviceProfile(
    val id: String,
    val ramClassGb: Int,
    val abi: String,
    val socHint: String,
    val supportsNnapi: Boolean,
    val supportsGpuPath: Boolean,
)

enum class DeviceTier {
    S,
    A,
    B,
}

enum class ModelAssetKind {
    TOKENIZER,
    MODEL_GRAPH,
    MODEL_WEIGHT,
    RUNTIME_CONFIG,
    MODEL_CONFIG,
    EMBEDDING,
}

data class ModelAsset(
    val fileName: String,
    val downloadUrl: String,
    val sha256: String,
    val kind: ModelAssetKind,
    val sizeBytes: Long,
)

data class ModelManifest(
    val modelId: String,
    val displayName: String,
    val recommendedMinRamGb: Int,
    val supportsVision: Boolean,
    val supportsAudio: Boolean,
    val recommendedMaxInput: Int,
    val recommendedDeviceTier: DeviceTier,
    val estimatedStorageBytes: Long,
    val assets: List<ModelAsset>,
    val promptTemplateVersion: String,
)

enum class ModelInstallStatus {
    NOT_INSTALLED,
    INSTALLED,
    BROKEN,
}

data class InstalledModel(
    val manifest: ModelManifest,
    val installStatus: ModelInstallStatus,
    val localDirectory: String? = null,
)

data class DeviceCapabilityReport(
    val deviceTier: DeviceTier,
    val canRunTextModel: Boolean,
    val canRunMultimodalModel: Boolean,
    val notes: List<String>,
)

enum class InferenceBackend {
    CPU,
    NNAPI,
    OPENCL,
}

data class MnnSessionConfig(
    val modelId: String,
    val modelDirectory: String,
    val backend: InferenceBackend,
    val threadCount: Int,
    val enableLowMemoryMode: Boolean,
    val enableMultimodalPath: Boolean,
    val cpuSmeCoreCount: Int = 2,
    val cpuSme2NeonDivisionRatio: Int = 41,
)

data class ModelProbeResult(
    val modelId: String? = null,
    val modelDirectory: String,
    val exists: Boolean,
    val hasTokenizer: Boolean,
    val hasWeights: Boolean,
    val hasConfig: Boolean,
    val missingFiles: List<String>,
)

data class SessionOpenResult(
    val success: Boolean,
    val runtimeVersion: String,
    val backendName: String,
    val sessionId: String? = null,
    val errorMessage: String? = null,
)

data class TextGenerationResult(
    val success: Boolean,
    val runtimeVersion: String,
    val backendName: String,
    val outputText: String? = null,
    val errorMessage: String? = null,
)

data class CpuAccelerationProbeResult(
    val isArm64: Boolean,
    val hasSme: Boolean,
    val hasSme2: Boolean,
    val detectionSource: String,
    val rawHints: String? = null,
)
