package cn.chenxuhang.creativeai.ai.orchestrator

import cn.chenxuhang.creativeai.ai.mnn.MnnRuntime
import cn.chenxuhang.creativeai.core.database.MemoTaskLocalDataSource
import cn.chenxuhang.creativeai.core.database.StructuredMemoLocalDataSource
import cn.chenxuhang.creativeai.core.model.ActionItem
import cn.chenxuhang.creativeai.core.model.MemoTask
import cn.chenxuhang.creativeai.core.model.MnnSessionConfig
import cn.chenxuhang.creativeai.core.model.ProcessingMode
import cn.chenxuhang.creativeai.core.model.SourceInputSection
import cn.chenxuhang.creativeai.core.model.StructuredMemo
import cn.chenxuhang.creativeai.core.model.TopicSummary
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class StructuredMemoTaskRequest(
    val title: String,
    val type: String,
    val sourceText: String,
    val sourceSections: List<SourceInputSection> = emptyList(),
    val assetRefs: List<String> = emptyList(),
    val processingMode: ProcessingMode,
    val sessionConfig: MnnSessionConfig,
)

data class StructuredMemoTaskExecutionResult(
    val task: MemoTask,
    val memo: StructuredMemo? = null,
    val rawOutput: String? = null,
    val errorMessage: String? = null,
)

class StructuredMemoTaskExecutor(
    private val runtime: MnnRuntime,
    private val taskLocalDataSource: MemoTaskLocalDataSource,
    private val memoLocalDataSource: StructuredMemoLocalDataSource,
) {
    fun execute(request: StructuredMemoTaskRequest): StructuredMemoTaskExecutionResult {
        val taskId = UUID.randomUUID().toString()
        val runningTask = MemoTask(
            id = taskId,
            title = request.title,
            type = request.type,
            status = "RUNNING",
            processingMode = request.processingMode,
            sourceChannels = request.sourceSections.map { it.channel.name }.distinct(),
            assetRefs = request.assetRefs,
        )
        taskLocalDataSource.save(runningTask)

        val generation = runtime.generateText(
            config = request.sessionConfig,
            prompt = buildPrompt(
                sourceText = request.sourceText,
                sourceSections = request.sourceSections,
            ),
            maxNewTokens = 384,
        )
        if (!generation.success) {
            val failedTask = runningTask.copy(
                status = "FAILED",
                summary = generation.errorMessage ?: "generation failed",
            )
            taskLocalDataSource.save(failedTask)
            return StructuredMemoTaskExecutionResult(
                task = failedTask,
                rawOutput = generation.outputText,
                errorMessage = generation.errorMessage,
            )
        }

        val rawOutput = generation.outputText.orEmpty()
        val memo = runCatching {
            parseStructuredMemo(taskId, rawOutput, request)
        }.getOrElse { error ->
            val failedTask = runningTask.copy(
                status = "FAILED",
                summary = error.message ?: "parse failed",
            )
            taskLocalDataSource.save(failedTask)
            return StructuredMemoTaskExecutionResult(
                task = failedTask,
                rawOutput = rawOutput,
                errorMessage = error.message ?: "failed to parse structured memo JSON",
            )
        }

        memoLocalDataSource.save(memo)
        val completedTask = runningTask.copy(
            status = "COMPLETED",
            summary = memo.oneLineSummary,
        )
        taskLocalDataSource.save(completedTask)
        return StructuredMemoTaskExecutionResult(
            task = completedTask,
            memo = memo,
            rawOutput = rawOutput,
        )
    }

    private fun buildPrompt(
        sourceText: String,
        sourceSections: List<SourceInputSection>,
    ): String {
        val sectionBlock = if (sourceSections.isEmpty()) {
            sourceText
        } else {
            buildString {
                sourceSections.forEach { section ->
                    appendLine("[${section.label}]")
                    appendLine(section.content.trim())
                    appendLine()
                }
                appendLine("[统一文本上下文]")
                append(sourceText.trim())
            }.trim()
        }
        return """
            你是一个本地端侧创意纪要助手。请根据下面的输入内容，输出严格 JSON，不要输出 Markdown，不要输出解释，不要加代码块。
            输入可能来自图片描述、OCR 文本、录音转写和补充文字。请先整合多源信息，再生成统一纪要。
            
            JSON schema:
            {
              "oneLineSummary": "string",
              "background": "string",
              "topics": [{"name": "string", "summary": "string"}],
              "facts": ["string"],
              "decisions": ["string"],
              "actionItems": [{"task": "string", "owner": "string", "deadline": "string"}],
              "risks": ["string"],
              "quotes": ["string"],
              "tags": ["string"]
            }
            
            输入内容:
            $sectionBlock
        """.trimIndent()
    }

    private fun parseStructuredMemo(
        taskId: String,
        rawOutput: String,
        request: StructuredMemoTaskRequest,
    ): StructuredMemo {
        val normalized = extractJsonObject(rawOutput)
        val json = JSONObject(normalized)
        return StructuredMemo(
            taskId = taskId,
            oneLineSummary = json.optString("oneLineSummary"),
            background = json.optString("background"),
            topics = json.optJSONArray("topics").toTopicSummaries(),
            facts = json.optJSONArray("facts").toStringList(),
            decisions = json.optJSONArray("decisions").toStringList(),
            actionItems = json.optJSONArray("actionItems").toActionItems(),
            risks = json.optJSONArray("risks").toStringList(),
            quotes = json.optJSONArray("quotes").toStringList(),
            tags = json.optJSONArray("tags").toStringList(),
            rawJson = normalized,
            sourceTrace = buildList {
                add("local-task-executor")
                add("qwen-mnn")
                addAll(request.sourceSections.map { "input:${it.channel.name.lowercase()}" })
            },
            sourceOutline = request.sourceSections.map { section ->
                "${section.label}: ${section.content.replace("\n", " ").take(120)}"
            },
            assetRefs = request.assetRefs,
        )
    }
}

private fun extractJsonObject(rawOutput: String): String {
    val unwrapped = rawOutput
        .replace("```json", "")
        .replace("```JSON", "")
        .replace("```", "")
        .trim()
    val start = unwrapped.indexOf('{')
    val end = unwrapped.lastIndexOf('}')
    require(start >= 0 && end > start) { "No JSON object found in model output." }
    return unwrapped.substring(start, end + 1)
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            add(optString(index))
        }
    }
}

private fun JSONArray?.toTopicSummaries(): List<TopicSummary> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            add(
                TopicSummary(
                    name = item.optString("name"),
                    summary = item.optString("summary"),
                ),
            )
        }
    }
}

private fun JSONArray?.toActionItems(): List<ActionItem> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            add(
                ActionItem(
                    task = item.optString("task"),
                    owner = item.optString("owner"),
                    deadline = item.optString("deadline").takeIf { it.isNotBlank() },
                ),
            )
        }
    }
}
