package com.hyf.mallseckillservice.constant;

import com.hyf.mallcommon.core.constant.MallConstants;

/**
 * 秒杀业务常量集中定义。
 *
 * <p>包含 Redis key、MQ 名称、消息状态机、订单状态和 Lua 脚本返回码，避免各链路散落魔法值。</p>
 */
public final class SeckillConstants {

    private SeckillConstants() {
    }

    public static final String STOCK_KEY = MallConstants.REDIS_PREFIX + "seckill:stock:%d:%d";
    public static final String ITEM_KEY = MallConstants.REDIS_PREFIX + "seckill:item:%d";
    public static final String USER_KEY = MallConstants.REDIS_PREFIX + "seckill:user:%d:%d:%d";
    public static final String ORDER_KEY = MallConstants.REDIS_PREFIX + "seckill:order:%d:%d:%d";
    public static final String RESTORE_KEY = MallConstants.REDIS_PREFIX + "seckill:restore:%s";

    /** 下单消息队列：Redis 预占成功后投递，消费者异步建单。 */
    public static final String SECKILL_EXCHANGE = "seckill.exchange";
    public static final String SECKILL_QUEUE = "seckill.order.queue";
    public static final String SECKILL_ROUTING = "seckill.order";
    /** 延迟取消队列：订单未支付到期后触发库存回补。 */
    public static final String SECKILL_DELAY_EXCHANGE = "seckill.delay.exchange";
    public static final String SECKILL_DELAY_QUEUE = "seckill.delay.queue";
    public static final String SECKILL_DELAY_ROUTING = "seckill.delay.routing";

    /** mq_message 状态机：0待扣减、1待发送、2已发送、3失败、4完成。 */
    public static final int MSG_PENDING_DEDUCT = 0;
    public static final int MSG_PENDING_SEND = 1;
    public static final int MSG_SENT = 2;
    public static final int MSG_SEND_FAILED = 3;
    public static final int MSG_DONE = 4;

    public static final int ORDER_STATE_PENDING_PAY = 1;
    public static final int ORDER_STATE_CANCELLED = 6;
    public static final int ORDER_SOURCE_NORMAL = 1;
    public static final int ORDER_SOURCE_SECKILL = 2;

    /** Redis 订单幂等状态：防止同一 MQ 消息重复建单。 */
    public static final int IDEMPOTENT_PROCESSING = 1;
    public static final int IDEMPOTENT_SUCCESS = 2;
    public static final int IDEMPOTENT_FAILED = 3;
    public static final int SECKILL_ORDER_TTL_SEC = 30 * 60;
    public static final int SECKILL_RESTORE_TTL_SEC = 7 * 24 * 60 * 60;

    /** Lua 扣减库存脚本返回码。 */
    public static final long REDIS_OK = 1L;
    public static final long REDIS_STOCK_NOT_ENOUGH = 0L;
    public static final long REDIS_LIMIT_HIT = -1L;
    public static final long REDIS_INVALID_QTY = -2L;

    public static String stockKey(Long activityId, Long seckillItemId) {
        return STOCK_KEY.formatted(activityId, seckillItemId);
    }

    public static String itemKey(Long seckillItemId) {
        return ITEM_KEY.formatted(seckillItemId);
    }

    public static String userKey(Long activityId, Long seckillItemId, Long userId) {
        return USER_KEY.formatted(activityId, seckillItemId, userId);
    }

    public static String orderKey(Long userId, Long activityId, Long seckillItemId) {
        return ORDER_KEY.formatted(userId, activityId, seckillItemId);
    }

    public static String restoreKey(String orderNo) {
        return RESTORE_KEY.formatted(orderNo);
    }
}
