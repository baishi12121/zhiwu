"""
聊天业务服务。

Service 层负责会话 ID、非流式聚合和流式事件包装，具体 Agent 执行交给
ChatAgent，底层 RAG/LLM 能力继续复用现有 LangGraph 节点和仓储。
"""

import json
import uuid
from collections.abc import AsyncIterator

from app.agent.chat_agent import ChatAgent
from app.api.schemas.chat_schema import ChatData, ChatRequest, ChatResponse
from app.core.log import logger


class ChatService:
    """聊天业务编排服务。"""

    def __init__(self, chat_agent: ChatAgent):
        self.chat_agent = chat_agent

    async def chat(self, request: ChatRequest) -> ChatResponse:
        conversation_id = request.conversationId or str(uuid.uuid4())
        answer_parts: list[str] = []

        async for event in self.chat_agent.stream(request.question):
            if event.type == "token" and event.content:
                answer_parts.append(event.content)
            elif event.type == "error":
                raise RuntimeError(event.content or "AI 服务处理失败")

        answer = "".join(answer_parts)
        logger.info(f"非流式聊天完成 conversation_id={conversation_id}")
        return ChatResponse(
            data=ChatData(answer=answer, conversationId=conversation_id),
        )

    async def stream_chat(self, request: ChatRequest) -> AsyncIterator[str]:
        async for event in self.chat_agent.stream(request.question):
            payload = event.model_dump(exclude_none=True)
            yield f"data: {json.dumps(payload, ensure_ascii=False)}\n\n"
