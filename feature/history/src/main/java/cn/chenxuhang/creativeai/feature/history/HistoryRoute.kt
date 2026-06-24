package cn.chenxuhang.creativeai.feature.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class HistoryTaskItem(
    val taskId: String,
    val title: String,
    val status: String,
    val summary: String,
    val detail: String,
    val canRecall: Boolean,
    val canOpenResult: Boolean,
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
    onOpenTaskResult: (String) -> Unit = {},
    onDeleteTask: (String) -> Unit = {},
    onRenameArchiveFolder: (String, String) -> Unit = { _, _ -> },
    onArchiveTaskIntoFolder: (String, String) -> Unit = { _, _ -> },
    onMoveArchivedTaskToFolder: (String, String) -> Unit = { _, _ -> },
    onUnarchiveTask: (String) -> Unit = {},
    onAddArchiveFolder: () -> Unit = {},
    onDeleteArchiveFolder: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val archiveBounds = remember { mutableStateMapOf<String, Rect>() }
    val taskBounds = remember { mutableStateMapOf<String, Rect>() }
    var draggingTaskId by remember { mutableStateOf<String?>(null) }
    var pressedTaskId by remember { mutableStateOf<String?>(null) }
    var dragTranslation by remember { mutableStateOf(Offset.Zero) }
    var dragPointerInWindow by remember { mutableStateOf<Offset?>(null) }
    var hoveredArchive by remember { mutableStateOf<String?>(null) }
    var openedArchive by rememberSaveable { mutableStateOf<String?>(null) }
    var archiveMenuTarget by remember { mutableStateOf<String?>(null) }
    var taskMenuTarget by remember { mutableStateOf<HistoryTaskItem?>(null) }
    var renameDialogTarget by remember { mutableStateOf<String?>(null) }
    var deleteArchiveTarget by remember { mutableStateOf<String?>(null) }

    Surface(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = uiState.headline,
                            style = MaterialTheme.typography.headlineMedium,
                            letterSpacing = 0.sp,
                        )
                        if (uiState.subheadline.isNotBlank()) {
                            Text(
                                text = uiState.subheadline,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (uiState.archiveGroups.isEmpty()) {
                    item {
                        EmptyArchiveLibraryCard(onAddArchiveFolder = onAddArchiveFolder)
                    }
                } else {
                    item {
                        ArchiveLibraryHeader(
                            archiveCount = uiState.archiveGroups.size,
                            onAddArchiveFolder = onAddArchiveFolder,
                        )
                    }
                    items(uiState.archiveGroups, key = { it.folderName }) { group ->
                        ArchiveFolderCard(
                            group = group,
                            tint = archivedNoteColor(group.folderName.hashCode()),
                            isHovered = hoveredArchive == group.folderName,
                            isOpen = openedArchive == group.folderName,
                            onClick = {
                                openedArchive = if (openedArchive == group.folderName) null else group.folderName
                            },
                            onLongPress = {
                                archiveMenuTarget = group.folderName
                            },
                            onBoundsChanged = { rect ->
                                archiveBounds[group.folderName] = rect
                            },
                        )
                    }
                }

                if (uiState.activeTasks.isNotEmpty()) {
                    item {
                        HistorySectionTitle(
                            title = "最近任务",
                            tint = Color(0xFF7A55E8),
                            icon = Icons.Outlined.EditNote,
                            trailing = "${uiState.activeTasks.size} 条",
                        )
                    }
                    items(uiState.activeTasks, key = { it.taskId }) { task ->
                        StickyTaskCard(
                            task = task,
                            tint = activeNoteColor(task.taskId.hashCode()),
                            isDragging = draggingTaskId == task.taskId,
                            isDragReady = pressedTaskId == task.taskId,
                            dragTranslation = if (draggingTaskId == task.taskId) dragTranslation else Offset.Zero,
                            onTaskBoundsChanged = { rect ->
                                taskBounds[task.taskId] = rect
                            },
                            onClick = {
                                if (task.canOpenResult) onOpenTaskResult(task.taskId)
                            },
                            onLongPress = {
                                taskMenuTarget = task
                                pressedTaskId = null
                            },
                            onLongPressReady = {
                                pressedTaskId = task.taskId
                            },
                            onDragStart = { touchPoint ->
                                val taskRect = taskBounds[task.taskId]
                                draggingTaskId = task.taskId
                                pressedTaskId = null
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
                                    openedArchive = targetFolder
                                }
                                draggingTaskId = null
                                pressedTaskId = null
                                dragTranslation = Offset.Zero
                                dragPointerInWindow = null
                                hoveredArchive = null
                            },
                            onDragCancel = {
                                draggingTaskId = null
                                pressedTaskId = null
                                dragTranslation = Offset.Zero
                                dragPointerInWindow = null
                                hoveredArchive = null
                            },
                        )
                    }
                }

                if (uiState.activeTasks.isEmpty() && uiState.archiveGroups.isEmpty()) {
                    item {
                        HistoryEmptyCard()
                    }
                }
            }

            openedArchive
                ?.let { archiveName ->
                    val archive = uiState.archiveGroups.firstOrNull { it.folderName == archiveName }
                    if (archive != null) {
                        ArchiveDetailOverlay(
                            group = archive,
                            onDismiss = { openedArchive = null },
                            onTaskClick = { task ->
                                if (task.canOpenResult) onOpenTaskResult(task.taskId)
                            },
                            onTaskLongPress = { task ->
                                taskMenuTarget = task
                            },
                            onMoveHintChanged = { hoveredArchive = it },
                        )
                    }
                }

            hoveredArchive
                ?.takeIf { draggingTaskId != null }
                ?.let { folderName ->
                    DragHintBanner(folderName = folderName)
                }
        }
    }

    val archiveMenuName = archiveMenuTarget
    if (archiveMenuName != null) {
        ArchiveMenuDialog(
            folderName = archiveMenuName,
            onDismiss = { archiveMenuTarget = null },
            onRename = {
                renameDialogTarget = archiveMenuName
                archiveMenuTarget = null
            },
            onDelete = {
                deleteArchiveTarget = archiveMenuName
                archiveMenuTarget = null
            },
        )
    }

    val archiveRenameName = renameDialogTarget
    if (archiveRenameName != null) {
        RenameArchiveDialog(
            currentName = archiveRenameName,
            onDismiss = { renameDialogTarget = null },
            onConfirm = { newName ->
                onRenameArchiveFolder(archiveRenameName, newName)
                if (openedArchive == archiveRenameName) {
                    openedArchive = newName.trim().ifBlank { archiveRenameName }
                }
                renameDialogTarget = null
            },
        )
    }

    val archiveDeleteName = deleteArchiveTarget
    if (archiveDeleteName != null) {
        ConfirmDeleteArchiveDialog(
            folderName = archiveDeleteName,
            onDismiss = { deleteArchiveTarget = null },
            onConfirm = {
                onDeleteArchiveFolder(archiveDeleteName)
                if (openedArchive == archiveDeleteName) {
                    openedArchive = null
                }
                deleteArchiveTarget = null
            },
        )
    }

    val taskMenu = taskMenuTarget
    if (taskMenu != null) {
        TaskActionDialog(
            task = taskMenu,
            archiveGroups = uiState.archiveGroups,
            onDismiss = { taskMenuTarget = null },
            onOpenResult = {
                if (taskMenu.canOpenResult) onOpenTaskResult(taskMenu.taskId)
                taskMenuTarget = null
            },
            onRecall = {
                if (taskMenu.canRecall) onRecallTask(taskMenu.taskId)
                taskMenuTarget = null
            },
            onDelete = {
                onDeleteTask(taskMenu.taskId)
                taskMenuTarget = null
            },
            onUnarchive = {
                onUnarchiveTask(taskMenu.taskId)
                taskMenuTarget = null
            },
            onMoveToArchive = { folderName ->
                if (taskMenu.isArchived) {
                    onMoveArchivedTaskToFolder(taskMenu.taskId, folderName)
                } else {
                    onArchiveTaskIntoFolder(taskMenu.taskId, folderName)
                }
                openedArchive = folderName
                taskMenuTarget = null
            },
        )
    }
}

