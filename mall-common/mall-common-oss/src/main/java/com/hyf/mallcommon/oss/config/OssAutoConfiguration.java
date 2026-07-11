package com.hyf.mallcommon.oss.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.hyf.mallcommon.oss.controller.OssController;
import com.hyf.mallcommon.oss.properties.AliOssProperties;
import com.hyf.mallcommon.oss.service.OssService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

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
@ConditionalOnProperty(prefix = "alioss", name = "bucket-name")
public class OssAutoConfiguration {

    /**
     * OSS 客户端，应用关闭时自动 shutdown。
     */
    @Bean(destroyMethod = "shutdown")
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
    public OssService ossService(OSS ossClient, AliOssProperties properties) {
        return new OssService(ossClient, properties);
    }

    /**
     * 文件上传 / 下载 REST 控制器。
     */
    @Bean
    public OssController ossController(OssService ossService) {
        return new OssController(ossService);
    }
}
