package com.hyf.mallaiservice.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyf.mallaiservice.client.dto.PythonChatResponse;
import com.hyf.mallaiservice.dto.AgentChatRequest;
import com.hyf.mallaiservice.dto.AgentChatResponse;
import com.hyf.mallaiservice.dto.AgentStreamEvent;
import com.hyf.mallaiservice.exception.PythonServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.ConnectException;
import java.util.function.Consumer;

/**
 * 专门负责与 Python FastAPI 进行 HTTP 通信。
 */
@Component
public class PythonClient {

    private static final Logger log = LoggerFactory.getLogger(PythonClient.class);

    private final RestClient pythonRestClient;
    private final ObjectMapper objectMapper;

    public PythonClient(RestClient pythonRestClient, ObjectMapper objectMapper) {
        this.pythonRestClient = pythonRestClient;
        this.objectMapper = objectMapper;
    }

    public AgentChatResponse chat(AgentChatRequest request, String traceId) {
        try {
            log.info("[trace_id={}] 调用 Python 非流式聊天接口: conversationId={}, userId={}, knowledgeBaseId={}",
                    traceId, request.getConversationId(), request.getUserId(), request.getKnowledgeBaseId());

            PythonChatResponse response = pythonRestClient.post()
                    .uri("/api/chat")
                    .header("X-Trace-Id", traceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(PythonChatResponse.class);

            if (response == null) {
                throw new PythonServiceException("AI 服务无响应");
            }
            if (response.getCode() != 200) {
                throw new PythonServiceException("AI 服务返回失败: " + safeMessage(response.getMessage()));
            }
            if (response.getData() == null) {
                throw new PythonServiceException("AI 服务返回数据为空");
            }
            return response.getData();
        } catch (RestClientResponseException e) {
            log.warn("[trace_id={}] Python HTTP 状态异常: status={}, body={}",
                    traceId, e.getStatusCode().value(), abbreviate(e.getResponseBodyAsString()));
            throw new PythonServiceException("AI 服务暂时不可用", e);
        } catch (ResourceAccessException e) {
            log.warn("[trace_id={}] Python 连接或读取超时: {}", traceId, e.getMessage());
            throw new PythonServiceException(toConnectionErrorMessage(e, "AI 服务"), e);
        }
    }

    public void streamChat(AgentChatRequest request,
                           String traceId,
                           Consumer<AgentStreamEvent> eventConsumer) {
        try {
            log.info("[trace_id={}] 调用 Python 流式聊天接口: conversationId={}, userId={}, knowledgeBaseId={}",
                    traceId, request.getConversationId(), request.getUserId(), request.getKnowledgeBaseId());

            pythonRestClient.post()
                    .uri("/api/chat/stream")
                    .header("X-Trace-Id", traceId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .body(request)
                    .exchange((clientRequest, clientResponse) -> {
                        HttpStatusCode statusCode = clientResponse.getStatusCode();
                        if (statusCode.is4xxClientError() || statusCode.is5xxServerError()) {
                            throw new PythonServiceException("AI 流式服务返回异常: HTTP " + statusCode.value());
                        }
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                                clientResponse.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (!line.startsWith("data:")) {
                                    continue;
                                }
                                String json = line.substring("data:".length()).trim();
                                if (json.isEmpty()) {
                                    continue;
                                }
                                eventConsumer.accept(objectMapper.readValue(json, AgentStreamEvent.class));
                            }
                            return null;
                        } catch (IOException e) {
                            throw new PythonServiceException("读取 AI 流式响应失败", e);
                        }
                    });
        } catch (RestClientResponseException e) {
            log.warn("[trace_id={}] Python 流式 HTTP 状态异常: status={}, body={}",
                    traceId, e.getStatusCode().value(), abbreviate(e.getResponseBodyAsString()));
            throw new PythonServiceException("AI 流式服务暂时不可用", e);
        } catch (ResourceAccessException e) {
            log.warn("[trace_id={}] Python 流式连接或读取超时: {}", traceId, e.getMessage());
            throw new PythonServiceException(toConnectionErrorMessage(e, "AI 流式服务"), e);
        }
    }

    private static String toConnectionErrorMessage(ResourceAccessException e, String serviceName) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof ConnectException) {
                return serviceName + "未启动或地址配置错误，请检查 python.service.base-url";
            }
            cause = cause.getCause();
        }
        return serviceName + "连接超时或读取超时";
    }

    private static String safeMessage(String message) {
        return message == null || message.isBlank() ? "unknown" : message;
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 300 ? value.substring(0, 300) + "..." : value;
    }
}
