from __future__ import annotations

from abc import ABC, abstractmethod
from collections.abc import Callable

from ..models import AgentExecutionResult, AgentTask

ProgressCallback = Callable[[str, str, str], None]


class BaseAgentAdapter(ABC):
    @abstractmethod
    def execute(self, task: AgentTask, progress_callback: ProgressCallback | None = None) -> AgentExecutionResult:
        raise NotImplementedError
