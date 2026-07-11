package com.hyf.malluserservice.controller;

import com.hyf.mallcommon.core.result.Result;
import com.hyf.mallcommon.oss.service.OssService;
import com.hyf.malluserservice.dto.request.ProfileUpdateRequest;
import com.hyf.malluserservice.dto.response.ProfileResponse;
import com.hyf.malluserservice.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 用户域控制器。
 *
 * <p>提供用户资料读写与头像上传（上传至阿里云 OSS）。
 *
 * @author hyf
 */
@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final OssService ossService;

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(Map.of(
                "service", "mall-user-service",
                "status", "UP"
        ));
    }

    /**
     * 公开头像上传接口（用于微信登录前上传 chooseAvatar 返回的临时文件）。
     *
     * <p>不需要登录态：上传后仅返回 OSS URL，由前端在 wxLogin 时回传给 auth-service。
     *
     * @param file multipart/form-data 中的 file 字段
     * @return OSS 永久访问 URL
     */
    @PostMapping("/avatar/upload")
    public Result<Map<String, String>> uploadPublicAvatar(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error(400, "请选择文件");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            return Result.error(400, "文件大小不能超过 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.error(400, "仅支持图片格式");
        }

        String avatarUrl = ossService.upload(file);
        log.info("[user] 公开头像上传成功: url={}", avatarUrl);
        return Result.success(Map.of("avatar", avatarUrl));
    }

    /**
     * 获取当前登录用户的资料。
     */
    @GetMapping("/profile")
    public Result<ProfileResponse> getProfile() {
        return Result.success(userService.getProfile());
    }

    /**
     * 修改当前登录用户的资料。
     *
     * @param req 更新请求（所有字段可选）
     */
    @PutMapping("/profile")
    public Result<ProfileResponse> updateProfile(@RequestBody ProfileUpdateRequest req) {
        return Result.success(userService.updateProfile(req));
    }

    /**
     * 上传头像，返回 OSS 永久 URL。
     *
     * <p>前端以 {@code multipart/form-data} 上传，字段名为 {@code file}。
     * 返回格式：{@code { code: 200, message: "ok", data: { avatar: "url" } }}
     */
    @PostMapping("/profile/avatar")
    public Result<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error(400, "请选择文件");
        }

        // 限制文件大小 5MB
        if (file.getSize() > 5 * 1024 * 1024) {
            return Result.error(400, "文件大小不能超过 5MB");
        }

        // 限制文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return Result.error(400, "仅支持图片格式");
        }

        String avatarUrl = ossService.upload(file);
        log.info("[user] 头像上传成功: userId={}, url={}",
                com.hyf.mallcommon.security.context.SecurityContextHolder.getUserId(), avatarUrl);

        // 更新用户头像
        userService.updateAvatar(avatarUrl);

        return Result.success(Map.of("avatar", avatarUrl));
    }
}
