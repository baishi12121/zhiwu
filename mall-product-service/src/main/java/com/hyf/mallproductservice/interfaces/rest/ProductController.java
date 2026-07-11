package com.hyf.mallproductservice.interfaces.rest;

import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;
import com.hyf.mallcommon.core.result.Result;
import com.hyf.mallproductservice.application.service.ProductApplicationService;
import com.hyf.mallproductservice.domain.model.valueobject.SkuVO;
import com.hyf.mallproductservice.domain.model.valueobject.SpecVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 商品域 Controller.
 *
 * @author hyf
 */
@RestController
public class ProductController {

    private final ProductApplicationService productApplicationService;

    public ProductController(ProductApplicationService productApplicationService) {
        this.productApplicationService = productApplicationService;
    }

    /** 健康检查 */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(Map.of(
                "service", "mall-product-service",
                "status", "UP",
                "layer", "DDD: interfaces/application/domain/infrastructure"
        ));
    }

    /** 商品列表（分页 + 筛选），也兼容 ?id=xxx 查询单品 */
    @GetMapping("/products")
    public Result<?> products(@RequestParam(required = false) Long id,
                              @RequestParam(required = false) Long categoryId,
                              @RequestParam(required = false) String keyword,
                              @RequestParam(required = false) String sort,
                              PageQuery query) {
        // 兼容前端 ?id=xxx 查询详情
        if (id != null) {
            Map<String, Object> detail = productApplicationService.getProductDetail(id);
            if (detail == null) {
                return Result.error(404, "商品不存在");
            }
            return Result.success(detail);
        }
        PageResult<Map<String, Object>> pageResult = productApplicationService.getProductList(query, categoryId, keyword, sort);
        return Result.success(pageResult);
    }

    /** 商品详情 */
    @GetMapping("/products/{id}")
    public Result<Map<String, Object>> productDetail(@PathVariable Long id) {
        Map<String, Object> detail = productApplicationService.getProductDetail(id);
        if (detail == null) {
            return Result.error(404, "商品不存在");
        }
        return Result.success(detail);
    }

    /** 商品 SKU 列表 */
    @GetMapping("/products/{id}/skus")
    public Result<List<SkuVO>> productSkus(@PathVariable Long id) {
        return Result.success(productApplicationService.getSkuVOs(id));
    }

    /** 商品规格树 */
    @GetMapping("/products/{id}/specs")
    public Result<List<SpecVO>> productSpecs(@PathVariable Long id) {
        return Result.success(productApplicationService.getSpecVOs(id));
    }

    /** 商品库存 */
    @GetMapping("/products/{id}/stock")
    public Result<Map<String, Object>> productStock(@PathVariable Long id) {
        return Result.success(productApplicationService.getStock(id));
    }

    /** 关键词搜索 */
    @GetMapping("/products/search")
    public Result<PageResult<Map<String, Object>>> search(@RequestParam String keyword, PageQuery query) {
        return Result.success(productApplicationService.getProductList(query, null, keyword, "sales_desc"));
    }
}
