package com.hyf.malladminservice.controller;

import com.hyf.malladminservice.dto.request.AdminLoginRequest;
import com.hyf.malladminservice.dto.response.AdminLoginResponse;
import com.hyf.malladminservice.entity.AdminUser;
import com.hyf.malladminservice.service.AdminAuthService;
import com.hyf.mallcommon.core.constant.MallConstants;
import com.hyf.mallcommon.core.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理后台 - 认证 Controller。
 *
 * <p>接口清单：
 * <ul>
 *   <li>POST /admin/login    —— 管理员登录（白名单，无需 token）</li>
 *   <li>GET  /admin/logout   —— 退出登录（写 Redis 黑名单）</li>
 *   <li>GET  /admin/profile  —— 当前登录管理员资料</li>
 *   <li>GET  /admin/health   —— 健康检查（白名单）</li>
 * </ul>
 *
 * <p>除 {@code /admin/login} 和 {@code /admin/health} 外，其他端点需经过网关 token 校验，
 * 网关会把 userId 透传到 {@code X-User-Id} 头。
 *
 * @author hyf
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    /** 健康检查 */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("service", "mall-admin-service");
        data.put("status", "UP");
        data.put("modules", "auth/product/seckill/user/sales");
        return Result.success(data);
    }

    /**
     * 管理员登录。
     *
     * @param req 登录请求（account + password）
     * @return 含 token 对的登录响应
     */
    @PostMapping("/login")
    public Result<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest req) {
        return Result.success(adminAuthService.login(req));
    }

    /**
     * 退出登录：将当前 access token 写入 Redis 黑名单。
     *
     * @param request HTTP 请求（用于提取 Authorization 头）
     * @return 成功无数据
     */
    @GetMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = extractBearer(request.getHeader(MallConstants.HEADER_AUTH));
        adminAuthService.logout(token);
        return Result.success();
    }

    /**
     * 当前登录管理员资料。
     *
     * @param userId 网关透传的 X-User-Id
     * @return 管理员实体（含 isAdmin 等字段）
     */
    @GetMapping("/profile")
    public Result<AdminUser> profile(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(adminAuthService.getCurrentAdmin(userId));
    }

    /** 从 Authorization 头提取裸 token（去 Bearer 前缀） */
    private String extractBearer(String auth) {
        if (auth == null || auth.isBlank() || !auth.startsWith(MallConstants.TOKEN_PREFIX)) {
            return null;
        }
        String token = auth.substring(MallConstants.TOKEN_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
