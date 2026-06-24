package cn.chenxuhang.creativeai.ai.orchestrator

import cn.chenxuhang.creativeai.ai.mnn.MnnRuntime
import cn.chenxuhang.creativeai.core.database.MemoTaskLocalDataSource
import cn.chenxuhang.creativeai.core.database.StructuredMemoLocalDataSource
import cn.chenxuhang.creativeai.core.model.ActionItem
import cn.chenxuhang.creativeai.core.model.MemoTask
import cn.chenxuhang.creativeai.core.model.MnnSessionConfig
import cn.chenxuhang.creativeai.core.model.ProcessingMode
import cn.chenxuhang.creativeai.core.model.SourceInputChannel
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
            sourceText = request.sourceText,
            sourceSections = request.sourceSections,
            sourceChannels = request.sourceSections.map { it.channel.name }.distinct(),
            assetRefs = request.assetRefs,
        )
        taskLocalDataSource.save(runningTask)

        val preparedSections = prepareSectionsForGeneration(request)
        val preparedSourceText = composeGenerationSourceText(
            sections = preparedSections,
            fallbackSourceText = request.sourceText,
        )
        val generation = runtime.generateText(
            config = request.sessionConfig,
            prompt = buildPrompt(
                sourceText = preparedSourceText,
                sourceSections = preparedSections,
            ),
            maxNewTokens = request.sessionConfig.generationMaxNewTokens,
        )
        if (!generation.success) {
            val fallbackMemo = buildFallbackMemo(
                taskId = taskId,
                rawOutput = generation.outputText.orEmpty(),
                repairedOutput = "",
                request = request,
                fallbackReason = generation.errorMessage ?: "generation-failed",
            )
            memoLocalDataSource.save(fallbackMemo)
            val completedFallbackTask = runningTask.copy(
                status = "COMPLETED",
                summary = fallbackMemo.oneLineSummary,
            )
            taskLocalDataSource.save(completedFallbackTask)
            return StructuredMemoTaskExecutionResult(
                task = completedFallbackTask,
                memo = fallbackMemo,
                rawOutput = generation.outputText,
                errorMessage = generation.errorMessage ?: "generation-failed",
            )
        }

        val rawOutput = generation.outputText.orEmpty()
        val memo = parseStructuredMemoSafely(
            taskId = taskId,
            rawOutput = rawOutput,
            request = request,
        )

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
            你现在是“结构化纪要生成器”，不是聊天助手。
            你的唯一任务：把“原始内容”整理成简洁、清晰、可读的结构化纪要 JSON。
            不要输出思考过程，不要输出 Thinking Process，不要解释，不要复述任务，不要输出 Markdown，不要加代码块，不要省略字段。
            输出的第一个字符必须是 {，最后一个字符必须是 }。
            输入可能来自图片描述、OCR 文本、录音转写和补充文字。请先整合多源信息，再生成统一纪要。
            顶层字段必须完整返回；如果无法判断，字符串字段填空字符串，数组字段填 []。
            请优先做“概括”，不要照抄原文，不要铺陈细节。
            除 oneLineSummary 和 background 外，所有数组尽量精简：
            - topics 最多 2 个
            - facts 最多 3 条
            - decisions 最多 2 条
            - actionItems 最多 3 条
            - risks 最多 2 条
            - quotes 默认留空，除非原文出现非常关键的原话
            - tags 最多 4 个，必须是简短中文词组
            actionItems 中每个对象必须包含 task、owner、deadline 三个键；如果没有负责人或截止时间，填空字符串。
            topics 中每个对象必须包含 name、summary 两个键。
            
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
            
            原始内容开始
            $sectionBlock
            原始内容结束
        """.trimIndent()
    }

    private fun prepareSectionsForGeneration(
        request: StructuredMemoTaskRequest,
    ): List<SourceInputSection> {
        val normalizedSections = request.sourceSections
            .map { it.copy(content = it.content.trim()) }
            .filter { it.content.isNotBlank() }
        if (normalizedSections.isEmpty()) return emptyList()

        val totalChars = normalizedSections.sumOf { it.content.length }
        if (totalChars <= request.sessionConfig.maxPromptChars) {
            return normalizedSections
        }

        val condensedSections = normalizedSections.map { section ->
            val desiredLimit = when (section.channel) {
                SourceInputChannel.AUDIO_TRANSCRIPT,
                SourceInputChannel.OCR_TEXT,
                SourceInputChannel.DOCUMENT_TEXT,
                -> request.sessionConfig.chunkSoftLimitChars
                else -> request.sessionConfig.maxPromptChars / normalizedSections.size
            }.coerceAtLeast(800)

            if (section.content.length <= desiredLimit) {
                section
            } else {
                section.copy(
                    label = "${section.label}（压缩）",
                    content = condenseSectionContent(
                        section = section,
                        config = request.sessionConfig,
                    ),
                )
            }
        }

        return trimSectionsToPromptBudget(
            sections = condensedSections,
            maxPromptChars = request.sessionConfig.maxPromptChars,
        )
    }

    private fun condenseSectionContent(
        section: SourceInputSection,
        config: MnnSessionConfig,
    ): String {
        val chunks = splitTextIntoChunks(
            text = section.content,
            softLimitChars = config.chunkSoftLimitChars,
        )
        if (chunks.size <= 1) {
            return heuristicCompactText(section.content, hardLimitChars = config.chunkSoftLimitChars)
        }

        val chunkSummaries = chunks.mapIndexed { index, chunk ->
            summarizeChunk(
                section = section,
                chunk = chunk,
                chunkIndex = index,
                chunkCount = chunks.size,
                config = config,
            )
        }
        return mergeChunkSummaries(
            section = section,
            chunkSummaries = chunkSummaries,
            config = config,
        )
    }

    private fun summarizeChunk(
        section: SourceInputSection,
        chunk: String,
        chunkIndex: Int,
        chunkCount: Int,
        config: MnnSessionConfig,
    ): String {
        val prompt = """
            你现在在做长内容预压缩，不是最终纪要。
            请把下面这段${section.label}提炼成尽量短的中文要点，保留事实、结论、负责人、时间和数字。
            不要解释，不要输出 JSON，不要输出 Markdown 标题。
            输出最多 5 行，每行一句。
            
            当前片段：${chunkIndex + 1}/$chunkCount
            内容开始
            ${chunk.trim()}
            内容结束
        """.trimIndent()
        val result = runtime.generateText(
            config = config,
            prompt = prompt,
            maxNewTokens = 112,
        )
        return result.outputText
            ?.takeIf { result.success }
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: heuristicCompactText(chunk, hardLimitChars = config.chunkSoftLimitChars / 2)
    }

    private fun mergeChunkSummaries(
        section: SourceInputSection,
        chunkSummaries: List<String>,
        config: MnnSessionConfig,
    ): String {
        var rollingSummary = chunkSummaries.firstOrNull().orEmpty()
        if (chunkSummaries.size == 1) return rollingSummary

        chunkSummaries.drop(1).forEach { nextSummary ->
            val prompt = """
                你现在在合并长内容摘要，不是最终纪要。
                请把两段${section.label}摘要合并成更短的统一版本，去掉重复，保留最重要的事实、结论、负责人、时间和数字。
                不要解释，不要输出 JSON。
                输出最多 8 行，每行一句。
                
                已有摘要：
                ${rollingSummary.trim()}
                
                新片段摘要：
                ${nextSummary.trim()}
            """.trimIndent()
            val merged = runtime.generateText(
                config = config,
                prompt = prompt,
                maxNewTokens = 128,
            )
            rollingSummary = merged.outputText
                ?.takeIf { merged.success }
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: listOf(rollingSummary, nextSummary)
                    .joinToString("\n")
                    .let { heuristicCompactText(it, hardLimitChars = config.maxPromptChars / 2) }
        }
        return heuristicCompactText(
            text = rollingSummary,
            hardLimitChars = config.maxPromptChars / 2,
        )
    }

    private fun splitTextIntoChunks(
        text: String,
        softLimitChars: Int,
    ): List<String> {
        val normalizedParagraphs = text
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
        if (normalizedParagraphs.isEmpty()) return emptyList()

        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        normalizedParagraphs.forEach { paragraph ->
            if (current.isNotEmpty() && current.length + paragraph.length + 1 > softLimitChars) {
                chunks += current.toString().trim()
                current.clear()
            }
            if (paragraph.length <= softLimitChars) {
                if (current.isNotEmpty()) current.append('\n')
                current.append(paragraph)
            } else {
                paragraph.chunked(softLimitChars).forEach { slice ->
                    if (current.isNotEmpty()) {
                        chunks += current.toString().trim()
                        current.clear()
                    }
                    chunks += slice.trim()
                }
            }
        }
        if (current.isNotEmpty()) {
            chunks += current.toString().trim()
        }
        return chunks.filter { it.isNotBlank() }
    }

    private fun heuristicCompactText(
        text: String,
        hardLimitChars: Int,
    ): String {
        val lines = text
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line -> line.replace(Regex("\\s+"), " ") }
            .distinct()
            .toList()
        if (lines.isEmpty()) return ""

        val kept = mutableListOf<String>()
        var charCount = 0
        for (line in lines) {
            val projected = charCount + line.length + if (kept.isEmpty()) 0 else 1
            if (projected > hardLimitChars && kept.isNotEmpty()) break
            kept += line
            charCount = projected
            if (kept.size >= 8) break
        }
        return kept.joinToString(separator = "\n")
    }

    private fun trimSectionsToPromptBudget(
        sections: List<SourceInputSection>,
        maxPromptChars: Int,
    ): List<SourceInputSection> {
        val totalChars = sections.sumOf { it.label.length + it.content.length + 8 }
        if (totalChars <= maxPromptChars) return sections

        val budgetPerSection = (maxPromptChars / sections.size).coerceAtLeast(600)
        return sections.map { section ->
            val trimmed = heuristicCompactText(
                text = section.content,
                hardLimitChars = budgetPerSection,
            )
            section.copy(content = trimmed.ifBlank { section.content.take(budgetPerSection) })
        }
    }

    private fun composeGenerationSourceText(
        sections: List<SourceInputSection>,
        fallbackSourceText: String,
    ): String {
        if (sections.isEmpty()) return fallbackSourceText.trim()
        return buildString {
            sections.forEachIndexed { index, section ->
                appendLine("[${section.label}]")
                appendLine(section.content.trim())
                if (index != sections.lastIndex) appendLine()
            }
        }.trim()
    }

    private fun buildRepairPrompt(
        rawOutput: String,
    ): String {
        return """
            请把下面这段模型输出整理成严格 JSON。
            不要解释，不要输出 Markdown，不要输出代码块，不要输出 Thinking Process，只能输出一个完整 JSON object。
            输出的第一个字符必须是 {，最后一个字符必须是 }。
            所有字段都必须返回；字符串字段缺失时填空字符串，数组字段缺失时填 []。
            
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
            
            待整理输出:
            ${rawOutput.trim()}
        """.trimIndent()
    }

    private fun parseStructuredMemoSafely(
        taskId: String,
        rawOutput: String,
        request: StructuredMemoTaskRequest,
    ): StructuredMemo {
        runCatching {
            parseStructuredMemo(taskId, rawOutput, request)
        }.getOrNull()?.let { return it }

        return buildFallbackMemo(
            taskId = taskId,
            rawOutput = rawOutput,
            repairedOutput = "",
            request = request,
        )
    }

    private fun parseStructuredMemo(
        taskId: String,
        rawOutput: String,
        request: StructuredMemoTaskRequest,
    ): StructuredMemo {
        val normalized = extractJsonObject(rawOutput)
        val json = JSONObject(normalized)
        val topics = json.flexTopicSummaries("topics", "主题", "topicSummaries")
        val facts = json.flexStringList("facts", "事实", "keyFacts")
        val decisions = json.flexStringList("decisions", "结论", "conclusions")
        val actionItems = json.flexActionItems("actionItems", "行动项", "todos", "待办")
        val risks = json.flexStringList("risks", "风险")
        val quotes = json.flexStringList("quotes", "引用")
        val tags = json.flexTagList("tags", "标签", "keywords")
        val summary = json.flexString("oneLineSummary", "summary", "一句话总结", "总结")
            .ifBlank {
                decisions.firstOrNull()
                    ?: facts.firstOrNull()
                    ?: topics.firstOrNull()?.summary
                    ?: request.title
            }
        val background = json.flexString("background", "背景", "context")
            .ifBlank {
                deriveBackgroundFromRequest(request)
            }
        return normalizeStructuredMemo(
            StructuredMemo(
                taskId = taskId,
                oneLineSummary = summary,
                background = background,
                topics = topics,
                facts = facts,
                decisions = decisions,
                actionItems = actionItems,
                risks = risks,
                quotes = quotes,
                tags = tags,
                rawJson = normalized,
                sourceTrace = buildList {
                    add("local-task-executor")
                    add("qwen-mnn")
                    addAll(request.sourceSections.map { "input:${it.channel.name.lowercase()}" })
                },
                sourceOutline = request.sourceSections
                    .map { it.label }
                    .distinct(),
                assetRefs = request.assetRefs,
            ),
            request = request,
        )
    }

    private fun buildFallbackMemo(
        taskId: String,
        rawOutput: String,
        repairedOutput: String,
        request: StructuredMemoTaskRequest,
        fallbackReason: String = "model-output-was-not-valid-json",
    ): StructuredMemo {
        val candidateOutput = if (repairedOutput.isNotBlank()) repairedOutput else rawOutput
        val cleanedOutput = sanitizeModelOutput(candidateOutput)
        val extractedSummary = extractNamedStringField(cleanedOutput, "oneLineSummary")
            ?: extractNamedStringField(cleanedOutput, "summary")
        val extractedBackground = extractNamedStringField(cleanedOutput, "background")
        val extractedTopics = extractTopicSummariesFromPartialJson(cleanedOutput)
        val extractedFacts = extractFactLinesFromPartialJson(cleanedOutput)
        val extractedActions = extractActionItemsFromPartialJson(cleanedOutput)
        val lines = cleanedOutput.toReadableLines()
        val summary = extractedSummary
            ?.takeUnless(::looksLikePromptLeakOrPlaceholder)
            ?: deriveSummaryFromRequest(request)
            ?: lines.firstOrNull { !looksLikePromptLeakOrPlaceholder(it) }
            ?: request.title
        val facts = extractedFacts.ifEmpty {
            lines
                .drop(1)
                .filterNot(::looksLikePromptLeakOrPlaceholder)
                .filterNot(::looksLikeSchemaFragment)
                .map(::normalizeKeyPointCandidate)
                .filter { it.isNotBlank() }
                .take(4)
        }
        val background = extractedBackground
            ?: deriveBackgroundFromRequest(request)
        val tags = request.sourceSections
            .map { it.label }
            .distinct()
        val fallbackJson = JSONObject().apply {
            put("oneLineSummary", summary)
            put("background", background)
            put(
                "topics",
                JSONArray().apply {
                    extractedTopics.forEach { topic ->
                        put(
                            JSONObject().apply {
                                put("name", topic.name)
                                put("summary", topic.summary)
                            },
                        )
                    }
                },
            )
            put("facts", JSONArray(facts))
            put("decisions", JSONArray())
            put(
                "actionItems",
                JSONArray().apply {
                    extractedActions.forEach { actionItem ->
                        put(
                            JSONObject().apply {
                                put("task", actionItem.task)
                                put("owner", actionItem.owner)
                                put("deadline", actionItem.deadline.orEmpty())
                            },
                        )
                    }
                },
            )
            put("risks", JSONArray())
            put("quotes", JSONArray())
            put("tags", JSONArray(tags))
            put(
                "_fallbackReason",
                fallbackReason,
            )
            put(
                "_rawOutputPreview",
                cleanedOutput.take(1000),
            )
        }.toString()
        return normalizeStructuredMemo(
            StructuredMemo(
                taskId = taskId,
                oneLineSummary = summary,
                background = background,
                topics = extractedTopics,
                facts = facts,
                decisions = emptyList(),
                actionItems = extractedActions,
                risks = emptyList(),
                quotes = emptyList(),
                tags = tags,
                rawJson = fallbackJson,
                sourceTrace = buildList {
                    add("local-task-executor")
                    add("qwen-mnn")
                    add("fallback-structured-memo")
                    add("generation-fallback:$fallbackReason")
                    addAll(request.sourceSections.map { "input:${it.channel.name.lowercase()}" })
                },
                sourceOutline = request.sourceSections
                    .map { it.label }
                    .distinct(),
                assetRefs = request.assetRefs,
            ),
            request = request,
        )
    }
}

private fun extractJsonObject(rawOutput: String): String {
    val unwrapped = sanitizeModelOutput(rawOutput)
    val start = unwrapped.indexOf('{')
    val end = unwrapped.lastIndexOf('}')
    require(start >= 0 && end > start) { "No JSON object found in model output." }
    return unwrapped.substring(start, end + 1)
}

private fun sanitizeModelOutput(
    rawOutput: String,
): String {
    return rawOutput
        .replace(Regex("""<think>[\s\S]*?</think>"""), "")
        .replace("</think>", "")
        .replace("<think>", "")
        .replace(Regex("""</?tool_call[^>]*>"""), "")
        .replace("```json", "")
        .replace("```JSON", "")
        .replace("```", "")
        .replace("\uFEFF", "")
        .trim()
}

private fun String.toReadableLines(): List<String> {
    return lineSequence()
        .map { it.trim() }
        .map { it.trimStart('-', '•', '*', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '、', ')', '）') }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toList()
}

private fun normalizeStructuredMemo(
    memo: StructuredMemo,
    request: StructuredMemoTaskRequest,
): StructuredMemo {
    val cleanedSummary = (
        cleanMemoText(memo.oneLineSummary)
            .takeUnless(::looksLikeBrokenMemoField)
            ?.takeUnless(::looksLikeSchemaFragment)
            ?.takeUnless(::looksLikePromptLeakOrPlaceholder)
            ?.takeUnless { it.length < 8 }
            ?: deriveSummaryFromMemo(memo, request)
        )
        .compactSummary(88)
    val cleanedBackground = cleanMemoText(memo.background)
        .takeUnless(::looksLikeBrokenMemoField)
        ?.takeUnless(::looksLikeSchemaFragment)
        ?.takeUnless(::looksLikePromptLeakOrPlaceholder)
        ?.compactParagraph(160)
        ?: deriveBackgroundFromRequest(request)
    val cleanedTopics = memo.topics
        .mapNotNull { topic ->
            val name = cleanMemoText(topic.name)
            val summary = cleanMemoText(topic.summary)
            if (
                (looksLikeBrokenMemoField(name) && looksLikeBrokenMemoField(summary)) ||
                containsUnreadableNoise(name) ||
                containsUnreadableNoise(summary)
            ) {
                null
            } else {
                TopicSummary(
                    name = name.ifBlank { "主题" }.take(18),
                    summary = summary.ifBlank { name }.compactSummary(56),
                )
            }
        }
        .distinctBy { "${it.name}|${it.summary}" }
        .take(2)
    val cleanedModelFacts = memo.facts
        .map(::cleanMemoText)
        .filterNot(::looksLikeBrokenMemoField)
        .filterNot(::looksLikeSchemaFragment)
        .filterNot(::looksLikePromptLeakOrPlaceholder)
        .filterNot(::containsUnreadableNoise)
        .filterNot { it.length < 4 }
        .map(::normalizeKeyPointCandidate)
        .filter { it.isNotBlank() }
        .distinct()
        .take(3)
    val cleanedFacts = extractKeyPointsFromSource(request).ifEmpty { cleanedModelFacts }
    val cleanedDecisions = memo.decisions
        .map(::cleanMemoText)
        .filterNot(::looksLikeBrokenMemoField)
        .filterNot(::looksLikeSchemaFragment)
        .filterNot(::looksLikePromptLeakOrPlaceholder)
        .filterNot(::containsUnreadableNoise)
        .filterNot { it.length < 4 }
        .map { it.compactSummary(48) }
        .distinct()
        .take(2)
    val cleanedActionItems = memo.actionItems
        .mapNotNull { actionItem ->
            val task = cleanMemoText(actionItem.task)
            val owner = cleanMemoText(actionItem.owner)
            val deadline = actionItem.deadline?.let(::cleanMemoText)
            if (
                looksLikeBrokenMemoField(task) ||
                looksLikeSchemaFragment(task) ||
                containsUnreadableNoise(task)
            ) {
                null
            } else {
                ActionItem(
                    task = task.compactSummary(34),
                    owner = owner.takeUnless(::looksLikeBrokenMemoField).orEmpty().take(16),
                    deadline = deadline?.takeUnless(::looksLikeBrokenMemoField)?.take(20),
                )
            }
        }
        .distinctBy { "${it.task}|${it.owner}|${it.deadline.orEmpty()}" }
        .take(3)
    val cleanedRisks = memo.risks
        .map(::cleanMemoText)
        .filterNot(::looksLikeBrokenMemoField)
        .filterNot(::looksLikeSchemaFragment)
        .filterNot(::containsUnreadableNoise)
        .map { it.compactSummary(40) }
        .distinct()
        .take(2)
    val cleanedTags = memo.tags
        .map(::cleanMemoText)
        .filterNot(::looksLikeBrokenMemoField)
        .filterNot { it == "补充文字" || it == "OCR 文本" || it == "录音转写文本" || it == "图片内容补充" || it == "文字" }
        .flatMap { it.split(Regex("""[\s,/，、]+""")) }
        .map(String::trim)
        .filter { it.isNotBlank() && it.length in 2..10 }
        .distinct()
        .take(4)
        .ifEmpty {
            inferTagsFromRequest(request)
        }
    val sourceOutline = request.sourceSections
        .map { it.label }
        .distinct()
    val normalizedMemo = memo.copy(
        oneLineSummary = cleanedSummary,
        background = cleanedBackground,
        topics = cleanedTopics,
        facts = cleanedFacts,
        decisions = cleanedDecisions,
        actionItems = cleanedActionItems,
        risks = cleanedRisks,
        quotes = emptyList(),
        tags = cleanedTags,
        sourceOutline = sourceOutline,
    )
    return normalizedMemo.copy(
        rawJson = buildCanonicalMemoJson(normalizedMemo),
    )
}

private fun deriveSummaryFromMemo(
    memo: StructuredMemo,
    request: StructuredMemoTaskRequest,
): String {
    val candidate = listOfNotNull(
        memo.decisions.firstOrNull(),
        memo.facts.firstOrNull(),
        memo.topics.firstOrNull()?.summary,
        request.title.takeUnless { it.isBlank() },
        request.sourceText.toReadableLines().firstOrNull(),
    )
        .map(::cleanMemoText)
        .firstOrNull {
            !looksLikeBrokenMemoField(it) &&
                !looksLikeSchemaFragment(it) &&
                !looksLikePromptLeakOrPlaceholder(it)
        }
        .orEmpty()
    return candidate.compactSummary(88)
}

private fun deriveSummaryFromRequest(
    request: StructuredMemoTaskRequest,
): String? {
    val keyPoints = extractKeyPointsFromSource(request)
    val firstPoint = keyPoints.firstOrNull()
    if (!firstPoint.isNullOrBlank()) {
        return if (firstPoint.length <= 18) {
            "围绕${firstPoint}进行纪要整理"
        } else {
            firstPoint.compactSummary(88)
        }
    }
    val background = deriveBackgroundFromRequest(request)
        .takeUnless { it.isBlank() || it == request.title }
    return background?.compactSummary(88)
}

private fun deriveBackgroundFromRequest(
    request: StructuredMemoTaskRequest,
): String {
    val candidates = request.sourceSections
        .filterNot { it.channel == SourceInputChannel.IMAGE_BRIEF }
        .flatMap { section ->
            section.content
                .lineSequence()
                .map(::cleanMemoText)
                .map(::normalizeKeyPointCandidate)
                .map(String::trim)
                .filter { it.isNotBlank() }
                .filterNot(::looksLikeStructuralHeading)
                .filterNot(::looksLikePromptLeakOrPlaceholder)
                .filterNot(::containsUnreadableNoise)
                .toList()
        }
        .filter { it.length >= 8 }
    return candidates
        .take(2)
        .joinToString("；")
        .takeIf { it.isNotBlank() }
        ?.compactParagraph(160)
        ?: request.title.compactParagraph(160)
}

private fun inferTagsFromRequest(
    request: StructuredMemoTaskRequest,
): List<String> {
    return request.sourceSections
        .map { section ->
            when (section.channel) {
                SourceInputChannel.IMAGE_BRIEF -> "图片"
                SourceInputChannel.OCR_TEXT -> "OCR"
                SourceInputChannel.DOCUMENT_TEXT -> "文档"
                SourceInputChannel.AUDIO_TRANSCRIPT -> "录音"
                SourceInputChannel.SUPPLEMENTAL_TEXT -> "文字"
            }
        }
        .distinct()
        .take(4)
}

private fun extractKeyPointsFromSource(
    request: StructuredMemoTaskRequest,
): List<String> {
    return request.sourceSections
        .filterNot { it.channel == SourceInputChannel.IMAGE_BRIEF }
        .flatMap { section ->
            section.content
                .lineSequence()
                .flatMap { line ->
                    val cleanedLine = normalizeKeyPointCandidate(cleanMemoText(line))
                    when {
                        cleanedLine.isBlank() -> emptySequence()
                        looksLikeStructuralHeading(cleanedLine) -> emptySequence()
                        else -> cleanedLine
                            .split(Regex("""[。！？!?；;]"""))
                            .asSequence()
                            .map(::normalizeKeyPointCandidate)
                    }
                }
                .map(String::trim)
                .filter { it.isNotBlank() }
                .filterNot(::looksLikeBrokenMemoField)
                .filterNot(::looksLikeSchemaFragment)
                .filterNot(::looksLikePromptLeakOrPlaceholder)
                .filterNot(::containsUnreadableNoise)
                .filter { it.length >= 4 }
        }
        .distinct()
        .take(3)
}

private fun stripSystemKeyPointPrefix(
    value: String,
): String {
    return value
        .replace(Regex("""^\[(图片\d+\s*(OCR|paper|摘要|要点))]\s*""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""^\[第\d+张图片识别文本]\s*"""), "")
        .replace(Regex("""^图片\d+\s*(OCR|paper|摘要|要点)\s*[:：]?\s*""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""^图片\d+\s*[:：]?\s*"""), "")
        .replace(Regex("""^第\d+张图片识别文本\s*[:：]?\s*"""), "")
        .replace(Regex("""^[（(\[]?\s*[-一二三四五六七八九十\d]+\s*[）)\].、．-]*\s*"""), "")
        .replace(Regex("""^[（(]\s*[一二三四五六七八九十\d]+\s*[）)]\s*"""), "")
        .replace(Regex("""^[一二三四五六七八九十]+\s*[、.．]\s*"""), "")
        .replace(Regex("""^\d+\s*[.、．)\uFF09]\s*"""), "")
        .trim()
}

private fun normalizeKeyPointCandidate(
    value: String,
): String {
    return stripSystemKeyPointPrefix(value)
        .replace(Regex("""^(原始内容开始|原始内容结束|统一文本上下文)\s*[:：]?\s*"""), "")
        .replace(Regex("""^(图片内容补充|OCR 文本|图片识别文本|录音转写文本|补充文字|版面理解)\s*[:：]?\s*"""), "")
        .replace(Regex("""^(待整理输出|原始数据未提供)\s*[:：]?\s*"""), "")
        .trim()
        .trim('"', '：', ':', '，', ',', '。', ';', '；')
        .compactSummary(48)
}

private fun looksLikeStructuralHeading(
    value: String,
): Boolean {
    val cleaned = value.trim()
    if (cleaned.isBlank()) return true
    if (cleaned.length <= 14 && looksLikeHeadingCandidate(cleaned)) return true
    return cleaned.matches(Regex("""^(研究目的及意义|研究目的|研究意义|理论方面|实践方面|摘要|引言|结论|背景)$"""))
}

private fun looksLikeHeadingCandidate(
    value: String,
): Boolean {
    if (value.isBlank()) return false
    if (value.length > 24) return false
    if (value.contains('：') || value.contains(':')) return false
    return value.none { it in setOf('。', '！', '？', '.', '!', '?', ';', '；') }
}

private fun buildCanonicalMemoJson(
    memo: StructuredMemo,
): String {
    return JSONObject().apply {
        put("oneLineSummary", memo.oneLineSummary)
        put("background", memo.background)
        put(
            "topics",
            JSONArray().apply {
                memo.topics.forEach { topic ->
                    put(
                        JSONObject().apply {
                            put("name", topic.name)
                            put("summary", topic.summary)
                        },
                    )
                }
            },
        )
        put("facts", JSONArray(memo.facts))
        put("decisions", JSONArray(memo.decisions))
        put(
            "actionItems",
            JSONArray().apply {
                memo.actionItems.forEach { actionItem ->
                    put(
                        JSONObject().apply {
                            put("task", actionItem.task)
                            put("owner", actionItem.owner)
                            put("deadline", actionItem.deadline.orEmpty())
                        },
                    )
                }
            },
        )
        put("risks", JSONArray(memo.risks))
        put("quotes", JSONArray(memo.quotes))
        put("tags", JSONArray(memo.tags))
        put("sourceOutline", JSONArray(memo.sourceOutline))
    }.toString(2)
}

private fun cleanMemoText(
    value: String,
): String {
    return value
        .replace(Regex("""<think>[\s\S]*?</think>"""), "")
        .replace("</think>", "")
        .replace("<think>", "")
        .replace(Regex("""</?tool_call[^>]*>"""), "")
        .replace("Thinking Process:", "")
        .replace("Analyze the Request:", "")
        .replace(Regex("""图片\d+\s*(OCR|paper|摘要|要点)?\s*[:：]?\s*""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\bpaper\b\s*[:：]?\s*""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""^(oneLineSummary|summary|background|topics|facts|actionItems|tags)\s*"?\s*:\s*"""), "")
        .replace(Regex("""^"+|"+$"""), "")
        .replace(Regex("""^[\{\}\[\],]+|[\{\}\[\],]+$"""), "")
        .replace("**", "")
        .replace("\\n", " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .trim('"', ',', ':', ';')
}

