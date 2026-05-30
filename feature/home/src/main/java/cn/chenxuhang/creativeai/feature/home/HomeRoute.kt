package cn.chenxuhang.creativeai.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

data class ReadinessItem(
    val label: String,
    val value: String,
)

data class ModelInstallItem(
    val title: String,
    val status: String,
    val detail: String,
)

data class TaskRecordItem(
    val title: String,
    val status: String,
    val detail: String,
)

data class HomeUiState(
    val headline: String,
    val subheadline: String,
    val readinessItems: List<ReadinessItem>,
    val installedModels: List<ModelInstallItem>,
    val nextMilestones: List<String>,
    val recentTasks: List<TaskRecordItem> = emptyList(),
    val latestMemoItems: List<ReadinessItem> = emptyList(),
    val latestRawOutput: String? = null,
)

@Composable
fun HomeRoute(
    uiState: HomeUiState,
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

            item {
                SectionCard(title = "工程就绪度") {
                    uiState.readinessItems.forEach { item ->
                        Text(
                            text = "${item.label}: ${item.value}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            }

            item {
                SectionCard(title = "已登记模型") {
                    uiState.installedModels.forEach { model ->
                        Text(
                            text = "${model.title} | ${model.status}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = model.detail,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            }

            item {
                SectionCard(title = "下一步") {
                    uiState.nextMilestones.forEach { milestone ->
                        Text(
                            text = "- $milestone",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            }

            if (uiState.recentTasks.isNotEmpty()) {
                item {
                    SectionCard(title = "最近任务") {
                        uiState.recentTasks.forEach { task ->
                            Text(
                                text = "${task.title} | ${task.status}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            Text(
                                text = task.detail,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                    }
                }
            }

            if (uiState.latestMemoItems.isNotEmpty() || !uiState.latestRawOutput.isNullOrBlank()) {
                item {
                    SectionCard(title = "最近纪要") {
                        uiState.latestMemoItems.forEach { item ->
                            Text(
                                text = "${item.label}: ${item.value}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        if (!uiState.latestRawOutput.isNullOrBlank()) {
                            Text(
                                text = uiState.latestRawOutput,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
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
private fun HomeRoutePreview() {
    HomeRoute(
        uiState = HomeUiState(
            headline = "Creative AI Android",
            subheadline = "本地 Qwen 优先的多模态纪要骨架。",
            readinessItems = listOf(
                ReadinessItem("MNN 运行时", "Stub ready"),
                ReadinessItem("执行路径", "LOCAL_TEXT_PIVOT"),
            ),
            installedModels = listOf(
                ModelInstallItem(
                    title = "Qwen Local Text 1.5B",
                    status = "INSTALLED",
                    detail = "qwen-local-1_5b-text | 1900MB | /data/models/qwen-local-1_5b-text",
                ),
            ),
            nextMilestones = listOf(
                "接入 JNI",
                "打通模型下载",
                "接入本地 OCR/ASR",
            ),
            recentTasks = listOf(
                TaskRecordItem(
                    title = "头脑风暴纪要",
                    status = "COMPLETED",
                    detail = "本地 CPU | qwen-local-1_5b-text | 一句话总结已生成",
                ),
            ),
            latestMemoItems = listOf(
                ReadinessItem("一句话总结", "多模态创意纪要首版先聚焦本地文本链路"),
                ReadinessItem("行动项数", "3"),
            ),
        ),
    )
}
