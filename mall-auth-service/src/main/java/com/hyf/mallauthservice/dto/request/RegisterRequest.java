package com.hyf.mallauthservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求体。
 *
 * <p>对应 {@code POST /auth/register}：
 * <pre>{@code
 * { "account": "newuser", "password": "123456", "mobile": "13800000099", "nickname": "新人" }
 * }</pre>
 *
 * @author hyf
 */
@Data
public class RegisterRequest {

    /** 账号 */
    @NotBlank(message = "账号不能为空")
    @Size(min = 2, max = 50, message = "账号长度 2-50")
    private String account;

    /** 密码（明文） */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度 6-100")
    private String password;

    /** 手机号 */
    @NotBlank(message = "手机号不能为空")
    private String mobile;

    /** 昵称 */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称最长 50 字符")
    private String nickname;
}
