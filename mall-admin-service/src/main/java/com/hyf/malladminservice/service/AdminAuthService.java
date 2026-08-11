package com.hyf.malladminservice.service;

import com.hyf.malladminservice.dto.request.AdminLoginRequest;
import com.hyf.malladminservice.dto.response.AdminLoginResponse;
import com.hyf.malladminservice.entity.AdminUser;
import com.hyf.malladminservice.mapper.AdminUserMapper;
import com.hyf.mallcommon.core.constant.MallConstants;
import com.hyf.mallcommon.core.exception.BizException;
import com.hyf.mallcommon.core.result.ResultCode;
import com.hyf.mallcommon.redis.utils.RedisUtils;
import com.hyf.mallcommon.security.jwt.JwtTokenService;
import com.hyf.mallcommon.security.jwt.LoginUser;
import com.hyf.mallcommon.security.jwt.TokenPair;
import com.hyf.mallcommon.security.properties.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 管理员登录业务逻辑。
 *
 * <p>校验账号密码 + is_admin=1 后签发 JWT；退出时把 access token 写入 Redis 黑名单
 * （key 形如 {@code token:blacklist:<token>}，TTL 对齐 token 有效期），
 * 由 mall-common-security 的 TokenAuthInterceptor 在每次请求时检查。
 *
 * @author hyf
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminUserMapper adminUserMapper;
    private final JwtTokenService jwtTokenService;
    private final RedisUtils redisUtils;
    private final JwtProperties jwtProperties;

    /**
     * 管理员登录。
     *
     * @param req 登录请求
     * @return 含 token 对的登录响应
     * @throws BizException 账号不存在 / 密码错误 / 非管理员 / 账号被禁用
     */
    public AdminLoginResponse login(AdminLoginRequest req) {
        // 1. 按账号查正常状态用户
        AdminUser user = adminUserMapper.selectByAccount(req.getAccount());
        if (user == null) {
            log.warn("[admin-auth] 登录失败——账号不存在: {}", req.getAccount());
            throw new BizException(ResultCode.USER_AUTH_FAILED);
        }

        // 2. 校验管理员身份
        if (user.getIsAdmin() == null || user.getIsAdmin() != 1) {
            log.warn("[admin-auth] 登录失败——非管理员账号: userId={}, account={}", user.getId(), req.getAccount());
            throw new BizException(ResultCode.FORBIDDEN, "无管理员权限");
        }

        // 3. 校验密码（MD5，与 auth-service 一致）
        if (user.getPassword() == null) {
            log.warn("[admin-auth] 登录失败——未设置密码: userId={}", user.getId());
            throw new BizException(ResultCode.USER_AUTH_FAILED);
        }
        String encrypted = DigestUtils.md5DigestAsHex(req.getPassword().getBytes(StandardCharsets.UTF_8));
        if (!encrypted.equalsIgnoreCase(user.getPassword())) {
            log.warn("[admin-auth] 登录失败——密码错误: account={}", req.getAccount());
            throw new BizException(ResultCode.USER_AUTH_FAILED);
        }

        // 4. 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        adminUserMapper.updateById(user);

        log.info("[admin-auth] 登录成功: userId={}, account={}", user.getId(), req.getAccount());
        return buildLoginResponse(user);
    }

    /**
     * 退出登录：把 access token 写入 Redis 黑名单。
     *
     * @param accessToken 裸 access token（已去 Bearer 前缀）
     */
    public void logout(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            log.warn("[admin-auth] 退出登录——token 为空，跳过黑名单写入");
            return;
        }
        String blacklistKey = MallConstants.TOKEN_BLACKLIST_PREFIX + accessToken;
        long ttlSeconds = jwtProperties.getAccessTokenTtl().getSeconds();
        redisUtils.stringSet(blacklistKey, "1", ttlSeconds);
        log.info("[admin-auth] 退出登录——token 已加入黑名单, ttl={}s", ttlSeconds);
    }

    /**
     * 根据 X-User-Id 拉取当前登录管理员资料。
     *
     * @param userId 网关透传的用户 ID
     * @return 管理员实体
     * @throws BizException 用户不存在
     */
    public AdminUser getCurrentAdmin(Long userId) {
        AdminUser user = adminUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "管理员不存在");
        }
        return user;
    }

    // ==================== 内部工具 ====================

    private AdminLoginResponse buildLoginResponse(AdminUser user) {
        LoginUser loginUser = LoginUser.builder()
                .userId(user.getId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .memberLevel(user.getMemberLevel())
                .client("admin")
                .build();

        TokenPair tokenPair = jwtTokenService.createTokenPair(loginUser);

        return AdminLoginResponse.builder()
                .userId(user.getId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .memberLevel(user.getMemberLevel())
                .accessToken(tokenPair.getAccessToken())
                .refreshToken(tokenPair.getRefreshToken())
                .expiresIn(tokenPair.getExpiresIn())
                .build();
    }
}
