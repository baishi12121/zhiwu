package com.hyf.malluserservice.dto;

import com.hyf.malluserservice.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录成功返回
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /**
     * 登录凭证,前端需要在 header/请求中携带
     */
    private String token;

    /**
     * 有效期(秒)
     */
    private Long expiresIn;

    /**
     * 登录用户信息(密码字段已置空)
     */
    private User user;
}
