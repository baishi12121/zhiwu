package com.hyf.malladminservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.malladminservice.entity.OrderLogisticsTrack;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单物流轨迹 Mapper。
 */
@Mapper
public interface OrderLogisticsTrackMapper extends BaseMapper<OrderLogisticsTrack> {

    @Select("""
            SELECT *
            FROM order_logistics_track
            WHERE order_logistics_id = #{logisticsId}
            ORDER BY sort_order ASC, occur_time ASC, id ASC
            """)
    List<OrderLogisticsTrack> listByLogisticsId(@Param("logisticsId") Long logisticsId);
}