private fun looksLikeBrokenMemoField(
    value: String,
): Boolean {
    val cleaned = value.trim()
    return cleaned.isBlank()
        || cleaned == "{"
        || cleaned == "}"
        || cleaned == "<tool_call>"
        || cleaned == "</think>"
        || cleaned.startsWith("\"oneLineSummary\"")
        || cleaned.startsWith("\"background\"")
        || cleaned.startsWith("\"topics\"")
        || cleaned.startsWith("\"facts\"")
        || cleaned.startsWith("\"actionItems\"")
        || cleaned.startsWith("\"tags\"")
        || cleaned.startsWith("Thinking Process")
}

private fun looksLikeSchemaFragment(
    value: String,
): Boolean {
    return value.contains("\"oneLineSummary\"")
        || value.contains("\"background\"")
        || value.contains("\"topics\"")
        || value.contains("\"facts\"")
        || value.contains("\"actionItems\"")
        || value.contains("\"tags\"")
        || value.contains("Produce strict JSON output only")
        || value.contains("Do not explain")
}

private fun looksLikePromptLeakOrPlaceholder(
    value: String,
): Boolean {
    val cleaned = value.trim()
    return cleaned.contains("待整理输出") ||
        cleaned.contains("<tool_call>") ||
        cleaned.contains("</think>") ||
        cleaned.contains("原始数据未提供") ||
        cleaned.contains("JSON schema") ||
        cleaned.contains("Produce strict JSON output only") ||
        cleaned.contains("Do not explain") ||
        cleaned.contains("paper", ignoreCase = true) ||
        Regex("""图片\d+""").containsMatchIn(cleaned) ||
        cleaned.contains("原始内容开始") ||
        cleaned.contains("原始内容结束")
}

