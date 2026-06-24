package cn.chenxuhang.creativeai.core.network

import cn.chenxuhang.creativeai.core.model.ActionItem
import cn.chenxuhang.creativeai.core.model.AgentTask
import cn.chenxuhang.creativeai.core.model.AgentTaskContext
import cn.chenxuhang.creativeai.core.model.AgentTaskError
import cn.chenxuhang.creativeai.core.model.AgentTaskMode
import cn.chenxuhang.creativeai.core.model.AgentTaskPermission
import cn.chenxuhang.creativeai.core.model.AgentTaskProgressEvent
import cn.chenxuhang.creativeai.core.model.AgentTaskResult
import cn.chenxuhang.creativeai.core.model.AgentTaskStatus
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class AgentTaskRemoteConfig(
    val supabaseUrl: String,
    val anonKey: String,
    val memomindUserId: String,
    val accessToken: String? = null,
    val debugServiceRoleKey: String? = null,
    val tableName: String = "agent_tasks",
) {
    val isConfigured: Boolean
        get() = supabaseUrl.isNotBlank() && anonKey.isNotBlank() && memomindUserId.isNotBlank()

    val requestApiKey: String
        get() = debugServiceRoleKey?.takeIf { it.isNotBlank() } ?: anonKey

    val requestBearerToken: String
        get() = debugServiceRoleKey?.takeIf { it.isNotBlank() }
            ?: accessToken?.takeIf { it.isNotBlank() }
            ?: anonKey
}

interface AgentTaskRemoteDataSource {
    fun isConfigured(): Boolean
    fun create(task: AgentTask): Result<AgentTask>
    fun fetchById(taskId: String): Result<AgentTask?>
    fun fetchRecentByUser(limit: Int = 8): Result<List<AgentTask>>
    fun cancel(taskId: String): Result<AgentTask>
    fun requeueInSafeMode(taskId: String): Result<AgentTask>
    fun approveForWorkspaceWrite(taskId: String): Result<AgentTask>
}

class DisabledAgentTaskRemoteDataSource : AgentTaskRemoteDataSource {
    override fun isConfigured(): Boolean = false

    override fun create(task: AgentTask): Result<AgentTask> {
        return Result.failure(IllegalStateException("MemoMind Agent Bridge 未配置 Supabase。"))
    }

    override fun fetchById(taskId: String): Result<AgentTask?> {
        return Result.failure(IllegalStateException("MemoMind Agent Bridge 未配置 Supabase。"))
    }

    override fun fetchRecentByUser(limit: Int): Result<List<AgentTask>> {
        return Result.failure(IllegalStateException("MemoMind Agent Bridge 未配置 Supabase。"))
    }

    override fun cancel(taskId: String): Result<AgentTask> {
        return Result.failure(IllegalStateException("MemoMind Agent Bridge 未配置 Supabase。"))
    }

    override fun requeueInSafeMode(taskId: String): Result<AgentTask> {
        return Result.failure(IllegalStateException("MemoMind Agent Bridge 未配置 Supabase。"))
    }

    override fun approveForWorkspaceWrite(taskId: String): Result<AgentTask> {
        return Result.failure(IllegalStateException("MemoMind Agent Bridge 未配置 Supabase。"))
    }
}

