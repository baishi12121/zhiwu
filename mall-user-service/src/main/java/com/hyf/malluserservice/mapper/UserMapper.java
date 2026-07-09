package com.hyf.malluserservice.mapper;

import com.hyf.malluserservice.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 用户 Mapper 接口，定义 tb_user 表的数据访问操作
 */
@Mapper
public interface UserMapper {

    /**
     * 插入一条用户数据
     *
     * @param user 用户实体对象
     * @return 影响的行数，插入成功返回 1
     */
    int insert(User user);

    /**
     * 根据用户ID删除用户
     *
     * @param id 用户主键ID
     * @return 影响的行数，删除成功返回 1
     */
    int deleteById(@Param("id") Long id);

    /**
     * 更新用户信息（动态更新非空字段）
     *
     * @param user 用户实体对象，需包含 id 属性
     * @return 影响的行数，更新成功返回 1
     */
    int update(User user);

    /**
     * 根据主键ID查询用户详情
     *
     * @param id 用户主键ID
     * @return 用户实体对象，未找到返回 null
     */
    User selectById(@Param("id") Long id);

    /**
     * 根据用户名查询用户详情（用于账号登录或排重校验）
     *
     * @param username 用户名
     * @return 用户实体对象，未找到返回 null
     */
    User selectByUsername(@Param("username") String username);

    /**
     * 根据手机号查询用户详情（用于手机号登录）
     *
     * @param phone 手机号
     * @return 用户实体对象，未找到返回 null
     */
    User selectByPhone(@Param("phone") String phone);

    /**
     * 仅更新用户最后登录时间
     *
     * @param id          用户主键ID
     * @param lastLoginAt 最近一次登录时间
     * @return 影响的行数
     */
    int updateLastLoginAt(@Param("id") Long id, @Param("lastLoginAt") java.time.LocalDateTime lastLoginAt);

    /**
     * 查询所有用户列表
     *
     * @return 用户实体对象集合
     */
    List<User> selectAll();
}
