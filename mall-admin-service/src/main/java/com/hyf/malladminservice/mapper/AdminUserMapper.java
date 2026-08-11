package com.hyf.malladminservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.malladminservice.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户主表 Mapper（管理后台用）。
 *
 * @author hyf
 */
@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {

    /**
     * 按账号查询正常状态用户（管理员登录入口使用）。
     *
     * @param account 账号
     * @return 用户实体，未找到返回 null
     */
    @Select("SELECT * FROM user WHERE account = #{account} AND status = 1")
    AdminUser selectByAccount(@Param("account") String account);
}
