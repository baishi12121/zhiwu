package com.hyf.malluserservice.service;

import com.hyf.malluserservice.dto.LoginRequest;
import com.hyf.malluserservice.dto.LoginResponse;
import com.hyf.malluserservice.entity.User;

/**
 * 登录服务接口
 */
public interface LoginService {

    /**
     * 手机号 + 密码登录
     *
     * @param req 登录请求
     * @return 登录成功返回 {token, user}
     */
    LoginResponse loginByPhone(LoginRequest req);

    /**
     * 根据 token 获取当前登录用户
     *
     * @param token 登录凭证
     * @return 用户实体
     */
    User getUserByToken(String token);

    /**
     * 退出登录(删除 token 记录)
     *
     * @param token 登录凭证
     */
    void logout(String token);
}
