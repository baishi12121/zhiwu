package com.hyf.mallseckillservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyf.mallseckillservice.constant.SeckillConstants;
import com.hyf.mallseckillservice.dto.SeckillOrderMessageDTO;
import com.hyf.mallseckillservice.entity.MqMessageDO;
import com.hyf.mallseckillservice.mapper.MqMessageMapper;
import com.hyf.mallseckillservice.redis.SeckillStockRedis;
import com.hyf.mallseckillservice.service.impl.MqMessageServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MqMessageServiceTest {

    @Test
    void createPendingWritesPendingSendStateWithRetryGraceWindow() {
        MqMessageMapper mapper = mock(MqMessageMapper.class);
        MqMessageService service = new MqMessageServiceImpl(
                mapper,
                mock(RabbitTemplate.class),
                mock(SeckillStockRedis.class),
                new ObjectMapper());
        LocalDateTime before = LocalDateTime.now();
        SeckillOrderMessageDTO dto = new SeckillOrderMessageDTO();
        dto.setMessageId("msg-1");
        dto.setUserId(10L);
        dto.setActivityId(20L);
        dto.setSeckillItemId(30L);
        dto.setSpuId(40L);
        dto.setSkuId(50L);
        dto.setQuantity(1);

        service.createPending(dto);

        ArgumentCaptor<MqMessageDO> captor = ArgumentCaptor.forClass(MqMessageDO.class);
        verify(mapper).insert(captor.capture());
        MqMessageDO message = captor.getValue();
        // 库存已在 Redis 预扣，落库即「待发送」，并预留 60s 宽限避免被 retryExpired 立即重投。
        assertThat(message.getStatus()).isEqualTo(SeckillConstants.MSG_PENDING_SEND);
        assertThat(message.getRetryCount()).isEqualTo(0);
        assertThat(message.getNextRetryTime()).isAfterOrEqualTo(before.plusSeconds(55));
        assertThat(message.getNextRetryTime()).isBeforeOrEqualTo(before.plus(Duration.ofSeconds(65)));
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
