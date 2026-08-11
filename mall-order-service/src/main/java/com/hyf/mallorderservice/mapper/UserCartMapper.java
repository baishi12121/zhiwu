package com.hyf.mallorderservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.mallorderservice.dataobject.UserCartDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 购物车 Mapper — 订单服务只读，用于查询用户选中的购物车商品生成订单明细。
 *
 * @author hyf
 */
@Mapper
public interface UserCartMapper extends BaseMapper<UserCartDO> {
}
