package com.hyf.mallproductservice.interfaces.rest;

import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.result.Result;
import com.hyf.mallproductservice.application.service.HomeApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 热门推荐 Controller — 对应前端 /hot/{type} 路由.
 *
 * @author hyf
 */
@RestController
public class HotController {

    private final HomeApplicationService homeApplicationService;

    public HotController(HomeApplicationService homeApplicationService) {
        this.homeApplicationService = homeApplicationService;
    }

    @GetMapping("/hot/{type}")
    public Result<Map<String, Object>> hotActivity(@PathVariable String type,
                                                    PageQuery query,
                                                    @RequestParam(required = false) String subType) {
        return Result.success(homeApplicationService.getHotActivity(type, query, subType));
    }
}
