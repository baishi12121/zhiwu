package com.hyf.mallaiservice.service;

import com.hyf.mallaiservice.dto.AgentChatRequest;
import com.hyf.mallaiservice.dto.AgentChatResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 智能客服对接业务服务。
 */
public interface AgentChatService {

    AgentChatResponse chat(AgentChatRequest request, String traceId);

    SseEmitter streamChat(AgentChatRequest request, String traceId);
}
