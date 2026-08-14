package com.hyf.mallseckillservice.service.impl;

import com.hyf.mallseckillservice.entity.MqMessageDO;
import com.hyf.mallseckillservice.entity.SeckillItemDO;
import com.hyf.mallseckillservice.constant.SeckillConstants;
import com.hyf.mallcommon.redis.utils.RedisLock;
import com.hyf.mallseckillservice.mapper.MqMessageMapper;
import com.hyf.mallseckillservice.mapper.SeckillItemMapper;
import com.hyf.mallseckillservice.mq.RabbitMqManagementClient;
import com.hyf.mallseckillservice.redis.SeckillStockRedis;
import com.hyf.mallseckillservice.service.MqMessageService;
import com.hyf.mallseckillservice.service.SeckillCompensateService;
import com.hyf.mallseckillservice.service.SeckillReconcileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.OptionalLong;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "seckill.tasks", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeckillReconcileServiceImpl implements SeckillReconcileService {

    private static final int MESSAGE_RECONCILE_LIMIT = 200;
    private static final int STOCK_RECONCILE_LIMIT = 1_000;

    private final MqMessageMapper mqMessageMapper;
    private final SeckillItemMapper seckillItemMapper;
    private final SeckillStockRedis seckillStockRedis;
    private final MqMessageService mqMessageService;
    private final SeckillCompensateService seckillCompensateService;
    private final RabbitMqManagementClient rabbitMqManagementClient;
    private final RedisLock redisLock;

    @Override
    @Scheduled(fixedDelay = 60_000L)
    public void reconcileMinute() {
        boolean executed = redisLock.executeIfLocked(SeckillConstants.TASK_LOCK_RECONCILE_MINUTE, 60, this::doReconcileMinute);
        if (!executed) {
            log.debug("[seckill-reconcile] skip minute reconcile, lock not acquired");
        }
    }

    private void doReconcileMinute() {
        int pendingDue = mqMessageMapper.countPendingSendDue();
        OptionalLong backlog = rabbitMqManagementClient.orderQueueBacklog();
        if (backlog.isPresent()) {
            long queueMessages = backlog.getAsLong();
            if (pendingDue > queueMessages) {
                log.warn("[seckill-reconcile] pending mq_message exceeds queue backlog, pendingDue={}, queueBacklog={}, action=retryExpired",
                        pendingDue, queueMessages);
                mqMessageService.retryExpired(MESSAGE_RECONCILE_LIMIT);
            } else {
                log.info("[seckill-reconcile] message backlog ok, pendingDue={}, queueBacklog={}", pendingDue, queueMessages);
            }
        } else {
            log.warn("[seckill-reconcile] skip RabbitMQ backlog item, pendingDue={}", pendingDue);
        }

        List<MqMessageDO> missingOrders = mqMessageMapper.selectSentWithoutOrder(MESSAGE_RECONCILE_LIMIT);
        for (MqMessageDO missing : missingOrders) {
            log.error("[seckill-reconcile] mq_message has no seckill order, messageId={}, status={}, userId={}, activityId={}, seckillItemId={}",
                    missing.getMessageId(), missing.getStatus(), missing.getUserId(), missing.getActivityId(), missing.getSeckillItemId());
        }
    }

    @Override
    @Scheduled(cron = "0 0 * * * *")
    public void reconcileHour() {
        boolean executed = redisLock.executeIfLocked(SeckillConstants.TASK_LOCK_RECONCILE_HOUR, 120, this::doReconcileHour);
        if (!executed) {
            log.debug("[seckill-reconcile] skip hour reconcile, lock not acquired");
        }
    }

    private void doReconcileHour() {
        int fixed = 0;
        int lower = 0;
        for (SeckillItemDO item : seckillItemMapper.selectEnabledItemsForReconcile(STOCK_RECONCILE_LIMIT)) {
            Integer redisStock = seckillStockRedis.getStock(item.getActivityId(), item.getId());
            Integer mysqlStock = item.getSeckillStock();
            if (redisStock == null || mysqlStock == null || redisStock.equals(mysqlStock)) {
                continue;
            }
            if (redisStock > mysqlStock) {
                // Redis 比 DB 多只可能是回补时 Redis 多回 / DB 未回上，向下校准是安全且必要的（否则超卖）。
                int diff = redisStock - mysqlStock;
                String messageId = "reconcile:" + item.getActivityId() + ":" + item.getId();
                seckillCompensateService.recordReconcileDiff(messageId, item.getActivityId(), item.getId(), 0L, diff);
                seckillStockRedis.calibrateStock(item.getActivityId(), item.getId(), mysqlStock);
                fixed++;
                log.warn("[seckill-reconcile] stock calibrated down, activityId={}, seckillItemId={}, redisStock={}, mysqlStock={}",
                        item.getActivityId(), item.getId(), redisStock, mysqlStock);
            } else {
                // Redis < DB：正常的在途预扣（Redis 先扣、DB 异步后扣）或 Redis 回补失败；
                // 向上校准会把已售出的在途库存加回 Redis 导致超卖，因此只告警、不校准。
                lower++;
                log.info("[seckill-reconcile] stock lower than mysql (in-flight or restore lag), activityId={}, seckillItemId={}, redisStock={}, mysqlStock={}",
                        item.getActivityId(), item.getId(), redisStock, mysqlStock);
            }
        }
        log.info("[seckill-reconcile] hourly stock reconcile finished, fixed={}, lower={}", fixed, lower);
    }
}
