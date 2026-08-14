你现在是一个资深 Java + Python + AI Agent 全栈架构师。

请基于当前项目代码，对项目进行 Spring Boot + Python FastAPI 双服务架构改造。

【目标架构】

前端 Vue
↓ HTTP / SSE
Spring Boot
↓ HTTP
Python FastAPI
↓
AI Agent / RAG / LLM / OCR

其中：

1. Spring Boot 是业务服务
2. Python FastAPI 是 AI 服务
3. 前端禁止直接调用 Python 服务
4. 所有前端请求统一进入 Spring Boot
5. Spring Boot 负责调用 Python
6. Python 只负责 AI、RAG、Agent、模型等计算逻辑
7. Spring Boot 与 Python 之间使用 HTTP REST API 通信
8. 后续需要支持 SSE 流式响应


【重要要求】

不要修改现有业务逻辑。

优先采用最小改动原则。

先分析当前项目结构，再设计改造方案。

不要直接开始修改代码。

第一阶段只需要输出：

1. 当前项目架构分析
2. Spring Boot 与 Python 的职责划分
3. Spring Boot → Python 的接口设计
4. 请求/响应 DTO 设计
5. SSE 流式调用设计
6. 异常处理设计
7. 超时设计
8. 重试设计
9. 日志与 trace_id 设计
10. 推荐的项目目录结构

等待我确认方案后再开始修改代码。


## phase 1 【☑️】
现在开始实现 Spring Boot → Python FastAPI 的服务调用层。

【技术要求】

Spring Boot：
- Java 17
- Spring Boot 3.x
- 使用 RestClient
- 不使用 Feign
- 不引入不必要的新框架

Python：
- FastAPI
- HTTP REST API

【Spring Boot】
在mall-ai-service完成编码


Python 服务地址必须配置化。

application.yml：

python:
service:
base-url: http://localhost:8000
connect-timeout: 3000
read-timeout: 60000

禁止把：

http://localhost:8000

硬编码在业务代码中。

【调用方式】

Spring Boot：

POST /api/agent/chat

内部调用：

POST http://localhost:8000/api/chat

请求：

{
"question": "...",
"conversationId": "...",
"userId": "...",
"knowledgeBaseId": "..."
}

Python 返回：

{
"code": 200,
"message": "success",
"data": {
"answer": "...",
"conversationId": "..."
}
}

Spring Boot 将 Python 返回结果转换为自己的统一响应结构。

【要求】

1. Controller 不允许直接调用 RestClient
2. Controller → Service → PythonClient
3. PythonClient 专门负责 HTTP 通信
4. DTO 与 Python API 请求结构保持清晰
5. Python 服务异常必须统一处理
6. HTTP 4xx / 5xx 必须处理
7. 连接超时必须处理
8. 读取超时必须处理
9. 日志中必须包含 trace_id
10. 不打印 API Key、Token 等敏感信息
11. 不修改无关代码
12. 优先最小改动

完成后：

1. 输出修改了哪些文件
2. 输出每个文件的作用
3. 输出调用链
4. 输出启动 Spring Boot 和 Python 的方式
5. 给出 curl 测试命令

## phase 2 【☑️】
现在实现 Python FastAPI AI 服务接口。

【接口】

POST /api/chat

请求：

{
"question": "用户问题",
"conversationId": "会话ID",
"userId": "用户ID",
"knowledgeBaseId": "知识库ID"
}

返回：

{
"code": 200,
"message": "success",
"data": {
"answer": "AI回答",
"conversationId": "会话ID"
}
}

【要求】

1. 使用 Pydantic 定义 Request / Response
2. 不在 Controller 中写 AI 业务逻辑
3. API 层只负责参数接收和返回
4. Service 层负责业务逻辑
5. Agent 层负责 Agent
6. RAG 层负责检索
7. LLM 层负责模型调用


不要为了实现接口修改现有 Agent / RAG 业务逻辑。

如果当前项目已经存在对应模块，优先复用。


## phase 3 【☑️】
现在在现有 Spring Boot → Python FastAPI 架构上增加 SSE 流式响应。

【目标】

Vue
↓ SSE
Spring Boot
↓ HTTP Streaming
Python FastAPI
↓
Agent
↓
LLM Stream

要求：

1. 前端不能直接访问 Python
2. Spring Boot 作为 SSE 网关
3. Spring Boot 调用 Python Streaming API
4. Python 使用 StreamingResponse
5. Python 产生一个 chunk 就向 Spring Boot 返回一个 chunk
6. Spring Boot 不等待 Python 完整响应
7. Spring Boot 收到 chunk 后立即发送给前端
8. 禁止一次性读取完整 response
9. 支持正常结束
10. 支持异常结束
11. 支持客户端主动断开
12. 支持 trace_id

接口：

Spring Boot：

GET /api/agent/chat/stream

Python：

POST /api/chat/stream

Python 返回：

data: {"type":"token","content":"你"}

data: {"type":"token","content":"好"}

data: {"type":"token","content":"，"}

data: {"type":"finish"}

【事件类型】

token
thinking
tool_call
tool_result
citation
finish
error

统一事件结构：

{
"type": "token",
"content": "..."
}

请结合当前项目技术栈实现。

不要修改现有 Agent 核心逻辑。

完成后给出完整调用链和关键代码。
