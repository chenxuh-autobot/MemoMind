package cn.chenxuhang.creativeai

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.AllInbox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import cn.chenxuhang.creativeai.ai.mnn.NativeBackedMnnRuntime
import cn.chenxuhang.creativeai.ai.modelmanager.InMemoryModelManager
import cn.chenxuhang.creativeai.ai.modelmanager.ModelCatalogOverrides
import cn.chenxuhang.creativeai.ai.orchestrator.LocalFirstMemoOrchestrator
import cn.chenxuhang.creativeai.ai.orchestrator.StructuredMemoTaskExecutionResult
import cn.chenxuhang.creativeai.ai.orchestrator.StructuredMemoTaskExecutor
import cn.chenxuhang.creativeai.ai.orchestrator.StructuredMemoTaskRequest
import cn.chenxuhang.creativeai.feature.capture.CaptureRoute
import cn.chenxuhang.creativeai.feature.capture.CaptureAssetItem
import cn.chenxuhang.creativeai.feature.capture.CaptureUiState
import cn.chenxuhang.creativeai.core.database.JsonFileMemoTaskLocalDataSource
import cn.chenxuhang.creativeai.core.database.JsonFileStructuredMemoLocalDataSource
import cn.chenxuhang.creativeai.core.filesystem.AppStorageDirectories
import cn.chenxuhang.creativeai.core.model.AgentTask
import cn.chenxuhang.creativeai.core.model.AgentTaskContext
import cn.chenxuhang.creativeai.core.model.AgentTaskMode
import cn.chenxuhang.creativeai.core.model.AgentTaskPermission
import cn.chenxuhang.creativeai.core.model.AgentTaskProgressEvent
import cn.chenxuhang.creativeai.core.model.AgentTaskStatus
import cn.chenxuhang.creativeai.core.model.ActionItem
import cn.chenxuhang.creativeai.core.model.DeviceProfile
import cn.chenxuhang.creativeai.core.model.InferenceBackend
import cn.chenxuhang.creativeai.core.model.MemoTask
import cn.chenxuhang.creativeai.core.model.MemoRemoteAgentTaskRef
import cn.chenxuhang.creativeai.core.model.MnnSessionConfig
import cn.chenxuhang.creativeai.core.model.ModelInstallStatus
import cn.chenxuhang.creativeai.core.model.ProcessingMode
import cn.chenxuhang.creativeai.core.model.SourceInputChannel
import cn.chenxuhang.creativeai.core.model.SourceInputSection
import cn.chenxuhang.creativeai.core.model.StructuredMemo
import cn.chenxuhang.creativeai.core.model.TopicSummary
import cn.chenxuhang.creativeai.core.network.AgentTaskRemoteConfig
import cn.chenxuhang.creativeai.core.network.AgentTaskRemoteDataSource
import cn.chenxuhang.creativeai.core.network.DisabledAgentTaskRemoteDataSource
import cn.chenxuhang.creativeai.core.network.SupabaseAgentTaskRemoteDataSource
import cn.chenxuhang.creativeai.feature.history.HistoryRoute
import cn.chenxuhang.creativeai.feature.history.HistoryArchiveGroup
import cn.chenxuhang.creativeai.feature.history.HistoryTaskItem
import cn.chenxuhang.creativeai.feature.history.HistoryUiState
import cn.chenxuhang.creativeai.feature.home.HomeRoute
import cn.chenxuhang.creativeai.feature.home.HomeUiState
import cn.chenxuhang.creativeai.feature.home.ModelInstallItem
import cn.chenxuhang.creativeai.feature.home.SystemComponentItem
import cn.chenxuhang.creativeai.feature.result.ResultAssetItem
import cn.chenxuhang.creativeai.feature.result.AgentActionItem
import cn.chenxuhang.creativeai.feature.result.ResultBridgeStatusUiState
import cn.chenxuhang.creativeai.feature.result.ResultRemoteTaskActionItem
import cn.chenxuhang.creativeai.feature.result.ResultRemoteTaskOptionItem
import cn.chenxuhang.creativeai.feature.result.ResultRoute
import cn.chenxuhang.creativeai.feature.result.ResultRemoteTaskUiState
import cn.chenxuhang.creativeai.feature.result.ResultSectionItem
import cn.chenxuhang.creativeai.feature.result.ResultUiState
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Share
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

private enum class AppScreen(
    val label: String,
) {
    CAPTURE("任务"),
    HISTORY("历史"),
    RESULT("结果"),
    HOME("设置"),
}

data class SelectedLocalAsset(
    val uri: String,
    val displayName: String,
    val mimeTypeLabel: String,
)

private data class BundledModelSpec(
    val modelId: String,
    val assetDirectory: String,
    val targetDirectory: File,
    val requiredFiles: List<String>,
    val bundleVersion: String,
    val bundledInApk: Boolean = true,
)

private val VisionEnhancedModelIds = setOf(
    "qwen-vl-2b-instruct-mnn",
)

private const val VisionModelId = "qwen-vl-2b-instruct-mnn"
private const val MemoMindAgentProjectId = "memomind_android"
private const val MemoMindSourceAppId = "memomind_android"
private const val MemoMindAgentUserIdPrefKey = "memomind_agent_user_id"
private val BridgeCapableAgentIds = setOf("codex", "trae", "work_buddy")

