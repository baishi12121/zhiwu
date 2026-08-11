package com.hyf.mallorderservice.mq;

import com.hyf.mallcommon.core.constant.MallConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 订单超时取消 — 延迟消息插件配置。
 *
 * <p>依赖 RabbitMQ {@code rabbitmq_delayed_message_exchange} 插件，交换机类型为
 * {@code x-delayed-message}，底层路由模式为 {@code direct}。
 *
 * <p>流程：下单时发送带 {@code x-delay} 头的消息 → 30 分钟后投递到队列 →
 * {@link OrderTimeoutConsumer} 消费并执行取消。
 *
 * <p>交换机/队列/绑定均 durable=true，服务重启后声明仍可恢复；消息本身由
 * {@link org.springframework.amqp.core.MessageProperties#setMessageId} 持久化。
 *
 * @author hyf
 */
@Configuration
public class RabbitMQDelayedConfig {

    /**
     * 延迟交换机 — 自定义类型 {@code x-delayed-message}，参数 {@code x-delayed-type} 指定底层路由模式。
     */
    @Bean
    public CustomExchange orderDelayExchange() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-delayed-type", "direct");
        return new CustomExchange(
                MallConstants.MQ_ORDER_DELAY_EXCHANGE,
                "x-delayed-message",
                true,   // durable
                false); // autoDelete
    }

    /**
     * 延迟队列 — durable，存放待取消的订单 ID。
     */
    @Bean
    public Queue orderDelayQueue() {
        return new Queue(MallConstants.MQ_ORDER_DELAY_QUEUE, true);
    }

    /**
     * 绑定队列到延迟交换机，路由键固定。
     */
    @Bean
    public Binding orderDelayBinding(CustomExchange orderDelayExchange, Queue orderDelayQueue) {
        return BindingBuilder.bind(orderDelayQueue)
                .to(orderDelayExchange)
                .with(MallConstants.MQ_ORDER_DELAY_ROUTING_KEY)
                .noargs();
    }
}
