package com.hyf.mallauthservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 密码登录请求体（手机号 + 密码）。
 *
 * <p>对应 {@code POST /auth/login}：
 * <pre>{@code
 * { "phone": "13800000002", "password": "123456" }
 * }</pre>
 *
 * @author hyf
 */
@Data
public class LoginRequest {

    /** 手机号 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String phone;

    /** 密码（明文） */
    @NotBlank(message = "密码不能为空")
    private String password;
}
