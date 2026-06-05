package cn.chenxuhang.creativeai.feature.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class CaptureAssetItem(
    val displayName: String,
    val uri: String,
)

data class CaptureUiState(
    val headline: String,
    val subheadline: String,
    val titleInput: String,
    val showTitleRequiredHint: Boolean,
    val imageBriefInput: String,
    val ocrTextInput: String,
    val transcriptInput: String,
    val notesInput: String,
    val maxImageCount: Int,
    val selectedImageAssets: List<CaptureAssetItem> = emptyList(),
    val selectedAudioAssetLabel: String? = null,
    val composedPreview: String,
    val activeSourceLabels: List<String>,
    val canRunImageOcr: Boolean,
    val isRunningImageOcr: Boolean,
    val canRunImageSummary: Boolean,
    val isRunningImageSummary: Boolean,
    val canToggleAudioTranscription: Boolean,
    val isPreparingAudioTranscription: Boolean,
    val isRunningAudioTranscription: Boolean,
    val audioTranscriptionModeLabel: String,
    val audioToggleLabel: String,
    val isSubmitting: Boolean,
    val submitEnabled: Boolean,
    val statusMessage: String? = null,
)

@Composable
fun CaptureRoute(
    uiState: CaptureUiState,
    onTitleChange: (String) -> Unit,
    onImageBriefChange: (String) -> Unit,
    onOcrTextChange: (String) -> Unit,
    onTranscriptChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onCaptureImage: () -> Unit,
    onClearImage: () -> Unit,
    onOpenImageAsset: (String) -> Unit,
    onRunImageSummary: () -> Unit,
    onRunImageOcr: () -> Unit,
    onPickAudio: () -> Unit,
    onClearAudio: () -> Unit,
    onToggleAudioTranscription: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var ocrExpanded by rememberSaveable { mutableStateOf(false) }
    var transcriptExpanded by rememberSaveable { mutableStateOf(false) }
    var contextExpanded by rememberSaveable { mutableStateOf(false) }
    Surface(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = uiState.headline,
                        style = MaterialTheme.typography.headlineMedium,
                        letterSpacing = 1.8.sp,
                    )
                }
            }

            item {
                CaptureModuleCard(
                    title = "任务标题",
                    accent = MaterialTheme.colorScheme.primary,
                    icon = Icons.Outlined.DriveFileRenameOutline,
                    badge = "必填",
                ) {
                    OutlinedTextField(
                        value = uiState.titleInput,
                        onValueChange = onTitleChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("任务标题（必填）") },
                        placeholder = { Text("例如：品牌头脑风暴纪要 / 客户访谈整理") },
                        isError = uiState.showTitleRequiredHint,
                        singleLine = true,
                        supportingText = {
                            Text(
                                if (uiState.showTitleRequiredHint) {
                                    "请先填写任务标题，填写后才可以生成结构化纪要。"
                                } else {
                                    "用一句话说明这次任务是什么，后续回看会更省时间。"
                                },
                            )
                        },
                    )
                }
            }

            item {
                CaptureModuleCard(
                    title = "图片素材",
                    accent = Color(0xFF2F7BF6),
                    icon = Icons.Outlined.Collections,
                    badge = "最多 ${uiState.maxImageCount} 张",
                ) {
                    Text(
                        text = "可上传多张图片，或直接现场拍摄。MemoMind 会提取图片中的文字内容，供后续纪要整理使用。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DualActionRow(
                        primaryLabel = if (uiState.selectedImageAssets.isEmpty()) {
                            "选择图片文件"
                        } else {
                            "继续添加图片"
                        },
                        primaryIcon = Icons.Outlined.ImageSearch,
                        onPrimaryClick = onPickImage,
                        secondaryLabel = "使用摄像头拍摄图片",
                        secondaryIcon = Icons.Outlined.CameraAlt,
                        onSecondaryClick = onCaptureImage,
                    )
                    if (uiState.selectedImageAssets.isNotEmpty()) {
                        AssetActionList(
                            title = "已选图片",
                            items = uiState.selectedImageAssets,
                            onOpen = onOpenImageAsset,
                            actionLabel = "查看",
                        )
                        OutlinedButton(
                            onClick = onRunImageOcr,
                            enabled = uiState.canRunImageOcr && !uiState.isRunningImageOcr,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (uiState.isRunningImageOcr) "正在识别图片文本..." else "从图片识别文本")
                        }
                        OutlinedButton(
                            onClick = onClearImage,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("清除全部图片素材")
                        }
                    }
                    CollapsibleTextEditor(
                        title = "图片识别文本",
                        value = uiState.ocrTextInput,
                        expanded = ocrExpanded,
                        onToggle = { ocrExpanded = !ocrExpanded },
                        onValueChange = onOcrTextChange,
                        placeholder = "这里显示图片中识别出的文字内容。",
                        supportingText = "图片文字识别结果会保留在这个模块里，方便单独检查和修正。",
                    )
                }
            }

            item {
                CaptureModuleCard(
                    title = "音频素材",
                    accent = Color(0xFF0FA37F),
                    icon = Icons.Outlined.GraphicEq,
                    badge = "本地识别",
                ) {
                    Text(
                        text = "可选择已有录音，也可直接调用麦克风做端侧转写。中途启动会有短暂准备时间。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = onPickAudio,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (uiState.selectedAudioAssetLabel == null) "选择录音文件" else "更换录音文件")
                    }
                    if (uiState.selectedAudioAssetLabel != null) {
                        AssetSummaryBlock(
                            title = "已选录音",
                            lines = listOf(uiState.selectedAudioAssetLabel),
                        )
                        OutlinedButton(
                            onClick = onClearAudio,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("清除录音文件")
                        }
                    }
                    AudioToggleButton(
                        label = uiState.audioToggleLabel,
                        modeLabel = uiState.audioTranscriptionModeLabel,
                        isPreparing = uiState.isPreparingAudioTranscription,
                        isRunning = uiState.isRunningAudioTranscription,
                        enabled = uiState.canToggleAudioTranscription,
                        onClick = onToggleAudioTranscription,
                    )
                    CollapsibleTextEditor(
                        title = "音频转写文本",
                        value = uiState.transcriptInput,
                        expanded = transcriptExpanded,
                        onToggle = { transcriptExpanded = !transcriptExpanded },
                        onValueChange = onTranscriptChange,
                        placeholder = "麦克风或录音文件提取出的文字会显示在这里。",
                        supportingText = "音频相关文本只保留在这个模块里，方便单独核对。",
                    )
                }
            }

            item {
                CaptureModuleCard(
                    title = "补充文字",
                    accent = Color(0xFFE67B16),
                    icon = Icons.Outlined.EditNote,
                ) {
                    Text(
                        text = "强力建议补充该任务的背景、目标、约束等。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = uiState.notesInput,
                        onValueChange = onNotesChange,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6,
                        label = { Text("补充文字") },
                        placeholder = { Text("例如：这次讨论更关注结论、行动项和负责人。") },
                    )
                }
            }

            item {
                CaptureModuleCard(
                    title = "上下文汇总",
                    accent = Color(0xFF8457E6),
                    icon = Icons.Outlined.Summarize,
                ) {
                    if (uiState.activeSourceLabels.isNotEmpty()) {
                        AssetSummaryBlock(
                            title = "当前已接入来源",
                            lines = uiState.activeSourceLabels,
                        )
                    }
                    CollapsibleTextEditor(
                        title = "统一上下文预览",
                        value = uiState.composedPreview,
                        expanded = contextExpanded,
                        onToggle = { contextExpanded = !contextExpanded },
                        onValueChange = {},
                        placeholder = "填写上面的素材后，这里会自动汇总成本次纪要的完整上下文。",
                        supportingText = "生成前可以在这里快速检查：图片、音频和补充文字是否都已进入纪要上下文。",
                        readOnly = true,
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!uiState.statusMessage.isNullOrBlank()) {
                        Text(
                            text = uiState.statusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = onSubmit,
                        enabled = uiState.submitEnabled && !uiState.isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                    ) {
                        Text(if (uiState.isSubmitting) "正在生成..." else "生成结构化纪要")
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsibleTextEditor(
    title: String,
    value: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onValueChange: (String) -> Unit,
    placeholder: String,
    supportingText: String,
    readOnly: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(16.dp),
                )
                .clickable { onToggle() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    letterSpacing = 0.6.sp,
                )
                Text(
                    text = if (value.isBlank()) "当前为空" else "当前已有内容",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "收起$title" else "展开$title",
            )
        }
        if (expanded) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                label = { Text(title) },
                placeholder = { Text(placeholder) },
                supportingText = { Text(supportingText) },
                readOnly = readOnly,
            )
        }
    }
}

