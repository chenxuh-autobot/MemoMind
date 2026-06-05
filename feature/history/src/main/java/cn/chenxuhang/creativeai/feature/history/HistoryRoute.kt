package cn.chenxuhang.creativeai.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class HistoryTaskItem(
    val taskId: String,
    val title: String,
    val status: String,
    val summary: String,
    val detail: String,
    val canRecall: Boolean,
    val isArchived: Boolean,
)

data class HistoryArchiveGroup(
    val folderName: String,
    val tasks: List<HistoryTaskItem>,
)

data class HistoryUiState(
    val headline: String,
    val subheadline: String,
    val activeTasks: List<HistoryTaskItem>,
    val archiveGroups: List<HistoryArchiveGroup>,
)

@Composable
fun HistoryRoute(
    uiState: HistoryUiState,
    onRecallTask: (String) -> Unit = {},
    onDeleteTask: (String) -> Unit = {},
    onRenameArchiveFolder: (String, String) -> Unit = { _, _ -> },
    onArchiveTaskIntoFolder: (String, String) -> Unit = { _, _ -> },
    onAddArchiveFolder: () -> Unit = {},
    onDeleteArchiveFolder: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val archiveBounds = remember { mutableStateMapOf<String, Rect>() }
    val taskBounds = remember { mutableStateMapOf<String, Rect>() }
    var draggingTaskId by remember { mutableStateOf<String?>(null) }
    var dragTranslation by remember { mutableStateOf(Offset.Zero) }
    var dragPointerInWindow by remember { mutableStateOf<Offset?>(null) }
    var hoveredArchive by remember { mutableStateOf<String?>(null) }

    Surface(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = uiState.headline,
                    style = MaterialTheme.typography.headlineMedium,
                    letterSpacing = 1.8.sp,
                )
            }

            if (uiState.activeTasks.isEmpty() && uiState.archiveGroups.isEmpty()) {
                item {
                    HistoryEmptyCard()
                }
            }

            if (uiState.activeTasks.isNotEmpty()) {
                item {
                    HistorySectionTitle(
                        title = "最近任务",
                        tint = Color(0xFF7A55E8),
                        icon = Icons.Outlined.EditNote,
                    )
                }
                items(uiState.activeTasks.size, key = { uiState.activeTasks[it].taskId }) { index ->
                    val task = uiState.activeTasks[index]
                    StickyTaskCard(
                        task = task,
                        tint = activeNoteColor(index),
                        onRecallTask = onRecallTask,
                        onDeleteTask = onDeleteTask,
                        isDragging = draggingTaskId == task.taskId,
                        dragTranslation = if (draggingTaskId == task.taskId) dragTranslation else Offset.Zero,
                        onTaskBoundsChanged = { rect ->
                            taskBounds[task.taskId] = rect
                        },
                        onDragStart = { touchPoint ->
                            val taskRect = taskBounds[task.taskId]
                            draggingTaskId = task.taskId
                            dragTranslation = Offset.Zero
                            dragPointerInWindow = taskRect?.topLeft?.plus(touchPoint)
                            hoveredArchive = dragPointerInWindow?.let { pointer ->
                                archiveBounds.entries.firstOrNull { it.value.contains(pointer) }?.key
                            }
                        },
                        onDrag = { dragAmount ->
                            dragTranslation += dragAmount
                            dragPointerInWindow = dragPointerInWindow?.plus(dragAmount)
                            hoveredArchive = dragPointerInWindow?.let { pointer ->
                                archiveBounds.entries.firstOrNull { it.value.contains(pointer) }?.key
                            }
                        },
                        onDragEnd = {
                            val targetFolder = hoveredArchive
                            if (targetFolder != null) {
                                onArchiveTaskIntoFolder(task.taskId, targetFolder)
                            }
                            draggingTaskId = null
                            dragTranslation = Offset.Zero
                            dragPointerInWindow = null
                            hoveredArchive = null
                        },
                        onDragCancel = {
                            draggingTaskId = null
                            dragTranslation = Offset.Zero
                            dragPointerInWindow = null
                            hoveredArchive = null
                        },
                    )
                }
            }

            item {
                ArchiveLibraryHeader(onAddArchiveFolder = onAddArchiveFolder)
            }

            if (uiState.archiveGroups.isEmpty()) {
                item {
                    EmptyArchiveLibraryCard()
                }
            } else {
                items(uiState.archiveGroups.size, key = { uiState.archiveGroups[it].folderName }) { index ->
                    val group = uiState.archiveGroups[index]
                    ArchiveFolderCard(
                        group = group,
                        tint = archivedNoteColor(index),
                        isHovered = hoveredArchive == group.folderName,
                        onRecallTask = onRecallTask,
                        onDeleteTask = onDeleteTask,
                        onDeleteArchiveFolder = onDeleteArchiveFolder,
                        onRenameArchiveFolder = onRenameArchiveFolder,
                        onBoundsChanged = { rect ->
                            archiveBounds[group.folderName] = rect
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryEmptyCard() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFF0C9))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFFFD66B).copy(alpha = 0.26f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PushPin,
                        contentDescription = null,
                        tint = Color(0xFFBE7B00),
                    )
                }
                Text(
                    text = "这里还空着",
                    style = MaterialTheme.typography.titleLarge,
                    letterSpacing = 1.sp,
                )
            }
            Text(
                text = "先去任务页做一条纪要吧。做完以后，它会像一张便利贴一样贴在这里，等你回看、回填或塞进档案库。",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun HistorySectionTitle(
    title: String,
    tint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(tint.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = title, tint = tint)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            letterSpacing = 1.1.sp,
        )
    }
}

