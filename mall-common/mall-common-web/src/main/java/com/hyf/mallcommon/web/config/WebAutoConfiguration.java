package com.hyf.mallcommon.web.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.hyf.mallcommon.web.handler.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.math.BigInteger;

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

    /**
     * JavaScript cannot safely represent Snowflake IDs as numbers.
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance)
                .serializerByType(BigInteger.class, ToStringSerializer.instance);
    }
}
