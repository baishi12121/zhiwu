package com.hyf.mallseckillservice.mq;

import com.hyf.mallseckillservice.constant.SeckillConstants;
import com.hyf.mallseckillservice.dto.SeckillOrderMessageDTO;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 秒杀死信队列消费者。
 *
 * <p>死信消息不再自动补偿，保留日志告警和人工介入入口，避免错误数据被重复自动处理。</p>
 */
@Slf4j
@Component
public class SeckillDeadLetterConsumer {

    @RabbitListener(queues = SeckillConstants.SECKILL_ORDER_DLQ)
    public void handleOrderDeadLetter(SeckillOrderMessageDTO dto, Channel channel, Message message) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        log.error("[seckill-dlq] order message dead-lettered, messageId={}, userId={}, activityId={}, seckillItemId={}, xDeath={}",
                dto.getMessageId(), dto.getUserId(), dto.getActivityId(), dto.getSeckillItemId(),
                message.getMessageProperties().getXDeathHeader());
        channel.basicAck(deliveryTag, false);
    }

    @RabbitListener(queues = SeckillConstants.SECKILL_TIMEOUT_DLQ)
    public void handleTimeoutDeadLetter(String orderId, Channel channel, Message message) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        log.error("[seckill-dlq] timeout message dead-lettered, orderId={}, xDeath={}",
                orderId, message.getMessageProperties().getXDeathHeader());
        channel.basicAck(deliveryTag, false);
    }
}
