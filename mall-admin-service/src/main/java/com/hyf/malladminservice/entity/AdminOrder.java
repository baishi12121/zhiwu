package com.hyf.malladminservice.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台订单视图实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`order`")
public class AdminOrder extends BaseEntity {

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

    @TableField(exist = false)
    private String nickname;
    @TableField(exist = false)
    private String itemImage;
    @TableField(exist = false)
    private String itemName;
    @TableField(exist = false)
    private Integer itemCount;
    @TableField(exist = false)
    private Integer totalNum;
    @TableField(exist = false)
    private List<AdminOrderItem> items;
    @TableField(exist = false)
    private List<OrderStatusLog> statusLogs;
    @TableField(exist = false)
    private OrderLogistics logistics;
}
