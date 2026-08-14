package com.hyf.mallseckillservice.mq;

import com.hyf.mallseckillservice.dto.SeckillOrderMessageDTO;
import com.hyf.mallseckillservice.service.MqMessageService;
import com.hyf.mallseckillservice.service.SeckillCompensateService;
import com.hyf.mallseckillservice.service.SeckillOrderService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.io.IOException;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeckillOrderConsumerTest {

    @Test
    void failureRetriesRestoresStockOnceAndNacksToDlq() throws IOException {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        SeckillOrderService orderService = mock(SeckillOrderService.class);
        SeckillCompensateService compensateService = mock(SeckillCompensateService.class);
        MqMessageService mqMessageService = mock(MqMessageService.class);
        SeckillConsumerRetryExecutor retryExecutor = new SeckillConsumerRetryExecutor(millis -> {
        });
        SeckillOrderConsumer consumer = new SeckillOrderConsumer(
                redisTemplate, orderService, compensateService, mqMessageService, retryExecutor);
        SeckillOrderMessageDTO dto = new SeckillOrderMessageDTO();
        dto.setMessageId("msg-1");
        dto.setUserId(10L);
        dto.setActivityId(20L);
        dto.setSeckillItemId(30L);
        dto.setQuantity(2);
        doThrow(new IllegalStateException("boom")).when(orderService).createSeckillOrder(dto);
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(99L);
        Channel channel = mock(Channel.class);

        consumer.handle(dto, channel, new Message(new byte[0], properties));

        verify(orderService, times(4)).createSeckillOrder(dto);
        verify(compensateService).restoreForCreateFailure("msg-1", 20L, 30L, 10L, 2);
        verify(mqMessageService).markFailed("msg-1");
        verify(channel).basicNack(99L, false, false);
    }
}
