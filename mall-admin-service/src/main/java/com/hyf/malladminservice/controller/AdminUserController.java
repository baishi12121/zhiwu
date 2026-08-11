package com.hyf.malladminservice.controller;

import com.hyf.malladminservice.dto.request.UserLevelRequest;
import com.hyf.malladminservice.dto.request.UserStatusRequest;
import com.hyf.malladminservice.entity.AdminUser;
import com.hyf.malladminservice.service.AdminUserService;
import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;
import com.hyf.mallcommon.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - 用户管理 Controller。
 *
 * <p>接口清单：
 * <ul>
 *   <li>GET    /admin/users              —— 用户分页（keyword/status/memberLevel 筛选）</li>
 *   <li>GET    /admin/users/{id}         —— 用户详情</li>
 *   <li>PUT    /admin/users/{id}/status  —— 启停用户</li>
 *   <li>PUT    /admin/users/{id}/level   —— 调整会员等级</li>
 * </ul>
 *
 * @author hyf
 */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /** 用户分页 */
    @GetMapping
    public Result<PageResult<AdminUser>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String memberLevel,
            PageQuery pageQuery) {
        return Result.success(adminUserService.listUsers(pageQuery, keyword, status, memberLevel));
    }

    /** 用户详情 */
    @GetMapping("/{id}")
    public Result<AdminUser> detail(@PathVariable Long id) {
        return Result.success(adminUserService.getUser(id));
    }

    /** 启停用户 */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest req) {
        adminUserService.updateStatus(id, req.getStatus());
        return Result.success();
    }

    /** 调整会员等级 */
    @PutMapping("/{id}/level")
    public Result<Void> updateLevel(@PathVariable Long id, @Valid @RequestBody UserLevelRequest req) {
        adminUserService.updateMemberLevel(id, req.getMemberLevel());
        return Result.success();
    }
}
