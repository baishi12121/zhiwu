package com.hyf.malladminservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.malladminservice.entity.AdminOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 管理后台订单明细 Mapper。
 */
@Mapper
public interface AdminOrderItemMapper extends BaseMapper<AdminOrderItem> {

    @Select("""
            SELECT oi.*, ps.sku_code
            FROM order_item oi
            LEFT JOIN product_sku ps ON ps.id = oi.sku_id
            WHERE oi.order_id = #{orderId}
            ORDER BY oi.id ASC
            """)
    List<AdminOrderItem> listByOrderId(@Param("orderId") Long orderId);
}
