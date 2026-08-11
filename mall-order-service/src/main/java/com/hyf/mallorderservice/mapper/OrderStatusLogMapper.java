package com.hyf.mallorderservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.mallorderservice.dataobject.OrderStatusLogDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单状态流转日志 Mapper。
 *
 * @author hyf
 */
@Mapper
public interface OrderStatusLogMapper extends BaseMapper<OrderStatusLogDO> {
}
