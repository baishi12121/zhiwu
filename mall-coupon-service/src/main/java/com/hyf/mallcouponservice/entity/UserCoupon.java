package com.hyf.mallcouponservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户领券记录实体类，对应数据库 user_coupon 表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCoupon implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 优惠券ID
     */
    private Long couponId;

    /**
     * 使用状态：0-未使用，1-已使用，2-已过期
     */
    private Integer status;

    /**
     * 抢券时间
     */
    private LocalDateTime createTime;

    /**
     * 使用时间
     */
    private LocalDateTime useTime;
}
