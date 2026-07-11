package com.hyf.mallcommon.security.jwt;

import com.hyf.mallcommon.security.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Map;

/**
 * JWT token 签发与解析服务（基于 jjwt）。
 *
 * <p>本项目的 token 核心：用 HMAC-SHA 算法签发 access / refresh 两类 JWT，
 * 各业务服务与网关共享同一密钥即可本地验签，无需每次回查 auth-service。
 *
 * <p>token 载荷约定（claim）：
 * <ul>
 *   <li>{@code sub} —— 用户 ID（字符串形式）；</li>
 *   <li>{@code type} —— {@link TokenType}，校验时按用途区分；</li>
 *   <li>{@code nickname} / {@code avatar} / {@code memberLevel} / {@code client} —— 用户非敏感信息；</li>
 *   <li>{@code iss} / {@code iat} / {@code exp} —— 标准声明。</li>
 * </ul>
 *
 * <p>校验失败（签名错误、过期、类型不符）抛 {@link InvalidTokenException}，
 * 由上层拦截器转成 401 响应。
 *
 * @author hyf
 */
@Slf4j
@RequiredArgsConstructor
public class JwtTokenService {

    /** JWT 中存放 token 类型的 claim 名 */
    public static final String CLAIM_TYPE = "type";
    /** JWT 中存放昵称的 claim 名 */
    public static final String CLAIM_NICKNAME = "nickname";
    /** JWT 中存放头像的 claim 名 */
    public static final String CLAIM_AVATAR = "avatar";
    /** JWT 中存放会员等级的 claim 名 */
    public static final String CLAIM_MEMBER_LEVEL = "memberLevel";
    /** JWT 中存放客户端类型的 claim 名 */
    public static final String CLAIM_CLIENT = "client";

    private final JwtProperties properties;

    /** HMAC 签名密钥，由 secret 派生 */
    private SecretKey secretKey;

    /**
     * 初始化签名密钥。
     *
     * <p>secret 至少需 32 字节（HS256 要求），不足时抛异常提醒配置错误。
     */
    @PostConstruct
    public void init() {
        byte[] keyBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("mall.jwt.secret 长度不足 32 字节，HMAC-SHA256 要求至少 32 字节");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("[jwt] JwtTokenService 初始化完成，accessTtl={}s, refreshTtl={}s",
                properties.getAccessTokenTtl().getSeconds(), properties.getRefreshTokenTtl().getSeconds());
    }

    /**
     * 颁发 access + refresh token 对。
     *
     * @param loginUser 登录用户信息
     * @return 包含两个 token 与 access 有效期的对
     */
    public TokenPair createTokenPair(LoginUser loginUser) {
        Duration accessTtl = properties.getAccessTokenTtl();
        String accessToken = buildToken(loginUser, TokenType.ACCESS, accessTtl);
        String refreshToken = buildToken(loginUser, TokenType.REFRESH, properties.getRefreshTokenTtl());
        return TokenPair.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(accessTtl.getSeconds())
                .build();
    }

    /**
     * 仅用 refresh token 换发新的 access + refresh token 对。
     *
     * <p>典型刷新流程：校验 refresh token 合法且类型为 REFRESH → 重新签发一对新 token。
     * refresh token 自身不续期，过期后需重新登录。
     *
     * @param refreshToken 前端持有的 refresh token
     * @return 新的 token 对
     * @throws InvalidTokenException refresh token 非法或过期
     */
    public TokenPair refresh(String refreshToken) {
        Claims claims = parse(refreshToken);
        assertType(claims, TokenType.REFRESH);
        LoginUser loginUser = toLoginUser(claims);
        return createTokenPair(loginUser);
    }

    /**
     * 解析并校验 access token，返回载荷。
     *
     * @param accessToken access token
     * @return 解析出的 claims
     * @throws InvalidTokenException token 非法/过期/类型不符
     */
    public Claims parseAccessToken(String accessToken) {
        Claims claims = parse(accessToken);
        assertType(claims, TokenType.ACCESS);
        return claims;
    }

    /**
     * 把 JWT claims 还原为 {@link LoginUser}。
     *
     * @param claims token 载荷
     * @return 登录用户信息
     */
    public LoginUser toLoginUser(Claims claims) {
        return LoginUser.builder()
                .userId(Long.valueOf(claims.getSubject()))
                .nickname(claims.get(CLAIM_NICKNAME, String.class))
                .avatar(claims.get(CLAIM_AVATAR, String.class))
                .memberLevel(claims.get(CLAIM_MEMBER_LEVEL, String.class))
                .client(claims.get(CLAIM_CLIENT, String.class))
                .build();
    }

    // ---------- 内部方法 ----------

    /**
     * 构造单个 JWT。
     *
     * @param loginUser 用户信息
     * @param type      token 类型
     * @param ttl       有效期
     * @return 紧凑序列化的 JWT 字符串
     */
    private String buildToken(LoginUser loginUser, TokenType type, Duration ttl) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + ttl.toMillis());
        return Jwts.builder()
                .subject(String.valueOf(loginUser.getUserId()))
                .issuer(properties.getIssuer())
                .issuedAt(now)
                .expiration(expiration)
                .claims(Map.of(
                        CLAIM_TYPE, type.name(),
                        CLAIM_NICKNAME, nullToEmpty(loginUser.getNickname()),
                        CLAIM_AVATAR, nullToEmpty(loginUser.getAvatar()),
                        CLAIM_MEMBER_LEVEL, nullToEmpty(loginUser.getMemberLevel()),
                        CLAIM_CLIENT, nullToEmpty(loginUser.getClient())
                ))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析并验签 token，失败统一转 {@link InvalidTokenException}。
     */
    private Claims parse(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("token 为空");
        }
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException("token 已过期", e);
        } catch (JwtException e) {
            throw new InvalidTokenException("token 非法", e);
        }
    }

    /**
     * 断言 token 类型匹配，防止 refresh token 用于业务接口。
     */
    private void assertType(Claims claims, TokenType expected) {
        Object type = claims.get(CLAIM_TYPE);
        if (type == null || !expected.name().equals(type.toString())) {
            throw new InvalidTokenException("token 类型不符，期望: " + expected);
        }
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
