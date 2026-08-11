package com.hyf.mallcommon.oss.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.model.ObjectMetadata;
import com.hyf.mallcommon.core.exception.BizException;
import com.hyf.mallcommon.core.result.ResultCode;
import com.hyf.mallcommon.oss.properties.AliOssProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.UUID;

/**
 * 阿里云 OSS 文件服务。
 *
 * <p>封装上传 / 预签名下载 / objectKey 反解等操作，由 {@code OssAutoConfiguration} 装配。
 * 上传返回可公开访问的 HTTPS URL；下载生成有时效的预签名链接，客户端直连 OSS 不经过本服务。
 *
 * @author hyf
 */
@Slf4j
public class OssService {

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OSS oss;
    private final AliOssProperties properties;

    public OssService(OSS oss, AliOssProperties properties) {
        this.oss = oss;
        this.properties = properties;
    }

    // ===================== 上传 =====================

    /**
     * 上传 {@link MultipartFile} 到 OSS，返回可公开访问的 HTTPS URL。
     *
     * @param file 前端上传的文件
     * @return 文件访问 URL（形如 {@code https://skyhyf.oss-cn-beijing.aliyuncs.com/20260710/uuid.jpg}）
     * @throws BizException 文件为空或上传失败
     */
    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        String objectKey = buildObjectKey(originalName);
        try (InputStream in = file.getInputStream()) {
            doUpload(objectKey, in, file.getContentType(), file.getSize());
            return buildUrl(objectKey);
        } catch (OSSException | ClientException e) {
            log.error("[oss] 上传失败 objectKey={}", objectKey, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "文件上传失败", e);
        } catch (IOException e) {
            log.error("[oss] 读取上传流失败 {}", originalName, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "文件上传失败", e);
        }
    }

    /**
     * 上传字节数组到 OSS（供非 multipart 场景，如 base64 / Feign 内部调用）。
     *
     * @param bytes            文件字节
     * @param originalFilename 原始文件名（用于推断扩展名）
     * @return 文件访问 URL
     */
    public String upload(byte[] bytes, String originalFilename) {
        if (bytes == null || bytes.length == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件不能为空");
        }
        String objectKey = buildObjectKey(originalFilename);
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            doUpload(objectKey, in, null, bytes.length);
            return buildUrl(objectKey);
        } catch (OSSException | ClientException e) {
            log.error("[oss] 上传失败 objectKey={}", objectKey, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "文件上传失败", e);
        } catch (IOException e) {
            log.error("[oss] 读取上传流失败 {}", originalFilename, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "文件上传失败", e);
        }
    }

    private void doUpload(String objectKey, InputStream in, String contentType, long contentLength) {
        if (oss == null) {
            doLocalUpload(objectKey, in);
            log.info("[oss-local] 上传成功 objectKey={} size={}", objectKey, contentLength);
            return;
        }
        ObjectMetadata meta = new ObjectMetadata();
        if (contentType != null) {
            meta.setContentType(contentType);
        }
        meta.setContentLength(contentLength);
        oss.putObject(properties.getBucketName(), objectKey, in, meta);
        log.info("[oss] 上传成功 objectKey={} size={}", objectKey, contentLength);
    }

    /**
     * 未配置 OSS 凭证时的本地回退：把文件写入 {@link AliOssProperties#getLocalDir()}，
     * 由 {@code ossLocalResourceConfigurer} 的静态资源映射（{@code localUrlPrefix/**}）对外提供访问。
     */
    private void doLocalUpload(String objectKey, InputStream in) {
        Path target = Paths.get(properties.getLocalDir()).resolve(objectKey).normalize();
        try {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("[oss-local] 本地文件写入失败 objectKey={} target={}", objectKey, target, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "文件上传失败", e);
        }
    }

    // ===================== 下载（预签名） =====================

    /**
     * 生成预签名下载 URL，客户端凭此直连 OSS 下载，不经过本服务。
     * 有效期由 {@link AliOssProperties#getDownloadExpireSeconds()} 控制，默认 1 小时。
     *
     * @param objectKey OSS 对象 Key（可从完整 URL 用 {@link #extractObjectKey(String)} 反解）
     * @return 带签名参数的下载 URL
     */
    public String generateDownloadUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "objectKey 不能为空");
        }
        if (oss == null) {
            // 本地模式：文件已通过静态资源映射对外可访问，直接返回访问 URL，无需预签名
            return buildUrl(objectKey);
        }
        try {
            Date expiration = new Date(System.currentTimeMillis()
                    + properties.getDownloadExpireSeconds() * 1000);
            URL url = oss.generatePresignedUrl(properties.getBucketName(), objectKey, expiration);
            return url.toString();
        } catch (ClientException e) {
            log.error("[oss] 生成预签名 URL 失败 objectKey={}", objectKey, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "生成下载链接失败", e);
        }
    }

    // ===================== 工具 =====================

    /**
     * 从完整访问 URL 反解 objectKey。
     * <p>例如输入 {@code https://skyhyf.oss-cn-beijing.aliyuncs.com/20260710/abc.jpg}，
     * 返回 {@code /20260710/abc.jpg}（若需去掉前导 / 调用方自行 trim）。
     *
     * @param url 文件访问 URL
     * @return objectKey 部分
     */
    public String extractObjectKey(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String localPrefix = properties.getLocalUrlPrefix();
        if (localPrefix != null && url.startsWith(localPrefix + "/")) {
            return url.substring(localPrefix.length() + 1);
        }
        String prefix = "https://" + properties.getBucketName() + "." + properties.getEndpoint() + "/";
        // 也兼容 http:// 和没有协议头的情况
        if (url.startsWith(prefix)) {
            return url.substring(prefix.length());
        }
        String httpPrefix = "http://" + properties.getBucketName() + "." + properties.getEndpoint() + "/";
        if (url.startsWith(httpPrefix)) {
            return url.substring(httpPrefix.length());
        }
        // 兜底：尝试从第一个单斜杠前取最后一个 / 之后的内容
        int idx = url.indexOf("://");
        if (idx >= 0) {
            String afterProtocol = url.substring(idx + 3);
            int firstSlash = afterProtocol.indexOf('/');
            if (firstSlash >= 0) {
                return afterProtocol.substring(firstSlash + 1);
            }
        }
        return url;
    }

    /** 构建外部可访问的 URL；本地模式返回静态资源前缀路径 */
    String buildUrl(String objectKey) {
        if (oss == null) {
            return properties.getLocalUrlPrefix() + "/" + objectKey;
        }
        return "https://" + properties.getBucketName() + "." + properties.getEndpoint() + "/" + objectKey;
    }

    /** 生成 objectKey：分区目录（yyyyMMdd）+ UUID + 原扩展名 */
    String buildObjectKey(String originalFilename) {
        String dateDir = LocalDate.now().format(DATE_DIR);
        String ext = "";
        if (originalFilename != null) {
            int dot = originalFilename.lastIndexOf('.');
            if (dot >= 0) {
                ext = originalFilename.substring(dot); // 含 .jpg 的 .
            }
        }
        return dateDir + "/" + UUID.randomUUID().toString().replace("-", "") + ext;
    }
}
