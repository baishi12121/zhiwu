package com.hyf.mallproductservice.interfaces.rest;

import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;
import com.hyf.mallcommon.core.result.Result;
import com.hyf.mallproductservice.application.service.HomeApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 首页聚合 Controller.
 *
 * @author hyf
 */
@RestController
public class HomeController {

    private final HomeApplicationService homeApplicationService;

    public HomeController(HomeApplicationService homeApplicationService) {
        this.homeApplicationService = homeApplicationService;
    }

    /** 轮播图 */
    @GetMapping("/home/banner")
    public Result<List<Map<String, Object>>> banner(@RequestParam(defaultValue = "1") Integer distributionSite) {
        return Result.success(homeApplicationService.getBanners(distributionSite));
    }

    /** 首页前台分类 */
    @GetMapping("/home/category/mutli")
    public Result<List<Map<String, Object>>> categoryMutli() {
        return Result.success(homeApplicationService.getCategoryMutli());
    }

    /** 首页热门推荐卡 */
    @GetMapping("/home/hot/mutli")
    public Result<List<Map<String, Object>>> hotMutli() {
        return Result.success(homeApplicationService.getHotMutli());
    }

    /** 猜你喜欢（分页） */
    @GetMapping("/home/goods/guessLike")
    public Result<PageResult<Map<String, Object>>> guessLike(PageQuery query) {
        return Result.success(homeApplicationService.getGuessLike(query));
    }
}
