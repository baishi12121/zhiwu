package com.hyf.mallcouponservice.mapper;

import com.hyf.mallcouponservice.entity.Coupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CouponMapper {
    /**
     * 乐观锁扣减库存：利用 remain_stock > 0 作为兜底防线，绝对防止超卖
     */
    @Update("UPDATE coupon " +
            "SET remain_stock = remain_stock - 1, " +
            "    update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{couponId} AND remain_stock > 0")
    int deductStock(@Param("couponId") Long couponId);

    /**
     * 查询所有有效优惠券
     */
    @Select("SELECT id, title, total_stock, remain_stock, status, create_time, update_time " +
            "FROM coupon WHERE status = 1")
    List<Coupon> selectActiveCoupons();
}
