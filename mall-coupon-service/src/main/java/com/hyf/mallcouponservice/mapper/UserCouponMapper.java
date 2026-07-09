package com.hyf.mallcouponservice.mapper;

import com.hyf.mallcouponservice.entity.UserCoupon;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserCouponMapper {
    /**
     * 插入领券记录
     * 注意：如果违反联合唯一索引(user_id, coupon_id)，会抛出 DuplicateKeyException
     */
    @Insert("INSERT INTO user_coupon(user_id, coupon_id, status, grab_time) " +
            "VALUES(#{userId}, #{couponId}, #{status}, CURRENT_TIMESTAMP)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserCoupon userCoupon);

    /**
     * 核销（使用）优惠券
     * 必须指定 status = 0 才能更新，防止重复核销
     */
    @Update("UPDATE user_coupon " +
            "SET status = 1, " +
            "    use_time = CURRENT_TIMESTAMP " +
            "WHERE user_id = #{userId} AND coupon_id = #{couponId} AND status = 0")
    int useCoupon(@Param("userId") Long userId, @Param("couponId") Long couponId);
}
