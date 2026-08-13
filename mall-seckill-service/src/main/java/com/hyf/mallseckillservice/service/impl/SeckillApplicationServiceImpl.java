package com.hyf.mallseckillservice.service.impl;


import com.hyf.mallseckillservice.service.MqMessageService;
import com.hyf.mallseckillservice.service.SeckillApplicationService;
import com.hyf.mallseckillservice.service.SeckillCompensateService;
import com.hyf.mallseckillservice.service.SeckillOrderService;
import com.hyf.mallseckillservice.service.SeckillTask;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyf.mallcommon.core.exception.BizException;
import com.hyf.mallcommon.core.result.ResultCode;
import com.hyf.mallseckillservice.constant.SeckillConstants;
import com.hyf.mallseckillservice.dto.*;
import com.hyf.mallseckillservice.entity.*;
import com.hyf.mallseckillservice.mapper.*;
import com.hyf.mallseckillservice.redis.SeckillStockRedis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀入口应用服务。
 *
 * <p>串联活动校验、商品元数据读取、本地消息落库、Redis 预扣库存和 MQ 投递，是用户抢购请求的主链路。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillApplicationServiceImpl implements SeckillApplicationService {

    private static final int STOCK_NOT_ENOUGH_CODE = 4001;
    private static final int LIMIT_HIT_CODE = 4002;
    private static final int ACTIVITY_CLOSED_CODE = 4003;
    private static final long MIN_LIMIT_TTL_SECONDS = 30 * 60L;

    private final SeckillActivityMapper seckillActivityMapper;
    private final SeckillItemMapper seckillItemMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ProductMapper productMapper;
    private final MqMessageService mqMessageService;
    private final OrderMapper orderMapper;
    private final SeckillStockRedis seckillStockRedis;
    private final ObjectMapper objectMapper;

    public void warmUp() {
        // 启动时做全量预热：库存 key 使用 SETNX，元数据 key 带活动剩余 TTL。
        List<SeckillActivityDO> activities = seckillActivityMapper.selectActiveActivities();
        for (SeckillActivityDO activity : activities) {
            long ttl = ttlSeconds(activity);
            List<SeckillItemDO> items = seckillItemMapper.selectEnabledByActivityId(activity.getId());
            for (SeckillItemDO item : items) {
                seckillStockRedis.warmUpStock(activity.getId(), item.getId(), item.getSeckillStock());
                seckillStockRedis.cacheItemMeta(item.getId(), toMetaJson(activity.getId(), item), ttl);
                log.info("[seckill-warmup] activityId={}, seckillItemId={}, stock={}",
                        activity.getId(), item.getId(), item.getSeckillStock());
            }
        }
    }

    public void refreshActiveItemMeta() {
        // 活动进行中的定时刷新只更新商品元数据，不碰库存，避免复活已被 Redis 扣减的库存。
        List<SeckillActivityDO> activities = seckillActivityMapper.selectActiveActivities();
        for (SeckillActivityDO activity : activities) {
            long ttl = ttlSeconds(activity);
            List<SeckillItemDO> items = seckillItemMapper.selectEnabledByActivityId(activity.getId());
            for (SeckillItemDO item : items) {
                seckillStockRedis.cacheItemMeta(item.getId(), toMetaJson(activity.getId(), item), ttl);
                log.info("[seckill-warmup] refresh item meta, activityId={}, seckillItemId={}",
                        activity.getId(), item.getId());
            }
        }
    }

    public ExecuteResultDTO execute(Long userId, Long activityId, ExecuteReqDTO req) {
        SeckillActivityDO activity = requireOpenActivity(activityId);
        SeckillItemMetaDTO meta = readItemMeta(activityId, req.getSeckillItemId());
        int quantity = req.getQuantity() == null ? 1 : req.getQuantity();
        // 用户+活动+秒杀商品维度天然幂等，对应 mq_message 唯一键。
        String messageId = buildMessageId(userId, activityId, req.getSeckillItemId());

        SeckillOrderMessageDTO message = new SeckillOrderMessageDTO();
        message.setMessageId(messageId);
        message.setUserId(userId);
        message.setActivityId(activityId);
        message.setSeckillItemId(req.getSeckillItemId());
        message.setSpuId(meta.getSpuId());
        message.setSkuId(meta.getSkuId());
        message.setSeckillPrice(meta.getSeckillPrice());
        message.setPrice(meta.getPrice());
        message.setQuantity(quantity);
        message.setAddressId(req.getAddressId());
        message.setCreateTime(LocalDateTime.now());

        try {
            // 先落本地消息，再扣 Redis；进程崩溃时至少能通过本地消息表判断请求走到哪一步。
            mqMessageService.createPending(message);
        } catch (DuplicateKeyException e) {
            MqMessageDO existing = mqMessageService.findByMessageId(messageId);
            if (existing != null && existing.getStatus() != null
                    && existing.getStatus() == SeckillConstants.MSG_DONE) {
                throw new BizException(LIMIT_HIT_CODE, "您已购买过该商品");
            }
            if (existing != null && existing.getStatus() != null
                    && existing.getStatus() == SeckillConstants.MSG_SEND_FAILED) {
                // 失败记录再次进入时必须重新做 Redis 预占，防止绕过库存保护直接重投 MQ。
                retryFailedMessage(activity, req, userId, quantity, messageId, message, meta);
            }
            return new ExecuteResultDTO("queued", messageId);
        }

        long result = seckillStockRedis.tryDeduct(
                activityId,
                req.getSeckillItemId(),
                userId,
                quantity,
                meta.getLimitPerUser(),
                ttlSeconds(activity));

        if (result == SeckillConstants.REDIS_OK) {
            try {
                mqMessageService.markSending(messageId);
                mqMessageService.sendOrderMessage(message);
            } catch (Exception e) {
                // Redis 已预扣但消息未可靠进入 MQ 时，立即回补并把本地消息标失败，交给用户重试。
                seckillStockRedis.restoreStock(activityId, req.getSeckillItemId(), quantity);
                mqMessageService.markFailed(messageId);
                throw e;
            }
            return new ExecuteResultDTO("queued", messageId);
        }
        mqMessageService.markFailed(messageId);
        if (result == SeckillConstants.REDIS_STOCK_NOT_ENOUGH) {
            throw new BizException(STOCK_NOT_ENOUGH_CODE, "库存不足");
        }
        if (result == SeckillConstants.REDIS_LIMIT_HIT) {
            throw new BizException(LIMIT_HIT_CODE, "您已购买过该商品");
        }
        throw new BizException(ResultCode.BAD_REQUEST.getCode(), "参数错误");
    }

    private void retryFailedMessage(SeckillActivityDO activity, ExecuteReqDTO req, Long userId, int quantity,
                                    String messageId, SeckillOrderMessageDTO message, SeckillItemMetaDTO meta) {
        long result = seckillStockRedis.tryDeduct(
                activity.getId(),
                req.getSeckillItemId(),
                userId,
                quantity,
                meta.getLimitPerUser(),
                ttlSeconds(activity));
        if (result == SeckillConstants.REDIS_OK) {
            try {
                mqMessageService.resetFailedToSending(messageId);
                mqMessageService.sendOrderMessage(message);
            } catch (Exception e) {
                // 重试路径同样要守住 Redis/MQ 双写边界，投递失败必须归还刚预占的库存。
                seckillStockRedis.restoreStock(activity.getId(), req.getSeckillItemId(), quantity);
                mqMessageService.markFailed(messageId);
                throw e;
            }
            return;
        }
        mqMessageService.markFailed(messageId);
        if (result == SeckillConstants.REDIS_STOCK_NOT_ENOUGH) {
            throw new BizException(STOCK_NOT_ENOUGH_CODE, "库存不足");
        }
        if (result == SeckillConstants.REDIS_LIMIT_HIT) {
            throw new BizException(LIMIT_HIT_CODE, "您已购买过该商品");
        }
        throw new BizException(ResultCode.BAD_REQUEST.getCode(), "参数错误");
    }

    public SeckillResultDTO result(Long userId, Long activityId, Long seckillItemId) {
        String messageId = buildMessageId(userId, activityId, seckillItemId);
        MqMessageDO message = mqMessageService.findByMessageId(messageId);
        if (message == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "未参与秒杀");
        }
        SeckillResultDTO dto = new SeckillResultDTO();
        if (message.getStatus() == SeckillConstants.MSG_DONE) {
            OrderDO order = orderMapper.selectByUserActivityItem(userId, activityId, seckillItemId);
            dto.setStatus("ordered");
            if (order != null) {
                dto.setOrderId(order.getId());
                dto.setOrderNo(order.getOrderNo());
            }
            return dto;
        }
        if (message.getStatus() == SeckillConstants.MSG_SEND_FAILED) {
            dto.setStatus("failed");
            return dto;
        }
        dto.setStatus("pending");
        return dto;
    }

    public String buildMessageId(Long userId, Long activityId, Long seckillItemId) {
        return userId + ":" + activityId + ":" + seckillItemId;
    }

    SeckillItemMetaDTO readItemMeta(Long activityId, Long seckillItemId) {
        // Redis 元数据缺失或损坏时回源 DB，避免缓存问题直接阻断活动。
        String json = seckillStockRedis.getItemMeta(seckillItemId);
        if (json != null && !json.isBlank()) {
            try {
                return objectMapper.readValue(json, SeckillItemMetaDTO.class);
            } catch (JsonProcessingException e) {
                log.warn("[seckill] item meta json invalid, seckillItemId={}", seckillItemId, e);
            }
        }
        SeckillItemDO item = seckillItemMapper.selectById(seckillItemId);
        if (item == null || !activityId.equals(item.getActivityId()) || item.getStatus() == null || item.getStatus() != 1) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "秒杀商品不存在");
        }
        return toMeta(activityId, item);
    }

    private SeckillActivityDO requireOpenActivity(Long activityId) {
        SeckillActivityDO activity = seckillActivityMapper.selectById(activityId);
        LocalDateTime now = LocalDateTime.now();
        if (activity == null || activity.getEnabled() == null || activity.getEnabled() != 1
                || now.isBefore(activity.getStartTime()) || now.isAfter(activity.getEndTime())) {
            throw new BizException(ACTIVITY_CLOSED_CODE, "活动未开始或已结束");
        }
        return activity;
    }

    private String toMetaJson(Long activityId, SeckillItemDO item) {
        try {
            return objectMapper.writeValueAsString(toMeta(activityId, item));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("秒杀商品元数据序列化失败", e);
        }
    }

    private SeckillItemMetaDTO toMeta(Long activityId, SeckillItemDO item) {
        ProductSkuDO sku = productSkuMapper.selectById(item.getSkuId());
        SeckillItemMetaDTO meta = new SeckillItemMetaDTO();
        meta.setActivityId(activityId);
        meta.setSeckillItemId(item.getId());
        meta.setSpuId(item.getSpuId());
        meta.setSkuId(item.getSkuId());
        meta.setSeckillPrice(item.getSeckillPrice());
        meta.setPrice(sku != null && sku.getPrice() != null ? sku.getPrice() : item.getSeckillPrice());
        meta.setLimitPerUser(item.getLimitPerUser() == null ? 1 : item.getLimitPerUser());
        return meta;
    }

    private long ttlSeconds(SeckillActivityDO activity) {
        long seconds = Duration.between(LocalDateTime.now(), activity.getEndTime()).getSeconds();
        return Math.max(MIN_LIMIT_TTL_SECONDS, seconds);
    }
}
