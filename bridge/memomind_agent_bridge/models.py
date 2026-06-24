from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


TERMINAL_STATUSES = {"done", "failed", "cancelled"}
SAFE_TASK_MODES = {"plan_only", "read_only"}


@dataclass(slots=True)
class AgentTaskPermission:
    require_user_approval: bool = True
    approved_for_execution: bool = False
    allow_code_write: bool = False
    allow_shell_command: bool = False
    allow_git_commit: bool = False
    allow_git_push: bool = False
    allow_file_delete: bool = False
    allow_network_access: bool = False

    @classmethod
    def from_dict(cls, payload: dict[str, Any] | None) -> "AgentTaskPermission":
        payload = payload or {}
        return cls(
            require_user_approval=bool(payload.get("require_user_approval", True)),
            approved_for_execution=bool(payload.get("approved_for_execution", False)),
            allow_code_write=bool(payload.get("allow_code_write", False)),
            allow_shell_command=bool(payload.get("allow_shell_command", False)),
            allow_git_commit=bool(payload.get("allow_git_commit", False)),
            allow_git_push=bool(payload.get("allow_git_push", False)),
            allow_file_delete=bool(payload.get("allow_file_delete", False)),
            allow_network_access=bool(payload.get("allow_network_access", False)),
        )

    def requests_elevated_access(self) -> bool:
        return any(
            (
                self.allow_code_write,
                self.allow_shell_command,
                self.allow_git_commit,
                self.allow_git_push,
                self.allow_file_delete,
                self.allow_network_access,
            )
        )


@dataclass(slots=True)
class AgentTask:
    id: str
    user_id: str
    source_app: str
    source_task_id: str | None
    target_agent: str
    project_id: str
    task_type: str
    mode: str
    goal: str
    prompt: str
    context: dict[str, Any] = field(default_factory=dict)
    permission: AgentTaskPermission = field(default_factory=AgentTaskPermission)
    status: str = "pending"
    created_at: str | None = None
    updated_at: str | None = None
    claimed_by: str | None = None
    claimed_at: str | None = None

    @classmethod
    def from_dict(cls, payload: dict[str, Any]) -> "AgentTask":
        return cls(
            id=str(payload.get("id", "")),
            user_id=str(payload.get("user_id", "")),
            source_app=str(payload.get("source_app", "")),
            source_task_id=payload.get("source_task_id"),
            target_agent=str(payload.get("target_agent", "")),
            project_id=str(payload.get("project_id", "")),
            task_type=str(payload.get("task_type", "")),
            mode=str(payload.get("mode", "plan_only")),
            goal=str(payload.get("goal", "")),
            prompt=str(payload.get("prompt", "")),
            context=dict(payload.get("context") or {}),
            permission=AgentTaskPermission.from_dict(payload.get("permission")),
            status=str(payload.get("status", "pending")),
            created_at=payload.get("created_at"),
            updated_at=payload.get("updated_at"),
            claimed_by=payload.get("claimed_by"),
            claimed_at=payload.get("claimed_at"),
        )

    def requires_waiting_approval(self) -> bool:
        if self.permission.requests_elevated_access() and not self.permission.approved_for_execution:
            return True
        return self.mode not in SAFE_TASK_MODES and not self.permission.approved_for_execution


@dataclass(slots=True)
class AgentProgressEvent:
    phase: str
    message: str
    created_at: str
    level: str = "info"

    def to_dict(self) -> dict[str, Any]:
        return {
            "phase": self.phase,
            "message": self.message,
            "created_at": self.created_at,
            "level": self.level,
        }


@dataclass(slots=True)
class AgentExecutionResult:
    summary: str
    plan_markdown: str
    files_to_touch: list[str] = field(default_factory=list)
    risks: list[str] = field(default_factory=list)
    test_suggestions: list[str] = field(default_factory=list)
    raw_stdout: str = ""
    raw_stderr: str = ""
    exit_code: int | None = None
    current_phase: str = ""
    progress_events: list[AgentProgressEvent] = field(default_factory=list)

    def to_supabase_result(self) -> dict[str, Any]:
        return {
            "summary": self.summary,
            "plan_markdown": self.plan_markdown,
            "files_to_touch": self.files_to_touch,
            "risks": self.risks,
            "test_suggestions": self.test_suggestions,
            "raw_stdout": self.raw_stdout,
            "raw_stderr": self.raw_stderr,
            "exit_code": self.exit_code,
            "current_phase": self.current_phase,
            "progress_events": [event.to_dict() for event in self.progress_events],
        }
