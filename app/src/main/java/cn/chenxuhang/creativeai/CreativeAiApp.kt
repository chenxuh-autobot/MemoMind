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
import cn.chenxuhang.creativeai.core.model.DeviceProfile
import cn.chenxuhang.creativeai.core.model.InferenceBackend
import cn.chenxuhang.creativeai.core.model.MemoTask
import cn.chenxuhang.creativeai.core.model.MnnSessionConfig
import cn.chenxuhang.creativeai.core.model.ProcessingMode
import cn.chenxuhang.creativeai.core.model.SourceInputChannel
import cn.chenxuhang.creativeai.core.model.SourceInputSection
import cn.chenxuhang.creativeai.core.model.StructuredMemo
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
import cn.chenxuhang.creativeai.feature.result.ResultRoute
import cn.chenxuhang.creativeai.feature.result.ResultSectionItem
import cn.chenxuhang.creativeai.feature.result.ResultUiState
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Share
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class AppScreen(
    val label: String,
) {
    HOME("设置"),
    CAPTURE("任务"),
    HISTORY("历史"),
    RESULT("结果"),
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

@Composable
fun CreativeAiApp() {
    val context = LocalContext.current
    val storage = remember(context) { AppStorageDirectories(context) }
    val appPrefs = remember(context) {
        context.getSharedPreferences("memomind_settings", Context.MODE_PRIVATE)
    }
    val taskLocalStore = remember(storage.taskIndexFile) {
        JsonFileMemoTaskLocalDataSource(storage.taskIndexFile)
    }
    val memoLocalStore = remember(storage.memoIndexFile) {
        JsonFileStructuredMemoLocalDataSource(storage.memoIndexFile)
    }
    val modelDirectories = remember(storage) {
        mapOf(
            "qwen-vl-2b-instruct-mnn" to storage.modelDir("qwen-vl-2b-instruct-mnn"),
        )
    }
    val modelOverrides = remember {
        ModelCatalogOverrides.Empty
    }
    val modelManager = remember {
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
                modelId = "qwen-vl-2b-instruct-mnn",
                assetDirectory = "models/qwen-vl-2b-instruct-mnn",
                targetDirectory = modelDirectories.getValue("qwen-vl-2b-instruct-mnn"),
                requiredFiles = listOf(
                    "tokenizer.txt",
                    "llm.mnn",
                    "llm.mnn.weight",
                    "llm_config.json",
                    "config.json",
                    "visual.mnn",
                    "visual.mnn.weight",
                ),
                bundleVersion = "qwen-vl-2b-instruct-mnn-v1",
                bundledInApk = BuildConfig.BUNDLE_QWEN_VL_MODEL,
            ),
        )
    }
    var bundledModelResults by remember { mutableStateOf<Map<String, BundledModelBootstrapResult>>(emptyMap()) }
    var modelBootstrapEpoch by remember { mutableStateOf(0) }
    var selectedModelId by remember {
        mutableStateOf(
            appPrefs.getString("selected_model_id", "qwen-vl-2b-instruct-mnn")
                ?.takeIf { it in modelDirectories.keys }
                ?: "qwen-vl-2b-instruct-mnn",
        )
    }
    val smeCoreCount = bundledMnnBuildProfile?.cpuSmeCoreNum ?: 2
    val smeDivisionRatio = bundledMnnBuildProfile?.cpuSme2NeonDivisionRatio ?: 41
    val currentModelDirectory = remember(modelDirectories, selectedModelId) {
        modelDirectories.getValue(selectedModelId)
    }
    val currentModelSpec = remember(bundledModelSpecs, selectedModelId) {
        bundledModelSpecs.first { it.modelId == selectedModelId }
    }
    val currentModelUsesVisionPath = remember(currentModelSpec) {
        currentModelSpec.requiredFiles.any { it == "visual.mnn" || it == "visual.mnn.weight" }
    }
    val installPlan = remember(modelManager, currentModelDirectory, selectedModelId, modelBootstrapEpoch) {
        modelManager.installPlan(selectedModelId, currentModelDirectory.absolutePath)
    }
    val installStatus = remember(modelManager, currentModelDirectory, selectedModelId, modelBootstrapEpoch) {
        modelManager.validateInstallation(selectedModelId, currentModelDirectory.absolutePath)
    }
    val isBundledModelReady = bundledModelResults[selectedModelId]?.success == true
    val modelProbe = remember(mnnRuntime, currentModelDirectory, selectedModelId, modelBootstrapEpoch, isBundledModelReady, currentModelSpec.requiredFiles) {
        if (isBundledModelReady) {
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
                missingFiles = currentModelSpec.requiredFiles,
            )
        }
    }
    val generationConfig = remember(currentModelDirectory, selectedModelId, currentModelUsesVisionPath, smeCoreCount, smeDivisionRatio) {
        MnnSessionConfig(
            modelId = selectedModelId,
            modelDirectory = currentModelDirectory.absolutePath,
            backend = InferenceBackend.CPU,
            threadCount = 4,
            enableLowMemoryMode = true,
            enableMultimodalPath = currentModelUsesVisionPath,
            cpuSmeCoreCount = smeCoreCount,
            cpuSme2NeonDivisionRatio = smeDivisionRatio,
        )
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
    var draftTranscript by remember { mutableStateOf("") }
    var draftNotes by remember { mutableStateOf("") }
    val maxImageCount = remember { 8 }
    var selectedImageAssets by remember { mutableStateOf<List<SelectedLocalAsset>>(emptyList()) }
    var selectedAudioAsset by remember { mutableStateOf<SelectedLocalAsset?>(null) }
    var pendingCameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var isRunningImageSummary by remember { mutableStateOf(false) }
    var isRunningImageOcr by remember { mutableStateOf(false) }
    var isPreparingAudioTranscription by remember { mutableStateOf(false) }
    var isRunningAudioTranscription by remember { mutableStateOf(false) }
    var audioTranscriptionModeLabel by remember { mutableStateOf("端侧离线 ASR") }
    var playingAudioAssetUri by remember { mutableStateOf<String?>(null) }
    var retranscribingAudioAssetUri by remember { mutableStateOf<String?>(null) }
    var croppingImageSourceUri by remember { mutableStateOf<String?>(null) }
    var hiddenResultAssetUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var isSubmittingDraft by remember { mutableStateOf(false) }
    var draftStatusMessage by remember { mutableStateOf<String?>(null) }
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
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
                append("已选择 ${selectedImageAssets.size} 张图片。")
                if (selectedImageAssets.size >= maxImageCount) {
                    append(" 当前已达到上限 $maxImageCount 张。")
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

    LaunchedEffect(bundledModelResults, selectedModelId) {
        val selectedReady = bundledModelResults[selectedModelId]?.success == true
        if (selectedReady) return@LaunchedEffect
        val fallbackModelId = listOf(
            "qwen-vl-2b-instruct-mnn",
        ).firstOrNull { bundledModelResults[it]?.success == true } ?: return@LaunchedEffect
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
                        isBundledModelReady -> {
                            "当前模型 ${selectedModelId} 已准备完成，真正生成时才会按需加载本地会话，避免启动即吃满内存。"
                        }
                        BuildConfig.BUNDLE_QWEN_VL_MODEL -> {
                            bundledModelResults[selectedModelId]?.message ?: "首次启动正在准备本地模型，请稍候..."
                        }
                        else -> {
                            "当前默认分发的是轻量包，未内置 2GB 级本地大模型；提交任务时会自动回退到轻量纪要整理。"
                        }
                    },
                    statusLabel = when {
                        isBundledModelReady -> "按需加载"
                        BuildConfig.BUNDLE_QWEN_VL_MODEL -> "准备中"
                        else -> "轻量模式"
                    },
                    isReady = isBundledModelReady || !BuildConfig.BUNDLE_QWEN_VL_MODEL,
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
                    if (it.manifest.modelId == "qwen-vl-2b-instruct-mnn") {
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
    val archiveFolders = remember(savedTasks, taskDataEpoch) {
        loadArchiveFolders(appPrefs, savedTasks)
    }
    val historyUiState = remember(savedTasks, memoByTaskId, archiveFolders) {
        val activeTasks = savedTasks.filterNot { it.isArchived }
        val archivedTasksByFolder = savedTasks
            .filter { it.isArchived && !it.archiveFolder.isNullOrBlank() }
            .groupBy { it.archiveFolder.orEmpty() }
        val archiveGroups = archiveFolders.map { folderName ->
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
                        detail = "",
                        canRecall = task.sourceSections.isNotEmpty() || task.sourceText.isNotBlank() || !relatedMemo?.sourceOutline.isNullOrEmpty(),
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
                    detail = "",
                    canRecall = task.sourceSections.isNotEmpty() || task.sourceText.isNotBlank() || !relatedMemo?.sourceOutline.isNullOrEmpty(),
                    isArchived = false,
                )
            },
            archiveGroups = archiveGroups,
        )
    }

    val captureStatusMessage = remember(draftStatusMessage, bundledModelResults, selectedModelId, isBundledModelReady) {
        if (!isBundledModelReady) {
            if (BuildConfig.BUNDLE_QWEN_VL_MODEL) {
                bundledModelResults[selectedModelId]?.message ?: "首次启动正在准备本地模型，请稍候..."
            } else {
                draftStatusMessage
                    ?: "当前为轻量包，提交任务会先走轻量纪要整理；后续可再接入本地或云端模型。"
            }
        } else {
            draftStatusMessage
        }
    }

    val captureUiState = remember(
        draftTitle,
        draftImageBrief,
        draftOcrText,
        draftTranscript,
        draftNotes,
        selectedImageAssets,
        selectedAudioAsset,
        isRunningImageSummary,
        isRunningImageOcr,
        hasBundledSpeechRecognition,
        isBundledModelReady,
        isSubmittingDraft,
        captureStatusMessage,
    ) {
        val sourceSections = composeDraftSourceSections(
            imageBrief = draftImageBrief,
            ocrText = draftOcrText,
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
            canRunImageOcr = selectedImageAssets.isNotEmpty(),
            isRunningImageOcr = isRunningImageOcr,
            canRunImageSummary = selectedImageAssets.isNotEmpty(),
            isRunningImageSummary = isRunningImageSummary,
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

    val resultUiState = remember(latestMemo, lastExecution, playingAudioAssetUri, retranscribingAudioAssetUri, hiddenResultAssetUris) {
        ResultUiState(
            headline = "纪要结果",
            subheadline = "看看 MemoMind 刚刚帮你整理出了什么重点。",
            summary = latestMemo?.oneLineSummary,
            sections = latestMemo?.let { memo ->
                buildList {
                    add(ResultSectionItem("背景", memo.background))
                    if (memo.facts.isNotEmpty()) {
                        add(ResultSectionItem("关键要点", formatNumberedLines(memo.facts)))
                    } else if (memo.topics.isNotEmpty()) {
                        add(ResultSectionItem("关键要点", formatNumberedLines(memo.topics.map { "${it.name}: ${it.summary}" })))
                    } else if (memo.decisions.isNotEmpty()) {
                        add(ResultSectionItem("关键要点", formatNumberedLines(memo.decisions)))
                    }
                    if (memo.actionItems.isNotEmpty()) {
                        add(
                            ResultSectionItem(
                                "行动项",
                                memo.actionItems.joinToString("\n") {
                                    listOfNotNull(
                                        it.task,
                                        it.owner.takeIf { owner -> owner.isNotBlank() },
                                        it.deadline,
                                    ).joinToString(" | ")
                                },
                            ),
                        )
                    }
                    if (memo.risks.isNotEmpty()) {
                        add(ResultSectionItem("风险提示", memo.risks.joinToString("\n")))
                    }
                    if (memo.sourceOutline.isNotEmpty()) {
                        add(ResultSectionItem("输入来源", memo.sourceOutline.joinToString(" / ")))
                    }
                    if (memo.tags.isNotEmpty()) {
                        add(ResultSectionItem("标签", formatTagLines(memo.tags)))
                    }
                }
            }.orEmpty(),
            assetItems = latestMemo?.assetRefs
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
            agentActions = latestMemo?.let {
                listOf(
                    AgentActionItem(
                        id = "codex",
                        label = "复制给 Codex",
                        detail = "继续整理成图表、表格、邮件和交付物。",
                        icon = Icons.Outlined.ContentCopy,
                    ),
                    AgentActionItem(
                        id = "claude_code",
                        label = "复制给 Claude Code",
                        detail = "继续结构化分析，并生成更长文档或说明稿。",
                        icon = Icons.Outlined.ContentCopy,
                    ),
                    AgentActionItem(
                        id = "trae",
                        label = "复制给 Trae",
                        detail = "继续转换成项目任务、图示或执行方案。",
                        icon = Icons.Outlined.ContentCopy,
                    ),
                    AgentActionItem(
                        id = "work_buddy",
                        label = "复制给 Work Buddy",
                        detail = "继续生成待办、汇报和执行清单。",
                        icon = Icons.Outlined.ContentCopy,
                    ),
                    AgentActionItem(
                        id = "share",
                        label = "系统分享",
                        detail = "发到电脑端常用协作工具或聊天软件。",
                        icon = Icons.Outlined.Share,
                    ),
                    AgentActionItem(
                        id = "email",
                        label = "发到邮箱",
                        detail = "直接生成一封纪要邮件草稿。",
                        icon = Icons.Outlined.Email,
                    ),
                )
            }.orEmpty(),
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
                    onTranscriptChange = { draftTranscript = it },
                    onNotesChange = { draftNotes = it },
                    onPickImage = { imagePickerLauncher.launch(arrayOf("image/*")) },
                    onCaptureImage = {
                        if (selectedImageAssets.size >= maxImageCount) {
                            draftStatusMessage = "图片最多只能保留 $maxImageCount 张，请先清除部分图片。"
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
                        draftStatusMessage = "已清除图片文件引用。"
                    },
                    onOpenImageAsset = { assetUri ->
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
                    },
                    onRunImageSummary = {},
                    onRunImageOcr = {
                        val imageUris = selectedImageAssets.map { Uri.parse(it.uri) }
                        if (imageUris.isEmpty()) return@CaptureRoute
                        if (isRunningImageOcr) return@CaptureRoute
                        isRunningImageOcr = true
                        draftStatusMessage = if (selectedModelId in VisionEnhancedModelIds && isBundledModelReady) {
                            "正在执行图片识别，并结合视觉模型进行文字整理..."
                        } else {
                            "正在执行端侧图片 OCR..."
                        }
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    imageUris.mapIndexed { index, imageUri ->
                                        val ocr = context.runChineseImageOcr(imageUri)
                                        val visionText = if (selectedModelId in VisionEnhancedModelIds && isBundledModelReady) {
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
                            imageBrief = "",
                            ocrText = draftOcrText,
                            transcriptText = draftTranscript,
                            supplementalText = draftNotes,
                        )
                        val assetRefs = buildList {
                            addAll(selectedImageAssets.map { it.toTaskAssetRef("IMAGE") })
                            selectedAudioAsset?.let { add(it.toTaskAssetRef("AUDIO")) }
                        }
                        if (isSubmittingDraft || draftTitle.isBlank() || sourceSections.isEmpty()) return@CaptureRoute
                        draftStatusMessage = if (isBundledModelReady) {
                            "正在调用本地 Qwen 生成结构化纪要..."
                        } else {
                            "本地大模型未就绪，正在使用轻量纪要整理..."
                        }
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
                                        processingMode = if (isBundledModelReady) {
                                            ProcessingMode.LOCAL_ONLY
                                        } else {
                                            ProcessingMode.LOCAL_PREFERRED
                                        },
                                        sessionConfig = generationConfig,
                                    ),
                                )
                            }
                            lastExecution = result
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
                        draftTranscript = restoredSections.firstContent(SourceInputChannel.AUDIO_TRANSCRIPT)
                        draftNotes = restoredSections.firstContent(SourceInputChannel.SUPPLEMENTAL_TEXT)
                        selectedImageAssets = task.assetRefs
                            .parsedAssets(kindLabel = "IMAGE")
                            .map { it.toSelectedLocalAsset() }
                            .take(maxImageCount)
                        selectedAudioAsset = task.assetRefs
                            .firstParsedAsset(kindLabel = "AUDIO")
                            ?.toSelectedLocalAsset()
                        draftStatusMessage = "已回填历史输入，可继续编辑后重新生成。"
                        currentScreen = AppScreen.CAPTURE
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
                                        latestMemo?.assetRefs
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
                    onRunAgentAction = { actionId ->
                        val memo = latestMemo ?: return@ResultRoute
                        val prompt = buildAgentHandoffPrompt(
                            actionId = actionId,
                            memo = memo,
                            execution = lastExecution,
                        )
                        when (actionId) {
                            "share" -> {
                                context.sharePlainText(
                                    subject = "${memo.taskId} - MemoMind 纪要",
                                    text = prompt,
                                )
                                draftStatusMessage = "已打开系统分享，可继续发给电脑端协作工具。"
                            }
                            "email" -> {
                                context.composeEmailDraft(
                                    subject = "MemoMind 纪要协作任务",
                                    body = prompt,
                                )
                                draftStatusMessage = "已生成邮件草稿。"
                            }
                            else -> {
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
    transcriptText: String,
    supplementalText: String,
): List<SourceInputSection> {
    return listOfNotNull(
        ocrText.takeIf { it.isNotBlank() }?.let {
            SourceInputSection(
                channel = SourceInputChannel.OCR_TEXT,
                label = "图片识别文本",
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
