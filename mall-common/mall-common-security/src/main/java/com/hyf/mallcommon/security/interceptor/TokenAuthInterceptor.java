package com.hyf.mallcommon.security.interceptor;

import com.hyf.mallcommon.core.constant.MallConstants;
import com.hyf.mallcommon.core.exception.UnauthorizedException;
import com.hyf.mallcommon.feign.FeignAuthHolder;
import com.hyf.mallcommon.redis.utils.RedisUtils;
import com.hyf.mallcommon.security.context.SecurityContextHolder;
import com.hyf.mallcommon.security.jwt.JwtTokenService;
import com.hyf.mallcommon.security.jwt.LoginUser;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Token 鉴权拦截器。
 *
 * <p>职责：
 * <ol>
 *   <li>从请求头 {@code Authorization: Bearer xxx} 取出 access token；</li>
 *   <li>用 {@link JwtTokenService#parseAccessToken(String)} 本地验签解析出用户身份；</li>
 *   <li>把 {@link LoginUser} 写入 {@link SecurityContextHolder}，并把原始 Authorization 头
 *       写入 {@link FeignAuthHolder} 以便后续 Feign 调用透传；</li>
 *   <li>请求结束后在 {@link #afterCompletion} 清理两个 ThreadLocal，防止线程池复用串号。</li>
 * </ol>
 *
 * <p>白名单（放行）路径由 {@link com.hyf.mallcommon.security.config.SecurityAutoConfiguration}
 * 在注册拦截器时通过 {@code addPathPatterns} / {@code excludePathPatterns} 控制，
 * 本拦截器只处理“已确定需要鉴权”的请求。
 *
 * @author hyf
 */
@Slf4j
public class TokenAuthInterceptor implements HandlerInterceptor {

    private final JwtTokenService jwtTokenService;
    private final ObjectProvider<RedisUtils> redisUtilsProvider;

    public TokenAuthInterceptor(JwtTokenService jwtTokenService, ObjectProvider<RedisUtils> redisUtilsProvider) {
        this.jwtTokenService = jwtTokenService;
        this.redisUtilsProvider = redisUtilsProvider;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String auth = request.getHeader(MallConstants.HEADER_AUTH);
        String token = extractBearer(auth);
        if (token == null) {
            throw new UnauthorizedException("缺少访问令牌");
        }

        // 1. 检查 Redis 黑名单（logout 后 token 不可复用）
        if (isBlacklisted(token)) {
            log.warn("[auth] token 已在黑名单中，拒绝访问");
            throw new UnauthorizedException("访问令牌已失效，请重新登录");
        }

        try {
            Claims claims = jwtTokenService.parseAccessToken(token);
            LoginUser loginUser = jwtTokenService.toLoginUser(claims);
            SecurityContextHolder.set(loginUser);
            // 把原始 Authorization 头透传给 Feign 调用
            FeignAuthHolder.set(auth, loginUser.getClient());
            return true;
        } catch (Exception e) {
            // 解析失败统一视为未认证，由全局异常处理器转 401
            log.warn("[auth] token 校验失败: {}", e.getMessage());
            throw new UnauthorizedException("访问令牌无效或已过期");
        }
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        // 必须清理，否则线程池复用会串号
        SecurityContextHolder.clear();
        FeignAuthHolder.clear();
    }

    /**
     * 检查 token 是否在黑名单中（已 logout）。
     * 若 Redis 未配置（RedisUtils 不可用），跳过黑名单检查。
     *
     * @param token 裸 access token
     * @return 在黑名单中返回 true
     */
    private boolean isBlacklisted(String token) {
        RedisUtils redisUtils = redisUtilsProvider.getIfAvailable();
        if (redisUtils == null) {
            return false;
        }
        String key = MallConstants.TOKEN_BLACKLIST_PREFIX + token;
        return Boolean.TRUE.equals(redisUtils.hasKey(key));
    }

    /**
     * 从 {@code Authorization} 头提取裸 token，去掉 {@code Bearer } 前缀。
     *
     * @param auth Authorization 头原值
     * @return 裸 token，格式不符返回 null
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
}
