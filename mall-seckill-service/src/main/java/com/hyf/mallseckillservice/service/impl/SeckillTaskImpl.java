package com.hyf.mallseckillservice.service.impl;


import com.hyf.mallseckillservice.service.MqMessageService;
import com.hyf.mallseckillservice.service.SeckillApplicationService;
import com.hyf.mallseckillservice.service.SeckillCompensateService;
import com.hyf.mallseckillservice.service.SeckillOrderService;
import com.hyf.mallseckillservice.service.SeckillTask;
import com.hyf.mallseckillservice.entity.OrderDO;
import com.hyf.mallseckillservice.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
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
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "seckill.tasks", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeckillTaskImpl implements SeckillTask, ApplicationRunner {

    private final SeckillApplicationService seckillApplicationService;
    private final MqMessageService mqMessageService;
    private final SeckillCompensateService seckillCompensateService;
    private final OrderMapper orderMapper;

    @Override
    public void run(ApplicationArguments args) {
        // Warm up stock and item metadata once when the service starts.
        seckillApplicationService.warmUp();
    }

    @Scheduled(fixedDelay = 60_000L)
    public void warmUpActiveItems() {
        // Refresh metadata only; never overwrite active stock keys.
        seckillApplicationService.refreshActiveItemMeta();
    }

    // Phase 1 is single-instance; Phase 2 should guard this scan with a distributed lock.
    @Scheduled(fixedDelay = 30_000L)
    public void retryPendingMessages() {
        mqMessageService.retryExpired(200);
    }

    // 回收「Redis 已预扣但 mq_message 未落库」的崩溃遗留，防止进程崩溃导致库存泄漏。
    // Phase 1 is single-instance; Phase 2 should guard this scan with a distributed lock.
    @Scheduled(fixedDelay = 30_000L)
    public void recoverOrphanInflightDeducts() {
        seckillApplicationService.recoverOrphanInflightDeducts();
    }

    @Scheduled(fixedDelay = 60_000L)
    public void cancelExpiredOrders() {
        for (OrderDO order : orderMapper.selectExpiredPendingSeckillOrders(java.time.LocalDateTime.now(), 100)) {
            seckillCompensateService.cancelAndRestore(order.getId());
        }
    }
}
