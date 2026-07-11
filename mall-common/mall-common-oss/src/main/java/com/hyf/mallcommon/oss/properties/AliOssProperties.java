package com.hyf.mallcommon.oss.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云 OSS 配置属性。
 *
 * <p>绑定 {@code application.yml} 中的 {@code alioss.*}，示例：
 * <pre>
 * alioss:
 *   endpoint: oss-cn-beijing.aliyuncs.com
 *   access-key-id: LTAI5t****
 *   access-key-secret: VeJrDS9****
 *   bucket-name: skyhyf
 * </pre>
 *
 * <p>生产环境建议通过环境变量覆盖密钥（{@code ALIOSS_ACCESS_KEY_ID} 等）。
 *
 * @author hyf
 */
@Data
@ConfigurationProperties(prefix = "alioss")
public class AliOssProperties {

    /** OSS 地域节点，如 oss-cn-beijing.aliyuncs.com */
    private String endpoint;

    /** 访问密钥 ID */
    private String accessKeyId;

    /** 访问密钥 Secret */
    private String accessKeySecret;

    /** Bucket 名称 */
    private String bucketName;

    /** 预签名下载 URL 有效期（秒），默认 3600（1 小时） */
    private long downloadExpireSeconds = 3600L;
}
