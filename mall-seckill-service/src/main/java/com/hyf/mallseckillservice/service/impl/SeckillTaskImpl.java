package com.hyf.mallseckillservice.service.impl;

import com.hyf.mallcommon.redis.utils.RedisLock;
import com.hyf.mallseckillservice.constant.SeckillConstants;
import com.hyf.mallseckillservice.entity.OrderDO;
import com.hyf.mallseckillservice.mapper.OrderMapper;
import com.hyf.mallseckillservice.service.MqMessageService;
import com.hyf.mallseckillservice.service.SeckillApplicationService;
import com.hyf.mallseckillservice.service.SeckillCompensateService;
import com.hyf.mallseckillservice.service.SeckillTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 秒杀后台定时任务。
 *
 * <p>负责启动预热、消息重投、活动元数据刷新和超时订单扫描兜底。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "seckill.tasks", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeckillTaskImpl implements SeckillTask, ApplicationRunner {

    private final SeckillApplicationService seckillApplicationService;
    private final MqMessageService mqMessageService;
    private final SeckillCompensateService seckillCompensateService;
    private final OrderMapper orderMapper;
    private final RedisLock redisLock;

    @Override
    public void run(ApplicationArguments args) {
        // Warm up stock and item metadata once when the service starts.
        seckillApplicationService.warmUp();
    }

    @Scheduled(fixedDelay = 60_000L)
    public void warmUpActiveItems() {
        executeLocked(SeckillConstants.TASK_LOCK_REFRESH_META, 90, () -> {
            // Refresh metadata only; never overwrite active stock keys.
            seckillApplicationService.refreshActiveItemMeta();
        });
    }

    @Scheduled(fixedDelay = 30_000L)
    public void retryPendingMessages() {
        executeLocked(SeckillConstants.TASK_LOCK_RETRY_PENDING, 60,
                () -> mqMessageService.retryExpired(200));
    }

    // 回收「Redis 已预扣但 mq_message 未落库」的崩溃遗留，防止进程崩溃导致库存泄漏。
    @Scheduled(fixedDelay = 30_000L)
    public void recoverOrphanInflightDeducts() {
        executeLocked(SeckillConstants.TASK_LOCK_RECOVER_INFLIGHT, 60,
                seckillApplicationService::recoverOrphanInflightDeducts);
    }

    @Scheduled(fixedDelay = 60_000L)
    public void cancelExpiredOrders() {
        executeLocked(SeckillConstants.TASK_LOCK_CANCEL_EXPIRED, 90, () -> {
            for (OrderDO order : orderMapper.selectExpiredPendingSeckillOrders(java.time.LocalDateTime.now(), 100)) {
                seckillCompensateService.cancelAndRestore(order.getId());
            }
        });
    }

    private void executeLocked(String lockKey, long ttlSeconds, Runnable task) {
        boolean executed = redisLock.executeIfLocked(lockKey, ttlSeconds, task);
        if (!executed) {
            log.debug("[seckill-task] skip, lock not acquired, key={}", lockKey);
        }
    }
}
