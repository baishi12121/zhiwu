package com.hyf.mallseckillservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.mallseckillservice.entity.UserAddressDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户地址 Mapper。
 *
 * <p>建单时按地址 ID 和用户 ID 双条件查询，防止使用他人地址创建订单。</p>
 */
@Mapper
public interface UserAddressMapper extends BaseMapper<UserAddressDO> {

    UserAddressDO selectByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
