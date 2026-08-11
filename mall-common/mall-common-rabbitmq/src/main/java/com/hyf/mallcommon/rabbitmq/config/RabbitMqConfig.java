package com.hyf.mallcommon.rabbitmq.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * RabbitMQ 自动装配。
 *
 * <p>通过 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 注册（同 mall-common-redis / mall-common-security 模式），保证本 jar 的包名
 * {@code com.hyf.mallcommon.rabbitmq} 不在各业务服务 base package 下时仍能装配。
 *
 * <p>所有服务统一使用 {@link Jackson2JsonMessageConverter} 做 JSON 序列化，
 * 杜绝 Java 原生序列化（SimpleMessageConverter）导致的 SecurityException 和跨语言兼容问题。
 *
 * @author hyf
 */
@AutoConfiguration
public class RabbitMqConfig {

    @Bean
    public MessageConverter messageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(mapper);
    }
}