@Composable
private fun ArchiveLibraryHeader(
    onAddArchiveFolder: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HistorySectionTitle(
            title = "任务档案库",
            tint = Color(0xFF1D8F6A),
            icon = Icons.Outlined.FolderOpen,
        )
        IconButton(onClick = onAddArchiveFolder) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "新增档案",
                tint = Color(0xFF1D8F6A),
            )
        }
    }
}

@Composable
private fun EmptyArchiveLibraryCard() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFEAF8F1))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "先新建一个档案吧",
                style = MaterialTheme.typography.titleLarge,
                letterSpacing = 1.sp,
            )
            Text(
                text = "点右上角的 + 先放几个档案名，之后长按最近任务，把它“抓起来”拖进对应档案里就行。",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun StickyTaskCard(
    task: HistoryTaskItem,
    tint: Color,
    onRecallTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    isDragging: Boolean,
    dragTranslation: Offset,
    onTaskBoundsChanged: (Rect) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                onTaskBoundsChanged(coordinates.boundsInWindow())
            }
            .graphicsLayer {
                translationX = dragTranslation.x
                translationY = dragTranslation.y
                scaleX = if (isDragging) 0.93f else 1f
                scaleY = if (isDragging) 0.93f else 1f
                rotationZ = if (isDragging) (dragTranslation.x / 30f).coerceIn(-8f, 8f) else 0f
                alpha = if (isDragging) 0.9f else 1f
            }
            .pointerInput(task.taskId) {
                detectDragGesturesAfterLongPress(
                    onDragStart = onDragStart,
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            },
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(tint.copy(alpha = 0.16f))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PushPin,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(
                    onClick = { onDeleteTask(task.taskId) },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Backspace,
                        contentDescription = "删除任务",
                        tint = Color(0xFFBA4A3A),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Text(
                text = task.summary,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (task.canRecall) {
                Button(
                    onClick = { onRecallTask(task.taskId) },
                    modifier = Modifier.fillMaxWidth(0.42f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("回填")
                }
            }
        }
    }
}

@Composable
private fun ArchiveFolderCard(
    group: HistoryArchiveGroup,
    tint: Color,
    isHovered: Boolean,
    onRecallTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onDeleteArchiveFolder: (String) -> Unit,
    onRenameArchiveFolder: (String, String) -> Unit,
    onBoundsChanged: (Rect) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                onBoundsChanged(coordinates.boundsInWindow())
            },
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isHovered) tint.copy(alpha = 0.30f) else tint.copy(alpha = 0.18f),
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Inventory2,
                        contentDescription = null,
                        tint = Color(0xFF1D8F6A),
                    )
                    Text(
                        text = "任务档案",
                        style = MaterialTheme.typography.titleMedium,
                        letterSpacing = 0.8.sp,
                    )
                }
                IconButton(
                    onClick = { onDeleteArchiveFolder(group.folderName) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Backspace,
                        contentDescription = "删除档案",
                        tint = Color(0xFFBA4A3A),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            OutlinedTextField(
                value = group.folderName,
                onValueChange = { onRenameArchiveFolder(group.folderName, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("档案名称") },
                singleLine = true,
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.DriveFileRenameOutline,
                        contentDescription = "重命名档案",
                    )
                },
            )
            if (group.tasks.isEmpty()) {
                Text(
                    text = if (isHovered) "松手就会把任务塞进这个档案里。" else "这里还没有任务，长按最近任务拖进来试试。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    group.tasks.forEach { task ->
                        MiniArchiveTaskChip(
                            task = task,
                            onRecallTask = onRecallTask,
                            onDeleteTask = onDeleteTask,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniArchiveTaskChip(
    task: HistoryTaskItem,
    onRecallTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
) {
    ElevatedCard {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = task.summary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (task.canRecall) {
                    Button(onClick = { onRecallTask(task.taskId) }) {
                        Text("回填")
                    }
                }
                IconButton(onClick = { onDeleteTask(task.taskId) }) {
                    Icon(
                        imageVector = Icons.Outlined.Backspace,
                        contentDescription = "删除任务",
                        tint = Color(0xFFBA4A3A),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

private fun activeNoteColor(index: Int): Color {
    val palette = listOf(
        Color(0xFFFFE7A4),
        Color(0xFFFFD9D0),
        Color(0xFFD9F1FF),
        Color(0xFFE5DCF9),
    )
    return palette[index % palette.size]
}

private fun archivedNoteColor(index: Int): Color {
    val palette = listOf(
        Color(0xFFE7F7DD),
        Color(0xFFE6EEF9),
        Color(0xFFF6E7D8),
        Color(0xFFEFE7FF),
    )
    return palette[index % palette.size]
}

@Preview
@Composable
private fun HistoryRoutePreview() {
    HistoryRoute(
        uiState = HistoryUiState(
            headline = "任务历史",
            subheadline = "",
            activeTasks = listOf(
                HistoryTaskItem(
                    taskId = "demo-task",
                    title = "Creative AI Android 头脑风暴纪要",
                    status = "COMPLETED",
                    summary = "端侧纪要链路已打通，准备继续调优结果质量。",
                    detail = "brainstorm_memo | LOCAL_ONLY",
                    canRecall = true,
                    isArchived = false,
                ),
            ),
            archiveGroups = listOf(
                HistoryArchiveGroup(
                    folderName = "比赛准备阶段",
                    tasks = listOf(
                        HistoryTaskItem(
                            taskId = "archived-task",
                            title = "产品定位草稿",
                            status = "COMPLETED",
                            summary = "多模态输入到结构化纪要的主线已确认。",
                            detail = "strategy_memo | LOCAL_ONLY",
                            canRecall = true,
                            isArchived = true,
                        ),
                    ),
                ),
            ),
        ),
    )
}
