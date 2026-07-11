package com.hyf.mallcommon.security.jwt;

/**
 * JWT 类型枚举。
 *
 * <p>本项目签发两类 token（详见 {@code doc/API接口文档.md} §1.2）：
 * <ul>
 *   <li>{@link #ACCESS} —— 访问令牌，有效期短（默认 30 分钟），用于业务接口鉴权；</li>
 *   <li>{@link #REFRESH} —— 刷新令牌，有效期长（默认 7 天），仅用于换取新的 access token。</li>
 * </ul>
 *
 * <p>把类型写入 JWT 的 {@code type} claim，校验时按用途校验类型，
 * 防止用 refresh token 直接访问业务接口。
 *
 * @author hyf
 */
public enum TokenType {

    /** 访问令牌 */
    ACCESS,
    /** 刷新令牌 */
    REFRESH
}
