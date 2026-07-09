package com.hyf.mallcouponservice.rabbitmq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RabbitMqProducer implements MqProducer{

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public void send(String topic, Object message) {
        try{
            rabbitTemplate.convertAndSend(topic,"",message);
            log.info("消息已发送至 Exchange [{}], 内容: {}", topic, message);
        }catch (Exception e){
            log.error("RabbitMQ 发送失败, topic: {}, message: {}", topic, message, e);
            throw new RuntimeException("MQ 发送失败", e);
        }
    }
}