@Composable
fun CreativeAiApp() {
    val context = LocalContext.current
    val storage = remember(context) { AppStorageDirectories(context) }
    val appPrefs = remember(context) {
        context.getSharedPreferences("memomind_settings", Context.MODE_PRIVATE)
    }
    val memomindAgentUserId = remember(appPrefs) {
        appPrefs.getString(MemoMindAgentUserIdPrefKey, null)
            ?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().also { generated ->
                appPrefs.edit().putString(MemoMindAgentUserIdPrefKey, generated).apply()
            }
    }
    val taskLocalStore = remember(storage.taskIndexFile) {
        JsonFileMemoTaskLocalDataSource(storage.taskIndexFile)
    }
    val memoLocalStore = remember(storage.memoIndexFile) {
        JsonFileStructuredMemoLocalDataSource(storage.memoIndexFile)
    }
    val agentTaskRemoteDataSource: AgentTaskRemoteDataSource = remember {
        val config = AgentTaskRemoteConfig(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            anonKey = BuildConfig.SUPABASE_ANON_KEY,
            memomindUserId = memomindAgentUserId,
            accessToken = BuildConfig.SUPABASE_ACCESS_TOKEN.takeIf { it.isNotBlank() },
            debugServiceRoleKey = BuildConfig.SUPABASE_SERVICE_ROLE_KEY.takeIf {
                BuildConfig.DEBUG && it.isNotBlank()
            },
        )
        if (config.isConfigured) {
            SupabaseAgentTaskRemoteDataSource(config)
        } else {
            DisabledAgentTaskRemoteDataSource()
        }
    }
    val modelDirectories = remember(storage) {
        mapOf(
            VisionModelId to storage.modelDir(VisionModelId),
        )
    }
    val modelOverrides = remember {
        ModelCatalogOverrides.Empty
    }
    val modelManager = remember(modelOverrides) {
        InMemoryModelManager.bootstrapDefaults(overrides = modelOverrides)
    }
    val orchestrator = remember { LocalFirstMemoOrchestrator(modelManager) }
    val mnnRuntime = remember { NativeBackedMnnRuntime() }
    val bundledMnnBuildProfile = remember(context) { context.loadBundledMnnBuildProfile() }
    val cpuAccelerationProbe = remember(mnnRuntime) { mnnRuntime.cpuAccelerationProbe() }
    val taskExecutor = remember {
        StructuredMemoTaskExecutor(
            runtime = mnnRuntime,
            taskLocalDataSource = taskLocalStore,
            memoLocalDataSource = memoLocalStore,
        )
    }
    val deviceProfile = remember {
        DeviceProfile(
            id = "bootstrap-device",
            ramClassGb = 8,
            abi = "arm64-v8a",
            socHint = "unknown",
            supportsNnapi = true,
            supportsGpuPath = false,
        )
    }
    val plan = remember { orchestrator.plan(deviceProfile, wantsVision = true, wantsAudio = true) }
    val capabilityReport = plan.deviceCapabilityReport
    val bundledModelSpecs = remember(storage, modelDirectories) {
        listOf(
            BundledModelSpec(
                modelId = VisionModelId,
                assetDirectory = "models/qwen-vl-2b-instruct-mnn",
                targetDirectory = modelDirectories.getValue(VisionModelId),
                requiredFiles = listOf(
                    "tokenizer.txt",
                    "llm.mnn",
                    "llm.mnn.weight",
                    "llm_config.json",
                    "config.json",
                    "visual.mnn",
                    "visual.mnn.weight",
                ),
                bundleVersion = "qwen-vl-2b-instruct-mnn-v2",
                bundledInApk = BuildConfig.BUNDLE_QWEN_VL_MODEL,
            ),
        )
    }
    var bundledModelResults by remember { mutableStateOf<Map<String, BundledModelBootstrapResult>>(emptyMap()) }
    var modelBootstrapEpoch by remember { mutableStateOf(0) }
    var selectedModelId by remember {
        mutableStateOf(
            appPrefs.getString("selected_model_id", VisionModelId)
                ?.takeIf { it in modelDirectories.keys }
                ?: VisionModelId,
        )
    }
    val smeCoreCount = bundledMnnBuildProfile?.cpuSmeCoreNum ?: 2
    val smeDivisionRatio = bundledMnnBuildProfile?.cpuSme2NeonDivisionRatio ?: 41
    val currentModelDirectory = remember(modelDirectories, selectedModelId) {
        modelDirectories.getValue(selectedModelId)
    }
    val currentModelSpec = remember(modelManager, selectedModelId) {
        modelManager.catalog().first { it.modelId == selectedModelId }
    }
    val currentModelUsesVisionPath = remember(currentModelSpec) {
        currentModelSpec.supportsVision
    }
    val installPlan = remember(modelManager, currentModelDirectory, selectedModelId, modelBootstrapEpoch) {
        modelManager.installPlan(selectedModelId, currentModelDirectory.absolutePath)
    }
    val installStatus = remember(modelManager, currentModelDirectory, selectedModelId, modelBootstrapEpoch) {
        modelManager.validateInstallation(selectedModelId, currentModelDirectory.absolutePath)
    }
    val modelInstallStatuses = remember(modelManager, modelDirectories, modelBootstrapEpoch) {
        modelDirectories.mapValues { (modelId, directory) ->
            modelManager.validateInstallation(modelId, directory.absolutePath)
        }
    }
    val isSelectedModelReady = installStatus == ModelInstallStatus.INSTALLED
    val modelProbe = remember(mnnRuntime, currentModelDirectory, selectedModelId, modelBootstrapEpoch, isSelectedModelReady, currentModelSpec.assets) {
        if (isSelectedModelReady) {
            mnnRuntime.probeModelDirectory(
                modelId = selectedModelId,
                modelDirectory = currentModelDirectory.absolutePath,
            )
        } else {
            cn.chenxuhang.creativeai.core.model.ModelProbeResult(
                modelId = selectedModelId,
                modelDirectory = currentModelDirectory.absolutePath,
                exists = currentModelDirectory.exists(),
                hasTokenizer = false,
                hasWeights = false,
                hasConfig = false,
                missingFiles = currentModelSpec.assets.map { it.fileName },
            )
        }
    }
    val sessionConfigsByModelId = remember(modelDirectories, modelManager, smeCoreCount, smeDivisionRatio) {
        modelManager.catalog()
            .filter { it.modelId in modelDirectories.keys }
            .associate { manifest ->
                manifest.modelId to MnnSessionConfig(
                    modelId = manifest.modelId,
                    modelDirectory = modelDirectories.getValue(manifest.modelId).absolutePath,
                    backend = InferenceBackend.CPU,
                    threadCount = 4,
                    enableLowMemoryMode = true,
                    enableMultimodalPath = manifest.supportsVision,
                    cpuSmeCoreCount = smeCoreCount,
                    cpuSme2NeonDivisionRatio = smeDivisionRatio,
                    maxPromptChars = 5_600,
                    chunkSoftLimitChars = 2_200,
                    generationMaxNewTokens = 416,
                )
            }
    }
    val generationConfig = remember(sessionConfigsByModelId, selectedModelId) {
        sessionConfigsByModelId.getValue(selectedModelId)
    }

    var lastExecution by remember {
        mutableStateOf(
            StructuredMemoTaskExecutionResult(
                task = MemoTask(
                    id = "idle",
                    title = "未执行",
                    type = "structured_memo",
                    status = "IDLE",
                ),
            ),
        )
    }
    var taskDataEpoch by remember { mutableStateOf(0) }
    var draftTitle by remember { mutableStateOf("") }
    var draftImageBrief by remember { mutableStateOf("") }
    var draftOcrText by remember { mutableStateOf("") }
    var draftDocumentText by remember { mutableStateOf("") }
    var draftTranscript by remember { mutableStateOf("") }
    var draftNotes by remember { mutableStateOf("") }
    val maxImageCount = remember { 8 }
    var selectedImageAssets by remember { mutableStateOf<List<SelectedLocalAsset>>(emptyList()) }
    var selectedAudioAsset by remember { mutableStateOf<SelectedLocalAsset?>(null) }
    var pendingCameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var isRunningImageSummary by remember { mutableStateOf(false) }
    var isRunningImageOcr by remember { mutableStateOf(false) }
    var isReadingDocuments by remember { mutableStateOf(false) }
    var isPreparingAudioTranscription by remember { mutableStateOf(false) }
    var isRunningAudioTranscription by remember { mutableStateOf(false) }
    var audioTranscriptionModeLabel by remember { mutableStateOf("端侧离线 ASR") }
    var playingAudioAssetUri by remember { mutableStateOf<String?>(null) }
    var retranscribingAudioAssetUri by remember { mutableStateOf<String?>(null) }
    var croppingImageSourceUri by remember { mutableStateOf<String?>(null) }
    var hiddenResultAssetUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var activeRemoteAgentTaskId by remember { mutableStateOf<String?>(null) }
    var activeRemoteAgentTask by remember { mutableStateOf<AgentTask?>(null) }
    var recentRemoteAgentTasks by remember { mutableStateOf<List<AgentTask>>(emptyList()) }
    var focusedResultTaskId by remember { mutableStateOf<String?>(null) }
    var isSubmittingAgentTask by remember { mutableStateOf(false) }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var isSubmittingDraft by remember { mutableStateOf(false) }
    var draftStatusMessage by remember { mutableStateOf<String?>(null) }
    var currentScreen by remember { mutableStateOf(AppScreen.CAPTURE) }
    val scope = rememberCoroutineScope()
    val hasBundledSpeechRecognition = remember(context) {
        SpeechRecognitionAvailability.isAnyRecognitionAvailable(context)
    }
    val audioPreviewPlayer = remember(context) {
        AudioPreviewPlayer(
            context = context,
            onStateChanged = { playingAudioAssetUri = it },
            onStatusMessage = { message -> draftStatusMessage = message },
        )
    }
    val speechTranscriber = remember(context) {
        DeviceSpeechTranscriber(
            context = context,
            recordingsDirectory = storage.recordingsDir,
            onTranscript = { update ->
                draftTranscript = update.text
            },
            onStateChanged = { state ->
                isPreparingAudioTranscription = false
                isRunningAudioTranscription = state.isRunning
                audioTranscriptionModeLabel = state.modeLabel
            },
            onStatusMessage = { message ->
                draftStatusMessage = message
            },
            onAudioCaptured = { asset ->
                if (asset != null) {
                    selectedAudioAsset = asset
                }
            },
        )
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            val resolved = uris.map {
                context.persistReadPermission(it)
                context.resolveSelectedLocalAsset(it)
            }
            selectedImageAssets = appendSelectedImages(
                existing = selectedImageAssets,
                incoming = resolved,
                maxCount = maxImageCount,
            )
            draftStatusMessage = buildString {
                append("已选择 ${selectedImageAssets.size} 个素材。")
                if (selectedImageAssets.size >= maxImageCount) {
                    append(" 当前已达到上限 $maxImageCount 个。")
                }
            }
        }
    }
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        selectedAudioAsset = uri?.let {
            context.persistReadPermission(it)
            context.resolveSelectedLocalAsset(it)
        }
        if (uri != null) {
            draftStatusMessage = "录音文件已选择，可继续补充转写文本。"
        }
    }
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val targetUri = pendingCameraImageUri
        if (success && targetUri != null) {
            selectedImageAssets = appendSelectedImages(
                existing = selectedImageAssets,
                incoming = listOf(context.resolveSelectedLocalAsset(targetUri)),
                maxCount = maxImageCount,
            )
            draftStatusMessage = buildString {
                append("现场拍摄图片已加入素材。当前共 ${selectedImageAssets.size} 张。")
                if (selectedImageAssets.size >= maxImageCount) {
                    append(" 已达到上限 $maxImageCount 张。")
                }
            }
        } else {
            draftStatusMessage = "已取消拍摄图片。"
        }
        pendingCameraImageUri = null
    }
    val cropImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val sourceUri = croppingImageSourceUri
        croppingImageSourceUri = null
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val outputUri = result.data?.let(UCrop::getOutput)
                if (outputUri != null && sourceUri != null) {
                    selectedImageAssets = replaceSelectedImageAsset(
                        existing = selectedImageAssets,
                        sourceUri = sourceUri,
                        replacement = context.resolveSelectedLocalAsset(outputUri),
                    )
                    draftStatusMessage = "图片裁剪已完成，新的区域已经替换原图片。"
                } else {
                    draftStatusMessage = "图片裁剪完成，但没有拿到新的图片结果。"
                }
            }
            Activity.RESULT_CANCELED -> {
                draftStatusMessage = "已取消图片裁剪。"
            }
            else -> {
                val error = result.data?.let(UCrop::getError)
                draftStatusMessage = error?.message ?: "图片裁剪失败。"
            }
        }
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            isPreparingAudioTranscription = true
            draftStatusMessage = "正在唤起麦克风和端侧识别，请稍候..."
            scope.launch(Dispatchers.Default) {
                runCatching {
                    speechTranscriber.startListening()
                }.onFailure { error ->
                    withContext(Dispatchers.Main) {
                        isPreparingAudioTranscription = false
                        draftStatusMessage = error.message ?: "无法启动麦克风转写。"
                    }
                }
            }
        } else {
            draftStatusMessage = "未授予录音权限，无法启动麦克风转写。"
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val captureUri = pendingCameraImageUri
            if (captureUri != null) {
                takePictureLauncher.launch(captureUri)
            }
        } else {
            pendingCameraImageUri = null
            draftStatusMessage = "未授予相机权限，无法拍摄图片。"
        }
    }
    val clearCaptureDraft = remember {
        {
            draftTitle = ""
            draftImageBrief = ""
            draftOcrText = ""
            draftDocumentText = ""
            draftTranscript = ""
            draftNotes = ""
            selectedImageAssets = emptyList()
            selectedAudioAsset = null
            pendingCameraImageUri = null
            croppingImageSourceUri = null
            hiddenResultAssetUris = emptySet()
        }
    }

    DisposableEffect(speechTranscriber, audioPreviewPlayer) {
        onDispose {
            speechTranscriber.destroy()
            audioPreviewPlayer.stop()
        }
    }

    LaunchedEffect(bundledModelSpecs) {
        bundledModelResults = bundledModelSpecs.associate { spec ->
            spec.modelId to BundledModelBootstrapResult(
                success = false,
                copiedFiles = emptyList(),
                targetDirectory = spec.targetDirectory.absolutePath,
                message = if (spec.bundledInApk) {
                    "首次启动正在准备 ${spec.modelId}，请稍候..."
                } else {
                    "${spec.modelId} 未随安装包内置，需后续单独下载或导入。"
                },
            )
        }
        val results = mutableMapOf<String, BundledModelBootstrapResult>()
        bundledModelSpecs.forEach { spec ->
            val result = if (spec.bundledInApk) {
                context.ensureBundledModelInstalled(
                    assetDirectory = spec.assetDirectory,
                    targetDirectory = spec.targetDirectory,
                    requiredFiles = spec.requiredFiles,
                    bundleVersion = spec.bundleVersion,
                )
            } else {
                BundledModelBootstrapResult(
                    success = false,
                    copiedFiles = emptyList(),
                    targetDirectory = spec.targetDirectory.absolutePath,
                    message = "${spec.modelId} 未随安装包内置，需后续单独下载或导入。",
                )
            }
            results[spec.modelId] = result
            bundledModelResults = results.toMap()
            if (result.success) {
                modelManager.markInstalled(
                    modelId = spec.modelId,
                    localDirectory = spec.targetDirectory.absolutePath,
                )
            }
        }
        modelBootstrapEpoch += 1
    }

    LaunchedEffect(modelInstallStatuses, bundledModelResults, selectedModelId) {
        val selectedReady = modelInstallStatuses[selectedModelId] == ModelInstallStatus.INSTALLED
        if (selectedReady) return@LaunchedEffect
        val fallbackModelId = listOf(VisionModelId)
            .firstOrNull { modelInstallStatuses[it] == ModelInstallStatus.INSTALLED }
            ?: return@LaunchedEffect
        if (fallbackModelId != selectedModelId) {
            selectedModelId = fallbackModelId
            appPrefs.edit().putString("selected_model_id", fallbackModelId).apply()
        }
    }

    LaunchedEffect(generationConfig.modelDirectory, selectedModelId, modelBootstrapEpoch) {
        val persistedTasks = taskLocalStore.getAll()
        val persistedMemos = memoLocalStore.getAll()
        val hasPersistedResults = persistedTasks.isNotEmpty() || persistedMemos.isNotEmpty()

        lastExecution = if (hasPersistedResults) {
            StructuredMemoTaskExecutionResult(
                task = persistedTasks.lastOrNull() ?: MemoTask(
                    id = "persisted",
                    title = "已加载本地任务",
                    type = "structured_memo",
                    status = "COMPLETED",
                ),
                memo = persistedMemos.lastOrNull(),
                rawOutput = persistedMemos.lastOrNull()?.rawJson,
            )
        } else {
            StructuredMemoTaskExecutionResult(
                task = MemoTask(
                    id = "idle",
                    title = "未执行",
                    type = "structured_memo",
                    status = "IDLE",
                ),
            )
        }
    }

    val savedTasks = remember(taskLocalStore, lastExecution, taskDataEpoch) {
        taskLocalStore.getAll().asReversed()
    }
    val savedMemos = remember(memoLocalStore, lastExecution, taskDataEpoch) {
        memoLocalStore.getAll().asReversed()
    }
    val latestMemo = savedMemos.firstOrNull()

    LaunchedEffect(savedTasks, focusedResultTaskId) {
        if (savedTasks.isEmpty()) return@LaunchedEffect
        val hasFocusedTask = focusedResultTaskId != null && savedTasks.any { it.id == focusedResultTaskId }
        if (!hasFocusedTask) {
            focusedResultTaskId = savedTasks.firstOrNull()?.id
        }
    }

    LaunchedEffect(savedTasks, activeRemoteAgentTaskId) {
        if (activeRemoteAgentTaskId != null) return@LaunchedEffect
        val resumableRemoteTaskId = savedTasks.firstNotNullOfOrNull(::findResumableRemoteTaskId)
        if (!resumableRemoteTaskId.isNullOrBlank()) {
            activeRemoteAgentTaskId = resumableRemoteTaskId
        }
    }

    LaunchedEffect(activeRemoteAgentTaskId, agentTaskRemoteDataSource) {
        val taskId = activeRemoteAgentTaskId ?: return@LaunchedEffect
        if (!agentTaskRemoteDataSource.isConfigured()) return@LaunchedEffect
        while (true) {
            val fetchedTask = withContext(Dispatchers.IO) {
                agentTaskRemoteDataSource.fetchById(taskId).getOrNull()
            }
            if (fetchedTask != null) {
                activeRemoteAgentTask = fetchedTask
                val relatedTaskId = fetchedTask.sourceTaskId
                if (!relatedTaskId.isNullOrBlank()) {
                    val localTask = taskLocalStore.getAll().firstOrNull { it.id == relatedTaskId }
                    if (localTask != null) {
                        taskLocalStore.save(localTask.withUpsertedRemoteTask(fetchedTask))
                        taskDataEpoch += 1
                    }
                }
                if (fetchedTask.status in setOf(AgentTaskStatus.DONE, AgentTaskStatus.FAILED, AgentTaskStatus.CANCELLED)) {
                    break
                }
            }
            delay(5_000)
        }
    }

    LaunchedEffect(agentTaskRemoteDataSource, memomindAgentUserId) {
        if (!agentTaskRemoteDataSource.isConfigured()) return@LaunchedEffect
        while (true) {
            val fetchedTasks = withContext(Dispatchers.IO) {
                agentTaskRemoteDataSource.fetchRecentByUser(limit = 8).getOrNull().orEmpty()
            }
            if (fetchedTasks.isNotEmpty()) {
                recentRemoteAgentTasks = fetchedTasks
                var changed = false
                fetchedTasks.forEach { fetchedTask ->
                    val relatedTaskId = fetchedTask.sourceTaskId ?: return@forEach
                    val localTask = taskLocalStore.getAll().firstOrNull { it.id == relatedTaskId } ?: return@forEach
                    val mergedTask = localTask.withUpsertedRemoteTask(fetchedTask)
                    if (mergedTask != localTask) {
                        taskLocalStore.save(mergedTask)
                        changed = true
                    }
                }
                if (changed) {
                    taskDataEpoch += 1
                }
            }
            delay(20_000)
        }
    }

    LaunchedEffect(focusedResultTaskId, agentTaskRemoteDataSource) {
        val focusedTaskId = focusedResultTaskId ?: return@LaunchedEffect
        if (!agentTaskRemoteDataSource.isConfigured()) return@LaunchedEffect
        while (true) {
            var mergedFocusedTask = taskLocalStore.getAll().firstOrNull { it.id == focusedTaskId } ?: break
            val remoteTaskRefs = mergedFocusedTask.remoteAgentTasks.ifEmpty {
                listOfNotNull(mergedFocusedTask.toLegacyRemoteAgentTaskRef())
            }
            remoteTaskRefs.forEach { remoteTaskRef ->
                val fetchedTask = withContext(Dispatchers.IO) {
                    agentTaskRemoteDataSource.fetchById(remoteTaskRef.taskId).getOrNull()
                } ?: return@forEach
                mergedFocusedTask = mergedFocusedTask.withUpsertedRemoteTask(fetchedTask)
                taskLocalStore.save(mergedFocusedTask)
                if (activeRemoteAgentTaskId == fetchedTask.id) {
                    activeRemoteAgentTask = fetchedTask
                }
                taskDataEpoch += 1
            }
            delay(15_000)
        }
    }

    val homeUiState = remember(
        storage,
        modelManager,
        plan,
        mnnRuntime,
        capabilityReport,
        bundledMnnBuildProfile,
        cpuAccelerationProbe,
        installPlan,
        installStatus,
        modelProbe,
        selectedModelId,
        bundledModelResults,
        currentModelDirectory,
    ) {
        HomeUiState(
            headline = "设置",
            subheadline = "看看 MemoMind 的端侧链路是不是都站稳了，模型、推理、加速和存储状态一眼就能看明白。",
            components = listOf(
                SystemComponentItem(
                    title = "Qwen 端侧模型包",
                    detail = if (modelProbe.exists && modelProbe.hasTokenizer && modelProbe.hasWeights && modelProbe.hasConfig) {
                        "模型目录已准备完成，tokenizer、权重和配置文件都可用。"
                    } else {
                        "模型目录还没补齐，缺失项：${modelProbe.missingFiles.joinToString().ifBlank { "待检查" }}。"
                    },
                    statusLabel = if (modelProbe.exists && modelProbe.hasTokenizer && modelProbe.hasWeights && modelProbe.hasConfig) "已就绪" else "待补齐",
                    isReady = modelProbe.exists && modelProbe.hasTokenizer && modelProbe.hasWeights && modelProbe.hasConfig,
                    icon = Icons.Outlined.AllInbox,
                ),
                SystemComponentItem(
                    title = "MNN 运行时",
                    detail = "真实 MNN=${mnnRuntime.supportsRealMnn()}，桥接版本 ${mnnRuntime.runtimeVersion().substringBefore(',')}，端侧 CPU 推理链已经接通。",
                    statusLabel = if (mnnRuntime.supportsRealMnn()) "已链接" else "stub",
                    isReady = mnnRuntime.supportsRealMnn(),
                    icon = Icons.Outlined.Memory,
                ),
                SystemComponentItem(
                    title = "SME2 构建",
                    detail = "ABI ${bundledMnnBuildProfile?.androidAbi ?: "unknown"}，SME2=${bundledMnnBuildProfile?.mnnSme2Enabled ?: false}，KleidiAI=${bundledMnnBuildProfile?.mnnKleidiAiEnabled ?: false}。",
                    statusLabel = if (bundledMnnBuildProfile?.mnnSme2Enabled == true) "已编入" else "未确认",
                    isReady = bundledMnnBuildProfile?.mnnSme2Enabled == true,
                    icon = Icons.Outlined.Tune,
                ),
                SystemComponentItem(
                    title = "SME2 硬件探测",
                    detail = "arm64=${cpuAccelerationProbe.isArm64}，SME=${cpuAccelerationProbe.hasSme}，SME2=${cpuAccelerationProbe.hasSme2}，来源 ${cpuAccelerationProbe.detectionSource}。",
                    statusLabel = if (cpuAccelerationProbe.hasSme2) "已命中" else "未命中",
                    isReady = cpuAccelerationProbe.hasSme2,
                    icon = Icons.Outlined.DeveloperBoard,
                ),
                SystemComponentItem(
                    title = "纪要生成策略",
                    detail = when {
                        isSelectedModelReady -> {
                            "当前模型 ${selectedModelId} 已准备完成，真正生成时才会按需加载本地会话；文本任务也会优先路由到更轻的文本模型。"
                        }
                        selectedModelId == VisionModelId && BuildConfig.BUNDLE_QWEN_VL_MODEL -> {
                            bundledModelResults[selectedModelId]?.message ?: "首次启动正在准备本地模型，请稍候..."
                        }
                        else -> {
                            "当前模型尚未完整就绪；提交任务时会优先走轻量纪要整理，避免为了追求大模型效果先把内存吃满。"
                        }
                    },
                    statusLabel = when {
                        isSelectedModelReady -> "按需加载"
                        selectedModelId == VisionModelId && BuildConfig.BUNDLE_QWEN_VL_MODEL -> "准备中"
                        else -> "轻量模式"
                    },
                    isReady = isSelectedModelReady || !BuildConfig.BUNDLE_QWEN_VL_MODEL,
                    icon = Icons.Outlined.CheckCircle,
                ),
                SystemComponentItem(
                    title = "本地存储",
                    detail = "模型、纪要和录音目录都可读写，任务索引与纪要索引已经接通。",
                    statusLabel = "可读写",
                    isReady = storage.modelsDir.exists() && storage.memosDir.exists() && storage.recordingsDir.exists(),
                    icon = Icons.Outlined.SdStorage,
                ),
            ),
            installedModels = modelManager.installedModels().map {
                val actualDirectory = modelDirectories[it.manifest.modelId]
                val actualStatus = actualDirectory?.let { directory ->
                    modelManager.validateInstallation(it.manifest.modelId, directory.absolutePath)
                } ?: it.installStatus
                val tags = buildList {
                    if (it.manifest.modelId == VisionModelId) {
                        add("图片理解更强")
                        add("多图语境更稳")
                        add("适合图文混合纪要")
                        add(if (BuildConfig.BUNDLE_QWEN_VL_MODEL) "已随包内置" else "默认不随包分发")
                    }
                    add("RAM ${it.manifest.recommendedMinRamGb}GB+")
                    add("最大输入约 ${it.manifest.recommendedMaxInput} tokens")
                }
                ModelInstallItem(
                    modelId = it.manifest.modelId,
                    title = it.manifest.displayName,
                    status = actualStatus.name,
                    detail = "${it.manifest.modelId} | ${it.manifest.estimatedStorageBytes / 1_000_000}MB | ${actualDirectory?.absolutePath ?: "pending"}",
                    tags = tags,
                    isReady = actualStatus.name == "INSTALLED",
                    isSelected = selectedModelId == it.manifest.modelId,
                )
            },
            projectSourceUrl = "https://github.com/chenxuh-autobot/MemoMind",
            feedbackIdeas = listOf(
                "推荐优先做一份飞书表单，现场扫码就能提交，适合比赛答辩时快速收反馈。",
                "建议题目包含：使用的是哪个模型、图片/OCR/语音哪一步最满意、纪要质量是否达标。",
                "最后保留一个开放题，让用户直接写下最想优化的体验点和新功能建议。",
            ),
        )
    }

    val memoByTaskId = remember(savedMemos) {
        savedMemos.associateBy { it.taskId }
    }
    val focusedTask: MemoTask? = remember(savedTasks, lastExecution, focusedResultTaskId) {
        savedTasks.firstOrNull { it.id == focusedResultTaskId }
            ?: savedTasks.firstOrNull()
            ?: lastExecution.task.takeIf { it.id != "idle" }
    }
    val displayedMemo: StructuredMemo? = remember(memoByTaskId, focusedTask, latestMemo) {
        focusedTask?.let { memoByTaskId[it.id] } ?: latestMemo
    }
    val selectedRemoteTaskId = remember(activeRemoteAgentTaskId, focusedTask) {
        activeRemoteAgentTaskId ?: focusedTask?.let(::preferredRemoteTaskId)
    }
    val displayedRemoteTaskOptions = remember(focusedTask, selectedRemoteTaskId) {
        buildRemoteTaskOptions(
            task = focusedTask,
            selectedRemoteTaskId = selectedRemoteTaskId,
        )
    }
    val displayedRemoteTask: AgentTask? = remember(activeRemoteAgentTask, focusedTask, selectedRemoteTaskId) {
        activeRemoteAgentTask?.takeIf { remoteTask ->
            remoteTask.id == selectedRemoteTaskId &&
                (focusedTask == null || remoteTask.sourceTaskId.isNullOrBlank() || remoteTask.sourceTaskId == focusedTask.id)
        }
    }
    val archiveFolders = remember(savedTasks, taskDataEpoch) {
        loadArchiveFolders(appPrefs, savedTasks)
    }
    val historyUiState = remember(savedTasks, memoByTaskId, archiveFolders) {
        val activeTasks = savedTasks.filterNot { it.isArchived }
        val archivedTasksByFolder = savedTasks
            .filter { it.isArchived && !it.archiveFolder.isNullOrBlank() }
            .groupBy { it.archiveFolder.orEmpty() }
        val archiveGroups = archiveFolders
            .sortedByDescending { folderName ->
                archivedTasksByFolder[folderName]
                    .orEmpty()
                    .maxOfOrNull { task ->
                        savedTasks.indexOfFirst { it.id == task.id }
                    } ?: Int.MIN_VALUE
            }
            .map { folderName ->
            val tasks = archivedTasksByFolder[folderName].orEmpty()
            HistoryArchiveGroup(
                folderName = folderName,
                tasks = tasks.map { task ->
                    val relatedMemo = memoByTaskId[task.id]
                    HistoryTaskItem(
                        taskId = task.id,
                        title = task.title,
                        status = task.status,
                        summary = task.summary.ifBlank { "暂无摘要" },
                        detail = buildHistoryTaskDetail(task),
                        canRecall = task.sourceSections.isNotEmpty() || task.sourceText.isNotBlank() || !relatedMemo?.sourceOutline.isNullOrEmpty(),
                        canOpenResult = relatedMemo != null || task.remoteAgentTasks.isNotEmpty() || !task.remoteAgentTaskId.isNullOrBlank(),
                        isArchived = true,
                    )
                },
            )
        }
        HistoryUiState(
            headline = "任务历史",
            subheadline = "",
            activeTasks = activeTasks.map { task ->
                val relatedMemo = memoByTaskId[task.id]
                HistoryTaskItem(
                    taskId = task.id,
                    title = task.title,
                    status = task.status,
                    summary = task.summary.ifBlank { "暂无摘要" },
                    detail = buildHistoryTaskDetail(task),
                    canRecall = task.sourceSections.isNotEmpty() || task.sourceText.isNotBlank() || !relatedMemo?.sourceOutline.isNullOrEmpty(),
                    canOpenResult = relatedMemo != null || task.remoteAgentTasks.isNotEmpty() || !task.remoteAgentTaskId.isNullOrBlank(),
                    isArchived = false,
                )
            },
            archiveGroups = archiveGroups,
        )
    }

    val captureStatusMessage = remember(draftStatusMessage, bundledModelResults, selectedModelId, isSelectedModelReady) {
        if (!isSelectedModelReady) {
            if (selectedModelId == VisionModelId && BuildConfig.BUNDLE_QWEN_VL_MODEL) {
                bundledModelResults[selectedModelId]?.message ?: "首次启动正在准备本地模型，请稍候..."
            } else {
                draftStatusMessage
                    ?: "当前会优先走轻量链路；接入文本小模型后，纯文本任务会自动切到更省内存的本地模型。"
            }
        } else {
            draftStatusMessage
        }
    }

    val captureUiState = remember(
        draftTitle,
        draftImageBrief,
        draftOcrText,
        draftDocumentText,
        draftTranscript,
        draftNotes,
        selectedImageAssets,
        selectedAudioAsset,
        isRunningImageSummary,
        isRunningImageOcr,
        isReadingDocuments,
        hasBundledSpeechRecognition,
        isPreparingAudioTranscription,
        isRunningAudioTranscription,
        audioTranscriptionModeLabel,
        isSelectedModelReady,
        isSubmittingDraft,
        captureStatusMessage,
    ) {
        val sourceSections = composeDraftSourceSections(
            imageBrief = draftImageBrief,
            ocrText = draftOcrText,
            documentText = draftDocumentText,
            transcriptText = draftTranscript,
            supplementalText = draftNotes,
        )
        CaptureUiState(
            headline = "纪要任务",
            subheadline = "把图片、语音和文字交给 MemoMind，它会帮你整理成清晰纪要。",
            titleInput = draftTitle,
            showTitleRequiredHint = draftTitle.isBlank() && sourceSections.isNotEmpty(),
            imageBriefInput = draftImageBrief,
            ocrTextInput = draftOcrText,
            documentTextInput = draftDocumentText,
            transcriptInput = draftTranscript,
            notesInput = draftNotes,
            maxImageCount = maxImageCount,
            selectedImageAssets = selectedImageAssets.map {
                CaptureAssetItem(
                    displayName = it.displayName,
                    uri = it.uri,
                )
            },
            selectedAudioAssetLabel = selectedAudioAsset?.displayName,
            composedPreview = composeUnifiedSourceText(sourceSections),
            activeSourceLabels = sourceSections.map { it.label },
            canRunImageOcr = selectedImageAssets.any { it.isImageAsset() },
            isRunningImageOcr = isRunningImageOcr,
            canRunImageSummary = selectedImageAssets.any { it.isImageAsset() },
            isRunningImageSummary = isRunningImageSummary,
            canReadDocuments = selectedImageAssets.any { it.isReadableDocumentAsset() },
            isReadingDocuments = isReadingDocuments,
            canToggleAudioTranscription = hasBundledSpeechRecognition && !isSubmittingDraft,
            isPreparingAudioTranscription = isPreparingAudioTranscription,
            isRunningAudioTranscription = isRunningAudioTranscription,
            audioTranscriptionModeLabel = if (hasBundledSpeechRecognition) {
                audioTranscriptionModeLabel
            } else {
                "当前 APK 未打包端侧 ASR 模型"
            },
            audioToggleLabel = "使用麦克风转写",
            isSubmitting = isSubmittingDraft,
            submitEnabled = draftTitle.isNotBlank() && sourceSections.isNotEmpty(),
            statusMessage = captureStatusMessage,
        )
    }

    val resultUiState = remember(
        displayedMemo,
        focusedTask,
        savedTasks,
        recentRemoteAgentTasks,
        lastExecution,
        playingAudioAssetUri,
        retranscribingAudioAssetUri,
        hiddenResultAssetUris,
        displayedRemoteTask,
        displayedRemoteTaskOptions,
        selectedRemoteTaskId,
        agentTaskRemoteDataSource,
    ) {
        ResultUiState(
            headline = "纪要结果",
            subheadline = "",
            summary = displayedMemo?.oneLineSummary ?: displayedRemoteTask?.result?.summary?.takeIf { it.isNotBlank() },
            sections = buildResultSections(
                memo = displayedMemo,
                remoteTask = displayedRemoteTask,
            ),
            bridgeStatus = buildBridgeStatusUiState(
                remoteTask = displayedRemoteTask,
                focusedTask = focusedTask,
                allTasks = savedTasks,
                recentRemoteTasks = recentRemoteAgentTasks,
                isBridgeConfigured = agentTaskRemoteDataSource.isConfigured(),
            ),
            remoteTaskOptions = displayedRemoteTaskOptions,
            remoteTask = displayedRemoteTask?.let(::buildRemoteTaskUiState),
            remoteTaskPlaceholder = selectedRemoteTaskId?.takeIf { displayedRemoteTask == null }?.let {
                "正在加载所选 Bridge 任务详情..."
            },
            assetItems = displayedMemo?.assetRefs
                ?.mapNotNull(::parseAssetRef)
                ?.filterNot { it.uri in hiddenResultAssetUris }
                ?.map { asset ->
                    ResultAssetItem(
                        kindLabel = asset.kindLabel,
                        displayName = asset.displayName,
                        detail = asset.mimeTypeLabel,
                        uri = asset.uri,
                        isPlayableAudio = asset.kindLabel == "AUDIO",
                        isPlaying = playingAudioAssetUri == asset.uri,
                        canRetranscribeAudio = asset.kindLabel == "AUDIO",
                        isRetranscribing = retranscribingAudioAssetUri == asset.uri,
                    )
                }
                .orEmpty(),
            agentActions = displayedMemo?.let {
                listOf(
                    AgentActionItem(
                        id = "codex",
                        label = if (agentTaskRemoteDataSource.isConfigured()) "提交给 Codex CLI Bridge" else "复制给 Codex CLI",
                        icon = Icons.Outlined.ContentCopy,
                    ),
                    AgentActionItem(
                        id = "share",
                        label = "系统分享",
                        icon = Icons.Outlined.Share,
                    ),
                    AgentActionItem(
                        id = "email",
                        label = "发到邮箱",
                        icon = Icons.Outlined.Email,
                    ),
                )
            }.orEmpty(),
            canEdit = displayedMemo != null,
        )
    }
    val hasResultScreenContent = remember(resultUiState) {
        !resultUiState.summary.isNullOrBlank() || resultUiState.sections.isNotEmpty()
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppScreen.entries.forEach { screen ->
                    val enabled = screen != AppScreen.RESULT || hasResultScreenContent
                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick = {
                            if (enabled) {
                                currentScreen = screen
                            }
                        },
                        enabled = enabled,
                        icon = {
                            Icon(
                                imageVector = screen.icon(),
                                contentDescription = screen.label,
                            )
                        },
                        label = { Text(screen.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (currentScreen) {
                AppScreen.HOME -> HomeRoute(
                    uiState = homeUiState,
                    onSelectModel = { modelId ->
                        if (selectedModelId != modelId) {
                            selectedModelId = modelId
                            appPrefs.edit().putString("selected_model_id", modelId).apply()
                            draftStatusMessage = "已切换到 ${modelManager.catalog().firstOrNull { it.modelId == modelId }?.displayName ?: modelId}。"
                        }
                    },
                    onOpenProjectSource = {
                        context.openAssetExternally(Uri.parse(homeUiState.projectSourceUrl))
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                AppScreen.CAPTURE -> CaptureRoute(
                    uiState = captureUiState,
                    onTitleChange = { draftTitle = it },
                    onImageBriefChange = { draftImageBrief = it },
                    onOcrTextChange = { draftOcrText = it },
                    onDocumentTextChange = { draftDocumentText = it },
                    onTranscriptChange = { draftTranscript = it },
                    onNotesChange = { draftNotes = it },
                    onPickImage = {
                        imagePickerLauncher.launch(
                            arrayOf(
                                "image/*",
                                "text/plain",
                                "application/pdf",
                                "application/msword",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/vnd.ms-powerpoint",
                                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                            ),
                        )
                    },
                    onCaptureImage = {
                        if (selectedImageAssets.size >= maxImageCount) {
                            draftStatusMessage = "素材最多只能保留 $maxImageCount 个，请先清除部分素材。"
                            return@CaptureRoute
                        }
                        val captureUri = context.createCapturedImageUri(storage)
                        pendingCameraImageUri = captureUri
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            takePictureLauncher.launch(captureUri)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onClearImage = {
                        selectedImageAssets = emptyList()
                        draftDocumentText = ""
                        draftStatusMessage = "已清除素材引用。"
                    },
                    onOpenImageAsset = { assetUri ->
                        val asset = selectedImageAssets.firstOrNull { it.uri == assetUri }
                        if (asset?.isImageAsset() == true) {
                            val sourceUri = Uri.parse(assetUri)
                            val destinationUri = context.createImageCropOutputUri(storage)
                            val options = UCrop.Options().apply {
                                setFreeStyleCropEnabled(true)
                                setHideBottomControls(false)
                                setToolbarTitle("裁剪图片")
                                setCompressionQuality(92)
                            }
                            croppingImageSourceUri = assetUri
                            cropImageLauncher.launch(
                                UCrop.of(sourceUri, destinationUri)
                                    .withOptions(options)
                                    .getIntent(context),
                            )
                        } else {
                            context.openAssetExternally(Uri.parse(assetUri))
                        }
                    },
                    onRunImageSummary = {
                        val imageUris = selectedImageAssets
                            .filter { it.isImageAsset() }
                            .map { Uri.parse(it.uri) }
                        if (imageUris.isEmpty()) return@CaptureRoute
                        if (isRunningImageSummary) return@CaptureRoute
                        isRunningImageSummary = true
                        draftStatusMessage = "正在把图片压缩成轻量文字要点..."
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    imageUris.mapIndexed { index, imageUri ->
                                        context.buildImageContextSummary(imageUri).text
                                            .takeIf { it.isNotBlank() }
                                            ?.let { "[第${index + 1}张图片要点]\n$it" }
                                            .orEmpty()
                                    }
                                }
                            }
                            isRunningImageSummary = false
                            result.onSuccess { summaries ->
                                val mergedText = summaries
                                    .filter { it.isNotBlank() }
                                    .joinToString("\n\n")
                                draftImageBrief = mergedText
                                draftStatusMessage = if (mergedText.isBlank()) {
                                    "图片预处理完成，但没有提炼出稳定要点。"
                                } else {
                                    "图片预处理完成，已把轻量文字要点回填到图片摘要。"
                                }
                            }.onFailure { error ->
                                draftStatusMessage = error.message ?: "图片预处理失败。"
                            }
                        }
                    },
                    onRunImageOcr = {
                        val imageUris = selectedImageAssets
                            .filter { it.isImageAsset() }
                            .map { Uri.parse(it.uri) }
                        if (imageUris.isEmpty()) return@CaptureRoute
                        if (isRunningImageOcr) return@CaptureRoute
                        isRunningImageOcr = true
                        draftStatusMessage = if (selectedModelId in VisionEnhancedModelIds && isSelectedModelReady) {
                            "正在执行图片识别，并结合视觉模型进行文字整理..."
                        } else {
                            "正在执行端侧图片 OCR..."
                        }
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    imageUris.mapIndexed { index, imageUri ->
                                        val ocr = context.runChineseImageOcr(imageUri)
                                        val visionText = if (selectedModelId in VisionEnhancedModelIds && isSelectedModelReady) {
                                            context.describeImageWithVisionModel(
                                                runtime = mnnRuntime,
                                                config = generationConfig,
                                                imageUri = imageUri,
                                            ).text
                                        } else {
                                            ""
                                        }
                                        val merged = chooseImageRecognitionText(
                                            ocrText = ocr.text,
                                            visionText = visionText,
                                        )
                                        OcrRecognitionResult(
                                            text = merged
                                                .takeIf { it.isNotBlank() }
                                                ?.let { "[第${index + 1}张图片识别文本]\n$it" }
                                                .orEmpty(),
                                            blockCount = ocr.blockCount,
                                        )
                                    }
                                }
                            }
                            isRunningImageOcr = false
                            result.onSuccess { ocrResults ->
                                val mergedText = ocrResults
                                    .map { it.text }
                                    .filter { it.isNotBlank() }
                                    .joinToString("\n\n")
                                draftOcrText = mergedText
                                val totalBlocks = ocrResults.sumOf { it.blockCount }
                                draftStatusMessage = if (mergedText.isBlank()) {
                                    "OCR 已完成，但没有识别到可用文字。"
                                } else {
                                    "图片识别已完成，识别到 $totalBlocks 个文本块，结果已回填到图片识别文本。"
                                }
                            }.onFailure { error ->
                                draftStatusMessage = error.message ?: "OCR 执行失败"
                            }
                        }
                    },
                    onReadDocuments = {
                        val documentAssets = selectedImageAssets.filter { it.isReadableDocumentAsset() }
                        if (documentAssets.isEmpty()) return@CaptureRoute
                        if (isReadingDocuments) return@CaptureRoute
                        isReadingDocuments = true
                        draftStatusMessage = "正在读取文档文字..."
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    documentAssets.map { asset ->
                                        context.extractDocumentTextFromAsset(
                                            asset = asset,
                                            cacheDirectory = storage.cacheDir,
                                        )
                                    }
                                }
                            }
                            isReadingDocuments = false
                            result.onSuccess { extractionResults ->
                                val mergedText = extractionResults
                                    .mapNotNull { extraction ->
                                        extraction.text.takeIf { it.isNotBlank() }?.let {
                                            "[文档：${extraction.displayName}]\n$it"
                                        }
                                    }
                                    .joinToString("\n\n")
                                draftDocumentText = mergedText
                                val warnings = extractionResults
                                    .mapNotNull { resultItem ->
                                        resultItem.warning?.let { "${resultItem.displayName}: $it" }
                                    }
                                val textParts = extractionResults.sumOf { it.textPartCount }
                                val imageOcrCount = extractionResults.sumOf { it.imageOcrCount }
                                val pdfPages = extractionResults.sumOf { it.renderedPageCount }
                                draftStatusMessage = buildString {
                                    if (mergedText.isBlank()) {
                                        append("文档读取完成，但没有提取到可用文字。")
                                    } else {
                                        append("文档读取完成：正文 $textParts 段")
                                        if (pdfPages > 0) append("，PDF OCR $pdfPages 页")
                                        if (imageOcrCount > 0) append("，内嵌图片 OCR $imageOcrCount 张")
                                        append("。")
                                    }
                                    if (warnings.isNotEmpty()) {
                                        append(" ")
                                        append(warnings.take(2).joinToString("；"))
                                    }
                                }
                            }.onFailure { error ->
                                draftStatusMessage = error.message ?: "文档读取失败。"
                            }
                        }
                    },
                    onPickAudio = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                    onClearAudio = {
                        val removedAudioUri = selectedAudioAsset?.uri
                        selectedAudioAsset = null
                        draftTranscript = ""
                        removedAudioUri?.let { uri ->
                            hiddenResultAssetUris = hiddenResultAssetUris + uri
                            if (playingAudioAssetUri == uri) {
                                audioPreviewPlayer.stop()
                            }
                        }
                        draftStatusMessage = "已清除录音文件引用和转写文本。"
                    },
                    onToggleAudioTranscription = {
                        if (isRunningAudioTranscription || isPreparingAudioTranscription) {
                            isPreparingAudioTranscription = false
                            isRunningAudioTranscription = false
                            speechTranscriber.stopListening()
                            draftStatusMessage = "正在停止录音并整理端侧转写结果..."
                        } else if (hasAudioPermission) {
                            isPreparingAudioTranscription = true
                            draftStatusMessage = "正在唤起麦克风和端侧识别，请稍候..."
                            scope.launch(Dispatchers.Default) {
                                runCatching {
                                    speechTranscriber.startListening()
                                }.onFailure { error ->
                                    withContext(Dispatchers.Main) {
                                        isPreparingAudioTranscription = false
                                        draftStatusMessage = error.message ?: "无法启动麦克风转写。"
                                    }
                                }
                            }
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onSubmit = {
                        val sourceSections = composeDraftSourceSections(
                            imageBrief = draftImageBrief,
                            ocrText = draftOcrText,
                            documentText = draftDocumentText,
                            transcriptText = draftTranscript,
                            supplementalText = draftNotes,
                        )
                        val assetRefs = buildList {
                            addAll(selectedImageAssets.map { it.toTaskAssetRef(it.taskAssetKindLabel()) })
                            selectedAudioAsset?.let { add(it.toTaskAssetRef("AUDIO")) }
                        }
                        if (isSubmittingDraft || draftTitle.isBlank() || sourceSections.isEmpty()) return@CaptureRoute
                        val executionRoute = ExecutionModelRoute(
                            sessionConfig = generationConfig,
                            isLocalModelReady = isSelectedModelReady,
                            statusMessage = if (isSelectedModelReady) {
                                "图片内容已先压成 OCR/要点文本，正在按需加载 qwen3-vl-2b 生成纪要..."
                            } else {
                                "本地模型未完整就绪，正在使用轻量纪要整理..."
                            },
                        )
                        draftStatusMessage = executionRoute.statusMessage
                        isSubmittingDraft = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                taskExecutor.execute(
                                    StructuredMemoTaskRequest(
                                        title = draftTitle,
                                        type = "multimodal_capture_memo",
                                        sourceText = composeUnifiedSourceText(sourceSections),
                                        sourceSections = sourceSections,
                                        assetRefs = assetRefs,
                                        processingMode = if (executionRoute.isLocalModelReady) {
                                            ProcessingMode.LOCAL_ONLY
                                        } else {
                                            ProcessingMode.LOCAL_PREFERRED
                                        },
                                        sessionConfig = executionRoute.sessionConfig,
                                    ),
                                )
                            }
                            lastExecution = result
                            focusedResultTaskId = result.task.id
                            isSubmittingDraft = false
                            val generatedMemo = result.memo
                            if (generatedMemo != null) {
                                val usedFallbackMemo = generatedMemo.sourceTrace.any { it.startsWith("generation-fallback:") }
                                draftStatusMessage = if (usedFallbackMemo) {
                                    "轻量纪要已生成并写入本地任务流；后续接入本地或云端模型后可再生成更高质量版本。"
                                } else {
                                    "纪要生成完成，已写入本地任务流。"
                                }
                                hiddenResultAssetUris = emptySet()
                                clearCaptureDraft()
                                currentScreen = AppScreen.RESULT
                            } else {
                                draftStatusMessage = result.errorMessage ?: "任务执行失败"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                AppScreen.HISTORY -> HistoryRoute(
                    uiState = historyUiState,
                    onRecallTask = { taskId ->
                        val task = savedTasks.firstOrNull { it.id == taskId } ?: return@HistoryRoute
                        val relatedMemo = memoByTaskId[task.id]
                        val restoredSections = restoreDraftSourceSections(
                            task = task,
                            memo = relatedMemo,
                        )
                        draftTitle = task.title
                        draftImageBrief = ""
                        draftOcrText = restoredSections.firstContent(SourceInputChannel.OCR_TEXT)
                        draftDocumentText = restoredSections.firstContent(SourceInputChannel.DOCUMENT_TEXT)
                        draftTranscript = restoredSections.firstContent(SourceInputChannel.AUDIO_TRANSCRIPT)
                        draftNotes = restoredSections.firstContent(SourceInputChannel.SUPPLEMENTAL_TEXT)
                        selectedImageAssets = task.assetRefs
                            .parsedAssets(kindLabels = setOf("IMAGE", "DOCUMENT", "TEXT"))
                            .map { it.toSelectedLocalAsset() }
                            .take(maxImageCount)
                        selectedAudioAsset = task.assetRefs
                            .firstParsedAsset(kindLabel = "AUDIO")
                            ?.toSelectedLocalAsset()
                        draftStatusMessage = "已回填历史输入，可继续编辑后重新生成。"
                        currentScreen = AppScreen.CAPTURE
                    },
                    onOpenTaskResult = { taskId ->
                        val task = savedTasks.firstOrNull { it.id == taskId } ?: return@HistoryRoute
                        focusedResultTaskId = task.id
                        activeRemoteAgentTask = null
                        val selectedRemoteTaskId = preferredRemoteTaskId(task)
                        activeRemoteAgentTaskId = selectedRemoteTaskId
                        draftStatusMessage = if (selectedRemoteTaskId.isNullOrBlank()) {
                            "已打开这条历史任务的结果。"
                        } else {
                            "已打开历史结果，并继续追踪这条纪要最近一次的 MemoMind Agent Bridge 任务。"
                        }
                        currentScreen = AppScreen.RESULT
                    },
                    onDeleteTask = { taskId ->
                        taskLocalStore.delete(taskId)
                        memoLocalStore.delete(taskId)
                        taskDataEpoch += 1
                        draftStatusMessage = "已删除这条历史任务。"
                    },
                    onRenameArchiveFolder = { oldName, newName ->
                        val trimmed = newName.trim()
                        if (trimmed.isBlank() || trimmed == oldName) return@HistoryRoute
                        savedTasks.filter { it.archiveFolder == oldName }.forEach { task ->
                            taskLocalStore.save(task.copy(archiveFolder = trimmed, isArchived = true))
                        }
                        replaceArchiveFolder(appPrefs, oldName, trimmed)
                        taskDataEpoch += 1
                    },
                    onArchiveTaskIntoFolder = { taskId, folderName ->
                        val task = savedTasks.firstOrNull { it.id == taskId } ?: return@HistoryRoute
                        taskLocalStore.save(
                            task.copy(
                                isArchived = true,
                                archiveFolder = folderName,
                            ),
                        )
                        taskDataEpoch += 1
                        draftStatusMessage = "已把任务放进 $folderName。"
                    },
                    onMoveArchivedTaskToFolder = { taskId, folderName ->
                        val task = savedTasks.firstOrNull { it.id == taskId } ?: return@HistoryRoute
                        taskLocalStore.save(
                            task.copy(
                                isArchived = true,
                                archiveFolder = folderName,
                            ),
                        )
                        taskDataEpoch += 1
                        draftStatusMessage = "已将任务移动到 $folderName。"
                    },
                    onUnarchiveTask = { taskId ->
                        val task = savedTasks.firstOrNull { it.id == taskId } ?: return@HistoryRoute
                        taskLocalStore.save(
                            task.copy(
                                isArchived = false,
                                archiveFolder = null,
                            ),
                        )
                        taskDataEpoch += 1
                        draftStatusMessage = "已将任务移回最近任务。"
                    },
                    onAddArchiveFolder = {
                        val newName = generateArchiveFolderName(archiveFolders)
                        saveArchiveFolders(appPrefs, archiveFolders + newName)
                        taskDataEpoch += 1
                        draftStatusMessage = "已新增档案：$newName。"
                    },
                    onDeleteArchiveFolder = { folderName ->
                        savedTasks.filter { it.archiveFolder == folderName }.forEach { task ->
                            taskLocalStore.save(task.copy(isArchived = false, archiveFolder = null))
                        }
                        saveArchiveFolders(appPrefs, archiveFolders - folderName)
                        taskDataEpoch += 1
                        draftStatusMessage = "已删除档案：$folderName。"
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                AppScreen.RESULT -> ResultRoute(
                    uiState = resultUiState,
                    onToggleAudioPlayback = { assetUri ->
                        audioPreviewPlayer.togglePlayback(assetUri)
                    },
                    onRetranscribeAudio = { assetUri ->
                        if (retranscribingAudioAssetUri == null) {
                            retranscribingAudioAssetUri = assetUri
                            audioPreviewPlayer.stop()
                            draftStatusMessage = "正在从已保存录音重跑转写..."
                            scope.launch {
                                val result = runCatching {
                                    context.retranscribeAudioFile(
                                        audioUri = Uri.parse(assetUri),
                                        cacheDirectory = storage.cacheDir,
                                    )
                                }
                                retranscribingAudioAssetUri = null
                                result.onSuccess { transcript ->
                                    val parsedAsset = parseAssetRef(
                                        displayedMemo?.assetRefs
                                            ?.firstOrNull { raw ->
                                                parseAssetRef(raw)?.uri == assetUri
                                            }
                                            .orEmpty(),
                                    )
                                    selectedAudioAsset = parsedAsset?.toSelectedLocalAsset()
                                        ?: SelectedLocalAsset(
                                            uri = assetUri,
                                            displayName = Uri.parse(assetUri).lastPathSegment ?: "saved_audio",
                                            mimeTypeLabel = "audio/*",
                                        )
                                    draftTranscript = transcript.text
                                    audioTranscriptionModeLabel = transcript.modeLabel
                                    draftStatusMessage = if (transcript.text.isBlank()) {
                                        "重跑转写完成，但没有识别到可用文本。你可以在 Capture 页手动补充。"
                                    } else {
                                        "重跑转写完成，结果已回填到 Capture 页。"
                                    }
                                    currentScreen = AppScreen.CAPTURE
                                }.onFailure { error ->
                                    draftStatusMessage = error.message ?: "音频文件重跑转写失败。"
                                }
                            }
                        }
                    },
                    onOpenAsset = { assetUri ->
                        context.openAssetExternally(Uri.parse(assetUri))
                    },
                    onEditResultSection = { sectionId, value ->
                        val memo = displayedMemo ?: return@ResultRoute
                        val updatedMemo = memo.withEditedResultSection(sectionId, value)
                        memoLocalStore.save(updatedMemo)
                        lastExecution = lastExecution.copy(
                            memo = updatedMemo,
                            rawOutput = updatedMemo.rawJson,
                        )
                        taskDataEpoch += 1
                        draftStatusMessage = "已保存修改。"
                    },
                    onRemoteTaskAction = { actionId ->
                        if (actionId.startsWith("select_remote_task:")) {
                            val selectedTaskId = actionId.substringAfter("select_remote_task:").takeIf { it.isNotBlank() }
                                ?: return@ResultRoute
                            activeRemoteAgentTask = activeRemoteAgentTask?.takeIf { it.id == selectedTaskId }
                            activeRemoteAgentTaskId = selectedTaskId
                            draftStatusMessage = "正在切换查看所选 Bridge 任务..."
                            return@ResultRoute
                        }
                        val remoteTask = displayedRemoteTask ?: return@ResultRoute
                        when (actionId) {
                            "refresh_remote_task" -> {
                                activeRemoteAgentTaskId = remoteTask.id
                                draftStatusMessage = "正在刷新 MemoMind Agent Bridge 任务状态..."
                            }
                            "copy_remote_task_id" -> {
                                context.copyTextToClipboard(
                                    label = "MemoMind Agent Task Id",
                                    text = remoteTask.id,
                                )
                                draftStatusMessage = "已复制 MemoMind Agent Bridge 任务 ID。"
                            }
                            "copy_remote_task_plan" -> {
                                val plan = remoteTask.result?.planMarkdown.orEmpty()
                                if (plan.isBlank()) return@ResultRoute
                                context.copyTextToClipboard(
                                    label = "MemoMind Agent Plan",
                                    text = plan,
                                )
                                draftStatusMessage = "已复制 Codex 执行计划。"
                            }
                            "copy_remote_task_error" -> {
                                val errorText = listOfNotNull(
                                    remoteTask.error?.message?.takeIf { it.isNotBlank() },
                                    remoteTask.error?.detail?.takeIf { it.isNotBlank() },
                                ).joinToString("\n\n")
                                if (errorText.isBlank()) return@ResultRoute
                                context.copyTextToClipboard(
                                    label = "MemoMind Agent Error",
                                    text = errorText,
                                )
                                draftStatusMessage = "已复制 Bridge 错误详情。"
                            }
                            "cancel_remote_task" -> {
                                if (!agentTaskRemoteDataSource.isConfigured()) {
                                    draftStatusMessage = "当前未配置 Supabase，无法取消远程任务。"
                                    return@ResultRoute
                                }
                                scope.launch {
                                    draftStatusMessage = "正在取消 MemoMind Agent Bridge 任务..."
                                    val cancelled = withContext(Dispatchers.IO) {
                                        agentTaskRemoteDataSource.cancel(remoteTask.id)
                                    }
                                    cancelled
                                        .onSuccess { updatedTask ->
                                            activeRemoteAgentTask = updatedTask
                                            activeRemoteAgentTaskId = updatedTask.id
                                            synchronizeLocalTaskWithRemote(
                                                remoteTask = updatedTask,
                                                taskLocalStore = taskLocalStore,
                                                onTaskDataChanged = { taskDataEpoch += 1 },
                                            )
                                            draftStatusMessage = "已把远程任务标记为 cancelled。"
                                        }
                                        .onFailure { error ->
                                            draftStatusMessage = error.message ?: "取消远程任务失败。"
                                        }
                                }
                            }
                            "approve_remote_task_workspace_write" -> {
                                if (!agentTaskRemoteDataSource.isConfigured()) {
                                    draftStatusMessage = "当前未配置 Supabase，无法批准远程任务执行。"
                                    return@ResultRoute
                                }
                                scope.launch {
                                    draftStatusMessage = "正在批准 MemoMind Agent Bridge 任务进入 workspace_write..."
                                    val approved = withContext(Dispatchers.IO) {
                                        agentTaskRemoteDataSource.approveForWorkspaceWrite(remoteTask.id)
                                    }
                                    approved
                                        .onSuccess { updatedTask ->
                                            activeRemoteAgentTaskId = updatedTask.id
                                            activeRemoteAgentTask = updatedTask
                                            synchronizeLocalTaskWithRemote(
                                                remoteTask = updatedTask,
                                                taskLocalStore = taskLocalStore,
                                                onTaskDataChanged = { taskDataEpoch += 1 },
                                            )
                                            draftStatusMessage = "已批准这条 Bridge 任务进入 workspace_write，并重新排队执行。"
                                        }
                                        .onFailure { error ->
                                            draftStatusMessage = error.message ?: "批准远程任务执行失败。"
                                        }
                                }
                            }
                            "retry_remote_task_safe" -> {
                                if (!agentTaskRemoteDataSource.isConfigured()) {
                                    draftStatusMessage = "当前未配置 Supabase，无法执行安全降级重试。"
                                    return@ResultRoute
                                }
                                scope.launch {
                                    draftStatusMessage = "正在按安全模式重新排队 MemoMind Agent Bridge 任务..."
                                    val requeued = withContext(Dispatchers.IO) {
                                        agentTaskRemoteDataSource.requeueInSafeMode(remoteTask.id)
                                    }
                                    requeued
                                        .onSuccess { updatedTask ->
                                            activeRemoteAgentTaskId = updatedTask.id
                                            activeRemoteAgentTask = updatedTask
                                            synchronizeLocalTaskWithRemote(
                                                remoteTask = updatedTask,
                                                taskLocalStore = taskLocalStore,
                                                onTaskDataChanged = { taskDataEpoch += 1 },
                                            )
                                            draftStatusMessage = "已按安全模式重新排队 Bridge 任务。"
                                        }
                                        .onFailure { error ->
                                            draftStatusMessage = error.message ?: "安全降级重试失败。"
                                        }
                                }
                            }
                        }
                    },
                    onRunAgentAction = { actionId ->
                        val memo = displayedMemo ?: return@ResultRoute
                        val executionForResult = StructuredMemoTaskExecutionResult(
                            task = focusedTask ?: lastExecution.task,
                            memo = memo,
                            rawOutput = memo.rawJson,
                        )
                        when (actionId) {
                            in BridgeCapableAgentIds -> {
                                val prompt = buildAgentHandoffPrompt(
                                    actionId = actionId,
                                    memo = memo,
                                    execution = executionForResult,
                                )
                                if (!agentTaskRemoteDataSource.isConfigured()) {
                                    context.copyTextToClipboard(
                                        label = "MemoMind Agent Handoff",
                                        text = prompt,
                                    )
                                    draftStatusMessage = "尚未配置 Supabase，已回退为复制给 ${resultUiState.agentActions.firstOrNull { it.id == actionId }?.label ?: actionId}。"
                                    return@ResultRoute
                                }
                                if (isSubmittingAgentTask) {
                                    draftStatusMessage = "MemoMind Agent Bridge 任务正在提交中，请稍候。"
                                    return@ResultRoute
                                }
                                scope.launch {
                                    isSubmittingAgentTask = true
                                    draftStatusMessage = "正在提交 MemoMind Agent Bridge 任务到 Supabase..."
                                    val task = buildBridgeTaskForAgent(
                                        targetAgent = actionId,
                                        memo = memo,
                                        execution = executionForResult,
                                        userId = memomindAgentUserId,
                                    )
                                    val submitted = withContext(Dispatchers.IO) {
                                        agentTaskRemoteDataSource.create(task)
                                    }
                                    submitted
                                        .onSuccess { createdTask ->
                                            activeRemoteAgentTaskId = createdTask.id
                                            activeRemoteAgentTask = createdTask
                                            focusedResultTaskId = memo.taskId
                                            val localTask = taskLocalStore.getAll().firstOrNull { it.id == memo.taskId }
                                            if (localTask != null) {
                                                taskLocalStore.save(localTask.withUpsertedRemoteTask(createdTask))
                                                taskDataEpoch += 1
                                            }
                                            currentScreen = AppScreen.RESULT
                                            draftStatusMessage = "已提交到 MemoMind Agent Bridge，电脑端 Bridge 可开始拉取并执行 ${createdTask.targetAgent} 任务。"
                                        }
                                        .onFailure { error ->
                                            draftStatusMessage = error.message ?: "MemoMind Agent Bridge 任务提交失败。"
                                        }
                                    isSubmittingAgentTask = false
                                }
                            }
                            "share" -> {
                                val prompt = buildAgentHandoffPrompt(
                                    actionId = actionId,
                                    memo = memo,
                                    execution = executionForResult,
                                )
                                context.sharePlainText(
                                    subject = "${memo.taskId} - MemoMind 纪要",
                                    text = prompt,
                                )
                                draftStatusMessage = "已打开系统分享，可继续发给电脑端协作工具。"
                            }
                            "email" -> {
                                val prompt = buildAgentHandoffPrompt(
                                    actionId = actionId,
                                    memo = memo,
                                    execution = executionForResult,
                                )
                                context.composeEmailDraft(
                                    subject = "MemoMind 纪要协作任务",
                                    body = prompt,
                                )
                                draftStatusMessage = "已生成邮件草稿。"
                            }
                            else -> {
                                val prompt = buildAgentHandoffPrompt(
                                    actionId = actionId,
                                    memo = memo,
                                    execution = executionForResult,
                                )
                                context.copyTextToClipboard(
                                    label = "MemoMind Agent Handoff",
                                    text = prompt,
                                )
                                draftStatusMessage = "已复制 ${resultUiState.agentActions.firstOrNull { it.id == actionId }?.label ?: "Agent 任务"}。"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private fun composeDraftSourceSections(
    imageBrief: String,
    ocrText: String,
    documentText: String,
    transcriptText: String,
    supplementalText: String,
): List<SourceInputSection> {
    return listOfNotNull(
        imageBrief.takeIf { it.isNotBlank() }?.let {
            SourceInputSection(
                channel = SourceInputChannel.IMAGE_BRIEF,
                label = "图片轻量要点",
                content = it.trim(),
            )
        },
        ocrText.takeIf { it.isNotBlank() }?.let {
            SourceInputSection(
                channel = SourceInputChannel.OCR_TEXT,
                label = "图片识别文本",
                content = it.trim(),
            )
        },
        documentText.takeIf { it.isNotBlank() }?.let {
            SourceInputSection(
                channel = SourceInputChannel.DOCUMENT_TEXT,
                label = "文档读取文本",
                content = it.trim(),
            )
        },
        transcriptText.takeIf { it.isNotBlank() }?.let {
            SourceInputSection(
                channel = SourceInputChannel.AUDIO_TRANSCRIPT,
                label = "录音转写文本",
                content = it.trim(),
            )
        },
        supplementalText.takeIf { it.isNotBlank() }?.let {
            SourceInputSection(
                channel = SourceInputChannel.SUPPLEMENTAL_TEXT,
                label = "补充文字",
                content = it.trim(),
            )
        },
    )
}

private data class ExecutionModelRoute(
    val sessionConfig: MnnSessionConfig,
    val isLocalModelReady: Boolean,
    val statusMessage: String,
)

private fun composeUnifiedSourceText(
    sections: List<SourceInputSection>,
): String {
    if (sections.isEmpty()) return ""
    return buildString {
        sections.forEachIndexed { index, section ->
            appendLine("[${section.label}]")
            appendLine(section.content)
            if (index != sections.lastIndex) {
                appendLine()
            }
        }
    }.trim()
}

private fun SelectedLocalAsset.toTaskAssetRef(
    kindLabel: String,
): String {
    return "$kindLabel | $displayName | $mimeTypeLabel | $uri"
}

private data class ParsedAssetRef(
    val kindLabel: String,
    val displayName: String,
    val mimeTypeLabel: String,
    val uri: String,
)

private fun parseAssetRef(
    raw: String,
): ParsedAssetRef? {
    val parts = raw.split(" | ", limit = 4)
    if (parts.size < 4) return null
    return ParsedAssetRef(
        kindLabel = parts[0],
        displayName = parts[1],
        mimeTypeLabel = parts[2],
        uri = parts[3],
    )
}

private fun ParsedAssetRef.toSelectedLocalAsset(): SelectedLocalAsset {
    return SelectedLocalAsset(
        uri = uri,
        displayName = displayName,
        mimeTypeLabel = mimeTypeLabel,
    )
}

private fun SelectedLocalAsset.isImageAsset(): Boolean {
    return mimeTypeLabel.startsWith("image/", ignoreCase = true)
}

private fun SelectedLocalAsset.isReadableDocumentAsset(): Boolean {
    val normalizedMime = mimeTypeLabel.lowercase(Locale.US)
    val normalizedName = displayName.lowercase(Locale.US)
    return normalizedMime.startsWith("text/") ||
        normalizedMime.contains("pdf") ||
        normalizedMime.contains("word") ||
        normalizedMime.contains("presentation") ||
        normalizedMime.contains("powerpoint") ||
        normalizedName.endsWith(".txt") ||
        normalizedName.endsWith(".md") ||
        normalizedName.endsWith(".pdf") ||
        normalizedName.endsWith(".doc") ||
        normalizedName.endsWith(".docx") ||
        normalizedName.endsWith(".ppt") ||
        normalizedName.endsWith(".pptx")
}

private fun SelectedLocalAsset.taskAssetKindLabel(): String {
    return when {
        mimeTypeLabel.startsWith("image/", ignoreCase = true) -> "IMAGE"
        mimeTypeLabel.startsWith("text/", ignoreCase = true) -> "TEXT"
        mimeTypeLabel.contains("pdf", ignoreCase = true) ||
            mimeTypeLabel.contains("word", ignoreCase = true) ||
            mimeTypeLabel.contains("presentation", ignoreCase = true) ||
            mimeTypeLabel.contains("powerpoint", ignoreCase = true) ||
            displayName.endsWith(".pdf", ignoreCase = true) ||
            displayName.endsWith(".doc", ignoreCase = true) ||
            displayName.endsWith(".docx", ignoreCase = true) ||
            displayName.endsWith(".ppt", ignoreCase = true) ||
            displayName.endsWith(".pptx", ignoreCase = true) -> "DOCUMENT"
        else -> "DOCUMENT"
    }
}

private fun List<SourceInputSection>.firstContent(
    channel: SourceInputChannel,
): String {
    return firstOrNull { it.channel == channel }?.content.orEmpty()
}

private fun List<String>.firstParsedAsset(
    kindLabel: String,
): ParsedAssetRef? {
    return asSequence()
        .mapNotNull(::parseAssetRef)
        .firstOrNull { it.kindLabel == kindLabel }
}

private fun List<String>.parsedAssets(
    kindLabel: String,
): List<ParsedAssetRef> {
    return mapNotNull(::parseAssetRef)
        .filter { it.kindLabel == kindLabel }
}

private fun List<String>.parsedAssets(
    kindLabels: Set<String>,
): List<ParsedAssetRef> {
    return mapNotNull(::parseAssetRef)
        .filter { it.kindLabel in kindLabels }
}

private fun restoreDraftSourceSections(
    task: MemoTask,
    memo: StructuredMemo?,
): List<SourceInputSection> {
    if (task.sourceSections.isNotEmpty()) return task.sourceSections
    val parsedSections = parseUnifiedSourceText(task.sourceText)
    if (parsedSections.isNotEmpty()) return parsedSections
    val outlineSections = memo?.sourceOutline
        ?.mapNotNull(::parseOutlineSourceSection)
        .orEmpty()
    if (outlineSections.isNotEmpty()) return outlineSections
    return task.sourceText.takeIf { it.isNotBlank() }
        ?.let {
            listOf(
                SourceInputSection(
                    channel = SourceInputChannel.SUPPLEMENTAL_TEXT,
                    label = "补充文字",
                    content = it.trim(),
                ),
            )
        }
        .orEmpty()
}

private fun parseUnifiedSourceText(
    sourceText: String,
): List<SourceInputSection> {
    if (sourceText.isBlank()) return emptyList()
    val matches = Regex("""\[(.+?)]\s*([\s\S]*?)(?=\n\[[^\]]+]|$)""")
        .findAll(sourceText.trim())
        .mapNotNull { match ->
            val label = match.groupValues[1].trim()
            val content = match.groupValues[2].trim()
            if (label == "统一文本上下文" || content.isBlank()) {
                null
            } else {
                sourceInputChannelFromLabel(label)?.let { channel ->
                    SourceInputSection(
                        channel = channel,
                        label = label,
                        content = content,
                    )
                }
            }
        }
        .toList()
    return matches
}

private fun parseOutlineSourceSection(
    raw: String,
): SourceInputSection? {
    val delimiterIndex = raw.indexOf(':')
    if (delimiterIndex <= 0) return null
    val label = raw.substring(0, delimiterIndex).trim()
    val content = raw.substring(delimiterIndex + 1).trim()
    if (content.isBlank()) return null
    val channel = sourceInputChannelFromLabel(label) ?: return null
    return SourceInputSection(
        channel = channel,
        label = label,
        content = content,
    )
}

private fun sourceInputChannelFromLabel(
    label: String,
): SourceInputChannel? {
    return when (label.trim()) {
        "图片内容补充" -> SourceInputChannel.IMAGE_BRIEF
        "OCR 文本", "图片识别文本" -> SourceInputChannel.OCR_TEXT
        "文档读取文本" -> SourceInputChannel.DOCUMENT_TEXT
        "录音转写文本" -> SourceInputChannel.AUDIO_TRANSCRIPT
        "补充文字" -> SourceInputChannel.SUPPLEMENTAL_TEXT
        else -> null
    }
}

@Composable
private fun AppScreen.icon() = when (this) {
    AppScreen.HOME -> Icons.Outlined.Settings
    AppScreen.CAPTURE -> Icons.Outlined.AssignmentTurnedIn
    AppScreen.HISTORY -> Icons.Outlined.History
    AppScreen.RESULT -> Icons.Outlined.Article
}

private fun android.content.Context.persistReadPermission(
    uri: Uri,
) {
    runCatching {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}

private fun android.content.Context.resolveSelectedLocalAsset(
    uri: Uri,
): SelectedLocalAsset {
    val mimeType = contentResolver.getType(uri).orEmpty().ifBlank { "unknown" }
    val displayName = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (columnIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(columnIndex)
            } else {
                null
            }
        }
        ?: uri.lastPathSegment
        ?: "unnamed"
    return SelectedLocalAsset(
        uri = uri.toString(),
        displayName = displayName,
        mimeTypeLabel = mimeType,
    )
}

private fun appendSelectedImages(
    existing: List<SelectedLocalAsset>,
    incoming: List<SelectedLocalAsset>,
    maxCount: Int,
): List<SelectedLocalAsset> {
    return (existing + incoming)
        .distinctBy { it.uri }
        .take(maxCount)
}

private fun replaceSelectedImageAsset(
    existing: List<SelectedLocalAsset>,
    sourceUri: String,
    replacement: SelectedLocalAsset,
): List<SelectedLocalAsset> {
    return existing.map { asset ->
        if (asset.uri == sourceUri) {
            replacement
        } else {
            asset
        }
    }
}

private fun android.content.Context.createCapturedImageUri(
    storage: AppStorageDirectories,
): Uri {
    val imageFile = File(
        storage.capturedImagesDir,
        "captured_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg",
    )
    imageFile.parentFile?.mkdirs()
    return FileProvider.getUriForFile(
        this,
        "$packageName.fileprovider",
        imageFile,
    )
}

private fun android.content.Context.createImageCropOutputUri(
    storage: AppStorageDirectories,
): Uri {
    val croppedFile = File(
        storage.cacheDir,
        "cropped_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg",
    )
    croppedFile.parentFile?.mkdirs()
    return FileProvider.getUriForFile(
        this,
        "$packageName.fileprovider",
        croppedFile,
    )
}

private fun android.content.Context.openAssetExternally(
    uri: Uri,
) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setData(uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(intent) }
}

private fun android.content.Context.copyTextToClipboard(
    label: String,
    text: String,
) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun android.content.Context.sharePlainText(
    subject: String,
    text: String,
) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(Intent.createChooser(intent, "分享纪要任务"))
}

private fun android.content.Context.composeEmailDraft(
    subject: String,
    body: String,
) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(intent) }
}

private fun buildResultSections(
    memo: StructuredMemo?,
    remoteTask: AgentTask?,
): List<ResultSectionItem> {
    return buildList {
        memo?.let {
            add(ResultSectionItem(id = "background", label = "背景", value = it.background))
            if (it.facts.isNotEmpty()) {
                add(ResultSectionItem(id = "facts", label = "关键要点", value = formatNumberedLines(it.facts)))
            } else if (it.topics.isNotEmpty()) {
                add(ResultSectionItem(id = "topics", label = "关键要点", value = formatNumberedLines(it.topics.map { topic -> "${topic.name}: ${topic.summary}" })))
            } else if (it.decisions.isNotEmpty()) {
                add(ResultSectionItem(id = "decisions", label = "关键要点", value = formatNumberedLines(it.decisions)))
            }
            if (it.actionItems.isNotEmpty()) {
                add(
                    ResultSectionItem(
                        id = "action_items",
                        label = "行动项",
                        value = it.actionItems.joinToString("\n") { item ->
                            listOfNotNull(
                                item.task,
                                item.owner.takeIf { owner -> owner.isNotBlank() },
                                item.deadline,
                            ).joinToString(" | ")
                        },
                    ),
                )
            }
            if (it.risks.isNotEmpty()) {
                add(ResultSectionItem(id = "risks", label = "风险提示", value = it.risks.joinToString("\n")))
            }
            if (it.sourceOutline.isNotEmpty()) {
                add(ResultSectionItem(id = "source_outline", label = "输入来源", value = it.sourceOutline.joinToString(" / ")))
            }
            if (it.tags.isNotEmpty()) {
                add(ResultSectionItem(id = "tags", label = "标签", value = formatTagLines(it.tags)))
            }
        }
    }
}

private fun StructuredMemo.withEditedResultSection(
    sectionId: String,
    value: String,
): StructuredMemo {
    val cleaned = value.trim()
    return when (sectionId) {
        "summary" -> copy(oneLineSummary = cleaned)
        "background" -> copy(background = cleaned)
        "facts" -> copy(facts = parseEditableLines(cleaned))
        "topics" -> copy(
            topics = parseEditableLines(cleaned).map { line ->
                val name = line.substringBefore(':', missingDelimiterValue = line).trim()
                val summary = line.substringAfter(':', missingDelimiterValue = "").trim()
                TopicSummary(
                    name = name,
                    summary = summary.ifBlank { name },
                )
            },
        )
        "decisions" -> copy(decisions = parseEditableLines(cleaned))
        "action_items" -> copy(
            actionItems = parseEditableLines(cleaned).map { line ->
                val parts = line.split("|").map { it.trim() }
                ActionItem(
                    task = parts.getOrNull(0).orEmpty(),
                    owner = parts.getOrNull(1).orEmpty(),
                    deadline = parts.getOrNull(2)?.takeIf { it.isNotBlank() },
                )
            }.filter { it.task.isNotBlank() },
        )
        "risks" -> copy(risks = parseEditableLines(cleaned))
        "source_outline" -> copy(sourceOutline = cleaned.split("/", " / ").map { it.trim() }.filter { it.isNotBlank() })
        "tags" -> copy(tags = parseEditableLines(cleaned).map { it.trimStart('●', '-', '*').trim() })
        else -> this
    }
}

private fun parseEditableLines(
    value: String,
): List<String> {
    return value
        .lineSequence()
        .map { line ->
            line.trim()
                .replace(Regex("""^\d+[\.)、]\s*"""), "")
                .trimStart('●', '-', '*')
                .trim()
        }
        .filter { it.isNotBlank() }
        .toList()
}

private fun buildBridgeStatusUiState(
    remoteTask: AgentTask?,
    focusedTask: MemoTask?,
    allTasks: List<MemoTask>,
    recentRemoteTasks: List<AgentTask>,
    isBridgeConfigured: Boolean,
): ResultBridgeStatusUiState? {
    if (!isBridgeConfigured) {
        return ResultBridgeStatusUiState(
            label = "Bridge 状态",
            summary = "当前设备尚未配置 Supabase，手机端还不能直接把任务投递给桌面 Bridge。",
            detailLines = listOf("完成 Supabase 配置后，这里会显示桌面 Bridge 最近活跃状态。"),
        )
    }
    val latestRemoteRef = remoteTask?.let(::toRemoteTaskRef)
        ?: focusedTask?.remoteAgentTasks?.lastOrNull()
        ?: focusedTask?.toLegacyRemoteAgentTaskRef()
        ?: recentRemoteTasks.maxByOrNull { parseBridgeTimestampMillis(it.updatedAt) ?: 0L }?.let(::toRemoteTaskRef)
        ?: allTasks
            .asSequence()
            .flatMap { task -> task.remoteAgentTasks.ifEmpty { listOfNotNull(task.toLegacyRemoteAgentTaskRef()) }.asSequence() }
            .maxByOrNull { parseBridgeTimestampMillis(it.updatedAt) ?: 0L }
    val statusSummary = when {
        remoteTask != null && isRecentlyActive(remoteTask.updatedAt, withinMinutes = 5) &&
            remoteTask.status in setOf(AgentTaskStatus.RUNNING, AgentTaskStatus.WAITING_APPROVAL) ->
            "桌面 Bridge 很可能在线，最近几分钟内仍在处理任务。"
        latestRemoteRef != null && isRecentlyActive(latestRemoteRef.updatedAt, withinMinutes = 15) ->
            "桌面 Bridge 最近活跃过，手机端可以继续提交或刷新任务。"
        latestRemoteRef != null ->
            "桌面 Bridge 最近没有新的任务活动，可能暂时未启动。"
        else ->
            "还没有检测到任何 Bridge 任务记录，先提交一条纪要任务试试看。"
    }
    val detailLines = buildList {
        remoteTask?.claimedBy?.takeIf { it.isNotBlank() }?.let { add("Bridge：$it") }
        latestRemoteRef?.targetAgent?.takeIf { it.isNotBlank() }?.let { add("最近 Agent：$it") }
        latestRemoteRef?.status?.takeIf { it.isNotBlank() }?.let { status ->
            add(
                when {
                    "running" in status || "waiting_approval" in status -> "状态推断：执行链路活跃"
                    "done" in status -> "状态推断：最近已完成任务"
                    "failed" in status -> "状态推断：最近一次任务失败"
                    "cancelled" in status -> "状态推断：最近一次任务被取消"
                    else -> "状态推断：已有任务记录"
                },
            )
        }
        latestRemoteRef?.updatedAt?.takeIf { it.isNotBlank() }?.let { add("最后更新时间：$it") }
        latestRemoteRef?.taskId?.takeIf { it.isNotBlank() }?.let { add("最近任务：${it.take(8)}") }
    }
    return ResultBridgeStatusUiState(
        label = "Bridge 状态",
        summary = statusSummary,
        detailLines = detailLines,
    )
}

private fun toRemoteTaskRef(
    remoteTask: AgentTask,
): MemoRemoteAgentTaskRef {
    return MemoRemoteAgentTaskRef(
        taskId = remoteTask.id,
        targetAgent = remoteTask.targetAgent,
        status = remoteTask.status.name.lowercase(),
        summary = remoteTask.result?.summary.orEmpty(),
        updatedAt = remoteTask.updatedAt,
    )
}

private fun isRecentlyActive(
    updatedAt: String?,
    withinMinutes: Int,
): Boolean {
    val updatedMillis = parseBridgeTimestampMillis(updatedAt) ?: return false
    val delta = System.currentTimeMillis() - updatedMillis
    return delta in 0..(withinMinutes * 60_000L)
}

private fun parseBridgeTimestampMillis(
    raw: String?,
): Long? {
    val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val normalized = value.replace(Regex("\\.(\\d{3})\\d+"), ".$1")
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
    )
    return patterns.firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(normalized)?.time
        }.getOrNull()
    }
}

private fun buildRemoteTaskUiState(
    remoteTask: AgentTask,
): ResultRemoteTaskUiState {
    val summary = buildRemoteTaskSummary(remoteTask)
    val detailLines = buildList {
        add("任务 ID：${remoteTask.id.take(8)}")
        add("模式：${remoteTask.mode.name.lowercase()} | 状态：${remoteTask.status.name.lowercase()}")
        remoteTask.result?.currentPhase
            ?.takeIf { phase -> phase.isNotBlank() }
            ?.let { phase -> add("阶段：$phase") }
        if (remoteTask.permission.approvedForExecution) {
            add("审批：已批准执行 elevated workspace_write")
        }
        remoteTask.claimedBy?.takeIf { it.isNotBlank() }?.let { add("Bridge：$it") }
        remoteTask.updatedAt?.takeIf { it.isNotBlank() }?.let { add("更新时间：$it") }
        remoteTask.error?.message
            ?.takeIf { remoteTask.status == AgentTaskStatus.FAILED && it.isNotBlank() }
            ?.let { add("错误：$it") }
        remoteTask.result?.rawStderr
            ?.takeIf { remoteTask.status == AgentTaskStatus.FAILED && it.isNotBlank() }
            ?.let { rawStderr ->
                rawStderr.lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toList()
                    .takeLast(3)
                    .joinToString(" | ")
            }
            ?.takeIf { it.isNotBlank() }
            ?.let { add("诊断：$it") }
    }
    val progressTimeline = remoteTask.result?.progressEvents
        .orEmpty()
        .mapNotNull(::formatBridgeProgressEvent)
        .takeLast(4)
    val resultSections = buildRemoteTaskResultSections(remoteTask)
    val actions = buildList {
        add(ResultRemoteTaskActionItem("refresh_remote_task", "刷新", Icons.Outlined.Refresh))
        add(ResultRemoteTaskActionItem("copy_remote_task_id", "复制任务 ID", Icons.Outlined.ContentCopy))
        if (remoteTask.status in setOf(AgentTaskStatus.PENDING, AgentTaskStatus.RUNNING, AgentTaskStatus.WAITING_APPROVAL)) {
            add(ResultRemoteTaskActionItem("cancel_remote_task", "取消任务", Icons.Outlined.ContentCopy))
        }
        if (remoteTask.status == AgentTaskStatus.WAITING_APPROVAL) {
            add(ResultRemoteTaskActionItem("approve_remote_task_workspace_write", "批准执行", Icons.Outlined.AssignmentTurnedIn))
        }
        remoteTask.result?.planMarkdown?.takeIf { it.isNotBlank() }?.let {
            add(ResultRemoteTaskActionItem("copy_remote_task_plan", "复制计划", Icons.Outlined.ContentCopy))
        }
        remoteTask.error?.message?.takeIf { it.isNotBlank() }?.let {
            add(ResultRemoteTaskActionItem("copy_remote_task_error", "复制错误", Icons.Outlined.ContentCopy))
        }
        if (remoteTask.status in setOf(AgentTaskStatus.WAITING_APPROVAL, AgentTaskStatus.FAILED, AgentTaskStatus.CANCELLED)) {
            add(ResultRemoteTaskActionItem("retry_remote_task_safe", "安全重排", Icons.Outlined.Refresh))
        }
    }
    return ResultRemoteTaskUiState(
        taskId = remoteTask.id,
        targetAgent = remoteTask.targetAgent,
        statusLabel = remoteTask.status.name.lowercase(),
        modeLabel = remoteTask.mode.name.lowercase(),
        goal = remoteTask.goal,
        summary = summary,
        detailLines = detailLines,
        progressTimeline = progressTimeline,
        resultSections = resultSections,
        actions = actions,
    )
}

private fun buildRemoteTaskSummary(
    remoteTask: AgentTask,
): String {
    val rawSummary = remoteTask.result?.summary
        ?.takeIf { it.isNotBlank() }
        ?.let(::cleanBridgeMarkdown)
        .orEmpty()
    return when {
        remoteTask.status == AgentTaskStatus.FAILED && !remoteTask.error?.message.isNullOrBlank() ->
            remoteTask.error?.message.orEmpty()
        remoteTask.status == AgentTaskStatus.DONE && remoteTask.result?.planMarkdown?.isNullOrBlank() == false ->
            "Codex 已返回结果，下面保留了最关键的步骤、风险和测试建议。"
        rawSummary.isNotBlank() && rawSummary.length > 8 -> rawSummary
        !remoteTask.error?.message.isNullOrBlank() -> remoteTask.error?.message.orEmpty()
        remoteTask.status == AgentTaskStatus.WAITING_APPROVAL -> "这条任务需要额外确认后才能继续执行。"
        remoteTask.status == AgentTaskStatus.RUNNING -> "电脑端 Bridge 已经接手任务，正在执行中。"
        remoteTask.status == AgentTaskStatus.DONE -> "MemoMind Agent Bridge 已完成这条远程任务。"
        remoteTask.status == AgentTaskStatus.FAILED -> "MemoMind Agent Bridge 执行失败。"
        else -> "MemoMind Agent Bridge 任务已创建。"
    }
}

private fun buildRemoteTaskResultSections(
    remoteTask: AgentTask,
): List<ResultSectionItem> {
    val planSections = remoteTask.result?.planMarkdown
        ?.takeIf { it.isNotBlank() }
        ?.let(::extractBridgePlanSections)
        .orEmpty()
    return buildList {
        buildCompactSection(
            title = "项目概览",
            items = compactPlanItems(planSections["项目结构分析"], limit = 3),
        )?.let(::add)
        buildCompactSection(
            title = "建议修改文件",
            items = compactPlanItems(
                body = planSections["需要修改的文件"],
                limit = 4,
                preferListItems = true,
            ).ifEmpty {
                remoteTask.result?.filesToTouch
                    .orEmpty()
                    .map(::cleanBridgeMarkdown)
                    .filter { it.isNotBlank() && !it.endsWith("：") }
                    .take(4)
            },
        )?.let(::add)
        buildCompactSection(
            title = "实现步骤",
            items = compactPlanItems(planSections["实现步骤"], limit = 4, preferListItems = true),
        )?.let(::add)
        buildCompactSection(
            title = "风险点",
            items = remoteTask.result?.risks
                .orEmpty()
                .map(::cleanBridgeMarkdown)
                .filter { it.isNotBlank() }
                .take(4)
                .ifEmpty { compactPlanItems(planSections["风险点"], limit = 4, preferListItems = true) },
        )?.let(::add)
        buildCompactSection(
            title = "测试建议",
            items = remoteTask.result?.testSuggestions
                .orEmpty()
                .map(::cleanBridgeMarkdown)
                .filter { it.isNotBlank() }
                .take(4)
                .ifEmpty { compactPlanItems(planSections["测试建议"], limit = 4, preferListItems = true) },
        )?.let(::add)
        remoteTask.error?.detail
            ?.takeIf { it.isNotBlank() }
            ?.let { detail ->
                buildCompactSection(
                    title = "错误详情",
                    items = compactPlanItems(detail, limit = 3),
                )?.let(::add)
            }
    }
}

private fun buildCompactSection(
    title: String,
    items: List<String>,
): ResultSectionItem? {
    val cleanedItems = items
        .map(::cleanBridgeMarkdown)
        .map { abbreviateBridgeLine(it, maxChars = 72) }
        .filter { it.isNotBlank() }
    if (cleanedItems.isEmpty()) return null
    return ResultSectionItem(
        label = title,
        value = formatBulletLines(cleanedItems),
    )
}

private fun extractBridgePlanSections(
    rawPlan: String,
): Map<String, String> {
    val recognizedHeadings = setOf(
        "项目结构分析",
        "需要修改的文件",
        "实现步骤",
        "风险点",
        "测试建议",
        "允许 workspace_write 后的执行方式",
    )
    val cleaned = cleanBridgeMarkdown(rawPlan)
    val sections = linkedMapOf<String, MutableList<String>>()
    var currentHeading = "项目结构分析"
    sections.getOrPut(currentHeading) { mutableListOf() }
    cleaned.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isBlank()) return@forEach
        if (line in recognizedHeadings) {
            currentHeading = line
            sections.getOrPut(currentHeading) { mutableListOf() }
        } else {
            sections.getValue(currentHeading).add(line)
        }
    }
    return sections.mapValues { (_, lines) -> lines.joinToString("\n") }
}

