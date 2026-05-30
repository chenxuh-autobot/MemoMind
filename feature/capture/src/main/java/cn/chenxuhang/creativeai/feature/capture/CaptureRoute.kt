package cn.chenxuhang.creativeai.feature.capture

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

data class CaptureUiState(
    val headline: String,
    val subheadline: String,
    val titleInput: String,
    val imageBriefInput: String,
    val ocrTextInput: String,
    val transcriptInput: String,
    val notesInput: String,
    val selectedImageAssetLabel: String? = null,
    val selectedAudioAssetLabel: String? = null,
    val composedPreview: String,
    val activeSourceLabels: List<String>,
    val canRunImageOcr: Boolean,
    val isRunningImageOcr: Boolean,
    val canRunImageSummary: Boolean,
    val isRunningImageSummary: Boolean,
    val canStartAudioTranscription: Boolean,
    val canStopAudioTranscription: Boolean,
    val isRunningAudioTranscription: Boolean,
    val audioTranscriptionModeLabel: String,
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
    onClearImage: () -> Unit,
    onRunImageSummary: () -> Unit,
    onRunImageOcr: () -> Unit,
    onPickAudio: () -> Unit,
    onClearAudio: () -> Unit,
    onStartAudioTranscription: () -> Unit,
    onStopAudioTranscription: () -> Unit,
    onSubmit: () -> Unit,
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
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = uiState.titleInput,
                            onValueChange = onTitleChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("任务标题") },
                            singleLine = true,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "素材选择",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            OutlinedButton(
                                onClick = onPickImage,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (uiState.selectedImageAssetLabel == null) "选择图片文件" else "更换图片文件")
                            }
                            if (uiState.selectedImageAssetLabel != null) {
                                Text(
                                    text = "已选图片: ${uiState.selectedImageAssetLabel}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Button(
                                    onClick = onRunImageSummary,
                                    enabled = uiState.canRunImageSummary && !uiState.isRunningImageSummary,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(if (uiState.isRunningImageSummary) "正在提取图片要点..." else "提取图片要点")
                                }
                                Button(
                                    onClick = onRunImageOcr,
                                    enabled = uiState.canRunImageOcr && !uiState.isRunningImageOcr,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(if (uiState.isRunningImageOcr) "正在识别 OCR..." else "从图片识别 OCR")
                                }
                                OutlinedButton(
                                    onClick = onClearImage,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("清除图片文件")
                                }
                            }
                            OutlinedButton(
                                onClick = onPickAudio,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (uiState.selectedAudioAssetLabel == null) "选择录音文件" else "更换录音文件")
                            }
                            if (uiState.selectedAudioAssetLabel != null) {
                                Text(
                                    text = "已选录音: ${uiState.selectedAudioAssetLabel}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                OutlinedButton(
                                    onClick = onClearAudio,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("清除录音文件")
                                }
                            }
                            Text(
                                text = "语音转写模式: ${uiState.audioTranscriptionModeLabel}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Button(
                                onClick = onStartAudioTranscription,
                                enabled = uiState.canStartAudioTranscription && !uiState.isRunningAudioTranscription,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (uiState.isRunningAudioTranscription) "正在监听语音..." else "开始麦克风转写")
                            }
                            OutlinedButton(
                                onClick = onStopAudioTranscription,
                                enabled = uiState.canStopAudioTranscription,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("停止麦克风转写")
                            }
                        }
                        OutlinedTextField(
                            value = uiState.imageBriefInput,
                            onValueChange = onImageBriefChange,
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            label = { Text("图片内容补充") },
                            supportingText = {
                                Text("先手动填写图片里看到的画面、白板、草图或版面重点，后续会替换成真实图像理解结果。")
                            },
                        )
                        OutlinedTextField(
                            value = uiState.ocrTextInput,
                            onValueChange = onOcrTextChange,
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            label = { Text("OCR 文本") },
                            supportingText = {
                                Text("这里承接截图、海报、白板拍照后的 OCR 输出。")
                            },
                        )
                        OutlinedTextField(
                            value = uiState.transcriptInput,
                            onValueChange = onTranscriptChange,
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 5,
                            label = { Text("录音转写文本") },
                            supportingText = {
                                Text("这里承接录音、访谈或会议语音的转写结果。")
                            },
                        )
                        OutlinedTextField(
                            value = uiState.notesInput,
                            onValueChange = onNotesChange,
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 5,
                            label = { Text("补充文字") },
                            supportingText = {
                                Text("自由补充背景、目标、待办或任何你想强调的上下文。")
                            },
                        )
                        if (uiState.activeSourceLabels.isNotEmpty()) {
                            Text(
                                text = "当前输入源: ${uiState.activeSourceLabels.joinToString(" / ")}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        OutlinedTextField(
                            value = uiState.composedPreview,
                            onValueChange = {},
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 8,
                            label = { Text("统一文本上下文预览") },
                            readOnly = true,
                            supportingText = {
                                Text("提交时会把多路输入先合并成统一上下文，再交给本地 Qwen 生成结构化纪要。")
                            },
                        )
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
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (uiState.isSubmitting) "正在生成..." else "生成结构化纪要")
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun CaptureRoutePreview() {
    CaptureRoute(
        uiState = CaptureUiState(
            headline = "提交纪要任务",
            subheadline = "把图片说明、OCR、录音转写和补充文字统一汇总后，直接触发本地纪要生成。",
            titleInput = "Creative AI Android 头脑风暴纪要",
            imageBriefInput = "白板上写着端侧优先、云端兜底、结构化纪要。",
            ocrTextInput = "推荐技术栈：Qwen3.5-0.8B/2B/4B，MNN。",
            transcriptInput = "我们先把图片、录音、文字统一成文本上下文，再让本地模型生成纪要。",
            notesInput = "目标是在安卓手机上完成可演示的本地纪要链路。",
            selectedImageAssetLabel = "whiteboard_brainstorm.jpg",
            selectedAudioAssetLabel = "brainstorm_meeting.m4a",
            composedPreview = "[图片内容补充]\n白板上写着端侧优先...\n\n[OCR 文本]\n推荐技术栈...",
            activeSourceLabels = listOf("图片内容补充", "OCR 文本", "录音转写文本", "补充文字"),
            canRunImageOcr = true,
            isRunningImageOcr = false,
            canRunImageSummary = true,
            isRunningImageSummary = false,
            canStartAudioTranscription = true,
            canStopAudioTranscription = false,
            isRunningAudioTranscription = false,
            audioTranscriptionModeLabel = "本地优先",
            isSubmitting = false,
            submitEnabled = true,
            statusMessage = "当前将优先走本地 CPU 低内存模式。",
        ),
        onTitleChange = {},
        onImageBriefChange = {},
        onOcrTextChange = {},
        onTranscriptChange = {},
        onNotesChange = {},
        onPickImage = {},
        onClearImage = {},
        onRunImageSummary = {},
        onRunImageOcr = {},
        onPickAudio = {},
        onClearAudio = {},
        onStartAudioTranscription = {},
        onStopAudioTranscription = {},
        onSubmit = {},
    )
}
