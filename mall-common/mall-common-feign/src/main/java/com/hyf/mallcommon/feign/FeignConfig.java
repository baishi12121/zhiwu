package com.hyf.mallcommon.feign;

import com.hyf.mallcommon.core.constant.MallConstants;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 通用配置
 *
 * <p>把当前请求的 {@code Authorization} / {@code source-client} 头透传到下游服务。
 *
 * @author hyf
 */
@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> {
            // 让 Feign 调用也带上网关颁发的 token（由 SecurityContext 提供，待落地）
            String auth = FeignAuthHolder.getAuth();
            if (auth != null && !auth.isBlank()) {
                template.header(MallConstants.HEADER_AUTH, auth);
            }
            String client = FeignAuthHolder.getClient();
            if (client != null && !client.isBlank()) {
                template.header(MallConstants.HEADER_SOURCE_CLIENT, client);
            }
        };
    }
}
