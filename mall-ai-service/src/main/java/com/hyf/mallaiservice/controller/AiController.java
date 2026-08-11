package com.hyf.mallaiservice.controller;

import com.hyf.mallaiservice.dto.ChatRequest;
import com.hyf.mallaiservice.service.AiAgentService;
import com.hyf.mallcommon.core.result.Result;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * AI 域 Controller
 *
 * 提供智能客服聊天接口和健康检查
 * /ai/chat 代理转发到 shopkeeper-agent Python 服务的 SSE 接口
 *
 * @author hyf
 */
@RestController
@RequestMapping("/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final AiAgentService aiAgentService;

    public AiController(AiAgentService aiAgentService) {
        this.aiAgentService = aiAgentService;
    }

    /**
     * 健康检查（包含 Python Agent 可达性检测）
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        boolean agentReachable = aiAgentService.healthCheck();
        return Result.success(Map.of(
                "service", "mall-ai-service",
                "status", "UP",
                "agentReachable", agentReachable,
                "note", "智能客服代理层，转发到 shopkeeper-agent"
        ));
    }

    /**
     * 智能客服聊天接口（SSE 流式响应）
     *
     * 前端通过 EventSource 或 fetch 消费 SSE 流
     * 每条消息格式：data: {"type":"progress|result|error","message":"..."}\n\n
     *
     * @param request 聊天请求（包含 query 字段）
     * @return SSE 文本流
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@Valid @RequestBody ChatRequest request) {
        log.info("收到智能客服提问: {}", request.getQuery());
        return aiAgentService.chat(request.getQuery());
    }

    /**
     * 兼容 GET 方式的简单测试接口（非流式，用于快速验证）
     */
    @GetMapping("/chat/test")
    public Result<String> chatTest(@RequestParam String query) {
        log.info("测试智能客服提问: {}", query);
        // 取 SSE 流的最后一条结果作为返回
        String lastResult = aiAgentService.chat(query)
                .blockLast();
        return Result.success(lastResult);
    }
}
