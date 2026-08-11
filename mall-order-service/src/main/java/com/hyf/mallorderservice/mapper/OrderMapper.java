package com.hyf.mallorderservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.mallorderservice.dataobject.OrderDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单主表 Mapper。
 *
 * @author hyf
 */
@Mapper
public interface OrderMapper extends BaseMapper<OrderDO> {
}
