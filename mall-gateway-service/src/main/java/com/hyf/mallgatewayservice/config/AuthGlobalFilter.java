package com.hyf.mallgatewayservice.config;

import com.hyf.mallcommon.core.constant.MallConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 网关鉴权过滤器。
 *
 * <p>职责：
 * <ol>
 *   <li>放行白名单路径（公开资源 + 登录/注册接口）；</li>
 *   <li>其余路径校验 {@code Authorization: Bearer xxx}，使用 jjwt 本地验签；</li>
 *   <li>验签通过后将 userId / nickname 写入下游请求头。</li>
 * </ol>
 *
 * @author hyf
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /** 白名单路径（Ant 风格），无需携带 token */
    private static final List<String> WHITELIST = List.of(
            "/auth/**",
            "/home/**",
            "/categories/**",
            "/products/**",
            "/upload/**",
            "/dict/**",
            "/health/**",
            "/avatar/**",
            "/user/avatar/upload",
            "/pay/wx/notify",
            // 管理后台登录入口（其他 /admin/** 仍需 token）
            "/admin/login",
            "/admin/health",
            // 智能客服聊天接口（允许匿名咨询）
            "/ai/chat",
            "/ai/chat/**",
            "/ai/health",
            "/error"
    );

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final SecretKey secretKey;
    private final String issuer;

    public AuthGlobalFilter(@Value("${mall.jwt.secret:zhiwu-mall-secret-change-me}") String secret,
                            @Value("${mall.jwt.issuer:zhiwu-mall}") String issuer) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            log.warn("[gateway] jwt.secret 长度不足 32 字节, 实际: {}", keyBytes.length);
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.issuer = issuer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. 白名单直接放行
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        // 2. 提取 Bearer token
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String token = extractBearer(authHeader);
        if (token == null) {
            return unauthorized(exchange, "缺少访问令牌");
        }

        // 3. 解析 JWT
        try {
            Claims claims = parseToken(token);
            String userId = claims.getSubject();
            String nickname = claims.get("nickname", String.class);

            // 4. 透传 userId 到下游请求头
            ServerHttpRequest mutated = request.mutate()
                    .header(MallConstants.HEADER_AUTH, authHeader) // 原始 token 透传到下游
                    .header("X-User-Id", userId != null ? userId : "")
                    .header("X-User-Nickname", nickname != null ? nickname : "")
                    .build();

            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (ExpiredJwtException e) {
            return unauthorized(exchange, "访问令牌已过期");
        } catch (JwtException e) {
            log.warn("[gateway] JWT 验签失败: path={}, msg={}", path, e.getMessage());
            return unauthorized(exchange, "访问令牌无效");
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }

    // ==================== 内部方法 ====================

    /**
     * 是否在白名单中。
     */
    private boolean isWhitelisted(String path) {
        return WHITELIST.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    /**
     * 提取 Bearer token。
     */
    private String extractBearer(String auth) {
        if (auth == null || auth.isBlank()) {
            return null;
        }
        if (!auth.startsWith(MallConstants.TOKEN_PREFIX)) {
            return null;
        }
        String token = auth.substring(MallConstants.TOKEN_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * 解析并验签 JWT。
     */
    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 返回 401 Unauthorized 的 JSON 响应体（对齐 Result<T> 格式）。
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format("{\"code\":401,\"message\":\"%s\",\"data\":null}", message);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
