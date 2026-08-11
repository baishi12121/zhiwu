package com.hyf.mallcommon.oss.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.hyf.mallcommon.oss.controller.OssController;
import com.hyf.mallcommon.oss.properties.AliOssProperties;
import com.hyf.mallcommon.oss.service.OssService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 阿里云 OSS 自动装配。
 *
 * <p>通过 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 注册，服务引入本模块后自动生效。需要服务在 yml 中配置 {@code alioss:} 块。
 *
 * <p>装配内容：
 * <ol>
 *   <li>{@link OSS} 客户端 bean（destroyMethod=shutdown）；</li>
 *   <li>{@link OssService} —— 封装上传 / 预签名下载；</li>
 *   <li>{@link OssController} —— {@code /upload} 对外接口。</li>
 * </ol>
 *
 * <p>{@code @ConditionalOnProperty(bucket-name)} 保护：服务若不配 OSS，不创建任何 bean。
 *
 * @author hyf
 */
@AutoConfiguration
@EnableConfigurationProperties(AliOssProperties.class)
public class OssAutoConfiguration {

    /**
     * OSS 客户端，应用关闭时自动 shutdown。
     */
    @Bean(destroyMethod = "shutdown")
    @Conditional(OssConfiguredCondition.class)
    public OSS ossClient(AliOssProperties properties) {
        return new OSSClientBuilder().build(
                properties.getEndpoint(),
                new DefaultCredentialProvider(
                        properties.getAccessKeyId(),
                        properties.getAccessKeySecret()));
    }

    /**
     * OSS 文件服务：上传 / 预签名下载 / objectKey 反解。
     */
    @Bean
    public OssService ossService(ObjectProvider<OSS> ossClientProvider, AliOssProperties properties) {
        return new OssService(ossClientProvider.getIfAvailable(), properties);
    }

    /**
     * 文件上传 / 下载 REST 控制器。
     */
    @Bean
    public OssController ossController(OssService ossService) {
        return new OssController(ossService);
    }

    @Bean
    public WebMvcConfigurer ossLocalResourceConfigurer(AliOssProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                String prefix = normalizeUrlPrefix(properties.getLocalUrlPrefix());
                String location = Paths.get(properties.getLocalDir())
                        .toAbsolutePath()
                        .normalize()
                        .toUri()
                        .toString();
                registry.addResourceHandler(prefix + "/**")
                        .addResourceLocations(location);
            }
        };
    }

    private static String normalizeUrlPrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return "/uploads";
        }
        String normalized = prefix.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}

class OssConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        AliOssProperties properties = Binder.get(context.getEnvironment())
                .bind("alioss", Bindable.of(AliOssProperties.class))
                .orElse(null);
        return properties != null
                && StringUtils.hasText(properties.getEndpoint())
                && StringUtils.hasText(properties.getAccessKeyId())
                && StringUtils.hasText(properties.getAccessKeySecret())
                && StringUtils.hasText(properties.getBucketName());
    }
}