class SupabaseAgentTaskRemoteDataSource(
    private val config: AgentTaskRemoteConfig,
) : AgentTaskRemoteDataSource {
    override fun isConfigured(): Boolean = config.isConfigured

    override fun create(task: AgentTask): Result<AgentTask> = runCatching {
        check(config.isConfigured) { "Supabase 配置缺失。" }
        val connection = openConnection(
            method = "POST",
            path = "/rest/v1/${config.tableName}",
            query = mapOf("select" to "*"),
        )
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Prefer", "return=representation")
        connection.doOutput = true
        connection.outputStream.use { output ->
            output.write(task.toCreateJson().toString().toByteArray(Charsets.UTF_8))
        }
        val response = connection.readJsonArrayResponse()
        response.optJSONObject(0)?.toAgentTask()
            ?: error("Supabase 未返回已创建的 MemoMind Agent 任务。")
    }

    override fun fetchById(taskId: String): Result<AgentTask?> = runCatching {
        check(config.isConfigured) { "Supabase 配置缺失。" }
        val connection = openConnection(
            method = "GET",
            path = "/rest/v1/${config.tableName}",
            query = mapOf(
                "id" to "eq.$taskId",
                "user_id" to "eq.${config.memomindUserId}",
                "select" to "*",
            ),
        )
        val response = connection.readJsonArrayResponse()
        response.optJSONObject(0)?.toAgentTask()
    }

    override fun fetchRecentByUser(limit: Int): Result<List<AgentTask>> = runCatching {
        check(config.isConfigured) { "Supabase 配置缺失。" }
        val connection = openConnection(
            method = "GET",
            path = "/rest/v1/${config.tableName}",
            query = mapOf(
                "user_id" to "eq.${config.memomindUserId}",
                "order" to "updated_at.desc",
                "limit" to limit.toString(),
                "select" to "*",
            ),
        )
        val response = connection.readJsonArrayResponse()
        buildList {
            for (index in 0 until response.length()) {
                response.optJSONObject(index)?.toAgentTask()?.let(::add)
            }
        }
    }

    override fun cancel(taskId: String): Result<AgentTask> = runCatching {
        updateTask(
            taskId = taskId,
            payload = JSONObject().apply {
                put("status", AgentTaskStatus.CANCELLED.toWireValue())
            },
        )
    }

    override fun requeueInSafeMode(taskId: String): Result<AgentTask> = runCatching {
        updateTask(
            taskId = taskId,
            payload = JSONObject().apply {
                put("status", AgentTaskStatus.PENDING.toWireValue())
                put("mode", AgentTaskMode.PLAN_ONLY.toWireValue())
                put("permission", AgentTaskPermission().toJson())
                put("claimed_by", JSONObject.NULL)
                put("claimed_at", JSONObject.NULL)
                put("error", JSONObject.NULL)
                put("result", JSONObject.NULL)
            },
        )
    }

    override fun approveForWorkspaceWrite(taskId: String): Result<AgentTask> = runCatching {
        updateTask(
            taskId = taskId,
            payload = JSONObject().apply {
                put("status", AgentTaskStatus.PENDING.toWireValue())
                put("mode", AgentTaskMode.WORKSPACE_WRITE.toWireValue())
                put(
                    "permission",
                    AgentTaskPermission(
                        requireUserApproval = true,
                        approvedForExecution = true,
                        allowCodeWrite = true,
                        allowShellCommand = true,
                        allowGitCommit = false,
                        allowGitPush = false,
                        allowFileDelete = false,
                        allowNetworkAccess = false,
                    ).toJson(),
                )
                put("claimed_by", JSONObject.NULL)
                put("claimed_at", JSONObject.NULL)
                put("error", JSONObject.NULL)
                put("result", JSONObject.NULL)
            },
        )
    }

    private fun updateTask(
        taskId: String,
        payload: JSONObject,
    ): AgentTask {
        check(config.isConfigured) { "Supabase 配置缺失。" }
        val connection = openConnection(
            method = "PATCH",
            path = "/rest/v1/${config.tableName}",
            query = mapOf(
                "id" to "eq.$taskId",
                "user_id" to "eq.${config.memomindUserId}",
                "select" to "*",
            ),
        )
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Prefer", "return=representation")
        connection.doOutput = true
        connection.outputStream.use { output ->
            output.write(payload.toString().toByteArray(Charsets.UTF_8))
        }
        val response = connection.readJsonArrayResponse()
        return response.optJSONObject(0)?.toAgentTask()
            ?: error("Supabase 未返回更新后的 MemoMind Agent 任务。")
    }

    private fun openConnection(
        method: String,
        path: String,
        query: Map<String, String>,
    ): HttpURLConnection {
        val queryString = query.entries.joinToString("&") { (key, value) ->
            "${key.encodeUrl()}=${value.encodeUrl()}"
        }
        val normalizedBaseUrl = config.supabaseUrl.trimEnd('/')
        val url = URL(
            buildString {
                append(normalizedBaseUrl)
                append(path)
                if (queryString.isNotBlank()) {
                    append('?')
                    append(queryString)
                }
            },
        )
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("apikey", config.requestApiKey)
            setRequestProperty("Authorization", "Bearer ${config.requestBearerToken}")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("x-memomind-user-id", config.memomindUserId)
        }
    }
}

