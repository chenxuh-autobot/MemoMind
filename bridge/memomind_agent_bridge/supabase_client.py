from __future__ import annotations

import json
from datetime import datetime, timezone
from typing import Any
from urllib import error, parse, request

from .config import BridgeConfig
from .models import AgentTask


class SupabaseClient:
    def __init__(self, config: BridgeConfig) -> None:
        self._config = config

    def fetch_pending_tasks(self, limit: int = 1) -> list[AgentTask]:
        rows = self._request_json(
            method="GET",
            path=f"/rest/v1/{self._config.supabase_table}",
            query={
                "status": "eq.pending",
                "order": "created_at.asc",
                "limit": str(limit),
                "select": "*",
            },
        )
        return [AgentTask.from_dict(row) for row in rows]

    def fetch_recent_tasks(self, limit: int = 10) -> list[AgentTask]:
        rows = self._request_json(
            method="GET",
            path=f"/rest/v1/{self._config.supabase_table}",
            query={
                "order": "updated_at.desc",
                "limit": str(limit),
                "select": "*",
            },
        )
        return [AgentTask.from_dict(row) for row in rows]

    def claim_task(self, task_id: str) -> AgentTask | None:
        claimed_at = datetime.now(tz=timezone.utc).isoformat()
        rows = self._request_json(
            method="PATCH",
            path=f"/rest/v1/{self._config.supabase_table}",
            query={
                "id": f"eq.{task_id}",
                "status": "eq.pending",
                "select": "*",
            },
            body={
                "status": "running",
                "claimed_by": self._config.claimed_by,
                "claimed_at": claimed_at,
            },
            extra_headers={"Prefer": "return=representation"},
        )
        if not rows:
            return None
        return AgentTask.from_dict(rows[0])

    def fetch_stale_running_tasks(self, *, claimed_by: str, stale_before_iso: str, limit: int = 10) -> list[AgentTask]:
        rows = self._request_json(
            method="GET",
            path=f"/rest/v1/{self._config.supabase_table}",
            query={
                "status": "eq.running",
                "claimed_by": f"eq.{claimed_by}",
                "updated_at": f"lt.{stale_before_iso}",
                "order": "updated_at.asc",
                "limit": str(limit),
                "select": "*",
            },
        )
        return [AgentTask.from_dict(row) for row in rows]

    def mark_done(self, task_id: str, result: dict[str, Any]) -> None:
        self._request_json(
            method="PATCH",
            path=f"/rest/v1/{self._config.supabase_table}",
            query={
                "id": f"eq.{task_id}",
                "status": "eq.running",
            },
            body={
                "status": "done",
                "result": result,
                "error": None,
            },
        )

    def mark_failed_if_running(self, task_id: str, error_payload: dict[str, Any], result: dict[str, Any] | None = None) -> bool:
        body: dict[str, Any] = {
            "status": "failed",
            "error": error_payload,
        }
        if result is not None:
            body["result"] = result
        rows = self._request_json(
            method="PATCH",
            path=f"/rest/v1/{self._config.supabase_table}",
            query={
                "id": f"eq.{task_id}",
                "status": "eq.running",
                "select": "id",
            },
            body=body,
            extra_headers={"Prefer": "return=representation"},
        )
        return bool(rows)

    def mark_failed(self, task_id: str, error_payload: dict[str, Any], result: dict[str, Any] | None = None) -> None:
        body: dict[str, Any] = {
            "status": "failed",
            "error": error_payload,
        }
        if result is not None:
            body["result"] = result
        self._request_json(
            method="PATCH",
            path=f"/rest/v1/{self._config.supabase_table}",
            query={
                "id": f"eq.{task_id}",
                "status": "eq.running",
            },
            body=body,
        )

    def mark_waiting_approval(self, task_id: str, result: dict[str, Any], error_payload: dict[str, Any] | None = None) -> None:
        self._request_json(
            method="PATCH",
            path=f"/rest/v1/{self._config.supabase_table}",
            query={
                "id": f"eq.{task_id}",
                "status": "eq.running",
            },
            body={
                "status": "waiting_approval",
                "result": result,
                "error": error_payload,
            },
        )

    def update_progress(
        self,
        task_id: str,
        summary: str,
        current_phase: str,
        progress_events: list[dict[str, Any]],
    ) -> None:
        self._request_json(
            method="PATCH",
            path=f"/rest/v1/{self._config.supabase_table}",
            query={
                "id": f"eq.{task_id}",
                "status": "eq.running",
            },
            body={
                "result": {
                    "summary": summary,
                    "plan_markdown": "",
                    "files_to_touch": [],
                    "risks": [],
                    "test_suggestions": [],
                    "raw_stdout": "",
                    "raw_stderr": "",
                    "exit_code": None,
                    "current_phase": current_phase,
                    "progress_events": progress_events,
                },
            },
        )

    def _request_json(
        self,
        method: str,
        path: str,
        query: dict[str, str] | None = None,
        body: dict[str, Any] | None = None,
        extra_headers: dict[str, str] | None = None,
    ) -> list[dict[str, Any]]:
        query_string = parse.urlencode(query or {})
        url = f"{self._config.supabase_url}{path}"
        if query_string:
            url = f"{url}?{query_string}"
        headers = {
            "apikey": self._config.supabase_service_role_key,
            "Authorization": f"Bearer {self._config.supabase_service_role_key}",
            "Accept": "application/json",
        }
        if body is not None:
            headers["Content-Type"] = "application/json"
        if extra_headers:
            headers.update(extra_headers)
        payload = json.dumps(body).encode("utf-8") if body is not None else None
        req = request.Request(url=url, data=payload, headers=headers, method=method)
        try:
            with request.urlopen(req, timeout=20) as response:
                text = response.read().decode("utf-8").strip()
        except error.HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"Supabase HTTP {exc.code}: {detail}") from exc
        if not text:
            return []
        parsed = json.loads(text)
        if isinstance(parsed, list):
            return parsed
        if isinstance(parsed, dict):
            return [parsed]
        return []
