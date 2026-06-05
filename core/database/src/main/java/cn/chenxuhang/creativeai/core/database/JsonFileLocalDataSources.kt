package cn.chenxuhang.creativeai.core.database

import cn.chenxuhang.creativeai.core.model.ActionItem
import cn.chenxuhang.creativeai.core.model.MemoTask
import cn.chenxuhang.creativeai.core.model.ProcessingMode
import cn.chenxuhang.creativeai.core.model.SourceInputChannel
import cn.chenxuhang.creativeai.core.model.SourceInputSection
import cn.chenxuhang.creativeai.core.model.StructuredMemo
import cn.chenxuhang.creativeai.core.model.TopicSummary
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class JsonFileMemoTaskLocalDataSource(
    private val file: File,
) : MemoTaskLocalDataSource {
    override fun getAll(): List<MemoTask> = synchronized(file) {
        readJsonArray(file).map { item ->
            MemoTask(
                id = item.optString("id"),
                title = item.optString("title"),
                type = item.optString("type"),
                status = item.optString("status"),
                summary = item.optString("summary"),
                processingMode = item.optString("processingMode")
                    .takeIf { it.isNotBlank() }
                    ?.let { ProcessingMode.valueOf(it) }
                    ?: ProcessingMode.LOCAL_ONLY,
                sourceText = item.optString("sourceText"),
                sourceSections = item.optJSONArray("sourceSections").toSourceInputSections(),
                sourceChannels = item.optJSONArray("sourceChannels").toStringList(),
                assetRefs = item.optJSONArray("assetRefs").toStringList(),
                isArchived = item.optBoolean("isArchived", false),
                archiveFolder = item.optString("archiveFolder").takeIf { it.isNotBlank() },
            )
        }
    }

    override fun save(task: MemoTask) = synchronized(file) {
        val current = getAll().toMutableList()
        current.removeAll { it.id == task.id }
        current += task
        writeJsonArray(
            file = file,
            items = current.map { memoTask ->
                JSONObject().apply {
                    put("id", memoTask.id)
                    put("title", memoTask.title)
                    put("type", memoTask.type)
                    put("status", memoTask.status)
                    put("summary", memoTask.summary)
                    put("processingMode", memoTask.processingMode.name)
                    put("sourceText", memoTask.sourceText)
                    put("sourceSections", JSONArray().apply {
                        memoTask.sourceSections.forEach { sourceSection ->
                            put(
                                JSONObject().apply {
                                    put("channel", sourceSection.channel.name)
                                    put("label", sourceSection.label)
                                    put("content", sourceSection.content)
                                },
                            )
                        }
                    })
                    put("sourceChannels", JSONArray(memoTask.sourceChannels))
                    put("assetRefs", JSONArray(memoTask.assetRefs))
                    put("isArchived", memoTask.isArchived)
                    put("archiveFolder", memoTask.archiveFolder)
                }
            },
        )
    }

    override fun delete(taskId: String) = synchronized(file) {
        val current = getAll().filterNot { it.id == taskId }
        writeJsonArray(
            file = file,
            items = current.map { memoTask ->
                JSONObject().apply {
                    put("id", memoTask.id)
                    put("title", memoTask.title)
                    put("type", memoTask.type)
                    put("status", memoTask.status)
                    put("summary", memoTask.summary)
                    put("processingMode", memoTask.processingMode.name)
                    put("sourceText", memoTask.sourceText)
                    put("sourceSections", JSONArray().apply {
                        memoTask.sourceSections.forEach { sourceSection ->
                            put(
                                JSONObject().apply {
                                    put("channel", sourceSection.channel.name)
                                    put("label", sourceSection.label)
                                    put("content", sourceSection.content)
                                },
                            )
                        }
                    })
                    put("sourceChannels", JSONArray(memoTask.sourceChannels))
                    put("assetRefs", JSONArray(memoTask.assetRefs))
                    put("isArchived", memoTask.isArchived)
                    put("archiveFolder", memoTask.archiveFolder)
                }
            },
        )
    }

    fun describe(): String = "JsonFileMemoTaskLocalDataSource(${file.absolutePath})"
}