private fun compactPlanItems(
    body: String?,
    limit: Int,
    preferListItems: Boolean = false,
): List<String> {
    val normalizedLines = body
        ?.lineSequence()
        ?.map { line ->
            line.trim()
                .removePrefix("- ")
                .replace(Regex("^\\d+\\.\\s*"), "")
        }
        ?.filter { it.isNotBlank() }
        ?.toList()
        .orEmpty()
    if (normalizedLines.isEmpty()) return emptyList()
    val explicitItems = normalizedLines.filter { line ->
        line.startsWith("`") ||
            line.startsWith("1.").not() && line.contains("：") ||
            line.contains("/") ||
            line.contains(".kt") ||
            line.contains(".kts") ||
            line.contains(".md")
    }
    val source = when {
        preferListItems && explicitItems.isNotEmpty() -> explicitItems
        preferListItems -> normalizedLines
        explicitItems.isNotEmpty() -> explicitItems
        else -> normalizedLines
    }
    return source
        .map { line -> abbreviateBridgeLine(line) }
        .distinct()
        .take(limit)
}

private fun cleanBridgeMarkdown(
    raw: String,
): String {
    return raw
        .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
        .lineSequence()
        .map { line ->
            line.trimEnd()
                .replace(Regex("^#{1,6}\\s*"), "")
                .replace(Regex("^\\*\\*(.+)\\*\\*$"), "$1")
        }
        .joinToString("\n")
        .replace("**", "")
        .trim()
}

