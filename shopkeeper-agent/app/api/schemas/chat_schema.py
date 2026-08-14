"""
聊天接口请求和响应模型。
"""

from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    """Spring Boot 调用 Python AI 服务的聊天请求。"""

    question: str = Field(..., min_length=1, max_length=500)
    conversationId: str | None = None
    userId: str | None = None
    knowledgeBaseId: str | None = None


class ChatData(BaseModel):
    """聊天接口业务数据。"""

    answer: str
    conversationId: str


class ChatResponse(BaseModel):
    """统一响应结构。"""

    code: int = 200
    message: str = "success"
    data: ChatData


class ChatStreamEvent(BaseModel):
    """SSE 流式事件。"""

    type: str
    content: str | None = None
    message: str | None = None
