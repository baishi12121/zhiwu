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
    /** 活动结束时间缓存 key：预热时写入活动结束时间戳(epoch ms)，入口校验活动启停不再每请求查库。 */
    public static final String ACTIVITY_KEY = MallConstants.REDIS_PREFIX + "seckill:activity:%d";
    /** 在途扣减标记 key（单条）：Redis 已扣库存但 mq_message 尚未落库时存在，值为 "quantity:epochSeconds"。 */
    public static final String INFLIGHT_KEY = MallConstants.REDIS_PREFIX + "seckill:inflight:%s";
    /** 在途扣减索引（Redis SET）：Lua 扣减成功时原子 SADD messageId，补偿任务据此回收崩溃遗留的预扣。 */
    public static final String INFLIGHT_INDEX_KEY = MallConstants.REDIS_PREFIX + "seckill:inflight:index";

    /** 下单消息队列：Redis 预占成功后投递，消费者异步建单。 */
    public static final String SECKILL_EXCHANGE = "seckill.exchange";
    public static final String SECKILL_QUEUE = "seckill.order.queue";
    public static final String SECKILL_ROUTING = "seckill.order";
    public static final String SECKILL_DLX_EXCHANGE = "seckill.dlx";
    public static final String SECKILL_ORDER_DLQ = "seckill.order.dlq";
    public static final String SECKILL_ORDER_DLQ_ROUTING = "seckill.order.dlq";
    public static final String SECKILL_TIMEOUT_DLQ = "seckill.timeout.dlq";
    public static final String SECKILL_TIMEOUT_DLQ_ROUTING = "seckill.timeout.dlq";
    public static final String SECKILL_ORDER_LISTENER_ID = "seckillOrderConsumer";
    /** 延迟取消队列：订单未支付到期后触发库存回补。 */
    public static final String SECKILL_DELAY_EXCHANGE = "seckill.delay.exchange";
    public static final String SECKILL_DELAY_QUEUE = "seckill.delay.queue";
    public static final String SECKILL_DELAY_ROUTING = "seckill.delay.routing";
    public static final int[] CONSUMER_RETRY_BACKOFF_MILLIS = {1_000, 5_000, 30_000};

    /** mq_message 状态机：0待扣减、1待发送、2已发送、3失败、4完成。 */
    public static final int MSG_PENDING_DEDUCT = 0;
    public static final int MSG_PENDING_SEND = 1;
    public static final int MSG_SENT = 2;
    public static final int MSG_SEND_FAILED = 3;
    public static final int MSG_DONE = 4;
    /** 落库后给 MQ 发送预留的宽限期（秒）：防止 confirm 尚未回来就被 retryExpired 立即重投。 */
    public static final int MSG_SEND_GRACE_SECONDS = 60;
    /** 补偿任务回收在途预扣的最小存活宽限（秒）：小于该值视为仍在正常链路内，跳过避免误回收。 */
    public static final int INFLIGHT_GRACE_SECONDS = 60;

    public static final int ORDER_STATE_PENDING_PAY = 1;
    public static final int ORDER_STATE_CANCELLED = 6;
    public static final int ORDER_SOURCE_NORMAL = 1;
    public static final int ORDER_SOURCE_SECKILL = 2;

    /** Redis 订单幂等状态：防止同一 MQ 消息重复建单。 */
    public static final int IDEMPOTENT_PROCESSING = 1;
    public static final int IDEMPOTENT_SUCCESS = 2;
    public static final int IDEMPOTENT_FAILED = 3;
    public static final int SECKILL_ORDER_TTL_SEC = 30 * 60;

    public static final int COMPENSATE_TYPE_ORDER_CREATE_FAILED = 1;
    public static final int COMPENSATE_TYPE_PAY_TIMEOUT = 2;
    public static final int COMPENSATE_TYPE_USER_CANCEL = 3;
    public static final int COMPENSATE_TYPE_RECONCILE_DIFF = 4;
    public static final int COMPENSATE_STATUS_PENDING = 0;
    public static final int COMPENSATE_STATUS_DONE = 1;
    public static final int COMPENSATE_STATUS_FAILED = 2;

    public static final String TASK_LOCK_REFRESH_META = "seckill:task:refresh-meta";
    public static final String TASK_LOCK_RETRY_PENDING = "seckill:task:retry-pending";
    public static final String TASK_LOCK_CANCEL_EXPIRED = "seckill:task:cancel-expired";
    public static final String TASK_LOCK_RECOVER_INFLIGHT = "seckill:task:recover-inflight";
    public static final String TASK_LOCK_RECONCILE_MINUTE = "seckill:task:reconcile-minute";
    public static final String TASK_LOCK_RECONCILE_HOUR = "seckill:task:reconcile-hour";

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

    public static String activityKey(Long activityId) {
        return ACTIVITY_KEY.formatted(activityId);
    }

    public static String inflightKey(String messageId) {
        return INFLIGHT_KEY.formatted(messageId);
    }
}
