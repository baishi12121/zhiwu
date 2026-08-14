package com.hyf.mallaiservice.controller;

import com.hyf.mallaiservice.dto.AgentChatRequest;
import com.hyf.mallaiservice.dto.AgentChatResponse;
import com.hyf.mallaiservice.service.AgentChatService;
import com.hyf.mallcommon.core.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * Spring Boot 对外暴露的 AI 聊天接口。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentChatController {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final AgentChatService agentChatService;

    public AgentChatController(AgentChatService agentChatService) {
        this.agentChatService = agentChatService;
    }

    @PostMapping("/chat")
    public Result<AgentChatResponse> chat(@Valid @RequestBody AgentChatRequest request,
                                          HttpServletRequest servletRequest) {
        String traceId = resolveTraceId(servletRequest);
        MDC.put("trace_id", traceId);
        try {
            return Result.success(agentChatService.chat(request, traceId));
        } finally {
            MDC.remove("trace_id");
        }
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @ModelAttribute AgentChatRequest request,
                                 HttpServletRequest servletRequest) {
        String traceId = resolveTraceId(servletRequest);
        MDC.put("trace_id", traceId);
        try {
            return agentChatService.streamChat(request, traceId);
        } finally {
            MDC.remove("trace_id");
        }
    }

    private static String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        return traceId;
    }
}
