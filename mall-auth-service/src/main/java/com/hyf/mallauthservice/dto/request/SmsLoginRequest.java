package com.hyf.mallauthservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 短信登录请求体。
 *
 * <p>对应 {@code POST /auth/sms/login}：
 * <pre>{@code
 * { "mobile": "13800000002", "code": "839201" }
 * }</pre>
 *
 * @author hyf
 */
@Data
public class SmsLoginRequest {

    /** 手机号 */
    @NotBlank(message = "手机号不能为空")
    private String mobile;

    /** 短信验证码 */
    @NotBlank(message = "验证码不能为空")
    private String code;
}
