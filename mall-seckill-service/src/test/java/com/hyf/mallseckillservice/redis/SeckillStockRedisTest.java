package com.hyf.mallseckillservice.redis;

import com.hyf.mallseckillservice.constant.SeckillConstants;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeckillStockRedisTest {

    @Test
    void tryDeductExecutesLuaWithStockUserAndInflightKeys() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), any(List.class),
                eq("1800"), eq("2"), eq("5"), eq("30:10:20")))
                .thenReturn(SeckillConstants.REDIS_OK);
        SeckillStockRedis stockRedis = new SeckillStockRedis(redisTemplate);

        long result = stockRedis.tryDeduct(10L, 20L, 30L, 2, 5, 1800, "30:10:20");

        assertThat(result).isEqualTo(SeckillConstants.REDIS_OK);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture(),
                eq("1800"), eq("2"), eq("5"), eq("30:10:20"));
        assertThat(keysCaptor.getValue())
                .containsExactly(
                        "mall:seckill:stock:10:20",
                        "mall:seckill:user:10:20:30",
                        "mall:seckill:inflight:30:10:20",
                        "mall:seckill:inflight:index");
    }

    @Test
    void warmUpDoesNotOverwriteExistingStockAndRestoreUsePlainStringCounters() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        SeckillStockRedis stockRedis = new SeckillStockRedis(redisTemplate);

        stockRedis.warmUpStock(10L, 20L, 100);
        stockRedis.restoreStock(10L, 20L, 2);
        stockRedis.cacheItemMeta(20L, "{\"limitPerUser\":1}", 60);

        verify(ops).setIfAbsent("mall:seckill:stock:10:20", "100");
        verify(ops).increment("mall:seckill:stock:10:20", 2L);
        verify(ops).set("mall:seckill:item:20", "{\"limitPerUser\":1}", Duration.ofSeconds(60));
    }
}
