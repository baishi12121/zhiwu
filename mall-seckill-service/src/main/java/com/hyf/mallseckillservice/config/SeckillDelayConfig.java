package com.hyf.mallseckillservice.config;

import com.hyf.mallseckillservice.constant.SeckillConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * 秒杀订单支付超时延迟队列配置。
 *
 * <p>订单创建成功后会投递延迟消息，到期后由消费者触发取消和库存回补。</p>
 */
@Configuration
public class SeckillDelayConfig {

    @Bean
    public CustomExchange seckillDelayExchange() {
        // 使用 RabbitMQ delayed-message 插件实现精确到消息级别的支付超时延迟。
        return new CustomExchange(
                SeckillConstants.SECKILL_DELAY_EXCHANGE,
                "x-delayed-message",
                true,
                false,
                Map.of("x-delayed-type", "direct"));
    }

    @Bean
    public Queue seckillDelayQueue() {
        return new Queue(SeckillConstants.SECKILL_DELAY_QUEUE, true);
    }

    @Bean
    public Binding seckillDelayBinding(CustomExchange seckillDelayExchange, Queue seckillDelayQueue) {
        // 延迟交换机到超时队列的固定路由绑定。
        return BindingBuilder.bind(seckillDelayQueue)
                .to(seckillDelayExchange)
                .with(SeckillConstants.SECKILL_DELAY_ROUTING)
                .noargs();
    }
}
