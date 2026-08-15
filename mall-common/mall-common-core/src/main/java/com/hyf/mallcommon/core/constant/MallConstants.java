package com.hyf.mallcommon.core.constant;

/**
 * 商城通用常量集合。
 *
 * <p>集中存放请求头名称、客户端类型、内部调用前缀、Redis Key 模板等被多个微服务复用的字符串常量，
 * 避免在各业务服务中散落硬编码，降低拼写不一致导致的联调风险。
 *
 * <p>使用约定：
 * <ul>
 *   <li>请求头名以 {@code HEADER_} 开头，客户端类型以 {@code CLIENT_} 开头；</li>
 *   <li>Redis Key 若带占位符（如 {@code %d}），调用方需用 {@link String#formatted(Object...)} 填入业务主键，
 *       并统一在键首拼接 {@link #REDIS_PREFIX} 避免跨业务键冲突；</li>
 *   <li>该类仅作为命名空间，不应被实例化或继承。</li>
 * </ul>
 *
 * @author hyf
 */
public final class MallConstants {

    private MallConstants() {
        // 工具类禁止实例化
    }

    // ---------- 时区 ----------

    /** 默认时区，全系统时间计算与展示统一使用 */
    public static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";

    // ---------- 请求头与客户端 ----------

    /** 请求头：访问令牌（Authorization） */
    public static final String HEADER_AUTH = "Authorization";
    /** 请求头：访问令牌前缀，形如 {@code Bearer xxx} */
    public static final String TOKEN_PREFIX = "Bearer ";
    /** 请求头：来源客户端标识，用于区分小程序/App/管理后台 */
    public static final String HEADER_SOURCE_CLIENT = "source-client";
    /** 客户端：微信小程序 */
    public static final String CLIENT_MINIAPP = "miniapp";
    /** 客户端：App */
    public static final String CLIENT_APP = "app";

    // ---------- 内部调用 ----------

    /** 内部 Feign 调用路径前缀；网关层会对 {@code /internal/**} 直接返回 404，Feign 调用绕过网关直连服务端口 */
    public static final String INTERNAL_PREFIX = "/internal";

    // ---------- Redis Key ----------

    /** Redis 通用前缀，所有业务键建议以它开头以做命名空间隔离 */
    public static final String REDIS_PREFIX = "mall:";

    /** Token 黑名单 Key 前缀，logout 时将 access token 写入此前缀下，TTL 对齐 token 剩余有效期 */
    public static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";

    // ---------- 优惠券 Redis Key（沿用旧版设计，仅改前缀） ----------

    /** 优惠券剩余库存 Key 模板，参数为优惠券 ID */
    public static final String COUPON_STOCK_KEY = "coupon:stock:%d";
    /** 已抢券用户集合 Key 模板（Set），参数为优惠券 ID */
    public static final String COUPON_USERS_KEY = "coupon:users:%d";
    /** 优惠券元信息缓存 Key 模板，参数为优惠券 ID */
    public static final String COUPON_INFO_KEY = "coupon:info:%d";

    // ---------- 商品热榜 Redis Key（沿用旧版设计） ----------

    /** 商品热度排行榜 Key（ZSet，score 为热度分） */
    public static final String PRODUCT_HOT_RANK_KEY = "product:hot:rank";

    // ---------- RabbitMQ（订单超时自动取消，延迟消息插件） ----------

    /** 延迟交换机名（类型 x-delayed-message，需安装 rabbitmq_delayed_message_exchange 插件） */
    public static final String MQ_ORDER_DELAY_EXCHANGE = "order.delay.exchange";
    /** 延迟队列名（durable，存放待取消的订单 ID） */
    public static final String MQ_ORDER_DELAY_QUEUE = "order.delay.queue";
    /** 延迟路由键 */
    public static final String MQ_ORDER_DELAY_ROUTING_KEY = "order.delay.routing.key";
    /** 延迟消息头名（值为延迟毫秒数） */
    public static final String MQ_X_DELAY_HEADER = "x-delay";

    // ---------- RabbitMQ product search index sync ----------

    public static final String MQ_PRODUCT_INDEX_EXCHANGE = "product.index.exchange";
    public static final String MQ_PRODUCT_INDEX_QUEUE = "product.index.queue";
    public static final String MQ_PRODUCT_INDEX_DLQ = "product.index.dlq";
    public static final String MQ_PRODUCT_INDEX_DLX = "product.index.dlx";
    public static final String MQ_PRODUCT_INDEX_UPSERT_ROUTING_KEY = "product.index.upsert";
    public static final String MQ_PRODUCT_INDEX_DELETE_ROUTING_KEY = "product.index.delete";
    public static final String MQ_PRODUCT_INDEX_DLQ_ROUTING_KEY = "product.index.dlq";
}
