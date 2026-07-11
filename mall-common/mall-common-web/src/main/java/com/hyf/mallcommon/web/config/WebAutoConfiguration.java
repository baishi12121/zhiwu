package com.hyf.mallcommon.web.config;

import com.hyf.mallcommon.web.handler.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * mall-common-web 自动装配。
 *
 * <p>通过 {@code META-INF/spring/…AutoConfiguration.imports} 被各业务服务自动加载，
 * 无需在启动类上显式 {@code @ComponentScan} 扫描此包。
 *
 * @author hyf
 */
@AutoConfiguration
@Import({GlobalExceptionHandler.class, CorsConfig.class})
public class WebAutoConfiguration {
}
