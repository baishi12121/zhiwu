package com.hyf.mallsearchservice.controller;

import com.hyf.mallcommon.core.result.Result;
import com.hyf.mallsearchservice.entity.RequestParams;
import com.hyf.mallsearchservice.entity.SearchResult;
import com.hyf.mallsearchservice.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 搜索域 Controller.
 *
 * <p>当前落地:
 * <ul>
 *   <li>POST /search/all     商品搜索(分页+高亮+过滤+排序+聚合)</li>
 *   <li>GET  /search/tips    搜索自动补全(completion suggester)</li>
 *   <li>POST /search/internal/reindex  全量重建(见 InternalController)</li>
 * </ul>
 *
 * <p>所有方法均返回 {@link Result} 包装,对齐前端 {@code {code,message,data}} 契约。
 *
 * @author hyf
 */
@RestController
@RequestMapping("/search")
public class SearchController {

    @Autowired
    private ProductService productService;

    /**
     * 商品搜索:关键词 + 多维过滤 + 排序 + 高亮 + 分页 + 可选聚合.
     * <p>第一次请求 withFacets=true 返回侧边栏聚合;翻页传 false 只刷新商品列表.
     */
    @PostMapping("/all")
    public Result<SearchResult> search(@RequestBody RequestParams params) {
        return Result.success(productService.search(params));
    }

    /**
     * 搜索自动补全:用户输入前缀,返回匹配的提示词(品牌/分类/商品名片段).
     * <p>前端在搜索框边输边调,无须登录态(注意:网关当前白名单未含 /search/**,
     * 需登录后访问;若要开放匿名搜索,把 /search/** 加入 AuthGlobalFilter 白名单)。
     *
     * @param key   用户已输入的前缀,空白时返回空列表
     * @param limit 返回条数上限,默认 10,最大 20
     * @return 提示词列表
     */
    @GetMapping("/tips")
    public Result<List<String>> suggest(@RequestParam(required = false, defaultValue = "") String key,
                                        @RequestParam(required = false, defaultValue = "10") int limit) {
        return Result.success(productService.suggest(key, limit));
    }
}
