package com.hyf.mallseckillservice.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyf.mallseckillservice.constant.SeckillConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.OptionalLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMqManagementClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    @Value("${seckill.rabbitmq.management-url:http://localhost:15672}")
    private String managementUrl;
    @Value("${seckill.rabbitmq.management-username:admin}")
    private String username;
    @Value("${seckill.rabbitmq.management-password:123456}")
    private String password;
    @Value("${spring.rabbitmq.virtual-host:/mall}")
    private String virtualHost;

    public OptionalLong orderQueueBacklog() {
        return queueBacklog(SeckillConstants.SECKILL_QUEUE);
    }

    public OptionalLong queueBacklog(String queueName) {
        try {
            String encodedVhost = "%2F".equals(virtualHost) || "/".equals(virtualHost)
                    ? "%2F"
                    : virtualHost.replace("/", "%2F");
            String url = managementUrl + "/api/queues/" + encodedVhost + "/" + queueName;
            String token = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .header("Authorization", "Basic " + token)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[seckill-rabbitmq] management api status={}, queue={}", response.statusCode(), queueName);
                return OptionalLong.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode messages = root.get("messages");
            return messages == null || !messages.canConvertToLong()
                    ? OptionalLong.empty()
                    : OptionalLong.of(messages.asLong());
        } catch (Exception e) {
            log.warn("[seckill-rabbitmq] skip queue backlog, queue={}", queueName, e);
            return OptionalLong.empty();
        }
    }
}
