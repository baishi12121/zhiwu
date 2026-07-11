package com.hyf.mallaiservice.controller;

import com.hyf.mallcommon.core.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 域 Controller（预留，当前仅提供健康检查）
 *
 * @author hyf
 */
@RestController
@RequestMapping("/ai")
public class AiController {

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(Map.of(
                "service", "mall-ai-service",
                "status", "UP",
                "note", "reserved for future RAG integration"
        ));
    }
}
