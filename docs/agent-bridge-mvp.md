# MemoMind Agent Bridge MVP

## 当前落地范围

V1 已实现的骨架：

- `supabase/migrations/001_agent_tasks.sql`
- `bridge/memomind_agent_bridge/*`
- Android 端统一 `AgentTask` 协议
- Android 端 Supabase REST 客户端
- Android 端结果页投递/轮询入口
- Android 端多 Agent 远程任务列表持久化
- Android 端结果页多远程任务切换查看
- Bridge 端 `codex` / `trae` / `work_buddy` 多适配器骨架

## Supabase 配置

你的项目地址可以使用：

```text
https://lzkajelxcaiaqcbxqjea.supabase.co
```

需要完成：

1. 在 Project Settings 里获取 `anon key`
2. 在本地 Bridge `.env` 里填 `SUPABASE_SERVICE_ROLE_KEY`
3. 确保电脑端已登录 Codex，本地存在 `~/.codex/auth.json`

## Android 侧说明

当前 Android 客户端走的是 Supabase REST：

- `POST /rest/v1/agent_tasks`
- `GET /rest/v1/agent_tasks?id=eq.<task_id>`

当前 MVP 已改为“设备级 request header 身份”方案：

- App 会为当前设备生成一个稳定的 `memomindAgentUserId`
- 每次请求都会带上 `x-memomind-user-id`
- Supabase RLS 会用这个 header 与 `agent_tasks.user_id` 做匹配

这样手机端即使暂时不接入正式 Supabase Auth，也能用 `anon key` 直接创建、查询和更新属于自己的任务。

如果线上 RLS 还未及时切换完成，当前仓库也提供了一个仅用于本地调试包的回退方案：

```bash
-Pmemomind.supabaseServiceRoleKey=<local_debug_only_service_role_key>
```

这样 debug APK 会直接用 service role 访问 `agent_tasks`，用于你本人设备上的联调闭环验证。这个回退只建议在本地开发阶段使用，不能作为正式发布配置。

## 当前已经支持的桌面 Agent 能力

- `codex`: 原生 Adapter，支持 `plan_only` / `read_only` / 已批准的 `workspace_write`
- `trae`: 可选外部 CLI Adapter，通过 `.env` 里的 `TRAE_COMMAND` / `TRAE_ARGS` 注册
- `work_buddy`: 可选外部 CLI Adapter，通过 `.env` 里的 `WORK_BUDDY_COMMAND` / `WORK_BUDDY_ARGS` 注册

Bridge 启动时会进行自检：

- 校验 `CODEX_COMMAND`
- 自动准备 Bridge 专用最小 `CODEX_HOME`
- 校验 `project_registry.json`
- 校验项目路径存在
- 对可选的外部 Agent CLI 做存在性检查，缺失时仅 warning 并跳过注册

当前真实环境结论：

- Supabase `agent_tasks` 表已在项目 `lzkajelxcaiaqcbxqjea` 中落库
- `codex` 真实 CLI 已打通，Bridge 会使用独立 `CODEX_HOME` 降低插件和本地配置干扰
- `trae` 真实 CLI 可成功唤起桌面会话，但暂不支持结构化结果文件回写，因此目前按 launcher/handoff 模式接入
- `work_buddy` 本机尚未发现可执行 CLI，因此仍处于占位适配阶段
- Bridge 已支持 stale running task 自动回收，避免桌面休眠或 CLI 卡死后任务永久停在 `running`
- Bridge 已支持长任务执行心跳，手机端能持续看到 `executing` 阶段刷新
- Android 结果页已补充“Bridge 状态”卡，会根据最近一条远程任务活动推断桌面 Bridge 是否最近活跃
- 已提供 macOS LaunchAgent 安装脚本，可把 Bridge 作为常驻后台服务运行

## 当前 Android 侧联动能力

- 可以从结果页把同一条纪要提交给多个桌面 Agent
- 会在本地保存多条远程任务引用、状态、摘要、更新时间
- 结果页可切换查看不同 Agent 的远程任务
- 历史页会显示最近几条 Agent 任务的状态和摘要
