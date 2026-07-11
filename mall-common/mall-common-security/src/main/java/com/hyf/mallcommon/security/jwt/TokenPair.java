package com.hyf.mallcommon.security.jwt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录 token 对，包含 access 与 refresh 两个 JWT。
 *
 * <p>由 {@link JwtTokenService#createTokenPair(LoginUser)} 颁发，
 * 作为登录/刷新接口的响应数据返回给前端（对应 API 文档登录响应的
 * {@code accessToken} / {@code refreshToken} / {@code expiresIn}）。
 *
 * @author hyf
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenPair {

    /** 访问令牌 */
    private String accessToken;
    /** 刷新令牌 */
    private String refreshToken;
    /** access token 剩余有效期（秒） */
    private long expiresIn;
}
