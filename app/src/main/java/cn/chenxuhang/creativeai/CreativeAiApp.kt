package cn.chenxuhang.creativeai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
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
import cn.chenxuhang.creativeai.ai.mnn.MnnPrebuiltLocator
import cn.chenxuhang.creativeai.ai.mnn.NativeBackedMnnRuntime
import cn.chenxuhang.creativeai.ai.mnn.StubMnnRuntime
import cn.chenxuhang.creativeai.ai.modelmanager.InMemoryModelManager
import cn.chenxuhang.creativeai.ai.modelmanager.ModelAssetOverride
import cn.chenxuhang.creativeai.ai.modelmanager.ModelCatalogOverrides
import cn.chenxuhang.creativeai.ai.modelmanager.ModelOverride
import cn.chenxuhang.creativeai.ai.orchestrator.LocalFirstMemoOrchestrator
import cn.chenxuhang.creativeai.ai.orchestrator.StructuredMemoTaskExecutionResult
import cn.chenxuhang.creativeai.ai.orchestrator.StructuredMemoTaskExecutor
import cn.chenxuhang.creativeai.ai.orchestrator.StructuredMemoTaskRequest
import cn.chenxuhang.creativeai.feature.capture.CaptureRoute
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
import cn.chenxuhang.creativeai.feature.history.HistoryRoute
import cn.chenxuhang.creativeai.feature.history.HistoryTaskItem
import cn.chenxuhang.creativeai.feature.history.HistoryUiState
import cn.chenxuhang.creativeai.feature.home.HomeRoute
import cn.chenxuhang.creativeai.feature.home.HomeUiState
import cn.chenxuhang.creativeai.feature.home.ModelInstallItem
import cn.chenxuhang.creativeai.feature.home.ReadinessItem
import cn.chenxuhang.creativeai.feature.home.TaskRecordItem
import cn.chenxuhang.creativeai.feature.result.ResultAssetItem
import cn.chenxuhang.creativeai.feature.result.ResultRoute
import cn.chenxuhang.creativeai.feature.result.ResultSectionItem
import cn.chenxuhang.creativeai.feature.result.ResultUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppScreen(
    val label: String,
) {
    HOME("Home"),
    CAPTURE("Capture"),
    HISTORY("History"),
    RESULT("Result"),
}

data class SelectedLocalAsset(
    val uri: String,
    val displayName: String,
    val mimeTypeLabel: String,
)

