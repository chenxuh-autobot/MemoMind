from __future__ import annotations

import json
from pathlib import Path

from .config import BridgeConfig
from .main import preflight_config
from .supabase_client import SupabaseClient


def doctor() -> bool:
    root_dir = Path(__file__).resolve().parents[1]
    print("MemoMind Agent Bridge doctor")
    try:
        config = BridgeConfig.load(root_dir)
        project_registry = config.project_registry()
        warnings, infos = preflight_config(config, project_registry)
        print(f"supabase_url={config.supabase_url}")
        print(f"supabase_table={config.supabase_table}")
        print(f"claimed_by={config.claimed_by}")
        print(f"registered_projects={json.dumps(project_registry, ensure_ascii=False)}")
        for info in infos:
            print(f"[info] {info}")
        external_profiles = config.external_agent_profiles()
        if external_profiles:
            for agent_id, profile in external_profiles.items():
                resolved = profile.resolved_command()
                print(
                    f"[info] external_agent={agent_id} command={profile.command!r} "
                    f"resolved={resolved!r} timeout={profile.timeout_seconds}"
                )
        else:
            print("[info] no optional external agent profiles configured")
        for warning in warnings:
            print(f"[warn] {warning}")

        supabase = SupabaseClient(config)
        pending_tasks = supabase.fetch_pending_tasks(limit=1)
        print(f"[info] supabase_connectivity=ok pending_tasks_visible={len(pending_tasks)}")
        return True
    except Exception as exc:  # noqa: BLE001
        print(f"[error] supabase_connectivity_failed={exc}")
        return False
