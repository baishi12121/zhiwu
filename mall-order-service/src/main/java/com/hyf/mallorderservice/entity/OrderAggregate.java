package com.hyf.mallorderservice.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单聚合根（对应 {@code order} 表）
 *
 * <p>DDD：订单是一个聚合，聚合内含：
 * <pre>
 * Order
 *  ├─ OrderItem       订单明细
 *  ├─ OrderAddress    收货地址快照
 *  └─ Payment         支付信息
 * </pre>
 * 聚合根负责维护状态流转的一致性（1待付款 → 2待发货 → 3待收货 → 4待评价 → 5已完成 / 6已取消）。
 *
 * @author hyf
 */
@Data
public class OrderAggregate {

    private Long id;

    /** 业务订单号 */
    private String orderNo;

    /** 用户 ID */
    private Long userId;

    /** 订单状态：1待付款 2待发货 3待收货 4待评价 5已完成 6已取消 */
    private Integer orderState;

    /** 金额合计 */
    private BigDecimal totalMoney;

    /** 实付金额 */
    private BigDecimal payMoney;

    /** 邮费 */
    private BigDecimal postFee;

    /** 优惠金额 */
    private BigDecimal discountAmount;

    /** 支付方式：1在线支付 2货到付款 */
    private Integer payType;

    /** 支付渠道：1支付宝 2微信 */
    private Integer payChannel;

    /** 配送时间类型：1不限 2工作日 3双休或假日 */
    private Integer deliveryTimeType;

    /** 买家留言 */
    private String buyerMessage;

    /** 地址 ID */
    private Long addressId;

    /** 收货地址快照（JSON） */
    private String addressSnapshot;

    /** 收货人 */
    private String receiverContact;

    /** 收货人手机 */
    private String receiverMobile;

    /** 收货人地址 */
    private String receiverAddress;

    /** 优惠券 ID */
    private Long couponId;

    /** 用户优惠券 ID */
    private Long userCouponId;

    /** 取消原因 */
    private String cancelReason;

    /** 付款截止时间 */
    private LocalDateTime payLatestTime;

    /** 付款时间 */
    private LocalDateTime paidAt;

    /** 发货时间 */
    private LocalDateTime shippedAt;

    /** 收货时间 */
    private LocalDateTime receivedAt;

    /** 交易完成时间 */
    private LocalDateTime completedAt;

    /** 交易关闭时间 */
    private LocalDateTime cancelledAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
