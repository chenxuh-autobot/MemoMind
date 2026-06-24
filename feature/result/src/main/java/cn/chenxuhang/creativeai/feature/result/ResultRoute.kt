package cn.chenxuhang.creativeai.feature.result

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.AllInbox
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlaylistAddCheck
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ResultSectionItem(
    val label: String,
    val value: String,
    val id: String = "",
)

data class ResultRemoteTaskActionItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
)

data class ResultRemoteTaskOptionItem(
    val taskId: String,
    val targetAgent: String,
    val statusLabel: String,
    val summary: String,
    val detail: String,
    val isSelected: Boolean,
)

data class ResultRemoteTaskUiState(
    val taskId: String,
    val targetAgent: String,
    val statusLabel: String,
    val modeLabel: String,
    val goal: String,
    val summary: String,
    val detailLines: List<String> = emptyList(),
    val progressTimeline: List<String> = emptyList(),
    val resultSections: List<ResultSectionItem> = emptyList(),
    val actions: List<ResultRemoteTaskActionItem> = emptyList(),
)

data class ResultBridgeStatusUiState(
    val label: String,
    val summary: String,
    val detailLines: List<String> = emptyList(),
)

data class ResultAssetItem(
    val kindLabel: String,
    val displayName: String,
    val detail: String,
    val uri: String,
    val isPlayableAudio: Boolean,
    val isPlaying: Boolean,
    val canRetranscribeAudio: Boolean = false,
    val isRetranscribing: Boolean = false,
)

data class AgentActionItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
)

data class ResultUiState(
    val headline: String,
    val subheadline: String,
    val summary: String?,
    val sections: List<ResultSectionItem>,
    val bridgeStatus: ResultBridgeStatusUiState? = null,
    val remoteTaskOptions: List<ResultRemoteTaskOptionItem> = emptyList(),
    val remoteTask: ResultRemoteTaskUiState? = null,
    val remoteTaskPlaceholder: String? = null,
    val assetItems: List<ResultAssetItem> = emptyList(),
    val agentActions: List<AgentActionItem> = emptyList(),
    val canEdit: Boolean = false,
)

