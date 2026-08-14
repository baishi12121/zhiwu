package com.hyf.mallaiservice.config;

import com.hyf.mallaiservice.properties.PythonServiceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Spring Boot 调用 Python FastAPI 的 HTTP 配置。
 */
@Configuration
public class PythonServiceConfig {

    @Bean
    public RestClient pythonRestClient(PythonServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeout()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeout()));

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public TaskExecutor aiStreamTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ai-stream-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }
}
