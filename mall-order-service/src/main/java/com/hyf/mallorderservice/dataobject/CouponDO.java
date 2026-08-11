package com.hyf.mallorderservice.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板 DO — 对应 {@code coupon} 表。
 *
 * <p>订单服务用于查询可用优惠券、计算优惠金额。
 *
 * @author hyf
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("coupon")
public class CouponDO extends BaseEntity {

    private String title;
    /** 1满减 2折扣 */
    private Integer couponType;
    /** 满多少可用 */
    private BigDecimal thresholdAmount;
    /** 满减金额 */
    private BigDecimal discountAmount;
    /** 折扣率 0.85=8.5折 */
    private BigDecimal discountRate;
    private Integer totalStock;
    private Integer remainStock;
    private Integer perUserLimit;
    private LocalDateTime validStart;
    private LocalDateTime validEnd;
    private Integer status;
}
