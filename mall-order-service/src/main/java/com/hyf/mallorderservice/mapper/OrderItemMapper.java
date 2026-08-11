package com.hyf.mallorderservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.mallorderservice.dataobject.OrderItemDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单明细 Mapper。
 *
 * @author hyf
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItemDO> {
}
