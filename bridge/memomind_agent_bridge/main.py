from __future__ import annotations

import socket
import time
import traceback
from datetime import datetime, timezone
from pathlib import Path

from .adapters.codex_adapter import CodexAdapter
from .adapters.external_cli_adapter import ExternalCliAgentAdapter
from .config import BridgeConfig
from .models import AgentExecutionResult, AgentProgressEvent
from .router import AgentRouter
from .supabase_client import SupabaseClient


def preflight_config(config: BridgeConfig, project_registry: dict[str, str]) -> tuple[list[str], list[str]]:
    warnings: list[str] = []
    infos: list[str] = []

    if not config.supabase_service_role_key.strip():
        raise ValueError("SUPABASE_SERVICE_ROLE_KEY is empty in bridge/.env.")

    resolved_codex = config.resolved_codex_command()
    if resolved_codex is None:
        raise FileNotFoundError(
            "CODEX_COMMAND is not executable or not found in PATH. "
            f"Configured value: {config.codex_command!r}"
        )
    infos.append(f"Codex command: {resolved_codex}")
    codex_bridge_home = config.ensure_codex_bridge_home()
    infos.append(f"Codex bridge home: {codex_bridge_home}")
    infos.append(f"Stale running task timeout: {config.stale_running_task_timeout_seconds}s")
    infos.append(f"Execution heartbeat interval: {config.execution_heartbeat_seconds}s")
    infos.append(
        "Codex connectivity fast-fail: "
        f"{config.codex_connectivity_fail_seconds}s / reconnect signals >= {config.codex_reconnect_fail_count}"
    )

    if not project_registry:
        raise ValueError("Project registry is empty. Add at least one project_id -> path mapping.")
    for project_id, raw_path in project_registry.items():
        resolved_path = Path(raw_path).expanduser().resolve()
        if not resolved_path.exists():
            raise FileNotFoundError(f"Configured project path does not exist for {project_id}: {resolved_path}")
        infos.append(f"Project {project_id}: {resolved_path}")

    return warnings, infos


def build_progress_event(
    phase: str,
    message: str,
    level: str = "info",
) -> AgentProgressEvent:
    return AgentProgressEvent(
        phase=phase,
        message=message,
        created_at=datetime.now(tz=timezone.utc).isoformat(),
        level=level,
    )


