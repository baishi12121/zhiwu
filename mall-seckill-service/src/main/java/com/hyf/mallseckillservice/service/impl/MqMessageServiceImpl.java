package com.hyf.mallseckillservice.service.impl;


import com.hyf.mallseckillservice.service.MqMessageService;
import com.hyf.mallseckillservice.service.SeckillApplicationService;
import com.hyf.mallseckillservice.service.SeckillCompensateService;
import com.hyf.mallseckillservice.service.SeckillOrderService;
import com.hyf.mallseckillservice.service.SeckillTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyf.mallseckillservice.constant.SeckillConstants;
import com.hyf.mallseckillservice.dto.SeckillItemMetaDTO;
import com.hyf.mallseckillservice.dto.SeckillOrderMessageDTO;
import com.hyf.mallseckillservice.entity.MqMessageDO;
import com.hyf.mallseckillservice.mapper.MqMessageMapper;
import com.hyf.mallseckillservice.redis.SeckillStockRedis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地消息表与 RabbitMQ 投递服务。
 *
 * <p>负责维护 mq_message 状态、发送订单创建消息，以及定时重投尚未确认发送成功的消息。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqMessageServiceImpl implements MqMessageService {

    private static final int MAX_RETRY_COUNT = 3;

    private final MqMessageMapper mqMessageMapper;
    private final RabbitTemplate rabbitTemplate;
    private final SeckillStockRedis seckillStockRedis;
    private final ObjectMapper objectMapper;

    public void createPending(SeckillOrderMessageDTO dto) {
        // 库存已在 Redis 预扣，这里直接落「待发送」态并预留发送宽限，跳过原「待扣库存」中间态，
        // 使刚落库的消息不会被 retryExpired 立即扫到重投。messageId 唯一键兜底用户维度幂等。
        MqMessageDO message = new MqMessageDO();
        message.setMessageId(dto.getMessageId());
        message.setUserId(dto.getUserId());
        message.setActivityId(dto.getActivityId());
        message.setSeckillItemId(dto.getSeckillItemId());
        message.setSpuId(dto.getSpuId());
        message.setSkuId(dto.getSkuId());
        message.setQuantity(dto.getQuantity());
        message.setStatus(SeckillConstants.MSG_PENDING_SEND);
        message.setRetryCount(0);
        message.setNextRetryTime(LocalDateTime.now().plusSeconds(SeckillConstants.MSG_SEND_GRACE_SECONDS));
        mqMessageMapper.insert(message);
    }

    public void resetFailedToSending(String messageId) {
        // 只有失败状态可以被重新打开；单条 UPDATE 同时重置重试次数与发送宽限（见 MqMessageMapper.xml）。
        mqMessageMapper.resetFailedToSending(messageId);
    }

    public void markSent(String messageId) {
        mqMessageMapper.updateStatusByMessageId(messageId, SeckillConstants.MSG_SENT);
    }

    public void markFailed(String messageId) {
        mqMessageMapper.updateStatusByMessageId(messageId, SeckillConstants.MSG_SEND_FAILED);
    }

    public void markDone(String messageId) {
        mqMessageMapper.updateStatusByMessageId(messageId, SeckillConstants.MSG_DONE);
    }

    public MqMessageDO findByMessageId(String messageId) {
        return mqMessageMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<MqMessageDO>lambdaQuery()
                        .eq(MqMessageDO::getMessageId, messageId));
    }

    public void sendOrderMessage(SeckillOrderMessageDTO dto) {
        // 持久化消息并携带 CorrelationData，broker confirm 回调用 messageId 更新本地消息表。
        rabbitTemplate.convertAndSend(
                SeckillConstants.SECKILL_EXCHANGE,
                SeckillConstants.SECKILL_ROUTING,
                dto,
                message -> {
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    message.getMessageProperties().setMessageId(dto.getMessageId());
                    return message;
                },
                new CorrelationData(dto.getMessageId()));
        log.info("[seckill-mq] sent order message, messageId={}", dto.getMessageId());
    }

    public void retryExpired(int batchSize) {
        List<MqMessageDO> messages = mqMessageMapper.selectPendingSendForRetry(batchSize);
        for (MqMessageDO message : messages) {
            if (message.getRetryCount() != null && message.getRetryCount() >= MAX_RETRY_COUNT) {
                // 超过重试次数后标失败，让 result 接口和后续对账能看到明确终态。
                markFailed(message.getMessageId());
                log.error("[seckill-mq] retry exceeded, messageId={}", message.getMessageId());
                continue;
            }
            int nextRetry = message.getRetryCount() == null ? 1 : message.getRetryCount() + 1;
            // 简单指数退避，避免 MQ 故障时固定频率重投造成尖峰。
            int delaySeconds = Math.min(30 * 60, 1 << nextRetry);
            mqMessageMapper.markRetry(message.getMessageId(), nextRetry, LocalDateTime.now().plusSeconds(delaySeconds));
            SeckillOrderMessageDTO dto = toMessageDTO(message);
            sendOrderMessage(dto);
        }
    }

    private SeckillOrderMessageDTO toMessageDTO(MqMessageDO message) {
        SeckillOrderMessageDTO dto = new SeckillOrderMessageDTO();
        dto.setMessageId(message.getMessageId());
        dto.setUserId(message.getUserId());
        dto.setActivityId(message.getActivityId());
        dto.setSeckillItemId(message.getSeckillItemId());
        dto.setSpuId(message.getSpuId());
        dto.setSkuId(message.getSkuId());
        dto.setQuantity(message.getQuantity());
        dto.setCreateTime(LocalDateTime.now());
        fillItemSnapshot(dto);
        return dto;
    }

    private void fillItemSnapshot(SeckillOrderMessageDTO dto) {
        // 重投消息可能只来自 mq_message，价格快照尽量从 Redis 元数据补齐。
        String json = seckillStockRedis.getItemMeta(dto.getSeckillItemId());
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            SeckillItemMetaDTO meta = objectMapper.readValue(json, SeckillItemMetaDTO.class);
            dto.setSeckillPrice(meta.getSeckillPrice());
            dto.setPrice(meta.getPrice());
        } catch (Exception e) {
            log.warn("[seckill-mq] failed to fill retry snapshot, messageId={}", dto.getMessageId(), e);
        }
    }
}
