# MemoMind Agent Bridge

第一版 Bridge 目标：

- 轮询 Supabase `agent_tasks`
- claim `pending` 任务
- 将 `target_agent` 分发给已注册的 Agent 适配器
- 内置 `CodexAdapter`
- 可选注册 `trae`、`work_buddy` 外部 CLI 适配器
- 将结构化执行结果写回 Supabase

## 启动方式

1. 复制 `.env.example` 为 `.env`
2. 复制 `project_registry.example.json` 为 `project_registry.json`
3. 填入本地 `SUPABASE_SERVICE_ROLE_KEY`
4. 运行：

```bash
python3 -m memomind_agent_bridge
```

如果希望桌面端长期驻留为后台服务，可以安装 macOS LaunchAgent：

```bash
./scripts/install_launch_agent.sh
```

查看后台 Bridge 状态：

```bash
./scripts/bridge_status.sh
```

卸载后台服务：

```bash
./scripts/uninstall_launch_agent.sh
```

如需先做本机真实环境检查：

```bash
python3 -m memomind_agent_bridge doctor
```

如需直接查看最近几条 Bridge 任务活动：

```bash
python3 -m memomind_agent_bridge status
```

启动时 Bridge 会做以下自检：

- `CODEX_COMMAND` 是否可执行
- Bridge 专用 `CODEX_HOME` 是否已准备好最小认证环境
- `STALE_RUNNING_TASK_TIMEOUT_SECONDS` 是否已生效
- `EXECUTION_HEARTBEAT_SECONDS` 是否已生效
- `project_registry.json` 是否至少包含一个项目映射
- 每个项目路径是否存在
- 可选的 `TRAE_COMMAND` / `WORK_BUDDY_COMMAND` 是否存在

如果某个可选外部 Agent CLI 不存在，Bridge 会跳过该 adapter 并打印 warning，不会影响 `codex` 任务运行。

## 外部 Agent CLI

如果本机已经安装了 Trae 或 Work Buddy 的桌面 CLI，可以在 `.env` 中补充：

```bash
TRAE_COMMAND=trae
TRAE_ARGS=chat --mode agent --reuse-window "{prompt}"

WORK_BUDDY_COMMAND=workbuddy
WORK_BUDDY_ARGS=run --prompt-file {prompt_file} --output-file {output_file}
```

占位符支持：

- `{prompt_file}`: Bridge 自动生成的任务 prompt 文件
- `{output_file}`: 期望 agent 写回结果的 markdown 文件
- `{project_path}`: 当前任务对应的项目目录
- `{mode}`: 任务模式，如 `plan_only` / `read_only` / `workspace_write`
- `{goal}`: 任务目标

其中 `codex` 会自动使用 Bridge 专用的最小 `CODEX_HOME`，避免被用户本地插件/配置污染；默认超时时间建议至少保持在 `900` 秒，以容纳网络抖动下的自动重连。

Bridge 每轮轮询前都会回收一批“长时间未更新、但仍处于 running 的旧任务”。默认阈值为 `900` 秒，可通过 `STALE_RUNNING_TASK_TIMEOUT_SECONDS` 调整，用于处理桌面休眠、CLI 卡死或异常退出后的遗留任务。

对于执行时间较长的 Codex 任务，Bridge 会按 `EXECUTION_HEARTBEAT_SECONDS` 周期回写一次 `executing` 心跳，持续刷新 `updated_at` 和 `progress_events`，方便手机端判断任务仍在进行中。

`trae` 当前使用 launcher 模式：Bridge 会唤起桌面端 Trae 会话并返回一条“已接收任务”的结果，但 Trae 现有 CLI 暂不支持像 Codex 那样把结构化结果自动回写到 `output_file`。

如果未配置这些环境变量，Bridge 只会注册 `codex` 适配器。
