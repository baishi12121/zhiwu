package com.hyf.mallcommon.security.config;

import cn.dev33.satoken.stp.StpUtil;
import com.hyf.mallcommon.redis.utils.RedisUtils;
import com.hyf.mallcommon.security.interceptor.TokenAuthInterceptor;
import com.hyf.mallcommon.security.jwt.JwtTokenService;
import com.hyf.mallcommon.security.permission.PermissionStpInterface;
import com.hyf.mallcommon.security.properties.JwtProperties;
import com.hyf.mallcommon.security.properties.SaTokenProperties;
import com.hyf.mallcommon.security.satoken.JwtStpLogic;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 安全模块自动装配类。
 *
 * <p>被各业务 servlet 服务引入 mall-common-security 后自动生效（通过
 * {@code META-INF/spring/AutoConfiguration.imports} 注册），完成三件事：
 * <ol>
 *   <li>启用 {@link JwtProperties} / {@link SaTokenProperties} 配置绑定；</li>
 *   <li>注册自定义 {@link JwtStpLogic} 为全局 StpLogic，使 SaToken 的身份来源对接 JWT；</li>
 *   <li>把 {@link TokenAuthInterceptor} 挂到 MVC 拦截器链，对非白名单路径做 token 校验。</li>
 * </ol>
 *
 * <p>网关（WebFlux）不依赖本模块，其 token 校验单独用 jjwt 实现。
 *
 * @author hyf
 */
@AutoConfiguration
@EnableConfigurationProperties({JwtProperties.class, SaTokenProperties.class})
public class SecurityAutoConfiguration implements WebMvcConfigurer {

    /** SaToken 默认 loginType，与 StpUtil 内置一致 */
    private static final String DEFAULT_LOGIN_TYPE = "login";

    private final SaTokenProperties saTokenProperties;

    /** 用 ObjectProvider 延迟获取 JwtTokenService，打破 SecurityAutoConfiguration ↔ JwtTokenService 的循环依赖 */
    private final ObjectProvider<JwtTokenService> jwtTokenServiceProvider;

    /** TokenAuthInterceptor 单例（容器管理），通过 ObjectProvider 延迟获取避免循环依赖 */
    private final ObjectProvider<TokenAuthInterceptor> tokenAuthInterceptorProvider;

    public SecurityAutoConfiguration(SaTokenProperties saTokenProperties,
                                     ObjectProvider<JwtTokenService> jwtTokenServiceProvider,
                                     ObjectProvider<TokenAuthInterceptor> tokenAuthInterceptorProvider) {
        this.saTokenProperties = saTokenProperties;
        this.jwtTokenServiceProvider = jwtTokenServiceProvider;
        this.tokenAuthInterceptorProvider = tokenAuthInterceptorProvider;
    }

    /**
     * JWT 签发/解析服务，作为 token 核心。
     *
     * @param jwtProperties JWT 配置
     * @return token 服务
     */
    @Bean
    public JwtTokenService jwtTokenService(JwtProperties jwtProperties) {
        return new JwtTokenService(jwtProperties);
    }

    /**
     * 注册与 JWT 联动的 SaToken StpLogic。
     *
     * <p>设为全局后，{@code StpUtil.getLoginIdAsString()/isLogin()/checkPermission()} 等
     * 都基于当前 JWT 身份工作，权限/角色数据源由 {@link PermissionStpInterface} 提供。
     *
     * @return JWT 适配 StpLogic
     */
    @Bean
    public JwtStpLogic jwtStpLogic() {
        JwtStpLogic stpLogic = new JwtStpLogic(DEFAULT_LOGIN_TYPE);
        // 设为 StpUtil 的全局 StpLogic，覆盖 SaToken 默认实现
        StpUtil.setStpLogic(stpLogic);
        return stpLogic;
    }

    /**
     * SaToken 权限/角色数据源，占位返回空列表（待权限表落地）。
     *
     * @return 权限数据源实现
     */
    @Bean
    public PermissionStpInterface permissionStpInterface() {
        return new PermissionStpInterface();
    }

    /**
     * Token 鉴权拦截器，依赖 {@link JwtTokenService} 做本地验签，
     * 通过 {@link ObjectProvider}{@code <RedisUtils>} 可选的检查 token 黑名单。
     *
     * @param jwtTokenService    token 服务
     * @param redisUtilsProvider Redis 工具提供器（可选，未配置 Redis 时 getIfAvailable 返回 null）
     * @return token 拦截器
     */
    @Bean
    public TokenAuthInterceptor tokenAuthInterceptor(JwtTokenService jwtTokenService,
                                                       ObjectProvider<RedisUtils> redisUtilsProvider) {
        return new TokenAuthInterceptor(jwtTokenService, redisUtilsProvider);
    }

    /**
     * 注册拦截器：拦截所有请求，排除配置的白名单路径。
     *
     * <p>仅在 {@link SaTokenProperties#isEnabled()} 为 true 时挂载，便于本地调试关闭。
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (!saTokenProperties.isEnabled()) {
            return;
        }
        // 从容器获取单例拦截器，内部通过 ObjectProvider 处理 RedisUtils 可选依赖
        registry.addInterceptor(tokenAuthInterceptorProvider.getObject())
                .addPathPatterns("/**")
                .excludePathPatterns(saTokenProperties.getExcludePaths() == null
                        ? new String[0] : saTokenProperties.getExcludePaths());
    }
}
