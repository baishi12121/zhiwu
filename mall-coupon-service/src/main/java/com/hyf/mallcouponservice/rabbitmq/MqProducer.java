package com.hyf.mallcouponservice.rabbitmq;

/**
 * MQ消息转发
 */
public interface MqProducer {
    /**
     * 定义生成者
     * @param topic
     * @param message
     */
    void send(String topic, Object message);
}
