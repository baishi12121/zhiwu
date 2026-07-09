package com.hyf.malluserservice.service.impl;

import com.hyf.malluserservice.entity.User;
import com.hyf.malluserservice.mapper.UserMapper;
import com.hyf.malluserservice.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * 用户服务实现类，处理用户核心业务逻辑
 */
@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    /**
     * 构造器注入 UserMapper
     *
     * @param userMapper 用户数据库操作接口
     */
    @Autowired
    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 注册新用户
     *
     * @param user 用户实体，应包含 username, password, phone 等字段
     * @return 注册成功并回填自增 ID 后的用户实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public User registerUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("用户数据不能为空");
        }
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }

        // 排重校验
        User existUser = userMapper.selectByUsername(user.getUsername().trim());
        if (existUser != null) {
            throw new IllegalArgumentException("用户名已存在");
        }

        // 参数修饰与默认赋值
        user.setUsername(user.getUsername().trim());
        user.setPassword(md5Encrypt(user.getPassword().trim())); // 密码安全转换
        
        if (user.getBalance() == null) {
            user.setBalance(BigDecimal.ZERO);
        }
        if (user.getStatus() == null) {
            user.setStatus(1); // 默认正常状态
        }

        userMapper.insert(user);
        return user;
    }

    /**
     * 根据主键ID删除用户
     *
     * @param id 用户主键ID
     * @return 删除成功返回 true，否则返回 false
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteUser(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        return userMapper.deleteById(id) > 0;
    }

    /**
     * 更新用户属性（只更新传入实体中非空的属性）
     *
     * @param user 包含主键ID及需要更新属性的实体对象
     * @return 更新成功返回 true，否则返回 false
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateUser(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("更新数据且主键ID不能为空");
        }
        // 如果修改了密码，且密码不为 32 位的 MD5 结构，进行加密
        if (user.getPassword() != null && !user.getPassword().trim().isEmpty()) {
            String pwd = user.getPassword().trim();
            if (pwd.length() != 32) {
                user.setPassword(md5Encrypt(pwd));
            }
        }
        return userMapper.update(user) > 0;
    }

    /**
     * 根据用户ID获取详细信息
     *
     * @param id 用户主键ID
     * @return 对应的用户实体，未找到返回 null
     */
    @Override
    public User getUserById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        return userMapper.selectById(id);
    }

    /**
     * 根据用户名获取详细信息（用于登录或校验）
     *
     * @param username 用户名
     * @return 对应的用户实体，未找到返回 null
     */
    @Override
    public User getUserByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        return userMapper.selectByUsername(username.trim());
    }

    /**
     * 获取所有用户列表
     *
     * @return 用户实体集合
     */
    @Override
    public List<User> getAllUsers() {
        return userMapper.selectAll();
    }

    /**
     * 标准的 MD5 加密辅助方法
     *
     * @param source 待加密明文字符串
     * @return 32 位十六进制 MD5 密文字符串
     */
    private String md5Encrypt(String source) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(source.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) {
                    sb.append("0");
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 算法初始化失败", e);
        }
    }
}
