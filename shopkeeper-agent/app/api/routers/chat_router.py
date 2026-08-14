"""
聊天接口路由。
"""

from typing import Annotated

from fastapi import APIRouter, Depends
from starlette.responses import StreamingResponse

from app.api.dependencies import get_chat_service
from app.api.schemas.chat_schema import ChatRequest, ChatResponse
from app.services.chat_service import ChatService

chat_router = APIRouter()


@chat_router.post("/api/chat", response_model=ChatResponse)
async def chat_handler(
    request: ChatRequest,
    chat_service: Annotated[ChatService, Depends(get_chat_service)],
):
    """非流式聊天接口。"""

    return await chat_service.chat(request)


@chat_router.post("/api/chat/stream")
async def chat_stream_handler(
    request: ChatRequest,
    chat_service: Annotated[ChatService, Depends(get_chat_service)],
):
    """流式聊天接口。"""

    return StreamingResponse(
        chat_service.stream_chat(request),
        media_type="text/event-stream",
    )
