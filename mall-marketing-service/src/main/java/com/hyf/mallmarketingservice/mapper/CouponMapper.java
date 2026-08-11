package com.hyf.mallmarketingservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.mallmarketingservice.entity.Coupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 优惠券模板 Mapper。
 *
 * @author hyf
 */
@Mapper
public interface CouponMapper extends BaseMapper<Coupon> {

    /**
     * 原子扣减库存（防超卖），返回 0 表示库存不足。
     */
    @Update("UPDATE coupon SET remain_stock = remain_stock - 1 WHERE id = #{id} AND remain_stock > 0")
    int decreaseStock(@Param("id") Long id);

    /**
     * 恢复库存（领取失败时回滚）。
     */
    @Update("UPDATE coupon SET remain_stock = remain_stock + 1 WHERE id = #{id}")
    int increaseStock(@Param("id") Long id);
}