private fun containsUnreadableNoise(
    value: String,
): Boolean {
    val cleaned = value.trim()
    if (cleaned.isBlank()) return true
    if (cleaned.contains('�')) return true
    val noisyCharacters = cleaned.count { ch ->
        ch.isISOControl() ||
            (
                !ch.isLetterOrDigit() &&
                    ch !in setOf(' ', '\n', '，', '。', '！', '？', '；', '：', '、', ',', '.', ';', ':', '!', '?', '-', '—', '（', '）', '(', ')', '"', '\'')
                )
    }
    return noisyCharacters > cleaned.length / 4
}

private fun String.compactForMemo(
    maxLength: Int,
): String {
    return cleanMemoText(this).truncateAtSentenceBoundary(maxLength)
}

private fun String.compactParagraph(
    maxLength: Int,
): String {
    return cleanMemoText(this)
        .truncateAtSentenceBoundary(maxLength)
        .removeTrailingConnector()
}

private fun String.compactSummary(
    maxLength: Int,
): String {
    val cleaned = cleanMemoText(this)
        .removeJsonLikePrefix()
        .truncateAtNaturalBoundary(maxLength)
        .removeTrailingConnector()
    return cleaned
}

private fun String.truncateAtSentenceBoundary(
    maxLength: Int,
): String {
    val cleaned = trim()
    if (cleaned.length <= maxLength) return cleaned
    val candidate = cleaned.take(maxLength)
    val boundary = candidate.lastIndexOfAny(charArrayOf('。', '！', '？', ';', '；', '.', '!', '?'))
    return if (boundary >= maxLength / 2) {
        candidate.substring(0, boundary + 1).trim()
    } else {
        candidate.trimEnd()
    }
}

