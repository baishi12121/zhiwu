package com.hyf.mallaiservice.config;

import com.hyf.mallaiservice.properties.AiAgentProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * AI 服务配置类
 *
 * 创建 WebClient Bean，用于以响应式方式调用 shopkeeper-agent 的 SSE 接口
 * WebClient 是 Spring WebFlux 提供的非阻塞 HTTP 客户端，适合流式场景
 *
 * @author hyf
 */
@Configuration
public class AiServiceConfig {

    /**
     * 配置好的 WebClient，baseURL 指向 Python Agent
     */
    @Bean
    public WebClient aiAgentWebClient(AiAgentProperties properties) {
        // HttpClient 设置响应超时，避免长时间流式响应被误判超时
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(properties.getTimeoutMs()));

        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
