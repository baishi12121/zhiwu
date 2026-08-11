package com.hyf.mallorderservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.mallorderservice.dataobject.UserAddressDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收货地址 Mapper — 订单服务只读，用于查询用户地址列表、生成订单地址快照。
 *
 * @author hyf
 */
@Mapper
public interface UserAddressMapper extends BaseMapper<UserAddressDO> {
}
