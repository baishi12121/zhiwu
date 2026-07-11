package com.hyf.mallcommon.security.satoken;

import cn.dev33.satoken.stp.StpLogic;

/**
 * 与 JWT 联动的 SaToken 适配器 StpLogic。
 *
 * <p>本项目的 token 由 jjwt 签发/校验（见 {@link com.hyf.mallcommon.security.jwt.JwtTokenService}），
 * SaToken 不自管 token，仅承担权限/角色鉴权。因此需要让 SaToken 的身份来源
 * “看起来”是从它自己的 token 体系里读出来的——这里通过覆盖两个关键方法实现：
 *
 * <ul>
 *   <li>{@link #getLoginIdDefaultNull()} 直接读 {@link com.hyf.mallcommon.security.context.SecurityContextHolder}，
 *       该持有者由 {@link com.hyf.mallcommon.security.interceptor.TokenAuthInterceptor} 写入，
 *       从而让 {@code StpUtil.getLoginIdAsString()/isLogin()} 等基于“当前 JWT 身份”工作；</li>
 *   <li>{@link #getTokenValue()} 返回一个哨兵非空字符串，使 SaToken 判定为“已携带 token”，
 *       避免在 {@code getLoginIdDefaultNull} 内部因 token 为空而提前返回 null。</li>
 * </ul>
 *
 * <p>权限/角色校验（{@code checkPermission/checkRole}）依赖 {@code SaManager.getStpInterface()}
 * 查询权限码，不触碰 SaToken 的 session/storage，因此无需改写上下文。
 *
 * <p>构造时传入固定的 loginType（如 "login"），与默认 {@code StpUtil} 使用的 type 一致。
 * 该 bean 由 {@link com.hyf.mallcommon.security.config.SecurityAutoConfiguration} 注册并
 * 通过 {@code StpUtil.setStpLogic} 设为全局 StpLogic。
 *
 * <p>注意：本类仅用于“读”身份与鉴权，不实现 {@code login/logout} 等“写”操作——
 * 签发 token 走 {@link com.hyf.mallcommon.security.jwt.JwtTokenService}，登出走
 * 前端丢弃 token 即可（无状态 JWT 无法服务端主动失效，黑名单方案后续再议）。
 *
 * @author hyf
 */
public class JwtStpLogic extends StpLogic {

    /** 占位 token 值，使 SaToken 认为当前请求已携带 token */
    private static final String STUB_TOKEN = "jwt-bearer";

    /**
     * 构造适配器。
     *
     * @param loginType 登录类型，默认 "login"，须与 {@code StpUtil} 默认一致
     */
    public JwtStpLogic(String loginType) {
        super(loginType);
    }

    /**
     * 返回当前登录用户 ID，来源为 {@link com.hyf.mallcommon.security.context.SecurityContextHolder}。
     *
     * <p>未登录返回 null，{@code StpUtil.isLogin()} 等都依赖本方法的非空判定。
     *
     * @return 登录 ID，未登录返回 null
     */
    @Override
    public Object getLoginIdDefaultNull() {
        Long userId = com.hyf.mallcommon.security.context.SecurityContextHolder.getUserId();
        return userId == null ? null : String.valueOf(userId);
    }

    /**
     * 返回占位 token 值。
     *
     * <p>父类 {@code getLoginIdDefaultNull} 默认实现会先取 tokenValue，为空则直接返回 null；
     * 本类已直接覆盖 {@link #getLoginIdDefaultNull()} 返回真实身份，无需依赖 tokenValue，
     * 这里返回哨兵值仅为兼容 SaToken 内部个别仍读取 tokenValue 的路径，避免空指针。
     *
     * @return 占位 token 字符串
     */
    @Override
    public String getTokenValue() {
        return STUB_TOKEN;
    }
}

