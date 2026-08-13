package com.hyf.mallseckillservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyf.mallseckillservice.constant.SeckillConstants;
import com.hyf.mallseckillservice.mapper.MqMessageMapper;
import com.hyf.mallseckillservice.redis.SeckillStockRedis;
import com.hyf.mallseckillservice.service.impl.MqMessageServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MqMessageServiceTest {

    @Test
    void markSendingSetsRetryWindowSoFreshMessagesAreNotImmediatelyRetried() {
        MqMessageMapper mapper = mock(MqMessageMapper.class);
        MqMessageService service = new MqMessageServiceImpl(
                mapper,
                mock(RabbitTemplate.class),
                mock(SeckillStockRedis.class),
                new ObjectMapper());
        LocalDateTime before = LocalDateTime.now();

        service.markSending("msg-1");

        verify(mapper).updateStatusByMessageId("msg-1", SeckillConstants.MSG_PENDING_SEND);
        ArgumentCaptor<LocalDateTime> nextRetryCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).markRetry(eq("msg-1"), eq(0), nextRetryCaptor.capture());
        LocalDateTime nextRetryTime = nextRetryCaptor.getValue();
        assertThat(nextRetryTime).isAfterOrEqualTo(before.plusSeconds(55));
        assertThat(nextRetryTime).isBeforeOrEqualTo(before.plus(Duration.ofSeconds(65)));
    }

    @Test
    void resetFailedToSendingReopensOnlyFailedMessagesForRetry() {
        MqMessageMapper mapper = mock(MqMessageMapper.class);
        MqMessageService service = new MqMessageServiceImpl(
                mapper,
                mock(RabbitTemplate.class),
                mock(SeckillStockRedis.class),
                new ObjectMapper());

        service.resetFailedToSending("msg-1");

        verify(mapper).resetFailedToSending("msg-1");
    }
}
