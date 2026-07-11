package com.hyf.mallauthservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录/注册成功响应体。
 *
 * <p>对应 {@code POST /auth/login} 等接口的 {@code data} 字段：
 * <pre>{@code
 * {
 *   "userId": 2,
 *   "nickname": "张三",
 *   "avatar": "https://...",
 *   "memberLevel": "GOLD",
 *   "accessToken": "eyJhbGciOi...",
 *   "refreshToken": "eyJhbGciOi...",
 *   "expiresIn": 1800
 * }
 * }</pre>
 *
 * @author hyf
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /** 用户 ID */
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
    private long expiresIn;

    /** 是否需要绑定手机号（微信新用户首次登录时为 true） */
    @Builder.Default
    private boolean needBindPhone = false;

    /** 微信 openid（仅 needBindPhone=true 时返回，供绑定手机号接口使用） */
    private String openid;

    /**
     * 用户手机号（明文），仅当数据库中已存在时返回。
     *
     * <p>前端在弹窗中展示时应当做中间四位脱敏（如 {@code 138****0000}）。
     * 新用户（未绑定手机号）该字段为 {@code null}。
     */
    private String mobile;
}