private fun abbreviateBridgeLine(
    raw: String,
    maxChars: Int = 96,
): String {
    val singleLine = raw
        .replace("```", "")
        .replace(Regex("\\s+"), " ")
        .trim()
    return if (singleLine.length <= maxChars) {
        singleLine
    } else {
        singleLine.take(maxChars - 1).trimEnd() + "…"
    }
}

private fun formatBulletLines(
    lines: List<String>,
): String {
    return lines.joinToString("\n") { line -> "• $line" }
}

private fun formatBridgeProgressEvent(
    event: AgentTaskProgressEvent,
): String? {
    val message = event.message
        .takeIf { it.isNotBlank() }
        ?.let { abbreviateBridgeLine(cleanBridgeMarkdown(it), maxChars = 40) }
    val phase = event.phase.takeIf { it.isNotBlank() }
    val time = event.createdAt?.let(::formatBridgeClock)
    return listOfNotNull(time, phase, message)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" | ")
}

private fun formatBridgeClock(
    raw: String,
): String? {
    val millis = parseBridgeTimestampMillis(raw) ?: return null
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
}

private fun buildHistoryTaskDetail(
    task: MemoTask,
): String {
    val remoteTasks = task.remoteAgentTasks.ifEmpty {
        listOfNotNull(task.toLegacyRemoteAgentTaskRef())
    }
    val latestRemoteTask = remoteTasks.lastOrNull()
    val parts = buildList {
        add(task.type)
        add(task.processingMode.name)
        latestRemoteTask?.targetAgent?.takeIf { it.isNotBlank() }?.let { target ->
            add("Bridge:$target")
        }
        latestRemoteTask?.status?.takeIf { it.isNotBlank() }?.let { remoteStatus ->
            add("远程:$remoteStatus")
        }
    }
    val summaryLines = remoteTasks
        .asReversed()
        .take(3)
        .map { remoteTask ->
            buildString {
                append(remoteTask.targetAgent)
                append(" | ")
                append(remoteTask.status)
                remoteTask.summary.takeIf { it.isNotBlank() }?.let { summary ->
                    append(" | ")
                    append(summary.take(48))
                }
            }
        }
    return buildString {
        append(parts.joinToString(" | "))
        if (summaryLines.isNotEmpty()) {
            append('\n')
            append(summaryLines.joinToString("\n"))
        }
        if (remoteTasks.size > 3) {
            append('\n')
            append("还有 ${remoteTasks.size - 3} 条更早的 Agent 任务")
        }
    }
}

