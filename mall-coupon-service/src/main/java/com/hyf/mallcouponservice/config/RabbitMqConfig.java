package com.hyf.mallcouponservice.config;


import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {
    @Bean
    public MessageConverter messageConverter() {
        // 使用 Jackson 自动将 Java 对象序列化为 JSON 字符串发送
        return new Jackson2JsonMessageConverter();
    }
    // 动态创建秒杀专用的 Topic Exchange
    @Bean
    public TopicExchange seckillExchange() {
        return new TopicExchange("COUPON_SECKILL_TOPIC");
    }
    // 2. 声明队列
    @Bean
    public Queue seckillQueue() {
        return new Queue("coupon.seckill.queue", true); // 队列持久化
    }

    // 3. 将队列通过 Routing Key 绑定到交换机
    @Bean
    public Binding bindingSeckill() {
        return BindingBuilder.bind(seckillQueue())
                .to(seckillExchange())
                .with("coupon.seckill.routing.key"); // 路由键
    }
}
