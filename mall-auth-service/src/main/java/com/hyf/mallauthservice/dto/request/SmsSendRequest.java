package com.hyf.mallauthservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送短信验证码请求体。
 *
 * <p>对应 {@code POST /auth/sms/send}：
 * <pre>{@code
 * { "mobile": "13800000002" }
 * }</pre>
 *
 * @author hyf
 */
@Data
public class SmsSendRequest {

    /** 手机号 */
    @NotBlank(message = "手机号不能为空")
    private String mobile;
}
