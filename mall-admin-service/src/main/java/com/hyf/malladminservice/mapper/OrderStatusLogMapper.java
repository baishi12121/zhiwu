package com.hyf.malladminservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.malladminservice.entity.OrderStatusLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单状态日志 Mapper。
 */
@Mapper
public interface OrderStatusLogMapper extends BaseMapper<OrderStatusLog> {

    @Select("SELECT * FROM order_status_log WHERE order_id = #{orderId} ORDER BY create_time ASC, id ASC")
    List<OrderStatusLog> listByOrderId(@Param("orderId") Long orderId);
}
