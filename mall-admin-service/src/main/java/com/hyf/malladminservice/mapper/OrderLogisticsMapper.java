package com.hyf.malladminservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.malladminservice.entity.OrderLogistics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 订单物流 Mapper。
 */
@Mapper
public interface OrderLogisticsMapper extends BaseMapper<OrderLogistics> {

    @Select("""
            SELECT ol.*, lc.name AS company_name, lc.code AS company_code, lc.tel AS company_tel
            FROM order_logistics ol
            LEFT JOIN logistics_company lc ON lc.id = ol.company_id
            WHERE ol.order_id = #{orderId}
            """)
    OrderLogistics selectByOrderId(@Param("orderId") Long orderId);

    default void upsert(OrderLogistics logistics) {
        OrderLogistics exists = selectByOrderId(logistics.getOrderId());
        if (exists == null) {
            insert(logistics);
            return;
        }
        logistics.setId(exists.getId());
        updateById(logistics);
    }
}
