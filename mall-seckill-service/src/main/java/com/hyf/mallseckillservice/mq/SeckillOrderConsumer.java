package com.hyf.mallseckillservice.mq;

import com.hyf.mallseckillservice.constant.SeckillConstants;
import com.hyf.mallseckillservice.dto.SeckillOrderMessageDTO;
import com.hyf.mallseckillservice.redis.SeckillStockRedis;
import com.hyf.mallseckillservice.service.MqMessageService;
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
    private final SeckillStockRedis seckillStockRedis;
    private final MqMessageService mqMessageService;

    @RabbitListener(queues = SeckillConstants.SECKILL_QUEUE)
    public void handle(SeckillOrderMessageDTO dto, Channel channel, Message message) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String key = SeckillConstants.orderKey(dto.getUserId(), dto.getActivityId(), dto.getSeckillItemId());
        try {
            // Redis SETNX 作为消费者幂等锁，防止同一消息重复投递时并发建多张订单。
            Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                    key,
                    String.valueOf(SeckillConstants.IDEMPOTENT_PROCESSING),
                    Duration.ofSeconds(SeckillConstants.SECKILL_ORDER_TTL_SEC));
            if (!Boolean.TRUE.equals(locked)) {
                String state = stringRedisTemplate.opsForValue().get(key);
                if (String.valueOf(SeckillConstants.IDEMPOTENT_SUCCESS).equals(state)) {
                    // 已成功处理过的重复消息直接 ACK。
                    channel.basicAck(deliveryTag, false);
                    return;
                }
                if (String.valueOf(SeckillConstants.IDEMPOTENT_FAILED).equals(state)) {
                    // 失败状态允许重新入队再尝试一次，先清理失败标记避免一直命中旧状态。
                    stringRedisTemplate.delete(key);
                    channel.basicNack(deliveryTag, false, true);
                    return;
                }
                channel.basicNack(deliveryTag, false, true);
                return;
            }
            seckillOrderService.createSeckillOrder(dto);
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
            // 消费失败时补齐 Redis 和本地消息表状态，然后 ACK 当前消息，重试交给本地表/人工对账。
            seckillStockRedis.restoreStock(dto.getActivityId(), dto.getSeckillItemId(), dto.getQuantity());
            mqMessageService.markFailed(dto.getMessageId());
            stringRedisTemplate.opsForValue().set(
                    key,
                    String.valueOf(SeckillConstants.IDEMPOTENT_FAILED),
                    Duration.ofSeconds(SeckillConstants.SECKILL_ORDER_TTL_SEC));
            log.error("[seckill-consumer] create order failed, messageId={}", dto.getMessageId(), e);
            channel.basicAck(deliveryTag, false);
        }
    }
}
