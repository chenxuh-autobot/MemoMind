from __future__ import annotations

from .adapters.base import BaseAgentAdapter
from .models import AgentTask


class AgentRouter:
    def __init__(self, adapters: dict[str, BaseAgentAdapter]) -> None:
        self._adapters = adapters

    def route(self, task: AgentTask) -> BaseAgentAdapter:
        adapter = self._adapters.get(self._normalize_agent_id(task.target_agent))
        if adapter is None:
            raise KeyError(f"Unsupported target_agent: {task.target_agent}")
        return adapter

    def _normalize_agent_id(self, agent_id: str) -> str:
        normalized = agent_id.strip().lower()
        aliases = {
            "workbuddy": "work_buddy",
            "work-buddy": "work_buddy",
        }
        return aliases.get(normalized, normalized)