class JsonFileStructuredMemoLocalDataSource(
    private val file: File,
) : StructuredMemoLocalDataSource {
    override fun getAll(): List<StructuredMemo> = synchronized(file) {
        readJsonArray(file).map { item ->
            StructuredMemo(
                taskId = item.optString("taskId"),
                oneLineSummary = item.optString("oneLineSummary"),
                background = item.optString("background"),
                topics = item.optJSONArray("topics").toTopicSummaries(),
                facts = item.optJSONArray("facts").toStringList(),
                decisions = item.optJSONArray("decisions").toStringList(),
                actionItems = item.optJSONArray("actionItems").toActionItems(),
                risks = item.optJSONArray("risks").toStringList(),
                quotes = item.optJSONArray("quotes").toStringList(),
                tags = item.optJSONArray("tags").toStringList(),
                rawJson = item.optString("rawJson"),
                sourceTrace = item.optJSONArray("sourceTrace").toStringList(),
                sourceOutline = item.optJSONArray("sourceOutline").toStringList(),
                assetRefs = item.optJSONArray("assetRefs").toStringList(),
            )
        }
    }

    override fun findByTaskId(taskId: String): StructuredMemo? = synchronized(file) {
        getAll().lastOrNull { it.taskId == taskId }
    }

    override fun save(memo: StructuredMemo) = synchronized(file) {
        val current = getAll().toMutableList()
        current.removeAll { it.taskId == memo.taskId }
        current += memo
        writeJsonArray(
            file = file,
            items = current.map { structuredMemo ->
                JSONObject().apply {
                    put("taskId", structuredMemo.taskId)
                    put("oneLineSummary", structuredMemo.oneLineSummary)
                    put("background", structuredMemo.background)
                    put("topics", JSONArray().apply {
                        structuredMemo.topics.forEach { topic ->
                            put(
                                JSONObject().apply {
                                    put("name", topic.name)
                                    put("summary", topic.summary)
                                },
                            )
                        }
                    })
                    put("facts", JSONArray(structuredMemo.facts))
                    put("decisions", JSONArray(structuredMemo.decisions))
                    put("actionItems", JSONArray().apply {
                        structuredMemo.actionItems.forEach { actionItem ->
                            put(
                                JSONObject().apply {
                                    put("task", actionItem.task)
                                    put("owner", actionItem.owner)
                                    put("deadline", actionItem.deadline)
                                },
                            )
                        }
                    })
                    put("risks", JSONArray(structuredMemo.risks))
                    put("quotes", JSONArray(structuredMemo.quotes))
                    put("tags", JSONArray(structuredMemo.tags))
                    put("rawJson", structuredMemo.rawJson)
                    put("sourceTrace", JSONArray(structuredMemo.sourceTrace))
                    put("sourceOutline", JSONArray(structuredMemo.sourceOutline))
                    put("assetRefs", JSONArray(structuredMemo.assetRefs))
                }
            },
        )
    }

    override fun delete(taskId: String) = synchronized(file) {
        val current = getAll().filterNot { it.taskId == taskId }
        writeJsonArray(
            file = file,
            items = current.map { structuredMemo ->
                JSONObject().apply {
                    put("taskId", structuredMemo.taskId)
                    put("oneLineSummary", structuredMemo.oneLineSummary)
                    put("background", structuredMemo.background)
                    put("topics", JSONArray().apply {
                        structuredMemo.topics.forEach { topic ->
                            put(
                                JSONObject().apply {
                                    put("name", topic.name)
                                    put("summary", topic.summary)
                                },
                            )
                        }
                    })
                    put("facts", JSONArray(structuredMemo.facts))
                    put("decisions", JSONArray(structuredMemo.decisions))
                    put("actionItems", JSONArray().apply {
                        structuredMemo.actionItems.forEach { actionItem ->
                            put(
                                JSONObject().apply {
                                    put("task", actionItem.task)
                                    put("owner", actionItem.owner)
                                    put("deadline", actionItem.deadline)
                                },
                            )
                        }
                    })
                    put("risks", JSONArray(structuredMemo.risks))
                    put("quotes", JSONArray(structuredMemo.quotes))
                    put("tags", JSONArray(structuredMemo.tags))
                    put("rawJson", structuredMemo.rawJson)
                    put("sourceTrace", JSONArray(structuredMemo.sourceTrace))
                    put("sourceOutline", JSONArray(structuredMemo.sourceOutline))
                    put("assetRefs", JSONArray(structuredMemo.assetRefs))
                }
            },
        )
    }

    fun describe(): String = "JsonFileStructuredMemoLocalDataSource(${file.absolutePath})"
}

private fun readJsonArray(file: File): List<JSONObject> {
    if (!file.exists() || file.readText().isBlank()) {
        return emptyList()
    }
    val array = JSONArray(file.readText())
    return buildList {
        for (index in 0 until array.length()) {
            add(array.getJSONObject(index))
        }
    }
}

private fun writeJsonArray(file: File, items: List<JSONObject>) {
    file.parentFile?.mkdirs()
    val array = JSONArray()
    items.forEach(array::put)
    file.writeText(array.toString(2))
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

private fun JSONArray?.toSourceInputSections(): List<SourceInputSection> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            val channelName = item.optString("channel")
            val channel = runCatching { SourceInputChannel.valueOf(channelName) }.getOrNull() ?: continue
            add(
                SourceInputSection(
                    channel = channel,
                    label = item.optString("label"),
                    content = item.optString("content"),
                ),
            )
        }
    }
}
