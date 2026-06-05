package cn.chenxuhang.creativeai.feature.result

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlaylistAddCheck
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ResultSectionItem(
    val label: String,
    val value: String,
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
    val detail: String,
    val icon: ImageVector,
)

data class ResultUiState(
    val headline: String,
    val subheadline: String,
    val summary: String?,
    val sections: List<ResultSectionItem>,
    val assetItems: List<ResultAssetItem> = emptyList(),
    val agentActions: List<AgentActionItem> = emptyList(),
)

@Composable
fun ResultRoute(
    uiState: ResultUiState,
    onToggleAudioPlayback: (String) -> Unit = {},
    onRetranscribeAudio: (String) -> Unit = {},
    onOpenAsset: (String) -> Unit = {},
    onRunAgentAction: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
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

            if (uiState.summary != null) {
                item {
                    HeroSummaryCard(uiState.summary)
                }
            }

            if (uiState.sections.isEmpty()) {
                item {
                    ResultStoryCard(
                        title = "还没有纪要内容",
                        value = "先在任务页完成一次生成，MemoMind 会把整理后的结果放到这里。",
                        tint = Color(0xFFD9E7FF),
                        icon = Icons.Outlined.AutoAwesome,
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
}

@Composable
private fun HeroSummaryCard(
    summary: String,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFF1D6))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFFFB74D).copy(alpha = 0.22f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lightbulb,
                        contentDescription = "一句话总结",
                        tint = Color(0xFFD97A00),
                    )
                }
                Text(
                    text = "一句话总结",
                    style = MaterialTheme.typography.titleLarge,
                    letterSpacing = 1.1.sp,
                )
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun ResultStoryCard(
    title: String,
    value: String,
    tint: Color,
    icon: ImageVector,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(tint.copy(alpha = 0.18f))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = tint.darker(),
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    letterSpacing = 0.9.sp,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
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
    val title = if (isImage) "图片素材" else if (asset.kindLabel == "AUDIO") "音频素材" else "${asset.kindLabel}素材"
    val tint = if (isImage) Color(0xFFD9ECFF) else Color(0xFFE6F7EE)
    val icon = if (isImage) Icons.Outlined.Image else Icons.Outlined.GraphicEq

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(tint)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(icon, contentDescription = title, modifier = Modifier.size(20.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    letterSpacing = 0.8.sp,
                )
            }
            Text(
                text = asset.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = asset.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isImage) {
                OutlinedButton(
                    onClick = { onOpenAsset(asset.uri) },
                    modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier.fillMaxWidth(),
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
            }
        }
    }
}

@Composable
private fun AgentHandoffCard(
    actions: List<AgentActionItem>,
    onRunAgentAction: (String) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFEAF2FF))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "把纪要继续交给桌面 Agent 或邮件工作流。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { action ->
                    Text(
                        text = "• ${action.label}：${action.detail}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                AgentActionItem("codex", "复制给 Codex", "复制一份适合继续做图表与整理的任务提示词。", Icons.Outlined.ContentCopy),
                AgentActionItem("share", "系统分享", "把纪要内容分享给电脑端常用工作流。", Icons.Outlined.Share),
                AgentActionItem("email", "发到邮箱", "直接生成一封带纪要正文的邮件草稿。", Icons.Outlined.Email),
            ),
        ),
    )
}
