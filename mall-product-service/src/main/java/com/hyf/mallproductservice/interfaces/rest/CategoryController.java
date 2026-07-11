package com.hyf.mallproductservice.interfaces.rest;

import com.hyf.mallcommon.core.result.Result;
import com.hyf.mallproductservice.application.service.CategoryApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 分类 Controller.
 *
 * @author hyf
 */
@RestController
public class CategoryController {

    private final CategoryApplicationService categoryApplicationService;

    public CategoryController(CategoryApplicationService categoryApplicationService) {
        this.categoryApplicationService = categoryApplicationService;
    }

    /** 分类树 */
    @GetMapping("/categories/tree")
    public Result<List<Map<String, Object>>> tree() {
        return Result.success(categoryApplicationService.getTree());
    }

    /** 顶级分类（含二级分类和商品） */
    @GetMapping("/categories/top")
    public Result<List<Map<String, Object>>> top() {
        return Result.success(categoryApplicationService.getTopCategories());
    }
}
