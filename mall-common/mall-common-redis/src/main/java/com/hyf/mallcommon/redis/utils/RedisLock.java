package com.hyf.mallcommon.redis.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 基于 Redis 的简易分布式锁。
 *
 * <p>使用 {@code SET key value NX PX ttl} 加锁，Lua 脚本原子释放
 * （仅当键的值等于锁标识时才删，防止误删其他线程/实例的锁）。
 *
 * <p>典型用法（非阻塞）：
 * <pre>{@code
 * String lockId = redisLock.tryLock("coupon:grab:1", 10);
 * if (lockId != null) {
 *     try { ... 抢券业务 ... }
 *     finally { redisLock.unlock("coupon:grab:1", lockId); }
 * }
 * }</pre>
 *
 * <p>或使用模板方法：
 * <pre>{@code
 * boolean executed = redisLock.executeIfLocked("coupon:grab:1", 10, () -> {
 *     ... 抢券业务 ...
 * });
 * }</pre>
 *
 * <p>本实现为单 Redis 节点下的简单锁，不适合严格一致性要求场景。
 * 如需强一致（多分片/集群），建议接入 Redisson。
 *
 * @author hyf
 */
@Slf4j
public class RedisLock {

    private final StringRedisTemplate stringRedis;

    private static final String LOCK_PREFIX = "lock:";

    /**
     * Lua 脚本：原子比较键的值是否等于锁标识，匹配则删键。
     * KEYS[1] = lock key, ARGV[1] = lockId
     * 返回 1=成功释放，0=键不存在或值不匹配
     */
    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "return redis.call('del', KEYS[1]) " +
            "else return 0 end";

    private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);

    public RedisLock(StringRedisTemplate stringRedisTemplate) {
        this.stringRedis = stringRedisTemplate;
    }

    /**
     * 非阻塞尝试加锁。成功返回锁标识（用于后续解锁），失败立即返回 {@code null}。
     *
     * @param key       锁键（不含 {@code lock:} 前缀，内部自动拼）
     * @param ttlSeconds 锁过期时间（秒），防止死锁；应略大于业务执行时长
     * @return 锁标识 null=加锁失败，非 null 是唯一标识用于解锁
     */
    public String tryLock(String key, long ttlSeconds) {
        String lockKey = LOCK_PREFIX + key;
        String lockId = UUID.randomUUID().toString();
        Boolean ok = stringRedis.opsForValue()
                .setIfAbsent(lockKey, lockId, Duration.ofSeconds(ttlSeconds));
        boolean acquired = Boolean.TRUE.equals(ok);
        if (acquired) {
            log.debug("[redis-lock] 加锁成功 key={}", key);
        }
        return acquired ? lockId : null;
    }

    /**
     * 释放锁。仅当键的当前值等于调用时传入的 lockId 时才删除。
     *
     * @param key    锁键（与 tryLock 传入的一致）
     * @param lockId tryLock 返回的锁标识
     * @return true=成功释放，false=锁已过期/已被他人持有
     */
    public boolean unlock(String key, String lockId) {
        if (lockId == null) return false;
        String lockKey = LOCK_PREFIX + key;
        Long result = stringRedis.execute(UNLOCK, List.of(lockKey), lockId);
        boolean released = result != null && result == 1L;
        if (released) {
            log.debug("[redis-lock] 释放锁成功 key={}", key);
        } else {
            log.debug("[redis-lock] 释放锁失败（锁已过期或已被他人持有） key={}", key);
        }
        return released;
    }

    /**
     * 持锁执行业务：加锁 → 执行 → 释放。加锁失败则跳过，返回 false。
     *
     * <p>适合抢购/抢券等"竞争执行"场景——谁抢到锁谁执行，没抢到的直接返回。
     *
     * @param key        锁键
     * @param ttlSeconds 锁过期时间（秒）
     * @param task       要执行的任务
     * @return true=加锁成功且任务执行完毕；false=未获取锁，任务未执行
     */
    public boolean executeIfLocked(String key, long ttlSeconds, Runnable task) {
        String lockId = tryLock(key, ttlSeconds);
        if (lockId == null) {
            return false;
        }
        try {
            task.run();
            return true;
        } finally {
            unlock(key, lockId);
        }
    }
}
