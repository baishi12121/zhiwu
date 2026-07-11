package com.hyf.malladminservice.controller;

import com.hyf.mallcommon.core.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理后台 Controller（骨架）
 *
 * <p>目标接口前缀 {@code /admin/**}，具体待运营需求落地。
 *
 * @author hyf
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(Map.of(
                "service", "mall-admin-service",
                "status", "UP"
        ));
    }
}