private fun buildRemoteTaskOptions(
    task: MemoTask?,
    selectedRemoteTaskId: String?,
): List<ResultRemoteTaskOptionItem> {
    if (task == null) return emptyList()
    val remoteTasks = task.remoteAgentTasks.ifEmpty {
        listOfNotNull(task.toLegacyRemoteAgentTaskRef())
    }
    return remoteTasks
        .asReversed()
        .map { remoteTask ->
            ResultRemoteTaskOptionItem(
                taskId = remoteTask.taskId,
                targetAgent = remoteTask.targetAgent,
                statusLabel = remoteTask.status,
                summary = remoteTask.summary.ifBlank { defaultRemoteTaskSummary(remoteTask.status, remoteTask.targetAgent) },
                detail = listOfNotNull(
                    remoteTask.updatedAt?.takeIf { it.isNotBlank() }?.let { "更新时间：$it" },
                    "任务 ID：${remoteTask.taskId.take(8)}",
                ).joinToString(" | "),
                isSelected = remoteTask.taskId == selectedRemoteTaskId,
            )
        }
}

private fun synchronizeLocalTaskWithRemote(
    remoteTask: AgentTask,
    taskLocalStore: JsonFileMemoTaskLocalDataSource,
    onTaskDataChanged: () -> Unit,
) {
    val relatedTaskId = remoteTask.sourceTaskId ?: return
    val localTask = taskLocalStore.getAll().firstOrNull { it.id == relatedTaskId } ?: return
    taskLocalStore.save(localTask.withUpsertedRemoteTask(remoteTask))
    onTaskDataChanged()
}

