package com.hyf.mallauthservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.mallauthservice.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户主表 Mapper。
 *
 * @author hyf
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 按账号查询正常状态用户。
     *
     * @param account 账号
     * @return 用户实体，未找到返回 null
     */
    @Select("SELECT * FROM user WHERE account = #{account} AND status = 1")
    User selectByAccount(@Param("account") String account);

    /**
     * 按手机号查询正常状态用户。
     *
     * @param mobile 手机号
     * @return 用户实体，未找到返回 null
     */
    @Select("SELECT * FROM user WHERE mobile = #{mobile} AND status = 1")
    User selectByMobile(@Param("mobile") String mobile);
}
