package com.hyf.mallorderservice.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户优惠券 DO — 对应 {@code user_coupon} 表。
 *
 * <p>订单服务用于查询用户可用优惠券、下单时占用、取消时释放。
 *
 * @author hyf
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_coupon")
public class UserCouponDO extends BaseEntity {

    private Long userId;
    private Long couponId;
    /** 0未用 1已用 2过期 */
    private Integer status;
    private LocalDateTime grabTime;
    private LocalDateTime useTime;
    private Long orderId;
}
