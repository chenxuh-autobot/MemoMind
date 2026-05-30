package cn.chenxuhang.creativeai.feature.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

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

data class ResultUiState(
    val headline: String,
    val subheadline: String,
    val summary: String?,
    val sections: List<ResultSectionItem>,
    val assetItems: List<ResultAssetItem> = emptyList(),
    val rawJson: String?,
)

@Composable
fun ResultRoute(
    uiState: ResultUiState,
    onToggleAudioPlayback: (String) -> Unit = {},
    onRetranscribeAudio: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = uiState.headline,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = uiState.subheadline,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (uiState.summary != null) {
                item {
                    ResultCard(title = "一句话总结") {
                        Text(
                            text = uiState.summary,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            if (uiState.sections.isEmpty()) {
                item {
                    ResultCard(title = "暂无纪要") {
                        Text(
                            text = "还没有可展示的结构化纪要结果。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                items(uiState.sections.size) { index ->
                    val item = uiState.sections[index]
                    ResultCard(title = item.label) {
                        Text(
                            text = item.value,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            if (uiState.assetItems.isNotEmpty()) {
                items(uiState.assetItems.size) { index ->
                    val asset = uiState.assetItems[index]
                    ResultCard(title = "${asset.kindLabel}素材") {
                        Text(
                            text = asset.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = asset.detail,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (asset.isPlayableAudio) {
                            Button(
                                onClick = { onToggleAudioPlayback(asset.uri) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (asset.isPlaying) "停止播放录音" else "播放录音")
                            }
                            OutlinedButton(
                                onClick = { onRetranscribeAudio(asset.uri) },
                                enabled = asset.canRetranscribeAudio && !asset.isRetranscribing,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    if (asset.isRetranscribing) {
                                        "正在重跑转写..."
                                    } else if (asset.canRetranscribeAudio) {
                                        "重跑转写到 Capture"
                                    } else {
                                        "当前系统不支持文件级重跑转写"
                                    },
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("当前素材仅展示引用")
                            }
                        }
                    }
                }
            }

            if (!uiState.rawJson.isNullOrBlank()) {
                item {
                    ResultCard(title = "原始 JSON") {
                        Text(
                            text = uiState.rawJson,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    title: String,
    content: @Composable () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            content()
        }
    }
}

@Preview
@Composable
private fun ResultRoutePreview() {
    ResultRoute(
        uiState = ResultUiState(
            headline = "纪要结果",
            subheadline = "查看最近一次结构化纪要输出。",
            summary = "端侧纪要生成链路已完成首轮验证。",
            sections = listOf(
                ResultSectionItem("背景", "多模态创意纪要首版先聚焦本地文本链路。"),
                ResultSectionItem("行动项", "1. 接结果页 2. 接历史页 3. 接 OCR / ASR"),
            ),
            assetItems = listOf(
                ResultAssetItem(
                    kindLabel = "AUDIO",
                    displayName = "voice_note_20260530_203000.m4a",
                    detail = "audio/mp4",
                    uri = "file:///demo/voice_note.m4a",
                    isPlayableAudio = true,
                    isPlaying = false,
                    canRetranscribeAudio = true,
                ),
            ),
            rawJson = "{\"oneLineSummary\":\"demo\"}",
        ),
    )
}
