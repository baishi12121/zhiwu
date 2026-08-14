"""
聊天 Agent 适配层。

当前不改动 LangGraph 核心链路，只把现有 QueryService 的 SSE 输出转换成
业务后端约定的 token/thinking/tool_result/finish/error 事件结构。
"""

import json
from collections.abc import AsyncIterator
from typing import Any

from app.api.schemas.chat_schema import ChatStreamEvent
from app.services.query_service import QueryService
from app.services.error_message import to_public_error_message


class ChatAgent:
    """负责调用现有问数 Agent，并输出统一聊天事件。"""

    def __init__(self, query_service: QueryService):
        self.query_service = query_service

    async def stream(self, question: str) -> AsyncIterator[ChatStreamEvent]:
        async for raw_event in self.query_service.query(question):
            agent_event = _parse_sse_data(raw_event)
            if not agent_event:
                continue

            event_type = agent_event.get("type")
            if event_type == "progress":
                yield ChatStreamEvent(
                    type="thinking",
                    content=_format_progress(agent_event),
                )
            elif event_type == "summary":
                message = str(agent_event.get("message") or "")
                for token in message:
                    yield ChatStreamEvent(type="token", content=token)
            elif event_type == "result":
                data = agent_event.get("data")
                if data is not None:
                    yield ChatStreamEvent(
                        type="tool_result",
                        content=json.dumps(data, ensure_ascii=False, default=str),
                    )
            elif event_type == "error":
                yield ChatStreamEvent(
                    type="error",
                    content=to_public_error_message(
                        str(agent_event.get("message") or "AI 服务处理失败")
                    ),
                )
                return

        yield ChatStreamEvent(type="finish")


def _parse_sse_data(raw_event: str) -> dict[str, Any] | None:
    for line in raw_event.splitlines():
        if not line.startswith("data:"):
            continue
        payload = line.removeprefix("data:").strip()
        if not payload:
            continue
        return json.loads(payload)
    return None


def _format_progress(event: dict[str, Any]) -> str:
    step = event.get("step") or "agent"
    status = event.get("status") or "running"
    return f"{step}:{status}"