private fun HttpURLConnection.readJsonArrayResponse(): JSONArray {
    val stream = if (responseCode in 200..299) inputStream else errorStream
    val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    if (responseCode !in 200..299) {
        throw IllegalStateException(
            buildString {
                append("Supabase 请求失败：HTTP ")
                append(responseCode)
                if (body.isNotBlank()) {
                    append(" | ")
                    append(body)
                }
            },
        )
    }
    return if (body.isBlank()) JSONArray() else JSONArray(body)
}

private fun AgentTask.toCreateJson(): JSONObject {
    return JSONObject().apply {
        put("user_id", userId)
        put("source_app", sourceApp)
        put("source_task_id", sourceTaskId)
        put("target_agent", targetAgent)
        put("project_id", projectId)
        put("task_type", taskType)
        put("mode", mode.toWireValue())
        put("goal", goal)
        put("prompt", prompt)
        put("context", context.toJson())
        put("permission", permission.toJson())
        put("status", status.toWireValue())
    }
}

private fun JSONObject.toAgentTask(): AgentTask {
    return AgentTask(
        id = optString("id"),
        userId = optString("user_id"),
        sourceApp = optString("source_app"),
        sourceTaskId = optString("source_task_id").takeIf { it.isNotBlank() },
        targetAgent = optString("target_agent"),
        projectId = optString("project_id"),
        taskType = optString("task_type"),
        mode = optString("mode").toAgentTaskMode(),
        goal = optString("goal"),
        prompt = optString("prompt"),
        context = optJSONObject("context")?.toAgentTaskContext() ?: AgentTaskContext(),
        permission = optJSONObject("permission")?.toAgentTaskPermission() ?: AgentTaskPermission(),
        status = optString("status").toAgentTaskStatus(),
        result = optJSONObject("result")?.toAgentTaskResult(),
        error = optJSONObject("error")?.toAgentTaskError(),
        claimedBy = optString("claimed_by").takeIf { it.isNotBlank() },
        claimedAt = optString("claimed_at").takeIf { it.isNotBlank() },
        createdAt = optString("created_at").takeIf { it.isNotBlank() },
        updatedAt = optString("updated_at").takeIf { it.isNotBlank() },
    )
}

private fun AgentTaskContext.toJson(): JSONObject {
    return JSONObject().apply {
        put("meeting_summary", meetingSummary)
        put("requirements", JSONArray(requirements))
        put("constraints", JSONArray(constraints))
        put("memo_snapshot", JSONObject().apply {
            put("summary", memoSummary)
            put("background", memoBackground)
            put("facts", JSONArray(facts))
            put("decisions", JSONArray(decisions))
            put("action_items", JSONArray().apply {
                actionItems.forEach { item ->
                    put(
                        JSONObject().apply {
                            put("task", item.task)
                            put("owner", item.owner)
                            put("deadline", item.deadline)
                        },
                    )
                }
            })
            put("risks", JSONArray(risks))
            put("tags", JSONArray(tags))
            put("source_outline", JSONArray(sourceOutline))
        })
    }
}

private fun JSONObject.toAgentTaskContext(): AgentTaskContext {
    val memoSnapshot = optJSONObject("memo_snapshot")
    return AgentTaskContext(
        meetingSummary = optString("meeting_summary"),
        requirements = optJSONArray("requirements").toStringList(),
        constraints = optJSONArray("constraints").toStringList(),
        memoSummary = memoSnapshot?.optString("summary").orEmpty(),
        memoBackground = memoSnapshot?.optString("background").orEmpty(),
        facts = memoSnapshot?.optJSONArray("facts").toStringList(),
        decisions = memoSnapshot?.optJSONArray("decisions").toStringList(),
        actionItems = memoSnapshot?.optJSONArray("action_items").toActionItems(),
        risks = memoSnapshot?.optJSONArray("risks").toStringList(),
        tags = memoSnapshot?.optJSONArray("tags").toStringList(),
        sourceOutline = memoSnapshot?.optJSONArray("source_outline").toStringList(),
    )
}

