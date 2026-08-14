package com.hyf.mallseckillservice.service.impl;

import com.hyf.mallcommon.core.exception.BizException;
import com.hyf.mallcommon.core.result.ResultCode;
import com.hyf.mallseckillservice.constant.SeckillConstants;
import com.hyf.mallseckillservice.dto.StockCompensateDTO;
import com.hyf.mallseckillservice.entity.OrderDO;
import com.hyf.mallseckillservice.entity.OrderItemDO;
import com.hyf.mallseckillservice.entity.SeckillStockCompensateDO;
import com.hyf.mallseckillservice.mapper.OrderItemMapper;
import com.hyf.mallseckillservice.mapper.OrderMapper;
import com.hyf.mallseckillservice.mapper.SeckillItemMapper;
import com.hyf.mallseckillservice.mapper.SeckillStockCompensateMapper;
import com.hyf.mallseckillservice.redis.SeckillStockRedis;
import com.hyf.mallseckillservice.service.SeckillCompensateService;
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
 * <p>所有库存回补先写补偿流水，再执行库存修复；Redis SETNX 保留为快速去重，DB 流水用于最终留痕。</p>
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
    private final SeckillStockCompensateMapper compensateMapper;
    private final SeckillStockRedis seckillStockRedis;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelAndRestore(Long orderId) {
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
        restoreStockForOrder(order, SeckillConstants.COMPENSATE_TYPE_PAY_TIMEOUT);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreForCancel(String orderNo) {
        restoreForCancel(orderNo, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreForCancel(String orderNo, StockCompensateDTO dto) {
        OrderDO order = orderMapper.selectByOrderNo(orderNo);
        if (order == null || order.getOrderSource() == null || order.getOrderSource() != SeckillConstants.ORDER_SOURCE_SECKILL) {
            return;
        }
        validateCompensateRequest(order, dto);
        if (order.getOrderState() != null && order.getOrderState() == SeckillConstants.ORDER_STATE_PENDING_PAY) {
            int affected = orderMapper.cancelPendingOrder(order.getId(), USER_CANCEL_REASON, LocalDateTime.now());
            if (affected == 0) {
                return;
            }
        } else if (order.getOrderState() == null || order.getOrderState() != SeckillConstants.ORDER_STATE_CANCELLED) {
            log.info("[seckill-compensate] skip user cancel restore, orderNo={}, state={}", order.getOrderNo(), order.getOrderState());
            return;
        }
        restoreStockForOrder(order, SeckillConstants.COMPENSATE_TYPE_USER_CANCEL);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreForCreateFailure(String messageId, Long activityId, Long seckillItemId, Long userId, int quantity) {
        if (!insertCompensate(messageId, activityId, seckillItemId, userId, quantity,
                SeckillConstants.COMPENSATE_TYPE_ORDER_CREATE_FAILED)) {
            log.info("[seckill-compensate] create-failure stock already restored, messageId={}", messageId);
            return;
        }
        restoreRedisAndMark(messageId, SeckillConstants.COMPENSATE_TYPE_ORDER_CREATE_FAILED,
                activityId, seckillItemId, quantity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordReconcileDiff(String messageId, Long activityId, Long seckillItemId, Long userId, int quantity) {
        if (insertCompensate(messageId, activityId, seckillItemId, userId, quantity,
                SeckillConstants.COMPENSATE_TYPE_RECONCILE_DIFF)) {
            compensateMapper.markDone(messageId, SeckillConstants.COMPENSATE_TYPE_RECONCILE_DIFF);
        }
    }

    private void validateCompensateRequest(OrderDO order, StockCompensateDTO dto) {
        if (dto == null) {
            return;
        }
        if ((dto.getActivityId() != null && !dto.getActivityId().equals(order.getActivityId()))
                || (dto.getSeckillItemId() != null && !dto.getSeckillItemId().equals(order.getSeckillItemId()))
                || (dto.getUserId() != null && !dto.getUserId().equals(order.getUserId()))) {
            log.error("[seckill-compensate] dto mismatch order, orderNo={}, dto={}", order.getOrderNo(), dto);
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "compensate request does not match order");
        }
    }

    private void restoreStockForOrder(OrderDO order, int compensateType) {
        String messageId = order.getOrderNo();
        if (compensateMapper.countCancellationDone(messageId) > 0) {
            log.info("[seckill-compensate] cancellation stock already restored, orderNo={}", order.getOrderNo());
            return;
        }
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(
                SeckillConstants.restoreKey(order.getOrderNo()),
                "1",
                Duration.ofSeconds(SeckillConstants.SECKILL_RESTORE_TTL_SEC));
        if (!Boolean.TRUE.equals(first)) {
            log.info("[seckill-compensate] stock already restored by redis key, orderNo={}", order.getOrderNo());
            return;
        }

        OrderItemDO item = orderItemMapper.selectFirstByOrderId(order.getId());
        int quantity = item == null || item.getQuantity() == null ? 1 : item.getQuantity();
        if (!insertCompensate(messageId, order.getActivityId(), order.getSeckillItemId(), order.getUserId(), quantity, compensateType)) {
            return;
        }
        // DB 回补在事务内执行，失败则整笔（含补偿流水）随事务回滚，不会留下脏流水。
        seckillItemMapper.restoreStock(order.getSeckillItemId(), quantity);
        restoreRedisAndMark(messageId, compensateType, order.getActivityId(), order.getSeckillItemId(), quantity);
    }

    /**
     * 在事务提交后回补 Redis 库存，并按回补结果落补偿流水状态（成功 markDone / 失败 markFailed）。
     *
     * <p>必须在事务内调用（内部经 afterCommit 延后执行），保证 DB 回滚时 Redis 不会多回补；
     * 流水状态在 Redis 回补完成之后才落，避免「已标完成但 Redis 实际未回补」的错账。</p>
     */
    private void restoreRedisAndMark(String messageId, int compensateType,
                                     Long activityId, Long seckillItemId, int quantity) {
        runAfterCommit(() -> {
            try {
                seckillStockRedis.restoreStock(activityId, seckillItemId, quantity);
                compensateMapper.markDone(messageId, compensateType);
                log.info("[seckill-compensate] redis stock restored, messageId={}, seckillItemId={}, quantity={}, type={}",
                        messageId, seckillItemId, quantity, compensateType);
            } catch (Exception e) {
                compensateMapper.markFailed(messageId, compensateType);
                log.error("[seckill-compensate] redis stock restore failed, messageId={}, seckillItemId={}, quantity={}, type={}",
                        messageId, seckillItemId, quantity, compensateType, e);
            }
        });
    }

    private boolean insertCompensate(String messageId, Long activityId, Long seckillItemId,
                                     Long userId, int quantity, int compensateType) {
        SeckillStockCompensateDO compensate = new SeckillStockCompensateDO();
        compensate.setMessageId(messageId);
        compensate.setActivityId(activityId);
        compensate.setSeckillItemId(seckillItemId);
        compensate.setUserId(userId == null ? 0L : userId);
        compensate.setQuantity(quantity);
        compensate.setCompensateType(compensateType);
        compensate.setStatus(SeckillConstants.COMPENSATE_STATUS_PENDING);
        return compensateMapper.insertIgnore(compensate) > 0;
    }

    private void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
