package com.hyf.mallauthservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 微信绑定手机号请求体。
 *
 * <p>对应 {@code POST /auth/bindWechatPhone}：
 * <pre>{@code
 * { "openid": "oxxx123", "phone": "13800000002" }
 * }</pre>
 *
 * <p>微信新用户首次登录后，需绑定手机号完成注册或合并已有账号。
 *
 * @author hyf
 */
@Data
public class BindWechatPhoneRequest {

    /** 微信 openid（由 wxLogin 返回） */
    @NotBlank(message = "openid 不能为空")
    private String openid;

    /** 手机号（11 位中国大陆手机号） */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    private String phone;
}
