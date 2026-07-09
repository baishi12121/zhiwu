package com.hyf.malluserservice.controller;

import com.hyf.malluserservice.common.Result;
import com.hyf.malluserservice.entity.User;
import com.hyf.malluserservice.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 用户管理控制层，提供用户 CRUD 的 RESTful API 接口
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    /**
     * 构造器注入 UserService
     *
     * @param userService 用户服务业务层接口
     */
    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 注册/创建新用户
     *
     * @param user 用户数据
     * @return 注册成功后的用户数据（含回填的ID，且密码已被MD5哈希）
     */
    @PostMapping
    public Result<User> registerUser(@RequestBody User user) {
        try {
            User registeredUser = userService.registerUser(user);
            return Result.success(registeredUser);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error("注册用户失败: " + e.getMessage());
        }
    }

    /**
     * 根据用户ID删除用户
     *
     * @param id 用户ID
     * @return 操作状态描述
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteUser(@PathVariable("id") Long id) {
        try {
            boolean success = userService.deleteUser(id);
            if (success) {
                return Result.success("删除用户成功");
            } else {
                return Result.error("删除用户失败，该用户可能不存在");
            }
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error("删除用户操作失败: " + e.getMessage());
        }
    }

    /**
     * 更新用户信息
     *
     * @param user 包含主键ID及待更新信息的实体对象
     * @return 操作状态描述
     */
    @PutMapping
    public Result<String> updateUser(@RequestBody User user) {
        try {
            boolean success = userService.updateUser(user);
            if (success) {
                return Result.success("更新用户成功");
            } else {
                return Result.error("更新用户失败，该用户可能不存在");
            }
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error("更新用户操作失败: " + e.getMessage());
        }
    }

    /**
     * 根据用户ID获取用户详细信息
     *
     * @param id 用户主键ID
     * @return 用户详细信息
     */
    @GetMapping("/{id}")
    public Result<User> getUserById(@PathVariable("id") Long id) {
        try {
            User user = userService.getUserById(id);
            if (user != null) {


                return Result.success(user);
            } else {
                return Result.error(404, "未找到该用户");
            }
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error("查询用户失败: " + e.getMessage());
        }
    }

    /**
     * 根据用户名获取用户详情（支持精确查询，通常用于校验或登录）
     *
     * @param username 用户名
     * @return 用户详细信息
     */
    @GetMapping("/username/{username}")
    public Result<User> getUserByUsername(@PathVariable("username") String username) {
        try {
            User user = userService.getUserByUsername(username);
            if (user != null) {
                user.setPassword("******");
                return Result.success(user);
            } else {
                return Result.error(404, "未找到对应的用户名");
            }
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error("查询用户异常: " + e.getMessage());
        }
    }

    /**
     * 获取所有用户列表
     *
     * @return 用户数据集合
     */
    @GetMapping
    public Result<List<User>> getAllUsers() {
        try {
            List<User> users = userService.getAllUsers();
            for (User u : users) {
                u.setPassword("******"); // 隐藏敏感密码数据
            }
            return Result.success(users);
        } catch (Exception e) {
            return Result.error("获取用户列表失败: " + e.getMessage());
        }
    }
}
