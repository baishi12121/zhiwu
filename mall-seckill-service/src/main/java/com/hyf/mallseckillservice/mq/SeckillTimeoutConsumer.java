package com.hyf.mallseckillservice.mq;

import com.hyf.mallseckillservice.constant.SeckillConstants;
import com.hyf.mallseckillservice.service.SeckillCompensateService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 秒杀订单支付超时消费者。
 *
 * <p>接收延迟队列中的订单 ID，触发待支付订单取消和库存回补。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillTimeoutConsumer {

    private final SeckillCompensateService seckillCompensateService;

    @RabbitListener(queues = SeckillConstants.SECKILL_DELAY_QUEUE)
    public void handle(String orderId, Channel channel, Message message) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            // 回补服务内部会校验订单来源和状态，重复超时消息不会重复回补库存。
            seckillCompensateService.cancelAndRestore(Long.parseLong(orderId));
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("[seckill-timeout] cancel failed, orderId={}", orderId, e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
