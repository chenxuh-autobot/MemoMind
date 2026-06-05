package cn.chenxuhang.creativeai.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.AllInbox
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.ModelTraining
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SystemComponentItem(
    val title: String,
    val detail: String,
    val statusLabel: String,
    val isReady: Boolean,
    val icon: ImageVector,
)

data class ModelInstallItem(
    val modelId: String,
    val title: String,
    val status: String,
    val detail: String,
    val tags: List<String> = emptyList(),
    val isReady: Boolean = false,
    val isSelected: Boolean = false,
)

data class HomeUiState(
    val headline: String,
    val subheadline: String,
    val components: List<SystemComponentItem>,
    val installedModels: List<ModelInstallItem>,
    val projectSourceUrl: String,
    val feedbackIdeas: List<String>,
)

@Composable
fun HomeRoute(
    uiState: HomeUiState,
    onSelectModel: (String) -> Unit,
    onOpenProjectSource: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var coreExpanded by rememberSaveable { mutableStateOf(false) }
    var modelsExpanded by rememberSaveable { mutableStateOf(false) }
    var sourceExpanded by rememberSaveable { mutableStateOf(false) }
    var feedbackExpanded by rememberSaveable { mutableStateOf(false) }
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
                        letterSpacing = 1.8.sp,
                    )
                }
            }

            item {
                SettingsModuleCard(
                    title = "核心组件",
                    accent = MaterialTheme.colorScheme.primary,
                    icon = Icons.Outlined.DeveloperBoard,
                    badge = "${uiState.components.count { it.isReady }}/${uiState.components.size} 已生效",
                    expanded = coreExpanded,
                    onToggleExpanded = { coreExpanded = !coreExpanded },
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        uiState.components.forEach { item ->
                            ComponentStatusCard(
                                item = item,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            item {
                SettingsModuleCard(
                    title = "已登记模型",
                    accent = Color(0xFF2F7BF6),
                    icon = Icons.Outlined.ModelTraining,
                    badge = "${uiState.installedModels.count { it.isReady }} 个可用",
                    expanded = modelsExpanded,
                    onToggleExpanded = { modelsExpanded = !modelsExpanded },
                ) {
                    uiState.installedModels.forEach { model ->
                        ModelStatusCard(
                            model = model,
                            onSelectModel = onSelectModel,
                        )
                    }
                }
            }

            item {
                SettingsModuleCard(
                    title = "项目源代码",
                    accent = Color(0xFF5865F2),
                    icon = Icons.Outlined.Link,
                    expanded = sourceExpanded,
                    onToggleExpanded = { sourceExpanded = !sourceExpanded },
                ) {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = uiState.projectSourceUrl,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            FilledTonalButton(
                                onClick = onOpenProjectSource,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("打开源码仓库")
                            }
                        }
                    }
                }
            }

            item {
                SettingsModuleCard(
                    title = "用户反馈",
                    accent = Color(0xFF00A58B),
                    icon = Icons.Outlined.Feedback,
                    expanded = feedbackExpanded,
                    onToggleExpanded = { feedbackExpanded = !feedbackExpanded },
                ) {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Image(
                                painter = painterResource(id = R.drawable.feedback_survey),
                                contentDescription = "MemoMind 用户调研问卷",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsModuleCard(
    title: String,
    accent: Color,
    icon: ImageVector,
    badge: String? = null,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    content: @Composable () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpanded() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = accent.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(14.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accent,
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        letterSpacing = 1.1.sp,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    badge?.let {
                        StatusPill(
                            text = it,
                            ready = true,
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = accent,
                    )
                }
            }
            if (expanded) {
                content()
            }
        }
    }
}

@Composable
private fun ComponentStatusCard(
    item: SystemComponentItem,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = if (item.isReady) Color(0xFFDDF6E6) else Color(0xFFFFEEE0),
                            shape = RoundedCornerShape(12.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = if (item.isReady) Color(0xFF177245) else Color(0xFFAF5C12),
                    )
                }
                StatusPill(
                    text = item.statusLabel,
                    ready = item.isReady,
                )
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                letterSpacing = 0.8.sp,
            )
            Text(
                text = item.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModelStatusCard(
    model: ModelInstallItem,
    onSelectModel: (String) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = model.title,
                        style = MaterialTheme.typography.titleMedium,
                        letterSpacing = 0.8.sp,
                    )
                    Text(
                        text = model.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(
                    text = model.status,
                    ready = model.isReady,
                )
            }
            if (model.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    model.tags.forEach { tag ->
                        TagPill(text = tag)
                    }
                }
            }
            FilledTonalButton(
                onClick = { onSelectModel(model.modelId) },
                enabled = model.isReady && !model.isSelected,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = when {
                        model.isSelected -> "当前使用中"
                        model.isReady -> "切换到这个模型"
                        else -> "模型准备中"
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    ready: Boolean,
) {
    val background = if (ready) Color(0xFFDDF6E6) else Color(0xFFFFEEE0)
    val foreground = if (ready) Color(0xFF177245) else Color(0xFFAF5C12)
    Row(
        modifier = Modifier
            .background(background, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (ready) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = foreground,
        )
    }
}

@Composable
private fun TagPill(
    text: String,
) {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Preview
@Composable
private fun HomeRoutePreview() {
    HomeRoute(
        uiState = HomeUiState(
            headline = "设置",
            subheadline = "看看 MemoMind 的端侧推理链和模型状态是否都已经就位。",
            components = listOf(
                SystemComponentItem(
                    title = "Qwen 端侧模型包",
                    detail = "tokenizer、llm、weight、config 已准备完成。",
                    statusLabel = "已就绪",
                    isReady = true,
                    icon = Icons.Outlined.AllInbox,
                ),
                SystemComponentItem(
                    title = "MNN 运行时",
                    detail = "真实 MNN 已链接，本地 CPU 推理路径可用。",
                    statusLabel = "已生效",
                    isReady = true,
                    icon = Icons.Outlined.Memory,
                ),
                SystemComponentItem(
                    title = "SME2 构建",
                    detail = "MNN_SME2 与 KleidiAI 已打开。",
                    statusLabel = "已编入",
                    isReady = true,
                    icon = Icons.Outlined.Tune,
                ),
                SystemComponentItem(
                    title = "SME2 硬件探测",
                    detail = "arm64=true, sme2=true，支持端侧 CPU 加速演示。",
                    statusLabel = "已命中",
                    isReady = true,
                    icon = Icons.Outlined.DeveloperBoard,
                ),
                SystemComponentItem(
                    title = "本地会话打开",
                    detail = "MNN LLM session 已成功拉起。",
                    statusLabel = "成功",
                    isReady = true,
                    icon = Icons.Outlined.CheckCircle,
                ),
                SystemComponentItem(
                    title = "本地存储",
                    detail = "模型、纪要、录音目录均可正常读写。",
                    statusLabel = "可用",
                    isReady = true,
                    icon = Icons.Outlined.SdStorage,
                ),
            ),
            installedModels = listOf(
                ModelInstallItem(
                    modelId = "qwen-vl-2b-instruct-mnn",
                    title = "Qwen3-VL-2B 图文版",
                    status = "INSTALLED",
                    detail = "qwen-vl-2b-instruct-mnn | 2280MB | /data/models/qwen-vl-2b-instruct-mnn",
                    tags = listOf("图片理解更强", "多图语境更稳", "适合图文混合纪要", "已随包内置"),
                    isReady = true,
                    isSelected = true,
                ),
            ),
            projectSourceUrl = "https://github.com/chenxuh-autobot/MemoMind",
            feedbackIdeas = listOf(
                "推荐优先用飞书表单或腾讯问卷，扫码就能填，比赛演示时最顺手。",
                "问卷题目建议：你使用的是哪个模型、识别准确度如何、纪要是否清晰、最想补什么能力。",
                "最后加一个开放题：哪一步最卡、最想优化哪里，方便你收真实反馈。",
            ),
        ),
        onSelectModel = {},
        onOpenProjectSource = {},
    )
}
