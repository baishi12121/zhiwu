package com.hyf.mallaiservice.service.impl;

import com.hyf.mallaiservice.client.PythonClient;
import com.hyf.mallaiservice.dto.AgentChatRequest;
import com.hyf.mallaiservice.dto.AgentChatResponse;
import com.hyf.mallaiservice.dto.AgentStreamEvent;
import com.hyf.mallaiservice.exception.PythonServiceException;
import com.hyf.mallaiservice.properties.PythonServiceProperties;
import com.hyf.mallaiservice.service.AgentChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Spring Boot 业务层，负责调用 PythonClient 并统一返回给 Controller。
 */
@Service
public class AgentChatServiceImpl implements AgentChatService {

    private static final Logger log = LoggerFactory.getLogger(AgentChatServiceImpl.class);

    private final PythonClient pythonClient;
    private final PythonServiceProperties properties;
    private final TaskExecutor aiStreamTaskExecutor;

    public AgentChatServiceImpl(PythonClient pythonClient,
                                PythonServiceProperties properties,
                                @Qualifier("aiStreamTaskExecutor") TaskExecutor aiStreamTaskExecutor) {
        this.pythonClient = pythonClient;
        this.properties = properties;
        this.aiStreamTaskExecutor = aiStreamTaskExecutor;
    }

    @Override
    public AgentChatResponse chat(AgentChatRequest request, String traceId) {
        return pythonClient.chat(request, traceId);
    }

    @Override
    public SseEmitter streamChat(AgentChatRequest request, String traceId) {
        SseEmitter emitter = new SseEmitter((long) properties.getReadTimeout() + 5000L);
        AtomicBoolean active = new AtomicBoolean(true);

        emitter.onCompletion(() -> {
            active.set(false);
            log.info("[trace_id={}] 前端 SSE 正常结束", traceId);
        });
        emitter.onTimeout(() -> {
            active.set(false);
            log.warn("[trace_id={}] 前端 SSE 超时", traceId);
            sendQuietly(emitter, AgentStreamEvent.error("AI 流式响应超时"));
            emitter.complete();
        });
        emitter.onError(e -> {
            active.set(false);
            log.warn("[trace_id={}] 前端 SSE 连接异常: {}", traceId, e.getMessage());
        });

        aiStreamTaskExecutor.execute(() -> {
            MDC.put("trace_id", traceId);
            try {
                pythonClient.streamChat(request, traceId, event -> {
                    if (!active.get()) {
                        throw new PythonServiceException("前端 SSE 已断开");
                    }
                    sendQuietly(emitter, event);
                    if ("finish".equals(event.getType())) {
                        active.set(false);
                        emitter.complete();
                    }
                });
                emitter.complete();
            } catch (PythonServiceException e) {
                if (active.get()) {
                    log.warn("[trace_id={}] Python 流式调用失败: {}", traceId, e.getMessage());
                    sendQuietly(emitter, AgentStreamEvent.error(e.getMessage()));
                } else {
                    log.info("[trace_id={}] SSE 已断开，停止转发 Python 流", traceId);
                }
                emitter.complete();
            } finally {
                MDC.remove("trace_id");
            }
        });

        return emitter;
    }

    private static void sendQuietly(SseEmitter emitter, AgentStreamEvent event) {
        try {
            emitter.send(SseEmitter.event().data(event));
        } catch (IOException | IllegalStateException ignored) {
            emitter.complete();
        }
    }
}
