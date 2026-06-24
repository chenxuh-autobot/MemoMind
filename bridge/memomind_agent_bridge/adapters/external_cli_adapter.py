from __future__ import annotations

import shlex
import subprocess
import tempfile
from datetime import datetime, timezone
from pathlib import Path

from ..config import ExternalAgentProfile
from ..models import AgentExecutionResult, AgentProgressEvent, AgentTask
from .base import BaseAgentAdapter, ProgressCallback


class ExternalCliAgentAdapter(BaseAgentAdapter):
    def __init__(
        self,
        profile: ExternalAgentProfile,
        project_registry: dict[str, str],
    ) -> None:
        self._profile = profile
        self._project_registry = project_registry

    def execute(self, task: AgentTask, progress_callback: ProgressCallback | None = None) -> AgentExecutionResult:
        project_path = self._resolve_project_path(task.project_id)
        prompt = self._build_prompt(task)
        with tempfile.NamedTemporaryFile(prefix=f"memomind_{self._profile.agent_id}_prompt_", suffix=".txt", delete=False) as prompt_file:
            prompt_file_path = Path(prompt_file.name)
        prompt_file_path.write_text(prompt, encoding="utf-8")
        with tempfile.NamedTemporaryFile(prefix=f"memomind_{self._profile.agent_id}_output_", suffix=".md", delete=False) as output_file:
            output_file_path = Path(output_file.name)

        command = self._build_command(
            prompt=prompt,
            prompt_file_path=prompt_file_path,
            output_file_path=output_file_path,
            project_path=project_path,
            task=task,
        )

        try:
            completed = subprocess.run(
                command,
                cwd=project_path,
                capture_output=True,
                text=True,
                timeout=self._profile.timeout_seconds,
                check=False,
            )
            output_markdown = output_file_path.read_text(encoding="utf-8") if output_file_path.exists() else ""
        finally:
            prompt_file_path.unlink(missing_ok=True)
            output_file_path.unlink(missing_ok=True)

        launcher_only = "{output_file}" not in self._profile.args_template
        combined_output = output_markdown.strip() or completed.stdout.strip()
        if not combined_output and launcher_only and completed.returncode == 0:
            combined_output = (
                f"# {self._profile.agent_id} 已接收任务\n\n"
                f"- Bridge 已成功唤起 {self._profile.agent_id} 桌面端会话。\n"
                "- 当前 CLI 仅支持会话启动，不支持结构化结果自动回写。\n"
                "- 请在电脑端继续查看并同步执行结果。"
            )
        elif launcher_only and completed.returncode == 0 and not output_markdown.strip():
            combined_output = (
                f"# {self._profile.agent_id} 已接收任务\n\n"
                f"- Bridge 已成功唤起 {self._profile.agent_id} 桌面端会话。\n"
                "- 当前 CLI 暂未提供结构化结果文件，以下为启动日志。\n\n"
                f"```text\n{completed.stdout.strip()}\n```"
            )
        if completed.returncode != 0 and not combined_output:
            raise RuntimeError(
                f"{self._profile.agent_id} CLI failed without a usable output.\n"
                f"stdout:\n{completed.stdout}\n\nstderr:\n{completed.stderr}"
            )

        progress_events = [
            AgentProgressEvent(
                phase="external_cli_completed",
                message=f"{self._profile.agent_id} CLI 已返回，exit_code={completed.returncode}。",
                created_at=datetime.now(tz=timezone.utc).isoformat(),
                level="info" if completed.returncode == 0 else "warning",
            )
        ]
        return AgentExecutionResult(
            summary=self._derive_summary(combined_output, completed.stdout, completed.stderr),
            plan_markdown=combined_output,
            files_to_touch=self._extract_list_section(combined_output, "需要修改的文件"),
            risks=self._extract_list_section(combined_output, "风险点"),
            test_suggestions=self._extract_list_section(combined_output, "测试建议"),
            raw_stdout=completed.stdout,
            raw_stderr=completed.stderr,
            exit_code=completed.returncode,
            current_phase="external_cli_completed",
            progress_events=progress_events,
        )

    def _resolve_project_path(self, project_id: str) -> Path:
        raw_path = self._project_registry.get(project_id)
        if not raw_path:
            raise KeyError(f"Unknown project_id: {project_id}")
        path = Path(raw_path).expanduser().resolve()
        if not path.exists():
            raise FileNotFoundError(f"Configured project path does not exist: {path}")
        return path

    def _build_command(
        self,
        prompt: str,
        prompt_file_path: Path,
        output_file_path: Path,
        project_path: Path,
        task: AgentTask,
    ) -> list[str]:
        format_vars = {
            "prompt": prompt,
            "prompt_file": str(prompt_file_path),
            "output_file": str(output_file_path),
            "project_path": str(project_path),
            "mode": task.mode,
            "goal": task.goal,
            "target_agent": task.target_agent,
        }
        if not self._profile.args_template:
            return [*shlex.split(self._profile.command), prompt]
        args = [token.format_map(format_vars) for token in shlex.split(self._profile.args_template)]
        return [*shlex.split(self._profile.command), *args]

    def _build_prompt(self, task: AgentTask) -> str:
        mode_instruction = {
            "plan_only": "当前任务只需要输出计划或结构化结果，不要修改文件。",
            "read_only": "当前任务只能在只读前提下分析项目或纪要内容，不要修改文件。",
            "workspace_write": "当前任务已获批准，可以在当前工作区内完成修改，但不要删除文件、不要执行 git push。",
        }.get(task.mode, f"当前任务模式为 {task.mode}。")
        return f"""
你是 MemoMind 桌面端 Agent，目标 agent 为 {self._profile.agent_id}。

任务目标：
{task.goal}

具体要求：
{task.prompt}

上下文：
{task.context}

执行约束：
1. {mode_instruction}
2. 优先输出清晰、结构化、可执行的结果。
3. 不要执行 git push。
4. 如果适合，请明确列出后续步骤、风险点和测试/验证建议。
5. 如果产出涉及项目修改，请额外列出需要修改的文件。
""".strip()

    def _derive_summary(self, plan_markdown: str, stdout: str, stderr: str) -> str:
        for source in (plan_markdown, stdout, stderr):
            first_line = next((line.strip() for line in source.splitlines() if line.strip()), "")
            if first_line:
                return first_line[:140]
        return f"{self._profile.agent_id} 已完成 MemoMind Agent Bridge 任务。"

    def _extract_list_section(self, markdown: str, section_title: str) -> list[str]:
        capture = False
        lines: list[str] = []
        for raw_line in markdown.splitlines():
            line = raw_line.strip()
            if not line:
                if capture and lines:
                    break
                continue
            if section_title in line:
                capture = True
                continue
            if capture:
                if line.startswith("#"):
                    break
                normalized = line.removeprefix("- ").removeprefix("* ").strip()
                if normalized:
                    lines.append(normalized)
        return lines