def main() -> None:
    root_dir = Path(__file__).resolve().parents[1]
    config = BridgeConfig.load(root_dir)
    if config.claimed_by == "memomind-agent-bridge":
        config.claimed_by = f"memomind-agent-bridge@{socket.gethostname()}"
    project_registry = config.project_registry()
    warnings, infos = preflight_config(config, project_registry)
    supabase = SupabaseClient(config)
    adapters = {
        "codex": CodexAdapter(config=config, project_registry=project_registry),
    }
    for agent_id, profile in config.external_agent_profiles().items():
        resolved_command = profile.resolved_command()
        if resolved_command is None:
            warnings.append(
                f"Skipped adapter {agent_id}: command not found ({profile.command!r})."
            )
            continue
        infos.append(f"External agent {agent_id}: {resolved_command}")
        adapters[agent_id] = ExternalCliAgentAdapter(
            profile=profile,
            project_registry=project_registry,
        )
    router = AgentRouter(
        adapters=adapters,
    )

    print("MemoMind Agent Bridge started.")
    print(f"Polling {config.supabase_url}/{config.supabase_table} every {config.poll_interval_seconds}s")
    print(f"Registered adapters: {', '.join(sorted(adapters.keys()))}")
    for info in infos:
        print(f"[info] {info}")
    for warning in warnings:
        print(f"[warn] {warning}")
    while True:
        claimed = None
        progress_events: list[AgentProgressEvent] = []
        try:
            recover_stale_running_tasks(config=config, supabase=supabase)
            pending_tasks = supabase.fetch_pending_tasks(limit=1)
            if not pending_tasks:
                time.sleep(config.poll_interval_seconds)
                continue
            claimed = supabase.claim_task(pending_tasks[0].id)
            if claimed is None:
                continue
            print(f"[running] {claimed.id} -> {claimed.target_agent} ({claimed.project_id})")
            progress_events.append(build_progress_event("claimed", "电脑端 Bridge 已领取任务，开始校验权限与执行模式。"))
            supabase.update_progress(
                claimed.id,
                summary="电脑端 Bridge 已领取任务，准备校验权限与路由。",
                current_phase="claimed",
                progress_events=[event.to_dict() for event in progress_events],
            )
            if claimed.requires_waiting_approval():
                progress_events.append(
                    build_progress_event(
                        "waiting_approval",
                        "任务请求了超出默认安全范围的权限，已转入等待手机端批准。",
                        level="warning",
                    )
                )
                supabase.mark_waiting_approval(
                    claimed.id,
                    result=AgentExecutionResult(
                        summary="任务请求的权限超出了 MemoMind Agent Bridge MVP 的默认安全范围，已转入 waiting_approval。",
                        plan_markdown="",
                        risks=[
                            "当前 Bridge 默认只允许 plan_only / read_only。",
                            "需要用户在手机端明确批准后，任务才会升级到 workspace_write 并重新排队。",
                        ],
                        current_phase="waiting_approval",
                        progress_events=list(progress_events),
                    ).to_supabase_result(),
                    error_payload={
                        "type": "approval_required",
                        "message": "MemoMind Agent Bridge MVP only supports plan_only / read_only tasks.",
                        "detail": (
                            f"mode={claimed.mode}, "
                            f"approved_for_execution={claimed.permission.approved_for_execution}, "
                            f"allow_code_write={claimed.permission.allow_code_write}, "
                            f"allow_shell_command={claimed.permission.allow_shell_command}"
                        ),
                    },
                )
                print(f"[waiting_approval] {claimed.id}")
                continue
            progress_events.append(build_progress_event("routing", f"任务已通过校验，准备路由到 {claimed.target_agent}。"))
            supabase.update_progress(
                claimed.id,
                summary=f"任务已通过校验，正在路由到 {claimed.target_agent}。",
                current_phase="routing",
                progress_events=[event.to_dict() for event in progress_events],
            )
            adapter = router.route(claimed)
            progress_events.append(
                build_progress_event(
                    "executing",
                    f"{claimed.target_agent} 已开始执行任务，当前模式为 {claimed.mode}。",
                )
            )
            supabase.update_progress(
                claimed.id,
                summary=f"{claimed.target_agent} 已开始执行任务。",
                current_phase="executing",
                progress_events=[event.to_dict() for event in progress_events],
            )
            def progress_callback(phase: str, message: str, level: str = "info") -> None:
                progress_events.append(build_progress_event(phase, message, level=level))
                supabase.update_progress(
                    claimed.id,
                    summary=message,
                    current_phase=phase,
                    progress_events=[event.to_dict() for event in progress_events],
                )

            execution = adapter.execute(claimed, progress_callback=progress_callback)
            progress_events.extend(execution.progress_events)
            progress_events.append(build_progress_event("done", "任务执行完成，结果已回写到 MemoMind。"))
            execution.current_phase = "done"
            execution.progress_events = list(progress_events)
            supabase.mark_done(claimed.id, execution.to_supabase_result())
            print(f"[done] {claimed.id}")
        except KeyboardInterrupt:
            print("MemoMind Agent Bridge stopped.")
            break
        except Exception as exc:  # noqa: BLE001
            print(f"[error] {exc}")
            traceback.print_exc()
            if claimed is not None:
                try:
                    progress_events.append(
                        build_progress_event(
                            "failed",
                            f"Bridge 执行失败：{exc}",
                            level="error",
                        )
                    )
                    supabase.mark_failed(
                        claimed.id,
                        {
                            "type": exc.__class__.__name__,
                            "message": str(exc),
                            "detail": traceback.format_exc(limit=8),
                        },
                        result=AgentExecutionResult(
                            summary=str(exc),
                            plan_markdown="",
                            current_phase="failed",
                            progress_events=list(progress_events),
                            raw_stderr=str(exc),
                        ).to_supabase_result(),
                    )
                except Exception as mark_error:  # noqa: BLE001
                    print(f"[error] failed to mark task as failed: {mark_error}")
            time.sleep(config.poll_interval_seconds)


def recover_stale_running_tasks(config: BridgeConfig, supabase: SupabaseClient) -> None:
    stale_before = datetime.now(tz=timezone.utc).timestamp() - config.stale_running_task_timeout_seconds
    stale_before_iso = datetime.fromtimestamp(stale_before, tz=timezone.utc).isoformat()
    stale_tasks = supabase.fetch_stale_running_tasks(
        claimed_by=config.claimed_by,
        stale_before_iso=stale_before_iso,
        limit=10,
    )
    for stale_task in stale_tasks:
        recovered = supabase.mark_failed_if_running(
            stale_task.id,
            error_payload={
                "type": "stale_running_task_recovered",
                "message": (
                    "Bridge detected a stale running task from a previous execution window "
                    "and marked it as failed for safe recovery."
                ),
                "detail": (
                    f"claimed_by={stale_task.claimed_by}, "
                    f"claimed_at={stale_task.claimed_at}, "
                    f"updated_at={stale_task.updated_at}, "
                    f"timeout_seconds={config.stale_running_task_timeout_seconds}"
                ),
            },
            result=AgentExecutionResult(
                summary="Bridge 检测到一条长时间未更新的运行中任务，已安全回收并标记为失败。",
                plan_markdown="",
                risks=[
                    "该任务可能在上一次桌面 Bridge 中断、网络异常或 CLI 卡住时遗留。",
                    "如需继续，请在手机端执行安全重排，或重新提交该任务。",
                ],
                current_phase="recovered_stale_task",
                progress_events=[
                    build_progress_event(
                        "recovered_stale_task",
                        "Bridge 已回收一条超时未更新的运行中任务，避免任务永久卡死。",
                        level="warning",
                    ),
                ],
            ).to_supabase_result(),
        )
        if recovered:
            print(f"[recovered_stale_task] {stale_task.id}")
