package com.hyf.mallseckillservice.redis;

import com.hyf.mallseckillservice.constant.SeckillConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 秒杀 Redis 库存与限购操作组件。
 *
 * <p>入口流量先在 Redis 中原子扣减库存和记录用户购买数，数据库扣减由异步消费者最终确认。</p>
 */
@Component
@RequiredArgsConstructor
public class SeckillStockRedis {

    /**
     * 原子完成库存扣减和用户限购计数，避免高并发下分步 GET/SET 产生超卖。
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
            return 1
            """, Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    public long tryDeduct(Long activityId, Long seckillItemId, Long userId,
                          int quantity, int limitPerUser, long ttlSeconds) {
        // Redis Lua 在服务端串行执行，库存判断、扣减和限购计数是一个不可拆分的原子动作。
        Long result = stringRedisTemplate.execute(
                DECREMENT_SCRIPT,
                List.of(
                        SeckillConstants.stockKey(activityId, seckillItemId),
                        SeckillConstants.userKey(activityId, seckillItemId, userId)
                ),
                String.valueOf(ttlSeconds),
                String.valueOf(quantity),
                String.valueOf(limitPerUser)
        );
        return result == null ? SeckillConstants.REDIS_STOCK_NOT_ENOUGH : result;
    }

    public void restoreStock(Long activityId, Long seckillItemId, int quantity) {
        stringRedisTemplate.opsForValue().increment(SeckillConstants.stockKey(activityId, seckillItemId), quantity);
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
}