@Composable
fun ResultRoute(
    uiState: ResultUiState,
    onToggleAudioPlayback: (String) -> Unit = {},
    onRetranscribeAudio: (String) -> Unit = {},
    onOpenAsset: (String) -> Unit = {},
    onRemoteTaskAction: (String) -> Unit = {},
    onRunAgentAction: (String) -> Unit = {},
    onEditResultSection: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    var editingSection by remember { mutableStateOf<ResultSectionItem?>(null) }
    Surface(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = uiState.headline,
                        style = MaterialTheme.typography.headlineMedium,
                        letterSpacing = 0.sp,
                    )
                    uiState.subheadline.takeIf { it.isNotBlank() }?.let { subheadline ->
                        Text(
                            text = subheadline,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (uiState.summary != null) {
                item {
                    HeroSummaryCard(
                        summary = uiState.summary,
                        editable = uiState.canEdit,
                        onEdit = {
                            editingSection = ResultSectionItem(
                                id = "summary",
                                label = "一句话总结",
                                value = uiState.summary,
                            )
                        },
                    )
                }
            }

            if (uiState.sections.isEmpty()) {
                item {
                    ResultStoryCard(
                        title = "还没有纪要内容",
                        value = "先在任务页完成一次生成，MemoMind 会把整理后的结果放到这里。",
                        tint = Color(0xFFD9E7FF),
                        icon = Icons.Outlined.AutoAwesome,
                        editable = false,
                        onEdit = {},
                    )
                }
            } else {
                items(uiState.sections.size) { index ->
                    val item = uiState.sections[index]
                    val style = sectionStyle(item.label, index)
                    ResultStoryCard(
                        title = item.label,
                        value = item.value,
                        tint = style.tint,
                        icon = style.icon,
                        editable = uiState.canEdit && item.id.isNotBlank(),
                        onEdit = { editingSection = item },
                    )
                }
            }

            uiState.bridgeStatus?.let { bridgeStatus ->
                item {
                    ResultStoryCard(
                        title = bridgeStatus.label,
                        value = listOf(bridgeStatus.summary)
                            .plus(bridgeStatus.detailLines)
                            .filter { it.isNotBlank() }
                            .joinToString("\n"),
                        tint = Color(0xFFEAF2FF),
                        icon = Icons.Outlined.AllInbox,
                        editable = false,
                        onEdit = {},
                    )
                }
            }

            if (uiState.remoteTaskOptions.isNotEmpty() || uiState.remoteTask != null || uiState.remoteTaskPlaceholder != null) {
                item {
                    Text(
                        text = "Bridge 任务",
                        style = MaterialTheme.typography.titleLarge,
                        letterSpacing = 1.1.sp,
                    )
                }
            }

            if (uiState.remoteTaskOptions.isNotEmpty()) {
                item {
                    RemoteTaskSwitcherCard(
                        options = uiState.remoteTaskOptions,
                        onRemoteTaskAction = onRemoteTaskAction,
                    )
                }
            }

            uiState.remoteTask?.let { remoteTask ->
                item {
                    RemoteTaskCard(
                        remoteTask = remoteTask,
                        onRemoteTaskAction = onRemoteTaskAction,
                    )
                }
            } ?: uiState.remoteTaskPlaceholder?.let { placeholder ->
                item {
                    ResultStoryCard(
                        title = "Bridge 任务详情",
                        value = placeholder,
                        tint = Color(0xFFEAF2FF),
                        icon = Icons.Outlined.TaskAlt,
                        editable = false,
                        onEdit = {},
                    )
                }
            }

            if (uiState.assetItems.isNotEmpty()) {
                item {
                    Text(
                        text = "素材关联",
                        style = MaterialTheme.typography.titleLarge,
                        letterSpacing = 1.1.sp,
                    )
                }
                items(uiState.assetItems.size) { index ->
                    val asset = uiState.assetItems[index]
                    AssetCard(
                        asset = asset,
                        onToggleAudioPlayback = onToggleAudioPlayback,
                        onRetranscribeAudio = onRetranscribeAudio,
                        onOpenAsset = onOpenAsset,
                    )
                }
            }

            if (uiState.agentActions.isNotEmpty()) {
                item {
                    Text(
                        text = "Agent 联动",
                        style = MaterialTheme.typography.titleLarge,
                        letterSpacing = 1.1.sp,
                    )
                }
                item {
                    AgentHandoffCard(
                        actions = uiState.agentActions,
                        onRunAgentAction = onRunAgentAction,
                    )
                }
            }
        }
    }

    editingSection?.let { section ->
        EditResultSectionDialog(
            section = section,
            onDismiss = { editingSection = null },
            onSave = { updatedValue ->
                onEditResultSection(section.id, updatedValue)
                editingSection = null
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroSummaryCard(
    summary: String,
    editable: Boolean,
    onEdit: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.42f))
                .combinedClickable(
                    onClick = {},
                    onLongClick = { if (editable) onEdit() },
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lightbulb,
                        contentDescription = "一句话总结",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = "一句话总结",
                    style = MaterialTheme.typography.titleMedium,
                    letterSpacing = 0.sp,
                    modifier = Modifier.weight(1f),
                )
                if (editable) {
                    Icon(
                        imageVector = Icons.Outlined.EditNote,
                        contentDescription = "编辑一句话总结",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .combinedClickable(
                                onClick = onEdit,
                                onLongClick = onEdit,
                            ),
                    )
                }
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultStoryCard(
    title: String,
    value: String,
    tint: Color,
    icon: ImageVector,
    editable: Boolean,
    onEdit: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(tint.adaptiveContainer())
                .combinedClickable(
                    onClick = {},
                    onLongClick = { if (editable) onEdit() },
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = tint.darker(),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    letterSpacing = 0.sp,
                    modifier = Modifier.weight(1f),
                )
                if (editable) {
                    Icon(
                        imageVector = Icons.Outlined.EditNote,
                        contentDescription = "编辑$title",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .combinedClickable(
                                onClick = onEdit,
                                onLongClick = onEdit,
                            ),
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AssetCard(
    asset: ResultAssetItem,
    onToggleAudioPlayback: (String) -> Unit,
    onRetranscribeAudio: (String) -> Unit,
    onOpenAsset: (String) -> Unit,
) {
    val isImage = asset.kindLabel == "IMAGE"
    val isAudio = asset.kindLabel == "AUDIO"
    val title = when (asset.kindLabel) {
        "IMAGE" -> "图片素材"
        "AUDIO" -> "音频素材"
        "DOCUMENT" -> "文档素材"
        "TEXT" -> "文本素材"
        else -> "${asset.kindLabel}素材"
    }
    val tint = when (asset.kindLabel) {
        "IMAGE" -> Color(0xFFD9ECFF)
        "DOCUMENT", "TEXT" -> Color(0xFFFFF0BF)
        else -> Color(0xFFE6F7EE)
    }
    val icon = if (isImage) Icons.Outlined.Image else if (isAudio) Icons.Outlined.GraphicEq else Icons.Outlined.Summarize

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(tint.adaptiveContainer())
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = title, modifier = Modifier.size(20.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    letterSpacing = 0.sp,
                )
                Text(
                    text = asset.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = asset.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isImage) {
                OutlinedButton(
                    onClick = { onOpenAsset(asset.uri) },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.OpenInNew,
                        contentDescription = "查看图片素材",
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "查看图片素材",
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            } else if (asset.isPlayableAudio) {
                Button(
                    onClick = { onToggleAudioPlayback(asset.uri) },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = "播放录音",
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (asset.isPlaying) "停止播放录音" else "播放录音",
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                OutlinedButton(
                    onClick = { onRetranscribeAudio(asset.uri) },
                    enabled = asset.canRetranscribeAudio && !asset.isRetranscribing,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlaylistAddCheck,
                        contentDescription = "重跑转写",
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = if (asset.isRetranscribing) "正在重跑转写..." else "重跑转写到任务",
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            } else {
                OutlinedButton(
                    onClick = { onOpenAsset(asset.uri) },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.OpenInNew,
                        contentDescription = "打开素材",
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "打开",
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteTaskSwitcherCard(
    options: List<ResultRemoteTaskOptionItem>,
    onRemoteTaskAction: (String) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { option ->
                    val label = "${option.targetAgent} | ${option.statusLabel}"
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (option.isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (option.isSelected) {
                                Button(
                                    onClick = { onRemoteTaskAction("select_remote_task:${option.taskId}") },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(text = label)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onRemoteTaskAction("select_remote_task:${option.taskId}") },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(text = label)
                                }
                            }
                            Text(
                                text = option.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (option.detail.isNotBlank()) {
                                Text(
                                    text = option.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteTaskCard(
    remoteTask: ResultRemoteTaskUiState,
    onRemoteTaskAction: (String) -> Unit,
) {
    val statusTint = remoteTaskStatusTint(remoteTask.statusLabel)
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(statusTint.adaptiveContainer())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.TaskAlt,
                    contentDescription = "Bridge 任务",
                    tint = statusTint.darker(),
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "${remoteTask.targetAgent} | ${remoteTask.statusLabel}",
                    style = MaterialTheme.typography.titleMedium,
                    letterSpacing = 0.sp,
                )
            }
            Text(
                text = remoteTask.summary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = remoteTask.goal,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (remoteTask.detailLines.isNotEmpty()) {
                RemoteTaskDetailBlock(
                    title = "任务元信息",
                    lines = remoteTask.detailLines,
                    background = Color.White.copy(alpha = 0.72f),
                )
            }
            remoteTask.resultSections.forEachIndexed { index, section ->
                RemoteTaskDetailBlock(
                    title = section.label,
                    lines = listOf(section.value),
                    background = if (index % 2 == 0) {
                        Color.White.copy(alpha = 0.72f)
                    } else {
                        statusTint.copy(alpha = 0.1f)
                    },
                )
            }
            if (remoteTask.progressTimeline.isNotEmpty()) {
                RemoteTaskDetailBlock(
                    title = "执行时间线",
                    lines = remoteTask.progressTimeline,
                    background = Color.White.copy(alpha = 0.72f),
                )
            }
            if (remoteTask.actions.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    remoteTask.actions.forEach { action ->
                        OutlinedButton(onClick = { onRemoteTaskAction(action.id) }) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = action.label,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = action.label,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteTaskDetailBlock(
    title: String,
    lines: List<String>,
    background: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            letterSpacing = 0.6.sp,
        )
        lines.filter { it.isNotBlank() }.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EditResultSectionDialog(
    section: ResultSectionItem,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var input by remember(section.id, section.value) { mutableStateOf(section.value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(section.label) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                maxLines = 12,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(input) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun AgentHandoffCard(
    actions: List<AgentActionItem>,
    onRunAgentAction: (String) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                actions.forEach { action ->
                    OutlinedButton(
                        onClick = { onRunAgentAction(action.id) },
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = action.label,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = action.label,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

private data class SectionStyle(
    val tint: Color,
    val icon: ImageVector,
)

private fun sectionStyle(label: String, index: Int): SectionStyle {
    return when (label) {
        "背景" -> SectionStyle(Color(0xFFDCEBFF), Icons.Outlined.Summarize)
        "关键要点" -> SectionStyle(Color(0xFFFFE8D8), Icons.Outlined.PlaylistAddCheck)
        "行动项" -> SectionStyle(Color(0xFFE5F8E8), Icons.Outlined.AssignmentTurnedIn)
        "风险提示" -> SectionStyle(Color(0xFFFFE0D9), Icons.Outlined.WarningAmber)
        "输入来源" -> SectionStyle(Color(0xFFE8E2FF), Icons.Outlined.AllInbox)
        "标签" -> SectionStyle(Color(0xFFFFF0BF), Icons.Outlined.Label)
        else -> {
            val palette = listOf(
                Color(0xFFDCEBFF),
                Color(0xFFFFE8D8),
                Color(0xFFE5F8E8),
                Color(0xFFE8E2FF),
            )
            SectionStyle(palette[index % palette.size], Icons.Outlined.AutoAwesome)
        }
    }
}

private fun Color.darker(): Color = copy(
    red = red * 0.7f,
    green = green * 0.7f,
    blue = blue * 0.7f,
)

@Composable
private fun Color.adaptiveContainer(): Color {
    return if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        copy(alpha = 0.26f)
    } else {
        copy(alpha = 0.18f)
    }
}

private fun remoteTaskStatusTint(statusLabel: String): Color {
    return when {
        "done" in statusLabel.lowercase() || "完成" in statusLabel -> Color(0xFFE5F8E8)
        "failed" in statusLabel.lowercase() || "失败" in statusLabel -> Color(0xFFFFE0D9)
        "waiting_approval" in statusLabel.lowercase() || "待确认" in statusLabel -> Color(0xFFFFF0BF)
        "running" in statusLabel.lowercase() || "执行中" in statusLabel -> Color(0xFFDCEBFF)
        else -> Color(0xFFE8E2FF)
    }
}

@Preview
@Composable
private fun ResultRoutePreview() {
    ResultRoute(
        uiState = ResultUiState(
            headline = "纪要结果",
            subheadline = "看看 MemoMind 刚刚帮你整理出了什么重点。",
            summary = "端侧纪要生成链路已完成首轮验证。",
            sections = listOf(
                ResultSectionItem("背景", "多模态创意纪要首版先聚焦本地文本链路。"),
                ResultSectionItem("关键要点", "1. 先把任务页体验做顺。\n2. 让结果更像真实纪要。\n3. 再做真机调优。"),
                ResultSectionItem("行动项", "1. 接结果页 2. 接历史页 3. 接 OCR / ASR"),
            ),
            bridgeStatus = ResultBridgeStatusUiState(
                label = "Bridge 状态",
                summary = "最近一条桌面任务显示 Bridge 仍在活跃。",
                detailLines = listOf(
                    "最近 Agent：codex",
                    "状态推断：最近活跃",
                    "最后更新时间：2026-06-15T08:58:45+00:00",
                ),
            ),
            remoteTask = ResultRemoteTaskUiState(
                taskId = "demo-remote-task",
                targetAgent = "codex",
                statusLabel = "running",
                modeLabel = "plan_only",
                goal = "为 MemoMind 增加 Markdown 导出能力",
                summary = "电脑端 Bridge 已 claim 任务，正在用 Codex 生成实现计划。",
                detailLines = listOf(
                    "任务 ID：demo-remote-task",
                    "模式：plan_only",
                    "Bridge：memomind-agent-bridge@demo",
                ),
                progressTimeline = listOf(
                    "13:21 | claimed | 电脑端 Bridge 已领取任务。",
                    "13:22 | executing | codex 正在分析项目结构。",
                ),
                resultSections = listOf(
                    ResultSectionItem("Codex 计划正文", "1. 新增导出入口\n2. 复用结果页结构化内容\n3. 增加分享与保存能力"),
                    ResultSectionItem("测试建议", "1. 编译 app\n2. 导出 markdown\n3. 回归已有结果页"),
                ),
                actions = listOf(
                    ResultRemoteTaskActionItem("refresh_remote_task", "刷新", Icons.Outlined.Refresh),
                    ResultRemoteTaskActionItem("copy_remote_task_id", "复制任务 ID", Icons.Outlined.ContentCopy),
                ),
            ),
            assetItems = listOf(
                ResultAssetItem(
                    kindLabel = "IMAGE",
                    displayName = "whiteboard_01.jpg",
                    detail = "image/jpeg",
                    uri = "file:///demo/whiteboard_01.jpg",
                    isPlayableAudio = false,
                    isPlaying = false,
                ),
                ResultAssetItem(
                    kindLabel = "AUDIO",
                    displayName = "voice_note_20260530_203000.wav",
                    detail = "audio/wav",
                    uri = "file:///demo/voice_note.wav",
                    isPlayableAudio = true,
                    isPlaying = false,
                    canRetranscribeAudio = true,
                ),
            ),
            agentActions = listOf(
                AgentActionItem("codex", "复制给 Codex", Icons.Outlined.ContentCopy),
                AgentActionItem("share", "系统分享", Icons.Outlined.Share),
                AgentActionItem("email", "发到邮箱", Icons.Outlined.Email),
            ),
        ),
    )
}
