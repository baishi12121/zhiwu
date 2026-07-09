package com.hyf.mallproductservice.rabbitmq;

import com.hyf.mallproductservice.entity.ProductScoreMessage;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 商品热度评分 MQ 监听器
 * <p>
 * 设计思路：不是每来一条消息就调一次 Redis ZINCRBY，而是在内存中聚合同一 productId
 * 的热度增量，满足以下任一条件时批量刷写到 Redis：
 * <ul>
 *   <li>累计收到 200 条消息（{@link #BATCH_SIZE}）</li>
 *   <li>距离上次刷写已过 100ms（{@link #FLUSH_INTERVAL_MS}）</li>
 * </ul>
 * <p>
 * 批量刷写使用 Redis Pipeline 在一次网络往返中完成所有 ZINCRBY 操作，相比逐条写入：
 * <ul>
 *   <li>200 条消息从 200 次 Redis 调用降为 1 次 Pipeline 调用</li>
 *   <li>相同 productId 的多次点击/下单会被聚合（如 A 商品点击 50 次 → ZINCRBY A 50）</li>
 *   <li>极大缓解 RabbitMQ 消息积压和 Redis 写入瓶颈</li>
 * </ul>
 */
@Component
@Slf4j
public class ProductScoreMessageListener {

    /** Redis 有序集合 key，存储商品热度排行榜 */
    private static final String PRODUCT_RANK_KEY = "product:hot:rank";

    /** 每累积 200 条消息触发一次刷写 */
    private static final int BATCH_SIZE = 200;

    /** 每 100ms 定时触发一次刷写，保证低流量时消息也不会长时间滞留在内存 */
    private static final long FLUSH_INTERVAL_MS = 100;

    private final StringRedisTemplate redisTemplate;

    /**
     * 内存聚合缓冲区：productId → 累计热度增量
     * 使用 ConcurrentHashMap 支持多线程并发 merge
     */
    private final ConcurrentHashMap<String, Double> scoreBuffer = new ConcurrentHashMap<>();

    /** 当前缓冲区中未刷写的消息数量，达到 BATCH_SIZE 触发刷写 */
    private final AtomicInteger messageCount = new AtomicInteger(0);

    /** 刷写锁，保证同一时刻只有一个线程执行 swap+clear 操作 */
    private final Object flushLock = new Object();

    /**
     * 定时刷写调度器，单线程守护线程，每 100ms 触发一次 {@link #flush()}
     */
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "product-score-flusher");
                t.setDaemon(true);
                return t;
            });

    @Autowired
    public ProductScoreMessageListener(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        // 首次延迟 100ms 后每隔 100ms 执行一次 flush
        scheduler.scheduleAtFixedRate(this::flush, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 容器销毁前的回调，关闭定时器并执行最后一次刷写，避免内存数据丢失
     */
    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        flush();
    }

    /**
     * 监听商品点击队列，每次点击加 1 分
     */
    @RabbitListener(queues = "product.click.queue")
    public void onProductClick(ProductScoreMessage message) {
        buffer(message, 1.0);
    }

    /**
     * 监听商品下单队列，每次下单加 5 分（下单比点击更具购买意向，权重更高）
     */
    @RabbitListener(queues = "product.order.queue")
    public void onProductOrder(ProductScoreMessage message) {
        buffer(message, 5.0);
    }

    /**
     * 将消息的热度增量写入内存缓冲区
     * <p>
     * 使用 {@link ConcurrentHashMap#merge} 原子地将同一 productId 的分数累加，
     * 例如商品 A 被点击 50 次，缓冲区中记录为 A→50.0，而非 50 条独立记录。
     *
     * @param message 评分消息（包含 productId、actionType、timestamp）
     * @param weight  热度增量权重（点击=1，下单=5）
     */
    private void buffer(ProductScoreMessage message, double weight) {
        if (message == null || message.getProductId() == null) {
            return;
        }
        String productId = String.valueOf(message.getProductId());
        // 原子地将 weight 累加到 productId 对应的分数上
        scoreBuffer.merge(productId, weight, Double::sum);
        // 达到批量阈值则立即触发刷写
        if (messageCount.incrementAndGet() >= BATCH_SIZE) {
            flush();
        }
    }

    /**
     * 将缓冲区中所有聚合热度数据批量刷写到 Redis
     * <p>
     * 采用 "swap & clear" 策略：持锁期间快照当前缓冲区内容并清空，
     * 锁外执行 Redis Pipeline（网络 IO 不占锁），避免阻塞消息写入线程。
     * <p>
     * Pipeline 将 N 个 ZINCRBY 合并为一次网络往返，时间复杂度仍为 O(N·log M)，
     * 但网络开销从 N 次降为 1 次。
     */
    private void flush() {
        Map<String, Double> toFlush;
        synchronized (flushLock) {
            if (scoreBuffer.isEmpty()) {
                return;
            }
            // 快照当前缓冲区数据，然后清空
            toFlush = new HashMap<>(scoreBuffer);
            scoreBuffer.clear();
            messageCount.set(0);
        }

        try {
            // Redis Pipeline 批量执行 ZINCRBY，一次网络往返完成所有写入
            redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                byte[] key = PRODUCT_RANK_KEY.getBytes();
                for (Map.Entry<String, Double> entry : toFlush.entrySet()) {
                    connection.zIncrBy(key, entry.getValue(), entry.getKey().getBytes());
                }
                return null;
            });
            log.info("Flushed {} aggregated product scores to Redis", toFlush.size());
        } catch (Exception e) {
            // Pipeline 失败则日志记录并丢弃本次批次，避免无限重试
            log.error("Batch flush to Redis failed, {} entries lost", toFlush.size(), e);
        }
    }
}