@Composable
private fun HistoryEmptyCard() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFF0C9))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "这里还没有任务",
                style = MaterialTheme.typography.titleLarge,
                letterSpacing = 0.8.sp,
            )
            Text(
                text = "先去任务页生成一条纪要。生成后的任务会自动出现在这里，之后你可以拖入档案、展开查看、回填或再次整理。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ArchiveLibraryHeader(
    archiveCount: Int,
    onAddArchiveFolder: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HistorySectionTitle(
            title = "任务档案",
            tint = Color(0xFF1D8F6A),
            icon = Icons.Outlined.FolderOpen,
            trailing = "$archiveCount 组",
        )
        TextButton(onClick = onAddArchiveFolder) {
            Text("新建档案")
        }
    }
}

@Composable
private fun EmptyArchiveLibraryCard(
    onAddArchiveFolder: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFEAF8F1))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "先建几个任务档案吧",
                style = MaterialTheme.typography.titleLarge,
                letterSpacing = 0.8.sp,
            )
            Text(
                text = "档案会按最新更新排列。长按最近任务拖进去就能归档，点开档案再看里面的任务，不会一上来堆满整页。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onAddArchiveFolder) {
                Text("创建第一个档案")
            }
        }
    }
}

@Composable
private fun HistorySectionTitle(
    title: String,
    tint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    trailing: String? = null,
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
        trailing?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickyTaskCard(
    task: HistoryTaskItem,
    tint: Color,
    isDragging: Boolean,
    isDragReady: Boolean,
    dragTranslation: Offset,
    onTaskBoundsChanged: (Rect) -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onLongPressReady: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                onTaskBoundsChanged(coordinates.boundsInWindow())
            }
            .graphicsLayer {
                translationX = dragTranslation.x
                translationY = dragTranslation.y
                val activeScale = when {
                    isDragging -> 0.95f
                    isDragReady -> 0.98f
                    else -> 1f
                }
                scaleX = activeScale
                scaleY = activeScale
                rotationZ = if (isDragging) (dragTranslation.x / 40f).coerceIn(-6f, 6f) else 0f
                alpha = if (isDragging) 0.92f else 1f
                shadowElevation = if (isDragging || isDragReady) 18f else 0f
            }
            .pointerInput(task.taskId) {
                detectLongPressMenuOrDrag(
                    onClick = onClick,
                    onLongPress = onLongPress,
                    onLongPressReady = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPressReady()
                    },
                    onDragStart = onDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                )
            },
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    tint.copy(alpha = if (isDragging || isDragReady) 0.26f else 0.14f),
                )
                .border(
                    width = if (isDragging || isDragReady) 1.dp else 0.dp,
                    color = if (isDragging || isDragReady) tint.copy(alpha = 0.85f) else Color.Transparent,
                    shape = RoundedCornerShape(18.dp),
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        letterSpacing = 0.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = task.summary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.PushPin,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (task.detail.isNotBlank()) {
                Text(
                    text = task.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArchiveFolderCard(
    group: HistoryArchiveGroup,
    tint: Color,
    isHovered: Boolean,
    isOpen: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                onBoundsChanged(coordinates.boundsInWindow())
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            ),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isHovered) tint.copy(alpha = 0.32f) else tint.copy(alpha = 0.16f),
                )
                .border(
                    width = if (isHovered) 2.dp else 0.dp,
                    color = if (isHovered) Color(0xFF1D8F6A) else Color.Transparent,
                    shape = RoundedCornerShape(18.dp),
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                        text = group.folderName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (group.tasks.isEmpty()) {
                            if (isHovered) "松手放入" else "空档案"
                        } else {
                            "${group.tasks.size} 条任务"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (isOpen) Icons.Outlined.FolderOpen else Icons.Outlined.Archive,
                    contentDescription = null,
                    tint = Color(0xFF1D8F6A),
                    modifier = Modifier.size(20.dp),
                )
            }
            if (group.tasks.isNotEmpty()) {
                Text(
                    text = buildArchivePreviewText(group.tasks),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ArchiveDetailOverlay(
    group: HistoryArchiveGroup,
    onDismiss: () -> Unit,
    onTaskClick: (HistoryTaskItem) -> Unit,
    onTaskLongPress: (HistoryTaskItem) -> Unit,
    onMoveHintChanged: (String?) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.18f))
            .combinedClickable(
                onClick = onDismiss,
                onLongClick = {},
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .padding(10.dp),
            shape = RoundedCornerShape(22.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = group.folderName,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "${group.tasks.size} 条任务",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text("收起")
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(group.tasks, key = { it.taskId }) { task ->
                        ArchiveDetailTaskCard(
                            task = task,
                            onClick = { onTaskClick(task) },
                            onLongPress = { onTaskLongPress(task) },
                        )
                    }
                }
            }
        }
    }
    onMoveHintChanged(null)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArchiveDetailTaskCard(
    task: HistoryTaskItem,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = task.summary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (task.detail.isNotBlank()) {
                Text(
                    text = task.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DragHintBanner(
    folderName: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .background(Color(0xFF1D8F6A), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "松手后将放入：$folderName",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ArchiveMenuDialog(
    folderName: String,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(folderName) },
        text = { Text("长按档案后的操作面板。你可以改名，或删除档案并将其中任务移回外层。") },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRename) { Text("改名") }
                TextButton(onClick = onDelete) { Text("删除") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun RenameArchiveDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var input by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改档案名称") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("档案名称") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(input) }) {
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
private fun ConfirmDeleteArchiveDialog(
    folderName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除档案") },
        text = { Text("删除“$folderName”后，里面的任务会回到外层最近任务。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除")
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
private fun TaskActionDialog(
    task: HistoryTaskItem,
    archiveGroups: List<HistoryArchiveGroup>,
    onDismiss: () -> Unit,
    onOpenResult: () -> Unit,
    onRecall: () -> Unit,
    onDelete: () -> Unit,
    onUnarchive: () -> Unit,
    onMoveToArchive: (String) -> Unit,
) {
    var moveMenuExpanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(task.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(task.summary)
                Box {
                    TextButton(onClick = { moveMenuExpanded = true }) {
                        Text(if (task.isArchived) "移动到其它档案" else "放入档案")
                    }
                    DropdownMenu(
                        expanded = moveMenuExpanded,
                        onDismissRequest = { moveMenuExpanded = false },
                    ) {
                        archiveGroups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group.folderName) },
                                onClick = {
                                    moveMenuExpanded = false
                                    onMoveToArchive(group.folderName)
                                },
                            )
                        }
                    }
                }
                if (task.isArchived) {
                    TextButton(onClick = onUnarchive) {
                        Text("移回外层最近任务")
                    }
                }
                if (task.canRecall) {
                    TextButton(onClick = onRecall) {
                        Text("回填到任务页")
                    }
                }
                if (task.canOpenResult) {
                    TextButton(onClick = onOpenResult) {
                        Text("查看结果")
                    }
                }
                TextButton(onClick = onDelete) {
                    Text("删除任务")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

private fun buildArchivePreviewText(
    tasks: List<HistoryTaskItem>,
): String {
    return tasks
        .take(3)
        .joinToString(" · ") { it.title }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectLongPressMenuOrDrag(
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onLongPressReady: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val longPress = awaitLongPressOrCancellation(down.id)
        if (longPress == null) {
            onClick()
            return@awaitEachGesture
        }

        onLongPressReady()
        var dragging = false
        var totalDrag = Offset.Zero
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == longPress.id } ?: event.changes.firstOrNull()
            if (change == null) {
                if (dragging) onDragCancel()
                return@awaitEachGesture
            }
            if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                if (dragging) {
                    onDragEnd()
                } else {
                    onLongPress()
                }
                return@awaitEachGesture
            }
            val delta = change.positionChange()
            if (delta != Offset.Zero) {
                totalDrag += delta
                if (!dragging && totalDrag.getDistance() > viewConfiguration.touchSlop * 0.8f) {
                    dragging = true
                    onDragStart(longPress.position)
                }
                if (dragging) {
                    change.consume()
                    onDrag(delta)
                }
            }
        }
    }
}

private fun activeNoteColor(seed: Int): Color {
    val palette = listOf(
        Color(0xFFFFE7A4),
        Color(0xFFFFD9D0),
        Color(0xFFD9F1FF),
        Color(0xFFE5DCF9),
    )
    return palette[kotlin.math.abs(seed) % palette.size]
}

private fun archivedNoteColor(seed: Int): Color {
    val palette = listOf(
        Color(0xFFE7F7DD),
        Color(0xFFE6EEF9),
        Color(0xFFF6E7D8),
        Color(0xFFEFE7FF),
    )
    return palette[kotlin.math.abs(seed) % palette.size]
}

@Preview
@Composable
private fun HistoryRoutePreview() {
    HistoryRoute(
        uiState = HistoryUiState(
            headline = "任务历史",
            subheadline = "把最近任务拖进不同档案，展开后再做详细处理。",
            activeTasks = listOf(
                HistoryTaskItem(
                    taskId = "demo-task",
                    title = "品牌头脑风暴纪要",
                    status = "COMPLETED",
                    summary = "今天的方向主要集中在品牌定位、海报语言和活动命名。",
                    detail = "multimodal_capture_memo | LOCAL_ONLY",
                    canRecall = true,
                    canOpenResult = true,
                    isArchived = false,
                ),
                HistoryTaskItem(
                    taskId = "demo-task-2",
                    title = "客户采访整理",
                    status = "COMPLETED",
                    summary = "客户最关注交付周期、价格透明度和服务边界。",
                    detail = "multimodal_capture_memo | LOCAL_ONLY",
                    canRecall = true,
                    canOpenResult = true,
                    isArchived = false,
                ),
            ),
            archiveGroups = listOf(
                HistoryArchiveGroup(
                    folderName = "A 组受访者",
                    tasks = listOf(
                        HistoryTaskItem(
                            taskId = "archived-task",
                            title = "第一次访谈",
                            status = "COMPLETED",
                            summary = "重点记录了对 onboarding 流程的反馈和产品使用障碍。",
                            detail = "strategy_memo | LOCAL_ONLY",
                            canRecall = true,
                            canOpenResult = true,
                            isArchived = true,
                        ),
                    ),
                ),
                HistoryArchiveGroup(
                    folderName = "品牌方案",
                    tasks = emptyList(),
                ),
            ),
        ),
    )
}
