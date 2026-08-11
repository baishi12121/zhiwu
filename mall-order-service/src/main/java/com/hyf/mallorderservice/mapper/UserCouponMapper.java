package com.hyf.mallorderservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.mallorderservice.dataobject.UserCouponDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 用户优惠券 Mapper — 订单服务用于查询可用券、下单占用、取消释放。
 *
 * @author hyf
 */
@Mapper
public interface UserCouponMapper extends BaseMapper<UserCouponDO> {

    /**
     * 占用优惠券（下单时调用）。
     *
     * @param userCouponId 用户优惠券 ID
     * @param orderId      订单 ID
     * @param now          当前时间
     * @return 受影响行数，0 表示券已被占用或不存在
     */
    @Update("UPDATE user_coupon SET status = 1, use_time = #{now}, order_id = #{orderId} " +
            "WHERE id = #{userCouponId} AND status = 0")
    int occupyCoupon(@Param("userCouponId") Long userCouponId,
                     @Param("orderId") Long orderId,
                     @Param("now") LocalDateTime now);

    /**
     * 释放优惠券（取消订单时调用）。
     *
     * @param userCouponId 用户优惠券 ID
     * @return 受影响行数，0 表示券已非占用状态
     */
    @Update("UPDATE user_coupon SET status = 0, use_time = NULL, order_id = NULL " +
            "WHERE id = #{userCouponId} AND status = 1")
    int releaseCoupon(@Param("userCouponId") Long userCouponId);
}
