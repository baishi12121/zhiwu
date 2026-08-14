package com.hyf.mallseckillservice.mq;

import com.hyf.mallseckillservice.constant.SeckillConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 秒杀消费端指数退避重试执行器。
 *
 * <p>重试只包裹订单创建动作，最终放弃后的库存回补和消息失败标记由消费者外层统一执行一次。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillConsumerRetryExecutor {

    private final RetrySleeper retrySleeper;

    public void execute(String messageId, RetryableAction action) throws Exception {
        int[] backoffs = SeckillConstants.CONSUMER_RETRY_BACKOFF_MILLIS;
        int maxAttempts = backoffs.length + 1;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                action.run();
                if (attempt > 1) {
                    log.info("[seckill-consumer-retry] recovered, messageId={}, attempt={}", messageId, attempt);
                }
                return;
            } catch (DuplicateKeyException e) {
                throw e;
            } catch (Exception e) {
                if (attempt >= maxAttempts) {
                    log.error("[seckill-consumer-retry] exhausted, messageId={}, attempts={}", messageId, attempt, e);
                    throw e;
                }
                int delayMillis = backoffs[attempt - 1];
                log.warn("[seckill-consumer-retry] failed, messageId={}, attempt={}, nextDelayMs={}",
                        messageId, attempt, delayMillis, e);
                retrySleeper.sleep(delayMillis);
            }
        }
    }

    @FunctionalInterface
    public interface RetryableAction {
        void run() throws Exception;
    }

    @FunctionalInterface
    public interface RetrySleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
