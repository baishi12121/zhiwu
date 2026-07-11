package com.hyf.mallcommon.security.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SaToken 鉴权相关配置属性。
 *
 * <p>本项目 token 的签发/校验由 jjwt 负责，SaToken 仅承担权限/角色鉴权，
 * 因此不使用 SaToken 自带的 token 配置（token-name/timeout 等），
 * 这里只提供业务侧的鉴权开关与白名单。
 *
 * <p>绑定 {@code application.yml} 中的 {@code mall.security.*}：
 * <pre>
 * mall:
 *   security:
 *     enabled: true                # 是否启用 token 拦截器（默认 true）
 *     exclude-paths:               # 拦截器白名单（Ant 风格）
 *       - /auth/**
 *       - /home/**
 * </pre>
 *
 * @author hyf
 */
@Data
@ConfigurationProperties(prefix = "mall.security")
public class SaTokenProperties {

    /** 是否启用 Token 拦截器，关闭后所有请求放行（便于本地调试） */
    private boolean enabled = true;

    /** 拦截器白名单路径（Ant 风格，如 {@code /auth/**}） */
    private String[] excludePaths = {};
}