@Composable
private fun CaptureModuleCard(
    title: String,
    accent: Color,
    icon: ImageVector,
    badge: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(accent.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accent,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        letterSpacing = 1.2.sp,
                    )
                    badge?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                        )
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun DualActionRow(
    primaryLabel: String,
    primaryIcon: ImageVector,
    onPrimaryClick: () -> Unit,
    secondaryLabel: String,
    secondaryIcon: ImageVector,
    onSecondaryClick: () -> Unit,
    primaryEnabled: Boolean = true,
    secondaryEnabled: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = onPrimaryClick,
            enabled = primaryEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(primaryIcon, contentDescription = primaryLabel, modifier = Modifier.size(18.dp))
            Text(
                text = primaryLabel,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        OutlinedButton(
            onClick = onSecondaryClick,
            enabled = secondaryEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(secondaryIcon, contentDescription = secondaryLabel, modifier = Modifier.size(18.dp))
            Text(
                text = secondaryLabel,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun AssetSummaryBlock(
    title: String,
    lines: List<String>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.6.sp,
        )
        Text(
            text = lines.joinToString("\n"),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AssetActionList(
    title: String,
    items: List<CaptureAssetItem>,
    onOpen: (String) -> Unit,
    actionLabel: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.6.sp,
        )
        items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = item.displayName,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedButton(onClick = { onOpen(item.uri) }) {
                    Icon(
                        imageVector = Icons.Outlined.OpenInNew,
                        contentDescription = actionLabel,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = actionLabel,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioToggleButton(
    label: String,
    modeLabel: String,
    isPreparing: Boolean,
    isRunning: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val buttonText = when {
        isPreparing -> "正在启动麦克风，请稍候..."
        isRunning -> "停止麦克风转写"
        else -> label
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isPreparing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Mic,
                    contentDescription = buttonText,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = buttonText,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            text = if (isPreparing) {
                "正在连接麦克风与本地 ASR，引擎启动通常需要 1 到 2 秒。"
            } else {
                "当前模式：$modeLabel"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview
@Composable
private fun CaptureRoutePreview() {
    CaptureRoute(
        uiState = CaptureUiState(
            headline = "纪要任务",
            subheadline = "把图片、语音和文字交给 MemoMind，它会帮你整理成清晰纪要。",
            titleInput = "Creative AI Android 头脑风暴纪要",
            showTitleRequiredHint = false,
            imageBriefInput = "白板上写着端侧优先、云端兜底、结构化纪要。",
            ocrTextInput = "推荐技术栈：Qwen3.5-0.8B/2B/4B，MNN。",
            transcriptInput = "我们先把图片、录音、文字统一成文本上下文，再让本地模型生成纪要。",
            notesInput = "目标是在安卓手机上完成可演示的本地纪要链路。",
            maxImageCount = 8,
            selectedImageAssets = listOf(
                CaptureAssetItem("whiteboard_brainstorm_1.jpg", "file:///demo/1.jpg"),
                CaptureAssetItem("whiteboard_brainstorm_2.jpg", "file:///demo/2.jpg"),
            ),
            selectedAudioAssetLabel = "brainstorm_meeting.m4a",
            composedPreview = "[图片识别文本]\n推荐技术栈...\n\n[音频转写文本]\n端侧优先...",
            activeSourceLabels = listOf("图片识别文本", "音频转写文本", "补充文字"),
            canRunImageOcr = true,
            isRunningImageOcr = false,
            canRunImageSummary = true,
            isRunningImageSummary = false,
            canToggleAudioTranscription = true,
            isPreparingAudioTranscription = false,
            isRunningAudioTranscription = false,
            audioTranscriptionModeLabel = "端侧离线 ASR（SenseVoice）",
            audioToggleLabel = "使用麦克风转写",
            isSubmitting = false,
            submitEnabled = true,
            statusMessage = "当前将优先走端侧模型生成纪要。",
        ),
        onTitleChange = {},
        onImageBriefChange = {},
        onOcrTextChange = {},
        onTranscriptChange = {},
        onNotesChange = {},
        onPickImage = {},
        onCaptureImage = {},
        onClearImage = {},
        onOpenImageAsset = {},
        onRunImageSummary = {},
        onRunImageOcr = {},
        onPickAudio = {},
        onClearAudio = {},
        onToggleAudioTranscription = {},
        onSubmit = {},
    )
}