private fun MemoTask.toLegacyRemoteAgentTaskRef(): MemoRemoteAgentTaskRef? {
    val taskId = remoteAgentTaskId?.takeIf { it.isNotBlank() } ?: return null
    val targetAgent = remoteAgentTarget?.takeIf { it.isNotBlank() } ?: return null
    val status = remoteAgentTaskStatus?.takeIf { it.isNotBlank() } ?: return null
    return MemoRemoteAgentTaskRef(
        taskId = taskId,
        targetAgent = targetAgent,
        status = status,
        summary = "",
    )
}

private fun preferredRemoteTaskId(
    task: MemoTask,
): String? {
    return task.remoteAgentTasks.lastOrNull()?.taskId
        ?: task.remoteAgentTaskId?.takeIf { it.isNotBlank() }
}

private fun findResumableRemoteTaskId(
    task: MemoTask,
): String? {
    val remoteTasks = task.remoteAgentTasks.ifEmpty {
        listOfNotNull(task.toLegacyRemoteAgentTaskRef())
    }
    return remoteTasks
        .asReversed()
        .firstOrNull { it.status !in listOf("done", "failed", "cancelled") }
        ?.taskId
}

private fun MemoTask.withUpsertedRemoteTask(
    remoteTask: AgentTask,
): MemoTask {
    val existing = remoteAgentTasks
        .ifEmpty { listOfNotNull(toLegacyRemoteAgentTaskRef()) }
        .filterNot { it.taskId == remoteTask.id }
    val merged = existing + MemoRemoteAgentTaskRef(
        taskId = remoteTask.id,
        targetAgent = remoteTask.targetAgent,
        status = remoteTask.status.name.lowercase(),
        summary = remoteTask.result?.summary
            ?.takeIf { it.isNotBlank() }
            ?: remoteTask.error?.message?.takeIf { it.isNotBlank() }
            ?: defaultRemoteTaskSummary(remoteTask.status.name.lowercase(), remoteTask.targetAgent),
        updatedAt = remoteTask.updatedAt,
    )
    return copy(
        remoteAgentTasks = merged,
        remoteAgentTaskId = merged.lastOrNull()?.taskId,
        remoteAgentTaskStatus = merged.lastOrNull()?.status,
        remoteAgentTarget = merged.lastOrNull()?.targetAgent,
    )
}

