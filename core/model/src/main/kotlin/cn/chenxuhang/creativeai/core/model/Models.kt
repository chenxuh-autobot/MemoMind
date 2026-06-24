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
    DOCUMENT_TEXT,
    AUDIO_TRANSCRIPT,
    SUPPLEMENTAL_TEXT,
}

data class SourceInputSection(
    val channel: SourceInputChannel,
    val label: String,
    val content: String,
)

data class MemoRemoteAgentTaskRef(
    val taskId: String,
    val targetAgent: String,
    val status: String,
    val summary: String = "",
    val updatedAt: String? = null,
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
    val remoteAgentTasks: List<MemoRemoteAgentTaskRef> = emptyList(),
    val remoteAgentTaskId: String? = null,
    val remoteAgentTaskStatus: String? = null,
    val remoteAgentTarget: String? = null,
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

enum class AgentTaskMode {
    PLAN_ONLY,
    READ_ONLY,
    WORKSPACE_WRITE,
}

enum class AgentTaskStatus {
    PENDING,
    RUNNING,
    WAITING_APPROVAL,
    DONE,
    FAILED,
    CANCELLED,
}

data class AgentTaskContext(
    val meetingSummary: String = "",
    val requirements: List<String> = emptyList(),
    val constraints: List<String> = emptyList(),
    val memoSummary: String = "",
    val memoBackground: String = "",
    val facts: List<String> = emptyList(),
    val decisions: List<String> = emptyList(),
    val actionItems: List<ActionItem> = emptyList(),
    val risks: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val sourceOutline: List<String> = emptyList(),
)

data class AgentTaskPermission(
    val requireUserApproval: Boolean = true,
    val approvedForExecution: Boolean = false,
    val allowCodeWrite: Boolean = false,
    val allowShellCommand: Boolean = false,
    val allowGitCommit: Boolean = false,
    val allowGitPush: Boolean = false,
    val allowFileDelete: Boolean = false,
    val allowNetworkAccess: Boolean = false,
)

data class AgentTaskProgressEvent(
    val phase: String = "",
    val message: String = "",
    val createdAt: String? = null,
    val level: String = "info",
)

data class AgentTaskResult(
    val summary: String = "",
    val planMarkdown: String = "",
    val filesToTouch: List<String> = emptyList(),
    val risks: List<String> = emptyList(),
    val testSuggestions: List<String> = emptyList(),
    val rawStdout: String = "",
    val rawStderr: String = "",
    val exitCode: Int? = null,
    val currentPhase: String = "",
    val progressEvents: List<AgentTaskProgressEvent> = emptyList(),
)

data class AgentTaskError(
    val type: String = "",
    val message: String = "",
    val detail: String = "",
)

data class AgentTask(
    val id: String = "",
    val userId: String,
    val sourceApp: String,
    val sourceTaskId: String? = null,
    val targetAgent: String,
    val projectId: String,
    val taskType: String,
    val mode: AgentTaskMode = AgentTaskMode.PLAN_ONLY,
    val goal: String,
    val prompt: String,
    val context: AgentTaskContext = AgentTaskContext(),
    val permission: AgentTaskPermission = AgentTaskPermission(),
    val status: AgentTaskStatus = AgentTaskStatus.PENDING,
    val result: AgentTaskResult? = null,
    val error: AgentTaskError? = null,
    val claimedBy: String? = null,
    val claimedAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
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
    val maxPromptChars: Int = 6_000,
    val chunkSoftLimitChars: Int = 2_400,
    val generationMaxNewTokens: Int = 448,
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
