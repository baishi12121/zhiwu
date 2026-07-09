package com.hyf.malluserservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录请求参数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    /**
     * 手机号
     */
    private String phone;

    /**
     * 明文密码(前端可不加密,后端统一 MD5)
     */
    private String password;

    /**
     * 登录端: H5/MP/APP(可不传,默认 H5)
     */
    private String client;
}
