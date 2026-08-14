package com.hyf.mallseckillservice.redis;

import com.hyf.mallseckillservice.constant.SeckillConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * 秒杀 Redis 库存与限购操作组件。
 *
 * <p>入口流量先在 Redis 中原子扣减库存和记录用户购买数，数据库扣减由异步消费者最终确认。
 * 扣减的同时写入「在途标记」，用于补偿任务回收进程崩溃导致的「已扣库存但未落库」遗留，
 * 从而把本地消息表(MySQL)从请求主链路中剥离出去——这是秒杀入口能扛住高并发的关键。</p>
 */
@Component
@RequiredArgsConstructor
public class SeckillStockRedis {

    /**
     * 原子完成库存扣减、用户限购计数与在途标记写入，避免高并发下分步 GET/SET 产生超卖。
     *
     * <p>KEYS[1] 库存、KEYS[2] 用户限购、KEYS[3] 在途单条标记、KEYS[4] 在途索引(SET)。
     * 在途标记值格式为 "quantity:epochSeconds"，epochSeconds 供补偿任务做最小存活宽限判断。</p>
     */
    private static final RedisScript<Long> DECREMENT_SCRIPT = RedisScript.of("""
            local quantity = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            if quantity < 1 then
                return -2
            end
            local bought = tonumber(redis.call('GET', KEYS[2]))
            if bought and bought + quantity > limit then
                return -1
            end
            local stock = tonumber(redis.call('GET', KEYS[1]))
            if not stock or stock < quantity then
                return 0
            end
            redis.call('DECRBY', KEYS[1], ARGV[2])
            redis.call('INCRBY', KEYS[2], ARGV[2])
            redis.call('EXPIRE', KEYS[2], ARGV[1])
            -- 在途标记：库存已扣但 mq_message 尚未落库。进程若在此窗口崩溃，补偿任务据 KEYS[4] 回收。
            local t = redis.call('TIME')
            redis.call('SET', KEYS[3], ARGV[2] .. ':' .. t[1], 'EX', ARGV[1])
            redis.call('SADD', KEYS[4], ARGV[4])
            redis.call('EXPIRE', KEYS[4], ARGV[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 原子预扣库存并写入在途标记。
     *
     * @param messageId 业务主键 userId:activityId:seckillItemId，同时作为在途索引成员
     */
    public long tryDeduct(Long activityId, Long seckillItemId, Long userId,
                          int quantity, int limitPerUser, long ttlSeconds, String messageId) {
        // Redis Lua 在服务端串行执行，库存判断、扣减、限购计数与在途标记是一个不可拆分的原子动作。
        Long result = stringRedisTemplate.execute(
                DECREMENT_SCRIPT,
                List.of(
                        SeckillConstants.stockKey(activityId, seckillItemId),
                        SeckillConstants.userKey(activityId, seckillItemId, userId),
                        SeckillConstants.inflightKey(messageId),
                        SeckillConstants.INFLIGHT_INDEX_KEY
                ),
                String.valueOf(ttlSeconds),
                String.valueOf(quantity),
                String.valueOf(limitPerUser),
                messageId
        );
        return result == null ? SeckillConstants.REDIS_STOCK_NOT_ENOUGH : result;
    }

    public void restoreStock(Long activityId, Long seckillItemId, int quantity) {
        stringRedisTemplate.opsForValue().increment(SeckillConstants.stockKey(activityId, seckillItemId), quantity);
    }

    /**
     * 回滚用户限购计数。仅在本请求 Lua 已 INCRBY 过限购 key 后调用，此时 key 必然存在。
     */
    public void restoreUserLimit(Long activityId, Long seckillItemId, Long userId, int quantity) {
        stringRedisTemplate.opsForValue().increment(SeckillConstants.userKey(activityId, seckillItemId, userId), -quantity);
    }

    /**
     * 清除在途标记（单条 + 索引成员）。mq_message 已成功落库后调用，后续可靠性交由本地消息表状态机兜底。
     */
    public void clearInflight(String messageId) {
        stringRedisTemplate.opsForSet().remove(SeckillConstants.INFLIGHT_INDEX_KEY, messageId);
        stringRedisTemplate.delete(SeckillConstants.inflightKey(messageId));
    }

    /**
     * 列出所有在途 messageId，供补偿任务扫描。
     */
    public Set<String> listInflightMessageIds() {
        return stringRedisTemplate.opsForSet().members(SeckillConstants.INFLIGHT_INDEX_KEY);
    }

    /**
     * 读取单条在途标记值 "quantity:epochSeconds"。
     */
    public String getInflightValue(String messageId) {
        return stringRedisTemplate.opsForValue().get(SeckillConstants.inflightKey(messageId));
    }

    public void warmUpStock(Long activityId, Long seckillItemId, int stock) {
        // 只在 key 不存在时写入，避免活动进行中把已扣减的在途库存覆盖回 DB 初始值。
        stringRedisTemplate.opsForValue().setIfAbsent(SeckillConstants.stockKey(activityId, seckillItemId), String.valueOf(stock));
    }

    public void cacheItemMeta(Long seckillItemId, String json, long ttlSeconds) {
        stringRedisTemplate.opsForValue().set(SeckillConstants.itemKey(seckillItemId), json, Duration.ofSeconds(ttlSeconds));
    }

    public String getItemMeta(Long seckillItemId) {
        return stringRedisTemplate.opsForValue().get(SeckillConstants.itemKey(seckillItemId));
    }

    /**
     * 缓存活动结束时间戳，入口校验活动启停时优先读它，避免每请求查库。
     */
    public void cacheActivity(Long activityId, long endTimeMillis, long ttlSeconds) {
        stringRedisTemplate.opsForValue().set(SeckillConstants.activityKey(activityId), String.valueOf(endTimeMillis), Duration.ofSeconds(ttlSeconds));
    }

    /**
     * 读取缓存的活动结束时间戳，未命中或异常返回 null，由调用方回源 DB。
     */
    public Long getActivityEndTimeMillis(Long activityId) {
        String v = stringRedisTemplate.opsForValue().get(SeckillConstants.activityKey(activityId));
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
