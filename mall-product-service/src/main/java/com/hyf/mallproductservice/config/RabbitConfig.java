package com.hyf.mallproductservice.config;

import com.hyf.mallproductservice.entity.ProductScoreMessage;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 配置类
 * 定义商品热度排行榜相关的交换机、队列和绑定关系
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE_PRODUCT_RANK = "exchange.product.rank";
    public static final String QUEUE_PRODUCT_CLICK = "product.click.queue";
    public static final String QUEUE_PRODUCT_ORDER = "product.order.queue";
    public static final String ROUTING_KEY_CLICK = "routing.product.click";
    public static final String ROUTING_KEY_ORDER = "routing.product.order";

    /**
     * 商品热度主题交换机
     */
    @Bean
    public TopicExchange productRankExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_PRODUCT_RANK)
                .durable(true)
                .build();
    }

    /**
     * 商品点击队列
     */
    @Bean
    public Queue productClickQueue() {
        return QueueBuilder.durable(QUEUE_PRODUCT_CLICK)
                .build();
    }

    /**
     * 商品下单队列
     */
    @Bean
    public Queue productOrderQueue() {
        return QueueBuilder.durable(QUEUE_PRODUCT_ORDER)
                .build();
    }

    /**
     * 点击队列绑定到交换机
     */
    @Bean
    public Binding clickBinding(Queue productClickQueue, TopicExchange productRankExchange) {
        return BindingBuilder.bind(productClickQueue)
                .to(productRankExchange)
                .with(ROUTING_KEY_CLICK);
    }

    /**
     * 下单队列绑定到交换机
     */
    @Bean
    public Binding orderBinding(Queue productOrderQueue, TopicExchange productRankExchange) {
        return BindingBuilder.bind(productOrderQueue)
                .to(productRankExchange)
                .with(ROUTING_KEY_ORDER);
    }

    /**
     * JSON 消息转换器，替代默认的 Java 序列化
     * 同时兼容本服务和订单服务的 ProductScoreMessage 类名
     */
    @Bean
    public MessageConverter messageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultClassMapper classMapper = new DefaultClassMapper();
        classMapper.setTrustedPackages(
                "com.hyf.mallproductservice.entity",
                "com.hyf.mallorderservice.entity"
        );
        Map<String, Class<?>> idClassMapping = new HashMap<>();
        idClassMapping.put(
                "com.hyf.mallorderservice.entity.ProductScoreMessage",
                ProductScoreMessage.class
        );
        idClassMapping.put(
                "com.hyf.mallproductservice.entity.ProductScoreMessage",
                ProductScoreMessage.class
        );
        classMapper.setIdClassMapping(idClassMapping);
        converter.setClassMapper(classMapper);
        return converter;
    }
}
