package com.hyf.mallseckillservice.service.impl;


import com.hyf.mallseckillservice.service.MqMessageService;
import com.hyf.mallseckillservice.service.SeckillApplicationService;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * 秒杀入口应用服务。
 *
 * <p>串联活动校验、商品元数据读取、Redis 原子预扣库存和 MQ 投递，是用户抢购请求的主链路。
 * 性能关键路径上不触碰 MySQL：活动与商品元数据走 Redis 缓存，被拒请求在 Redis 预扣阶段即返回，
 * 只有扣减成功的请求才落本地消息表并投递 MQ。</p>
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
        // 启动时做全量预热：活动结束时间、库存 key（SETNX）、元数据 key 一起写入，入口据此免查库。
        List<SeckillActivityDO> activities = seckillActivityMapper.selectActiveActivities();
        for (SeckillActivityDO activity : activities) {
            long endMillis = toEpochMilli(activity.getEndTime());
            long ttl = ttlSeconds(endMillis);
            seckillStockRedis.cacheActivity(activity.getId(), endMillis, ttl);
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
        // 活动进行中的定时刷新只更新商品元数据与活动结束时间，不碰库存，避免复活已被 Redis 扣减的库存。
        List<SeckillActivityDO> activities = seckillActivityMapper.selectActiveActivities();
        for (SeckillActivityDO activity : activities) {
            long endMillis = toEpochMilli(activity.getEndTime());
            long ttl = ttlSeconds(endMillis);
            seckillStockRedis.cacheActivity(activity.getId(), endMillis, ttl);
            List<SeckillItemDO> items = seckillItemMapper.selectEnabledByActivityId(activity.getId());
            for (SeckillItemDO item : items) {
                seckillStockRedis.cacheItemMeta(item.getId(), toMetaJson(activity.getId(), item), ttl);
                log.info("[seckill-warmup] refresh item meta, activityId={}, seckillItemId={}",
                        activity.getId(), item.getId());
            }
        }
    }

    public ExecuteResultDTO execute(Long userId, Long activityId, ExecuteReqDTO req) {
        // 活动校验与商品元数据均走 Redis 缓存，冷启动才回源 DB。
        long endTimeMillis = requireOpenActivity(activityId);
        SeckillItemMetaDTO meta = readItemMeta(activityId, req.getSeckillItemId());
        int quantity = req.getQuantity() == null ? 1 : req.getQuantity();
        String messageId = buildMessageId(userId, activityId, req.getSeckillItemId());

        // 先做 Redis 原子预扣（库存 + 限购 + 在途标记），只有扣减成功的请求才落库 / 投 MQ。
        // 被拒请求（库存不足 / 限购）在此直接抛出，全程不写 MySQL——这是压测 500 并发下 QPS 的关键。
        long result = seckillStockRedis.tryDeduct(
                activityId, req.getSeckillItemId(), userId, quantity,
                meta.getLimitPerUser(), ttlSeconds(endTimeMillis), messageId);

        if (result != SeckillConstants.REDIS_OK) {
            throw toStockException(result);
        }

        SeckillOrderMessageDTO message = buildMessage(userId, activityId, req, meta, messageId);
        try {
            // 先落本地消息（唯一键兜底幂等），落库成功即清除在途标记，后续可靠性交给 mq_message 状态机。
            mqMessageService.createPending(message);
            seckillStockRedis.clearInflight(messageId);
            mqMessageService.sendOrderMessage(message);
        } catch (DuplicateKeyException e) {
            return handleDuplicate(userId, activityId, req, quantity, messageId, message);
        } catch (Exception e) {
            // Redis 已预扣但落库 / 投递失败：回补库存与限购并清在途标记，交给用户重试。
            rollbackDeduct(activityId, req.getSeckillItemId(), userId, quantity, messageId);
            mqMessageService.markFailed(messageId);
            throw e;
        }
        return new ExecuteResultDTO("queued", messageId);
    }

    /**
     * 处理 mq_message 唯一键冲突：仅在限购 key 过期后重复请求同一商品项时出现。
     * 冲突发生时本次请求已完成 Redis 预扣，需按历史记录状态决定撤销还是复用本次预扣。
     */
    private ExecuteResultDTO handleDuplicate(Long userId, Long activityId, ExecuteReqDTO req,
                                             int quantity, String messageId, SeckillOrderMessageDTO message) {
        MqMessageDO existing = mqMessageService.findByMessageId(messageId);
        int status = existing == null || existing.getStatus() == null ? -1 : existing.getStatus();
        if (status == SeckillConstants.MSG_DONE) {
            // 已存在成功订单：撤销本次预扣，给出限购结论。
            rollbackDeduct(activityId, req.getSeckillItemId(), userId, quantity, messageId);
            throw new BizException(LIMIT_HIT_CODE, "您已购买过该商品");
        }
        if (status == SeckillConstants.MSG_SEND_FAILED) {
            // 历史失败记录：复用本次预扣，重新打开为待发送并投递。
            mqMessageService.resetFailedToSending(messageId);
            mqMessageService.sendOrderMessage(message);
            seckillStockRedis.clearInflight(messageId);
            return new ExecuteResultDTO("queued", messageId);
        }
        // 其它在途状态：本次预扣视为重复，撤销后提示排队中，由在途记录继续推进。
        rollbackDeduct(activityId, req.getSeckillItemId(), userId, quantity, messageId);
        return new ExecuteResultDTO("queued", messageId);
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

    /**
     * 回收「Redis 已预扣但 mq_message 未落库」的崩溃遗留。
     *
     * <p>进程在 Lua 扣减后、落库前崩溃会导致库存泄漏（Redis 扣了、无订单也无本地消息）。
     * 本方法扫描在途索引，对超过宽限期仍未落库的成员回补 Redis 库存与用户限购。</p>
     */
    public void recoverOrphanInflightDeducts() {
        Set<String> members = seckillStockRedis.listInflightMessageIds();
        if (members == null || members.isEmpty()) {
            return;
        }
        long nowSeconds = System.currentTimeMillis() / 1000;
        for (String messageId : members) {
            String value = seckillStockRedis.getInflightValue(messageId);
            if (value == null) {
                // 索引有残留但标记 key 已过期，直接清理索引避免脏数据堆积。
                seckillStockRedis.clearInflight(messageId);
                continue;
            }
            int sep = value.lastIndexOf(':');
            long markSeconds = sep > 0 ? Long.parseLong(value.substring(sep + 1)) : 0;
            // 刚扣减的请求仍可能在正常链路内（落库前的极小窗口），跳过宽限期内的成员避免误回收。
            if (nowSeconds - markSeconds < SeckillConstants.INFLIGHT_GRACE_SECONDS) {
                continue;
            }
            if (mqMessageService.findByMessageId(messageId) == null) {
                restoreOrphan(messageId, sep > 0 ? value.substring(0, sep) : "1");
            }
            // 无论是否孤儿（已落库的也清），清除在途标记：落库后由 mq_message 状态机继续兜底。
            seckillStockRedis.clearInflight(messageId);
        }
    }

    private void restoreOrphan(String messageId, String quantityStr) {
        String[] parts = messageId.split(":");
        if (parts.length != 3) {
            return;
        }
        Long userId = Long.valueOf(parts[0]);
        Long activityId = Long.valueOf(parts[1]);
        Long seckillItemId = Long.valueOf(parts[2]);
        int quantity = Integer.parseInt(quantityStr);
        seckillStockRedis.restoreStock(activityId, seckillItemId, quantity);
        seckillStockRedis.restoreUserLimit(activityId, seckillItemId, userId, quantity);
        log.warn("[seckill-inflight] recovered orphan deduct, messageId={}, quantity={}", messageId, quantity);
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

    /**
     * 校验活动是否开放，返回活动结束时间戳(epoch ms)。
     * 优先读 Redis 缓存，冷启动 / 缓存过期才回源 DB 并回填缓存。
     */
    private long requireOpenActivity(Long activityId) {
        Long cachedEnd = seckillStockRedis.getActivityEndTimeMillis(activityId);
        if (cachedEnd != null) {
            if (System.currentTimeMillis() > cachedEnd) {
                throw new BizException(ACTIVITY_CLOSED_CODE, "活动未开始或已结束");
            }
            return cachedEnd;
        }
        SeckillActivityDO activity = seckillActivityMapper.selectById(activityId);
        LocalDateTime now = LocalDateTime.now();
        if (activity == null || activity.getEnabled() == null || activity.getEnabled() != 1
                || now.isBefore(activity.getStartTime()) || now.isAfter(activity.getEndTime())) {
            throw new BizException(ACTIVITY_CLOSED_CODE, "活动未开始或已结束");
        }
        long endMillis = toEpochMilli(activity.getEndTime());
        seckillStockRedis.cacheActivity(activityId, endMillis, ttlSeconds(endMillis));
        return endMillis;
    }

    private SeckillOrderMessageDTO buildMessage(Long userId, Long activityId, ExecuteReqDTO req,
                                                SeckillItemMetaDTO meta, String messageId) {
        SeckillOrderMessageDTO message = new SeckillOrderMessageDTO();
        message.setMessageId(messageId);
        message.setUserId(userId);
        message.setActivityId(activityId);
        message.setSeckillItemId(req.getSeckillItemId());
        message.setSpuId(meta.getSpuId());
        message.setSkuId(meta.getSkuId());
        message.setSeckillPrice(meta.getSeckillPrice());
        message.setPrice(meta.getPrice());
        message.setQuantity(req.getQuantity() == null ? 1 : req.getQuantity());
        message.setAddressId(req.getAddressId());
        message.setCreateTime(LocalDateTime.now());
        return message;
    }

    private void rollbackDeduct(Long activityId, Long seckillItemId, Long userId, int quantity, String messageId) {
        seckillStockRedis.restoreStock(activityId, seckillItemId, quantity);
        seckillStockRedis.restoreUserLimit(activityId, seckillItemId, userId, quantity);
        seckillStockRedis.clearInflight(messageId);
    }

    private BizException toStockException(long result) {
        if (result == SeckillConstants.REDIS_STOCK_NOT_ENOUGH) {
            return new BizException(STOCK_NOT_ENOUGH_CODE, "库存不足");
        }
        if (result == SeckillConstants.REDIS_LIMIT_HIT) {
            return new BizException(LIMIT_HIT_CODE, "您已购买过该商品");
        }
        return new BizException(ResultCode.BAD_REQUEST.getCode(), "参数错误");
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

    private long ttlSeconds(long endTimeMillis) {
        long seconds = (endTimeMillis - System.currentTimeMillis()) / 1000;
        return Math.max(MIN_LIMIT_TTL_SECONDS, seconds);
    }

    private long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
