package com.hyf.mallseckillservice.service.impl;


import com.hyf.mallseckillservice.service.MqMessageService;
import com.hyf.mallseckillservice.service.SeckillApplicationService;
import com.hyf.mallseckillservice.service.SeckillCompensateService;
import com.hyf.mallseckillservice.service.SeckillOrderService;
import com.hyf.mallseckillservice.service.SeckillTask;
import com.hyf.mallseckillservice.constant.SeckillConstants;
import com.hyf.mallcommon.core.exception.BizException;
import com.hyf.mallcommon.core.result.ResultCode;
import com.hyf.mallseckillservice.dto.StockCompensateDTO;
import com.hyf.mallseckillservice.entity.OrderDO;
import com.hyf.mallseckillservice.entity.OrderItemDO;
import com.hyf.mallseckillservice.mapper.OrderItemMapper;
import com.hyf.mallseckillservice.mapper.OrderMapper;
import com.hyf.mallseckillservice.mapper.SeckillItemMapper;
import com.hyf.mallseckillservice.redis.SeckillStockRedis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 秒杀库存补偿服务。
 *
 * <p>处理支付超时、用户取消等场景的订单状态流转和 Redis/DB 库存回补。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillCompensateServiceImpl implements SeckillCompensateService {

    private static final String TIMEOUT_CANCEL_REASON = "支付超时自动取消";
    private static final String USER_CANCEL_REASON = "用户取消秒杀订单";

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final SeckillItemMapper seckillItemMapper;
    private final SeckillStockRedis seckillStockRedis;
    private final StringRedisTemplate stringRedisTemplate;

    @Transactional(rollbackFor = Exception.class)
    public void cancelAndRestore(Long orderId) {
        // 延迟队列触发的自动取消，只处理秒杀来源且仍为待支付的订单。
        OrderDO order = orderMapper.selectById(orderId);
        if (order == null || order.getOrderSource() == null || order.getOrderSource() != SeckillConstants.ORDER_SOURCE_SECKILL) {
            return;
        }
        if (order.getOrderState() == null || order.getOrderState() != SeckillConstants.ORDER_STATE_PENDING_PAY) {
            log.info("[seckill-compensate] skip timeout restore, orderNo={}, state={}", order.getOrderNo(), order.getOrderState());
            return;
        }
        int affected = orderMapper.cancelPendingOrder(order.getId(), TIMEOUT_CANCEL_REASON, LocalDateTime.now());
        if (affected == 0) {
            return;
        }
        restoreStockOnce(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public void restoreForCancel(String orderNo) {
        restoreForCancel(orderNo, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void restoreForCancel(String orderNo, StockCompensateDTO dto) {
        OrderDO order = orderMapper.selectByOrderNo(orderNo);
        if (order == null || order.getOrderSource() == null || order.getOrderSource() != SeckillConstants.ORDER_SOURCE_SECKILL) {
            return;
        }
        validateCompensateRequest(order, dto);
        if (order.getOrderState() != null && order.getOrderState() == SeckillConstants.ORDER_STATE_PENDING_PAY) {
            // 用户取消时先把待支付订单推进到取消态，再做库存回补。
            int affected = orderMapper.cancelPendingOrder(order.getId(), USER_CANCEL_REASON, LocalDateTime.now());
            if (affected == 0) {
                return;
            }
        } else if (order.getOrderState() == null || order.getOrderState() != SeckillConstants.ORDER_STATE_CANCELLED) {
            log.info("[seckill-compensate] skip user cancel restore, orderNo={}, state={}", order.getOrderNo(), order.getOrderState());
            return;
        }
        restoreStockOnce(order);
    }

    private void validateCompensateRequest(OrderDO order, StockCompensateDTO dto) {
        if (dto == null) {
            return;
        }
        // 内部接口参数和订单事实不一致时拒绝回补，避免错单把库存加回错误活动或商品。
        if ((dto.getActivityId() != null && !dto.getActivityId().equals(order.getActivityId()))
                || (dto.getSeckillItemId() != null && !dto.getSeckillItemId().equals(order.getSeckillItemId()))
                || (dto.getUserId() != null && !dto.getUserId().equals(order.getUserId()))) {
            log.error("[seckill-compensate] dto mismatch order, orderNo={}, dto={}", order.getOrderNo(), dto);
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "compensate request does not match order");
        }
    }

    private void restoreStockOnce(OrderDO order) {
        // 以 orderNo 维度做回补幂等，防止延迟消息和订单服务取消通知双触发导致重复加库存。
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(
                SeckillConstants.restoreKey(order.getOrderNo()),
                "1",
                Duration.ofSeconds(SeckillConstants.SECKILL_RESTORE_TTL_SEC));
        if (!Boolean.TRUE.equals(first)) {
            log.info("[seckill-compensate] stock already restored, orderNo={}", order.getOrderNo());
            return;
        }
        OrderItemDO item = orderItemMapper.selectFirstByOrderId(order.getId());
        int quantity = item == null || item.getQuantity() == null ? 1 : item.getQuantity();
        seckillItemMapper.restoreStock(order.getSeckillItemId(), quantity);
        Runnable restoreRedis = () -> seckillStockRedis.restoreStock(order.getActivityId(), order.getSeckillItemId(), quantity);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // DB 事务提交后再回补 Redis，避免 DB 回滚但 Redis 已加回造成库存偏大。
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    restoreRedis.run();
                }
            });
        } else {
            restoreRedis.run();
        }
        log.info("[seckill-compensate] restored stock, orderNo={}, seckillItemId={}, quantity={}",
                order.getOrderNo(), order.getSeckillItemId(), quantity);
    }
}
