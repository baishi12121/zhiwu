package com.hyf.mallcommon.security.permission;

import cn.dev33.satoken.stp.StpInterface;

import java.util.Collections;
import java.util.List;

/**
 * SaToken 权限/角色数据源（占位实现）。
 *
 * <p>实现 {@link StpInterface}，供 {@code StpUtil.checkPermission/checkRole} 查询
 * 当前用户的权限码与角色码。本项目权限表尚未落地，这里先返回空列表，
 * 即默认“无任何权限/角色”——意味着带 {@code @SaCheckPermission} 的接口当前会拒绝。
 *
 * <p>TODO：待权限表（user_role / role_permission）设计落地后，根据
 * {@code StpUtil.getLoginIdAsString()} 查库返回真实权限码，例如：
 * <pre>{@code
 * public List<String> getPermissionList(Object loginId, String loginType) {
 *     return permissionMapper.selectPermissionsByUserId(Long.valueOf(loginId.toString()));
 * }
 * }</pre>
 *
 * @author hyf
 */
public class PermissionStpInterface implements StpInterface {

    /**
     * 查询某登录用户的权限码列表。
     *
     * @param loginId   登录 ID
     * @param loginType 登录类型
     * @return 权限码列表，占位返回空
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // TODO: 接入权限表后按 userId 查询真实权限码
        return Collections.emptyList();
    }

    /**
     * 查询某登录用户的角色码列表。
     *
     * @param loginId   登录 ID
     * @param loginType 登录类型
     * @return 角色码列表，占位返回空
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // TODO: 接入角色表后按 userId 查询真实角色码
        return Collections.emptyList();
    }
}
