package com.hyf.mallmarketingservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.mallmarketingservice.entity.UserCoupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户优惠券 Mapper。
 *
 * @author hyf
 */
@Mapper
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    /**
     * 查询用户已领取的优惠券 ID 列表（用于平台券列表标记 grabbed 状态）。
     */
    @Select("SELECT coupon_id FROM user_coupon WHERE user_id = #{userId}")
    List<Long> findGrabbedCouponIds(@Param("userId") Long userId);
}