private fun AgentTaskPermission.toJson(): JSONObject {
    return JSONObject().apply {
        put("require_user_approval", requireUserApproval)
        put("approved_for_execution", approvedForExecution)
        put("allow_code_write", allowCodeWrite)
        put("allow_shell_command", allowShellCommand)
        put("allow_git_commit", allowGitCommit)
        put("allow_git_push", allowGitPush)
        put("allow_file_delete", allowFileDelete)
        put("allow_network_access", allowNetworkAccess)
    }
}

private fun JSONObject.toAgentTaskPermission(): AgentTaskPermission {
    return AgentTaskPermission(
        requireUserApproval = optBoolean("require_user_approval", true),
        approvedForExecution = optBoolean("approved_for_execution", false),
        allowCodeWrite = optBoolean("allow_code_write", false),
        allowShellCommand = optBoolean("allow_shell_command", false),
        allowGitCommit = optBoolean("allow_git_commit", false),
        allowGitPush = optBoolean("allow_git_push", false),
        allowFileDelete = optBoolean("allow_file_delete", false),
        allowNetworkAccess = optBoolean("allow_network_access", false),
    )
}

private fun JSONObject.toAgentTaskResult(): AgentTaskResult {
    return AgentTaskResult(
        summary = optString("summary"),
        planMarkdown = optString("plan_markdown"),
        filesToTouch = optJSONArray("files_to_touch").toStringList(),
        risks = optJSONArray("risks").toStringList(),
        testSuggestions = optJSONArray("test_suggestions").toStringList(),
        rawStdout = optString("raw_stdout"),
        rawStderr = optString("raw_stderr"),
        exitCode = if (has("exit_code")) optInt("exit_code") else null,
        currentPhase = optString("current_phase"),
        progressEvents = optJSONArray("progress_events").toAgentTaskProgressEvents(),
    )
}

private fun JSONObject.toAgentTaskError(): AgentTaskError {
    return AgentTaskError(
        type = optString("type"),
        message = optString("message"),
        detail = optString("detail"),
    )
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList(length()) {
        for (index in 0 until length()) {
            val value = optString(index)
            if (value.isNotBlank()) add(value)
        }
    }
}

private fun JSONArray?.toActionItems(): List<ActionItem> {
    if (this == null) return emptyList()
    return buildList(length()) {
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

private fun JSONArray?.toAgentTaskProgressEvents(): List<AgentTaskProgressEvent> {
    if (this == null) return emptyList()
    return buildList(length()) {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            add(
                AgentTaskProgressEvent(
                    phase = item.optString("phase"),
                    message = item.optString("message"),
                    createdAt = item.optString("created_at").takeIf { it.isNotBlank() },
                    level = item.optString("level").ifBlank { "info" },
                ),
            )
        }
    }
}

private fun String.toAgentTaskMode(): AgentTaskMode {
    return when (trim().lowercase()) {
        "workspace_write" -> AgentTaskMode.WORKSPACE_WRITE
        "read_only" -> AgentTaskMode.READ_ONLY
        else -> AgentTaskMode.PLAN_ONLY
    }
}

private fun String.toAgentTaskStatus(): AgentTaskStatus {
    return when (trim().lowercase()) {
        "running" -> AgentTaskStatus.RUNNING
        "waiting_approval" -> AgentTaskStatus.WAITING_APPROVAL
        "done" -> AgentTaskStatus.DONE
        "failed" -> AgentTaskStatus.FAILED
        "cancelled" -> AgentTaskStatus.CANCELLED
        else -> AgentTaskStatus.PENDING
    }
}

private fun AgentTaskMode.toWireValue(): String {
    return name.lowercase()
}

private fun AgentTaskStatus.toWireValue(): String {
    return name.lowercase()
}

private fun String.encodeUrl(): String {
    return URLEncoder.encode(this, Charsets.UTF_8.name())
}
