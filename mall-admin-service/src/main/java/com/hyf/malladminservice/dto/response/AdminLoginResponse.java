package com.hyf.malladminservice.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 管理员登录响应（含 JWT token 对）。
 *
 * @author hyf
 */
@Data
@Builder
public class AdminLoginResponse {

    /** 管理员用户 ID */
    private Long userId;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatar;

    /** 会员等级 */
    private String memberLevel;

    /** 访问令牌 */
    private String accessToken;

    /** 刷新令牌 */
    private String refreshToken;

    /** access token 有效期（秒） */
    private Long expiresIn;
}
