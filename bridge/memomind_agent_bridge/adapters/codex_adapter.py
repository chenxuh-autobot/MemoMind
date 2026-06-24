from __future__ import annotations

import os
import shlex
import subprocess
import tempfile
import time
from contextlib import ExitStack
from pathlib import Path

from ..config import BridgeConfig
from ..models import AgentExecutionResult, AgentTask, SAFE_TASK_MODES
from .base import BaseAgentAdapter, ProgressCallback


class CodexAdapter(BaseAgentAdapter):
    def __init__(self, config: BridgeConfig, project_registry: dict[str, str]) -> None:
        self._config = config
        self._project_registry = project_registry

    def execute(self, task: AgentTask, progress_callback: ProgressCallback | None = None) -> AgentExecutionResult:
        is_workspace_write = task.mode == "workspace_write"
        if task.mode not in SAFE_TASK_MODES and not is_workspace_write:
            raise ValueError(f"Unsupported task mode for CodexAdapter: {task.mode}")
        if is_workspace_write and not task.permission.approved_for_execution:
            raise ValueError("Workspace-write task must be explicitly approved before execution.")
        if not is_workspace_write and (task.permission.allow_code_write or task.permission.allow_shell_command):
            raise ValueError("Read-only Codex tasks cannot request write or shell permissions.")
        if task.permission.allow_git_commit or task.permission.allow_git_push:
            raise ValueError("CodexAdapter does not allow git commit or git push in MemoMind Bridge.")
        if task.permission.allow_file_delete:
            raise ValueError("CodexAdapter does not allow file deletion in MemoMind Bridge.")
        if task.permission.allow_network_access:
            raise ValueError("CodexAdapter does not allow network access in MemoMind Bridge.")

        project_path = self._resolve_project_path(task.project_id)
        prompt = self._build_prompt(task)
        codex_bridge_home = self._config.ensure_codex_bridge_home()
        with tempfile.NamedTemporaryFile(prefix="memomind_codex_", suffix=".txt", delete=False) as result_file:
            result_file_path = Path(result_file.name)
        with tempfile.NamedTemporaryFile(prefix="memomind_codex_stdout_", suffix=".log", delete=False) as stdout_file:
            stdout_file_path = Path(stdout_file.name)
        with tempfile.NamedTemporaryFile(prefix="memomind_codex_stderr_", suffix=".log", delete=False) as stderr_file:
            stderr_file_path = Path(stderr_file.name)

        command = [
            *shlex.split(self._config.codex_command),
            "exec",
            "--ignore-user-config",
            "--ignore-rules",
            "--disable",
            "plugins",
            "--disable",
            "browser_use",
            "--disable",
            "in_app_browser",
            "--sandbox",
            "workspace-write" if is_workspace_write else "read-only",
            "--skip-git-repo-check",
            "--ephemeral",
            "--output-last-message",
            str(result_file_path),
            prompt,
        ]
        env = os.environ.copy()
        env["CODEX_HOME"] = str(codex_bridge_home)

        with ExitStack() as stack:
            execution_cwd = project_path
            if not is_workspace_write:
                execution_cwd = self._prepare_plan_only_workspace(project_path, stack)

            start_monotonic = time.monotonic()
            last_heartbeat_at = start_monotonic
            stdout_handle = stack.enter_context(stdout_file_path.open("w", encoding="utf-8"))
            stderr_handle = stack.enter_context(stderr_file_path.open("w", encoding="utf-8"))
            process = subprocess.Popen(
                command,
                cwd=execution_cwd,
                stdout=stdout_handle,
                stderr=stderr_handle,
                text=True,
                env=env,
            )
            while True:
                if process.poll() is not None:
                    break
                now = time.monotonic()
                elapsed = int(now - start_monotonic)
                stderr_text = stderr_file_path.read_text(encoding="utf-8") if stderr_file_path.exists() else ""
                partial_output = result_file_path.read_text(encoding="utf-8") if result_file_path.exists() else ""
                if (
                    not partial_output.strip()
                    and elapsed >= self._config.codex_connectivity_fail_seconds
                    and self._reconnect_signal_count(stderr_text) >= self._config.codex_reconnect_fail_count
                ):
                    process.kill()
                    process.wait()
                    stdout_handle.flush()
                    stderr_handle.flush()
                    stdout = stdout_file_path.read_text(encoding="utf-8") if stdout_file_path.exists() else ""
                    raise RuntimeError(self._build_connectivity_failure_message(stderr_text, stdout))
                if progress_callback and (now - last_heartbeat_at) >= self._config.execution_heartbeat_seconds:
                    message = (
                        f"codex 仍在执行中，已运行 {elapsed}s。"
                        if not partial_output.strip()
                        else f"codex 仍在执行中，已运行 {elapsed}s，且已产生部分结果草稿。"
                    )
                    progress_callback("executing", message, "info")
                    last_heartbeat_at = now
                if now - start_monotonic > self._config.codex_timeout_seconds:
                    process.kill()
                    process.wait()
                    stdout_handle.flush()
                    stderr_handle.flush()
                    stdout = stdout_file_path.read_text(encoding="utf-8") if stdout_file_path.exists() else ""
                    stderr = stderr_file_path.read_text(encoding="utf-8") if stderr_file_path.exists() else ""
                    if partial_output.strip():
                        summary = self._derive_summary(partial_output, stdout or "", stderr or "")
                        return AgentExecutionResult(
                            summary=f"{summary}（Codex 超时，已回收部分输出）",
                            plan_markdown=partial_output,
                            files_to_touch=self._extract_list_section(partial_output, "需要修改的文件"),
                            risks=self._extract_list_section(partial_output, "风险点"),
                            test_suggestions=self._extract_list_section(partial_output, "测试建议"),
                            raw_stdout=stdout or "",
                            raw_stderr=stderr or "",
                            exit_code=124,
                        )
                    raise RuntimeError(self._build_timeout_message(stderr or ""))
                time.sleep(1)
            process.wait()
            stdout_handle.flush()
            stderr_handle.flush()
            stdout = stdout_file_path.read_text(encoding="utf-8") if stdout_file_path.exists() else ""
            stderr = stderr_file_path.read_text(encoding="utf-8") if stderr_file_path.exists() else ""
            completed = subprocess.CompletedProcess(
                args=command,
                returncode=process.returncode or 0,
                stdout=stdout,
                stderr=stderr,
            )
            plan_markdown = result_file_path.read_text(encoding="utf-8") if result_file_path.exists() else ""

        result_file_path.unlink(missing_ok=True)
        stdout_file_path.unlink(missing_ok=True)
        stderr_file_path.unlink(missing_ok=True)

        if completed.returncode != 0 and not plan_markdown.strip():
            raise RuntimeError(
                "Codex exec failed without a usable plan output.\n"
                f"stdout:\n{completed.stdout}\n\nstderr:\n{completed.stderr}"
            )

        summary = self._derive_summary(plan_markdown, completed.stdout, completed.stderr)
        return AgentExecutionResult(
            summary=summary,
            plan_markdown=plan_markdown,
            files_to_touch=self._extract_list_section(plan_markdown, "需要修改的文件"),
            risks=self._extract_list_section(plan_markdown, "风险点"),
            test_suggestions=self._extract_list_section(plan_markdown, "测试建议"),
            raw_stdout=completed.stdout,
            raw_stderr=completed.stderr,
            exit_code=completed.returncode,
        )

    def _prepare_plan_only_workspace(self, project_path: Path, stack: ExitStack) -> Path:
        temp_root = Path(stack.enter_context(tempfile.TemporaryDirectory(prefix="memomind_codex_workspace_")))
        for child in project_path.iterdir():
            if child.name == ".git":
                continue
            (temp_root / child.name).symlink_to(child, target_is_directory=child.is_dir())
        return temp_root

    def _resolve_project_path(self, project_id: str) -> Path:
        raw_path = self._project_registry.get(project_id)
        if not raw_path:
            raise KeyError(f"Unknown project_id: {project_id}")
        path = Path(raw_path).expanduser().resolve()
        if not path.exists():
            raise FileNotFoundError(f"Configured project path does not exist: {path}")
        return path

    def _build_prompt(self, task: AgentTask) -> str:
        if task.mode == "workspace_write":
            execution_requirements = """
1. 先分析当前项目结构，再实施代码修改。
2. 允许在当前工作区内修改文件，但不要删除文件。
3. 不要执行 git commit 或 git push。
4. 不要访问网络资源。
5. 完成后输出：变更摘要、修改文件、风险点、验证结果、后续建议。
""".strip()
        else:
            execution_requirements = f"""
1. 先分析当前项目结构。
2. 当前模式为 {task.mode}，只输出实现计划。
3. 不要修改任何文件。
4. 不要执行 git push。
5. 不要删除文件。
6. 输出内容必须包括：
   - 项目结构分析
   - 需要修改的文件
   - 实现步骤
   - 风险点
   - 测试建议
   - 后续如果允许 workspace_write，应该如何执行
""".strip()
        return f"""
你是 MemoMind 项目的电脑端 Codex Agent。

任务目标：
{task.goal}

具体要求：
{task.prompt}

上下文：
{task.context}

执行要求：
{execution_requirements}
""".strip()

    def _derive_summary(self, plan_markdown: str, stdout: str, stderr: str) -> str:
        for source in (plan_markdown, stdout, stderr):
            first_line = next((line.strip() for line in source.splitlines() if line.strip()), "")
            if first_line:
                return first_line[:140]
        return "Codex 已完成 MemoMind Agent Bridge 计划生成。"

    def _extract_list_section(self, plan_markdown: str, section_title: str) -> list[str]:
        capture = False
        lines: list[str] = []
        for raw_line in plan_markdown.splitlines():
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

    def _build_timeout_message(self, stderr: str) -> str:
        base = (
            f"Codex exec timed out after {self._config.codex_timeout_seconds}s. "
            "Desktop Codex did not return a final result in time."
        )
        diagnostics: list[str] = []
        lowered = stderr.lower()
        if "stream disconnected" in lowered or "reconnecting" in lowered:
            diagnostics.append("检测到 Codex 与模型流连接反复中断并重连")
        if "failed to connect to github.com" in lowered:
            diagnostics.append("检测到 Codex 插件同步访问 github.com 失败")
        if "api rate limit exceeded" in lowered:
            diagnostics.append("检测到 GitHub API rate limit exceeded")
        if diagnostics:
            return f"{base} {'；'.join(diagnostics)}。"
        return f"{base} Consider increasing CODEX_TIMEOUT_SECONDS if your network is unstable."

    def _build_connectivity_failure_message(self, stderr: str, stdout: str) -> str:
        diagnostics: list[str] = ["Codex 提前终止：检测到桌面 Codex 进入持续重连，短时间内无法稳定返回结果。"]
        lowered = f"{stderr}\n{stdout}".lower()
        if "stream disconnected" in lowered or "reconnecting" in lowered:
            diagnostics.append("模型流连接反复中断")
        if "failed to connect to github.com" in lowered:
            diagnostics.append("插件同步访问 github.com 失败")
        if "api rate limit exceeded" in lowered:
            diagnostics.append("GitHub API rate limit exceeded")
        return "；".join(diagnostics) + "。"

    def _reconnect_signal_count(self, stderr: str) -> int:
        lowered = stderr.lower()
        return lowered.count("stream disconnected") + lowered.count("reconnecting")
