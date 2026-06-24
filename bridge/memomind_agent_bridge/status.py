from __future__ import annotations

from pathlib import Path

from .config import BridgeConfig
from .main import preflight_config
from .supabase_client import SupabaseClient


def status() -> None:
    root_dir = Path(__file__).resolve().parents[1]
    print("MemoMind Agent Bridge status")
    config = BridgeConfig.load(root_dir)
    project_registry = config.project_registry()
    _, infos = preflight_config(config, project_registry)
    for info in infos:
        print(f"[info] {info}")

    try:
        supabase = SupabaseClient(config)
        recent_tasks = supabase.fetch_recent_tasks(limit=8)
    except Exception as exc:  # noqa: BLE001
        print(f"[warn] recent_tasks_unavailable={exc}")
        return
    if not recent_tasks:
        print("[info] no recent tasks found")
        return

    print("[info] recent_tasks")
    for task in recent_tasks:
        summary = " | ".join(
            part
            for part in (
                task.updated_at or "",
                task.target_agent or "",
                task.status or "",
                task.source_task_id or "",
                task.claimed_by or "",
                _trim(task.goal, 72),
            )
            if part
        )
        print(f"- {task.id[:8]} | {summary}")


def _trim(text: str, limit: int) -> str:
    value = text.strip()
    if len(value) <= limit:
        return value
    return f"{value[: limit - 1]}…"
