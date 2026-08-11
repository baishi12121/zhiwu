package com.hyf.mallorderservice.mq;

import com.hyf.mallcommon.core.constant.MallConstants;
import com.hyf.mallorderservice.service.OrderApplicationService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 订单超时取消 — 延迟消息消费者。
 *
 * <p>监听 {@link MallConstants#MQ_ORDER_DELAY_QUEUE}，收到消息后调用
 * {@link OrderApplicationService#cancelOrderBySystem(Long)} 执行取消。
 *
 * <p>幂等性：消费者侧已做双重防护 ——
 * <ol>
 *   <li>消息重试/重复投递时，{@code cancelOrderBySystem} 内部会检查订单状态，非待付款直接跳过；</li>
 *   <li>本消费者捕获异常后 {@code basicNack(requeue=false)}，失败消息进入死信或被丢弃，避免无限重投。</li>
 * </ol>
 *
 * <p>手动 ACK：成功后 {@code basicAck}，异常后 {@code basicNack(requeue=false)}。
 *
 * @author hyf
 */
@Component
public class OrderTimeoutConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutConsumer.class);

    private final OrderApplicationService orderApplicationService;

    public OrderTimeoutConsumer(OrderApplicationService orderApplicationService) {
        this.orderApplicationService = orderApplicationService;
    }

    /**
     * 处理超时订单消息。
     *
     * @param orderId 订单 ID（字符串形式）
     * @param channel RabbitMQ Channel，用于手动 ACK
     * @param message 原始消息，用于获取 deliveryTag
     */
    @RabbitListener(queues = MallConstants.MQ_ORDER_DELAY_QUEUE)
    public void handleTimeoutOrder(String orderId, Channel channel, Message message) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            log.info("收到订单超时取消消息: orderId={}", orderId);
            orderApplicationService.cancelOrderBySystem(Long.parseLong(orderId));
            // 处理成功，手动确认
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // 处理失败，不重回队列（避免毒消息无限重投），由日志追踪
            log.error("处理超时订单[{}]失败", orderId, e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
