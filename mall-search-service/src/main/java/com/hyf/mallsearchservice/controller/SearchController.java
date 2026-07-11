package com.hyf.mallsearchservice.controller;

import com.hyf.mallcommon.core.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 搜索域 Controller（骨架）
 *
 * <p>目标接口（{@code doc/小程序接口文档.md} §4）：
 * <ul>
 *   <li>POST /search/all     商品搜索（带筛选条件）</li>
 *   <li>GET  /search/tips    联想词</li>
 * </ul>
 *
 * <p>待 Elasticsearch 就绪后实现。
 *
 * @author hyf
 */
@RestController
@RequestMapping("/search")
public class SearchController {

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(Map.of(
                "service", "mall-search-service",
                "status", "UP",
                "note", "ES not wired yet"
        ));
    }
}
