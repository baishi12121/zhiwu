package com.hyf.mallaiservice.service.impl;


import com.hyf.mallaiservice.service.AiAgentService;
import com.hyf.mallaiservice.dto.AgentQueryRequest;
import com.hyf.mallaiservice.properties.AiAgentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/**
 * AI Agent 服务
 *
 * 负责调用 shopkeeper-agent Python 服务的 SSE 接口，
 * 过滤掉内部 pipeline 的 progress 进度事件，
 * 只把 AI 最终回答（summary / result / error）流式推送到前端。
 *
 * @author hyf
 */
@Service
public class AiAgentServiceImpl implements AiAgentService {

    private static final Logger log = LoggerFactory.getLogger(AiAgentService.class);

    private final WebClient aiAgentWebClient;
    private final AiAgentProperties properties;
    public AiAgentServiceImpl(WebClient aiAgentWebClient, AiAgentProperties properties) {
        this.aiAgentWebClient = aiAgentWebClient;
        this.properties = properties;
    }

    /**
     * 调用 Python Agent 的 SSE 查询接口
     *
     * 只将 AI 最终回答流式推送到前端，过滤掉内部 pipeline 的 progress 进度事件。
     *
     * 关键发现：WebClient bodyToFlux(String.class) 接收到的每个 chunk 已经是
     * 纯 JSON 对象（{@code {"type":"progress",...}}），不含 SSE 的 data: 前缀
     * 和 \n\n 分隔符。推测是 Netty/Spring 的 HTTP 客户端在 text/event-stream
     * 响应中自动拆帧并剥离了 SSE 协议头。
     *
     * 因此无需再做 \n\n 拆帧，直接在每个 chunk 上判断 type 即可。
     *
     * @param query 用户自然语言问题
     * @return SSE 文本流（仅含 summary / result / error 事件）
     */
    public Flux<String> chat(String query) {
        log.info("调用智能客服 Agent，问题: {}", query);

        AgentQueryRequest requestBody = new AgentQueryRequest(query);

        return aiAgentWebClient.post()
                .uri(properties.getQueryPath())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(AiAgentServiceImpl::isNotProgressEvent)
                .doOnSubscribe(s -> log.info("[SSE] 订阅 Python Agent 请求开始"))
                .doOnNext(data -> log.info("[SSE] 透传最终结果: {}",
                        data.length() > 200 ? data.substring(0, 200) + "..." : data))
                .doOnComplete(() -> log.info("[SSE] Python Agent 流结束"))
                .doOnCancel(() -> log.warn("[SSE] Python Agent 流被取消（可能是前端断开连接或 MVC async 超时）"))
                .doOnError(e -> log.error("[SSE] 调用 Python Agent 失败", e))
                .onErrorResume(e -> Flux.just(
                        "{\"type\":\"error\",\"message\":\"智能客服服务暂时不可用，请稍后重试\"}"
                ));
    }

    /**
     * 判断 chunk 是否为最终结果（非 progress）。
     *
     * Netty 已自动拆帧，每个 chunk 是纯 JSON 对象（不含 data: 前缀和 \n\n）。
     * 只丢弃 type 为 "progress" 的流水线进度帧，
     * summary / result / error 全部放行。
     *
     * Python json.dumps 默认输出 {@code "type": "progress"}（冒号后有空格）。
     */
    private static boolean isNotProgressEvent(String json) {
        String trimmed = json.trim();
        if (trimmed.isEmpty()) return false;

        return !trimmed.contains("\"type\":\"progress\"")
                && !trimmed.contains("\"type\": \"progress\"");
    }

    /**
     * 健康检查：测试 Python Agent 是否可达
     */
    public boolean healthCheck() {
        try {
            aiAgentWebClient.get()
                    .uri("/docs")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(java.time.Duration.ofSeconds(3));
            return true;
        } catch (Exception e) {
            log.warn("Python Agent 健康检查失败: {}", e.getMessage());
            return false;
        }
    }
}
