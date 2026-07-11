package com.hyf.mallauthservice.controller;

import com.hyf.mallauthservice.dto.request.BindWechatPhoneByCodeRequest;
import com.hyf.mallauthservice.dto.request.BindWechatPhoneRequest;
import com.hyf.mallauthservice.dto.request.LoginRequest;
import com.hyf.mallauthservice.dto.request.RefreshTokenRequest;
import com.hyf.mallauthservice.dto.request.RegisterRequest;
import com.hyf.mallauthservice.dto.request.SmsLoginRequest;
import com.hyf.mallauthservice.dto.request.SmsSendRequest;
import com.hyf.mallauthservice.dto.request.WxLoginRequest;
import com.hyf.mallauthservice.dto.response.LoginResponse;
import com.hyf.mallauthservice.service.AuthService;
import com.hyf.mallcommon.core.constant.MallConstants;
import com.hyf.mallcommon.core.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证中心控制器。
 *
 * <p>对应 {@code doc/API接口文档.md} §2 的全部 7 个端点：
 * <ul>
 *   <li>POST /auth/login —— 密码登录</li>
 *   <li>POST /auth/sms/send —— 发送短信验证码</li>
 *   <li>POST /auth/sms/login —— 短信登录</li>
 *   <li>POST /auth/wxLogin —— 微信登录</li>
 *   <li>POST /auth/refreshToken —— 刷新 token</li>
 *   <li>GET  /auth/logout —— 退出登录</li>
 *   <li>POST /auth/register —— 注册</li>
 * </ul>
 *
 * <p>认证中心不存储用户资料（当前临时直连 DB 快速落地，后续迁回 Feign 调 user-service）。
 *
 * @author hyf
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 密码登录。
     *
     * @param req 登录请求（phone + password）
     * @return 含 token 对的登录响应
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        LoginResponse resp = authService.login(req);
        return Result.success(resp);
    }

    /**
     * 发送短信验证码。
     *
     * @param req 发送请求（phone）
     * @return 成功无数据
     */
    @PostMapping("/sms/send")
    public Result<Void> sendSmsCode(@Valid @RequestBody SmsSendRequest req) {
        authService.sendSmsCode(req);
        return Result.success();
    }

    /**
     * 短信验证码登录（骨架，待对接短信网关后启用）。
     *
     * @param req 短信登录请求（phone + code）
     * @return 含 token 对的登录响应
     */
    @PostMapping("/sms/login")
    public Result<LoginResponse> smsLogin(@Valid @RequestBody SmsLoginRequest req) {
        LoginResponse resp = authService.smsLogin(req);
        return Result.success(resp);
    }

    /**
     * 微信小程序登录（骨架，待对接微信 code2Session 后启用）。
     *
     * @param req 微信登录请求（code + nickname + avatar）
     * @return 含 token 对的登录响应
     */
    @PostMapping("/wxLogin")
    public Result<LoginResponse> wxLogin(@Valid @RequestBody WxLoginRequest req) {
        LoginResponse resp = authService.wxLogin(req);
        return Result.success(resp);
    }

    /**
     * 刷新 token。
     *
     * @param req 刷新请求（refreshToken）
     * @return 新的 token 对
     */
    @PostMapping("/refreshToken")
    public Result<LoginResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest req) {
        LoginResponse resp = authService.refreshToken(req);
        return Result.success(resp);
    }

    /**
     * 退出登录：将当前 access token 写入 Redis 黑名单，使其在有效期内不可再用。
     *
     * @param request HTTP 请求（用于提取 Authorization 头）
     * @return 成功无数据
     */
    @GetMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = extractBearer(request.getHeader(MallConstants.HEADER_AUTH));
        authService.logout(token);
        return Result.success();
    }

    /**
     * 从 Authorization 头提取裸 token（去 Bearer 前缀）。
     */
    private String extractBearer(String auth) {
        if (auth == null || auth.isBlank() || !auth.startsWith(MallConstants.TOKEN_PREFIX)) {
            return null;
        }
        String token = auth.substring(MallConstants.TOKEN_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * 用户注册，注册成功后自动登录。
     *
     * @param req 注册请求（account + password + mobile + nickname）
     * @return 含 token 对的登录响应
     */
    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest req) {
        LoginResponse resp = authService.register(req);
        return Result.success(resp);
    }

    /**
     * 微信新用户绑定手机号。
     *
     * <p>微信首次登录后调用，通过手机号完成注册或合并已有账号。
     * 该端点不走 token 拦截器（在 whitelist 中），通过 openid 识别用户。
     *
     * @param req 绑定请求（openid + phone）
     * @return 含 token 对的登录响应
     */
    @PostMapping("/bindWechatPhone")
    public Result<LoginResponse> bindWechatPhone(@Valid @RequestBody BindWechatPhoneRequest req) {
        LoginResponse resp = authService.bindWechatPhone(req);
        return Result.success(resp);
    }

    /**
     * 微信新用户绑定手机号（phoneCode 形式）。
     *
     * <p>前端通过 {@code <button open-type="getPhoneNumber">} 拿到加密的 phoneCode，
     * 由本接口统一解密为真实手机号后完成注册/合并。要求小程序后台已开通
     * 「手机号快速验证」或「手机号实时验证」能力。
     *
     * @param req 绑定请求（openid + phoneCode）
     * @return 含 token 对的登录响应
     */
    @PostMapping("/bindWechatPhoneByCode")
    public Result<LoginResponse> bindWechatPhoneByCode(@Valid @RequestBody BindWechatPhoneByCodeRequest req) {
        LoginResponse resp = authService.bindWechatPhoneByCode(req);
        return Result.success(resp);
    }
}