private fun String.truncateAtNaturalBoundary(
    maxLength: Int,
): String {
    val cleaned = trim()
    if (cleaned.length <= maxLength) return cleaned
    val candidate = cleaned.take(maxLength)
    val preferredBoundary = candidate.lastIndexOfAny(charArrayOf('。', '！', '？', ';', '；', '.', '!', '?'))
    if (preferredBoundary >= maxLength / 2) {
        return candidate.substring(0, preferredBoundary + 1).trim()
    }
    val secondaryBoundary = candidate.lastIndexOfAny(charArrayOf('，', ',', '、', '：', ':'))
    if (secondaryBoundary >= maxLength / 2) {
        return candidate.substring(0, secondaryBoundary).trim()
    }
    return candidate.trimEnd()
}

private fun String.removeJsonLikePrefix(): String {
    return replace(Regex("""^(oneLineSummary|summary|background|topics|facts|actionItems|tags)\s*"?\s*:\s*"""), "")
        .trim()
}

private fun String.removeTrailingConnector(): String {
    return trimEnd('，', ',', '、', '：', ':', ';', '；', '和', '及', '与')
        .trim()
}

private fun extractNamedStringField(
    raw: String,
    fieldName: String,
): String? {
    val pattern = Regex(""""$fieldName"\s*:\s*"([\s\S]*?)"""")
    return pattern.find(raw)
        ?.groupValues
        ?.getOrNull(1)
        ?.let(::cleanMemoText)
        ?.takeIf { it.isNotBlank() && !looksLikeBrokenMemoField(it) }
}

private fun extractTopicSummariesFromPartialJson(
    raw: String,
): List<TopicSummary> {
    val pattern = Regex(""""name"\s*:\s*"([^"]+)"[\s\S]*?"summary"\s*:\s*"([^"]+)"""")
    return pattern.findAll(raw)
        .map {
            TopicSummary(
                name = cleanMemoText(it.groupValues[1]).take(18),
                summary = cleanMemoText(it.groupValues[2]).take(56),
            )
        }
        .distinctBy { "${it.name}|${it.summary}" }
        .take(2)
        .toList()
}

private fun extractFactLinesFromPartialJson(
    raw: String,
): List<String> {
    val textFacts = Regex(""""text"\s*:\s*"([^"]+)"""")
        .findAll(raw)
        .map { cleanMemoText(it.groupValues[1]).truncateAtSentenceBoundary(60) }
        .filter { it.isNotBlank() && !looksLikeSchemaFragment(it) }
        .distinct()
        .take(3)
        .toList()
    if (textFacts.isNotEmpty()) return textFacts
    return emptyList()
}

private fun extractActionItemsFromPartialJson(
    raw: String,
): List<ActionItem> {
    val taskMatches = Regex(
        """"task"\s*:\s*"([^"]+)"(?:[\s\S]*?"owner"\s*:\s*"([^"]*)")?(?:[\s\S]*?"deadline"\s*:\s*"([^"]*)")?""",
    ).findAll(raw)
    return taskMatches.map {
        ActionItem(
            task = cleanMemoText(it.groupValues[1]).take(40),
            owner = cleanMemoText(it.groupValues.getOrElse(2) { "" }).take(16),
            deadline = cleanMemoText(it.groupValues.getOrElse(3) { "" }).takeIf { value -> value.isNotBlank() }?.take(20),
        )
    }
        .filter { it.task.isNotBlank() && !looksLikeSchemaFragment(it.task) }
        .distinctBy { "${it.task}|${it.owner}|${it.deadline.orEmpty()}" }
        .take(3)
        .toList()
}

private fun JSONObject.flexString(
    vararg keys: String,
): String {
    return keys.firstNotNullOfOrNull { key ->
        val value = opt(key)
        when (value) {
            is String -> value.trim()
            is Number, is Boolean -> value.toString()
            else -> null
        }?.takeIf { it.isNotBlank() }
    }.orEmpty()
}

private fun JSONObject.flexStringList(
    vararg keys: String,
): List<String> {
    return keys.firstNotNullOfOrNull { key ->
        when (val value = opt(key)) {
            is JSONArray -> value.toStringList()
            is String -> value.toDelimitedStringList()
            is JSONObject -> value.toMapStringList()
            else -> null
        }?.takeIf { it.isNotEmpty() }
    }.orEmpty()
}

private fun JSONObject.flexTagList(
    vararg keys: String,
): List<String> {
    return keys.firstNotNullOfOrNull { key ->
        when (val value = opt(key)) {
            is JSONArray -> value.toStringList()
            is String -> value.toTagList()
            else -> null
        }?.takeIf { it.isNotEmpty() }
    }.orEmpty()
}

private fun JSONObject.flexTopicSummaries(
    vararg keys: String,
): List<TopicSummary> {
    return keys.firstNotNullOfOrNull { key ->
        when (val value = opt(key)) {
            is JSONArray -> value.toTopicSummaries()
            is String -> value.toTopicSummariesFromText()
            else -> null
        }?.takeIf { it.isNotEmpty() }
    }.orEmpty()
}

private fun JSONObject.flexActionItems(
    vararg keys: String,
): List<ActionItem> {
    return keys.firstNotNullOfOrNull { key ->
        when (val value = opt(key)) {
            is JSONArray -> value.toActionItems()
            is String -> value.toActionItemsFromText()
            else -> null
        }?.takeIf { it.isNotEmpty() }
    }.orEmpty()
}

private fun JSONArray.toStringList(): List<String> {
    return buildList {
        for (index in 0 until length()) {
            when (val item = opt(index)) {
                is String -> item.trim().takeIf { it.isNotBlank() }?.let(::add)
                is Number, is Boolean -> add(item.toString())
                is JSONObject -> {
                    val flattened = item.toMapStringList()
                    if (flattened.isNotEmpty()) {
                        add(flattened.joinToString(" | "))
                    }
                }
            }
        }
    }
}

private fun JSONArray.toTopicSummaries(): List<TopicSummary> {
    return buildList {
        for (index in 0 until length()) {
            when (val item = opt(index)) {
                is JSONObject -> {
                    val name = item.flexString("name", "topic", "title", "主题")
                    val summary = item.flexString("summary", "content", "内容", "描述")
                    if (name.isNotBlank() || summary.isNotBlank()) {
                        add(
                            TopicSummary(
                                name = name.ifBlank { "主题${index + 1}" },
                                summary = summary.ifBlank { name },
                            ),
                        )
                    }
                }
                is String -> parseTopicSummaryLine(item)?.let(::add)
            }
        }
    }
}

private fun JSONArray.toActionItems(): List<ActionItem> {
    return buildList {
        for (index in 0 until length()) {
            when (val item = opt(index)) {
                is JSONObject -> {
                    val task = item.flexString("task", "todo", "action", "事项", "内容", "任务")
                    val owner = item.flexString("owner", "assignee", "负责人", "ownerName")
                    val deadline = item.flexString("deadline", "due", "截止时间", "日期")
                    if (task.isNotBlank() || owner.isNotBlank() || deadline.isNotBlank()) {
                        add(
                            ActionItem(
                                task = task.ifBlank { "待补充行动项" },
                                owner = owner,
                                deadline = deadline.takeIf { it.isNotBlank() },
                            ),
                        )
                    }
                }
                is String -> parseActionItemLine(item)?.let(::add)
            }
        }
    }
}

private fun JSONObject.toMapStringList(): List<String> {
    return keys().asSequence()
        .mapNotNull { key ->
            val value = opt(key)?.toString()?.trim().orEmpty()
            value.takeIf { it.isNotBlank() }?.let { "$key: $it" }
        }
        .toList()
}

private fun String.toDelimitedStringList(): List<String> {
    return lineSequence()
        .flatMap { line ->
            line.split(Regex("""[；;]""")).asSequence()
        }
        .map { it.trim().trimStart('-', '•', '*', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '、', ')', '）') }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toList()
}

private fun String.toTagList(): List<String> {
    return split(Regex("""[\n,，；;]"""))
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

private fun String.toTopicSummariesFromText(): List<TopicSummary> {
    return lineSequence()
        .mapNotNull(::parseTopicSummaryLine)
        .toList()
}

private fun String.toActionItemsFromText(): List<ActionItem> {
    return lineSequence()
        .mapNotNull(::parseActionItemLine)
        .toList()
}

private fun parseTopicSummaryLine(
    raw: String,
): TopicSummary? {
    val cleaned = raw.trim().trimStart('-', '•', '*')
    if (cleaned.isBlank()) return null
    val parts = cleaned.split(Regex("""[:：|-]"""), limit = 2)
    val name = parts.firstOrNull().orEmpty().trim()
    val summary = parts.getOrNull(1)?.trim().orEmpty()
    return TopicSummary(
        name = name.ifBlank { "主题" },
        summary = summary.ifBlank { cleaned },
    )
}

private fun parseActionItemLine(
    raw: String,
): ActionItem? {
    val cleaned = raw.trim().trimStart('-', '•', '*')
    if (cleaned.isBlank()) return null
    val parts = cleaned.split(Regex("""[|｜]""")).map { it.trim() }.filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> null
        parts.size == 1 -> ActionItem(task = parts[0], owner = "", deadline = null)
        else -> ActionItem(
            task = parts[0],
            owner = parts.getOrNull(1).orEmpty(),
            deadline = parts.getOrNull(2)?.takeIf { it.isNotBlank() },
        )
    }
}
