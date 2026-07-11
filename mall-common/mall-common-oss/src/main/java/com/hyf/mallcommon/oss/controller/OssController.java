package com.hyf.mallcommon.oss.controller;

import com.hyf.mallcommon.core.result.Result;
import com.hyf.mallcommon.oss.service.OssService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件上传 / 下载控制器。
 *
 * <p>对齐 API 文档 §13.2：
 * <ul>
 *   <li>{@code POST /upload}（multipart/form-data，{@code file} 字段）→ 返回 {@code {"url":"https://..."}}</li>
 *   <li>{@code GET /upload/download-url?objectKey=...} → 返回预签名下载 URL</li>
 * </ul>
 *
 * <p>本控制器随 {@code OssAutoConfiguration} 一并装配；服务未配 {@code alioss.bucket-name} 时不创建。
 *
 * @author hyf
 */
@RestController
@RequestMapping("/upload")
public class OssController {

    private final OssService ossService;

    public OssController(OssService ossService) {
        this.ossService = ossService;
    }

    /**
     * 文件上传。
     *
     * @param file multipart 文件
     * @return {@code {"url": "https://..."}}
     */
    @PostMapping
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String url = ossService.upload(file);
        return Result.success(Map.of("url", url));
    }

    /**
     * 获取预签名下载 URL。
     *
     * @param objectKey OSS 对象 Key
     * @return {@code {"url": "https://...?Expires=...&Signature=..."}}
     */
    @GetMapping("/download-url")
    public Result<Map<String, String>> downloadUrl(@RequestParam String objectKey) {
        String url = ossService.generateDownloadUrl(objectKey);
        return Result.success(Map.of("url", url));
    }
}