private fun defaultRemoteTaskSummary(
    status: String,
    targetAgent: String,
): String {
    return when (status.lowercase()) {
        "waiting_approval" -> "$targetAgent 任务正在等待用户批准。"
        "running" -> "$targetAgent 正在执行这条纪要任务。"
        "done" -> "$targetAgent 已完成这条纪要任务。"
        "failed" -> "$targetAgent 执行失败，请查看错误详情。"
        "cancelled" -> "$targetAgent 任务已取消。"
        else -> "$targetAgent 任务已创建，等待 Bridge 处理。"
    }
}

private fun buildBridgeTaskForAgent(
    targetAgent: String,
    memo: StructuredMemo,
    execution: StructuredMemoTaskExecutionResult,
    userId: String,
): AgentTask {
    return when (targetAgent) {
        "codex" -> buildCodexBridgeTask(
            memo = memo,
            execution = execution,
            userId = userId,
        )
        "trae", "work_buddy" -> buildMemoFollowupBridgeTask(
            targetAgent = targetAgent,
            memo = memo,
            execution = execution,
            userId = userId,
        )
        else -> buildMemoFollowupBridgeTask(
            targetAgent = targetAgent,
            memo = memo,
            execution = execution,
            userId = userId,
        )
    }
}

private fun buildCodexBridgeTask(
    memo: StructuredMemo,
    execution: StructuredMemoTaskExecutionResult,
    userId: String,
): AgentTask {
    val executionTitle = execution.task.title.ifBlank { "MemoMind 纪要任务" }
    val requirements = buildList {
        add("先分析当前 Android 项目结构，再给出实现计划")
        add("只输出计划，不修改代码")
        add("明确需要修改的文件、实现步骤、风险点和测试建议")
        memo.actionItems.take(4).forEach { add(it.task) }
    }
    val constraints = buildList {
        add("默认保持 plan_only / read_only")
        add("不要自动 git commit")
        add("不要自动 git push")
        add("不要删除文件")
        add("代码修改前需要用户确认")
        memo.risks.take(3).forEach { add(it) }
    }
    return AgentTask(
        userId = userId,
        sourceApp = MemoMindSourceAppId,
        sourceTaskId = memo.taskId,
        targetAgent = "codex",
        projectId = MemoMindAgentProjectId,
        taskType = "code_change",
        mode = AgentTaskMode.PLAN_ONLY,
        goal = "基于 MemoMind 纪要，为 $executionTitle 生成 Android 项目实现计划",
        prompt = """
            请分析当前 MemoMind Android 项目结构，并结合下面纪要给出实现计划，暂时不要修改代码。
            输出请覆盖：项目结构分析、需要修改的文件、实现步骤、风险点、测试建议、以及后续如果允许 workspace_write 应如何继续执行。
        """.trimIndent(),
        context = AgentTaskContext(
            meetingSummary = memo.oneLineSummary,
            requirements = requirements,
            constraints = constraints,
            memoSummary = memo.oneLineSummary,
            memoBackground = memo.background,
            facts = memo.facts,
            decisions = memo.decisions,
            actionItems = memo.actionItems,
            risks = memo.risks,
            tags = memo.tags,
            sourceOutline = memo.sourceOutline,
        ),
        permission = AgentTaskPermission(
            requireUserApproval = true,
            allowCodeWrite = false,
            allowShellCommand = false,
            allowGitCommit = false,
            allowGitPush = false,
            allowFileDelete = false,
            allowNetworkAccess = false,
        ),
        status = AgentTaskStatus.PENDING,
    )
}

