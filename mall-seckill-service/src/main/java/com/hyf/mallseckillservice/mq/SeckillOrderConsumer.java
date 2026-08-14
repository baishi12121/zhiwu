package com.hyf.mallseckillservice.mq;

import com.hyf.mallseckillservice.constant.SeckillConstants;
import com.hyf.mallseckillservice.dto.SeckillOrderMessageDTO;
import com.hyf.mallseckillservice.service.MqMessageService;
import com.hyf.mallseckillservice.service.SeckillCompensateService;
import com.hyf.mallseckillservice.service.SeckillOrderService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

/**
 * 秒杀下单消息消费者。
 *
 * <p>消费 Redis 预占后的订单消息，执行数据库建单、DB 库存扣减、本地消息完成和消费者幂等控制。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderConsumer {

    private final StringRedisTemplate stringRedisTemplate;
    private final SeckillOrderService seckillOrderService;
    private final SeckillCompensateService seckillCompensateService;
    private final MqMessageService mqMessageService;
    private final SeckillConsumerRetryExecutor retryExecutor;

    @RabbitListener(id = SeckillConstants.SECKILL_ORDER_LISTENER_ID, queues = SeckillConstants.SECKILL_QUEUE)
    public void handle(SeckillOrderMessageDTO dto, Channel channel, Message message) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String key = SeckillConstants.orderKey(dto.getUserId(), dto.getActivityId(), dto.getSeckillItemId());
        try {
            boolean redelivered = Boolean.TRUE.equals(message.getMessageProperties().isRedelivered());
            if (!acquireProcessing(dto, key, channel, deliveryTag, redelivered)) {
                return;
            }
            retryExecutor.execute(dto.getMessageId(), () -> seckillOrderService.createSeckillOrder(dto));
            stringRedisTemplate.opsForValue().set(
                    key,
                    String.valueOf(SeckillConstants.IDEMPOTENT_SUCCESS),
                    Duration.ofSeconds(SeckillConstants.SECKILL_ORDER_TTL_SEC));
            channel.basicAck(deliveryTag, false);
        } catch (DuplicateKeyException e) {
            // DB 唯一键兜底说明订单已经存在，消费者视为成功并 ACK。
            stringRedisTemplate.opsForValue().set(
                    key,
                    String.valueOf(SeckillConstants.IDEMPOTENT_SUCCESS),
                    Duration.ofSeconds(SeckillConstants.SECKILL_ORDER_TTL_SEC));
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // 重试全部失败后才做一次最终回补和失败标记，然后 NACK 到 DLQ，避免失败即 ACK 丢消息。
            seckillCompensateService.restoreForCreateFailure(dto.getMessageId(), dto.getActivityId(),
                    dto.getSeckillItemId(), dto.getUserId(), dto.getQuantity());
            mqMessageService.markFailed(dto.getMessageId());
            stringRedisTemplate.opsForValue().set(
                    key,
                    String.valueOf(SeckillConstants.IDEMPOTENT_FAILED),
                    Duration.ofSeconds(SeckillConstants.SECKILL_ORDER_TTL_SEC));
            log.error("[seckill-consumer] create order failed after retry, messageId={}", dto.getMessageId(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private boolean acquireProcessing(SeckillOrderMessageDTO dto, String key, Channel channel,
                                      long deliveryTag, boolean redelivered) throws IOException {
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                key,
                String.valueOf(SeckillConstants.IDEMPOTENT_PROCESSING),
                Duration.ofSeconds(SeckillConstants.SECKILL_ORDER_TTL_SEC));
        if (Boolean.TRUE.equals(locked)) {
            return true;
        }
        String state = stringRedisTemplate.opsForValue().get(key);
        if (String.valueOf(SeckillConstants.IDEMPOTENT_SUCCESS).equals(state)) {
            // 已成功处理过的重复消息直接 ACK。
            channel.basicAck(deliveryTag, false);
            return false;
        }
        if (String.valueOf(SeckillConstants.IDEMPOTENT_FAILED).equals(state) || redelivered) {
            stringRedisTemplate.delete(key);
            Boolean relocked = stringRedisTemplate.opsForValue().setIfAbsent(
                    key,
                    String.valueOf(SeckillConstants.IDEMPOTENT_PROCESSING),
                    Duration.ofSeconds(SeckillConstants.SECKILL_ORDER_TTL_SEC));
            if (Boolean.TRUE.equals(relocked)) {
                return true;
            }
        }
        log.info("[seckill-consumer] skip duplicate processing message, messageId={}, state={}",
                dto.getMessageId(), state);
        channel.basicAck(deliveryTag, false);
        return false;
    }
}
