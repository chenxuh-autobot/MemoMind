package cn.chenxuhang.creativeai.feature.history

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

data class HistoryTaskItem(
    val title: String,
    val status: String,
    val summary: String,
    val detail: String,
)

data class HistoryUiState(
    val headline: String,
    val subheadline: String,
    val tasks: List<HistoryTaskItem>,
)

@Composable
fun HistoryRoute(
    uiState: HistoryUiState,
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

            if (uiState.tasks.isEmpty()) {
                item {
                    HistoryCard(title = "暂无任务") {
                        Text(
                            text = "本地纪要任务还没有落地结果。先在 Home 页跑通一次端侧执行器。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                items(uiState.tasks.size) { index ->
                    val task = uiState.tasks[index]
                    HistoryCard(title = task.title) {
                        Text(
                            text = "状态: ${task.status}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "摘要: ${task.summary}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = task.detail,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
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
private fun HistoryRoutePreview() {
    HistoryRoute(
        uiState = HistoryUiState(
            headline = "任务历史",
            subheadline = "查看最近的端侧纪要任务。",
            tasks = listOf(
                HistoryTaskItem(
                    title = "Creative AI Android 头脑风暴纪要",
                    status = "COMPLETED",
                    summary = "端侧纪要链路已打通",
                    detail = "brainstorm_memo | LOCAL_ONLY",
                ),
            ),
        ),
    )
}
