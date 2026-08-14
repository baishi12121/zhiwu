package com.hyf.mallseckillservice.config;

import com.hyf.mallseckillservice.constant.SeckillConstants;
import com.hyf.mallseckillservice.service.MqMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 秒杀下单 MQ 配置。
 *
 * <p>execute 接口只做 Redis 预占和本地消息落库，真正建单由该队列异步消费完成。</p>
 */
@Slf4j
@Configuration
public class SeckillMqConfig {

    @Bean
    public DirectExchange seckillExchange() {
        return new DirectExchange(SeckillConstants.SECKILL_EXCHANGE, true, false);
    }

    @Bean
    public Queue seckillOrderQueue() {
        return QueueBuilder.durable(SeckillConstants.SECKILL_QUEUE)
                .deadLetterExchange(SeckillConstants.SECKILL_DLX_EXCHANGE)
                .deadLetterRoutingKey(SeckillConstants.SECKILL_ORDER_DLQ_ROUTING)
                .build();
    }

    @Bean
    public Binding seckillOrderBinding(DirectExchange seckillExchange, Queue seckillOrderQueue) {
        return BindingBuilder.bind(seckillOrderQueue)
                .to(seckillExchange)
                .with(SeckillConstants.SECKILL_ROUTING);
    }

    @Bean
    public DirectExchange seckillDlxExchange() {
        return new DirectExchange(SeckillConstants.SECKILL_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue seckillOrderDlq() {
        return QueueBuilder.durable(SeckillConstants.SECKILL_ORDER_DLQ).build();
    }

    @Bean
    public Binding seckillOrderDlqBinding(DirectExchange seckillDlxExchange, Queue seckillOrderDlq) {
        return BindingBuilder.bind(seckillOrderDlq)
                .to(seckillDlxExchange)
                .with(SeckillConstants.SECKILL_ORDER_DLQ_ROUTING);
    }

    @Bean
    public Queue seckillTimeoutDlq() {
        return QueueBuilder.durable(SeckillConstants.SECKILL_TIMEOUT_DLQ).build();
    }

    @Bean
    public Binding seckillTimeoutDlqBinding(DirectExchange seckillDlxExchange, Queue seckillTimeoutDlq) {
        return BindingBuilder.bind(seckillTimeoutDlq)
                .to(seckillDlxExchange)
                .with(SeckillConstants.SECKILL_TIMEOUT_DLQ_ROUTING);
    }

    @Bean
    public RabbitTemplate.ConfirmCallback seckillConfirmCallback(MqMessageService mqMessageService) {
        return (CorrelationData correlationData, boolean ack, String cause) -> {
            String messageId = correlationData == null ? null : correlationData.getId();
            if (ack) {
                if (messageId != null) {
                    // 状态更新由 mapper 保证单调前进，避免 broker ACK 晚于消费完成时把 DONE 覆盖回 SENT。
                    mqMessageService.markSent(messageId);
                }
                log.info("[seckill-mq] broker ack, messageId={}", messageId);
            } else {
                log.warn("[seckill-mq] broker nack, messageId={}, cause={}", messageId, cause);
            }
        };
    }

    @Bean
    public RabbitTemplate.ReturnsCallback seckillReturnsCallback() {
        return returned -> log.warn("[seckill-mq] message returned, exchange={}, routingKey={}, replyCode={}, replyText={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyCode(), returned.getReplyText());
    }

    @Bean
    public RabbitTemplateConfigurer seckillRabbitTemplateConfigurer(RabbitTemplate rabbitTemplate,
                                                                    RabbitTemplate.ConfirmCallback seckillConfirmCallback,
                                                                    RabbitTemplate.ReturnsCallback seckillReturnsCallback) {
        // mandatory=true 让不可路由消息进入 ReturnsCallback，避免静默丢失。
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setConfirmCallback(seckillConfirmCallback);
        rabbitTemplate.setReturnsCallback(seckillReturnsCallback);
        return new RabbitTemplateConfigurer();
    }

    /**
     * RabbitTemplate 回调配置占位 Bean。
     *
     * <p>创建该 Bean 时完成 RabbitTemplate 的 mandatory、confirm 和 return 回调绑定。</p>
     */
    public static class RabbitTemplateConfigurer {
    }
}
