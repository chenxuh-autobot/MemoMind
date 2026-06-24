from __future__ import annotations

import json
import os
import shlex
import shutil
from dataclasses import dataclass
from pathlib import Path


def _load_dotenv(dotenv_path: Path) -> None:
    if not dotenv_path.exists():
        return
    for raw_line in dotenv_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip())


@dataclass(slots=True)
class ExternalAgentProfile:
    agent_id: str
    command: str
    args_template: str = ""
    timeout_seconds: int = 180

    def resolved_command(self) -> str | None:
        return resolve_command_path(self.command)


@dataclass(slots=True)
class BridgeConfig:
    supabase_url: str
    supabase_service_role_key: str
    supabase_table: str
    poll_interval_seconds: int
    stale_running_task_timeout_seconds: int
    execution_heartbeat_seconds: int
    codex_timeout_seconds: int
    codex_connectivity_fail_seconds: int
    codex_reconnect_fail_count: int
    codex_command: str
    codex_model: str
    codex_reasoning_effort: str
    codex_bridge_home_path: Path
    claimed_by: str
    project_registry_path: Path

    @classmethod
    def load(cls, root_dir: Path) -> "BridgeConfig":
        _load_dotenv(root_dir / ".env")
        return cls(
            supabase_url=os.environ["SUPABASE_URL"].rstrip("/"),
            supabase_service_role_key=os.environ["SUPABASE_SERVICE_ROLE_KEY"],
            supabase_table=os.environ.get("SUPABASE_TABLE", "agent_tasks"),
            poll_interval_seconds=int(os.environ.get("POLL_INTERVAL_SECONDS", "8")),
            stale_running_task_timeout_seconds=int(os.environ.get("STALE_RUNNING_TASK_TIMEOUT_SECONDS", "900")),
            execution_heartbeat_seconds=int(os.environ.get("EXECUTION_HEARTBEAT_SECONDS", "20")),
            codex_timeout_seconds=int(os.environ.get("CODEX_TIMEOUT_SECONDS", "900")),
            codex_connectivity_fail_seconds=int(os.environ.get("CODEX_CONNECTIVITY_FAIL_SECONDS", "90")),
            codex_reconnect_fail_count=int(os.environ.get("CODEX_RECONNECT_FAIL_COUNT", "2")),
            codex_command=os.environ.get("CODEX_COMMAND", "codex"),
            codex_model=os.environ.get("CODEX_MODEL", "gpt-5.5"),
            codex_reasoning_effort=os.environ.get("CODEX_REASONING_EFFORT", "none"),
            codex_bridge_home_path=(root_dir / os.environ.get("CODEX_BRIDGE_HOME_PATH", ".codex-bridge")).resolve(),
            claimed_by=os.environ.get("CLAIMED_BY", "memomind-agent-bridge"),
            project_registry_path=(root_dir / os.environ.get("PROJECT_REGISTRY_PATH", "project_registry.json")).resolve(),
        )

    def project_registry(self) -> dict[str, str]:
        if not self.project_registry_path.exists():
            raise FileNotFoundError(
                f"Project registry not found: {self.project_registry_path}. "
                "Copy bridge/project_registry.example.json and fill in local paths."
            )
        payload = json.loads(self.project_registry_path.read_text(encoding="utf-8"))
        return {str(key): str(value) for key, value in payload.items()}

    def external_agent_profiles(self) -> dict[str, ExternalAgentProfile]:
        profiles: dict[str, ExternalAgentProfile] = {}
        env_specs = {
            "trae": "TRAE",
            "work_buddy": "WORK_BUDDY",
        }
        for agent_id, env_prefix in env_specs.items():
            command = os.environ.get(f"{env_prefix}_COMMAND", "").strip()
            if not command:
                continue
            profiles[agent_id] = ExternalAgentProfile(
                agent_id=agent_id,
                command=command,
                args_template=os.environ.get(f"{env_prefix}_ARGS", "").strip(),
                timeout_seconds=int(os.environ.get(f"{env_prefix}_TIMEOUT_SECONDS", "180")),
            )
        return profiles

    def resolved_codex_command(self) -> str | None:
        return resolve_command_path(self.codex_command)

    def ensure_codex_bridge_home(self) -> Path:
        source_home = Path.home() / ".codex"
        auth_source = source_home / "auth.json"
        if not auth_source.exists():
            raise FileNotFoundError(
                f"Codex auth.json not found: {auth_source}. "
                "Please log into Codex on this machine before starting the bridge."
            )

        bridge_home = self.codex_bridge_home_path
        bridge_home.mkdir(parents=True, exist_ok=True)
        _copy_if_newer(auth_source, bridge_home / "auth.json")

        installation_id_source = source_home / "installation_id"
        if installation_id_source.exists():
            _copy_if_newer(installation_id_source, bridge_home / "installation_id")

        config_text = (
            f'model = "{self.codex_model}"\n'
            f'model_reasoning_effort = "{self.codex_reasoning_effort}"\n'
        )
        config_path = bridge_home / "config.toml"
        if not config_path.exists() or config_path.read_text(encoding="utf-8") != config_text:
            config_path.write_text(config_text, encoding="utf-8")
        return bridge_home


def resolve_command_path(command: str) -> str | None:
    normalized = command.strip()
    if not normalized:
        return None
    parts = shlex.split(normalized)
    binary = parts[0] if parts else normalized
    if os.path.sep in binary:
        path = Path(binary).expanduser().resolve()
        return str(path) if path.exists() else None
    return shutil.which(binary)


def _copy_if_newer(source: Path, target: Path) -> None:
    if not target.exists() or source.stat().st_mtime_ns > target.stat().st_mtime_ns:
        shutil.copy2(source, target)
