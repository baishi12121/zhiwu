package com.hyf.malluserservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.malluserservice.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户主表 Mapper（mall-user-service 侧）。
 *
 * <p>仅读写用户资料，不涉及认证逻辑。
 *
 * @author hyf
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
