package com.hyf.malladminservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员登录请求。
 *
 * @author hyf
 */
@Data
public class AdminLoginRequest {

    /** 账号 */
    @NotBlank(message = "账号不能为空")
    @Size(max = 50, message = "账号长度不能超过 50")
    private String account;

    /** 密码（明文，后端做 MD5 比对） */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度 6-32")
    private String password;
}
