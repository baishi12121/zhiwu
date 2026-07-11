package com.hyf.mallauthservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信小程序登录请求体。
 *
 * <p>对应 {@code POST /auth/wxLogin}：
 * <pre>{@code
 * { "code": "wx_jscode_xxx", "nickname": "微信昵称", "avatar": "https://..." }
 * }</pre>
 *
 * <p>服务端用 {@code code} 调微信 code2Session 拿 openid，第一次登录自动注册。
 *
 * @author hyf
 */
@Data
public class WxLoginRequest {

    /** 微信 jscode，用于换取 openid */
    @NotBlank(message = "微信授权码不能为空")
    private String code;

    /** 微信昵称（首次登录时写入） */
    private String nickname;

    /** 微信头像 URL（首次登录时写入） */
    private String avatar;
}
