package com.hyf.mallseckillservice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单表实体在秒杀服务内的轻量映射。
 *
 * <p>只映射秒杀建单、状态查询、超时取消和库存回补需要的字段。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`order`")
public class OrderDO extends BaseEntity {

    private String orderNo;
    private Long userId;
    private Integer orderState;
    private BigDecimal totalMoney;
    private BigDecimal payMoney;
    private BigDecimal postFee;
    private BigDecimal discountAmount;
    private Integer payType;
    private Integer payChannel;
    private Integer deliveryTimeType;
    private String buyerMessage;
    private Long addressId;
    private String addressSnapshot;
    private String receiverContact;
    private String receiverMobile;
    private String receiverAddress;
    private Long couponId;
    private Long userCouponId;
    private String cancelReason;
    private LocalDateTime payLatestTime;
    private LocalDateTime paidAt;
    private LocalDateTime shippedAt;
    private LocalDateTime receivedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private Integer orderSource;
    private Long activityId;
    private Long seckillItemId;
}
