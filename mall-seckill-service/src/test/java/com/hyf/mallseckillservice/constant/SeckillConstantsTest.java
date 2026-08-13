package com.hyf.mallseckillservice.constant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeckillConstantsTest {

    @Test
    void redisKeysIncludeMallPrefixAndExpectedDimensions() {
        assertThat(SeckillConstants.stockKey(1L, 2L)).isEqualTo("mall:seckill:stock:1:2");
        assertThat(SeckillConstants.itemKey(2L)).isEqualTo("mall:seckill:item:2");
        assertThat(SeckillConstants.userKey(1L, 2L, 3L)).isEqualTo("mall:seckill:user:1:2:3");
        assertThat(SeckillConstants.orderKey(3L, 1L, 2L)).isEqualTo("mall:seckill:order:3:1:2");
        assertThat(SeckillConstants.restoreKey("SECKILL1")).isEqualTo("mall:seckill:restore:SECKILL1");
    }

    @Test
    void mqNamesAreSeparatedFromOrderServiceDelayQueue() {
        assertThat(SeckillConstants.SECKILL_EXCHANGE).isEqualTo("seckill.exchange");
        assertThat(SeckillConstants.SECKILL_QUEUE).isEqualTo("seckill.order.queue");
        assertThat(SeckillConstants.SECKILL_DELAY_EXCHANGE).isEqualTo("seckill.delay.exchange");
        assertThat(SeckillConstants.SECKILL_DELAY_QUEUE).isEqualTo("seckill.delay.queue");
    }
}
