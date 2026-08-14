package com.hyf.mallseckillservice.service.impl;

import com.hyf.mallseckillservice.constant.SeckillConstants;
import com.hyf.mallseckillservice.mapper.MqMessageMapper;
import com.hyf.mallseckillservice.mq.RabbitMqManagementClient;
import com.hyf.mallseckillservice.service.SeckillConsumerScaleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillConsumerScaleServiceImpl implements SeckillConsumerScaleService {

    private final RabbitListenerEndpointRegistry endpointRegistry;
    private final RabbitMqManagementClient rabbitMqManagementClient;
    private final MqMessageMapper mqMessageMapper;

    @Value("${spring.rabbitmq.listener.simple.concurrency:5}")
    private int minConcurrency;
    @Value("${spring.rabbitmq.listener.simple.max-concurrency:10}")
    private int maxConcurrency;
    @Value("${seckill.consumer.scale.up-backlog:500}")
    private long scaleUpBacklog;
    @Value("${seckill.consumer.scale.down-backlog:50}")
    private long scaleDownBacklog;

    private final AtomicInteger currentConcurrency = new AtomicInteger(0);

    @Override
    public Map<String, Object> scaleTo(int concurrency) {
        SimpleMessageListenerContainer container = orderContainer();
        int target = Math.max(minConcurrency, Math.min(maxConcurrency, concurrency));
        int before = current();
        container.setConcurrentConsumers(target);
        currentConcurrency.set(target);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("before", before);
        result.put("after", target);
        result.put("max", maxConcurrency);
        log.info("[seckill-backlog] manual scale, before={}, after={}, max={}", before, target, maxConcurrency);
        return result;
    }

    @Override
    @Scheduled(fixedDelay = 30_000L)
    public Map<String, Object> inspectAndScale() {
        OptionalLong queueBacklog = rabbitMqManagementClient.orderQueueBacklog();
        int pendingDue = mqMessageMapper.countPendingSendDue();
        SimpleMessageListenerContainer container = orderContainer();
        int current = current();
        int target = current;
        String action = "keep";
        long backlog = queueBacklog.orElse(-1L);
        if (queueBacklog.isPresent() && backlog > scaleUpBacklog && current < maxConcurrency) {
            target = Math.min(maxConcurrency, current + 1);
            action = "scale-up";
        } else if (queueBacklog.isPresent() && backlog < scaleDownBacklog && current > minConcurrency) {
            target = Math.max(minConcurrency, current - 1);
            action = "scale-down";
        }
        if (target != current) {
            container.setConcurrentConsumers(target);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queueBacklog", backlog);
        result.put("pendingDue", pendingDue);
        result.put("before", current);
        result.put("after", target);
        result.put("action", action);
        log.info("[seckill-backlog] queueBacklog={}, pendingDue={}, concurrencyBefore={}, concurrencyAfter={}, action={}",
                backlog, pendingDue, current, target, action);
        return result;
    }

    private SimpleMessageListenerContainer orderContainer() {
        MessageListenerContainer container = endpointRegistry.getListenerContainer(SeckillConstants.SECKILL_ORDER_LISTENER_ID);
        if (container instanceof SimpleMessageListenerContainer simple) {
            return simple;
        }
        throw new IllegalStateException("seckill order listener container is not SimpleMessageListenerContainer");
    }

    private int current() {
        return currentConcurrency.updateAndGet(v -> v <= 0 ? minConcurrency : v);
    }
}
