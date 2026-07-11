package com.hyf.mallcommon.security.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 配置属性。
 *
 * <p>绑定 {@code application.yml} 中的 {@code mall.jwt.*}，由 auth-service 读取后
 * 构造 {@link com.hyf.mallcommon.security.jwt.JwtTokenService} 颁发/校验 token。
 *
 * <p>示例：
 * <pre>
 * mall:
 *   jwt:
 *     secret: zhiwu-mall-secret-change-me
 *     access-token-ttl: 1800     # 秒，30 分钟
 *     refresh-token-ttl: 604800  # 秒，7 天
 * </pre>
 *
 * @author hyf
 */
@Data
@ConfigurationProperties(prefix = "mall.jwt")
public class JwtProperties {

    /** 签名密钥，HMAC 算法要求足够长（建议 &gt;= 32 字节），生产环境务必通过环境变量覆盖 */
    private String secret = "zhiwu-mall-secret-please-change-in-production!";

    /** 访问令牌有效期，对应 API 文档 accessToken 30 分钟 */
    private Duration accessTokenTtl = Duration.ofMinutes(30);

    /** 刷新令牌有效期，对应 API 文档 refreshToken 7 天 */
    private Duration refreshTokenTtl = Duration.ofDays(7);

    /** token 签发者声明（iss claim） */
    private String issuer = "zhiwu-mall";
}
