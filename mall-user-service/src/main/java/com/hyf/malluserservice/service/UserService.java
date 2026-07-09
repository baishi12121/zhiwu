package com.hyf.malluserservice.service;

import com.hyf.malluserservice.entity.User;
import java.util.List;

/**
 * 用户服务接口，提供对外的用户业务层能力
 */
public interface UserService {

    /**
     * 注册/创建新用户
     *
     * @param user 用户实体，应包含 username, password, phone 等字段
     * @return 注册成功并回填自增 ID 后的用户实体
     */
    User registerUser(User user);

    /**
     * 根据主键ID删除用户
     *
     * @param id 用户主键ID
     * @return 删除成功返回 true，否则返回 false
     */
    boolean deleteUser(Long id);

    /**
     * 更新用户属性（只更新传入实体中非空的属性）
     *
     * @param user 包含主键ID及需要更新属性的实体对象
     * @return 更新成功返回 true，否则返回 false
     */
    boolean updateUser(User user);

    /**
     * 根据用户ID获取详细信息
     *
     * @param id 用户主键ID
     * @return 对应的用户实体，未找到返回 null
     */
    User getUserById(Long id);

    /**
     * 根据用户名获取详细信息（可用于验证用户登录）
     *
     * @param username 用户名
     * @return 对应的用户实体，未找到返回 null
     */
    User getUserByUsername(String username);

    /**
     * 获取所有用户列表
     *
     * @return 用户实体集合
     */
    List<User> getAllUsers();
}
