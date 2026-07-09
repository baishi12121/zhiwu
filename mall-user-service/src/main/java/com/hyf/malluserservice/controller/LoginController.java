package com.hyf.malluserservice.controller;

import com.hyf.malluserservice.common.Result;
import com.hyf.malluserservice.dto.LoginRequest;
import com.hyf.malluserservice.dto.LoginResponse;
import com.hyf.malluserservice.entity.User;
import com.hyf.malluserservice.service.LoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 登录鉴权控制器
 */
@RestController
@RequestMapping("/users")
public class LoginController {

    /**
     * 登录 token 传递的 header 名(前端 http.ts 也需对齐)
     */
    public static final String HEADER_TOKEN = "X-Token";

    private final LoginService loginService;

    @Autowired
    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }

    /**
     * 手机号 + 密码登录
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest req) {
        try {
            return Result.success(loginService.loginByPhone(req));
        }
        catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
        catch (Exception e) {
            return Result.error("登录失败: " + e.getMessage());
        }
    }

    /**
     * 根据 token 获取当前登录用户(等价于 /user/info)
     */
    @GetMapping("/me")
    public Result<User> me(@RequestHeader(value = HEADER_TOKEN, required = false) String token) {
        if (token == null || token.isEmpty()) {
            return Result.error(401, "未登录");
        }
        User user = loginService.getUserByToken(token);
        if (user == null) {
            return Result.error(401, "登录已过期,请重新登录");
        }
        return Result.success(user);
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader(value = HEADER_TOKEN, required = false) String token) {
        loginService.logout(token);
        return Result.success("已退出登录");
    }
}