private fun buildMemoFollowupBridgeTask(
    targetAgent: String,
    memo: StructuredMemo,
    execution: StructuredMemoTaskExecutionResult,
    userId: String,
): AgentTask {
    val executionTitle = execution.task.title.ifBlank { "MemoMind 纪要任务" }
    val requirements = buildList {
        add("先快速理解这份纪要，再输出适合继续执行的结构化结果")
        add("给出清晰的行动清单、优先级和后续建议")
        memo.actionItems.take(4).forEach { add(it.task) }
    }
    val constraints = buildList {
        add("默认保持 plan_only / read_only")
        add("不要自动 git commit")
        add("不要自动 git push")
        add("不要删除文件")
        memo.risks.take(3).forEach { add(it) }
    }
    val prompt = when (targetAgent) {
        "trae" -> "请将纪要继续整理为项目任务拆解、优先级列表、流程图/图表建议和执行方案。"
        "work_buddy" -> "请将纪要继续整理为工作待办、周报素材、对外同步文案和邮件草稿。"
        else -> "请基于这份纪要继续整理成清晰的结构化结果和后续执行清单。"
    }
    return AgentTask(
        userId = userId,
        sourceApp = MemoMindSourceAppId,
        sourceTaskId = memo.taskId,
        targetAgent = targetAgent,
        projectId = MemoMindAgentProjectId,
        taskType = "memo_followup",
        mode = AgentTaskMode.PLAN_ONLY,
        goal = "基于 MemoMind 纪要，为 $executionTitle 生成 ${targetAgent.replace('_', ' ')} 后续处理结果",
        prompt = prompt,
        context = AgentTaskContext(
            meetingSummary = memo.oneLineSummary,
            requirements = requirements,
            constraints = constraints,
            memoSummary = memo.oneLineSummary,
            memoBackground = memo.background,
            facts = memo.facts,
            decisions = memo.decisions,
            actionItems = memo.actionItems,
            risks = memo.risks,
            tags = memo.tags,
            sourceOutline = memo.sourceOutline,
        ),
        permission = AgentTaskPermission(
            requireUserApproval = true,
            allowCodeWrite = false,
            allowShellCommand = false,
            allowGitCommit = false,
            allowGitPush = false,
            allowFileDelete = false,
            allowNetworkAccess = false,
        ),
        status = AgentTaskStatus.PENDING,
    )
}

private fun buildAgentHandoffPrompt(
    actionId: String,
    memo: StructuredMemo,
    execution: StructuredMemoTaskExecutionResult,
): String {
    val taskTitle = execution.task.title.ifBlank { "未命名纪要任务" }
    val coreMemo = buildString {
        appendLine("任务标题：$taskTitle")
        appendLine("一句话总结：${memo.oneLineSummary}")
        appendLine("背景：${memo.background}")
        if (memo.facts.isNotEmpty()) {
            appendLine("关键要点：")
            memo.facts.forEachIndexed { index, item ->
                appendLine("${index + 1}. $item")
            }
        }
        if (memo.actionItems.isNotEmpty()) {
            appendLine("行动项：")
            memo.actionItems.forEachIndexed { index, item ->
                appendLine("${index + 1}. ${listOfNotNull(item.task, item.owner.takeIf { it.isNotBlank() }, item.deadline?.takeIf { it.isNotBlank() }).joinToString(" | ")}")
            }
        }
        if (memo.risks.isNotEmpty()) {
            appendLine("风险提示：")
            memo.risks.forEachIndexed { index, item ->
                appendLine("${index + 1}. $item")
            }
        }
        if (memo.tags.isNotEmpty()) {
            appendLine("标签：${memo.tags.joinToString("、")}")
        }
        if (memo.sourceOutline.isNotEmpty()) {
            appendLine("输入来源：${memo.sourceOutline.joinToString(" / ")}")
        }
    }.trim()
    val roleInstruction = when (actionId) {
        "codex" -> "请把这份纪要继续整理成图表建议、表格结构、后续执行步骤，并在必要时给出可直接发送邮件的内容。"
        "claude_code" -> "请把这份纪要继续提炼成更完整的分析稿、结构化提纲和适合复盘的表格。"
        "trae" -> "请把这份纪要整理成项目任务拆解、流程图或图表说明，并明确优先级。"
        "work_buddy" -> "请把这份纪要整理成工作待办、周报素材、对外同步文案和邮件草稿。"
        "email" -> "请将下面纪要改写成一封正式但简洁的邮件。"
        else -> "请基于下面纪要继续整理成清晰的表格、图表建议和执行清单。"
    }
    return """
        你现在是 MemoMind 联动的桌面 Agent。
        
        $roleInstruction
        
        输出要求：
        1. 先给出简短总览。
        2. 再给出适合继续处理的结构化结果。
        3. 如果适合，可补充图表建议、邮件草稿或任务拆解。
        
        以下是纪要内容：
        $coreMemo
    """.trimIndent()
}

private fun chooseImageRecognitionText(
    ocrText: String,
    visionText: String,
): String {
    val cleanedOcr = cleanRecognitionCandidate(ocrText)
    val cleanedVision = cleanRecognitionCandidate(visionText)
    if (cleanedOcr.isBlank()) return cleanedVision
    if (cleanedVision.isBlank()) return cleanedOcr
    val ocrScore = scoreRecognitionText(cleanedOcr)
    val visionScore = scoreRecognitionText(cleanedVision)
    val normalizedOcr = normalizeRecognitionForCompare(cleanedOcr)
    val normalizedVision = normalizeRecognitionForCompare(cleanedVision)
    return if (
        normalizedOcr == normalizedVision ||
        normalizedVision.contains(normalizedOcr) ||
        visionScore >= maxOf(44, ocrScore - 8)
    ) {
        cleanedVision
    } else {
        cleanedOcr
    }
}

private fun cleanRecognitionCandidate(
    value: String,
): String {
    return value
        .replace(Regex("""^\[第\d+张图片识别文本]\s*"""), "")
        .replace(Regex("""^第\d+张图片识别文本\s*[:：]?\s*"""), "")
        .replace(Regex("""^(图片识别文本|OCR 文本|版面理解)\s*[:：]?\s*"""), "")
        .replace(Regex("""^\s*paper\s*$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""^\s*ocr\s*$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""[ \t]+"""), " ")
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filterNot { it.equals("paper", ignoreCase = true) || it.equals("ocr", ignoreCase = true) }
        .joinToString("\n")
        .trim()
}

private fun normalizeRecognitionForCompare(
    value: String,
): String {
    return value
        .replace(Regex("""\s+"""), "")
        .lowercase(Locale.ROOT)
}

private fun scoreRecognitionText(
    value: String,
): Int {
    val alphaNumOrCjk = value.count {
        it.isLetterOrDigit() || Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN
    }
    val lineBonus = value.lineSequence().count { it.isNotBlank() } * 10
    val noisePenalty = listOf(
        "<tool_call>",
        "</think>",
        "图片识别文本",
        "ocr",
        "paper",
    ).sumOf { token ->
        Regex(Regex.escape(token), RegexOption.IGNORE_CASE).findAll(value).count()
    } * 18
    val symbolPenalty = value.count { it in setOf('{', '}', '[', ']', '<', '>', '|', '_', '~') } * 4
    return alphaNumOrCjk + lineBonus - noisePenalty - symbolPenalty
}

private fun formatNumberedLines(
    items: List<String>,
): String {
    return items
        .filter { it.isNotBlank() }
        .mapIndexed { index, item -> "${index + 1}. ${item.trim()}" }
        .joinToString("\n")
}

private fun formatTagLines(
    tags: List<String>,
): String {
    return tags
        .filter { it.isNotBlank() }
        .joinToString("\n") { "◇ ${it.trim()}" }
}

private fun loadArchiveFolders(
    prefs: android.content.SharedPreferences,
    tasks: List<MemoTask>,
): List<String> {
    val persisted = prefs.getStringSet("archive_folders", emptySet()).orEmpty()
    val fromTasks = tasks.mapNotNull { it.archiveFolder }.toSet()
    return (persisted + fromTasks).sorted()
}

private fun saveArchiveFolders(
    prefs: android.content.SharedPreferences,
    folders: Collection<String>,
) {
    prefs.edit().putStringSet("archive_folders", folders.filter { it.isNotBlank() }.toSet()).apply()
}

private fun replaceArchiveFolder(
    prefs: android.content.SharedPreferences,
    oldName: String,
    newName: String,
) {
    val current = prefs.getStringSet("archive_folders", emptySet()).orEmpty().toMutableSet()
    current.remove(oldName)
    current.add(newName)
    prefs.edit().putStringSet("archive_folders", current).apply()
}

private fun generateArchiveFolderName(
    existing: List<String>,
): String {
    val dateLabel = SimpleDateFormat("MM月dd日", Locale.US).format(Date())
    val base = "档案 $dateLabel"
    if (base !in existing) return base
    var index = 2
    while ("$base-$index" in existing) {
        index += 1
    }
    return "$base-$index"
}