@Composable
fun CreativeAiApp() {
    val context = LocalContext.current
    val storage = remember(context) { AppStorageDirectories(context) }
    val taskLocalStore = remember(storage.taskIndexFile) {
        JsonFileMemoTaskLocalDataSource(storage.taskIndexFile)
    }
    val memoLocalStore = remember(storage.memoIndexFile) {
        JsonFileStructuredMemoLocalDataSource(storage.memoIndexFile)
    }
    val bootstrapModelDirectory = remember(storage) {
        resolveBootstrapModelDirectory(storage.modelDir("qwen-local-1_5b-text"))
    }
    val modelOverrides = remember {
        ModelCatalogOverrides(
            models = mapOf(
                "qwen-local-1_5b-text" to ModelOverride(
                    assets = mapOf(
                        "tokenizer.txt" to ModelAssetOverride(
                            downloadUrl = BuildConfig.QWEN_1_5B_TEXT_TOKENIZER_TXT_URL,
                            sha256 = BuildConfig.QWEN_1_5B_TEXT_TOKENIZER_TXT_SHA256,
                        ),
                        "llm.mnn" to ModelAssetOverride(
                            downloadUrl = BuildConfig.QWEN_1_5B_TEXT_LLM_MNN_URL,
                            sha256 = BuildConfig.QWEN_1_5B_TEXT_LLM_MNN_SHA256,
                        ),
                        "llm.mnn.weight" to ModelAssetOverride(
                            downloadUrl = BuildConfig.QWEN_1_5B_TEXT_LLM_WEIGHT_URL,
                            sha256 = BuildConfig.QWEN_1_5B_TEXT_LLM_WEIGHT_SHA256,
                        ),
                        "llm_config.json" to ModelAssetOverride(
                            downloadUrl = BuildConfig.QWEN_1_5B_TEXT_LLM_CONFIG_URL,
                            sha256 = BuildConfig.QWEN_1_5B_TEXT_LLM_CONFIG_SHA256,
                        ),
                        "config.json" to ModelAssetOverride(
                            downloadUrl = BuildConfig.QWEN_1_5B_TEXT_CONFIG_URL,
                            sha256 = BuildConfig.QWEN_1_5B_TEXT_CONFIG_SHA256,
                        ),
                    ),
                ),
                "qwen-local-3b-multimodal" to ModelOverride(
                    assets = mapOf(
                        "tokenizer.json" to ModelAssetOverride(
                            downloadUrl = BuildConfig.QWEN_3B_MM_TOKENIZER_URL,
                            sha256 = BuildConfig.QWEN_3B_MM_TOKENIZER_SHA256,
                        ),
                        "model.mnn" to ModelAssetOverride(
                            downloadUrl = BuildConfig.QWEN_3B_MM_MODEL_URL,
                            sha256 = BuildConfig.QWEN_3B_MM_MODEL_SHA256,
                        ),
                        "config.json" to ModelAssetOverride(
                            downloadUrl = BuildConfig.QWEN_3B_MM_CONFIG_URL,
                            sha256 = BuildConfig.QWEN_3B_MM_CONFIG_SHA256,
                        ),
                    ),
                ),
            ),
        )
    }
    val modelManager = remember {
        InMemoryModelManager.bootstrapDefaults(overrides = modelOverrides).apply {
            markInstalled(
                modelId = "qwen-local-1_5b-text",
                localDirectory = bootstrapModelDirectory.absolutePath,
            )
        }
    }
    val orchestrator = remember { LocalFirstMemoOrchestrator(modelManager) }
    val mnnRuntime = remember { NativeBackedMnnRuntime() }
    val fallbackRuntime = remember { StubMnnRuntime() }
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
    val prebuiltLayout = remember {
        MnnPrebuiltLocator.inspect(
            rootDirectory = "ai/mnn/src/main/cpp/third_party/mnn",
        )
    }
    val installPlan = remember(modelManager, bootstrapModelDirectory) {
        modelManager.installPlan("qwen-local-1_5b-text", bootstrapModelDirectory.absolutePath)
    }
    val installStatus = remember(modelManager, bootstrapModelDirectory) {
        modelManager.validateInstallation("qwen-local-1_5b-text", bootstrapModelDirectory.absolutePath)
    }
    val modelProbe = remember(mnnRuntime, bootstrapModelDirectory) {
        mnnRuntime.probeModelDirectory(
            modelId = "qwen-local-1_5b-text",
            modelDirectory = bootstrapModelDirectory.absolutePath,
        )
    }
    val sessionOpen = remember(mnnRuntime, bootstrapModelDirectory) {
        mnnRuntime.openSession(
            MnnSessionConfig(
                modelId = "qwen-local-1_5b-text",
                modelDirectory = bootstrapModelDirectory.absolutePath,
                backend = InferenceBackend.CPU,
                threadCount = 4,
                enableLowMemoryMode = true,
                enableMultimodalPath = false,
            ),
        )
    }
    val generationConfig = remember(bootstrapModelDirectory) {
        MnnSessionConfig(
            modelId = "qwen-local-1_5b-text",
            modelDirectory = bootstrapModelDirectory.absolutePath,
            backend = InferenceBackend.CPU,
            threadCount = 4,
            enableLowMemoryMode = true,
            enableMultimodalPath = false,
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
    var draftTitle by remember { mutableStateOf("Creative AI Android 头脑风暴纪要") }
    var draftImageBrief by remember { mutableStateOf(defaultDraftImageBrief()) }
    var draftOcrText by remember { mutableStateOf(defaultDraftOcrText()) }
    var draftTranscript by remember { mutableStateOf(defaultDraftTranscript()) }
    var draftNotes by remember { mutableStateOf(defaultDraftNotes()) }
    var selectedImageAsset by remember { mutableStateOf<SelectedLocalAsset?>(null) }
    var selectedAudioAsset by remember { mutableStateOf<SelectedLocalAsset?>(null) }
    var isRunningImageSummary by remember { mutableStateOf(false) }
    var isRunningImageOcr by remember { mutableStateOf(false) }
    var isRunningAudioTranscription by remember { mutableStateOf(false) }
    var audioTranscriptionModeLabel by remember { mutableStateOf("本地优先") }
    var playingAudioAssetUri by remember { mutableStateOf<String?>(null) }
    var retranscribingAudioAssetUri by remember { mutableStateOf<String?>(null) }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var isSubmittingDraft by remember { mutableStateOf(false) }
    var draftStatusMessage by remember { mutableStateOf<String?>(null) }
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    val scope = rememberCoroutineScope()
    val audioRecorder = remember(storage.recordingsDir) {
        AudioNoteRecorder(
            outputDirectory = storage.recordingsDir,
        )
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
            onTranscript = { update ->
                draftTranscript = update.text
            },
            onStateChanged = { state ->
                if (!state.isRunning && audioRecorder.isRecording) {
                    selectedAudioAsset = audioRecorder.finishRecording() ?: selectedAudioAsset
                }
                isRunningAudioTranscription = state.isRunning
                audioTranscriptionModeLabel = state.modeLabel
            },
            onStatusMessage = { message ->
                draftStatusMessage = message
            },
        )
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        selectedImageAsset = uri?.let {
            context.persistReadPermission(it)
            context.resolveSelectedLocalAsset(it)
        }
        if (uri != null) {
            draftStatusMessage = "图片文件已选择，可继续补充图片描述或 OCR 文本。"
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
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            runCatching {
                audioRecorder.startRecording()
                speechTranscriber.startListening()
            }.onFailure { error ->
                audioRecorder.cancelRecording()
                draftStatusMessage = error.message ?: "无法启动录音与转写。"
            }
        } else {
            draftStatusMessage = "未授予录音权限，无法启动麦克风转写。"
        }
    }

    DisposableEffect(speechTranscriber, audioRecorder, audioPreviewPlayer) {
        onDispose {
            speechTranscriber.destroy()
            audioRecorder.cancelRecording()
            audioPreviewPlayer.stop()
        }
    }

    LaunchedEffect(sessionOpen.success, generationConfig.modelDirectory) {
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
        } else if (!sessionOpen.success) {
            StructuredMemoTaskExecutionResult(
                task = MemoTask(
                    id = "blocked",
                    title = "本地纪要任务",
                    type = "structured_memo",
                    status = "BLOCKED",
                    summary = sessionOpen.errorMessage ?: "session open failed",
                ),
                errorMessage = sessionOpen.errorMessage ?: "session open failed",
            )
        } else {
            withContext(Dispatchers.IO) {
                taskExecutor.execute(
                    StructuredMemoTaskRequest(
                        title = "Creative AI Android 头脑风暴纪要",
                        type = "brainstorm_memo",
                        processingMode = ProcessingMode.LOCAL_ONLY,
                        sessionConfig = generationConfig,
                        sourceText = """
                            项目名称：Creative AI Android
                            会议类型：产品头脑风暴
                            参会角色：产品、设计、Android、算法
                            原始记录：
                            1. 产品方向聚焦“图片、录音、文字一键生成结构化纪要”，优先解决创意讨论和灵感整理场景。
                            2. 首版坚持本地优先，核心交互必须支持端侧运行，云端只做补充和兜底。
                            3. 模型路线先从 Qwen3.5-0.8B MNN 包跑通文本纪要，再逐步扩展到更大模型和多模态模型。
                            4. 工程上已经接入 MNN JNI，需要下一步验证真实 prompt 到 JSON 输出是否稳定。
                            5. 设计上希望结果页不是单纯摘要，而是分成主题、结论、行动项、风险和引用。
                            6. Android 同学担心端侧内存压力，建议默认低内存模式并优先 CPU 路线，后面再评估 NNAPI。
                            7. 算法同学建议把 OCR、ASR、图片理解拆成前处理层，先统一汇总成文本上下文，再进入本地 Qwen 文本模型。
                            8. 本周目标是先打通一条可演示的本地链路，用来参加答辩演示。
                        """.trimIndent(),
                    ),
                )
            }
        }
    }

    val savedTasks = remember(taskLocalStore, lastExecution) {
        taskLocalStore.getAll().asReversed()
    }
    val savedMemos = remember(memoLocalStore, lastExecution) {
        memoLocalStore.getAll().asReversed()
    }
    val latestMemo = savedMemos.firstOrNull()

    val homeUiState = remember(
        storage,
        modelManager,
        plan,
        mnnRuntime,
        fallbackRuntime,
        capabilityReport,
        prebuiltLayout,
        installPlan,
        installStatus,
        modelProbe,
        sessionOpen,
        savedTasks,
        latestMemo,
        lastExecution,
    ) {
        HomeUiState(
            headline = "Creative AI Android",
            subheadline = "本地 Qwen 优先，云端仅辅助。当前工程已经具备真实 MNN-LLM 会话打开能力，并开始尝试端侧结构化纪要生成。",
            readinessItems = listOf(
                ReadinessItem("本地模型目录", storage.modelsDir.absolutePath),
                ReadinessItem("当前演示模型目录", bootstrapModelDirectory.absolutePath),
                ReadinessItem("纪要缓存目录", storage.memosDir.absolutePath),
                ReadinessItem("录音素材目录", storage.recordingsDir.absolutePath),
                ReadinessItem("任务索引文件", storage.taskIndexFile.absolutePath),
                ReadinessItem("纪要索引文件", storage.memoIndexFile.absolutePath),
                ReadinessItem("任务数据源", taskLocalStore.describe()),
                ReadinessItem("纪要数据源", memoLocalStore.describe()),
                ReadinessItem("MNN 运行时", mnnRuntime.describe()),
                ReadinessItem("MNN 兜底运行时", fallbackRuntime.describe()),
                ReadinessItem("MNN 版本", mnnRuntime.runtimeVersion()),
                ReadinessItem("真实 MNN 已链接", mnnRuntime.supportsRealMnn().toString()),
                ReadinessItem("MNN 期望 ABI", MnnPrebuiltLocator.expectedAbis().joinToString()),
                ReadinessItem(
                    "MNN 预编译布局",
                    "include=${prebuiltLayout.includeDirectoryExists}, available=${prebuiltLayout.availableAbis.joinToString()}, missing=${prebuiltLayout.missingAbis.joinToString()}",
                ),
                ReadinessItem(
                    "本地资源覆盖",
                    "1.5B llm=${BuildConfig.QWEN_1_5B_TEXT_LLM_MNN_URL.ifBlank { "unset" }}",
                ),
                ReadinessItem("设备档位", capabilityReport.deviceTier.name),
                ReadinessItem("执行路径", "${plan.path}: ${plan.reason}"),
                ReadinessItem(
                    "模型安装计划",
                    "assets=${installPlan?.requiredAssets?.size ?: 0}, bytes=${installPlan?.requiredFreeBytes ?: 0}",
                ),
                ReadinessItem("模型安装状态", installStatus.name),
                ReadinessItem(
                    "模型目录探测",
                    "exists=${modelProbe.exists}, tokenizer=${modelProbe.hasTokenizer}, weights=${modelProbe.hasWeights}, config=${modelProbe.hasConfig}, missing=${modelProbe.missingFiles.joinToString()}",
                ),
                ReadinessItem(
                    "会话打开结果",
                    "success=${sessionOpen.success}, backend=${sessionOpen.backendName}, error=${sessionOpen.errorMessage ?: "none"}",
                ),
            ),
            installedModels = modelManager.installedModels().map {
                ModelInstallItem(
                    title = it.manifest.displayName,
                    status = it.installStatus.name,
                    detail = "${it.manifest.modelId} | ${it.manifest.estimatedStorageBytes / 1_000_000}MB | ${it.localDirectory ?: "pending"}",
                )
            },
            nextMilestones = listOf(
                "把单轮 JSON 生成升级成可复用会话与流式输出",
                "接入 OCR / ASR 前处理，把图片和音频统一转成文本上下文",
                "把结构化纪要保存到本地任务流并接结果页",
            ),
            recentTasks = savedTasks.take(3).map { task ->
                TaskRecordItem(
                    title = task.title,
                    status = task.status,
                    detail = "${task.type} | ${task.processingMode.name} | ${task.summary.ifBlank { "暂无摘要" }}",
                )
            },
            latestMemoItems = latestMemo?.let { memo ->
                listOf(
                    ReadinessItem("一句话总结", memo.oneLineSummary),
                    ReadinessItem("背景", memo.background),
                    ReadinessItem("主题数", memo.topics.size.toString()),
                    ReadinessItem("结论数", memo.decisions.size.toString()),
                    ReadinessItem("行动项数", memo.actionItems.size.toString()),
                    ReadinessItem("标签", memo.tags.joinToString()),
                )
            }.orEmpty(),
            latestRawOutput = latestMemo?.rawJson ?: lastExecution.rawOutput?.take(1600),
        )
    }

    val historyUiState = remember(savedTasks) {
        HistoryUiState(
            headline = "任务历史",
            subheadline = "查看最近的端侧纪要任务执行记录。",
            tasks = savedTasks.map { task ->
                HistoryTaskItem(
                    title = task.title,
                    status = task.status,
                    summary = task.summary.ifBlank { "暂无摘要" },
                    detail = buildList {
                        add(task.type)
                        add(task.processingMode.name)
                        if (task.sourceChannels.isNotEmpty()) {
                            add(task.sourceChannels.joinToString(" + "))
                        }
                        if (task.assetRefs.isNotEmpty()) {
                            add("assets=${task.assetRefs.size}")
                        }
                    }.joinToString(" | "),
                )
            },
        )
    }

    val captureUiState = remember(
        draftTitle,
        draftImageBrief,
        draftOcrText,
        draftTranscript,
        draftNotes,
        selectedImageAsset,
        selectedAudioAsset,
        isRunningImageSummary,
        isRunningImageOcr,
        isSubmittingDraft,
        draftStatusMessage,
    ) {
        val sourceSections = composeDraftSourceSections(
            imageBrief = draftImageBrief,
            ocrText = draftOcrText,
            transcriptText = draftTranscript,
            supplementalText = draftNotes,
        )
        CaptureUiState(
            headline = "提交纪要任务",
            subheadline = "把图片说明、OCR、录音转写和补充文字统一汇总，再交给本地 Qwen 生成结构化纪要。",
            titleInput = draftTitle,
            imageBriefInput = draftImageBrief,
            ocrTextInput = draftOcrText,
            transcriptInput = draftTranscript,
            notesInput = draftNotes,
            selectedImageAssetLabel = selectedImageAsset?.displayName,
            selectedAudioAssetLabel = selectedAudioAsset?.displayName,
            composedPreview = composeUnifiedSourceText(sourceSections),
            activeSourceLabels = sourceSections.map { it.label },
            canRunImageOcr = selectedImageAsset != null,
            isRunningImageOcr = isRunningImageOcr,
            canRunImageSummary = selectedImageAsset != null,
            isRunningImageSummary = isRunningImageSummary,
            canStartAudioTranscription = hasAudioPermission || !isRunningAudioTranscription,
            canStopAudioTranscription = isRunningAudioTranscription,
            isRunningAudioTranscription = isRunningAudioTranscription,
            audioTranscriptionModeLabel = audioTranscriptionModeLabel,
            isSubmitting = isSubmittingDraft,
            submitEnabled = draftTitle.isNotBlank() && sourceSections.isNotEmpty(),
            statusMessage = draftStatusMessage,
        )
    }

    val resultUiState = remember(latestMemo, lastExecution, playingAudioAssetUri, retranscribingAudioAssetUri) {
        ResultUiState(
            headline = "纪要结果",
            subheadline = "查看最近一次结构化纪要输出。",
            summary = latestMemo?.oneLineSummary,
            sections = latestMemo?.let { memo ->
                buildList {
                    add(ResultSectionItem("背景", memo.background))
                    if (memo.topics.isNotEmpty()) {
                        add(ResultSectionItem("主题", memo.topics.joinToString("\n") { "${it.name}: ${it.summary}" }))
                    }
                    if (memo.facts.isNotEmpty()) {
                        add(ResultSectionItem("事实", memo.facts.joinToString("\n")))
                    }
                    if (memo.decisions.isNotEmpty()) {
                        add(ResultSectionItem("结论", memo.decisions.joinToString("\n")))
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
                    if (memo.sourceOutline.isNotEmpty()) {
                        add(ResultSectionItem("输入来源", memo.sourceOutline.joinToString("\n\n")))
                    }
                    if (memo.risks.isNotEmpty()) {
                        add(ResultSectionItem("风险", memo.risks.joinToString("\n")))
                    }
                    if (memo.quotes.isNotEmpty()) {
                        add(ResultSectionItem("引用", memo.quotes.joinToString("\n")))
                    }
                    if (memo.tags.isNotEmpty()) {
                        add(ResultSectionItem("标签", memo.tags.joinToString()))
                    }
                }
            }.orEmpty(),
            assetItems = latestMemo?.assetRefs
                ?.mapNotNull(::parseAssetRef)
                ?.map { asset ->
                    ResultAssetItem(
                        kindLabel = asset.kindLabel,
                        displayName = asset.displayName,
                        detail = asset.mimeTypeLabel,
                        uri = asset.uri,
                        isPlayableAudio = asset.kindLabel == "AUDIO",
                        isPlaying = playingAudioAssetUri == asset.uri,
                        canRetranscribeAudio = asset.kindLabel == "AUDIO" && Build.VERSION.SDK_INT >= 33,
                        isRetranscribing = retranscribingAudioAssetUri == asset.uri,
                    )
                }
                .orEmpty(),
            rawJson = latestMemo?.rawJson ?: lastExecution.rawOutput,
        )
    }

    Scaffold(
        topBar = {
            Surface(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppScreen.entries.forEach { screen ->
                        if (currentScreen == screen) {
                            FilledTonalButton(
                                onClick = { currentScreen = screen },
                            ) {
                                Text(screen.label)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { currentScreen = screen },
                            ) {
                                Text(screen.label)
                            }
                        }
                    }
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
                    onClearImage = {
                        selectedImageAsset = null
                        draftStatusMessage = "已清除图片文件引用。"
                    },
                    onRunImageSummary = {
                        val imageUri = selectedImageAsset?.uri?.let(Uri::parse) ?: return@CaptureRoute
                        if (isRunningImageSummary) return@CaptureRoute
                        isRunningImageSummary = true
                        draftStatusMessage = "正在提取图片语义要点..."
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    context.extractImageSemanticSummary(imageUri)
                                }
                            }
                            isRunningImageSummary = false
                            result.onSuccess { summary ->
                                if (summary.summaryText.isNotBlank()) {
                                    draftImageBrief = "图片中重点元素: ${summary.summaryText}"
                                    draftStatusMessage = "图片要点提取完成，已回填到图片内容补充。"
                                } else {
                                    draftStatusMessage = "图片要点提取完成，但没有拿到稳定标签。"
                                }
                            }.onFailure { error ->
                                draftStatusMessage = error.message ?: "图片要点提取失败"
                            }
                        }
                    },
                    onRunImageOcr = {
                        val imageUri = selectedImageAsset?.uri?.let(Uri::parse) ?: return@CaptureRoute
                        if (isRunningImageOcr) return@CaptureRoute
                        isRunningImageOcr = true
                        draftStatusMessage = "正在执行端侧图片 OCR..."
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    context.runChineseImageOcr(imageUri)
                                }
                            }
                            isRunningImageOcr = false
                            result.onSuccess { ocr ->
                                draftOcrText = ocr.text
                                draftStatusMessage = if (ocr.text.isBlank()) {
                                    "OCR 已完成，但没有识别到可用文字。"
                                } else {
                                    "OCR 已完成，识别到 ${ocr.blockCount} 个文本块，结果已回填到 OCR 文本。"
                                }
                            }.onFailure { error ->
                                draftStatusMessage = error.message ?: "OCR 执行失败"
                            }
                        }
                    },
                    onPickAudio = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                    onClearAudio = {
                        selectedAudioAsset = null
                        draftStatusMessage = "已清除录音文件引用。"
                    },
                    onStartAudioTranscription = {
                        if (!SpeechRecognitionAvailability.isAnyRecognitionAvailable(context)) {
                            draftStatusMessage = "当前设备没有可用的语音识别服务。"
                            return@CaptureRoute
                        }
                        if (hasAudioPermission) {
                            runCatching {
                                audioRecorder.startRecording()
                                speechTranscriber.startListening()
                            }.onSuccess {
                                draftStatusMessage = "已开始录音并进行语音转写。"
                            }.onFailure { error ->
                                audioRecorder.cancelRecording()
                                draftStatusMessage = error.message ?: "无法启动录音与转写。"
                            }
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onStopAudioTranscription = {
                        speechTranscriber.stopListening()
                        draftStatusMessage = "正在停止录音并整理转写结果..."
                    },
                    onSubmit = {
                        val sourceSections = composeDraftSourceSections(
                            imageBrief = draftImageBrief,
                            ocrText = draftOcrText,
                            transcriptText = draftTranscript,
                            supplementalText = draftNotes,
                        )
                        val assetRefs = listOfNotNull(
                            selectedImageAsset?.toTaskAssetRef("IMAGE"),
                            selectedAudioAsset?.toTaskAssetRef("AUDIO"),
                        )
                        if (isSubmittingDraft || draftTitle.isBlank() || sourceSections.isEmpty()) return@CaptureRoute
                        draftStatusMessage = "正在调用本地 Qwen 生成结构化纪要..."
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
                                        processingMode = ProcessingMode.LOCAL_ONLY,
                                        sessionConfig = generationConfig,
                                    ),
                                )
                            }
                            lastExecution = result
                            isSubmittingDraft = false
                            if (result.memo != null) {
                                draftStatusMessage = "纪要生成完成，已写入本地任务流。"
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
                    modifier = Modifier.fillMaxSize(),
                )
                AppScreen.RESULT -> ResultRoute(
                    uiState = resultUiState,
                    onToggleAudioPlayback = { assetUri ->
                        audioPreviewPlayer.togglePlayback(assetUri)
                    },
                    onRetranscribeAudio = { assetUri ->
                        if (Build.VERSION.SDK_INT < 33) {
                            draftStatusMessage = "音频文件级重跑转写需要 Android 13 及以上系统。"
                        } else if (retranscribingAudioAssetUri == null) {
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
        imageBrief.takeIf { it.isNotBlank() }?.let {
            SourceInputSection(
                channel = SourceInputChannel.IMAGE_BRIEF,
                label = "图片内容补充",
                content = it.trim(),
            )
        },
        ocrText.takeIf { it.isNotBlank() }?.let {
            SourceInputSection(
                channel = SourceInputChannel.OCR_TEXT,
                label = "OCR 文本",
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

private fun defaultDraftImageBrief(): String {
    return """
        白板拍照里有三块内容：端侧优先、云端兜底、结构化纪要；右侧还画了“图片 + 录音 + 文字 -> JSON 纪要”的流程箭头。
    """.trimIndent()
}

private fun defaultDraftOcrText(): String {
    return """
        推荐技术栈：Qwen3.5-0.8B/2B/4B，Qwen3-VL-2B/4B；推荐推理框架：MNN；推荐工具：Qwen Code。
    """.trimIndent()
}

private fun defaultDraftTranscript(): String {
    return """
        我们先不要等完整多模态模型，把图片理解、OCR、录音转写都当成前处理层，最后统一变成文本上下文，再进本地 Qwen 做纪要。
    """.trimIndent()
}

private fun defaultDraftNotes(): String {
    return """
        项目名称：Creative AI Android
        会议类型：创意产品讨论
        参会角色：产品、设计、Android、算法
        原始记录：
        1. 我们希望用户可以把图片、录音和文字一起丢进来，一键得到结构化纪要。
        2. 首版优先做头脑风暴和会议场景，结果页需要同时给出总结、结论、行动项和风险。
        3. 技术上坚持本地优先，核心链路必须在手机端运行，云端只做增强。
        4. 当前已经接入 Qwen + MNN，需要继续验证真机上的稳定性和生成质量。
        5. 下一步要把文字输入、历史页和结果页都串起来，形成可演示闭环。
    """.trimIndent()
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
