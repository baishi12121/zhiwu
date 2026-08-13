package com.hyf.mallauthservice.service.impl;


import com.hyf.mallauthservice.service.AuthService;
import com.hyf.mallauthservice.dto.request.LoginRequest;
import com.hyf.mallauthservice.dto.request.RefreshTokenRequest;
import com.hyf.mallauthservice.dto.request.RegisterRequest;
import com.hyf.mallauthservice.dto.request.SmsLoginRequest;
import com.hyf.mallauthservice.dto.request.SmsSendRequest;
import com.hyf.mallauthservice.dto.request.BindWechatPhoneByCodeRequest;
import com.hyf.mallauthservice.dto.request.BindWechatPhoneRequest;
import com.hyf.mallauthservice.dto.request.WxLoginRequest;
import com.hyf.mallauthservice.dto.response.LoginResponse;
import com.hyf.mallauthservice.entity.User;
import com.hyf.mallauthservice.entity.UserAuth;
import com.hyf.mallauthservice.mapper.UserAuthMapper;
import com.hyf.mallauthservice.mapper.UserMapper;
import com.hyf.mallauthservice.properties.WeChatProperties;
import com.hyf.mallcommon.core.constant.MallConstants;
import com.hyf.mallcommon.core.util.HttpClientUtil;
import com.hyf.mallcommon.core.exception.BizException;
import com.hyf.mallcommon.core.result.ResultCode;
import com.hyf.mallcommon.redis.utils.RedisUtils;
import com.hyf.mallcommon.security.jwt.InvalidTokenException;
import com.hyf.mallcommon.security.jwt.JwtTokenService;
import com.hyf.mallcommon.security.jwt.LoginUser;
import com.hyf.mallcommon.security.jwt.TokenPair;
import com.hyf.mallcommon.security.properties.JwtProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 认证中心业务逻辑。
 *
 * <p>负责密码登录、注册、token 刷新、退出；短信登录与微信登录为骨架（待对接第三方）。
 * 密码使用 MD5 哈希与数据库中的种子数据保持一致，生产环境建议升级 BCrypt。
 *
 * @author hyf
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final UserAuthMapper userAuthMapper;
    private final JwtTokenService jwtTokenService;
    private final WeChatProperties weChatProperties;
    private final RedisUtils redisUtils;
    private final JwtProperties jwtProperties;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ==================== 密码登录 ====================

    /**
     * 手机号 + 密码登录。
     *
     * @param req 登录请求
     * @return 登录响应（含 token 对）
     * @throws BizException 手机号不存在 / 密码错误 / 账号被禁用
     */
    public LoginResponse login(LoginRequest req) {
        // 1. 查 user_auth(identity_type=PHONE, identifier=phone) 拿 userId
        UserAuth auth = userAuthMapper.selectByIdentity("PHONE", req.getPhone());
        if (auth == null) {
            log.warn("[auth] 登录失败——手机号未注册: {}", req.getPhone());
            throw new BizException(ResultCode.USER_AUTH_FAILED);
        }

        // 2. 查 user 表
        User user = userMapper.selectById(auth.getUserId());
        if (user == null || user.getStatus() != 1) {
            log.warn("[auth] 登录失败——用户不存在或已禁用: userId={}", auth.getUserId());
            throw new BizException(ResultCode.USER_AUTH_FAILED);
        }

        // 3. 验密（MD5），密码存在 user 表
        if (user.getPassword() == null) {
            log.warn("[auth] 登录失败——用户未设置密码: userId={}", user.getId());
            throw new BizException(ResultCode.USER_AUTH_FAILED);
        }
        String encrypted = DigestUtils.md5DigestAsHex(req.getPassword().getBytes(StandardCharsets.UTF_8));
        if (!encrypted.equalsIgnoreCase(user.getPassword())) {
            log.warn("[auth] 登录失败——密码错误: phone={}", req.getPhone());
            throw new BizException(ResultCode.USER_AUTH_FAILED);
        }

        // 4. 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("[auth] 登录成功: userId={}, phone={}", user.getId(), req.getPhone());
        return buildLoginResponse(user);
    }

    // ==================== 注册 ====================

    /**
     * 用户注册，注册成功后自动登录。
     *
     * @param req 注册请求
     * @return 登录响应（含 token 对）
     * @throws BizException 用户名或手机号已存在
     */
    @Transactional
    public LoginResponse register(RegisterRequest req) {
        // 1. 校验账号是否已占用
        UserAuth existAuth = userAuthMapper.selectByIdentity("USERNAME", req.getAccount());
        if (existAuth != null) {
            throw new BizException(ResultCode.USER_EXISTS);
        }

        // 2. 校验手机号是否已占用
        User existUser = userMapper.selectByMobile(req.getMobile());
        if (existUser != null) {
            throw new BizException(ResultCode.USER_EXISTS);
        }

        // 3. 写 user 表
        User user = new User();
        user.setAccount(req.getAccount());
        user.setNickname(req.getNickname());
        user.setPassword(DigestUtils.md5DigestAsHex(req.getPassword().getBytes(StandardCharsets.UTF_8)));
        user.setMobile(req.getMobile());
        user.setGender(0);
        user.setMemberLevel("NORMAL");
        user.setStatus(1);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.insert(user);

        // 4. 写 user_auth（USERNAME + PHONE）
        UserAuth usernameAuth = new UserAuth();
        usernameAuth.setUserId(user.getId());
        usernameAuth.setIdentityType("USERNAME");
        usernameAuth.setIdentifier(req.getAccount());
        usernameAuth.setCredential(user.getPassword());
        userAuthMapper.insert(usernameAuth);

        UserAuth phoneAuth = new UserAuth();
        phoneAuth.setUserId(user.getId());
        phoneAuth.setIdentityType("PHONE");
        phoneAuth.setIdentifier(req.getMobile());
        phoneAuth.setCredential(null);
        userAuthMapper.insert(phoneAuth);

        log.info("[auth] 注册成功: userId={}, account={}", user.getId(), user.getAccount());
        return buildLoginResponse(user);
    }

    // ==================== Token 刷新 ====================

    /**
     * 用 refresh token 换发新的 token 对。
     *
     * @param req 刷新请求
     * @return 新的 token 对
     * @throws BizException refresh token 非法或过期
     */
    public LoginResponse refreshToken(RefreshTokenRequest req) {
        try {
            TokenPair tokenPair = jwtTokenService.refresh(req.getRefreshToken());
            // 从 refresh token 中解析用户信息
            LoginUser loginUser = jwtTokenService.toLoginUser(
                    jwtTokenService.parseAccessToken(tokenPair.getAccessToken()));
            return LoginResponse.builder()
                    .userId(loginUser.getUserId())
                    .nickname(loginUser.getNickname())
                    .avatar(loginUser.getAvatar())
                    .memberLevel(loginUser.getMemberLevel())
                    .accessToken(tokenPair.getAccessToken())
                    .refreshToken(tokenPair.getRefreshToken())
                    .expiresIn(tokenPair.getExpiresIn())
                    .build();
        } catch (InvalidTokenException e) {
            log.warn("[auth] refresh token 无效: {}", e.getMessage());
            throw new BizException(ResultCode.TOKEN_INVALID);
        }
    }

    // ==================== 退出 ====================

    /**
     * 退出登录：将当前 access token 写入 Redis 黑名单，TTL 对齐 token 有效期。
     *
     * <p>黑名单 key 格式：{@code token:blacklist:<token>}，value 为 "1"。
     * TokenAuthInterceptor 在每次校验时检查黑名单，命中则拒绝。
     *
     * @param accessToken 当前请求的 access token（已去除 Bearer 前缀）
     */
    public void logout(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            log.warn("[auth] 退出登录——token 为空，跳过黑名单写入");
            return;
        }
        String blacklistKey = MallConstants.TOKEN_BLACKLIST_PREFIX + accessToken;
        // TTL 对齐 access token 有效期，过期后 Redis 自动清理
        long ttlSeconds = jwtProperties.getAccessTokenTtl().getSeconds();
        redisUtils.stringSet(blacklistKey, "1", ttlSeconds);
        log.info("[auth] 退出登录——token 已加入黑名单, ttl={}s", ttlSeconds);
    }

    // ==================== 短信 ====================

    /**
     * 发送短信验证码（骨架）。
     *
     * <p>当前为 mock 实现；对接真实短信网关后补充发送逻辑与 Redis 暂存。
     *
     * @param req 发送请求
     */
    public void sendSmsCode(SmsSendRequest req) {
        log.info("[auth] 发送短信验证码（mock）: mobile={}", req.getMobile());
        // TODO: 对接真实短信网关，验证码存入 Redis（5 分钟过期）
    }

    /**
     * 短信验证码登录（骨架）。
     *
     * <p>当前未实现；需 Redis 校验验证码后查询或创建用户。
     *
     * @param req 短信登录请求
     * @return 登录响应
     */
    public LoginResponse smsLogin(SmsLoginRequest req) {
        log.info("[auth] 短信登录（mock）: mobile={}", req.getMobile());
        // TODO: 1. 从 Redis 校验验证码
        //       2. 查 user_auth(identity_type=PHONE) → user
        //       3. 若用户不存在则自动注册
        //       4. 签发 token
        throw new BizException(ResultCode.INTERNAL_ERROR, "短信登录暂未实现");
    }

    // ==================== 微信 ====================

    /**
     * 微信小程序登录。
     *
     * <p>通过微信 code2Session 获取 openid，首次登录自动注册并返回 needBindPhone。
     * 已绑定过的用户直接签发 token。
     *
     * @param code 微信登录请求
     * @return 登录响应
     */

    public String getOpenId(String code) {
        Map<String, String> params = Map.of(
                "appid", weChatProperties.getAppid(),
                "secret", weChatProperties.getSecret(),
                "js_code", code,
                "grant_type", "authorization_code"
        );
        try {
            String responseJson = HttpClientUtil.doGet(weChatProperties.getCode2SessionUrl(), params);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = OBJECT_MAPPER.readValue(responseJson, Map.class);
            // 微信返回 errcode=0 或没有 errcode 表示成功
            if (result.containsKey("errcode") && (int) result.get("errcode") != 0) {
                int errcode = (int) result.get("errcode");
                String errmsg = String.valueOf(result.getOrDefault("errmsg", "未知错误"));
                log.error("[auth] 微信 code2Session 失败: errcode={}, errmsg={}, raw={}", errcode, errmsg, responseJson);
                throw new BizException(ResultCode.INTERNAL_ERROR,
                        "微信登录失败 [" + errcode + "]: " + errmsg);
            }
            String openid = (String) result.get("openid");
            if (openid == null || openid.isEmpty()) {
                log.error("[auth] 微信 code2Session 未返回 openid: {}", responseJson);
                throw new BizException(ResultCode.INTERNAL_ERROR, "微信登录失败：未获取到 openid");
            }
            log.info("[auth] 微信 code2Session 成功: openid={}", openid);
            return openid;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[auth] 微信 code2Session 请求异常: {}", e.getMessage(), e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "微信登录失败: " + e.getMessage());
        }
    }

    @Transactional
    public LoginResponse wxLogin(WxLoginRequest req) {
        // 1. 调微信 code2Session 拿 openid
        String openid = getOpenId(req.getCode());

        // 2. 查 user_auth(identity_type=WECHAT, identifier=openid)
        UserAuth auth = userAuthMapper.selectByIdentity("WECHAT", openid);
        User user;

        if (auth == null) {
            // 3. 不存在：自动注册临时用户（不签发 token，需要先绑定手机号）
            user = new User();
            user.setNickname(StringUtils.hasText(req.getNickname()) ? req.getNickname() : "微信用户");
            user.setAvatar(req.getAvatar());
            user.setGender(0);
            user.setMemberLevel("NORMAL");
            user.setStatus(1);
            user.setLastLoginAt(LocalDateTime.now());
            userMapper.insert(user);

            UserAuth wechatAuth = new UserAuth();
            wechatAuth.setUserId(user.getId());
            wechatAuth.setIdentityType("WECHAT");
            wechatAuth.setIdentifier(openid);
            wechatAuth.setCredential(null);
            userAuthMapper.insert(wechatAuth);

            log.info("[auth] 微信自动注册——需绑定手机号: userId={}, openid={}", user.getId(), openid);

            // 不签发 token，返回 openid 供后续绑定使用
            return LoginResponse.builder()
                    .userId(user.getId())
                    .nickname(user.getNickname())
                    .avatar(user.getAvatar())
                    .memberLevel(user.getMemberLevel())
                    .needBindPhone(true)
                    .openid(openid)
                    .build();
        }

        // 4. 已存在：不直接签发 token，统一走手机号绑定流程
        user = userMapper.selectById(auth.getUserId());
        if (user == null || user.getStatus() != 1) {
            log.warn("[auth] 微信登录失败——用户不存在或已禁用: userId={}", auth.getUserId());
            throw new BizException(ResultCode.USER_AUTH_FAILED);
        }
        // 仅在有实质内容时更新，避免空串覆盖已有昵称/头像
        if (StringUtils.hasText(req.getNickname())) {
            user.setNickname(req.getNickname());
        }
        if (StringUtils.hasText(req.getAvatar())) {
            user.setAvatar(req.getAvatar());
        }
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("[auth] 微信老用户——需验证手机号后登录: userId={}, openid={}, mobile={}",
                user.getId(), openid, user.getMobile());

        // 不签发 token，返回 openid + 已有手机号供前端展示
        return LoginResponse.builder()
                .userId(user.getId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .memberLevel(user.getMemberLevel())
                .mobile(user.getMobile())
                .needBindPhone(true)
                .openid(openid)
                .build();
    }

    // ==================== 微信绑定手机号 ====================

    /**
     * 微信新用户绑定手机号，完成注册或合并已有账号。
     *
     * <p>三种场景：
     * <ol>
     *   <li>手机号匹配到另一个已有用户 → 转移 WECHAT 凭证 + 删除临时用户 + 为已有用户签发 token；</li>
     *   <li>手机号匹配到临时用户自身 → 绑定 phone + 签发 token；</li>
     *   <li>手机号不匹配任何人 → 绑定 phone 到临时用户 + 签发 token。</li>
     * </ol>
     *
     * @param req 绑定请求（openid + phone）
     * @return 完整的登录响应（含 token 对）
     */
    @Transactional
    public LoginResponse bindWechatPhone(BindWechatPhoneRequest req) {
        // 1. 查 WECHAT 凭证 → 拿到自动注册的临时用户
        UserAuth wechatAuth = userAuthMapper.selectByIdentity("WECHAT", req.getOpenid());
        if (wechatAuth == null) {
            log.warn("[auth] bindWechatPhone 失败——无效的 openid: {}", req.getOpenid());
            throw new BizException(ResultCode.BAD_REQUEST, "无效的绑定请求");
        }
        User tempUser = userMapper.selectById(wechatAuth.getUserId());
        if (tempUser == null) {
            log.warn("[auth] bindWechatPhone 失败——临时用户不存在: userId={}", wechatAuth.getUserId());
            throw new BizException(ResultCode.BAD_REQUEST, "无效的绑定请求");
        }

        // 2. 查手机号是否已有用户
        User existUser = userMapper.selectByMobile(req.getPhone());

        if (existUser != null && !existUser.getId().equals(tempUser.getId())) {
            // 场景 A: 手机号匹配到另一个已有用户 → 账号合并
            log.info("[auth] bindWechatPhone 账号合并: tempUserId={}, existUserId={}, openid={}",
                    tempUser.getId(), existUser.getId(), req.getOpenid());

            // 把 WECHAT 凭证转移到已有用户
            wechatAuth.setUserId(existUser.getId());
            userAuthMapper.updateById(wechatAuth);

            // 删除临时用户
            userMapper.deleteById(tempUser.getId());

            // 为已有用户更新最后登录时间并签发 token
            existUser.setLastLoginAt(LocalDateTime.now());
            userMapper.updateById(existUser);

            return buildLoginResponse(existUser);
        }

        // 场景 B/C: 绑定 phone 到临时用户
        tempUser.setMobile(req.getPhone());
        userMapper.updateById(tempUser);

        // 添加 PHONE 凭证（如已存在则跳过）
        UserAuth existPhone = userAuthMapper.selectByIdentity("PHONE", req.getPhone());
        if (existPhone == null) {
            UserAuth phoneAuth = new UserAuth();
            phoneAuth.setUserId(tempUser.getId());
            phoneAuth.setIdentityType("PHONE");
            phoneAuth.setIdentifier(req.getPhone());
            userAuthMapper.insert(phoneAuth);
        }

        log.info("[auth] bindWechatPhone 绑定成功: userId={}, phone={}, openid={}",
                tempUser.getId(), req.getPhone(), req.getOpenid());
        return buildLoginResponse(tempUser);
    }

    // ==================== 微信 getPhoneNumber（phoneCode 解密） ====================

    /**
     * 微信 access_token 缓存前缀到期的安全余量（5 分钟）。
     *
     * <p>微信官方 access_token 有效期 7200 秒；为避免临界过期导致解密失败，
     * 在缓存剩余 TTL 小于该余量时主动刷新。
     */
    private static final long ACCESS_TOKEN_SAFE_MARGIN_SECONDS = 300;

    /**
     * 拿微信 access_token，优先从 Redis 缓存取，过期或缺失时调 cgi-bin/token 刷新。
     *
     * @return access_token
     * @throws BizException 微信接口返回 errcode != 0 时抛错
     */
    public String getAccessToken() {
        String cacheKey = weChatProperties.getAccessTokenCacheKey();

        // 1. 命中缓存且剩余 TTL > 安全余量 → 直接返回
        String cached = redisUtils.stringGet(cacheKey);
        Long ttl = redisUtils.strGetExpire(cacheKey);
        if (cached != null && ttl != null && ttl > ACCESS_TOKEN_SAFE_MARGIN_SECONDS) {
            log.debug("[auth] access_token 命中缓存, ttl={}s", ttl);
            return cached;
        }

        // 2. 缓存缺失/快过期 → 调微信刷新
        Map<String, String> params = Map.of(
                "grant_type", "client_credential",
                "appid", weChatProperties.getAppid(),
                "secret", weChatProperties.getSecret()
        );
        try {
            String responseJson = HttpClientUtil.doGet(weChatProperties.getAccessTokenUrl(), params);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = OBJECT_MAPPER.readValue(responseJson, Map.class);
            if (result.containsKey("errcode") && (int) result.get("errcode") != 0) {
                int errcode = (int) result.get("errcode");
                String errmsg = String.valueOf(result.getOrDefault("errmsg", "未知错误"));
                log.error("[auth] 获取 access_token 失败: errcode={}, errmsg={}, raw={}", errcode, errmsg, responseJson);
                throw new BizException(ResultCode.INTERNAL_ERROR,
                        "获取 access_token 失败 [" + errcode + "]: " + errmsg);
            }
            String accessToken = (String) result.get("access_token");
            Integer expiresIn = (Integer) result.get("expires_in");
            if (accessToken == null || expiresIn == null) {
                log.error("[auth] 微信 access_token 响应异常: {}", responseJson);
                throw new BizException(ResultCode.INTERNAL_ERROR, "获取 access_token 失败：响应异常");
            }
            // 写入 Redis，TTL 取微信返回值（已默认 7200s，再留 60s 余量由上面的 margin 处理）
            redisUtils.stringSet(cacheKey, accessToken, expiresIn);
            log.info("[auth] access_token 刷新成功, expiresIn={}s", expiresIn);
            return accessToken;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[auth] 获取 access_token 请求异常: {}", e.getMessage(), e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "获取 access_token 失败: " + e.getMessage());
        }
    }

    /**
     * 用 phoneCode + access_token 调微信 getuserphonenumber 拿真实手机号。
     *
     * <p>接口文档：{@code POST /wxa/business/getuserphonenumber?access_token=...}，
     * body 为 {@code {"code":"<phoneCode>"}}，成功返回
     * {@code phone_info.phoneNumber}。
     *
     * @param phoneCode 前端 getPhoneNumber 拿到的加密 code
     * @return 真实手机号
     * @throws BizException 微信返回 errcode != 0 时抛错
     */
    public String decryptPhoneNumber(String phoneCode) {
        String accessToken = getAccessToken();
        String url = weChatProperties.getGetPhoneNumberUrl() + "?access_token=" + accessToken;

        try {
            String responseJson = HttpClientUtil.doPost4Json(url, Map.of("code", phoneCode));
            @SuppressWarnings("unchecked")
            Map<String, Object> result = OBJECT_MAPPER.readValue(responseJson, Map.class);
            if (result.containsKey("errcode") && (int) result.get("errcode") != 0) {
                int errcode = (int) result.get("errcode");
                String errmsg = String.valueOf(result.getOrDefault("errmsg", "未知错误"));
                log.error("[auth] 解密手机号失败: errcode={}, errmsg={}, phoneCode prefix={}, raw={}",
                        errcode, errmsg, phoneCode.substring(0, Math.min(8, phoneCode.length())), responseJson);
                throw new BizException(ResultCode.INTERNAL_ERROR,
                        "解密手机号失败 [" + errcode + "]: " + errmsg);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> phoneInfo = (Map<String, Object>) result.get("phone_info");
            if (phoneInfo == null) {
                log.error("[auth] 解密手机号响应无 phone_info: {}", responseJson);
                throw new BizException(ResultCode.INTERNAL_ERROR, "解密手机号失败：响应异常");
            }
            String phone = (String) phoneInfo.get("phoneNumber");
            if (phone == null || phone.isEmpty()) {
                log.error("[auth] 解密手机号响应无 phoneNumber: {}", responseJson);
                throw new BizException(ResultCode.INTERNAL_ERROR, "解密手机号失败：未拿到手机号");
            }
            log.info("[auth] 解密手机号成功");
            return phone;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[auth] 解密手机号请求异常: {}", e.getMessage(), e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "解密手机号失败: " + e.getMessage());
        }
    }

    /**
     * 微信新用户绑定手机号（phoneCode 形式）。
     *
     * <p>流程：phoneCode → 调微信 getuserphonenumber 解密 → 拿到真实手机号 →
     * 复用 {@link #bindWechatPhone} 的合并/绑定逻辑。
     *
     * @param req 绑定请求（openid + phoneCode）
     * @return 完整的登录响应（含 token 对）
     */
    @Transactional
    public LoginResponse bindWechatPhoneByCode(BindWechatPhoneByCodeRequest req) {
        String phone = decryptPhoneNumber(req.getPhoneCode());
        log.info("[auth] bindWechatPhoneByCode 解密成功 openid={} phone={}", req.getOpenid(), phone);
        BindWechatPhoneRequest inner = new BindWechatPhoneRequest();
        inner.setOpenid(req.getOpenid());
        inner.setPhone(phone);
        return bindWechatPhone(inner);
    }

    // ==================== 内部工具 ====================

    /**
     * 根据用户实体生成登录响应。
     */
    private LoginResponse buildLoginResponse(User user) {
        LoginUser loginUser = LoginUser.builder()
                .userId(user.getId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .memberLevel(user.getMemberLevel())
                .client(null) // 暂不区分客户端
                .build();

        TokenPair tokenPair = jwtTokenService.createTokenPair(loginUser);

        return LoginResponse.builder()
                .userId(user.getId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .memberLevel(user.getMemberLevel())
                .mobile(user.getMobile()) // 已绑定的手机号（可能为 null）
                .accessToken(tokenPair.getAccessToken())
                .refreshToken(tokenPair.getRefreshToken())
                .expiresIn(tokenPair.getExpiresIn())
                .build();
    }
}
