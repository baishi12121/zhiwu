package com.hyf.mallcommon.security.context;

import com.hyf.mallcommon.security.jwt.LoginUser;

/**
 * 登录用户上下文持有者（ThreadLocal）。
 *
 * <p>{@link com.hyf.mallcommon.security.interceptor.TokenAuthInterceptor} 在请求开始时
 * 从 JWT 解析出 {@link LoginUser} 并写入本持有者，业务层（service/domain）通过
 * {@link #get()} 获取当前登录用户，请求结束时由拦截器 {@link #clear()} 清理。
 *
 * <p>设计动机：相比直接用 SaToken 的 StpUtil.getLoginIdAsString，这里保留一个
 * 框架无关的 ThreadLocal 入口，方便单测与领域层使用；SaToken 的自定义 StpLogic
 * 也会从这里读取身份，使两套体系共享同一份数据。
 *
 * @author hyf
 * @see com.hyf.mallcommon.security.interceptor.TokenAuthInterceptor
 */
public final class SecurityContextHolder {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private SecurityContextHolder() {
    }

    /**
     * 写入当前线程的登录用户。
     *
     * @param loginUser 登录用户
     */
    public static void set(LoginUser loginUser) {
        HOLDER.set(loginUser);
    }

    /**
     * 获取当前线程的登录用户。
     *
     * @return 登录用户，未登录时为 null
     */
    public static LoginUser get() {
        return HOLDER.get();
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @return 用户 ID，未登录时为 null
     */
    public static Long getUserId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    /**
     * 当前线程是否已登录。
     *
     * @return 已登录返回 true
     */
    public static boolean isLogin() {
        return HOLDER.get() != null;
    }

    /**
     * 清理当前线程的登录用户，防止线程复用导致的用户串号。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
