package com.hyf.mallseckillservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.mallseckillservice.entity.OrderItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 订单明细 Mapper。
 *
 * <p>秒杀回补时读取订单明细数量，确保回补数量和实际购买数量一致。</p>
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItemDO> {

    OrderItemDO selectFirstByOrderId(@Param("orderId") Long orderId);
}
